package dcbb.core.run

import dcbb.core.content.Content
import dcbb.core.engine.Rng
import dcbb.core.engine.Stream
import dcbb.core.model.CardDef
import dcbb.core.model.CardType
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.IntentSpec
import dcbb.core.model.StatusType
import dcbb.core.text.RulesText

/**
 * The Echo of You (docs/03 "Enemy Rosters"): the self you abandoned by Rewinding, playing the deck it had then.
 * Cards map to intents deterministically: Attacks become Attacks, Skills become Guards (or Might when they have no
 * Block), Constants become Plate. It plays two cards per round, in a seeded order, then loops.
 */
object Echoes {
    const val ENCOUNTER = "echo_of_you"
    const val HP_PCT = 60

    fun intents(content: Content, deck: List<String>, seed: Long): List<IntentSpec> {
        val (order, _) = Rng.seeded(seed).shuffled(deck, Stream.MISC)
        return order.chunked(2).map { pair ->
            val actions = merge(pair.flatMap { actions(content.card(it)) })
            IntentSpec("main", "Echo: " + RulesText.intent(actions), actions)
        }
    }

    fun actions(def: CardDef): List<EnemyAction> {
        val face = def.forkFaces?.first() ?: def.misprintFaces?.first() ?: def.baseFace
        return when {
            def.type == CardType.CONSTANT -> listOf(EnemyAction.Buff(StatusType.PLATE, 2))
            face.type == CardType.ATTACK -> listOf(EnemyAction.Attack(maxOf(3, damage(face.effects))))
            else -> block(face.effects).let { if (it > 0) listOf(EnemyAction.Guard(it)) else listOf(EnemyAction.Buff(StatusType.MIGHT, 1)) }
        }
    }

    /** One Attack, one Guard and the Buffs, so a round's intent reads as a single line. */
    private fun merge(actions: List<EnemyAction>): List<EnemyAction> {
        val attack = actions.filterIsInstance<EnemyAction.Attack>().sumOf { it.damage * it.hits }
        val guard = actions.filterIsInstance<EnemyAction.Guard>().sumOf { it.block }
        val buffs = actions.filterIsInstance<EnemyAction.Buff>().groupBy { it.status }.map { (s, bs) -> EnemyAction.Buff(s, bs.sumOf { it.n }) }
        return buildList {
            if (guard > 0) add(EnemyAction.Guard(guard))
            if (attack > 0) add(EnemyAction.Attack(attack))
            addAll(buffs)
        }
    }

    private fun damage(effects: List<Effect>): Int = effects.sumOf { e ->
        when (e) {
            is Effect.Damage -> e.amount.base * e.hits
            is Effect.When -> maxOf(damage(e.then), damage(e.otherwise))
            else -> 0
        }
    }

    private fun block(effects: List<Effect>): Int = effects.sumOf { e ->
        when (e) {
            is Effect.Block -> e.amount.base
            is Effect.When -> maxOf(block(e.then), block(e.otherwise))
            is Effect.Schedule -> block(e.effects)
            else -> 0
        }
    }
}
