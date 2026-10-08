package dcbb.core.engine

import dcbb.core.model.Cost
import dcbb.core.model.EnergyColor

/** How a cost gets paid: energy spent from the pool, energy borrowed from next turn, and whether that is Attuned. */
data class Payment(val spend: Map<EnergyColor, Int>, val borrow: Map<EnergyColor, Int>, val attuned: Boolean) {
    val borrowed: Int get() = borrow.values.sum()
}

/**
 * Payment rules (docs/04 "Costs and paying"):
 * colored pips need that color, generic pips take any energy (Neutral included),
 * Attuned means the whole cost was paid with the card's faction color, and Borrow covers shortfalls in income colors.
 */
object Payments {
    private val COLORED = listOf(EnergyColor.FAITH, EnergyColor.COMPUTE, EnergyColor.FLUX)

    fun plan(
        cost: Cost,
        pool: Map<EnergyColor, Int>,
        factionColor: EnergyColor?,
        incomeColors: Set<EnergyColor>,
        style: PayStyle,
        borrowLeft: Int,
    ): Payment? {
        val attempts = buildList {
            if (style == PayStyle.AUTO && factionColor != null) add(Attempt(attune = true, borrow = 0))
            add(Attempt(attune = false, borrow = 0))
            if (borrowLeft > 0) {
                if (style == PayStyle.AUTO && factionColor != null) add(Attempt(attune = true, borrow = borrowLeft))
                add(Attempt(attune = false, borrow = borrowLeft))
            }
        }
        for (a in attempts) {
            val p = tryPlan(cost, pool, factionColor, incomeColors, a)
            if (p != null) return p
        }
        return null
    }

    private data class Attempt(val attune: Boolean, val borrow: Int)

    private fun tryPlan(
        cost: Cost,
        pool: Map<EnergyColor, Int>,
        factionColor: EnergyColor?,
        incomeColors: Set<EnergyColor>,
        a: Attempt,
    ): Payment? {
        val avail = pool.toMutableMap()
        val spend = mutableMapOf<EnergyColor, Int>()
        val borrow = mutableMapOf<EnergyColor, Int>()
        var borrowLeft = a.borrow

        fun take(color: EnergyColor, n: Int): Int {
            val have = avail[color] ?: 0
            val t = minOf(have, n)
            if (t > 0) {
                avail[color] = have - t
                spend.merge(color, t, Int::plus)
            }
            return t
        }

        fun borrowOf(color: EnergyColor, n: Int): Boolean {
            if (n <= 0) return true
            if (color !in incomeColors || borrowLeft < n) return false
            borrowLeft -= n
            borrow.merge(color, n, Int::plus)
            return true
        }

        for (color in COLORED) {
            val need = cost.colored(color)
            val missing = need - take(color, need)
            if (!borrowOf(color, missing)) return null
        }

        var generic = cost.generic
        if (a.attune) {
            if (factionColor == null) return null
            generic -= take(factionColor, generic)
            if (!borrowOf(factionColor, generic)) return null
        } else {
            val order = buildList {
                add(EnergyColor.NEUTRAL)
                COLORED.filter { it != factionColor }.forEach { add(it) }
                if (factionColor != null) add(factionColor)
            }
            for (color in order) {
                if (generic == 0) break
                generic -= take(color, generic)
            }
            if (generic > 0) {
                val borrowColor = when {
                    EnergyColor.NEUTRAL in incomeColors -> EnergyColor.NEUTRAL
                    factionColor != null -> factionColor
                    else -> return null
                }
                if (!borrowOf(borrowColor, generic)) return null
            }
        }

        val used = (spend.keys + borrow.keys).filter { (spend[it] ?: 0) + (borrow[it] ?: 0) > 0 }
        val attuned = factionColor != null && cost.total > 0 && used.isNotEmpty() && used.all { it == factionColor }
        return Payment(spend, borrow, attuned)
    }
}
