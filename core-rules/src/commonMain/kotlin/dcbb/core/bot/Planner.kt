package dcbb.core.bot

import dcbb.core.engine.Action
import dcbb.core.engine.EndTurn
import dcbb.core.engine.Engine
import dcbb.core.engine.Rng
import dcbb.core.state.CardInst
import dcbb.core.state.CombatState

/**
 * Plans the rest of the turn instead of one action at a time.
 *
 * 1. **Beam search** over sequences of plays, keeping the [beamWidth] best positions at each step by [Eval.score].
 *    This finds combos the greedy bot can't see: Shift then Caret Strike, Forecast then a Calculated card, Litany chains.
 * 2. **Look past the enemy phase.** Every position where the turn could end is scored by actually ending the turn on
 *    [samples] determinized copies (unseen cards reshuffled, fresh dice): the enemy phase plays out, the next Dawn
 *    deals a new hand, and that position is evaluated. This prices Block, Delay and Pressure by what really happens.
 * 3. **Commit, but stay honest.** The bot follows its plan while the visible state matches what it expected, and plans
 *    again as soon as hidden information changes things (a draw, a random target, a Misprint roll).
 *
 * Like every bot, it plans on determinized copies, so it never peeks at the real Future or the real dice.
 */
class PlannerBot(
    private val seed: Long = 0,
    private val beamWidth: Int = 4,
    private val maxDepth: Int = 6,
    private val samples: Int = 3,
) : Bot {
    override val name = "planner"

    private var plan: List<Step> = emptyList()

    /** A planned action and the visible state the bot expects after it. */
    private class Step(val action: Action, val expect: Any)

    private class Node(val state: CombatState, val steps: List<Step>, val score: Double)

    override fun act(engine: Engine, state: CombatState): Action {
        val pending = state.pending
        if (pending != null) return Moves.foreseeAnswers(engine, state, pending).first()
        // Keep following the plan while the last action led exactly where we expected.
        if (plan.isNotEmpty() && plan[0].expect == visible(state)) {
            plan = plan.drop(1)
            return plan.firstOrNull()?.action ?: EndTurn
        }
        plan = search(engine, state)
        return plan.firstOrNull()?.action ?: EndTurn
    }

    /** The best sequence of actions for the rest of the turn. An empty list means "end the turn now". */
    private fun search(engine: Engine, state: CombatState): List<Step> {
        val root = Eval.determinize(state, mix(seed, state))
        var beam = listOf(Node(root, emptyList(), Eval.score(engine, root)))
        val leaves = mutableListOf<Node>()
        repeat(maxDepth) {
            val children = mutableListOf<Node>()
            for (node in beam) {
                leaves += node
                for (m in Moves.candidates(engine, node.state)) {
                    if (m == EndTurn) continue
                    val out = engine.apply(node.state, m)
                    if (out.error != null) continue
                    val after = settle(engine, out.state)
                    val child = Node(after, node.steps + Step(m, visible(after)), Eval.score(engine, after))
                    if (after.phase.over) leaves += child else children += child
                }
            }
            if (children.isEmpty()) {
                beam = emptyList()
                return@repeat
            }
            val seen = HashSet<Any>()
            beam = children.sortedByDescending { it.score }.filter { seen.add(visible(it.state)) }.take(beamWidth)
        }
        leaves += beam
        return leaves.maxBy { leafValue(engine, it.state) }.steps
    }

    /** Expected value of ending the turn in [s]: play out the enemy phase and the next Dawn on a few copies. */
    private fun leafValue(engine: Engine, s: CombatState): Double {
        if (s.phase.over) return Eval.score(engine, s)
        var total = 0.0
        for (k in 0 until samples) {
            val copy = Eval.determinize(s, mix(seed + 7_919L * (k + 1), s))
            val out = engine.apply(copy, EndTurn)
            total += Eval.score(engine, settle(engine, out.state))
        }
        return total / samples
    }

    /** Answers any Foresee that interrupts a simulated line with the heuristic answer. */
    private fun settle(engine: Engine, s: CombatState): CombatState {
        var cur = s
        var guard = 0
        while (true) {
            val pending = cur.pending ?: return cur
            val out = engine.apply(cur, Moves.foreseeAnswers(engine, cur, pending).first())
            if (out.error != null || ++guard > 20) return cur
            cur = out.state
        }
    }

    private companion object {
        private val HIDDEN = CardInst(0, "?")

        /** What a player can see: everything except the order of unknown cards in the Future and the dice. */
        fun visible(s: CombatState): Any = s.copy(
            rng = Rng(0, 0, 0),
            queue = emptyList(),
            player = s.player.copy(future = s.player.future.map { if (it.known) it else HIDDEN }),
        )

        fun mix(seed: Long, s: CombatState): Long =
            seed * 1_000_003L + s.round * 7_919L + s.player.cardsPlayedThisTurn * 104_729L + if (s.player.signatureUsed) 1 else 0
    }
}
