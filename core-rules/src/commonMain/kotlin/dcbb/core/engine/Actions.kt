package dcbb.core.engine

import dcbb.core.state.ShiftPair

/** How generic pips are paid. AUTO pays in faction color when that makes the card Attuned. */
enum class PayStyle { AUTO, NEUTRAL_FIRST }

/** Player inputs. Every choice a card needs is part of the action, except Foresee, which pauses for [ResolveForesee]. */
sealed interface Action

data class PlayCard(
    val cardUid: Int,
    val target: Int? = null,
    val trackTarget: Int? = null,
    val cardTarget: Int? = null,
    /** 0 = face A, 1 = face B (Fork cards and Forked cards). */
    val face: Int = 0,
    val pay: PayStyle = PayStyle.AUTO,
    val allowBorrow: Boolean = false,
    val recallUids: List<Int> = emptyList(),
    val shiftPairs: List<ShiftPair> = emptyList(),
    /** Which installed Constant to replace when all 3 slots are full (default: the oldest). */
    val replaceConstantUid: Int? = null,
) : Action

data class UseSignature(val target: Int? = null, val shiftPair: ShiftPair? = null) : Action

/**
 * Answers a pending Foresee: [top] stays on top in this order (first = top), [bottom] goes under the Future,
 * [take] moves one revealed card into the Present (cards like Optimal Path).
 */
data class ResolveForesee(val top: List<Int>, val bottom: List<Int> = emptyList(), val take: Int? = null) : Action

data object EndTurn : Action
