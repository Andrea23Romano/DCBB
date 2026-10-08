package dcbb.core.bot

import dcbb.core.budget.Budget
import dcbb.core.content.Content
import dcbb.core.model.Bonus
import dcbb.core.engine.Engine
import dcbb.core.engine.Rng
import dcbb.core.engine.Stream
import dcbb.core.model.CardDef
import dcbb.core.model.Cond
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.Face
import dcbb.core.model.Signature
import dcbb.core.model.StatusType
import dcbb.core.state.CardInst
import dcbb.core.state.CombatState
import dcbb.core.state.Phase
import dcbb.core.state.TrackKind
import kotlin.math.max

/**
 * Card values in damage-equivalents: budget points / 2 (docs/08 anchor: 12 points ≈ 6 damage).
 * Values come from an immutable table built once per content, so bots can share it across threads.
 */
object CardValues {
    /** Calculated is a sure thing for a Known card and impossible for an unknown one. */
    private val KNOWN = Budget.CondOdds { if (it == Cond.Calculated) 1.0 else Budget.factor(it) }
    private val UNKNOWN = Budget.CondOdds { if (it == Cond.Calculated) 0.0 else Budget.factor(it) }

    private class Table(val content: Content) {
        val standard: Map<String, Double> = content.cards.mapValues { Budget.points(it.value) }
        val known: Map<String, Double> = content.cards.mapValues { Budget.points(it.value, KNOWN) }
        val unknown: Map<String, Double> = content.cards.mapValues { Budget.points(it.value, UNKNOWN) }
        val calculated: Set<String> = content.cards.values.filter { mentionsCalculated(it) }.map { it.id }.toSet()
        val faces: Map<Face, Double> = buildMap {
            for (faces in content.misprintTables.values) for (f in faces) put(f, Budget.face(f))
            for (def in content.cards.values) for (f in def.misprintFaces.orEmpty() + def.forkFaces.orEmpty()) put(f, Budget.face(f))
        }
    }

    // Racy but safe: a Table is immutable, so at worst two threads build equal tables.
    private var table: Table? = null

    private fun table(content: Content): Table {
        val t = table
        if (t != null && t.content === content) return t
        return Table(content).also { table = it }
    }

    fun points(content: Content, def: CardDef): Double = table(content).standard.getValue(def.id)

    private fun facePoints(t: Table, f: Face) = t.faces[f] ?: Budget.face(f)

    /**
     * What [inst] is worth if played now: Known cards get their Calculated bonuses, unknown ones don't, and Strike
     * buffs are worth nothing to an operative without Strike.
     */
    fun value(engine: Engine, state: CombatState, inst: CardInst): Double {
        if (inst.blank) return 0.0
        val def = engine.content.card(inst.defId)
        if (def.unplayable) return 0.0
        val t = table(engine.content)
        var base = if (inst.known) t.known.getValue(def.id) else t.unknown.getValue(def.id)
        val spec = def.constant
        if (spec != null && !strikes(engine, state)) base = maxOf(0.0, base - Budget.strikePart(spec))
        val face = inst.face
        val granted = inst.granted
        val pts = when {
            face != null -> facePoints(t, face)
            granted != null -> max(base, facePoints(t, granted) + 2.0)
            else -> base
        }
        return pts / 2.0
    }

    /**
     * What an installed Constant is worth. A Vow's restriction is left out: the bot pays for it in play, by avoiding
     * the restricted move or taking the Penance, so subtracting the budget credit too would count it twice.
     */
    fun installedValue(engine: Engine, state: CombatState, def: CardDef): Double {
        val spec = def.constant ?: return points(engine.content, def) / 2.0
        var pts = points(engine.content, def) + (spec.vow?.budgetCredit ?: 0.0)
        if (!strikes(engine, state)) pts -= Budget.strikePart(spec)
        return maxOf(0.0, pts) / 2.0
    }

    private fun strikes(engine: Engine, state: CombatState) =
        engine.content.operative(state.player.operativeId).signature == Signature.STRIKE

    fun bestInPast(engine: Engine, state: CombatState, n: Int): List<Int> =
        state.player.past.sortedByDescending { value(engine, state, it) }.take(n).map { it.uid }

