package dcbb.core.bot

import dcbb.core.engine.Action
import dcbb.core.engine.EndTurn
import dcbb.core.engine.Engine
import dcbb.core.engine.PayStyle
import dcbb.core.engine.PlayCard
import dcbb.core.engine.ResolveForesee
import dcbb.core.engine.TrackNeed
import dcbb.core.engine.UseSignature
import dcbb.core.model.CardType
import dcbb.core.model.Signature
import dcbb.core.state.CardInst
import dcbb.core.state.CombatState
import dcbb.core.state.PendingForesee
import dcbb.core.state.ShiftPair

/**
 * Candidate actions for bots. Not every combination of choices is listed: identical cards are deduplicated, and
 * Recall, Shift and Foresee choices come from a card-value heuristic. Candidates can still be illegal (the engine
 * has the final word), so callers check [dcbb.core.engine.Outcome.error].
 */
object Moves {

    fun candidates(engine: Engine, state: CombatState): List<Action> {
        if (state.phase.over) return emptyList()
        state.pending?.let { return foreseeAnswers(engine, state, it) }
        val out = mutableListOf<Action>()
        if (!state.player.signatureUsed) out += signatureMoves(engine, state)
        val seen = HashSet<Any>()
        for (inst in state.player.present) {
            if (!seen.add(cardKey(inst))) continue
            out += playMoves(engine, state, inst)
        }
        out += EndTurn
        return out
    }

    /** Cards that behave identically get one set of candidates. */
    fun cardKey(c: CardInst): Any = listOf(c.defId, c.face, c.granted, c.known, c.blank, c.costDelta, c.iterations, c.bleed, c.retainThisTurn)

    private fun signatureMoves(engine: Engine, state: CombatState): List<Action> {
        val p = state.player
        return when (engine.content.operative(p.operativeId).signature) {
            Signature.STRIKE -> state.livingEnemies.map { UseSignature(target = it.uid) }
            Signature.FORECAST -> if (p.future.isNotEmpty() || p.past.isNotEmpty()) listOf(UseSignature()) else emptyList()
            Signature.SHIFT -> {
                val seen = HashSet<Any>()
                buildList {
                    for (a in p.present) for (b in p.otherHand) {
                        if (seen.add(cardKey(a) to cardKey(b))) add(UseSignature(shiftPair = ShiftPair(a.uid, b.uid)))
                    }
                }
            }
        }
    }

    private fun playMoves(engine: Engine, state: CombatState, inst: CardInst): List<Action> {
        val def = engine.content.card(inst.defId)
        if (inst.blank || def.unplayable) return emptyList()
        if (def.requiresParadox > state.player.paradox) return emptyList()
        val p = state.player
        val out = mutableListOf<Action>()
        val faces = engine.faces(inst)
        for ((fi, face) in faces.withIndex()) {
            val needs = engine.needs(face)
            val enemies: List<Int?> = if (needs.enemy) state.livingEnemies.map { it.uid } else listOf(null)
            val tracks: List<Int?> = if (needs.track != TrackNeed.NONE) {
                engine.trackCandidates(state, needs.track).map { it.id }.ifEmpty { listOf(null) }
            } else {
                listOf(null)
            }
            val cardTargets: List<Int?> = if (needs.card) {
                val seen = HashSet<Any>()
                p.present.filter { it.uid != inst.uid && seen.add(cardKey(it)) }.map { it.uid }.ifEmpty { listOf(null) }
            } else {
                listOf(null)
            }
            val recall = if (needs.recall > 0) CardValues.bestInPast(engine, state, needs.recall) else emptyList()
            val shifts = if (needs.shift > 0) shiftOptions(engine, state, inst.uid, needs.shift) else listOf(emptyList())
            val replace: List<Int?> = if (face.type == CardType.CONSTANT && p.constants.size >= Engine.CONSTANT_SLOTS) {
                p.constants.map { it.uid }
            } else {
                listOf(null)
            }
            val pays = payOptions(engine, state, inst) ?: continue
            for (e in enemies) for (t in tracks) for (c in cardTargets) for (sh in shifts) for (r in replace) for ((style, borrow) in pays) {
                out += PlayCard(
                    cardUid = inst.uid,
                    target = e,
                    trackTarget = t,
                    cardTarget = c,
                    face = fi,
                    pay = style,
                    allowBorrow = borrow,
                    recallUids = recall,
                    shiftPairs = sh,
                    replaceConstantUid = r,
                )
            }
        }
        return out
    }

    /** Distinct ways to pay: Attuned-first or Neutral-first, borrowing only when it is needed. Null if unaffordable. */
    private fun payOptions(engine: Engine, state: CombatState, inst: CardInst): List<Pair<PayStyle, Boolean>>? {
        val seen = HashSet<Any>()
        val out = mutableListOf<Pair<PayStyle, Boolean>>()
        for (style in PayStyle.entries) {
            val plain = engine.payment(state, inst, style, allowBorrow = false)
            val pay = plain ?: engine.payment(state, inst, style, allowBorrow = true) ?: continue
            if (seen.add(pay)) out += style to (plain == null)
        }
        return out.ifEmpty { null }
    }

    /** Shift choices: the greedy upgrade (worst Present card for best Other Hand card), or one forced swap. */
    private fun shiftOptions(engine: Engine, state: CombatState, playedUid: Int, n: Int): List<List<ShiftPair>> {
        val p = state.player
        val present = p.present.filter { it.uid != playedUid }.sortedBy { CardValues.value(engine, state, it) }
        if (present.isEmpty()) return listOf(emptyList())
        if (engine.content.operative(p.operativeId).otherHandSize == 0) {
            return listOf(emptyList(), listOf(ShiftPair(present.first().uid, null)))
        }
        val other = p.otherHand.sortedByDescending { CardValues.value(engine, state, it) }
        if (other.isEmpty()) return listOf(emptyList())
        val greedy = present.zip(other).take(n)
            .filter { (a, b) -> CardValues.value(engine, state, b) > CardValues.value(engine, state, a) }
            .map { (a, b) -> ShiftPair(a.uid, b.uid) }
        val forced = listOf(ShiftPair(present.first().uid, other.first().uid))
        return listOf(greedy, forced).distinct()
    }

    /** Foresee answers: the heuristic order first (best on top, Blank cards to the bottom), then "leave as is". */
    fun foreseeAnswers(engine: Engine, state: CombatState, pending: PendingForesee): List<Action> {
        val revealed = state.player.future.filter { it.uid in pending.uids }
        val take = if (pending.tutor) revealed.maxByOrNull { CardValues.value(engine, state, it) }?.uid else null
        val rest = revealed.filter { it.uid != take }
        val bottom = rest.filter { it.blank || CardValues.value(engine, state, it) <= 0.0 }
        val top = (rest - bottom.toSet()).sortedByDescending { CardValues.value(engine, state, it) }
        val smart = ResolveForesee(top.map { it.uid }, bottom.map { it.uid }, take)
        val asIs = ResolveForesee(rest.map { it.uid }, emptyList(), take)
        return listOf(smart, asIs).distinct()
    }
}
