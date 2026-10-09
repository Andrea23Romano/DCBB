package dcbb.core.bot

import dcbb.core.budget.Budget
import dcbb.core.content.Variants
import dcbb.core.model.CardDef
import dcbb.core.model.CardType
import dcbb.core.model.Faction
import dcbb.core.model.Rarity
import dcbb.core.run.MomentType
import dcbb.core.run.Perk
import dcbb.core.run.Purpose
import dcbb.core.run.Returning
import dcbb.core.run.RunAction
import dcbb.core.run.RunEffect
import dcbb.core.run.RunEngine
import dcbb.core.run.RunState
import dcbb.core.run.Screen
import dcbb.core.run.ShopKind
import kotlin.math.max

/**
 * A simulated player for whole runs: a combat [Bot] for the fights (the planner by default, a fresh one per fight)
 * and plain heuristics between them. It is a baseline for run-level balance, not a strong player: it never Glimpses,
 * spends Foresight only to look at Elites, and judges cards by budget efficiency and deck shape.
 */
class RunBot(private val seed: Long = 0, private val fightBot: (Long) -> Bot = { PlannerBot(it) }) {
    private var bot: Bot? = null
    private var botFor: Int? = null

    fun act(re: RunEngine, s: RunState): RunAction = when (val sc = s.screen) {
        is Screen.Weft -> weft(re, s, sc)
        is Screen.Fight -> if (sc.combat.phase.over) {
            RunAction.Done
        } else {
            if (botFor != sc.moment.id) {
                bot = fightBot(seed * 31 + sc.moment.id)
                botFor = sc.moment.id
            }
            RunAction.Fight(bot!!.act(re.engine, sc.combat))
        }
        is Screen.Reward -> reward(re, s, sc)
        is Screen.Shop -> shop(re, s, sc)
        is Screen.Rest -> rest(re, s, sc)
        is Screen.Event -> event(re, s, sc)
        is Screen.PickCard -> RunAction.Choose(pickCard(re, s, sc.purpose))
        // Falling with an Anchor in hand: always Rewind (+5 Paradox beats the end of the run).
        is Screen.Fallen -> if (s.anchor != null) RunAction.Rewind else RunAction.Done
        is Screen.Note, is Screen.Over -> RunAction.Done
    }

    private fun hpFrac(s: RunState) = s.hp.toDouble() / s.maxHp

    private fun weft(re: RunEngine, s: RunState, w: Screen.Weft): RunAction {
        if (s.anchorOffer) return RunAction.SetAnchor
        if (w.options.size == 1) return RunAction.Choose(0)
        val unseenElite = w.options.any { it.type == MomentType.ELITE && it.id !in s.revealed }
        if (unseenElite && s.foresight > 0) return RunAction.Foresight
        val hp = hpFrac(s)
        val scored = w.options.mapIndexed { i, m ->
            val v = when (m.type) {
                MomentType.COMBAT -> 4 + 8 * (hp - 0.5) - 2.0 * (m.threat - 1)
                MomentType.ELITE -> 2 + 16 * (hp - 0.7)
                MomentType.EVENT, MomentType.ANOMALY -> 3.0
                MomentType.ANTIQUARIAN -> 0.5 + s.hours / 40.0
                MomentType.STILL_POINT -> 12 * (1 - hp) - 1 + if (s.paradox >= 4) 1.0 * s.paradox else 0.0
                MomentType.SHRINE -> if (m.faction == re.content.operative(s.operativeId).faction) 3.5 else 1.5
                MomentType.CACHE -> 6.0
                MomentType.BOSS -> 100.0
            } + when (m.returning) {
                Returning.AMBUSH -> -1.5
                Returning.EMPOWERED -> -4.0
                Returning.LOOTED -> -1.0
                else -> 0.0
            }
            i to v
        }
        return RunAction.Choose(scored.maxBy { it.second }.first)
    }

