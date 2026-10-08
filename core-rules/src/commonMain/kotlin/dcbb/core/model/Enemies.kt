package dcbb.core.model

/** What an enemy intent does when it resolves (docs/04 "Intent types"). */
sealed interface EnemyAction {
    data class Attack(val damage: Int, val hits: Int = 1) : EnemyAction
    data class Guard(val block: Int) : EnemyAction
    data class Buff(val status: StatusType, val n: Int) : EnemyAction
    data class Debuff(val status: StatusType, val n: Int) : EnemyAction

    /** Unpick: the top card(s) of your Future become Blank. */
    data class BlankFuture(val n: Int) : EnemyAction

    /** Prune / Unpick: Erase the top card(s) of your Future. */
    data class EraseFuture(val n: Int) : EnemyAction

    /** Delay your Scheduled effects. */
    data class Disrupt(val n: Int) : EnemyAction

    /** Snip: your Constants don't trigger next turn. */
    data object SilenceConstants : EnemyAction
}

/** An intent an enemy puts on the Track. [alt] makes it a Forked intent (A = actions, B = alt). */
data class IntentSpec(
    val slot: String,
    val label: String,
    val actions: List<EnemyAction>,
    val countdown: Int = 1,
    val fixed: Boolean = false,
    val alt: List<EnemyAction>? = null,
    val altLabel: String? = null,
)

/** What an enemy AI may look at when choosing an intent. */
data class AiView(
    val round: Int,
    val presentTypes: List<CardType>,
    val lastCardDamage: Int,
    val lastCardBlock: Int,
)

/** Enemy behavior. Implementations must be deterministic: no randomness outside the engine's RNG. */
interface EnemyAi {
    val slots: List<String> get() = listOf("main")

    /** Predictive enemies set their intent at Dawn, after you draw, reading your Present. */
    val predictive: Boolean get() = false

    /** Returns the next intent for [slot] and the updated AI state. */
    fun next(slot: String, aiState: Map<String, Int>, view: AiView): Pair<IntentSpec, Map<String, Int>>
}

/** Plays [opening] once, then loops [loop]. */
class RotationAi(private val loop: List<IntentSpec>, private val opening: List<IntentSpec> = emptyList()) : EnemyAi {
    init {
        require(loop.isNotEmpty())
    }

    override fun next(slot: String, aiState: Map<String, Int>, view: AiView): Pair<IntentSpec, Map<String, Int>> {
        val i = aiState["i"] ?: 0
        val spec = if (i < opening.size) opening[i] else loop[(i - opening.size) % loop.size]
        return spec.copy(slot = slot) to (aiState + ("i" to i + 1))
    }
}

/** Several independent rotations, one per slot (an elite's jab plus its charging finisher). */
class MultiSlotAi(private val rotations: Map<String, List<IntentSpec>>) : EnemyAi {
    override val slots: List<String> = rotations.keys.toList()

    override fun next(slot: String, aiState: Map<String, Int>, view: AiView): Pair<IntentSpec, Map<String, Int>> {
        val key = "i_$slot"
        val i = aiState[key] ?: 0
        val list = rotations.getValue(slot)
        return list[i % list.size].copy(slot = slot) to (aiState + (key to i + 1))
    }
}

/** Brass Proxy: more Attacks than Skills in your Present means it guards. */
class PredictiveAi(private val ifAttackHeavy: IntentSpec, private val otherwise: IntentSpec) : EnemyAi {
    override val predictive: Boolean = true

    override fun next(slot: String, aiState: Map<String, Int>, view: AiView): Pair<IntentSpec, Map<String, Int>> {
        val attacks = view.presentTypes.count { it == CardType.ATTACK }
        val skills = view.presentTypes.count { it == CardType.SKILL }
        val spec = if (attacks > skills) ifAttackHeavy else otherwise
        return spec.copy(slot = slot) to aiState
    }
}

/** Double: copies the last card you played as its intent. */
class DoubleAi(private val fallbackDamage: Int) : EnemyAi {
    override val predictive: Boolean = true

    override fun next(slot: String, aiState: Map<String, Int>, view: AiView): Pair<IntentSpec, Map<String, Int>> {
        val spec = when {
            view.lastCardDamage > 0 -> IntentSpec(slot, "Copy: Attack ${view.lastCardDamage}", listOf(EnemyAction.Attack(view.lastCardDamage)))
            view.lastCardBlock > 0 -> IntentSpec(slot, "Copy: Guard ${view.lastCardBlock}", listOf(EnemyAction.Guard(view.lastCardBlock)))
            else -> IntentSpec(slot, "Attack $fallbackDamage", listOf(EnemyAction.Attack(fallbackDamage)))
        }
        return spec to aiState
    }
}

data class EnemyDef(
    val id: String,
    val name: String,
    val faction: Faction,
    val maxHp: Int,
    val ai: EnemyAi,
    val elite: Boolean = false,
)

data class Encounter(val id: String, val name: String, val enemies: List<String>, val elite: Boolean = false)