    fun usesCalculated(content: Content, def: CardDef): Boolean = def.id in table(content).calculated

    private fun mentionsCalculated(def: CardDef): Boolean =
        mentionsCalculated(def.effects) || def.forkFaces.orEmpty().any { mentionsCalculated(it.effects) }

    private fun mentionsCalculated(effects: List<Effect>): Boolean = effects.any { e ->
        when (e) {
            is Effect.When -> e.cond == Cond.Calculated || mentionsCalculated(e.then) || mentionsCalculated(e.otherwise)
            is Effect.Damage -> e.amount.bonuses.any { it is Bonus.If && it.cond == Cond.Calculated }
            else -> false
        }
    }
}

/**
 * The bots' position evaluation, in damage-equivalents, from the point of view of the player mid-turn:
 * HP, enemy HP and kills, the coming enemy phase's unblocked damage, charging threats, stored energy,
 * installed Constants, and the surplus value of cards still playable this turn.
 */
object Eval {
    private const val KILL_BONUS = 8.0
    private const val ENERGY_COLOR = 2.5
    private const val ENERGY_OTHER = 1.5
    private const val DEBT = 3.0
    private const val CONSTANT_WEIGHT = 1.2
    private const val OPTION_WEIGHT = 0.8

    /**
     * [includeHand] adds the value of cards still playable this turn. Leave it out to value "end the turn now":
     * the Present is about to be discarded, so only Retain cards keep their value.
     */
    fun score(engine: Engine, s: CombatState, includeHand: Boolean = true): Double {
        val p = s.player
        when (s.phase) {
            // Debt outlives the combat (it is repaid at the next one's first Dawn), so a win with Debt is worth less.
            Phase.WON -> return 10_000.0 + (p.hp - DEBT * p.debtTotal) * 10.0
            Phase.LOST -> return -10_000.0 - s.livingEnemies.sumOf { it.hp }
            Phase.DRAW -> return -5_000.0
            else -> Unit
        }
        var v = p.hp.toDouble()
        for (e in s.enemies) {
            if (!e.alive) {
                v += KILL_BONUS
                continue
            }
            v -= e.hp
            v += 2.5 * e.status(StatusType.EXPOSED) + 1.0 * e.status(StatusType.WEAK) + 2.0 * e.status(StatusType.SLOW)
            val burn = e.status(StatusType.BURN)
            v += burn * (burn + 1) / 2.0
        }
        v -= threat(engine, s)
        v += energyValue(engine, s)
        v -= DEBT * p.debtTotal
        v += p.constants.sumOf { CardValues.installedValue(engine, s, engine.content.card(it.defId)) * CONSTANT_WEIGHT }
        v += if (includeHand) {
            handOption(engine, s)
        } else {
            0.5 * p.present.filter { it.retainThisTurn || engine.content.card(it.defId).retain }.sumOf { CardValues.value(engine, s, it) }
        }
        v += 0.3 * p.otherHand.sumOf { CardValues.value(engine, s, it) }
        v -= 0.8 * max(0, p.paradox - 5)
        v += 2.5 * p.status(StatusType.PLATE) + 4.0 * p.status(StatusType.MIGHT)
        v -= 3.0 * p.status(StatusType.WEAK) + 2.0 * p.status(StatusType.GLITCH)
        v -= 1.5 * p.future.count { it.blank } + 1.0 * p.erased.size
        v += p.future.take(Engine.DRAW_PER_TURN).sumOf { c ->
            if (!c.known) 0.0 else if (CardValues.usesCalculated(engine.content, engine.content.card(c.defId))) 0.6 else 0.2
        }
        for (item in s.track) {
            if (item.kind != TrackKind.SCHEDULED) continue
            val nonBlock = item.effects.filter { it !is Effect.Block }
            v += Budget.effects(nonBlock) / 2.0 * 0.7
            if (item.countdown > 1) v += Budget.effects(item.effects.filterIsInstance<Effect.Block>()) / 2.0 * 0.5
        }
        return v
    }

