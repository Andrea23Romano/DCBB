package dcbb.core.model

/** What an enemy intent does when it resolves (docs/04 "Intent types"). */
sealed interface EnemyAction {
    data class Attack(val damage: Int, val hits: Int = 1) : EnemyAction

    /** One hit of [per] damage for each [status] the enemy has when it resolves (the Lattice Engine's Cascade). */
    data class AttackPer(val per: Int, val status: StatusType) : EnemyAction
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
    /** The enemy's own statuses (the Lattice Engine counts its installed Subroutines). */
    val selfStatuses: Map<StatusType, Int> = emptyMap(),
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

/** One Subroutine the Lattice Engine installs on itself, and the rider it adds to every later main intent. */
data class LatticeSub(val name: String, val rider: List<EnemyAction>)

/**
 * The Lattice Engine (Act I boss, docs/03). Every other round its main intent installs the next of its [subs] on
 * itself; from then on that Subroutine's rider joins every main intent. In between it reads your Present like a
 * Brass Proxy: Attack-heavy hands meet a guard, anything else meets a hard hit. Its Fixed Cascade, on its own slot,
 * hits once for [cascadePer] per installed Subroutine and recharges.
 */
class LatticeAi(
    private val subs: List<LatticeSub>,
    /** Renders intent labels (the content layer passes RulesText.intent). */
    private val render: (List<EnemyAction>) -> String,
    private val installAttack: Int,
    private val guardedBlock: Int,
    private val guardedAttack: Int,
    private val openAttack: Int,
    private val cascadePer: Int,
    private val cascadeCountdown: Int = 3,
) : EnemyAi {
    override val slots: List<String> = listOf("main", "cascade")
    override val predictive: Boolean = true

    override fun next(slot: String, aiState: Map<String, Int>, view: AiView): Pair<IntentSpec, Map<String, Int>> {
        if (slot == "cascade") {
            val cascade = EnemyAction.AttackPer(cascadePer, StatusType.SUBROUTINES)
            return IntentSpec(slot, "Cascade: Attack $cascadePer × Subroutines", listOf(cascade), cascadeCountdown, fixed = true) to aiState
        }
        val i = aiState["i"] ?: 0
        val installed = (view.selfStatuses[StatusType.SUBROUTINES] ?: 0).coerceAtMost(subs.size)
        val riders = subs.take(installed).flatMap { it.rider }
        val (head, actions) = if (i % 2 == 0 && installed < subs.size) {
            val sub = subs[installed]
            "Install ${sub.name} (each round: ${render(sub.rider)})" to
                listOf(EnemyAction.Buff(StatusType.SUBROUTINES, 1), EnemyAction.Attack(installAttack))
        } else {
            val attacks = view.presentTypes.count { it == CardType.ATTACK }
            val skills = view.presentTypes.count { it == CardType.SKILL }
            val body = if (attacks > skills) {
                listOf(EnemyAction.Guard(guardedBlock), EnemyAction.Attack(guardedAttack))
            } else {
                listOf(EnemyAction.Attack(openAttack))
            }
            null to body
        }
        val shown = actions.filter { !(it is EnemyAction.Buff && it.status == StatusType.SUBROUTINES) } + riders
        val body = render(shown)
        val label = if (head != null) "$head: $body" else body
        return IntentSpec(slot, label, actions + riders) to (aiState + ("i" to i + 1))
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
