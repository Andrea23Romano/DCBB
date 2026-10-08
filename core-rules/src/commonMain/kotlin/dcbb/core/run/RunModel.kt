package dcbb.core.run

import dcbb.core.engine.Action
import dcbb.core.engine.GameEvent
import dcbb.core.engine.Rng
import dcbb.core.model.EnergyColor
import dcbb.core.model.Faction
import dcbb.core.state.CombatState

/** Moment types on the Weft (docs/05 "Era Deck composition"). */
enum class MomentType(val label: String) {
    COMBAT("Combat"),
    ELITE("Elite"),
    EVENT("Event"),
    ANOMALY("Anomaly"),
    ANTIQUARIAN("Antiquarian"),
    STILL_POINT("Still Point"),
    SHRINE("Shrine"),
    CACHE("Cache"),
    BOSS("Boss"),
    ;

    val fight: Boolean get() = this == COMBAT || this == ELITE || this == BOSS
}

/** One card of the Era Deck. The title, type, sigil and threat always show; [ref] stays hidden until Foresight. */
data class Moment(
    val id: Int,
    val type: MomentType,
    val title: String,
    /** An encounter id for fights; an event id for Events, Anomalies and Shrines. */
    val ref: String? = null,
    /** The faction sigil of a fight or a Shrine. */
    val faction: Faction? = null,
    /** Threat pips, 1–3, for fights. */
    val threat: Int = 0,
    /** Placed by a dealing rule (the last Still Point, the guaranteed Antiquarian, the Divergence): never replaced. */
    val pinned: Boolean = false,
    /** A Tear Moment: shuffled in by Paradox, or the ambush after an out-of-combat Unravel. */
    val tear: Boolean = false,
    /** The enemies act before your first turn. */
    val ambush: Boolean = false,
)

/** Run-level counters for the Chronicle and for simulation reports. */
data class RunStats(
    val fights: Int = 0,
    val elites: Int = 0,
    val hpLostInFights: Int = 0,
    val healed: Int = 0,
    val cardsTaken: Int = 0,
    val skipped: Int = 0,
    val erased: Int = 0,
    val inscribed: Int = 0,
    val hoursEarned: Int = 0,
    val hoursSpent: Int = 0,
    val glimpses: Int = 0,
    val foresightSpent: Int = 0,
    val unravels: Int = 0,
    val paradoxPeak: Int = 0,
    val fightRecords: List<FightRecord> = emptyList(),
    /** Which Moment types you chose on the Weft. */
    val chosen: Map<MomentType, Int> = emptyMap(),
)

data class FightRecord(
    val step: Int,
    val type: MomentType,
    val encounter: String,
    val threat: Int,
    val hpBefore: Int,
    val hpAfter: Int,
    val maxHp: Int,
    val rounds: Int,
    val won: Boolean,
    val paradoxBefore: Int = 0,
    val unravels: Int = 0,
)

/** Where the run is: what the player is looking at and may do next. */
sealed interface Screen {
    /** Choose one Moment of the step. */
    data class Weft(val options: List<Moment>) : Screen

    /** A fight in progress; once it is over, Done carries the result into the run. */
    data class Fight(val moment: Moment, val combat: CombatState, val hoursMult: Int = 1) : Screen

    /** Hours and any Artifact are already yours; choose a card (or Glimpse, or skip for Hours). */
    data class Reward(val title: String, val cards: List<String>, val lines: List<String>, val elite: Boolean, val glimpsed: Boolean = false) : Screen

    /** The Antiquarian. */
    data class Shop(val stock: List<ShopItem>) : Screen

    /** A Still Point. With Bell of the Still Hour you may both heal and Inscribe. */
    data class Rest(val healed: Boolean = false, val inscribed: Boolean = false) : Screen

    /** An Event, Anomaly or Shrine: choose one option. */
    data class Event(val eventId: String, val options: List<EventOption>) : Screen

