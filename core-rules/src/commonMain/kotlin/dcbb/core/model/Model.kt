package dcbb.core.model

/** Energy colors (docs/04 "Energy"). NEUTRAL is income-only and pays generic pips. */
enum class EnergyColor(val symbol: String, val letter: String) {
    FAITH("☀", "F"),
    COMPUTE("⬡", "C"),
    FLUX("◎", "X"),
    NEUTRAL("○", "N"),
}

enum class Faction(val color: EnergyColor?, val label: String) {
    ORDER(EnergyColor.FAITH, "Order"),
    CONVERGENCE(EnergyColor.COMPUTE, "Convergence"),
    ERRATA(EnergyColor.FLUX, "Errata"),
    NEUTRAL(null, "Neutral"),
    ERA(null, "Era"),
    TEAR(null, "Tear"),
}

enum class CardType(val label: String) {
    ATTACK("Attack"),
    SKILL("Skill"),
    CONSTANT("Constant"),
    HAZARD("Hazard"),
}

enum class ConstantKind(val label: String) {
    NONE("Constant"),
    VOW("Vow"),
    RELIC("Relic"),
    SUBROUTINE("Subroutine"),
}

enum class Rarity(val budgetMultiplier: Double, val short: String) {
    STARTER(1.0, "St"),
    COMMON(1.0, "C"),
    UNCOMMON(1.1, "U"),
    RARE(1.25, "R"),
    LEGENDARY(1.4, "L"),
}

/** Card kinds counted by Remember and Litany. */
enum class Kind(val label: String) {
    ANY("cards"),
    ATTACK("Attacks"),
    SKILL("Skills");

    fun matches(type: CardType): Boolean = when (this) {
        ANY -> true
        ATTACK -> type == CardType.ATTACK
        SKILL -> type == CardType.SKILL
    }
}

enum class StatusType(val label: String) {
    MIGHT("Might"),
    PLATE("Plate"),
    WEAK("Weak"),
    EXPOSED("Exposed"),
    BURN("Burn"),
    SLOW("Slow"),
    GLITCH("Glitch"),

    /** Enemy only: Subroutines the enemy has installed on itself (the Lattice Engine). */
    SUBROUTINES("Subroutines"),
}

/** Card cost. `1F+1` = one Faith pip plus one generic pip. */
data class Cost(val faith: Int = 0, val compute: Int = 0, val flux: Int = 0, val generic: Int = 0) {
    val total: Int get() = faith + compute + flux + generic

    fun colored(color: EnergyColor): Int = when (color) {
        EnergyColor.FAITH -> faith
        EnergyColor.COMPUTE -> compute
        EnergyColor.FLUX -> flux
        EnergyColor.NEUTRAL -> 0
    }

    /** Adds [delta] pips: positive deltas add generic pips, negative deltas remove generic pips first, then colored. */
    fun adjusted(delta: Int): Cost {
        if (delta >= 0) return copy(generic = generic + delta)
        var remaining = -delta
        var g = generic
        var f = faith
        var c = compute
        var x = flux
        val fromGeneric = minOf(g, remaining); g -= fromGeneric; remaining -= fromGeneric
        val fromF = minOf(f, remaining); f -= fromF; remaining -= fromF
        val fromC = minOf(c, remaining); c -= fromC; remaining -= fromC
        val fromX = minOf(x, remaining); x -= fromX
        return Cost(f, c, x, g)
    }

    override fun toString(): String {
        if (total == 0) return "0"
        val parts = buildList {
            if (faith > 0) add("${faith}F")
            if (compute > 0) add("${compute}C")
            if (flux > 0) add("${flux}X")
            if (generic > 0) add("$generic")
        }
        return parts.joinToString("+")
    }

    companion object {
        fun parse(text: String): Cost {
            if (text == "0") return Cost()
            var f = 0
            var c = 0
            var x = 0
            var g = 0
            for (part in text.split("+")) {
                val n = part.takeWhile { it.isDigit() }.toInt()
                when (part.drop(part.takeWhile { it.isDigit() }.length)) {
                    "F" -> f += n
                    "C" -> c += n
                    "X" -> x += n
                    "" -> g += n
                    else -> error("Bad cost part '$part' in '$text'")
                }
            }
            return Cost(f, c, x, g)
        }
    }
}

/** A playable face. Fork cards have two; Misprint cards roll one; Unravel and Fork effects can impose one. */
data class Face(val type: CardType, val effects: List<Effect>)

enum class Signature(val label: String) {
    STRIKE("Strike"),
    FORECAST("Forecast"),
    SHIFT("Shift"),
}

/** Vow restrictions. [budgetCredit] is the docs/08 severity credit (mild 4, moderate 8, severe 12). */
enum class VowRule(val text: String, val budgetCredit: Double) {
    MAX_ONE_SKILL_PER_TURN("play at most 1 Skill per turn", 8.0),
    NO_BORROW_OR_DELAY("never Borrow or Delay", 8.0),
}

/** Persistent behavior of a Constant once installed (Vows, Relics, Subroutines, other Constants). */
data class ConstantSpec(
    val kind: ConstantKind,
    val dawn: List<Effect> = emptyList(),
    val dusk: List<Effect> = emptyList(),
    val strikeDamage: Int = 0,
    val strikeBlock: Int = 0,
    val vow: VowRule? = null,
    val onShiftIn: ShiftInRule? = null,
)

/** The Thousand Doors: cards Shifted into the Present get Forked; the first each turn costs less. */
data class ShiftInRule(val forkShifted: Boolean, val firstShiftDiscount: Int)

data class CardDef(
    val id: String,
    val name: String,
    val faction: Faction,
    val type: CardType,
    val rarity: Rarity,
    val cost: Cost,
    val effects: List<Effect> = emptyList(),
    val constant: ConstantSpec? = null,
    val forkFaces: List<Face>? = null,
    val misprintFaces: List<Face>? = null,
    val retain: Boolean = false,
    val eraseAfterPlay: Boolean = false,
    val requiresParadox: Int = 0,
    val unplayable: Boolean = false,
    val flavor: String = "",
    /** Why this card may sit outside the ±10% budget window (docs/08 "Budget Targets"). Reviewed in playtests. */
    val budgetException: String? = null,
) {
    /** The default face (face A for Fork cards). */
    val baseFace: Face get() = forkFaces?.first() ?: Face(type, effects)

    init {
        require(forkFaces == null || forkFaces.size == 2) { "$id: Fork cards need exactly two faces" }
        require(type != CardType.CONSTANT || constant != null) { "$id: Constants need a ConstantSpec" }
    }
}

data class OperativeDef(
    val id: String,
    val name: String,
    val faction: Faction,
    val maxHp: Int,
    val signature: Signature,
    val starterDeck: List<String>,
    val otherHandSize: Int = 0,
) {
    val color: EnergyColor get() = faction.color ?: error("$id: operative faction needs an energy color")
}
