package dcbb.core.model

/** A status that matching enemies start the combat with (Ripples, Paradox thresholds). Null filters match anyone. */
data class EnemyBoost(
    val status: StatusType,
    val n: Int,
    val faction: Faction? = null,
    val enemyId: String? = null,
    val eliteOnly: Boolean = false,
) {
    fun matches(def: EnemyDef): Boolean =
        (faction == null || def.faction == faction) && (enemyId == null || def.id == enemyId) && (!eliteOnly || def.elite)
}

/**
 * Rule changes a run brings into a combat: Artifacts, Ripples and Paradox thresholds (docs/05). They live in the
 * combat state, so a saved fight keeps them, and they combine with [plus].
 */
data class CombatMods(
    /** Resolved after the opening Dawn, once you hold your first hand (Antikythera Gear: Foresee 3). */
    val startEffects: List<Effect> = emptyList(),
    val reservoirCap: Int = DEFAULT_RESERVOIR_CAP,
    /** Damage you take from Fixed intents, in percent (Gnomon Splinter: 80). */
    val fixedDamagePct: Int = 100,
    /** Clockwork Sparrow: the first time each combat you Delay an enemy intent, it gains no Pressure. */
    val freeFirstDelay: Boolean = false,
    /** Lovelace's Notes: at the start of each combat, a random Subroutine from your Future is installed for free. */
    val installSubroutine: Boolean = false,
    /** Misprinted Psalter: your Attack cards deal +1 damage per this much Paradox (0 = off). */
    val attackPerParadox: Int = 0,
    /** Enemy max HP in percent, by faction (The Engine Never Built: Convergence 90). */
    val enemyHpPct: Map<Faction, Int> = emptyMap(),
    val enemyBoosts: List<EnemyBoost> = emptyList(),
    /** Ambush: the enemies act once before your first turn. */
    val ambush: Boolean = false,
) {
    operator fun plus(o: CombatMods) = CombatMods(
        startEffects = startEffects + o.startEffects,
        reservoirCap = maxOf(reservoirCap, o.reservoirCap),
        fixedDamagePct = fixedDamagePct * o.fixedDamagePct / 100,
        freeFirstDelay = freeFirstDelay || o.freeFirstDelay,
        installSubroutine = installSubroutine || o.installSubroutine,
        attackPerParadox = listOf(attackPerParadox, o.attackPerParadox).filter { it > 0 }.minOrNull() ?: 0,
        enemyHpPct = (enemyHpPct.keys + o.enemyHpPct.keys).associateWith { f ->
            (enemyHpPct[f] ?: 100) * (o.enemyHpPct[f] ?: 100) / 100
        },
        enemyBoosts = enemyBoosts + o.enemyBoosts,
        ambush = ambush || o.ambush,
    )

    companion object {
        const val DEFAULT_RESERVOIR_CAP = 6
        val NONE = CombatMods()
    }
}
