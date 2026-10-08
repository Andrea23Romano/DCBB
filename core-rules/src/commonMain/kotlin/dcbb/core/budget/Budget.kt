package dcbb.core.budget

import dcbb.core.model.Amount
import dcbb.core.model.Bonus
import dcbb.core.model.CardDef
import dcbb.core.model.Cond
import dcbb.core.model.ConstantSpec
import dcbb.core.model.Counter
import dcbb.core.model.Effect
import dcbb.core.model.Face
import dcbb.core.model.Kind
import dcbb.core.model.StatusType
import dcbb.core.model.Tgt
import kotlin.math.pow

/**
 * The power budget from docs/08 ("Primitives and Point Costs", "Modifiers", "Budget Targets").
 * It is a sanity rail for authored and generated cards, not a balance oracle: simulation has the last word.
 */
object Budget {

    const val TOLERANCE = 0.10

    /** Misprint faces may sit further from the target than authored cards (docs/08 "Modifiers"). */
    const val MISPRINT_FACE_TOLERANCE = 0.25

    data class Report(val cardId: String, val points: Double, val target: Double, val exception: String? = null) {
        val ratio: Double get() = points / target
        val withinTolerance: Boolean get() = ratio in (1 - TOLERANCE - 1e-9)..(1 + TOLERANCE + 1e-9)

        /** Inside the window, or outside it with a documented exception. */
        val acceptable: Boolean get() = withinTolerance || exception != null
    }

    /** Expected values for scaling counters (docs/08 "Standard expected-value assumptions"). */
    fun expected(counter: Counter): Double = when (counter) {
        Counter.PAST_SIZE -> 8.0
        Counter.PARADOX -> 3.0
        Counter.KNOWN_IN_PRESENT -> 1.8
        Counter.CONSTANTS -> 1.0
        Counter.ITERATIONS -> 1.5 // Iterate: increment value x1.5 (expected extra plays)
    }

    private const val SHIFTS_PER_TURN = 1.5
    private const val HP_LOST_LAST_PHASE = 8.0
    private const val CONSTANT_FACTOR = 2.4

    fun baseBudget(totalCost: Int): Double = if (totalCost == 0) 6.0 else 12.0 * totalCost

    fun target(def: CardDef): Double = baseBudget(def.cost.total) * def.rarity.budgetMultiplier

    fun report(def: CardDef) = Report(def.id, points(def), target(def), def.budgetException)

    /** How likely each condition is to hold. The budget uses fixed difficulty factors; bots pass what they can see. */
    fun interface CondOdds {
        fun of(c: Cond): Double
    }

    val STANDARD = CondOdds { factor(it) }

    fun points(def: CardDef, odds: CondOdds = STANDARD): Double {
        var p = when {
            def.forkFaces != null -> def.forkFaces.maxOf { face(it, odds) } + 2.0
            def.misprintFaces != null -> def.misprintFaces.map { face(it, odds) }.average()
            else -> effects(def.effects, odds)
        }
        def.constant?.let { p += constant(it, odds) }
        if (def.retain) p += 2.0
        if (def.eraseAfterPlay) p -= 3.0
        return p
    }

    fun face(face: Face, odds: CondOdds = STANDARD): Double = effects(face.effects, odds)

    fun effects(effects: List<Effect>, odds: CondOdds = STANDARD): Double = effects.sumOf { effect(it, odds) }

    /** The share of a Constant's points that comes from buffing the Strike (worth nothing to other operatives). */
    fun strikePart(spec: ConstantSpec): Double = (spec.strikeDamage * 2.0 + spec.strikeBlock * 2.4) * CONSTANT_FACTOR

    fun constant(spec: ConstantSpec, odds: CondOdds = STANDARD): Double {
        var perTrigger = effects(spec.dawn, odds) + effects(spec.dusk, odds)
        perTrigger += spec.strikeDamage * 2.0 + spec.strikeBlock * 2.4
        spec.onShiftIn?.let { rule ->
            if (rule.forkShifted) perTrigger += 6.0 * SHIFTS_PER_TURN
            perTrigger += rule.firstShiftDiscount * 10.0
        }
        return perTrigger * CONSTANT_FACTOR - (spec.vow?.budgetCredit ?: 0.0)
    }