    /** How much a card would add to this deck: budget efficiency, rarity, and the Attack/Skill balance. */
    fun cardScore(re: RunEngine, s: RunState, def: CardDef): Double {
        val deck = s.deck.map { re.content.card(it) }
        val attacks = deck.count { it.type == CardType.ATTACK }
        val skills = deck.count { it.type == CardType.SKILL }
        var v = Budget.points(def) / max(6.0, Budget.target(def) / def.rarity.budgetMultiplier)
        v += when (def.rarity) {
            Rarity.UNCOMMON -> 0.15
            Rarity.RARE, Rarity.LEGENDARY -> 0.3
            else -> 0.0
        }
        if (def.type == CardType.ATTACK && attacks <= skills) v += 0.15
        if (def.type == CardType.SKILL && skills < attacks) v += 0.15
        if (def.type == CardType.CONSTANT) v += if (deck.count { it.type == CardType.CONSTANT } < 3) 0.1 else -0.4
        // Copies: a second Constant fights the first for a slot (two Paradox Engines Unravel you every other round).
        val copies = deck.count { Variants.baseId(it.id) == def.id }
        v -= copies * if (def.type == CardType.CONSTANT) 0.6 else 0.15
        if (def.faction == Faction.NEUTRAL) v -= 0.1
        if (s.deck.size > 22) v -= 0.25
        return v
    }

    private fun reward(re: RunEngine, s: RunState, r: Screen.Reward): RunAction {
        if (r.artifacts.isNotEmpty()) return RunAction.TakeArtifact(0)
        val best = r.cards.withIndex().maxByOrNull { cardScore(re, s, re.content.card(it.value)) }
        return if (best != null && cardScore(re, s, re.content.card(best.value)) >= 1.0) RunAction.Choose(best.index) else RunAction.Skip
    }

    private fun shop(re: RunEngine, s: RunState, sh: Screen.Shop): RunAction {
        fun affordable(i: Int) = !sh.stock[i].sold && sh.stock[i].price <= s.hours
        val idx = sh.stock.indices
        idx.firstOrNull { sh.stock[it].kind == ShopKind.ERASE && affordable(it) && s.deck.size > RunEngine.MIN_DECK + 4 && basics(re, s).isNotEmpty() }
            ?.let { return RunAction.Choose(it) }
        idx.firstOrNull { sh.stock[it].kind == ShopKind.INSCRIBE && affordable(it) && s.deck.any { c -> re.canInscribe(c) } }
            ?.let { return RunAction.Choose(it) }
        idx.filter { sh.stock[it].kind == ShopKind.CARD && affordable(it) }
            .maxByOrNull { cardScore(re, s, re.content.card(sh.stock[it].ref!!)) }
            ?.takeIf { cardScore(re, s, re.content.card(sh.stock[it].ref!!)) >= 1.1 }
            ?.let { return RunAction.Choose(it) }
        idx.firstOrNull { sh.stock[it].kind == ShopKind.ARTIFACT && affordable(it) }?.let { return RunAction.Choose(it) }
        return RunAction.Done
    }

    private fun rest(re: RunEngine, s: RunState, r: Screen.Rest): RunAction {
        val canInscribe = s.deck.any { re.canInscribe(it) }
        if (r.healed || r.inscribed) {
            // Bell of the Still Hour: the other half, if it's worth anything.
            return if (!r.inscribed && canInscribe) RunAction.Choose(1) else if (!r.healed && s.hp < s.maxHp) RunAction.Choose(0) else RunAction.Done
        }
        val both = Perk.REST_HEAL_AND_INSCRIBE in re.perks(s)
        return when {
            hpFrac(s) < 0.6 || both -> RunAction.Choose(0)
            s.paradox >= 4 -> RunAction.Choose(2)
            canInscribe -> RunAction.Choose(1)
            else -> RunAction.Choose(0)
        }
    }

