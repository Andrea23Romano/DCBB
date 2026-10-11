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

    /** Brings [n] more [enemyId] into the fight, while fewer than five enemies stand ([name] is for the Track). */
    data class Summon(val enemyId: String, val name: String, val n: Int = 1) : EnemyAction

    /** Restores [n] HP (MERIDIAN's Recompute). */
    data class Heal(val n: Int) : EnemyAction

    /** One hit of [per] × (its living [allyId] allies + 1), counted when it lands (the Thousandfold's Chorus). */
    data class AttackPerAlly(val per: Int, val allyId: String, val allyName: String) : EnemyAction
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
    /** Its own HP, for bosses that change as they bleed. */
    val hp: Int = 0,
    val maxHp: Int = 0,
    /** Attacks you played in your last turn. */
    val attacksLastTurn: Int = 0,
    /** The living enemies in the fight by definition id, itself included. */
    val allies: Map<String, Int> = emptyMap(),
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

/** An extra a main intent picks up when you played [minAttacks]+ Attacks in your last turn (a Vow, a Litany). */
data class Rider(val label: String, val actions: List<EnemyAction>, val minAttacks: Int)

/**
 * One AI per slot, each with its own state: a boss's main intent beside its Fixed signature move. [riders] join the
 * first slot's intents when your play calls for them.
 */
class ComboAi(
    private val parts: Map<String, EnemyAi>,
    private val riders: List<Rider> = emptyList(),
    override val predictive: Boolean = parts.values.any { it.predictive },
) : EnemyAi {
    override val slots: List<String> = parts.keys.toList()

    override fun next(slot: String, aiState: Map<String, Int>, view: AiView): Pair<IntentSpec, Map<String, Int>> {
        val prefix = "$slot."
        val (spec, mine) = parts.getValue(slot).next(slot, aiState.sub(prefix), view)
        val riding = if (slot == slots.first()) riders.filter { view.attacksLastTurn >= it.minAttacks } else emptyList()
        val out = riding.fold(spec) { acc, r ->
            acc.copy(
                label = "${acc.label} · ${r.label}",
                actions = acc.actions + r.actions,
                alt = acc.alt?.plus(r.actions),
                altLabel = acc.altLabel?.let { "$it · ${r.label}" },
            )
        }
        return out.copy(slot = slot) to aiState.merge(prefix, mine)
    }
}

/** A phase of a [PhasedAi]: it starts once HP falls to [atPct]% or below, opening with [opening] on the first slot. */
data class AiPhase(val atPct: Int, val ai: EnemyAi, val opening: IntentSpec? = null)

/**
 * A boss that changes as it bleeds. Phases are listed from the first ([AiPhase.atPct] 100) down, never go back (a
 * heal doesn't undo one), and share their slots. A new phase announces itself on the first slot.
 */
class PhasedAi(private val phases: List<AiPhase>) : EnemyAi {
    init {
        require(phases.isNotEmpty())
        require(phases.all { it.ai.slots == phases[0].ai.slots && it.ai.predictive == phases[0].ai.predictive }) {
            "Every phase needs the same slots and timing"
        }
    }

    override val slots: List<String> = phases[0].ai.slots
    override val predictive: Boolean = phases[0].ai.predictive

    override fun next(slot: String, aiState: Map<String, Int>, view: AiView): Pair<IntentSpec, Map<String, Int>> {
        val pct = if (view.maxHp > 0) 100 * view.hp / view.maxHp else 100
        val reached = phases.indices.last { it == 0 || pct <= phases[it].atPct }
        val now = maxOf(aiState["phase"] ?: 0, reached)
        val announced = aiState["announced"] ?: 0
        val opening = phases[now].opening
        if (slot == slots.first() && now > announced && opening != null) {
            return opening.copy(slot = slot) to (aiState + ("phase" to now) + ("announced" to now))
        }
        val prefix = "p$now."
        val (spec, mine) = phases[now].ai.next(slot, aiState.sub(prefix), view)
        val marks = if (slot == slots.first()) mapOf("phase" to now, "announced" to now) else mapOf("phase" to now)
        return spec.copy(slot = slot) to aiState.merge(prefix, mine) + marks
    }
}

private fun Map<String, Int>.sub(prefix: String): Map<String, Int> =
    filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }

private fun Map<String, Int>.merge(prefix: String, sub: Map<String, Int>): Map<String, Int> =
    filterKeys { !it.startsWith(prefix) } + sub.mapKeys { prefix + it.key }

data class EnemyDef(
    val id: String,
    val name: String,
    val faction: Faction,
    val maxHp: Int,
    val ai: EnemyAi,
    val elite: Boolean = false,
)

/** [runOnly] encounters need run data (an Echo's deck), so the combat lab and the fight simulator skip them. */
data class Encounter(val id: String, val name: String, val enemies: List<String>, val elite: Boolean = false, val runOnly: Boolean = false)
