package dcbb.core

import dcbb.core.content.Prototype
import dcbb.core.engine.Action
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.Engine
import dcbb.core.engine.Outcome
import dcbb.core.engine.ResolveForesee
import dcbb.core.model.EnergyColor
import dcbb.core.state.CardInst
import dcbb.core.state.CombatState
import dcbb.core.state.TrackItem
import dcbb.core.state.TrackKind
import kotlin.test.assertNull
import kotlin.test.fail

val content = Prototype.content
val engine = Engine(content)

/** Max HP of the Sandglass Golem, the tests' usual target, so tests survive enemy tuning. */
val GOLEM = content.enemy("saltwaste.sandglass_golem").maxHp

fun start(op: String, vararg enemies: String, seed: Long = 1, deck: List<String>? = null): CombatState =
    engine.start(CombatSetup(op, deck ?: content.deck(op, "starter"), enemies.toList(), seed)).state

/** Applies [action] and fails the test if the engine rejects it. */
fun CombatState.ok(action: Action): Outcome {
    val out = engine.apply(this, action)
    assertNull(out.error, "Engine rejected $action")
    return out
}

/** Answers a pending Foresee by keeping the revealed order (taking the first card if it must take one). */
fun CombatState.settle(): CombatState {
    val pending = pending ?: return this
    val answer = if (pending.tutor) ResolveForesee(pending.uids.drop(1), take = pending.uids.first()) else ResolveForesee(pending.uids)
    return ok(answer).state.settle()
}

fun CombatState.rejects(action: Action): String {
    val out = engine.apply(this, action)
    return out.error ?: fail("Engine accepted $action")
}

private fun CombatState.make(ids: List<String>): Pair<List<CardInst>, CombatState> {
    var uid = nextUid
    val cards = ids.map { CardInst(uid++, it) }
    return cards to copy(nextUid = uid)
}

fun CombatState.withPresent(vararg ids: String): CombatState {
    val (cards, s) = make(ids.toList())
    return s.copy(player = s.player.copy(present = cards))
}

fun CombatState.withFuture(vararg ids: String, known: Boolean = false): CombatState {
    val (cards, s) = make(ids.toList())
    return s.copy(player = s.player.copy(future = cards.map { it.copy(known = known) }))
}

fun CombatState.withPast(vararg ids: String): CombatState {
    val (cards, s) = make(ids.toList())
    return s.copy(player = s.player.copy(past = cards))
}

fun CombatState.withOtherHand(vararg ids: String): CombatState {
    val (cards, s) = make(ids.toList())
    return s.copy(player = s.player.copy(otherHand = cards))
}

fun CombatState.withEnergy(vararg pairs: Pair<EnergyColor, Int>): CombatState =
    copy(player = player.copy(energy = pairs.toMap().filterValues { it > 0 }))

/** Installs Constants directly, as if played on an earlier turn. */
fun CombatState.withConstants(vararg ids: String): CombatState {
    val (cards, s) = make(ids.toList())
    return s.copy(player = s.player.copy(constants = cards))
}

fun CombatState.present(defId: String): CardInst = player.present.first { it.defId == defId }

fun CombatState.enemyNamed(defId: String) = enemies.first { it.defId == defId && it.alive }

fun CombatState.intentOf(defId: String, slot: String = "main"): TrackItem =
    track.first { it.kind == TrackKind.INTENT && it.enemyUid == enemyNamed(defId).uid && it.slot == slot }
