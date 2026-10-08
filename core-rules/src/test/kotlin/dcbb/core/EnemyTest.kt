package dcbb.core

import dcbb.core.engine.EndTurn
import dcbb.core.engine.GameEvent
import dcbb.core.engine.PlayCard
import dcbb.core.model.EnergyColor.FAITH
import dcbb.core.model.EnergyColor.NEUTRAL
import dcbb.core.state.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnemyTest {

    @Test
    fun `Forked intents resolve to one of their two outcomes`() {
        val outcomes = (1L..40L).map { seed ->
            val out = start("vowknight", "saltwaste.mirage", seed = seed).ok(EndTurn)
            out.events.filterIsInstance<GameEvent.IntentResolved>().single().label
        }.toSet()
        assertEquals(setOf("Attack 12", "Guard 8"), outcomes)
    }

    @Test
    fun `Observe collapses a Forked intent early`() {
        val s = start("vowknight", "saltwaste.mirage").withPresent("neutral.observe")
        val out = s.ok(PlayCard(s.player.present[0].uid)).state.settle()
        val item = out.intentOf("saltwaste.mirage")
        val collapsed = assertNotNull(item.collapsed)
        val resolved = out.ok(EndTurn).events.filterIsInstance<GameEvent.IntentResolved>().single().label
        assertEquals(if (collapsed == 0) item.label else item.altLabel, resolved)
    }

    @Test
    fun `Predictive enemies read your Present at Dawn`() {
        val attacks = start("vowknight", "conv.brass_proxy").withFuture(*Array(5) { "order.line_strike" }).ok(EndTurn).state
        assertEquals("Guard 8 + Attack 6", attacks.intentOf("conv.brass_proxy").label)
        val skills = start("vowknight", "conv.brass_proxy").withFuture(*Array(5) { "order.shield_of_the_line" }).ok(EndTurn).state
        assertEquals("Attack 10", skills.intentOf("conv.brass_proxy").label)
    }

    @Test
    fun `the Double copies the last card you played`() {
        var s = start("vowknight", "errata.double").withPresent("order.line_strike").withEnergy(FAITH to 1)
        s = s.ok(PlayCard(s.player.present[0].uid)).state.ok(EndTurn).state
        assertEquals("Copy: Attack 5", s.intentOf("errata.double").label)
    }

    @Test
    fun `elites can hold two intents, one of them Fixed`() {
        val s = start("vowknight", "order.inquisitor")
        val intents = s.track.filter { it.kind == TrackKind.INTENT }
        assertEquals(setOf("strike", "purge"), intents.map { it.slot }.toSet())
        assertTrue(intents.single { it.slot == "purge" }.fixed)
    }

    @Test
    fun `Unpick Blanks the top of your Future, and Blank cards can't be played`() {
        val s = start("vowknight", "saltwaste.salt_wraith").ok(EndTurn).state
        val blank = s.player.present.firstOrNull { it.blank } ?: s.player.future.first { it.blank }
        if (blank in s.player.present) assertTrue("Blank" in s.rejects(PlayCard(blank.uid)))
    }

    @Test
    fun `Snip stops your Constants for a turn`() {
        var s = start("oracle", "tear.the_rent").withConstants("conv.watchdog")
        s = s.ok(EndTurn).state // Snip resolves: next turn's Constants are silenced
        assertTrue(s.player.constantsSilenced)
        val quiet = s.ok(EndTurn)
        assertTrue(quiet.events.none { it is GameEvent.BlockGained }, "Watchdog.exe stays silent")
        assertTrue(!quiet.state.player.constantsSilenced)
    }

    @Test
    fun `killing an enemy removes its intents from the Track`() {
        var s = start("vowknight", "conv.drone", "conv.drone").withPresent("order.crusaders_charge", "order.line_strike")
            .withEnergy(FAITH to 2, NEUTRAL to 1)
        val first = s.enemies[0].uid
        s = s.ok(PlayCard(s.present("order.crusaders_charge").uid, target = first)).state
        assertTrue(!s.enemy(first)!!.alive)
        assertTrue(s.track.none { it.enemyUid == first })
        assertTrue("defeated" in s.rejects(PlayCard(s.player.present[0].uid, target = first)))
        s.ok(PlayCard(s.player.present[0].uid)) // one enemy left: targeted automatically
    }
}