    fun effect(e: Effect, odds: CondOdds = STANDARD): Double = when (e) {
        is Effect.Damage -> amount(e.amount, odds) * e.hits * when (e.target) {
            Tgt.CHOSEN_ENEMY -> 2.0
            Tgt.WEAKEST_ENEMY -> 1.9
            Tgt.RANDOM_ENEMY -> 1.7
            Tgt.ALL_ENEMIES -> 3.0
        }

        is Effect.Block -> amount(e.amount, odds) * 2.4
        is Effect.GainPlate -> e.n * 5.0
        is Effect.Draw -> e.n * 6.0
        is Effect.GainEnergy -> amount(e.amount, odds) * 10.0
        is Effect.Foresee -> e.n * 2.0 + if (e.tutor) 8.0 else 0.0
        is Effect.Recall -> e.n * 7.0
        is Effect.Delay -> if (e.n >= 2) 18.0 + 8.0 * (e.n - 2) else 10.0 * e.n
        Effect.RemoveIntent -> 14.0
        Effect.Observe -> 4.0
        // All enemies: x1.5, the same ratio as damage (3 vs 2 per point).
        is Effect.ApplyStatus -> status(e.status, e.n) * if (e.target == Tgt.ALL_ENEMIES) 1.5 else 1.0
        is Effect.RestoreLastPhaseLoss -> minOf(e.max.toDouble(), HP_LOST_LAST_PHASE) * 2.0
        is Effect.Shift -> e.n * 4.0
        Effect.ForkCard -> 6.0
        is Effect.BleedThrough -> e.n * 8.0
        is Effect.GainParadox -> e.n * -2.0
        is Effect.SpendParadox -> e.n * -3.0
        Effect.GrantRetain -> 2.0
        is Effect.ReduceCostThisTurn -> e.n * 10.0
        is Effect.LoseHp -> e.n * -2.0
        is Effect.When -> {
            val base = effects(e.otherwise, odds)
            base + odds.of(e.cond) * (effects(e.then, odds) - base)
        }

        is Effect.Schedule -> effects(e.effects, odds) * 0.8.pow(e.countdown - 1)
    }

    private fun status(type: StatusType, n: Int): Double = when (type) {
        StatusType.WEAK -> 4.0 * n
        StatusType.EXPOSED -> 5.0 * n
        StatusType.BURN -> 3.0 * n
        StatusType.SLOW -> 6.0
        StatusType.MIGHT -> 8.0 * n
        StatusType.PLATE -> 5.0 * n
        StatusType.GLITCH -> 4.0 * n
    }

    /** Expected magnitude: base plus conditional bonuses (discounted) plus scaling bonuses (expected value). */
    fun amount(a: Amount, odds: CondOdds = STANDARD): Double = a.base + a.bonuses.sumOf { b ->
        when (b) {
            is Bonus.If -> b.plus * odds.of(b.cond)
            is Bonus.Per -> minOf(b.max.toDouble(), b.each * expected(b.counter))
        }
    }

    /** Condition multipliers: easy 0.8, medium 0.6, hard 0.4; the Attuned rider is 0.5. */
    fun factor(c: Cond): Double = when (c) {
        Cond.Attuned -> 0.5
        is Cond.Remember -> if (c.kind == Kind.ANY) {
            when {
                c.n <= 4 -> EASY
                c.n <= 8 -> MEDIUM
                else -> HARD
            }
        } else {
            if (c.n <= 3) MEDIUM else HARD
        }

        is Cond.Litany -> MEDIUM
        Cond.Calculated -> MEDIUM
        Cond.VowKept -> MEDIUM
        is Cond.ParadoxAtLeast -> if (c.n >= 7) HARD else MEDIUM
        is Cond.TargetFaction -> HARD
        is Cond.HpBelowPct -> if (c.pct >= 50) EASY else MEDIUM
        is Cond.ConstantsAtLeast -> if (c.n <= 1) EASY else MEDIUM
        Cond.ShiftedThisTurn -> MEDIUM
    }

    private const val EASY = 0.8
    private const val MEDIUM = 0.6
    private const val HARD = 0.4
}
