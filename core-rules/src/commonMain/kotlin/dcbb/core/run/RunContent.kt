package dcbb.core.run

import dcbb.core.content.Content
import dcbb.core.model.CombatMods
import dcbb.core.model.Faction
import dcbb.core.model.Rarity

/** Run-level rule changes that Artifacts and Ripples switch on. */
enum class Perk(val text: String) {
    REST_HEAL_AND_INSCRIBE("At Still Points you may both heal and Inscribe"),
    FORESIGHT_EXTRA_PEEK("Foresight reveals a second Moment of the next step"),
    RELIC_DISCOUNT("Relic cards cost 25% less at the Antiquarian"),
    INSCRIBE_DISCOUNT("Inscribe costs 20 Hours less at the Antiquarian"),
    CONVERGENCE_REWARDS("Convergence cards appear twice as often in card rewards"),
}

/** Out-of-place artifacts (docs/05 "Artifacts"): passives for the rest of the run. */
data class ArtifactDef(
    val id: String,
    val name: String,
    val text: String,
    /** Antiquarian price in Hours. */
    val price: Int,
    val mods: CombatMods = CombatMods.NONE,
    val perks: Set<Perk> = emptySet(),
    /** Applied once, when you gain it. */
    val onGain: List<RunEffect> = emptyList(),
)

/** A Divergence's lasting change to the run (docs/05 "Divergence events and Ripples"). */
data class RippleDef(
    val id: String,
    val name: String,
    val text: String,
    val now: List<RunEffect> = emptyList(),
    val mods: CombatMods = CombatMods.NONE,
    val perks: Set<Perk> = emptySet(),
)

/** Where a granted card comes from. A null faction means your normal reward pools. */
data class CardPool(val faction: Faction? = null, val rarity: Rarity? = null, val relicOnly: Boolean = false)

/** Mechanical outcomes. The UI renders them from the engine (docs/05 trust rule), never from the event's prose. */
sealed interface RunEffect {
    data class Hours(val n: Int) : RunEffect
    data class Heal(val n: Int) : RunEffect
    data class HealPct(val pct: Int) : RunEffect
    data class LoseHp(val n: Int) : RunEffect
    data class MaxHp(val n: Int) : RunEffect
    data class Paradox(val n: Int) : RunEffect
    data class Foresight(val n: Int) : RunEffect
    data class Standing(val faction: Faction, val n: Int) : RunEffect
    data class Flag(val id: String) : RunEffect
    data class GainCard(val pool: CardPool) : RunEffect
    data class GainArtifact(val id: String? = null) : RunEffect

    /** Choose [count] cards from your deck to Erase or Inscribe. */
    data class Pick(val purpose: Purpose, val count: Int = 1) : RunEffect

    /** A fight: [encounter], or your last fight's encounter when null (The Loop). Hours rewards are multiplied. */
    data class Fight(val encounter: String?, val hoursMult: Int = 1) : RunEffect
}

/** Who may take an event choice. Shrines offer boons to their own faction and Desecration to everyone else. */
enum class Requirement { NONE, OWN_FACTION, OTHER_FACTION }

data class ChoiceDef(
    val id: String,
    val text: String,
    val effects: List<RunEffect>,
    /** Divergence choices: the legal Ripples, one of which is selected (seeded) and shown before you choose. */
    val ripples: List<String> = emptyList(),
    val requires: Requirement = Requirement.NONE,
)

enum class EventKind(val label: String) {
    ENCOUNTER("Encounter"),
    DILEMMA("Dilemma"),
    DIVERGENCE("Divergence"),
    ANOMALY("Anomaly"),
    SHRINE("Shrine"),
}

/**
 * A designer-written event template (docs/05 "How events are built"). [text] is the authored scene, the fallback the
 * Chronicler will rewrite once GenAI is on; the outcomes are data.
 */
data class EventDef(
    val id: String,
    val title: String,
    /** What the Moment card shows before Foresight reveals the event. */
    val teaser: String,
    val kind: EventKind,
    val text: String,
    val choices: List<ChoiceDef>,
    /** A Shrine's faction. */
    val faction: Faction? = null,
)

/** A fight the era can deal, with its threat pips (1 easy, 3 hard). */
data class EraFight(val encounter: String, val threat: Int)

/** One act's era: what its Era Deck holds (docs/05 "The Weft"). */
data class EraDef(
    val id: String,
    val name: String,
    val act: Int,
    val steps: Int,
    val combats: List<EraFight>,
    val elites: List<String>,
    /** Tear fights shuffled in when Paradox is Frayed, and used for Tear ambushes. */
    val tearFights: List<String>,
    val events: List<String>,
    val divergence: String,
    val anomalies: List<String>,
    val boss: String,
    /** Era cards in reward and shop pools: authored ERA cards whose id starts with this prefix. */
    val cardPrefix: String,
    val composition: Map<MomentType, Int>,
    /** Era-flavored titles for Moments that aren't fights or events. */
    val titles: Map<MomentType, List<String>>,
)

/** Everything a run needs besides the combat content. */
class RunContent(
    val content: Content,
    val eras: List<EraDef>,
    events: List<EventDef>,
    artifacts: List<ArtifactDef>,
    ripples: List<RippleDef>,
    /** Shrine event per faction. */
    val shrines: Map<Faction, String>,
) {
    val events: Map<String, EventDef> = events.associateBy { it.id }
    val artifacts: Map<String, ArtifactDef> = artifacts.associateBy { it.id }
    val ripples: Map<String, RippleDef> = ripples.associateBy { it.id }

    fun era(id: String): EraDef = eras.firstOrNull { it.id == id } ?: error("Unknown era $id")
    fun event(id: String): EventDef = events[id] ?: error("Unknown event $id")
    fun artifact(id: String): ArtifactDef = artifacts[id] ?: error("Unknown artifact $id")
    fun ripple(id: String): RippleDef = ripples[id] ?: error("Unknown ripple $id")

    init {
        for (era in eras) {
            require(era.steps >= 7) { "${era.id}: the dealing rules need at least 7 steps" }
            require(era.composition.values.sum() == era.steps * 3) { "${era.id}: the Era Deck must deal 3 Moments per step" }
            for (f in era.combats) content.encounter(f.encounter)
            (era.elites + era.tearFights + era.boss).forEach { content.encounter(it) }
            (era.events + era.divergence + era.anomalies).forEach { event(it) }
        }
        for (id in shrines.values) event(id)
        for (e in events) for (c in e.choices) c.ripples.forEach { ripple(it) }
    }
}
