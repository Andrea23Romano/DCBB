package dcbb.core.model

/** Who an effect hits. CHOSEN_ENEMY uses the target picked when the card is played. */
enum class Tgt {
    CHOSEN_ENEMY,
    RANDOM_ENEMY,
    WEAKEST_ENEMY,
    ALL_ENEMIES,
}

/** Counters used by scaling bonuses ("+1 per card in your Past"). */
enum class Counter(val label: String) {
    PAST_SIZE("card in your Past"),
    PARADOX("Paradox"),
    KNOWN_IN_PRESENT("Known card in your Present"),
    CONSTANTS("Constant you have"),
    ITERATIONS("time you played it this combat"),
}

/** Conditions (docs/08 "Conditions"). */
sealed interface Cond {
    data object Attuned : Cond
    data class Remember(val kind: Kind, val n: Int) : Cond
    data class Litany(val kind: Kind) : Cond
    data object Calculated : Cond
    data object VowKept : Cond
    data class TargetFaction(val factions: Set<Faction>) : Cond
    data class HpBelowPct(val pct: Int) : Cond
    data class ConstantsAtLeast(val n: Int) : Cond
    data object ShiftedThisTurn : Cond
    data class ParadoxAtLeast(val n: Int) : Cond
}

sealed interface Bonus {
    /** +[plus] when [cond] holds. */
    data class If(val cond: Cond, val plus: Int) : Bonus

    /** +[each] per [counter], up to [max]. ITERATIONS is the Iterate keyword. */
    data class Per(val counter: Counter, val each: Int, val max: Int) : Bonus
}

data class Amount(val base: Int, val bonuses: List<Bonus> = emptyList()) {
    constructor(base: Int, vararg bonuses: Bonus) : this(base, bonuses.toList())
}

/** Card and trigger effects (docs/08 primitives). Rules text is rendered from these. */
sealed interface Effect {
    data class Damage(val amount: Amount, val target: Tgt = Tgt.CHOSEN_ENEMY, val hits: Int = 1) : Effect
    data class Block(val amount: Amount) : Effect
    data class GainPlate(val n: Int) : Effect
    data class Draw(val n: Int) : Effect
    data class GainEnergy(val color: EnergyColor, val amount: Amount) : Effect
    data class Foresee(val n: Int, val tutor: Boolean = false) : Effect
    data class Recall(val n: Int) : Effect
    data class Delay(val n: Int) : Effect
    data object RemoveIntent : Effect
    data object Observe : Effect
    data class ApplyStatus(val status: StatusType, val n: Int, val target: Tgt = Tgt.CHOSEN_ENEMY) : Effect
    data class RestoreLastPhaseLoss(val max: Int) : Effect
    data class Shift(val n: Int) : Effect
    data object ForkCard : Effect
    data class BleedThrough(val n: Int) : Effect
    data class GainParadox(val n: Int) : Effect
    data class SpendParadox(val n: Int) : Effect
    data object GrantRetain : Effect
    data class ReduceCostThisTurn(val n: Int) : Effect
    data class LoseHp(val n: Int) : Effect
    data class When(val cond: Cond, val then: List<Effect>, val otherwise: List<Effect> = emptyList()) : Effect
    data class Schedule(val countdown: Int, val effects: List<Effect>) : Effect
}

/** Small builders so content reads close to the card text. */
object Fx {
    fun dmg(n: Int, vararg bonuses: Bonus) = Effect.Damage(Amount(n, *bonuses))
    fun dmg(n: Int, target: Tgt, hits: Int = 1) = Effect.Damage(Amount(n), target, hits)
    fun block(n: Int, vararg bonuses: Bonus) = Effect.Block(Amount(n, *bonuses))
    fun attuned(plus: Int) = Bonus.If(Cond.Attuned, plus)
    fun whenever(cond: Cond, vararg then: Effect) = Effect.When(cond, then.toList())
}