    /** Unblocked damage expected in the coming enemy phase, your own Burn, and discounted charging threats. */
    /** Damage expected in the coming enemy phase that your current Block, Plate and Dusk/Scheduled Block won't stop. */
    fun unblocked(engine: Engine, s: CombatState): Double {
        val p = s.player
        var defense = p.block.toDouble() + p.status(StatusType.PLATE)
        if (!p.constantsSilenced) {
            defense += p.constants.sumOf { c ->
                engine.constantSpec(c)?.dusk?.filterIsInstance<Effect.Block>()?.sumOf { it.amount.base } ?: 0
            }
        }
        defense += s.track.filter { it.kind == TrackKind.SCHEDULED && it.countdown <= 1 }
            .sumOf { item -> item.effects.filterIsInstance<Effect.Block>().sumOf { it.amount.base } }
        return max(0.0, engine.expectedIncoming(s) - defense)
    }

    fun threat(engine: Engine, s: CombatState): Double {
        val p = s.player
        var t = unblocked(engine, s)
        val burn = p.status(StatusType.BURN)
        t += burn
        for (item in s.track) {
            if (item.kind != TrackKind.INTENT) continue
            if (item.countdown >= 2) {
                val dmg = if (item.forked && item.collapsed == null) {
                    (engine.intentDamage(s, item, 0) + engine.intentDamage(s, item, 1)) / 2.0
                } else {
                    engine.intentDamage(s, item).toDouble()
                }
                t += dmg * if (item.countdown == 2) 0.7 else 0.5
            } else if (item.actions.any { it !is EnemyAction.Attack && it !is EnemyAction.AttackPer && it !is EnemyAction.Guard }) {
                t += 2.0
            }
        }
        return t
    }

    private fun energyValue(engine: Engine, s: CombatState): Double {
        val color = engine.content.operative(s.player.operativeId).color
        val own = s.player.energyOf(color)
        val other = s.player.energyTotal - own
        val cap = s.mods.reservoirCap
        val ownKept = minOf(own, cap)
        val otherKept = minOf(other, cap - ownKept)
        return ownKept * ENERGY_COLOR + otherKept * ENERGY_OTHER
    }

    /**
     * Surplus value of the cards you could still afford this turn, best first. Block only counts against damage that
     * is actually coming, so a hand of Shields facing no attack is worth nothing.
     */
    private fun handOption(engine: Engine, s: CombatState): Double {
        var energy = s.player.energyTotal
        var threatLeft = unblocked(engine, s)
        var total = 0.0
        val options = s.player.present.mapNotNull { c ->
            if (c.blank || engine.content.card(c.defId).unplayable) return@mapNotNull null
            val face = engine.faces(c).maxByOrNull { Budget.face(it) } ?: return@mapNotNull null
            val block = face.effects.filterIsInstance<Effect.Block>().sumOf { Budget.amount(it.amount) }
            val other = if (face.effects.size == face.effects.count { it is Effect.Block }) {
                0.0
            } else {
                CardValues.value(engine, s, c) - Budget.effects(face.effects.filterIsInstance<Effect.Block>()) / 2.0
            }
            Option(block, other, engine.effectiveCost(s, c).total)
        }.sortedByDescending { it.block + it.other - ENERGY_COLOR * it.cost }
        for (o in options) {
            if (o.cost > energy) continue
            val blockValue = minOf(o.block, threatLeft)
            val surplus = blockValue + o.other - ENERGY_COLOR * o.cost
            if (surplus <= 0.0) continue
            total += surplus * OPTION_WEIGHT
            energy -= o.cost
            threatLeft -= blockValue
        }
        return total
    }

    private class Option(val block: Double, val other: Double, val cost: Int)

    /**
     * A copy of [state] with the unknown part of the Future reshuffled and a fresh RNG, so bots can look one action
     * ahead without peeking at hidden cards or at the real random rolls.
     */
    fun determinize(state: CombatState, seed: Long): CombatState {
        val p = state.player
        val unknownIdx = p.future.indices.filter { !p.future[it].known }
        val (shuffled, _) = Rng.seeded(seed).shuffled(unknownIdx.map { p.future[it] }, Stream.SHUFFLE)
        val future = p.future.toMutableList()
        unknownIdx.forEachIndexed { i, idx -> future[idx] = shuffled[i] }
        return state.copy(player = p.copy(future = future), rng = Rng.seeded(seed * 31 + 17))
    }
}
