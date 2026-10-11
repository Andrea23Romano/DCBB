package dcbb.core.model

/** A status that matching enemies start the combat with (Ripples, Paradox thresholds). Null filters match anyone. */
data class EnemyBoost(
    val status: StatusType,
    val n: Int,
    val faction: Faction? = null,
    val enemyId: String? = null,
    val eliteOnly: Boolean = false,
    /** Only enemies that read your Present (MERIDIAN Shard). */
    val predictiveOnly: Boolean = false,
) {
    fun matches(def: EnemyDef): Boolean =
        (faction == null || def.faction == faction) && (enemyId == null || def.id == enemyId) && (!eliteOnly || def.elite) &&
            (!predictiveOnly || def.ai.predictive)
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
    /** Every enemy's max HP in percent (an Empowered Elite: 125). */
    val hpPct: Int = 100,
    val enemyBoosts: List<EnemyBoost> = emptyList(),
    /** Ambush: the enemies act once before your first turn. */
    val ambush: Boolean = false,
    /** Your Borrow limit per turn; null keeps the rules' 2 (Long Credit: 3, Aurelian's Seal: 0). The lowest wins. */
    val borrowLimit: Int? = null,
    /** Energy you gain at every Dawn on top of your income, by color (Aurelian's Seal: +1 Faith). */
    val extraIncome: Map<EnergyColor, Int> = emptyMap(),
    /** Added to every Foresee you resolve (MERIDIAN Shard). */
    val foreseeBonus: Int = 0,
    /** Added to your Signature Action: Strike's damage, Forecast's Foresee (Precomputed). */
    val signatureBonus: Int = 0,
    /** More cards in your Other Hand. Operatives without one gain one that holds 1 + this (Margin Notes, Thousandfold's Ribbon). */
    val otherHandBonus: Int = 0,
    /** Cards drawn at every Dawn beyond the usual 5. */
    val drawBonus: Int = 0,
    /** The Paradox at which you Unravel (Unstable Equilibrium: 12). The highest wins. */
    val unravelAt: Int = DEFAULT_UNRAVEL,
    /** The first Vow you break each combat costs no Penance (Keeper of Vows). */
    val firstPenanceFree: Boolean = false,
    /** Remember also counts the cards in your Erased (Long Memory). */
    val rememberErased: Boolean = false,
    /** Your first Subroutine each combat costs this much less (Cache Hit). */
    val firstSubroutineDiscount: Int = 0,
    /** The first Known card you play each turn draws this many cards (Bayesian Prior). */
    val knownDraw: Int = 0,
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
        hpPct = hpPct * o.hpPct / 100,
        enemyBoosts = enemyBoosts + o.enemyBoosts,
        ambush = ambush || o.ambush,
        borrowLimit = listOfNotNull(borrowLimit, o.borrowLimit).minOrNull(),
        extraIncome = (extraIncome.keys + o.extraIncome.keys).associateWith { (extraIncome[it] ?: 0) + (o.extraIncome[it] ?: 0) },
        foreseeBonus = foreseeBonus + o.foreseeBonus,
        signatureBonus = signatureBonus + o.signatureBonus,
        otherHandBonus = otherHandBonus + o.otherHandBonus,
        drawBonus = drawBonus + o.drawBonus,
        unravelAt = maxOf(unravelAt, o.unravelAt),
        firstPenanceFree = firstPenanceFree || o.firstPenanceFree,
        rememberErased = rememberErased || o.rememberErased,
        firstSubroutineDiscount = firstSubroutineDiscount + o.firstSubroutineDiscount,
        knownDraw = knownDraw + o.knownDraw,
    )

    companion object {
        const val DEFAULT_UNRAVEL = 10
        const val DEFAULT_RESERVOIR_CAP = 6
        val NONE = CombatMods()
    }
}
