package dcbb.core.state

import dcbb.core.engine.Rng
import dcbb.core.model.CardType
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.EnergyColor
import dcbb.core.model.Face
import dcbb.core.model.Faction
import dcbb.core.model.StatusType

/** One physical card during a combat. Combat-only modifications live here, never on the definition. */
data class CardInst(
    val uid: Int,
    val defId: String,
    /** An imposed face (Misprint roll, Unravel). The card can only be played as this face. */
    val face: Face? = null,
    /** A second face granted by a Fork effect for this combat. */
    val granted: Face? = null,
    val known: Boolean = false,
    val blank: Boolean = false,
    val iterations: Int = 0,
    /** Cost change for this turn only (Bleed-through, Codex Sketch, The Thousand Doors). */
    val costDelta: Int = 0,
    val retainThisTurn: Boolean = false,
    /** Added by Bleed-through: Erased after play. */
    val bleed: Boolean = false,
)

enum class Phase {
    PLAYER_TURN,
    AWAIT_FORESEE,
    WON,
    LOST,
    DRAW,
    ;

    val over: Boolean get() = this == WON || this == LOST || this == DRAW
}

data class PlayerState(
    val operativeId: String,
    val hp: Int,
    val maxHp: Int,
    val block: Int = 0,
    val statuses: Map<StatusType, Int> = emptyMap(),
    val energy: Map<EnergyColor, Int> = emptyMap(),
    val debt: Map<EnergyColor, Int> = emptyMap(),
    val borrowedThisTurn: Int = 0,
    val future: List<CardInst>,
    val present: List<CardInst> = emptyList(),
    val past: List<CardInst> = emptyList(),
    val erased: List<CardInst> = emptyList(),
    val otherHand: List<CardInst> = emptyList(),
    val constants: List<CardInst> = emptyList(),
    /** The card currently resolving (out of every zone until it lands). */
    val resolving: CardInst? = null,
    val paradox: Int = 0,
    val signatureUsed: Boolean = false,
    val cardsPlayedThisTurn: Int = 0,
    val skillsPlayedThisTurn: Int = 0,
    val lastPlayedType: CardType? = null,
    val shiftedThisTurn: Boolean = false,
    val shiftDiscountUsed: Boolean = false,
    val hpLostLastEnemyPhase: Int = 0,
    val hpLostThisEnemyPhase: Int = 0,
    val constantsSilenced: Boolean = false,
    val silenceNextTurn: Boolean = false,
    /** What the last card played looked like, for the Double. */
    val lastCardDamage: Int = 0,
    val lastCardBlock: Int = 0,
) {
    fun status(type: StatusType): Int = statuses[type] ?: 0
    fun energyOf(color: EnergyColor): Int = energy[color] ?: 0
    val energyTotal: Int get() = energy.values.sum()
    val debtTotal: Int get() = debt.values.sum()

    /** Every card the player owns this combat, in any zone. */
    val allCards: List<CardInst>
        get() = future + present + past + erased + otherHand + constants + listOfNotNull(resolving)
}

data class EnemyState(
    val uid: Int,
    val defId: String,
    val hp: Int,
    val maxHp: Int,
    val block: Int = 0,
    val statuses: Map<StatusType, Int> = emptyMap(),
    val aiState: Map<String, Int> = emptyMap(),
    val alive: Boolean = true,
) {
    fun status(type: StatusType): Int = statuses[type] ?: 0
}

enum class TrackKind { INTENT, SCHEDULED }

/** One item on the Track: an enemy intent or one of your Scheduled effects. */
data class TrackItem(
    val id: Int,
    val kind: TrackKind,
    val countdown: Int,
    val order: Int,
    val label: String,
    val enemyUid: Int? = null,
    val slot: String? = null,
    val actions: List<EnemyAction> = emptyList(),
    val alt: List<EnemyAction>? = null,
    val altLabel: String? = null,
    /** For Forked intents: 0 = outcome A, 1 = outcome B, once collapsed. */
    val collapsed: Int? = null,
    val pressure: Int = 0,
    val fixed: Boolean = false,
    val effects: List<Effect> = emptyList(),
    val ctx: FxCtx? = null,
) {
    val forked: Boolean get() = alt != null
}

data class ShiftPair(val presentUid: Int, val otherUid: Int? = null)

/** Context an effect resolves in: the card (or trigger) it came from, and the choices made when it was played. */
data class FxCtx(
    val sourceUid: Int? = null,
    val sourceDefId: String? = null,
    val faction: Faction = Faction.NEUTRAL,
    val attuned: Boolean = false,
    val known: Boolean = false,
    val prevType: CardType? = null,
    val targetEnemy: Int? = null,
    val trackTarget: Int? = null,
    val cardTarget: Int? = null,
    val recallUids: List<Int> = emptyList(),
    val shiftPairs: List<ShiftPair> = emptyList(),
    val iterations: Int = 0,
)

enum class TurnStep {
    DUSK_REST,
    ENEMY_PHASE,
    ENEMY_PHASE_END,
    END_ROUND,
    DAWN,
    DAWN_PREDICT,
}

/** Pending engine work. Effects are data, so the whole queue can be saved and resumed. */
sealed interface Work {
    data class Fx(val effects: List<Effect>, val ctx: FxCtx) : Work
    data class FinishPlay(val cardUid: Int, val toErased: Boolean, val asConstant: Boolean, val replaceUid: Int?) : Work
    data class ResolveIntent(val trackId: Int) : Work
    data class Step(val step: TurnStep) : Work
}

data class PendingForesee(val uids: List<Int>, val tutor: Boolean)

data class CombatState(
    val round: Int,
    val phase: Phase,
    val player: PlayerState,
    val enemies: List<EnemyState>,
    val track: List<TrackItem> = emptyList(),
    val rng: Rng,
    val nextUid: Int,
    val nextOrder: Int,
    val queue: List<Work> = emptyList(),
    val pending: PendingForesee? = null,
    val roundCap: Int = 60,
) {
    val livingEnemies: List<EnemyState> get() = enemies.filter { it.alive }
    fun enemy(uid: Int): EnemyState? = enemies.firstOrNull { it.uid == uid }
    fun trackItem(id: Int): TrackItem? = track.firstOrNull { it.id == id }
}
