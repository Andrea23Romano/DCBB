package dcbb.core

import dcbb.core.engine.EndTurn
import dcbb.core.engine.GameEvent
import dcbb.core.engine.PlayCard
import dcbb.core.engine.ResolveForesee
import dcbb.core.model.EnergyColor.COMPUTE
import dcbb.core.model.EnergyColor.NEUTRAL
import dcbb.core.state.Phase
import dcbb.core.state.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackTest {

    @Test
    fun `Delay adds countdown and Pressure, and Pressure makes the hit bigger`() {
        var s = start("oracle", "london.rookery_brawler").withPresent("neutral.hold_the_hour").withEnergy(NEUTRAL to 1)
        val intent = s.intentOf("london.rookery_brawler")
        assertEquals(1, intent.countdown)

        s = s.ok(PlayCard(s.player.present[0].uid, trackTarget = intent.id)).state
        assertEquals(Phase.AWAIT_FORESEE, s.phase, "Hold the Hour also Foresees 1")
        s = s.ok(ResolveForesee(s.pending!!.uids)).state
        val delayed = s.trackItem(intent.id)!!
        assertEquals(2, delayed.countdown)
        assertEquals(1, delayed.pressure)
        assertEquals(12, engine.intentDamage(s, delayed))

        val quiet = s.ok(EndTurn)
        assertTrue(quiet.events.none { it is GameEvent.DamageToPlayer }, "the delayed hit doesn't land this round")
        val loud = quiet.state.ok(EndTurn)
        val resolved = loud.events.filterIsInstance<GameEvent.IntentResolved>().single()
        assertEquals(1, resolved.pressure)
        assertEquals(12, loud.events.filterIsInstance<GameEvent.DamageToPlayer>().single().amount)
        val next = loud.state.intentOf("london.rookery_brawler")
        assertEquals(0, next.pressure, "Pressure resets when the intent resolves")
        assertEquals(2, next.countdown, "the Brawler's next move is its charge")
    }

    @Test
    fun `Fixed intents can't be Delayed`() {
        val s = start("oracle", "order.inquisitor").withPresent("neutral.hold_the_hour").withEnergy(NEUTRAL to 1)
        val purge = s.intentOf("order.inquisitor", "purge")
        val strike = s.intentOf("order.inquisitor", "strike")
        assertTrue(purge.fixed)
        assertEquals(3, purge.countdown)
        assertTrue("Fixed" in s.rejects(PlayCard(s.player.present[0].uid, trackTarget = purge.id)))
        val out = s.ok(PlayCard(s.player.present[0].uid))
        assertEquals(2, out.state.trackItem(strike.id)!!.countdown, "the only Delayable item is chosen automatically")
    }

    @Test
    fun `Scheduled effects resolve before enemy intents`() {
        var s = start("oracle", "london.gaslight_footpad").withPresent("neutral.stitch_in_time").withEnergy(NEUTRAL to 1)
        s = s.ok(PlayCard(s.player.present[0].uid)).state
        val scheduled = s.track.single { it.kind == TrackKind.SCHEDULED }
        assertEquals(2, scheduled.countdown)

        s = s.ok(EndTurn).state // Attack 9 against 3 Block
        assertEquals(65 - 6, s.player.hp)
        val out = s.ok(EndTurn) // the Scheduled Block lands first, then Attack 4 + Weak 1
        val blockAt = out.events.indexOfFirst { it is GameEvent.BlockGained }
        val hitAt = out.events.indexOfFirst { it is GameEvent.DamageToPlayer }
        assertTrue(blockAt in 0 until hitAt, "Scheduled Block must resolve before the attack")
        val hit = out.events.filterIsInstance<GameEvent.DamageToPlayer>().single()
        assertEquals(3, hit.blocked)
        assertEquals(1, hit.amount)
    }

    @Test
    fun `Disrupt delays your Scheduled effects and Prune Erases the top of your Future`() {
        var s = start("oracle", "conv.pruner").withPresent("neutral.stitch_in_time").withEnergy(NEUTRAL to 1)
        s = s.ok(PlayCard(s.player.present[0].uid)).state
        val erasedBefore = s.player.erased.size
        s = s.ok(EndTurn).state
        val scheduled = s.track.single { it.kind == TrackKind.SCHEDULED }
        assertEquals(2, scheduled.countdown, "ticked to 1, then Disrupted back to 2")
        assertEquals(erasedBefore + 1, s.player.erased.size)
    }

    @Test
    fun `a Delay with no legal target fizzles instead of blocking the card`() {
        var s = start("oracle", "order.inquisitor").withPresent("neutral.hold_the_hour").withEnergy(NEUTRAL to 1)
        s = s.copy(track = s.track.filter { it.fixed })
        val out = s.ok(PlayCard(s.player.present[0].uid))
        assertTrue(out.events.none { it is GameEvent.Delayed })
    }
}
