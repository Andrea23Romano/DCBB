package dcbb.core.run

import dcbb.core.content.Variants
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.Engine
import dcbb.core.engine.GameEvent
import dcbb.core.engine.Rng
import dcbb.core.engine.Stream
import dcbb.core.model.CardDef
import dcbb.core.model.CombatMods
import dcbb.core.model.ConstantKind
import dcbb.core.model.EnemyBoost
import dcbb.core.model.EnergyColor
import dcbb.core.model.Faction
import dcbb.core.model.Rarity
import dcbb.core.model.StatusType
import dcbb.core.state.Phase

/**
 * The run layer (docs/05): a pure reducer over [RunState], like the combat [Engine] it wraps. A run is a seed plus
 * a list of [RunAction]s, so it saves, replays and simulates exactly.
 *
 * This first slice plays Act I of The First Hour: the Weft and its dealing rules, fights with HP, Paradox and Debt
 * carried between them, card rewards with Glimpse and Skip, Inscribe and Erase, the Antiquarian, Still Points,
 * Caches, Shrines, events with a Divergence and its Ripples, Artifacts, Paradox thresholds, and the act boss.
 * Not yet: Returning Moments, Anchors and Rewind, Imprints and XP, Defection, Standing effects, Forks.
 */
class RunEngine(val rc: RunContent) {
    val content = rc.content
    val engine = Engine(content)

