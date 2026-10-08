package dcbb.core.engine

import dcbb.core.content.Content
import dcbb.core.model.Amount
import dcbb.core.model.Bonus
import dcbb.core.model.CardType
import dcbb.core.model.CombatMods
import dcbb.core.model.Cond
import dcbb.core.model.ConstantKind
import dcbb.core.model.ConstantSpec
import dcbb.core.model.Cost
import dcbb.core.model.Counter
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.EnemyDef
import dcbb.core.model.EnergyColor
import dcbb.core.model.Face
import dcbb.core.model.Signature
import dcbb.core.model.StatusType
import dcbb.core.model.Tgt
import dcbb.core.model.VowRule
import dcbb.core.state.CardInst
import dcbb.core.state.CombatState
import dcbb.core.state.EnemyState
import dcbb.core.state.FxCtx
import dcbb.core.state.PendingForesee
import dcbb.core.state.Phase
import dcbb.core.state.PlayerState
import dcbb.core.state.ShiftPair
import dcbb.core.state.TrackItem
import dcbb.core.state.TrackKind
import dcbb.core.state.TurnStep
import dcbb.core.state.Work
import dcbb.core.text.RulesText
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class CombatSetup(
    val operativeId: String,
    val deck: List<String>,
    val enemies: List<String>,
    val seed: Long,
    val hp: Int? = null,
    val paradox: Int = 0,
    val roundCap: Int = 60,
    /** Debt still owed from the previous combat. It is repaid at this combat's first Dawn. */
    val debt: Map<EnergyColor, Int> = emptyMap(),
    /** Max HP when the run has changed it; the operative's printed max HP otherwise. */
    val maxHp: Int? = null,
    /** Rule changes the run brings in: Artifacts, Ripples, Paradox thresholds, Ambush. */
    val mods: CombatMods = CombatMods.NONE,
)

data class Outcome(val state: CombatState, val events: List<GameEvent>, val error: String? = null)

enum class TrackNeed { NONE, DELAYABLE, REMOVABLE_INTENT, FORKED_INTENT }

/** What a face needs chosen when it is played. */
data class Needs(
    val enemy: Boolean = false,
    val track: TrackNeed = TrackNeed.NONE,
    val card: Boolean = false,
    val recall: Int = 0,
    val shift: Int = 0,
)

/**
 * The combat rules engine (docs/04): a pure reducer. [apply] never mutates its input; it returns a new state plus
 * the events that happened. Pending work lives in the state's queue, so a Foresee can pause mid-card (or mid-Dusk)
 * and resume when the player answers.
 */
class Engine(val content: Content) {

    companion object {
        const val HAND_LIMIT = 10
        const val DRAW_PER_TURN = 5
        const val RESERVOIR_CAP = CombatMods.DEFAULT_RESERVOIR_CAP
        const val BORROW_LIMIT = 2

        /** Extra Debt the first time you Borrow in a turn: Borrow 1 and owe 2, Borrow 2 and owe 3. */
        const val BORROW_INTEREST = 1
        const val PENANCE = 4
        const val UNRAVEL_AT = 10
        const val UNRAVEL_RESET = 5
        const val STRIKE_DAMAGE = 4
        const val CONSTANT_SLOTS = 3
        const val PRIMARY_INCOME = 2
        const val NEUTRAL_INCOME = 1
        const val LOOSE_END = "tear.loose_end"

        /** Loop guard from docs/08: no turn plays more than this many cards. */
        const val PLAY_CAP = 60
    }

    fun start(setup: CombatSetup): Outcome {
        val op = content.operative(setup.operativeId)
        val mods = setup.mods
        var rng = Rng.seeded(setup.seed)
        var uid = 1
        val deck = setup.deck.map { CardInst(uid++, content.card(it).id) }
        val (future, shuffledRng) = rng.shuffled(deck, Stream.SHUFFLE)
        rng = shuffledRng
        val enemies = setup.enemies.map { id -> newEnemy(content.enemy(id), uid++, mods) }
        val maxHp = setup.maxHp ?: op.maxHp
        val player = PlayerState(
            operativeId = op.id,
            hp = (setup.hp ?: maxHp).coerceAtMost(maxHp),
            maxHp = maxHp,
            future = future,
            paradox = setup.paradox,
            debt = setup.debt.filterValues { it > 0 },
        )
        val initial = CombatState(
            round = if (mods.ambush) 0 else 1,
            phase = Phase.PLAYER_TURN,
            player = player,
            enemies = enemies,
            rng = rng,
            nextUid = uid,
            nextOrder = 1,
            roundCap = setup.roundCap,
            mods = mods,
        )
        val run = Run(initial)
        if (mods.installSubroutine) run.installRandomSubroutine()
        run.rearm(predictive = false)
        if (mods.ambush) {
            run.emit(GameEvent.Info("Ambush! The enemies act before your first turn."))
            run.pushFront(Work.Step(TurnStep.ENEMY_PHASE))
        } else {
            run.pushFront(Work.Step(TurnStep.DAWN))
        }
        if (mods.startEffects.isNotEmpty()) {
            // After the opening Dawn: the queue drains the Dawn (or the Ambush round) first.
            run.pushBack(Work.Fx(mods.startEffects, FxCtx(faction = op.faction)))
        }
        run.drain()
        return run.outcome()
    }

    /** An enemy as it enters a fight, with the run's HP changes and starting statuses. */
    private fun newEnemy(def: EnemyDef, uid: Int, mods: CombatMods): EnemyState {
        val pct = mods.enemyHpPct[def.faction] ?: 100
        val hp = max(1, (def.maxHp * pct + 50) / 100)
        val statuses = mods.enemyBoosts.filter { it.matches(def) }
            .fold(emptyMap<StatusType, Int>()) { acc, b -> acc.plusOne(b.status, b.n) }
        return EnemyState(uid, def.id, hp, hp, statuses = statuses)
    }

    fun apply(state: CombatState, action: Action): Outcome {
        if (state.phase.over) return Outcome(state, emptyList(), "The combat is over")
        val run = Run(state)
        val error = if (action is ResolveForesee) {
            run.resolveForesee(action)
        } else if (state.pending != null) {
            "Answer the Foresee first"
        } else {
            when (action) {
                is PlayCard -> run.playCard(action)
                is UseSignature -> run.useSignature(action)
                EndTurn -> run.endTurn()
                is ResolveForesee -> error("unreachable")
            }
        }
        if (error != null) return Outcome(state, emptyList(), error)
        run.drain()
        return run.outcome()
    }

    // ---------------------------------------------------------------------------------------------------------
    // Queries for UIs and bots
    // ---------------------------------------------------------------------------------------------------------

    fun faces(inst: CardInst): List<Face> {
        val def = content.card(inst.defId)
        inst.face?.let { return listOf(it) }
        def.forkFaces?.let { return it }
        inst.granted?.let { return listOf(def.baseFace, it) }
        return listOf(def.baseFace)
    }

    fun typeOf(inst: CardInst): CardType = inst.face?.type ?: content.card(inst.defId).type

    fun needs(face: Face): Needs = needsOf(face.effects, Needs())

