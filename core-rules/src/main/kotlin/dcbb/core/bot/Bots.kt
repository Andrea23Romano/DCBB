package dcbb.core.bot

import dcbb.core.engine.Action
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.EndTurn
import dcbb.core.engine.Engine
import dcbb.core.engine.GameEvent
import dcbb.core.engine.ResolveForesee
import dcbb.core.engine.Rng
import dcbb.core.engine.Stream
import dcbb.core.state.CombatState
import dcbb.core.state.Phase

/** A simulated player. Bots are not thread-safe; use one instance per fight. */
interface Bot {
    val name: String
    fun act(engine: Engine, state: CombatState): Action
}

/**
 * One-ply greedy: tries every candidate on a determinized copy of the state (no peeking at hidden cards or rolls)
 * and plays the one with the best [Eval.score], or ends the turn when nothing beats the current position.
 */
class GreedyBot(private val seed: Long = 0) : Bot {
    override val name = "greedy"

    override fun act(engine: Engine, state: CombatState): Action {
        val moves = Moves.candidates(engine, state)
        if (state.pending != null) return moves.first()
        val det = Eval.determinize(state, seed * 1_000_003L + state.round * 7_919L + state.player.cardsPlayedThisTurn)
        var best: Action? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (m in moves) {
            if (m == EndTurn) continue
            val out = engine.apply(det, m)
            if (out.error != null) continue
            val sc = Eval.score(engine, out.state)
            if (sc > bestScore) {
                bestScore = sc
                best = m
            }
        }
        // Play the best action only if it beats ending the turn now, when the unplayed Present is simply discarded.
        val endNow = Eval.score(engine, det, includeHand = false)
        return if (best != null && bestScore > endNow + MARGIN) best else EndTurn
    }

    private companion object {
        const val MARGIN = 0.25
    }
}

/** Uniformly random among the candidates, End Turn included. A floor for balance comparisons. */
class RandomBot(seed: Long) : Bot {
    override val name = "random"
    private var rng = Rng.seeded(seed)

    override fun act(engine: Engine, state: CombatState): Action {
        val moves = Moves.candidates(engine, state)
        val (i, next) = rng.nextInt(moves.size, Stream.MISC)
        rng = next
        return moves[i]
    }
}

/** What happened in one fight, gathered from the event stream. */
class FightStats {
    var cardsPlayed = 0
    var signatures = 0
    var intentDelays = 0
    var pressuredResolutions = 0
    var pressureSum = 0
    var borrowed = 0
    var unravels = 0
    var vowBreaks = 0
    var damageTaken = 0
    var otherHpLoss = 0
    var botErrors = 0
    val cardPlays = HashMap<String, Int>()

    fun consume(events: List<GameEvent>) {
        for (e in events) {
            when (e) {
                is GameEvent.CardPlayed -> {
                    cardsPlayed++
                    cardPlays.merge(e.defId, 1, Int::plus)
                }

                is GameEvent.SignatureUsed -> signatures++
                is GameEvent.Delayed -> if (e.isIntent) intentDelays++
                is GameEvent.IntentResolved -> if (e.pressure > 0) {
                    pressuredResolutions++
                    pressureSum += e.pressure
                }

                is GameEvent.Borrowed -> borrowed += e.amount
                GameEvent.Unravel -> unravels++
                is GameEvent.VowBroken -> vowBreaks++
                is GameEvent.DamageToPlayer -> damageTaken += e.amount
                is GameEvent.HpLost -> otherHpLoss += e.amount
                else -> Unit
            }
        }
    }
}

data class FightResult(
    val phase: Phase,
    val rounds: Int,
    val hpStart: Int,
    val hpEnd: Int,
    val maxHp: Int,
    val stats: FightStats,
    val log: List<String>,
    /** Debt still owed when the fight ended. Debt doesn't outlive a combat, so this energy was never repaid. */
    val debtAtEnd: Int = 0,
) {
    val won: Boolean get() = phase == Phase.WON
    val hpLost: Int get() = hpStart - maxOf(0, hpEnd)
}

object Runner {
    /** Plays one combat to the end with [bot]. Illegal bot choices fall back to the first legal candidate. */
    fun fight(engine: Engine, setup: CombatSetup, bot: Bot, keepLog: Boolean = false, maxDecisions: Int = 5_000): FightResult {
        val stats = FightStats()
        val log = mutableListOf<String>()
        var out = engine.start(setup)
        stats.consume(out.events)
        if (keepLog) out.events.mapTo(log) { it.text }
        val hpStart = out.state.player.hp
        var s = out.state
        var decisions = 0
        while (!s.phase.over && decisions < maxDecisions) {
            decisions++
            out = engine.apply(s, bot.act(engine, s))
            if (out.error != null) {
                stats.botErrors++
                out = fallback(engine, s)
            }
            stats.consume(out.events)
            if (keepLog) out.events.mapTo(log) { it.text }
            s = out.state
        }
        return FightResult(s.phase, s.round, hpStart, s.player.hp, s.player.maxHp, stats, log, s.player.debtTotal)
    }

    private fun fallback(engine: Engine, s: CombatState) =
        Moves.candidates(engine, s).asSequence()
            .map { engine.apply(s, it) }
            .firstOrNull { it.error == null }
            ?: engine.apply(s, s.pending?.let { if (it.tutor) ResolveForesee(it.uids.drop(1), take = it.uids.first()) else ResolveForesee(it.uids) } ?: EndTurn)
}