    companion object {
        const val START_HOURS = 100
        const val START_FORESIGHT = 2
        const val FORECAST_FORESIGHT = 3
        const val SKIP_HOURS = 10
        const val REST_HEAL_PCT = 30
        const val STEADY = 2
        const val GLIMPSE_PARADOX = 1
        const val FRAYED = 4
        const val UNSTABLE = 7
        const val UNRAVEL = 10
        const val UNRAVEL_RESET = 5
        const val TEAR_MOMENTS = 2
        const val UNRAVEL_MISPRINTS = 2
        const val MIN_DECK = 5
        const val STANDING_MIN = -3
        const val STANDING_MAX = 3
        const val ERASE_PRICE = 75
        const val ERASE_STEP = 25
        const val INSCRIBE_PRICE = 60
        const val INSCRIBE_DISCOUNT = 20
        const val FORESIGHT_PRICE = 40
        const val CACHE_HOURS = 60
        const val COMBAT_ARTIFACT_PCT = 5

        fun cardPrice(r: Rarity): Int = when (r) {
            Rarity.COMMON, Rarity.STARTER -> 45
            Rarity.UNCOMMON -> 70
            else -> 140
        }

        private val REWARD_RARITIES = setOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE)
    }

    // ---- starting and stepping --------------------------------------------------------------------------------

    fun start(operativeId: String, seed: Long, eraId: String = rc.eras.first().id): RunOutcome {
        val op = content.operative(operativeId)
        val era = rc.era(eraId)
        val initial = RunState(
            operativeId = op.id,
            seed = seed,
            eraId = era.id,
            act = era.act,
            step = 1,
            hp = op.maxHp,
            maxHp = op.maxHp,
            deck = op.starterDeck,
            hours = START_HOURS,
            foresight = if (op.signature == dcbb.core.model.Signature.FORECAST) FORECAST_FORESIGHT else START_FORESIGHT,
            paradox = 0,
            standing = mapOf(op.faction to 1),
            plan = emptyList(),
            screen = Screen.Note("", emptyList()),
            rng = Rng.seeded(seed xor 0x52554E5FL),
            nextId = 1,
        )
        val tx = Tx(initial)
        tx.log += "The First Hour · Act ${era.act}: ${era.name}"
        tx.planAct()
        val first = tx.deal(0)
        tx.s = tx.s.copy(screen = Screen.Weft(first))
        return tx.outcome()
    }

    /**
     * Enters [moment] directly, as if it had been chosen on the Weft. A tool for tests and the lab, not a player
     * action: it is not recorded in run codes.
     */
    fun visit(state: RunState, moment: Moment): RunOutcome {
        val tx = Tx(state)
        tx.visit(moment)
        return tx.outcome()
    }

    fun apply(state: RunState, action: RunAction): RunOutcome {
        if (state.over) return RunOutcome(state, error = "The run is over")
        val tx = Tx(state)
        val error = tx.act(action)
        return if (error != null) RunOutcome(state, error = error) else tx.outcome()
    }

    // ---- queries for UIs and bots -----------------------------------------------------------------------------

    fun era(state: RunState): EraDef = rc.era(state.eraId)

    fun perks(state: RunState): Set<Perk> =
        state.artifacts.flatMap { rc.artifact(it).perks }.toSet() + state.ripples.flatMap { rc.ripple(it).perks }

    /** The rule changes the run brings into a fight with [moment]. */
    fun mods(state: RunState, moment: Moment? = null): CombatMods {
        var m = CombatMods.NONE
        for (a in state.artifacts) m += rc.artifact(a).mods
        for (r in state.ripples) m += rc.ripple(r).mods
        if (state.paradox >= UNSTABLE) m += CombatMods(enemyBoosts = listOf(EnemyBoost(StatusType.MIGHT, 1, eliteOnly = true)))
        if (moment?.ambush == true) m += CombatMods(ambush = true)
        return m
    }

    fun canInscribe(id: String): Boolean =
        !Variants.isInscribed(id) && !Variants.isMisprinted(id) && Variants.inscribedId(id) in content.cards

    fun eraseCost(state: RunState) = ERASE_PRICE + ERASE_STEP * state.erases

    fun inscribeCost(state: RunState) = INSCRIBE_PRICE - if (Perk.INSCRIBE_DISCOUNT in perks(state)) INSCRIBE_DISCOUNT else 0

    /** Cards whose colored pips your income can pay (colored pips must be paid in their color, docs/04). */
    fun payable(state: RunState, def: CardDef): Boolean {
        val color = content.operative(state.operativeId).color
        return (def.cost.faith == 0 || color == EnergyColor.FAITH) &&
            (def.cost.compute == 0 || color == EnergyColor.COMPUTE) &&
            (def.cost.flux == 0 || color == EnergyColor.FLUX)
    }

    /** Engine-rendered outcome text (docs/05 trust rule). Flags are invisible, so they render as null. */
    fun describe(e: RunEffect): String? = when (e) {
        is RunEffect.Hours -> if (e.n >= 0) "+${e.n} Hours" else "−${-e.n} Hours"
        is RunEffect.Heal -> "Heal ${e.n}"
        is RunEffect.HealPct -> "Heal ${e.pct}% of max HP"
        is RunEffect.LoseHp -> "Lose ${e.n} HP"
        is RunEffect.MaxHp -> if (e.n >= 0) "+${e.n} max HP" else "−${-e.n} max HP"
        is RunEffect.Paradox -> if (e.n >= 0) "+${e.n} Paradox" else "−${-e.n} Paradox"
        is RunEffect.Foresight -> if (e.n >= 0) "+${e.n} Foresight" else "−${-e.n} Foresight"
        is RunEffect.Standing -> "${e.faction.label} Standing ${if (e.n >= 0) "+" else "−"}${kotlin.math.abs(e.n)}"
        is RunEffect.Flag -> null
        is RunEffect.GainCard -> buildString {
            append("Gain a random ")
            if (e.pool.relicOnly) {
                append("Relic card")
            } else {
                e.pool.faction?.let { append(it.label).append(' ') }
                append(e.pool.rarity?.name?.lowercase() ?: "")
                append(if (e.pool.rarity != null) " card" else "card")
            }
        }
        is RunEffect.GainArtifact -> e.id?.let { "Gain ${rc.artifact(it).name}" } ?: "Gain a random Artifact"
        is RunEffect.Pick -> if (e.count == 1) "${e.purpose.verb} a card in your deck" else "${e.purpose.verb} ${e.count} cards in your deck"
        is RunEffect.Fight -> buildString {
            append(if (e.encounter == null) "Fight your last fight again" else "Fight ${content.encounter(e.encounter).name}")
            if (e.hoursMult > 1) append(" for ×${e.hoursMult} Hours")
        }
    }

    // ---- one reduction ----------------------------------------------------------------------------------------

    /**
     * One reduction: an imperative shell over the immutable state, like the combat engine's. Helpers that draw
     * randomness or ids update [s] themselves, so compute their results into a local before `s = s.copy(...)`:
     * `s.copy(x = helper())` would copy the state from before the helper ran.
     */
    private inner class Tx(var s: RunState) {
        val log = mutableListOf<String>()
        val events = mutableListOf<GameEvent>()
        val era: EraDef get() = rc.era(s.eraId)
        val op get() = content.operative(s.operativeId)

        fun outcome() = RunOutcome(s, log.toList(), events.toList())

        fun rand(bound: Int, stream: Stream = Stream.MISC): Int {
            val (v, r) = s.rng.nextInt(bound, stream)
            s = s.copy(rng = r)
            return v
        }

        fun chance(pct: Int) = rand(100) < pct
        fun <T> pick(list: List<T>, stream: Stream = Stream.MISC): T = list[rand(list.size, stream)]
        fun <T> shuffled(list: List<T>): List<T> {
            val (l, r) = s.rng.shuffled(list, Stream.SHUFFLE)
            s = s.copy(rng = r)
            return l
        }

        fun moment(
            type: MomentType,
            title: String,
            ref: String? = null,
            faction: Faction? = null,
            threat: Int = 0,
            pinned: Boolean = false,
            tear: Boolean = false,
            ambush: Boolean = false,
        ): Moment {
            val id = s.nextId
            s = s.copy(nextId = id + 1)
            return Moment(id, type, title, ref, faction, threat, pinned, tear, ambush)
        }

        fun act(a: RunAction): String? = when (val sc = s.screen) {
            is Screen.Weft -> onWeft(sc, a)
            is Screen.Fight -> onFight(sc, a)
            is Screen.Reward -> onReward(sc, a)
            is Screen.Shop -> onShop(sc, a)
            is Screen.Rest -> onRest(sc, a)
            is Screen.Event -> onEvent(sc, a)
            is Screen.PickCard -> onPick(sc, a)
            is Screen.Note -> if (a == RunAction.Done) {
                go(sc.next)
                null
            } else {
                "Continue to go on"
            }
            is Screen.Over -> "The run is over"
        }

        /** Shows [next], or deals the next step when it is null. */
        fun go(next: Screen?) {
            if (next != null) s = s.copy(screen = next) else advance()
        }

        // ---- the Weft -----------------------------------------------------------------------------------------

        /** Builds the act's dealing plan: 3 Moments per step, honoring the dealing rules (docs/05 "Dealing rules"). */
        fun planAct() {
            val era = era
            val steps = era.steps
            val slots = List(steps) { mutableListOf<Moment>() }
            val counts = era.composition.toMutableMap()
            fun take(t: MomentType) {
                counts[t] = (counts[t] ?: 0) - 1
            }

            // Pinned by the rules: a Still Point before the boss, an Antiquarian in steps 3–6, the Divergence in 4–7.
            slots[steps - 1] += placeholder(MomentType.STILL_POINT, pinned = true).also { take(MomentType.STILL_POINT) }
            slots[2 + rand(4, Stream.SHUFFLE)] += placeholder(MomentType.ANTIQUARIAN, pinned = true).also { take(MomentType.ANTIQUARIAN) }
            val divergence = rc.event(era.divergence)
            slots[3 + rand(4, Stream.SHUFFLE)] += moment(MomentType.EVENT, divergence.teaser, divergence.id, pinned = true)
                .also { take(MomentType.EVENT) }

            // Elites: steps 3 and later, one per step at most, never your own faction's.
            val elites = shuffled(era.elites.filter { factionOf(it) != op.faction }).take(counts[MomentType.ELITE] ?: 0)
            for (e in elites) {
                val open = (2 until steps).filter { i -> slots[i].size < 3 && slots[i].none { it.type == MomentType.ELITE } }
                if (open.isEmpty()) break
                slots[pick(open, Stream.SHUFFLE)] += fight(MomentType.ELITE, e)
                take(MomentType.ELITE)
            }

            val rest = shuffled(counts.flatMap { (t, n) -> List(maxOf(0, n)) { t } }.sortedBy { it.ordinal })
            var k = 0
            for (slot in slots) while (slot.size < 3 && k < rest.size) slot += placeholder(rest[k++])

            // Variety: no step deals three Moments of one type. Swap one away, never making another step a triple.
            fun triple(slot: List<Moment>) = slot.size == 3 && slot.map { it.type }.toSet().size == 1
            var passes = 0
            while (slots.any { triple(it) } && passes++ < 20) {
                val i = slots.indexOfFirst { triple(it) }
                val mine = slots[i].indexOfLast { !it.pinned }
                if (mine < 0) break
                val t = slots[i][mine]
                val swap = slots.indices.filter { it != i }.sortedBy { if (it > i) it - i else 100 + i - it }.firstNotNullOfOrNull { j ->
                    slots[j].indices.firstOrNull { jx ->
                        val other = slots[j][jx]
                        val ok = !other.pinned && other.type != t.type &&
                            !(other.type == MomentType.ELITE && (i < 2 || slots[i].any { it.type == MomentType.ELITE && it !== t })) &&
                            !(t.type == MomentType.ELITE && (j < 2 || slots[j].any { it.type == MomentType.ELITE && it !== other }))
                        ok && !triple(slots[j].toMutableList().also { it[jx] = t })
                    }?.let { j to it }
                } ?: break
                val (j, jx) = swap
                slots[i][mine] = slots[j][jx]
                slots[j][jx] = t
            }

            // Fill in what each placeholder is: fights by threat tier, sampled events, a Shrine's faction.
            val events = ArrayDeque(shuffled(era.events))
            val anomalies = ArrayDeque(shuffled(era.anomalies))
            val tiers = era.combats.filter { factionOf(it.encounter) != op.faction }.groupBy { it.threat }
            val decks = mutableMapOf<Int, ArrayDeque<String>>()
            fun drawFight(want: Int): Pair<String, Int> {
                val order = listOf(want, want - 1, want + 1, want - 2, want + 2).filter { it in 1..3 && tiers[it] != null }
                val tier = order.first()
                val deck = decks.getOrPut(tier) { ArrayDeque() }
                if (deck.isEmpty()) deck.addAll(shuffled(tiers.getValue(tier).map { it.encounter }))
                return deck.removeFirst() to tier
            }
            val plan = slots.mapIndexed { i, slot ->
                slot.map { m ->
                    when {
                        m.ref != null -> m
                        m.type == MomentType.COMBAT -> {
                            val (enc, tier) = drawFight(if (i < 3) 1 else if (i < 6) 2 else 3)
                            m.copy(title = content.encounter(enc).name, ref = enc, faction = factionOf(enc), threat = tier)
                        }
                        m.type == MomentType.EVENT -> {
                            val ev = rc.event(events.removeFirstOrNull() ?: pick(era.events))
                            m.copy(title = ev.teaser, ref = ev.id)
                        }
                        m.type == MomentType.ANOMALY -> {
                            val ev = rc.event(anomalies.removeFirstOrNull() ?: pick(era.anomalies))
                            m.copy(title = ev.teaser, ref = ev.id)
                        }
                        m.type == MomentType.SHRINE -> {
                            val f = pick(listOf(Faction.ORDER, Faction.CONVERGENCE, Faction.ERRATA))
                            val ev = rc.event(rc.shrines.getValue(f))
                            m.copy(title = ev.title, ref = ev.id, faction = f)
                        }
                        else -> m
                    }
                }
            }
            s = s.copy(plan = plan)
        }

        private fun placeholder(type: MomentType, pinned: Boolean = false): Moment =
            moment(type, pick(era.titles[type] ?: listOf(type.label), Stream.SHUFFLE), pinned = pinned)

        private fun fight(type: MomentType, encounter: String, threat: Int = 3): Moment {
            val enc = content.encounter(encounter)
            return moment(type, enc.name, enc.id, factionOf(enc.id), threat)
        }

        fun factionOf(encounter: String): Faction = content.enemy(content.encounter(encounter).enemies.first()).faction

        /** The Moments step [index] deals, after Paradox has had its say (docs/05 "Thresholds"). */
        fun deal(index: Int): List<Moment> {
            if (s.paradox >= FRAYED && !s.tearAdded) addTearMoments(index)
            var options = s.plan[index]
            if (s.paradox >= UNSTABLE && options.none { it.type == MomentType.ANOMALY }) {
                val at = options.indexOfLast { replaceable(it) }
                if (at >= 0) {
                    val ev = rc.event(pick(era.anomalies))
                    val anomaly = moment(MomentType.ANOMALY, ev.teaser, ev.id)
                    options = options.toMutableList().also { it[at] = anomaly }
                    s = s.copy(plan = s.plan.toMutableList().also { it[index] = options })
                    log += "Unstable: the Weave slips, and an Anomaly is dealt."
                }
            }
            return options
        }

        private fun replaceable(m: Moment) = !m.pinned && m.id !in s.revealed && m.type in setOf(
            MomentType.COMBAT, MomentType.EVENT, MomentType.CACHE, MomentType.SHRINE, MomentType.ANOMALY,
        )

        /** Frayed: Tear Moments join the rest of this act's Era Deck, replacing Moments not yet dealt. */
        private fun addTearMoments(from: Int) {
            val spots = (from until s.plan.size).flatMap { i -> s.plan[i].indices.map { i to it } }
                .filter { (i, j) -> replaceable(s.plan[i][j]) }
            val chosen = shuffled(spots).take(TEAR_MOMENTS)
            val plan = s.plan.map { it.toMutableList() }
            for ((i, j) in chosen) {
                val enc = pick(era.tearFights)
                plan[i][j] = moment(MomentType.COMBAT, content.encounter(enc).name, enc, Faction.TEAR, 2, tear = true)
            }
            s = s.copy(plan = plan, tearAdded = true)
            if (chosen.isNotEmpty()) log += "Frayed: the Tear's Moments join the Era Deck."
        }

        fun advance() {
            if (s.tearAmbush) {
                s = s.copy(tearAmbush = false)
                val enc = pick(era.tearFights)
                val m = moment(MomentType.COMBAT, "Tear Ambush: ${content.encounter(enc).name}", enc, Faction.TEAR, 2, tear = true, ambush = true)
                s = s.copy(screen = Screen.Weft(listOf(m)))
                log += "The Tear finds you."
                return
            }
            val next = s.step + 1
            s = s.copy(step = next)
            if (next <= era.steps) {
                val options = deal(next - 1)
                s = s.copy(screen = Screen.Weft(options))
            } else {
                val boss = content.encounter(era.boss)
                val m = moment(MomentType.BOSS, boss.name, boss.id, factionOf(boss.id), 3)
                s = s.copy(screen = Screen.Weft(listOf(m)))
                log += "The act's last knot: ${boss.name}."
            }
        }

        fun onWeft(w: Screen.Weft, a: RunAction): String? = when (a) {
            is RunAction.Choose -> {
                val m = w.options.getOrNull(a.index) ?: return "No such Moment"
                enter(m)
                null
            }
            RunAction.Foresight -> {
                if (s.foresight <= 0) return "No Foresight left"
                val peek = if (Perk.FORESIGHT_EXTRA_PEEK in perks(s)) 2 else 1
                val upcoming = if (s.step < era.steps) s.plan[s.step] else emptyList()
                val next = upcoming.filter { it.id !in s.revealed }.take(peek)
                val now = w.options.filter { it.id !in s.revealed }
                if (now.isEmpty() && next.isEmpty()) return "Foresight has nothing left to reveal here"
                s = s.copy(
                    foresight = s.foresight - 1,
                    revealed = s.revealed + now.map { it.id } + next.map { it.id },
                    stats = s.stats.copy(foresightSpent = s.stats.foresightSpent + 1),
                )
                log += "Foresight: you see these Moments clearly" + if (next.isNotEmpty()) ", and ${next.size} of the next step" else ""
                null
            }
            else -> "Choose a Moment"
        }

        fun visit(m: Moment) = enter(m)

        private fun enter(m: Moment) {
            log += "Step ${s.step}: ${m.title} (${m.type.label})"
            s = s.copy(stats = s.stats.copy(chosen = s.stats.chosen + (m.type to (s.stats.chosen[m.type] ?: 0) + 1)))
            when (m.type) {
                MomentType.COMBAT, MomentType.ELITE, MomentType.BOSS -> startFight(m, 1)
                MomentType.EVENT, MomentType.ANOMALY, MomentType.SHRINE -> openEvent(m.ref ?: error("Event Moment without an event"))
                MomentType.ANTIQUARIAN -> {
                    val stock = stock()
                    s = s.copy(screen = Screen.Shop(stock))
                }
                MomentType.STILL_POINT -> s = s.copy(screen = Screen.Rest())
                MomentType.CACHE -> openCache()
            }
        }

        // ---- fights -------------------------------------------------------------------------------------------

        fun startFight(m: Moment, hoursMult: Int) {
            val enc = content.encounter(m.ref ?: error("Fight Moment without an encounter"))
            val setup = CombatSetup(
                operativeId = s.operativeId,
                deck = s.deck,
                enemies = enc.enemies,
                seed = s.seed * 1_000_003L + s.act * 7_919L + m.id * 104_729L,
                hp = s.hp,
                paradox = s.paradox,
                debt = s.debt,
                maxHp = s.maxHp,
                mods = mods(s, m),
            )
            val out = engine.start(setup)
            events += out.events
            s = s.copy(screen = Screen.Fight(m, out.state, hoursMult))
        }

        fun onFight(f: Screen.Fight, a: RunAction): String? {
            if (a is RunAction.Fight) {
                if (f.combat.phase.over) return "The fight is over: continue"
                val out = engine.apply(f.combat, a.action)
                if (out.error != null) return out.error
                events += out.events
                s = s.copy(screen = f.copy(combat = out.state))
                return null
            }
            if (a != RunAction.Done) return "Finish the fight first"
            if (!f.combat.phase.over) return "The fight isn't over"
            finishFight(f)
            return null
        }

        private fun finishFight(f: Screen.Fight) {
            val c = f.combat
            val p = c.player
            val enc = content.encounter(f.moment.ref!!)
            val unravels = c.enemies.count { it.defId == Engine.LOOSE_END } - enc.enemies.count { it == Engine.LOOSE_END }
            val record = FightRecord(
                s.step, f.moment.type, enc.id, f.moment.threat, s.hp, maxOf(0, p.hp), s.maxHp, c.round, c.phase == Phase.WON,
                paradoxBefore = s.paradox, unravels = unravels,
            )
            s = s.copy(
                stats = s.stats.copy(
                    fights = s.stats.fights + 1,
                    elites = s.stats.elites + if (f.moment.type == MomentType.ELITE) 1 else 0,
                    hpLostInFights = s.stats.hpLostInFights + maxOf(0, s.hp - maxOf(0, p.hp)),
                    fightRecords = s.stats.fightRecords + record,
                ),
            )
            when (c.phase) {
                Phase.LOST -> {
                    val where = if (s.step > era.steps) "at the end of Act ${s.act}" else "at step ${s.step} of Act ${s.act}"
                    s = s.copy(hp = 0, screen = Screen.Over(false, "You fell", listOf("${enc.name} ended your run in round ${c.round}, $where.") + chronicle()))
                }
                Phase.DRAW -> {
                    carry(c)
                    s = s.copy(screen = Screen.Note("The fight stalls", listOf("The round cap was reached. You slip away with no reward.")))
                }
                else -> {
                    carry(c)
                    s = s.copy(lastEncounter = enc.id)
                    log += "You win (${p.hp}/${p.maxHp} HP)."
                    if (f.moment.type == MomentType.BOSS) {
                        s = s.copy(screen = Screen.Over(true, "Act ${s.act} complete", listOf("${enc.name} falls. ${era.name} is behind you.") + chronicle()))
                    } else {
                        reward(f, enc.name)
                    }
                }
            }
        }

        private fun carry(c: dcbb.core.state.CombatState) {
            val p = c.player
            s = s.copy(hp = p.hp.coerceIn(1, s.maxHp), paradox = p.paradox, debt = p.debt.filterValues { it > 0 })
            notePeak()
        }

        private fun reward(f: Screen.Fight, name: String) {
            val elite = f.moment.type == MomentType.ELITE
            val hours = (if (elite) 40 + rand(16) else 15 + rand(11)) * f.hoursMult
            val lines = mutableListOf<String>()
            gainHours(hours)
            lines += "+$hours Hours"
            if (elite || chance(COMBAT_ARTIFACT_PCT)) randomArtifact()?.let { lines += gainArtifact(it) }
            val cards = rollCards(elite)
            s = s.copy(screen = Screen.Reward("Victory: $name", cards, lines, elite))
        }

        // ---- rewards ------------------------------------------------------------------------------------------

        fun onReward(r: Screen.Reward, a: RunAction): String? = when (a) {
            is RunAction.Choose -> {
                val id = r.cards.getOrNull(a.index) ?: return "No such card"
                s = s.copy(deck = s.deck + id, stats = s.stats.copy(cardsTaken = s.stats.cardsTaken + 1))
                log += "You take ${content.card(id).name}."
                advance()
                null
            }
            RunAction.Glimpse -> {
                if (r.glimpsed) return "You already Glimpsed this reward"
                s = s.copy(stats = s.stats.copy(glimpses = s.stats.glimpses + 1))
                val more = rollCards(r.elite, exclude = r.cards.toSet())
                log += "Glimpse: you see the other branch's cards."
                s = s.copy(screen = r.copy(cards = r.cards + more, glimpsed = true))
                changeParadox(GLIMPSE_PARADOX)
                null
            }
            RunAction.Skip, RunAction.Done -> {
                gainHours(SKIP_HOURS)
                s = s.copy(stats = s.stats.copy(skipped = s.stats.skipped + 1))
                log += "You skip the cards (+$SKIP_HOURS Hours)."
                advance()
                null
            }
            else -> "Take a card, Glimpse, or skip"
        }

        /** Card rewards (docs/05): 60% your faction, 20% neutral, 20% this era; rarity 60/32/8 (+2% per reward without a rare). */
        fun rollCards(elite: Boolean, exclude: Set<String> = emptySet(), n: Int = 3): List<String> {
            val offered = mutableListOf<String>()
            repeat(n) {
                val rarity = rollRarity(elite)
                drawCard(rarity, exclude + offered)?.let { offered += it }
            }
            val sawRare = offered.any { content.card(it).rarity == Rarity.RARE }
            s = s.copy(rareBonus = if (sawRare) 0 else s.rareBonus + 2)
            return offered
        }

        private fun rollRarity(elite: Boolean): Rarity {
            val r = rand(100)
            if (elite) return if (r < 15) Rarity.RARE else if (r < 60) Rarity.UNCOMMON else Rarity.COMMON
            val rare = 8 + s.rareBonus
            return if (r < rare) Rarity.RARE else if (r < rare + 32) Rarity.UNCOMMON else Rarity.COMMON
        }

        /** One card of [rarity] from a rolled source, falling back to your faction when a pool has none. */
        fun drawCard(rarity: Rarity, exclude: Set<String>): String? {
            val bootstrapped = Perk.CONVERGENCE_REWARDS in perks(s)
            val sources = buildList {
                add(op.faction to if (bootstrapped && op.faction == Faction.CONVERGENCE) 120 else 60)
                add(Faction.NEUTRAL to 20)
                add(Faction.ERA to 20)
                if (bootstrapped && op.faction != Faction.CONVERGENCE) add(Faction.CONVERGENCE to 20)
            }
            var r = rand(sources.sumOf { it.second })
            val source = sources.first { (_, w) -> (r < w).also { r -= w } }.first
            val tries = listOf(source to rarity, op.faction to rarity, op.faction to null)
            for ((f, rar) in tries) {
                val pool = pool(f, rar).filter { it.id !in exclude }
                if (pool.isNotEmpty()) return pick(pool).id
            }
            return null
        }

        fun pool(faction: Faction?, rarity: Rarity?, relicOnly: Boolean = false): List<CardDef> =
            content.authored.filter { def ->
                def.rarity in REWARD_RARITIES && (rarity == null || def.rarity == rarity) && payable(s, def) &&
                    (!relicOnly || def.constant?.kind == ConstantKind.RELIC) &&
                    when (faction) {
                        null -> def.faction == op.faction || def.faction == Faction.NEUTRAL || isEraCard(def)
                        Faction.ERA -> isEraCard(def)
                        else -> def.faction == faction
                    }
            }

        private fun isEraCard(def: CardDef) = def.faction == Faction.ERA && def.id.startsWith(era.cardPrefix)

        fun randomArtifact(): String? = rc.artifacts.keys.filter { it !in s.artifacts }.takeIf { it.isNotEmpty() }?.let { pick(it.sorted()) }

        fun gainArtifact(id: String): String {
            val def = rc.artifact(id)
            s = s.copy(artifacts = s.artifacts + id)
            log += "Artifact: ${def.name}."
            val lines = mutableListOf("Artifact: ${def.name}. ${def.text}")
            for (e in def.onGain) applyEffect(e, lines)
            return lines.joinToString(" ")
        }

        fun gainHours(n: Int) {
            s = s.copy(hours = s.hours + n, stats = s.stats.copy(hoursEarned = s.stats.hoursEarned + maxOf(0, n)))
        }

        fun spend(n: Int) {
            s = s.copy(hours = s.hours - n, stats = s.stats.copy(hoursSpent = s.stats.hoursSpent + n))
        }

        // ---- the Antiquarian ----------------------------------------------------------------------------------

        fun stock(): List<ShopItem> {
            val cards = mutableListOf<String>()
            for (r in listOf(Rarity.COMMON, Rarity.COMMON, Rarity.UNCOMMON, Rarity.UNCOMMON, Rarity.RARE)) {
                drawCard(r, cards.toSet())?.let { cards += it }
            }
            val artifacts = shuffled(rc.artifacts.keys.filter { it !in s.artifacts }.sorted()).take(2)
            return cards.map { ShopItem(ShopKind.CARD, it, cardCost(it)) } +
                artifacts.map { ShopItem(ShopKind.ARTIFACT, it, rc.artifact(it).price) } +
                listOf(
                    ShopItem(ShopKind.ERASE, null, eraseCost(s)),
                    ShopItem(ShopKind.INSCRIBE, null, inscribeCost(s)),
                    ShopItem(ShopKind.FORESIGHT, null, FORESIGHT_PRICE),
                )
        }

        private fun cardCost(id: String): Int {
            val def = content.card(id)
            val base = cardPrice(def.rarity)
            val relic = def.constant?.kind == ConstantKind.RELIC && Perk.RELIC_DISCOUNT in perks(s)
            return if (relic) base * 3 / 4 else base
        }

        fun onShop(sh: Screen.Shop, a: RunAction): String? {
            if (a == RunAction.Done) {
                log += "You leave the Antiquarian."
                advance()
                return null
            }
            val i = (a as? RunAction.Choose)?.index ?: return "Buy something or leave"
            val item = sh.stock.getOrNull(i) ?: return "No such item"
            if (item.sold) return "Already bought"
            if (s.hours < item.price) return "Not enough Hours (${item.price} needed, you have ${s.hours})"
            val sold = sh.copy(stock = sh.stock.toMutableList().also { it[i] = item.copy(sold = true) })
            when (item.kind) {
                ShopKind.CARD -> {
                    spend(item.price)
                    s = s.copy(deck = s.deck + item.ref!!, screen = sold)
                    log += "You buy ${content.card(item.ref).name} (${item.price} Hours)."
                }
                ShopKind.ARTIFACT -> {
                    spend(item.price)
                    s = s.copy(screen = sold)
                    gainArtifact(item.ref!!)
                }
                ShopKind.FORESIGHT -> {
                    spend(item.price)
                    s = s.copy(foresight = s.foresight + 1, screen = sold)
                    log += "You buy a Foresight charge."
                }
                ShopKind.ERASE -> {
                    if (s.deck.size <= MIN_DECK) return "Your deck can't go below $MIN_DECK cards"
                    s = s.copy(screen = Screen.PickCard(Purpose.ERASE, 1, next = sold, back = sh, shopItem = i))
                }
                ShopKind.INSCRIBE -> {
                    if (s.deck.none { canInscribe(it) }) return "Nothing in your deck can be Inscribed"
                    s = s.copy(screen = Screen.PickCard(Purpose.INSCRIBE, 1, next = sold, back = sh, shopItem = i))
                }
            }
            return null
        }

        // ---- Still Points -------------------------------------------------------------------------------------

        fun onRest(r: Screen.Rest, a: RunAction): String? {
            val both = Perk.REST_HEAL_AND_INSCRIBE in perks(s)
            if (a == RunAction.Done) {
                advance()
                return null
            }
            when ((a as? RunAction.Choose)?.index) {
                0 -> {
                    if (r.healed) return "You already healed here"
                    if (r.inscribed && !both) return "You already rested"
                    val n = s.maxHp * REST_HEAL_PCT / 100
                    heal(n)
                    log += "You rest and heal."
                    if (both && !r.inscribed) s = s.copy(screen = r.copy(healed = true)) else advance()
                }
                1 -> {
                    if (r.inscribed) return "You already Inscribed here"
                    if (r.healed && !both) return "You already rested"
                    if (s.deck.none { canInscribe(it) }) return "Nothing in your deck can be Inscribed"
                    val next = if (both && !r.healed) r.copy(inscribed = true) else null
                    s = s.copy(screen = Screen.PickCard(Purpose.INSCRIBE, 1, next = next, back = r))
                }
                2 -> {
                    if (r.healed || r.inscribed) return "You already rested"
                    changeParadox(-STEADY)
                    log += "You steady yourself (−$STEADY Paradox)."
                    advance()
                }
                else -> return "Choose heal, Inscribe or Steady"
            }
            return null
        }

        fun heal(n: Int) {
            val amount = minOf(n, s.maxHp - s.hp)
            if (amount <= 0) return
            s = s.copy(hp = s.hp + amount, stats = s.stats.copy(healed = s.stats.healed + amount))
        }

        // ---- picking cards from the deck ----------------------------------------------------------------------

        fun onPick(pc: Screen.PickCard, a: RunAction): String? {
            if (a == RunAction.Done) {
                go(pc.back ?: pc.next)
                return null
            }
            val i = (a as? RunAction.Choose)?.index ?: return "Choose a card"
            val id = s.deck.getOrNull(i) ?: return "No such card"
            val name = content.card(id).name
            when (pc.purpose) {
                Purpose.ERASE -> {
                    if (s.deck.size <= MIN_DECK) return "Your deck can't go below $MIN_DECK cards"
                    s = s.copy(deck = s.deck.filterIndexed { j, _ -> j != i }, stats = s.stats.copy(erased = s.stats.erased + 1))
                    log += "$name is Erased from history."
                }
                Purpose.INSCRIBE -> {
                    if (!canInscribe(id)) return "$name can't be Inscribed"
                    s = s.copy(
                        deck = s.deck.toMutableList().also { it[i] = Variants.inscribedId(id) },
                        stats = s.stats.copy(inscribed = s.stats.inscribed + 1),
                    )
                    log += "You Inscribe $name."
                }
            }
            pc.shopItem?.let { item ->
                val shop = pc.back as Screen.Shop
                spend(shop.stock[item].price)
                if (pc.purpose == Purpose.ERASE) s = s.copy(erases = s.erases + 1)
            }
            val more = pc.remaining - 1
            val canGoOn = when (pc.purpose) {
                Purpose.ERASE -> s.deck.size > MIN_DECK
                Purpose.INSCRIBE -> s.deck.any { canInscribe(it) }
            }
            if (more > 0 && canGoOn) s = s.copy(screen = pc.copy(remaining = more, back = null)) else go(pc.next)
            return null
        }

        // ---- events, anomalies and shrines --------------------------------------------------------------------

        fun openEvent(id: String) {
            val def = rc.event(id)
            val own = def.faction == null || def.faction == op.faction
            val options = def.choices
                .filter { c ->
                    when (c.requires) {
                        Requirement.NONE -> true
                        Requirement.OWN_FACTION -> def.faction == op.faction
                        Requirement.OTHER_FACTION -> !own
                    }
                }
                .map { c -> EventOption(c.id, c.text, c.effects, c.ripples.takeIf { it.isNotEmpty() }?.let { pick(it) }, blocked(c.effects)) }
            s = s.copy(screen = Screen.Event(id, options))
        }

        private fun blocked(effects: List<RunEffect>): String? {
            for (e in effects) {
                when (e) {
                    is RunEffect.Hours -> if (e.n < 0 && s.hours < -e.n) return "Needs ${-e.n} Hours"
                    is RunEffect.LoseHp -> if (s.hp <= e.n) return "Not enough HP"
                    is RunEffect.Pick -> when (e.purpose) {
                        Purpose.ERASE -> if (s.deck.size <= MIN_DECK) return "Your deck is too small"
                        Purpose.INSCRIBE -> if (s.deck.none { canInscribe(it) }) return "Nothing to Inscribe"
                    }
                    else -> Unit
                }
            }
            return null
        }

        fun onEvent(ev: Screen.Event, a: RunAction): String? {
            val i = (a as? RunAction.Choose)?.index ?: return "Choose an option"
            val opt = ev.options.getOrNull(i) ?: return "No such option"
            opt.blocked?.let { return it }
            val def = rc.event(ev.eventId)
            log += "${def.title}: ${opt.text}"
            val lines = mutableListOf<String>()
            var picks: RunEffect.Pick? = null
            var fight: RunEffect.Fight? = null
            for (e in opt.effects) {
                when (e) {
                    is RunEffect.Pick -> picks = e
                    is RunEffect.Fight -> fight = e
                    else -> applyEffect(e, lines)
                }
            }
            opt.ripple?.let { rid ->
                val r = rc.ripple(rid)
                s = s.copy(ripples = s.ripples + rid)
                lines += "Ripple: ${r.name}. ${r.text}"
                log += "Ripple: ${r.name}."
                for (e in r.now) applyEffect(e, lines)
            }
            val note = Screen.Note(def.title, lines.ifEmpty { listOf("Nothing changes, as far as you can tell.") })
            val f = fight
            val pk = picks
            when {
                f != null -> {
                    val enc = f.encounter ?: s.lastEncounter ?: pick(era.combats.filter { it.threat == 1 }).encounter
                    val e = content.encounter(enc)
                    startFight(moment(MomentType.COMBAT, e.name, e.id, factionOf(e.id), 2), f.hoursMult)
                }
                pk != null -> s = s.copy(screen = Screen.PickCard(pk.purpose, pk.count, next = note, back = null))
                else -> s = s.copy(screen = note)
            }
            return null
        }

        fun applyEffect(e: RunEffect, lines: MutableList<String>) {
            val text = describe(e)
            when (e) {
                is RunEffect.Hours -> if (e.n >= 0) gainHours(e.n) else spend(-e.n)
                is RunEffect.Heal -> heal(e.n)
                is RunEffect.HealPct -> heal(s.maxHp * e.pct / 100)
                is RunEffect.LoseHp -> s = s.copy(hp = maxOf(1, s.hp - e.n))
                is RunEffect.MaxHp -> s = s.copy(maxHp = maxOf(1, s.maxHp + e.n), hp = (s.hp + maxOf(0, e.n)).coerceAtMost(s.maxHp + e.n))
                is RunEffect.Paradox -> changeParadox(e.n)
                is RunEffect.Foresight -> s = s.copy(foresight = maxOf(0, s.foresight + e.n))
                is RunEffect.Standing -> s = s.copy(
                    standing = s.standing + (e.faction to (s.standingWith(e.faction) + e.n).coerceIn(STANDING_MIN, STANDING_MAX)),
                )
                is RunEffect.Flag -> s = s.copy(ledger = s.ledger + e.id)
                is RunEffect.GainCard -> {
                    val pool = pool(e.pool.faction, e.pool.rarity, e.pool.relicOnly)
                    if (pool.isEmpty()) {
                        lines += "No card answers the call."
                        return
                    }
                    val def = pick(pool)
                    s = s.copy(deck = s.deck + def.id)
                    lines += "You gain ${def.name}."
                    return
                }
                is RunEffect.GainArtifact -> {
                    val id = e.id?.takeIf { it !in s.artifacts } ?: randomArtifact()
                    if (id == null) {
                        gainHours(CACHE_HOURS)
                        lines += "+$CACHE_HOURS Hours (you hold every Artifact)"
                    } else {
                        lines += gainArtifact(id)
                    }
                    return
                }
                is RunEffect.Pick, is RunEffect.Fight -> Unit
            }
            text?.let { lines += it }
        }

        /** Out of combat, Paradox is a 0..10 meter too; reaching 10 Unravels (docs/05 "Unravel"). */
        fun changeParadox(delta: Int) {
            val to = (s.paradox + delta).coerceIn(0, UNRAVEL)
            s = s.copy(paradox = to)
            notePeak()
            if (to >= UNRAVEL) unravel()
        }

        private fun notePeak() {
            if (s.paradox > s.stats.paradoxPeak) s = s.copy(stats = s.stats.copy(paradoxPeak = s.paradox))
        }

        private fun unravel() {
            val candidates = s.deck.indices.filter { i ->
                val id = s.deck[i]
                !Variants.isMisprinted(id) && Variants.misprintedId(id) in content.cards
            }
            val hit = shuffled(candidates).take(UNRAVEL_MISPRINTS)
            val deck = s.deck.toMutableList()
            for (i in hit) deck[i] = Variants.misprintedId(deck[i])
            val names = hit.joinToString(", ") { content.card(s.deck[it]).name }
            s = s.copy(deck = deck, paradox = UNRAVEL_RESET, tearAmbush = true, stats = s.stats.copy(unravels = s.stats.unravels + 1))
            log += "PARADOX 10: the Weave tears around you. Misprinted: ${names.ifEmpty { "nothing" }}. The Tear will find you next."
        }

        // ---- Caches -------------------------------------------------------------------------------------------

        private fun openCache() {
            val lines = mutableListOf<String>()
            val upgraded = "tear_notices" in s.ripples && CACHE_UPGRADE_USED !in s.ledger
            if (upgraded) {
                s = s.copy(ledger = s.ledger + CACHE_UPGRADE_USED)
                lines += "The Tear noticed you: this Cache holds more."
                applyEffect(RunEffect.GainArtifact(), lines)
                gainHours(CACHE_HOURS)
                lines += "+$CACHE_HOURS Hours"
            } else {
                val r = rand(100)
                when {
                    r < 40 -> applyEffect(RunEffect.GainArtifact(), lines)
                    r < 80 -> {
                        gainHours(CACHE_HOURS)
                        lines += "+$CACHE_HOURS Hours"
                    }
                    else -> applyEffect(RunEffect.GainCard(CardPool(rarity = Rarity.RARE)), lines)
                }
            }
            s = s.copy(screen = Screen.Note("Cache", lines))
        }

        /** The run's story so far, for the end screen (the GenAI Chronicle comes later). */
        fun chronicle(): List<String> = buildList {
            val st = s.stats
            add("Fights won: ${st.fightRecords.count { it.won }} (${st.elites} elite) · HP lost in fights: ${st.hpLostInFights} · healed: ${st.healed}")
            add("Deck: ${s.deck.size} cards · taken ${st.cardsTaken}, skipped ${st.skipped}, Erased ${st.erased}, Inscribed ${st.inscribed}")
            add("Hours earned ${st.hoursEarned}, spent ${st.hoursSpent} · Foresight spent ${st.foresightSpent} · Glimpses ${st.glimpses}")
            add("Paradox peak ${st.paradoxPeak} · out-of-combat Unravels ${st.unravels}")
            if (s.artifacts.isNotEmpty()) add("Artifacts: " + s.artifacts.joinToString(", ") { rc.artifact(it).name })
            if (s.ripples.isNotEmpty()) add("Ripples: " + s.ripples.joinToString(", ") { rc.ripple(it).name })
            if (s.ledger.isNotEmpty()) add("Ledger: " + s.ledger.joinToString(", "))
        }
    }

    private val CACHE_UPGRADE_USED = "tear.cache_upgraded"
}