    private fun needsOf(effects: List<Effect>, acc: Needs): Needs = effects.fold(acc) { n, e ->
        when (e) {
            is Effect.Damage -> if (e.target == Tgt.CHOSEN_ENEMY) n.copy(enemy = true) else n
            is Effect.ApplyStatus -> if (e.target == Tgt.CHOSEN_ENEMY) n.copy(enemy = true) else n
            is Effect.Delay -> n.copy(track = TrackNeed.DELAYABLE)
            Effect.RemoveIntent -> n.copy(track = TrackNeed.REMOVABLE_INTENT)
            Effect.Observe -> if (n.track == TrackNeed.NONE) n.copy(track = TrackNeed.FORKED_INTENT) else n
            Effect.ForkCard, Effect.GrantRetain, is Effect.ReduceCostThisTurn -> n.copy(card = true)
            is Effect.Recall -> n.copy(recall = max(n.recall, e.n))
            is Effect.Shift -> n.copy(shift = n.shift + e.n)
            is Effect.When -> needsOf(e.otherwise, needsOf(e.then, n))
            is Effect.Schedule -> needsOf(e.effects, n)
            else -> n
        }
    }

    fun constantSpec(inst: CardInst): ConstantSpec? = content.card(inst.defId).constant

    /**
     * The cost to pay right now: printed cost, plus Glitch, plus this turn's changes. Bleed-through cards come from
     * another faction's branch, so their colored pips are paid as generic pips.
     */
    fun effectiveCost(state: CombatState, inst: CardInst): Cost {
        val glitch = if (state.player.status(StatusType.GLITCH) > 0) 1 else 0
        val printed = content.card(inst.defId).cost
        val base = if (inst.bleed) Cost(generic = printed.total) else printed
        return base.adjusted(inst.costDelta + glitch)
    }

    fun trackCandidates(state: CombatState, need: TrackNeed): List<TrackItem> = when (need) {
        TrackNeed.NONE -> emptyList()
        TrackNeed.DELAYABLE -> state.track.filter { !(it.kind == TrackKind.INTENT && it.fixed) }
        TrackNeed.REMOVABLE_INTENT -> state.track.filter { it.kind == TrackKind.INTENT && !it.fixed }
        TrackNeed.FORKED_INTENT -> state.track.filter { it.forked && it.collapsed == null }
    }

    fun incomeColors(state: CombatState): Set<EnergyColor> =
        setOf(content.operative(state.player.operativeId).color, EnergyColor.NEUTRAL)

    fun payment(state: CombatState, inst: CardInst, style: PayStyle, allowBorrow: Boolean): Payment? {
        val p = state.player
        val borrowLeft = if (allowBorrow) BORROW_LIMIT - p.borrowedThisTurn else 0
        return Payments.plan(
            effectiveCost(state, inst),
            p.energy,
            content.card(inst.defId).faction.color,
            incomeColors(state),
            style,
            borrowLeft,
        )
    }

    /** Total damage an intent would deal if it resolved right now (outcome A, B, or the collapsed one). */
    fun intentDamage(state: CombatState, item: TrackItem, outcome: Int = item.collapsed ?: 0): Int {
        val enemy = item.enemyUid?.let { state.enemy(it) } ?: return 0
        val actions = if (outcome == 1) item.alt ?: return 0 else item.actions
        val mult = 1.0 + 0.25 * item.pressure
        return actions.sumOf { a ->
            when (a) {
                is EnemyAction.Attack -> perHitToPlayer(state, enemy, a.damage, mult, item.fixed) * a.hits
                is EnemyAction.AttackPer -> {
                    val base = a.per * enemy.status(a.status)
                    if (base > 0) perHitToPlayer(state, enemy, base, mult, item.fixed) else 0
                }
                else -> 0
            }
        }
    }

    /** Expected damage from intents that resolve in the coming enemy phase (Forked intents averaged). */
    fun expectedIncoming(state: CombatState): Double = state.track
        .filter { it.kind == TrackKind.INTENT && it.countdown <= 1 }
        .sumOf { item ->
            if (item.forked && item.collapsed == null) {
                (intentDamage(state, item, 0) + intentDamage(state, item, 1)) / 2.0
            } else {
                intentDamage(state, item).toDouble()
            }
        }

    private fun perHitToPlayer(state: CombatState, enemy: EnemyState, base: Int, mult: Double, fixed: Boolean): Int {
        val pressured = ceil((base + enemy.status(StatusType.MIGHT)) * mult - 1e-9)
        var d = pressured
        if (enemy.status(StatusType.WEAK) > 0) d *= 0.75
        if (state.player.status(StatusType.EXPOSED) > 0) d *= 1.5
        if (fixed) d = d * state.mods.fixedDamagePct / 100.0
        return max(0, floor(d + 1e-9).toInt())
    }

    // ---------------------------------------------------------------------------------------------------------
    // One reduction: an imperative shell over immutable state
    // ---------------------------------------------------------------------------------------------------------

