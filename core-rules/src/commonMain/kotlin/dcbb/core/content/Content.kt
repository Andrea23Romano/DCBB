package dcbb.core.content

import dcbb.core.model.CardDef
import dcbb.core.model.CardType
import dcbb.core.model.Encounter
import dcbb.core.model.EnemyDef
import dcbb.core.model.Face
import dcbb.core.model.Faction
import dcbb.core.model.OperativeDef
import dcbb.core.model.Rarity

/** All game data the engine needs. Phase 1 defines it in Kotlin; Phase 2 moves it to YAML (docs/09). */
class Content(
    cards: List<CardDef>,
    enemies: List<EnemyDef>,
    operatives: List<OperativeDef>,
    val encounters: List<Encounter>,
    /** Generic Misprint faces by (type, total cost), used by Fork effects and Unravel (docs/08 "Misprint Tables"). */
    val misprintTables: Map<Pair<CardType, Int>, List<Face>>,
    /** Named decklists per operative, e.g. "starter", "mid". */
    val decks: Map<String, Map<String, List<String>>>,
    /** Generated variants (Inscribed `id+`, Misprinted `id~`): playable like any card, but outside pools and budget tables. */
    variants: List<CardDef> = emptyList(),
) {
    /** The authored cards: what reward pools, shops and budget tables draw from. */
    val authored: List<CardDef> = cards
    val cards: Map<String, CardDef> = (cards + variants).associateBy { it.id }
    val enemies: Map<String, EnemyDef> = enemies.associateBy { it.id }
    val operatives: Map<String, OperativeDef> = operatives.associateBy { it.id }

    init {
        require(this.cards.size == cards.size + variants.size) { "Duplicate card ids" }
        for ((op, lists) in decks) for ((name, list) in lists) for (id in list) {
            require(id in this.cards) { "Deck $op/$name references unknown card $id" }
        }
        for (e in encounters) for (id in e.enemies) require(id in this.enemies) { "Encounter ${e.id} references unknown enemy $id" }
    }

    fun card(id: String): CardDef = cards[id] ?: error("Unknown card $id")
    fun enemy(id: String): EnemyDef = enemies[id] ?: error("Unknown enemy $id")
    fun operative(id: String): OperativeDef = operatives[id] ?: error("Unknown operative $id")
    fun encounter(id: String): Encounter = encounters.firstOrNull { it.id == id } ?: error("Unknown encounter $id")
    fun deck(operativeId: String, name: String): List<String> =
        decks[operativeId]?.get(name) ?: error("Unknown deck $name for $operativeId")

    fun misprintFaces(type: CardType, totalCost: Int): List<Face> {
        val t = if (type == CardType.ATTACK) CardType.ATTACK else CardType.SKILL
        return misprintTables[t to totalCost.coerceIn(0, 3)] ?: error("No Misprint table for $t/$totalCost")
    }

    /** Cards that can Bleed through into a [faction] deck: other factions' non-starter cards. */
    fun bleedPool(faction: Faction): List<CardDef> {
        val factions = setOf(Faction.ORDER, Faction.CONVERGENCE, Faction.ERRATA) - faction
        return authored.filter { it.faction in factions && it.rarity != Rarity.STARTER }.sortedBy { it.id }
    }
}