    private fun event(re: RunEngine, s: RunState, ev: Screen.Event): RunAction {
        val hp = hpFrac(s)
        val errata = re.content.operative(s.operativeId).faction == Faction.ERRATA
        val scored = ev.options.mapIndexed { i, o ->
            if (o.blocked != null) return@mapIndexed i to Double.NEGATIVE_INFINITY
            var v = 0.0
            for (e in o.effects) {
                v += when (e) {
                    is RunEffect.Hours -> e.n / 10.0
                    is RunEffect.Heal -> e.n * if (hp < 0.6) 0.5 else 0.15
                    is RunEffect.HealPct -> e.pct * s.maxHp / 100.0 * if (hp < 0.6) 0.5 else 0.15
                    is RunEffect.LoseHp -> -e.n * if (hp < 0.5) 1.0 else 0.4
                    is RunEffect.MaxHp -> e.n * 0.6
                    is RunEffect.Paradox -> -e.n * if (errata) 0.5 else 1.5
                    is RunEffect.Foresight -> 1.5 * e.n
                    is RunEffect.GainCard -> if (e.pool.rarity == Rarity.RARE) 6.0 else 3.0
                    is RunEffect.GainArtifact -> 12.0
                    is RunEffect.Pick -> e.count * if (e.purpose == Purpose.INSCRIBE) 5.0 else 3.0
                    is RunEffect.Fight -> if (hp > 0.65) 3.0 * e.hoursMult else -6.0
                    is RunEffect.Standing, is RunEffect.Flag -> 0.0
                }
            }
            if (o.ripple != null) v += 1.0
            i to v
        }
        return RunAction.Choose(scored.maxBy { it.second }.first)
    }

    private fun basics(re: RunEngine, s: RunState): List<Int> = s.deck.indices.filter { i ->
        val def = re.content.card(s.deck[i])
        def.rarity == Rarity.STARTER && def.type != CardType.CONSTANT && def.forkFaces == null &&
            Budget.points(def) <= 12.0 && !Variants.isInscribed(def.id) && def.effects.size == 1
    }

    private fun pickCard(re: RunEngine, s: RunState, purpose: Purpose): Int = when (purpose) {
        Purpose.ERASE -> {
            val misprinted = s.deck.indices.firstOrNull { Variants.isMisprinted(s.deck[it]) }
            val basic = basics(re, s).sortedBy { if (re.content.card(s.deck[it]).type == CardType.ATTACK) 0 else 1 }.firstOrNull()
            misprinted ?: basic ?: s.deck.indices.minBy { Budget.points(re.content.card(s.deck[it])) }
        }
        Purpose.INSCRIBE -> s.deck.indices.filter { re.canInscribe(s.deck[it]) }.maxBy { i ->
            val base = re.content.card(s.deck[i])
            val up = re.content.card(Variants.inscribedId(base.id))
            Budget.points(up) - Budget.points(base) + if (base.type == CardType.CONSTANT) 4.0 else 0.0
        }
    }
}

/** Plays whole runs with a [RunBot]; used by the simulator and the tests. */
object RunRunner {
    fun play(re: RunEngine, operative: String, seed: Long, bot: RunBot = RunBot(seed), maxActions: Int = 20_000): Pair<RunState, List<RunAction>> {
        var s = re.start(operative, seed).state
        val actions = mutableListOf<RunAction>()
        var guard = 0
        while (!s.over && guard++ < maxActions) {
            val a = bot.act(re, s)
            val out = re.apply(s, a)
            if (out.error != null) {
                // A bot slip (an illegal play): fall back to the safest move for the screen.
                val fallback = when (val sc = s.screen) {
                    is Screen.Fight -> RunAction.Fight(dcbb.core.engine.EndTurn)
                    is Screen.Shop, is Screen.PickCard, is Screen.Rest, is Screen.Note, is Screen.Fallen -> RunAction.Done
                    is Screen.Reward -> RunAction.Skip
                    is Screen.Event -> RunAction.Choose(sc.options.indexOfFirst { it.blocked == null }.coerceAtLeast(0))
                    else -> RunAction.Choose(0)
                }
                val again = re.apply(s, fallback)
                if (again.error != null) error("Run bot stuck on ${s.screen::class.simpleName}: ${out.error} / ${again.error}")
                actions += fallback
                s = again.state
                continue
            }
            actions += a
            s = out.state
        }
        return s to actions
    }
}
