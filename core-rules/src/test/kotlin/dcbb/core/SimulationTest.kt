package dcbb.core

import dcbb.core.bot.GreedyBot
import dcbb.core.bot.Moves
import dcbb.core.bot.RandomBot
import dcbb.core.bot.Runner
import dcbb.core.engine.Action
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.EndTurn
import dcbb.core.engine.Engine
import dcbb.core.state.CombatState
import dcbb.core.state.Phase
import dcbb.core.state.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SimulationTest {

    @Test
    fun `the same seed and the same actions give the same combat`() {
        fun play(seed: Long): Pair<CombatState, List<String>> {
            val bot = GreedyBot(seed)
            val setup = CombatSetup("splinter", content.deck("splinter", "mid"), listOf("errata.misprint", "errata.double"), seed)
            var out = engine.start(setup)
            val log = out.events.map { it.text }.toMutableList()
            while (!out.state.phase.over) {
                out = engine.apply(out.state, bot.act(engine, out.state))
                log += out.events.map { it.text }
            }
            return out.state to log
        }
        val (a, logA) = play(42)
        val (b, logB) = play(42)
        assertEquals(a, b)
        assertEquals(logA, logB)
        assertNotEquals(logA, play(43).second)
    }

    @Test
    fun `fights with the greedy bot end, and it beats the random bot`() {
        var greedyWins = 0
        var randomWins = 0
        for (enc in content.encounters) for (op in content.operatives.keys) {
            val setup = CombatSetup(op, content.deck(op, "starter"), enc.enemies, seed = enc.id.hashCode().toLong())
            val g = Runner.fight(engine, setup, GreedyBot(1))
            val r = Runner.fight(engine, setup, RandomBot(1))
            assertTrue(g.phase.over && r.phase.over)
            assertEquals(0, g.stats.botErrors, "greedy bot chose an illegal action vs ${enc.id}")
            if (g.won) greedyWins++
            if (r.won) randomWins++
        }
        assertTrue(greedyWins >= randomWins, "greedy $greedyWins vs random $randomWins")
    }

    @Test
    fun `engine invariants hold through hundreds of random fights`() {
        var fights = 0
        for (seed in 1L..12L) for (enc in content.encounters) for (op in content.operatives.keys) {
            val deck = content.deck(op, if (seed % 2 == 0L) "mid" else "starter")
            val bot = RandomBot(seed * 7 + fights)
            var out = engine.start(CombatSetup(op, deck, enc.enemies, seed * 1000 + fights))
            check(out.state, deck.size, "start")
            var steps = 0
            while (!out.state.phase.over && steps++ < 3_000) {
                val before = out.state
                val action: Action = bot.act(engine, before)
                out = engine.apply(before, action)
                if (out.error != null) {
                    assertEquals(before, out.state, "a rejected action must not change the state")
                    out = engine.apply(before, Moves.candidates(engine, before).first { engine.apply(before, it).error == null })
                }
                check(out.state, deck.size, "$op vs ${enc.id}, seed $seed, after $action")
                if (action == EndTurn && !out.state.phase.over && out.state.pending == null) armed(out.state)
            }
            assertTrue(out.state.phase.over, "$op vs ${enc.id} didn't end")
            fights++
        }
        assertTrue(fights > 400)
    }

    private fun check(s: CombatState, deckSize: Int, where: String) {
        val p = s.player
        fun ensure(ok: Boolean, what: String) {
            if (!ok) fail("$what ($where)")
        }
        val uids = p.allCards.map { it.uid }
        ensure(uids.size == uids.toSet().size, "a card is in two places at once")
        ensure(p.allCards.count { !it.bleed } == deckSize, "cards were created or lost")
        ensure(p.hp <= p.maxHp, "HP above max")
        ensure(p.energy.values.all { it > 0 } && p.debt.values.all { it > 0 }, "zero or negative pools")
        ensure(p.debtTotal <= Engine.BORROW_LIMIT && p.borrowedThisTurn <= Engine.BORROW_LIMIT, "Borrow limit broken")
        ensure(p.constants.size <= Engine.CONSTANT_SLOTS, "too many Constants")
        ensure(p.present.size <= Engine.HAND_LIMIT, "Present over 10")
        ensure(p.otherHand.size <= content.operative(p.operativeId).otherHandSize, "Other Hand too big")
        ensure(p.paradox in 0 until Engine.UNRAVEL_AT, "Paradox out of range")
        ensure(s.enemies.all { !it.alive || it.hp > 0 }, "a living enemy with no HP")
        ensure(s.track.all { it.kind == TrackKind.SCHEDULED || s.enemy(it.enemyUid!!)!!.alive }, "a dead enemy's intent")
        ensure(s.track.map { it.id }.toSet().size == s.track.size, "duplicate Track ids")
        if (!s.phase.over && s.pending == null) {
            ensure(s.phase == Phase.PLAYER_TURN && s.queue.isEmpty() && p.resolving == null, "engine stopped mid-work")
        }
        if (s.pending != null) ensure(s.phase == Phase.AWAIT_FORESEE, "Foresee pending outside AWAIT_FORESEE")
    }

    /** At the start of your turn every living enemy has an intent in each of its slots. */
    private fun armed(s: CombatState) {
        for (e in s.livingEnemies) {
            for (slot in content.enemy(e.defId).ai.slots) {
                assertTrue(
                    s.track.any { it.kind == TrackKind.INTENT && it.enemyUid == e.uid && it.slot == slot },
                    "${e.defId} has no intent for $slot in round ${s.round}",
                )
            }
        }
    }
}