    private inner class Run(var s: CombatState) {
        val events = mutableListOf<GameEvent>()
        val p: PlayerState get() = s.player

        fun outcome() = Outcome(s, events)
        fun emit(e: GameEvent) {
            events += e
        }

        fun player(f: (PlayerState) -> PlayerState) {
            s = s.copy(player = f(s.player))
        }

        fun enemyUpdate(uid: Int, f: (EnemyState) -> EnemyState) {
            s = s.copy(enemies = s.enemies.map { if (it.uid == uid) f(it) else it })
        }

        fun pushBack(w: Work) {
            s = s.copy(queue = s.queue + w)
        }

        fun pushFront(vararg w: Work) = pushFront(w.toList())
        fun pushFront(ws: List<Work>) {
            if (ws.isNotEmpty()) s = s.copy(queue = ws + s.queue)
        }

        fun newUid(): Int {
            val u = s.nextUid
            s = s.copy(nextUid = u + 1)
            return u
        }

        fun newOrder(): Int {
            val o = s.nextOrder
            s = s.copy(nextOrder = o + 1)
            return o
        }

        fun rand(bound: Int, stream: Stream): Int {
            val (v, r) = s.rng.nextInt(bound, stream)
            s = s.copy(rng = r)
            return v
        }

        fun name(inst: CardInst) = content.card(inst.defId).name
        fun enemyName(e: EnemyState) = content.enemy(e.defId).name

        /** Lovelace's Notes: a random Subroutine from the Future is installed for free before the first Dawn. */
        fun installRandomSubroutine() {
            val subs = p.future.filter { constantSpec(it)?.kind == ConstantKind.SUBROUTINE }
            if (subs.isEmpty() || p.constants.size >= CONSTANT_SLOTS) return
            val pick = subs[rand(subs.size, Stream.MISC)]
            player { it.copy(future = it.future.filter { c -> c.uid != pick.uid }, constants = it.constants + pick) }
            emit(GameEvent.Info("Lovelace's Notes: ${name(pick)} is installed"))
        }

        // ---- the work loop -----------------------------------------------------------------------------------

        fun drain() {
            var guard = 0
            while (s.pending == null && s.queue.isNotEmpty() && !s.phase.over) {
                check(++guard < 200_000) { "Engine loop guard tripped" }
                val w = s.queue.first()
                s = s.copy(queue = s.queue.drop(1))
                process(w)
                checkEnd()
            }
            if (s.phase.over) s = s.copy(queue = emptyList(), pending = null)
        }

        private fun checkEnd() {
            if (s.phase.over) return
            if (p.hp <= 0) {
                s = s.copy(phase = Phase.LOST)
                emit(GameEvent.Info("You fall."))
            } else if (s.livingEnemies.isEmpty()) {
                s = s.copy(phase = Phase.WON)
                emit(GameEvent.Info("Victory."))
            }
        }

        private fun process(w: Work) {
            when (w) {
                is Work.Fx -> if (w.effects.isNotEmpty()) {
                    val rest = w.effects.drop(1)
                    if (rest.isNotEmpty()) pushFront(Work.Fx(rest, w.ctx))
                    resolve(w.effects.first(), w.ctx)
                }

                is Work.FinishPlay -> finishPlay(w)
                is Work.ResolveIntent -> resolveIntent(w.trackId)
                is Work.Step -> step(w.step)
            }
        }

        // ---- player actions ----------------------------------------------------------------------------------

        fun playCard(a: PlayCard): String? {
            val inst = p.present.firstOrNull { it.uid == a.cardUid } ?: return "That card isn't in your Present"
            val def = content.card(inst.defId)
            if (p.cardsPlayedThisTurn >= PLAY_CAP) return "You can't play more than $PLAY_CAP cards in a turn"
            if (def.unplayable) return "${def.name} can't be played"
            if (inst.blank) return "${def.name} is Blank: its thread was cut"
            val faces = faces(inst)
            if (a.face !in faces.indices) return if (faces.size == 1) "${def.name} has only one face" else "Choose face A or B"
            val face = faces[a.face]
            if (def.requiresParadox > p.paradox) {
                return "${def.name} needs ${def.requiresParadox} Paradox (you have ${p.paradox})"
            }
            val needs = needs(face)

            var target = a.target
            if (needs.enemy) {
                if (target == null && s.livingEnemies.size == 1) target = s.livingEnemies.first().uid
                val t = target?.let { s.enemy(it) } ?: return "Choose an enemy target"
                if (!t.alive) return "That enemy is already defeated"
            }

            // Track and card targets: required when there is a real choice, automatic when there is one option,
            // and the effect simply fizzles when there is none (a Delay with only Fixed intents on the Track).
            var trackTarget = a.trackTarget
            if (needs.track != TrackNeed.NONE) {
                val candidates = trackCandidates(s, needs.track)
                if (trackTarget == null) {
                    if (candidates.size == 1 || needs.track == TrackNeed.FORKED_INTENT) {
                        trackTarget = candidates.firstOrNull()?.id
                    } else if (candidates.size > 1) {
                        return if (needs.track == TrackNeed.DELAYABLE) "Choose a Track item to Delay" else "Choose an enemy intent"
                    }
                } else if (candidates.none { it.id == trackTarget }) {
                    val item = s.trackItem(trackTarget) ?: return "No such Track item"
                    return when {
                        item.fixed -> "${item.label} is Fixed"
                        needs.track == TrackNeed.FORKED_INTENT -> "${item.label} isn't a Forked intent"
                        else -> "Choose an enemy intent"
                    }
                }
            }

            var cardTarget = a.cardTarget
            if (needs.card) {
                val others = p.present.filter { it.uid != inst.uid }
                if (cardTarget == null) {
                    if (others.size == 1) {
                        cardTarget = others.first().uid
                    } else if (others.size > 1) {
                        return "Choose another card in your Present"
                    }
                } else if (others.none { it.uid == cardTarget }) {
                    return "Choose another card in your Present"
                }
            }

            if (face.type == CardType.CONSTANT && a.replaceConstantUid != null &&
                p.constants.none { it.uid == a.replaceConstantUid }
            ) {
                return "No such Constant to replace"
            }

            val cost = effectiveCost(s, inst)
            val pay = payment(s, inst, a.pay, a.allowBorrow)
            if (pay == null) {
                val couldBorrow = !a.allowBorrow && payment(s, inst, a.pay, true) != null
                return "Not enough energy for ${def.name} (cost $cost)" + if (couldBorrow) ". Borrow to play it." else ""
            }

            // Commit. The first Borrow of the turn also charges interest, in the color borrowed.
            val hadGlitch = p.status(StatusType.GLITCH) > 0
            val interest = if (pay.borrowed > 0 && p.borrowedThisTurn == 0) BORROW_INTEREST else 0
            val interestColor = pay.borrow.keys.firstOrNull()
            player { pl ->
                val debt = pl.debt.plusAll(pay.borrow)
                pl.copy(
                    energy = pl.energy.minusAll(pay.spend),
                    debt = if (interest > 0 && interestColor != null) debt.plusOne(interestColor, interest) else debt,
                    borrowedThisTurn = pl.borrowedThisTurn + pay.borrowed,
                    statuses = if (hadGlitch) pl.statuses.dec(StatusType.GLITCH) else pl.statuses,
                    present = pl.present.filter { it.uid != inst.uid },
                    resolving = inst,
                )
            }
            if (pay.borrowed > 0) emit(GameEvent.Borrowed(pay.borrowed, interest))

            if (face.type == CardType.SKILL && p.skillsPlayedThisTurn >= 1) breakVows(VowRule.MAX_ONE_SKILL_PER_TURN)
            if (pay.borrowed > 0) breakVows(VowRule.NO_BORROW_OR_DELAY)
            if (containsDelay(face.effects)) breakVows(VowRule.NO_BORROW_OR_DELAY)

            val prevType = p.lastPlayedType
            player {
                it.copy(
                    cardsPlayedThisTurn = it.cardsPlayedThisTurn + 1,
                    skillsPlayedThisTurn = it.skillsPlayedThisTurn + if (face.type == CardType.SKILL) 1 else 0,
                    lastPlayedType = face.type,
                    lastCardDamage = summaryDamage(face.effects),
                    lastCardBlock = summaryBlock(face.effects),
                )
            }
            emit(GameEvent.CardPlayed(def.id, def.name, pay.attuned, pay.borrowed))

            val ctx = FxCtx(
                sourceUid = inst.uid,
                sourceDefId = def.id,
                faction = def.faction,
                attuned = pay.attuned,
                known = inst.known,
                prevType = prevType,
                targetEnemy = target,
                trackTarget = trackTarget,
                cardTarget = cardTarget,
                recallUids = a.recallUids,
                shiftPairs = a.shiftPairs,
                iterations = inst.iterations,
                attack = face.type == CardType.ATTACK,
            )
            pushFront(
                Work.Fx(face.effects, ctx),
                Work.FinishPlay(inst.uid, def.eraseAfterPlay || inst.bleed, face.type == CardType.CONSTANT, a.replaceConstantUid),
            )
            return null
        }

        fun useSignature(a: UseSignature): String? {
            if (p.signatureUsed) return "You already used your Signature Action this turn"
            val op = content.operative(p.operativeId)
            when (op.signature) {
                Signature.STRIKE -> {
                    var target = a.target
                    if (target == null && s.livingEnemies.size == 1) target = s.livingEnemies.first().uid
                    val t = target?.let { s.enemy(it) }?.takeIf { it.alive } ?: return "Choose an enemy to Strike"
                    val bonus = p.constants.sumOf { constantSpec(it)?.strikeDamage ?: 0 }
                    val block = p.constants.sumOf { constantSpec(it)?.strikeBlock ?: 0 }
                    val effects = buildList {
                        add(Effect.Damage(Amount(STRIKE_DAMAGE + bonus)))
                        if (block > 0) add(Effect.Block(Amount(block)))
                    }
                    pushFront(Work.Fx(effects, FxCtx(faction = op.faction, targetEnemy = t.uid)))
                }

                Signature.FORECAST -> pushFront(Work.Fx(listOf(Effect.Foresee(2)), FxCtx(faction = op.faction)))
                Signature.SHIFT -> {
                    val pair = a.shiftPair ?: return "Choose a card in your Present and one in your Other Hand to Shift"
                    validateShift(pair)?.let { return it }
                    pushFront(Work.Fx(listOf(Effect.Shift(1)), FxCtx(faction = op.faction, shiftPairs = listOf(pair))))
                }
            }
            player { it.copy(signatureUsed = true) }
            emit(GameEvent.SignatureUsed(op.signature.label))
            return null
        }

        private fun validateShift(pair: ShiftPair): String? {
            if (p.present.none { it.uid == pair.presentUid }) return "That card isn't in your Present"
            val op = content.operative(p.operativeId)
            if (op.otherHandSize > 0) {
                if (p.otherHand.none { it.uid == pair.otherUid }) return "That card isn't in your Other Hand"
            } else if (p.future.isEmpty() && p.past.isEmpty()) {
                return "There is nothing to Shift with"
            }
            return null
        }

        fun endTurn(): String? {
            val dusk = if (p.constantsSilenced) {
                emptyList()
            } else {
                p.constants.mapNotNull { c ->
                    constantSpec(c)?.dusk?.takeIf { it.isNotEmpty() }?.let { Work.Fx(it, triggerCtx(c)) }
                }
            }
            emit(GameEvent.Info("You end your turn"))
            pushFront(dusk + listOf(Work.Step(TurnStep.DUSK_REST), Work.Step(TurnStep.ENEMY_PHASE)))
            return null
        }

        fun resolveForesee(a: ResolveForesee): String? {
            val pending = s.pending ?: return "There is nothing to Foresee"
            val revealed = pending.uids
            if (pending.tutor) {
                if (a.take == null || a.take !in revealed) return "Choose one revealed card to take"
            } else if (a.take != null) {
                return "This Foresee can't take a card"
            }
            val rest = revealed.filter { it != a.take }
            val placed = a.top + a.bottom
            if (placed.size != rest.size || placed.toSet() != rest.toSet()) {
                return "Put every revealed card on top or on the bottom, exactly once"
            }
            val byUid = p.future.filter { it.uid in revealed }.associateBy { it.uid }
            val remaining = p.future.filter { it.uid !in revealed }
            val newFuture = a.top.map { byUid.getValue(it) } + remaining + a.bottom.map { byUid.getValue(it) }
            var present = p.present
            var past = p.past
            a.take?.let { uid ->
                val c = byUid.getValue(uid)
                if (present.size < HAND_LIMIT) present = present + c else past = past + c
                emit(GameEvent.Info("You take ${name(c)} into your Present"))
            }
            player { it.copy(future = newFuture, present = present, past = past) }
            s = s.copy(pending = null, phase = Phase.PLAYER_TURN)
            return null
        }

        private fun finishPlay(w: Work.FinishPlay) {
            val inst = p.resolving ?: return
            val landed = inst.copy(iterations = inst.iterations + 1, costDelta = 0, retainThisTurn = false)
            player { it.copy(resolving = null) }
            when {
                w.asConstant -> install(landed, w.replaceUid)
                w.toErased -> player { it.copy(erased = it.erased + landed) }
                else -> player { it.copy(past = it.past + landed) }
            }
            checkUnravel()
        }

        private fun install(inst: CardInst, replaceUid: Int?) {
            if (p.constants.size >= CONSTANT_SLOTS) {
                val victim = p.constants.firstOrNull { it.uid == replaceUid } ?: p.constants.first()
                player { it.copy(constants = it.constants - victim, past = it.past + victim) }
                emit(GameEvent.Info("${name(victim)} is replaced"))
            }
            player { it.copy(constants = it.constants + inst) }
            emit(GameEvent.Info("You install ${name(inst)}"))
        }

        private fun breakVows(rule: VowRule) {
            val broken = p.constants.filter { constantSpec(it)?.vow == rule }
            for (v in broken) {
                player { it.copy(constants = it.constants - v, erased = it.erased + v) }
                emit(GameEvent.VowBroken(name(v)))
                loseHp(PENANCE, "Penance")
            }
        }

        private fun triggerCtx(c: CardInst) =
            FxCtx(sourceUid = c.uid, sourceDefId = c.defId, faction = content.card(c.defId).faction)

        // ---- effects -------------------------------------------------------------------------------------------

        private fun resolve(e: Effect, ctx: FxCtx) {
            when (e) {
                is Effect.Damage -> damage(e, ctx)
                is Effect.Block -> gainBlock(amount(e.amount, ctx, null))
                is Effect.GainPlate -> addPlayerStatus(StatusType.PLATE, e.n)
                is Effect.Draw -> draw(e.n)
                is Effect.GainEnergy -> {
                    val n = amount(e.amount, ctx, null)
                    if (n > 0) {
                        player { it.copy(energy = it.energy.plusOne(e.color, n)) }
                        emit(GameEvent.Info("You gain $n ${e.color.name.lowercase().replaceFirstChar { it.uppercase() }}"))
                    }
                }

                is Effect.Foresee -> foresee(e.n, e.tutor)
                is Effect.Recall -> recall(e.n, ctx)
                is Effect.Delay -> delay(e.n, ctx)
                Effect.RemoveIntent -> removeIntent(ctx)
                Effect.Observe -> observe(ctx)
                is Effect.ApplyStatus -> applyStatus(e, ctx)
                is Effect.RestoreLastPhaseLoss -> heal(min(e.max, p.hpLostLastEnemyPhase))
                is Effect.Shift -> shift(e.n, ctx)
                Effect.ForkCard -> ctx.cardTarget?.let { grantFork(it, drawIfForked = true) }
                is Effect.BleedThrough -> bleed(e.n, ctx)
                is Effect.GainParadox -> changeParadox(e.n)
                is Effect.SpendParadox -> changeParadox(-e.n)
                Effect.GrantRetain -> updatePresent(ctx.cardTarget) { it.copy(retainThisTurn = true) }
                is Effect.ReduceCostThisTurn -> updatePresent(ctx.cardTarget) { it.copy(costDelta = it.costDelta - e.n) }
                is Effect.LoseHp -> loseHp(e.n, "card")
                is Effect.When -> {
                    val branch = if (cond(e.cond, ctx, null)) e.then else e.otherwise
                    if (branch.isNotEmpty()) pushFront(Work.Fx(branch, ctx))
                }

                is Effect.Schedule -> {
                    val item = TrackItem(
                        id = newUid(),
                        kind = TrackKind.SCHEDULED,
                        countdown = e.countdown,
                        order = newOrder(),
                        label = RulesText.effects(e.effects).removeSuffix("."),
                        effects = e.effects,
                        ctx = ctx,
                    )
                    s = s.copy(track = s.track + item)
                    emit(GameEvent.Scheduled(item.label, item.countdown))
                }
            }
        }

        fun amount(a: Amount, ctx: FxCtx, target: EnemyState?): Int {
            var n = a.base
            for (b in a.bonuses) {
                n += when (b) {
                    is Bonus.If -> if (cond(b.cond, ctx, target)) b.plus else 0
                    is Bonus.Per -> min(b.max, b.each * counter(b.counter, ctx))
                }
            }
            return n
        }

        private fun counter(c: Counter, ctx: FxCtx): Int = when (c) {
            Counter.PAST_SIZE -> p.past.size
            Counter.PARADOX -> p.paradox
            Counter.KNOWN_IN_PRESENT -> p.present.count { it.known }
            Counter.CONSTANTS -> p.constants.size
            Counter.ITERATIONS -> ctx.iterations
        }

        fun cond(c: Cond, ctx: FxCtx, target: EnemyState?): Boolean = when (c) {
            Cond.Attuned -> ctx.attuned
            is Cond.Remember -> p.past.count { c.kind.matches(typeOf(it)) } >= c.n
            is Cond.Litany -> ctx.prevType?.let { c.kind.matches(it) } ?: false
            Cond.Calculated -> ctx.known
            Cond.VowKept -> p.constants.any { constantSpec(it)?.kind == ConstantKind.VOW }
            is Cond.TargetFaction -> target?.let { content.enemy(it.defId).faction in c.factions } ?: false
            is Cond.HpBelowPct -> p.hp * 100 < p.maxHp * c.pct
            is Cond.ConstantsAtLeast -> p.constants.size >= c.n
            Cond.ShiftedThisTurn -> p.shiftedThisTurn
            is Cond.ParadoxAtLeast -> p.paradox >= c.n
        }

        private fun damage(e: Effect.Damage, ctx: FxCtx) {
            val per = s.mods.attackPerParadox
            val edge = if (ctx.attack && per > 0) p.paradox / per else 0
            fun value(t: EnemyState) = amount(e.amount, ctx, t) + edge
            when (e.target) {
                Tgt.CHOSEN_ENEMY -> repeat(e.hits) {
                    val t = ctx.targetEnemy?.let { s.enemy(it) }?.takeIf { it.alive } ?: return
                    hitEnemy(t, value(t))
                }

                Tgt.RANDOM_ENEMY -> repeat(e.hits) {
                    val alive = s.livingEnemies
                    if (alive.isEmpty()) return
                    val t = alive[rand(alive.size, Stream.MISC)]
                    hitEnemy(t, value(t))
                }

                Tgt.WEAKEST_ENEMY -> repeat(e.hits) {
                    val t = s.livingEnemies.minByOrNull { it.hp } ?: return
                    hitEnemy(t, value(t))
                }

                Tgt.ALL_ENEMIES -> for (t0 in s.livingEnemies) {
                    repeat(e.hits) {
                        val t = s.enemy(t0.uid)?.takeIf { it.alive } ?: return@repeat
                        hitEnemy(t, value(t))
                    }
                }
            }
        }

        private fun hitEnemy(target: EnemyState, raw: Int) {
            var d = (raw + p.status(StatusType.MIGHT)).toDouble()
            if (p.status(StatusType.WEAK) > 0) d *= 0.75
            if (target.status(StatusType.EXPOSED) > 0) d *= 1.5
            dealToEnemy(target.uid, max(0, floor(d + 1e-9).toInt()))
        }

        private fun dealToEnemy(uid: Int, dmg: Int) {
            val e = s.enemy(uid)?.takeIf { it.alive } ?: return
            val blocked = min(e.block, dmg)
            val through = dmg - blocked
            val statuses = if (through > 0 && e.status(StatusType.PLATE) > 0) e.statuses.dec(StatusType.PLATE) else e.statuses
            enemyUpdate(uid) { it.copy(block = it.block - blocked, hp = it.hp - through, statuses = statuses) }
            emit(GameEvent.DamageToEnemy(uid, enemyName(e), through, blocked))
            if (e.hp - through <= 0) kill(uid)
        }

        private fun loseEnemyHp(uid: Int, n: Int) {
            val e = s.enemy(uid)?.takeIf { it.alive } ?: return
            enemyUpdate(uid) { it.copy(hp = it.hp - n) }
            emit(GameEvent.Info("${enemyName(e)} loses $n HP"))
            if (e.hp - n <= 0) kill(uid)
        }

        private fun kill(uid: Int) {
            val e = s.enemy(uid) ?: return
            enemyUpdate(uid) { it.copy(alive = false, hp = 0, block = 0) }
            s = s.copy(track = s.track.filter { it.enemyUid != uid })
            emit(GameEvent.EnemyDied(uid, enemyName(e)))
        }

        private fun gainBlock(n: Int) {
            if (n <= 0) return
            player { it.copy(block = it.block + n) }
            emit(GameEvent.BlockGained(n))
        }

        private fun addPlayerStatus(type: StatusType, n: Int) {
            if (n <= 0) return
            player { it.copy(statuses = it.statuses.plusOne(type, n)) }
            emit(GameEvent.Info("You gain ${type.label} $n"))
        }

        fun loseHp(n: Int, reason: String) {
            if (n <= 0) return
            player { it.copy(hp = it.hp - n) }
            emit(GameEvent.HpLost(n, reason))
        }

        private fun heal(n: Int) {
            val amount = min(n, p.maxHp - p.hp)
            if (amount <= 0) return
            player { it.copy(hp = it.hp + amount) }
            emit(GameEvent.Healed(amount))
        }

        fun draw(n: Int) {
            repeat(n) {
                if (p.future.isEmpty() && !reshuffle()) return
                val card = rollMisprint(p.future.first())
                player { it.copy(future = it.future.drop(1)) }
                if (p.present.size >= HAND_LIMIT) {
                    player { it.copy(past = it.past + card) }
                    emit(GameEvent.Info("Your Present is full: ${name(card)} goes to the Past"))
                } else {
                    player { it.copy(present = it.present + card) }
                }
            }
        }

        /** Reshuffles the Past into the Future. Known marks clear, and Blank cards are restored: the thread re-knits. */
        private fun reshuffle(): Boolean {
            if (p.past.isEmpty()) return false
            val restored = p.past.count { it.blank }
            val (shuffled, rng) = s.rng.shuffled(p.past.map { it.copy(known = false, blank = false) }, Stream.SHUFFLE)
            s = s.copy(rng = rng)
            player { it.copy(future = it.future + shuffled, past = emptyList()) }
            emit(GameEvent.Info("You reshuffle your Past into your Future"))
            if (restored > 0) emit(GameEvent.Info("$restored Blank card${if (restored > 1) "s are" else " is"} restored"))
            return true
        }

        private fun rollMisprint(inst: CardInst): CardInst {
            val faces = content.card(inst.defId).misprintFaces ?: return inst
            return inst.copy(face = faces[rand(faces.size, Stream.MISC)])
        }

        private fun foresee(n: Int, tutor: Boolean) {
            val revealed = p.future.take(n)
            if (revealed.isEmpty()) return
            val uids = revealed.map { it.uid }
            player { it.copy(future = it.future.map { c -> if (c.uid in uids) c.copy(known = true) else c }) }
            s = s.copy(pending = PendingForesee(uids, tutor), phase = Phase.AWAIT_FORESEE)
        }

        private fun recall(n: Int, ctx: FxCtx) {
            val chosen = ctx.recallUids.filter { uid -> p.past.any { it.uid == uid } }.distinct().take(n).toMutableList()
            for (c in p.past.asReversed()) {
                if (chosen.size >= n) break
                if (c.uid !in chosen) chosen += c.uid
            }
            for (uid in chosen) {
                if (p.present.size >= HAND_LIMIT) break
                val c = p.past.first { it.uid == uid }
                player { it.copy(past = it.past - c, present = it.present + c) }
                emit(GameEvent.Info("You Recall ${name(c)}"))
            }
        }

        private fun delay(n: Int, ctx: FxCtx) {
            val item = ctx.trackTarget?.let { s.trackItem(it) } ?: return
            if (item.kind == TrackKind.INTENT && item.fixed) return
            val isIntent = item.kind == TrackKind.INTENT
            val free = isIntent && s.mods.freeFirstDelay && !p.freeDelayUsed
            if (free) {
                player { it.copy(freeDelayUsed = true) }
                emit(GameEvent.Info("Clockwork Sparrow: this Delay adds no Pressure"))
            }
            val pressure = if (isIntent && !free) item.pressure + n else item.pressure
            val updated = item.copy(countdown = item.countdown + n, pressure = pressure)
            replaceTrack(updated)
            emit(GameEvent.Delayed(fullLabel(item), n, updated.pressure, isIntent))
        }

        private fun removeIntent(ctx: FxCtx) {
            val item = ctx.trackTarget?.let { s.trackItem(it) } ?: return
            if (item.kind != TrackKind.INTENT || item.fixed) return
            s = s.copy(track = s.track.filter { it.id != item.id })
            emit(GameEvent.IntentRemoved(fullLabel(item)))
        }

        private fun observe(ctx: FxCtx) {
            val item = (ctx.trackTarget?.let { s.trackItem(it) } ?: s.track.firstOrNull { it.forked && it.collapsed == null })
                ?.takeIf { it.forked && it.collapsed == null } ?: return
            val outcome = rand(2, Stream.ENEMY)
            replaceTrack(item.copy(collapsed = outcome))
            val label = if (outcome == 0) item.label else item.altLabel ?: item.label
            emit(GameEvent.Info("Observed: the Forked intent will be $label"))
        }

        private fun fullLabel(item: TrackItem) = if (item.forked) "${item.label} / ${item.altLabel}" else item.label

        private fun replaceTrack(item: TrackItem) {
            s = s.copy(track = s.track.map { if (it.id == item.id) item else it })
        }

        private fun applyStatus(e: Effect.ApplyStatus, ctx: FxCtx) {
            val targets = when (e.target) {
                Tgt.CHOSEN_ENEMY -> listOfNotNull(ctx.targetEnemy?.let { s.enemy(it) }?.takeIf { it.alive })
                Tgt.ALL_ENEMIES -> s.livingEnemies
                Tgt.WEAKEST_ENEMY -> listOfNotNull(s.livingEnemies.minByOrNull { it.hp })
                Tgt.RANDOM_ENEMY -> s.livingEnemies.let { if (it.isEmpty()) emptyList() else listOf(it[rand(it.size, Stream.MISC)]) }
            }
            for (t in targets) {
                enemyUpdate(t.uid) { it.copy(statuses = it.statuses.plusOne(e.status, e.n)) }
                emit(GameEvent.Info("${enemyName(t)} gains ${e.status.label} ${e.n}"))
            }
        }

        private fun shift(n: Int, ctx: FxCtx) {
            val op = content.operative(p.operativeId)
            var done = 0
            for (pair in ctx.shiftPairs) {
                if (done >= n) break
                val out = p.present.firstOrNull { it.uid == pair.presentUid } ?: continue
                val incoming: CardInst
                if (op.otherHandSize > 0) {
                    val other = p.otherHand.firstOrNull { it.uid == pair.otherUid } ?: continue
                    incoming = other
                    player { pl ->
                        pl.copy(
                            present = pl.present.map { if (it.uid == out.uid) other else it },
                            otherHand = pl.otherHand.map { if (it.uid == other.uid) out else it },
                        )
                    }
                } else {
                    if (p.future.isEmpty() && !reshuffle()) continue
                    val top = rollMisprint(p.future.first())
                    incoming = top
                    player { pl ->
                        pl.copy(
                            present = pl.present.map { if (it.uid == out.uid) top else it },
                            future = listOf(out) + pl.future.drop(1),
                        )
                    }
                }
                done++
                player { it.copy(shiftedThisTurn = true) }
                emit(GameEvent.Info("Shift: ${name(out)} ⇄ ${name(incoming)}"))
                onShiftIn(incoming.uid)
            }
        }

        private fun onShiftIn(uid: Int) {
            if (p.constantsSilenced) return
            for (c in p.constants) {
                val rule = constantSpec(c)?.onShiftIn ?: continue
                if (rule.forkShifted) grantFork(uid, drawIfForked = false)
                if (rule.firstShiftDiscount > 0 && !p.shiftDiscountUsed) {
                    updatePresent(uid) { it.copy(costDelta = it.costDelta - rule.firstShiftDiscount) }
                    player { it.copy(shiftDiscountUsed = true) }
                }
            }
        }

        private fun grantFork(uid: Int, drawIfForked: Boolean) {
            val c = p.present.firstOrNull { it.uid == uid } ?: return
            val def = content.card(c.defId)
            if (def.forkFaces != null || c.granted != null || c.face != null || def.unplayable) {
                if (drawIfForked) draw(1)
                return
            }
            val faces = content.misprintFaces(def.type, def.cost.total)
            val f = faces[rand(faces.size, Stream.MISC)]
            updatePresent(uid) { it.copy(granted = f) }
            emit(GameEvent.Info("${def.name} Forks: it gains a Misprint face (${RulesText.effects(f.effects)})"))
        }

        private fun bleed(n: Int, ctx: FxCtx) {
            val pool = content.bleedPool(ctx.faction)
            if (pool.isEmpty()) return
            repeat(n) {
                val def = pool[rand(pool.size, Stream.MISC)]
                val inst = rollMisprint(CardInst(newUid(), def.id, bleed = true, costDelta = -1))
                if (p.present.size < HAND_LIMIT) {
                    player { it.copy(present = it.present + inst) }
                } else {
                    player { it.copy(past = it.past + inst) }
                }
                emit(GameEvent.Info("Bleed-through: ${def.name} (${def.faction.label})"))
            }
        }

        /** Paradox is a 0..10 meter; reaching 10 Unravels immediately (docs/05). */
        private fun changeParadox(delta: Int) {
            val from = p.paradox
            val to = (from + delta).coerceIn(0, UNRAVEL_AT)
            if (from == to) return
            player { it.copy(paradox = to) }
            emit(GameEvent.ParadoxChanged(from, to))
            checkUnravel()
        }

        private fun updatePresent(uid: Int?, f: (CardInst) -> CardInst) {
            if (uid == null) return
            player { pl -> pl.copy(present = pl.present.map { if (it.uid == uid) f(it) else it }) }
        }

        private fun checkUnravel() {
            if (p.paradox < UNRAVEL_AT || s.phase.over) return
            emit(GameEvent.Unravel)
            val misprinted = p.present.map { c ->
                val def = content.card(c.defId)
                if (def.unplayable) {
                    c
                } else {
                    val faces = content.misprintFaces(typeOf(c), def.cost.total)
                    c.copy(face = faces[rand(faces.size, Stream.MISC)], granted = null)
                }
            }
            player { it.copy(present = misprinted) }
            val def = content.enemy(LOOSE_END)
            val spawned = newEnemy(def, newUid(), s.mods)
            s = s.copy(enemies = s.enemies + spawned)
            rearm(predictive = def.ai.predictive, onlyUid = spawned.uid)
            val from = p.paradox
            player { it.copy(paradox = UNRAVEL_RESET) }
            emit(GameEvent.ParadoxChanged(from, UNRAVEL_RESET))
        }

        // ---- turn steps ---------------------------------------------------------------------------------------

        private fun step(step: TurnStep) {
            when (step) {
                TurnStep.DUSK_REST -> duskRest()
                TurnStep.ENEMY_PHASE -> enemyPhase()
                TurnStep.ENEMY_PHASE_END -> enemyPhaseEnd()
                TurnStep.END_ROUND -> endRound()
                TurnStep.DAWN -> dawn()
                TurnStep.DAWN_PREDICT -> {
                    rearm(predictive = true)
                    checkUnravel()
                }
            }
        }

        private fun duskRest() {
            val keep = p.present.filter { content.card(it.defId).retain || it.retainThisTurn }
            val hazards = p.present.filter { content.card(it.defId).type == CardType.HAZARD }
            val discard = p.present.filter { it !in keep && it !in hazards }
            player {
                it.copy(
                    present = keep.map { c -> c.copy(retainThisTurn = false) },
                    past = it.past + discard,
                    erased = it.erased + hazards,
                )
            }
            evaporate()
            val burn = p.status(StatusType.BURN)
            if (burn > 0) {
                loseHp(burn, "Burn")
                player { it.copy(statuses = it.statuses.dec(StatusType.BURN)) }
            }
            val plate = p.status(StatusType.PLATE)
            if (plate > 0) gainBlock(plate)
        }

        /** Unspent energy carries over, up to the Reservoir cap. Neutral evaporates first, then the largest pool. */
        private fun evaporate() {
            var energy = p.energy.toMutableMap()
            val cap = s.mods.reservoirCap
            var excess = energy.values.sum() - cap
            if (excess <= 0) return
            val lost = excess
            val neutral = min(excess, energy[EnergyColor.NEUTRAL] ?: 0)
            energy[EnergyColor.NEUTRAL] = (energy[EnergyColor.NEUTRAL] ?: 0) - neutral
            excess -= neutral
            while (excess > 0) {
                val largest = energy.filter { it.value > 0 }.maxByOrNull { it.value }?.key ?: break
                energy[largest] = energy.getValue(largest) - 1
                excess--
            }
            energy = energy.filterValues { it > 0 }.toMutableMap()
            player { it.copy(energy = energy) }
            emit(GameEvent.Info("$lost energy evaporates (Reservoir cap $cap)"))
        }

        private fun enemyPhase() {
            player { it.copy(hpLostThisEnemyPhase = 0) }
            s = s.copy(
                enemies = s.enemies.map { it.copy(block = 0) },
                track = s.track.map { it.copy(countdown = it.countdown - 1) },
            )
            val due = s.track.filter { it.countdown <= 0 }
            val scheduled = due.filter { it.kind == TrackKind.SCHEDULED }.sortedBy { it.order }
            val position = s.enemies.mapIndexed { i, e -> e.uid to i }.toMap()
            val intents = due.filter { it.kind == TrackKind.INTENT }
                .sortedWith(compareBy({ position[it.enemyUid] ?: Int.MAX_VALUE }, { it.order }))
            s = s.copy(track = s.track.filter { it !in scheduled })
            val work = scheduled.map { Work.Fx(it.effects, it.ctx ?: FxCtx()) } +
                intents.map { Work.ResolveIntent(it.id) } +
                Work.Step(TurnStep.ENEMY_PHASE_END)
            pushFront(work)
        }

        private fun resolveIntent(id: Int) {
            val item = s.trackItem(id) ?: return
            s = s.copy(track = s.track.filter { it.id != id })
            val enemy = item.enemyUid?.let { s.enemy(it) }?.takeIf { it.alive } ?: return
            val def = content.enemy(enemy.defId)
            var actions = item.actions
            var label = item.label
            if (item.alt != null) {
                val choice = item.collapsed ?: rand(2, Stream.ENEMY)
                if (choice == 1) {
                    actions = item.alt
                    label = item.altLabel ?: label
                }
            }
            emit(GameEvent.IntentResolved(def.name, label, item.pressure))
            val mult = 1.0 + 0.25 * item.pressure
            for (act in actions) {
                val cur = s.enemy(enemy.uid)?.takeIf { it.alive } ?: return
                when (act) {
                    is EnemyAction.Attack -> repeat(act.hits) {
                        val attacker = s.enemy(enemy.uid)?.takeIf { it.alive } ?: return
                        hitPlayer(attacker, act.damage, mult, def.name, item.fixed)
                        if (p.hp <= 0) return
                    }

                    is EnemyAction.AttackPer -> {
                        val base = act.per * cur.status(act.status)
                        if (base > 0) hitPlayer(cur, base, mult, def.name, item.fixed)
                        if (p.hp <= 0) return
                    }

                    is EnemyAction.Guard -> enemyUpdate(cur.uid) { it.copy(block = it.block + scaled(act.block, mult)) }
                    is EnemyAction.Buff -> enemyUpdate(cur.uid) { it.copy(statuses = it.statuses.plusOne(act.status, scaled(act.n, mult))) }
                    is EnemyAction.Debuff -> addPlayerStatus(act.status, scaled(act.n, mult))
                    is EnemyAction.BlankFuture -> repeat(scaled(act.n, mult)) { blankTopOfFuture() }
                    is EnemyAction.EraseFuture -> repeat(scaled(act.n, mult)) { eraseTopOfFuture() }
                    is EnemyAction.Disrupt -> {
                        val hadScheduled = s.track.any { it.kind == TrackKind.SCHEDULED }
                        s = s.copy(track = s.track.map { if (it.kind == TrackKind.SCHEDULED) it.copy(countdown = it.countdown + act.n) else it })
                        if (hadScheduled) emit(GameEvent.Info("Your Scheduled effects are Disrupted (+${act.n})"))
                    }

                    EnemyAction.SilenceConstants -> {
                        player { it.copy(silenceNextTurn = true) }
                        emit(GameEvent.Info("Snip: your Constants won't trigger next turn"))
                    }
                }
            }
        }

        private fun scaled(n: Int, mult: Double): Int = ceil(n * mult - 1e-9).toInt()

        private fun hitPlayer(enemy: EnemyState, base: Int, mult: Double, source: String, fixed: Boolean) {
            val dmg = perHitToPlayer(s, enemy, base, mult, fixed)
            val blocked = min(p.block, dmg)
            val through = dmg - blocked
            val statuses = if (through > 0 && p.status(StatusType.PLATE) > 0) p.statuses.dec(StatusType.PLATE) else p.statuses
            player {
                it.copy(
                    block = it.block - blocked,
                    hp = it.hp - through,
                    statuses = statuses,
                    hpLostThisEnemyPhase = it.hpLostThisEnemyPhase + through,
                )
            }
            emit(GameEvent.DamageToPlayer(through, blocked, source))
        }

        private fun blankTopOfFuture() {
            if (p.future.isEmpty() && !reshuffle()) return
            val top = p.future.first()
            player { it.copy(future = listOf(top.copy(blank = true)) + it.future.drop(1)) }
            emit(GameEvent.Info("Unpicked: the top card of your Future is now Blank"))
        }

        private fun eraseTopOfFuture() {
            if (p.future.isEmpty() && !reshuffle()) return
            val top = p.future.first()
            player { it.copy(future = it.future.drop(1), erased = it.erased + top) }
            emit(GameEvent.Info("Unpicked: ${name(top)} is Erased from your Future"))
        }

        private fun enemyPhaseEnd() {
            for (e in s.livingEnemies) {
                val burn = e.status(StatusType.BURN)
                if (burn > 0) {
                    loseEnemyHp(e.uid, burn)
                    enemyUpdate(e.uid) { it.copy(statuses = it.statuses.dec(StatusType.BURN)) }
                }
                val cur = s.enemy(e.uid) ?: continue
                val plate = cur.status(StatusType.PLATE)
                if (cur.alive && plate > 0) enemyUpdate(cur.uid) { it.copy(block = it.block + plate) }
            }
            rearm(predictive = false)
            player { it.copy(hpLostLastEnemyPhase = it.hpLostThisEnemyPhase) }
            pushFront(Work.Step(TurnStep.END_ROUND))
        }

        private fun endRound() {
            player { it.copy(statuses = it.statuses.dec(StatusType.WEAK).dec(StatusType.EXPOSED)) }
            s = s.copy(enemies = s.enemies.map { it.copy(statuses = it.statuses.dec(StatusType.WEAK).dec(StatusType.EXPOSED)) })
            val next = s.round + 1
            if (next > s.roundCap) {
                s = s.copy(phase = Phase.DRAW)
                emit(GameEvent.Info("The fight stalls (round cap ${s.roundCap})"))
                return
            }
            s = s.copy(round = next)
            pushFront(Work.Step(TurnStep.DAWN))
        }

        private fun dawn() {
            emit(GameEvent.RoundStarted(s.round))
            val op = content.operative(p.operativeId)
            val income = mutableMapOf(op.color to PRIMARY_INCOME, EnergyColor.NEUTRAL to NEUTRAL_INCOME)
            var owed = 0
            for ((color, debt) in p.debt) {
                val repay = min(debt, income[color] ?: 0)
                income[color] = (income[color] ?: 0) - repay
                owed += debt - repay
            }
            for (color in income.keys.toList()) {
                if (owed == 0) break
                val repay = min(owed, income.getValue(color))
                income[color] = income.getValue(color) - repay
                owed -= repay
            }
            player { pl ->
                pl.copy(
                    block = 0,
                    energy = pl.energy.plusAll(income),
                    debt = emptyMap(),
                    borrowedThisTurn = 0,
                    signatureUsed = false,
                    cardsPlayedThisTurn = 0,
                    skillsPlayedThisTurn = 0,
                    lastPlayedType = null,
                    shiftedThisTurn = false,
                    shiftDiscountUsed = false,
                    constantsSilenced = pl.silenceNextTurn,
                    silenceNextTurn = false,
                    present = pl.present.map { it.copy(costDelta = 0) },
                    otherHand = pl.otherHand.map { it.copy(costDelta = 0) },
                )
            }
            draw(DRAW_PER_TURN)
            refillOtherHand()
            val dawnTriggers = if (p.constantsSilenced) {
                emptyList()
            } else {
                p.constants.mapNotNull { c ->
                    constantSpec(c)?.dawn?.takeIf { it.isNotEmpty() }?.let { Work.Fx(it, triggerCtx(c)) }
                }
            }
            pushFront(dawnTriggers + Work.Step(TurnStep.DAWN_PREDICT))
        }

        private fun refillOtherHand() {
            val size = content.operative(p.operativeId).otherHandSize
            while (p.otherHand.size < size) {
                if (p.future.isEmpty() && !reshuffle()) return
                val card = rollMisprint(p.future.first())
                player { it.copy(future = it.future.drop(1), otherHand = it.otherHand + card) }
            }
        }

        /** Gives every living enemy (optionally just [onlyUid]) an intent for each empty slot. */
        fun rearm(predictive: Boolean, onlyUid: Int? = null) {
            for (e in s.livingEnemies) {
                if (onlyUid != null && e.uid != onlyUid) continue
                val def = content.enemy(e.defId)
                if (def.ai.predictive != predictive) continue
                for (slot in def.ai.slots) {
                    if (s.track.any { it.kind == TrackKind.INTENT && it.enemyUid == e.uid && it.slot == slot }) continue
                    val cur = s.enemy(e.uid) ?: continue
                    val view = dcbb.core.model.AiView(
                        round = s.round,
                        presentTypes = p.present.map { typeOf(it) },
                        lastCardDamage = p.lastCardDamage,
                        lastCardBlock = p.lastCardBlock,
                        selfStatuses = cur.statuses,
                    )
                    val (spec, aiState) = def.ai.next(slot, cur.aiState, view)
                    val slow = cur.status(StatusType.SLOW) > 0
                    val item = TrackItem(
                        id = newUid(),
                        kind = TrackKind.INTENT,
                        countdown = spec.countdown + if (slow) 1 else 0,
                        order = newOrder(),
                        label = spec.label,
                        enemyUid = e.uid,
                        slot = slot,
                        actions = spec.actions,
                        alt = spec.alt,
                        altLabel = spec.altLabel,
                        fixed = spec.fixed,
                    )
                    s = s.copy(track = s.track + item)
                    enemyUpdate(e.uid) {
                        it.copy(aiState = aiState, statuses = if (slow) it.statuses.dec(StatusType.SLOW) else it.statuses)
                    }
                    emit(GameEvent.IntentSet(def.name, if (item.forked) "${item.label} / ${item.altLabel}" else item.label, item.countdown))
                }
            }
        }

        private fun containsDelay(effects: List<Effect>): Boolean = effects.any { e ->
            when (e) {
                is Effect.Delay -> true
                is Effect.When -> containsDelay(e.then) || containsDelay(e.otherwise)
                is Effect.Schedule -> containsDelay(e.effects)
                else -> false
            }
        }

        private fun summaryDamage(effects: List<Effect>): Int = effects.sumOf { e ->
            when (e) {
                is Effect.Damage -> e.amount.base * e.hits
                is Effect.When -> summaryDamage(e.otherwise)
                else -> 0
            }
        }

        private fun summaryBlock(effects: List<Effect>): Int = effects.sumOf { e ->
            when (e) {
                is Effect.Block -> e.amount.base
                is Effect.When -> summaryBlock(e.otherwise)
                else -> 0
            }
        }
    }
}

// ---- small immutable map helpers ----

internal fun <K> Map<K, Int>.plusOne(key: K, n: Int): Map<K, Int> {
    val v = (this[key] ?: 0) + n
    return if (v > 0) this + (key to v) else this - key
}

internal fun <K> Map<K, Int>.plusAll(other: Map<K, Int>): Map<K, Int> =
    other.entries.fold(this) { acc, (k, v) -> acc.plusOne(k, v) }

internal fun <K> Map<K, Int>.minusAll(other: Map<K, Int>): Map<K, Int> =
    other.entries.fold(this) { acc, (k, v) -> acc.plusOne(k, -v) }

internal fun Map<StatusType, Int>.dec(type: StatusType): Map<StatusType, Int> =
    if ((this[type] ?: 0) > 0) plusOne(type, -1) else this