    /** Choose a card from your deck to Erase or Inscribe. [remaining] picks are left; [next] follows (null: the next step). */
    data class PickCard(val purpose: Purpose, val remaining: Int, val next: Screen?, val back: Screen?, val shopItem: Int? = null) : Screen

    /** Something happened; read it and continue. [next] follows (null: the next step). */
    data class Note(val title: String, val lines: List<String>, val next: Screen? = null) : Screen

    /** The run is over. */
    data class Over(val won: Boolean, val title: String, val lines: List<String>) : Screen
}

enum class Purpose(val verb: String) {
    ERASE("Erase"),
    INSCRIBE("Inscribe"),
}

enum class ShopKind { CARD, ARTIFACT, ERASE, INSCRIBE, FORESIGHT }

data class ShopItem(val kind: ShopKind, val ref: String?, val price: Int, val sold: Boolean = false)

/** An event choice as offered: its effects (rendered by the engine, never by prose), and the pre-rolled Ripple. */
data class EventOption(
    val choiceId: String,
    val text: String,
    val effects: List<RunEffect>,
    val ripple: String? = null,
    /** Why the option can't be taken right now, or null. */
    val blocked: String? = null,
)

/** The whole run as one immutable value: saving it is saving the seed and the action list (see [RunReplay]). */
data class RunState(
    val operativeId: String,
    val seed: Long,
    val eraId: String,
    val act: Int,
    /** The step being played, 1-based. Steps past the era's count mean the boss. */
    val step: Int,
    val hp: Int,
    val maxHp: Int,
    val deck: List<String>,
    val hours: Int,
    val foresight: Int,
    val paradox: Int,
    val debt: Map<EnergyColor, Int> = emptyMap(),
    val standing: Map<Faction, Int>,
    val artifacts: List<String> = emptyList(),
    /** The Causality Ledger's flags, in the order they were set. */
    val ledger: List<String> = emptyList(),
    val ripples: List<String> = emptyList(),
    /** The act's Weft: the Moments each step deals (index 0 is step 1). */
    val plan: List<List<Moment>>,
    /** Moments revealed by Foresight. */
    val revealed: Set<Int> = emptySet(),
    val screen: Screen,
    val rng: Rng,
    val nextId: Int,
    /** Rare odds bonus: +2% per card reward without a rare (docs/05 "Rarity odds"). */
    val rareBonus: Int = 0,
    /** How many cards the Antiquarian has Erased (its price rises by 25 each time). */
    val erases: Int = 0,
    /** Frayed: this act's Tear Moments have joined the Era Deck. */
    val tearAdded: Boolean = false,
    /** An out-of-combat Unravel: the next step is a Tear ambush. */
    val tearAmbush: Boolean = false,
    val lastEncounter: String? = null,
    val stats: RunStats = RunStats(),
) {
    fun standingWith(f: Faction): Int = standing[f] ?: 0
    val over: Boolean get() = screen is Screen.Over
}

/** Player inputs between and during fights. */
sealed interface RunAction {
    /** Pick option [index] of the current screen: a Moment, a reward card, a shop item, a rest option, an event option, a deck card. */
    data class Choose(val index: Int) : RunAction

    /** Spend 1 Foresight on the Weft. */
    data object Foresight : RunAction

    /** See the other branch's card reward too (+1 Paradox). */
    data object Glimpse : RunAction

    /** Skip the card reward for +10 Hours. */
    data object Skip : RunAction

    /** Leave, continue, or go back, depending on the screen. */
    data object Done : RunAction

    /** A combat action while a fight is on. */
    data class Fight(val action: Action) : RunAction
}

data class RunOutcome(
    val state: RunState,
    /** What happened outside combat, for the run log. */
    val log: List<String> = emptyList(),
    /** Combat events, when the action was a fight action (or started a fight). */
    val events: List<GameEvent> = emptyList(),
    val error: String? = null,
)
