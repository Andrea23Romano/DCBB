package dcbb.core

import dcbb.core.bot.PlannerBot
import dcbb.core.bot.Runner
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.EndTurn
import dcbb.core.engine.GameEvent
import dcbb.core.engine.PlayCard
import dcbb.core.engine.TrackNeed
import dcbb.core.model.Amount
import dcbb.core.model.Effect
import dcbb.core.model.EnergyColor.FAITH
import dcbb.core.model.EnergyColor.NEUTRAL
import dcbb.core.model.StatusType
import dcbb.core.model.halved
import dcbb.core.state.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The keywords the content expansion brought in: Martyr, Accelerate and Afterimage. */
class ExpansionTest {

    private val footpad = content.enemy("london.gaslight_footpad").maxHp

    @Test
    fun `Martyr answers the first HP you lose in an enemy phase, aimed at whoever hurt you`() {
        var s = start("vowknight", "london.gaslight_footpad", "london.gaslight_footpad").withConstants("order.cilice").withPresent()
        s = s.ok(EndTurn).state // both Footpads Attack 9; only the first hit triggers Martyr
        val (first, second) = s.enemies
        assertEquals(footpad - 5, first.hp, "Cilice deals 5 to the Footpad that hurt you first")
        assertEquals(footpad, second.hp, "Martyr triggers once per enemy phase")
        val again = s.ok(EndTurn).state
        assertEquals(footpad - 5 - 3, again.enemies[0].hp, "and again in the next enemy phase, Weak from that same attack cutting 5 to 3")
    }

    @Test
    fun `Martyr's Vigil gains Plate mid-phase, and Stigmata Might`() {
        var s = start("vowknight", "london.gaslight_footpad", "london.gaslight_footpad").withConstants("order.martyrs_vigil").withPresent()
        s = s.ok(EndTurn).state
        assertEquals(1, s.player.status(StatusType.PLATE), "Plate 2 after the first hit; the second, unblocked, strips one")
        var t = start("vowknight", "london.gaslight_footpad").withConstants("order.stigmata").withPresent()
        t = t.ok(EndTurn).state
        assertEquals(2, t.player.status(StatusType.MIGHT))
        assertEquals(1, t.player.status(StatusType.PLATE))
    }

    @Test
    fun `Accelerate brings a Scheduled effect sooner, never below 1, and skips Fixed intents`() {
        var s = start("vowknight", "saltwaste.sandglass_golem")
            .withPresent("order.foretold_blow", "order.matins_bell")
            .withEnergy(FAITH to 1, NEUTRAL to 1)
        s = s.ok(PlayCard(s.present("order.foretold_blow").uid)).state
        val blow = s.track.single { it.kind == TrackKind.SCHEDULED }
        assertEquals(2, blow.countdown)
        s = s.ok(PlayCard(s.present("order.matins_bell").uid, trackTarget = blow.id)).state
        assertEquals(1, s.trackItem(blow.id)!!.countdown)
        assertTrue(engine.trackCandidates(s, TrackNeed.ACCELERATABLE).none { it.id == blow.id }, "a countdown of 1 can't come sooner")
        val out = s.ok(EndTurn).state
        assertEquals(GOLEM - 8, out.enemies[0].hp, "the Scheduled blow lands this enemy phase, before the Golem's Plate")

        val inq = start("vowknight", "order.inquisitor")
        assertTrue(engine.trackCandidates(inq, TrackNeed.ACCELERATABLE).none { it.fixed })
    }

    @Test
    fun `an Afterimage repeats the card at half value at your next Dawn, on the weakest enemy if its target fell`() {
        var s = start("vowknight", "london.gaslight_footpad").withPresent("neutral.lingering_strike").withEnergy(NEUTRAL to 2)
        s = s.ok(PlayCard(s.player.present[0].uid)).state
        assertEquals(footpad - 8, s.enemies[0].hp)
        assertEquals(1, s.player.afterimages.size)
        val dawn = s.ok(EndTurn)
        assertTrue(dawn.events.any { it is GameEvent.Info && it.text.startsWith("Afterimage: Lingering Strike") })
        assertEquals(footpad - 12, dawn.state.enemies[0].hp, "Deal 8 repeats as Deal 4")
        assertTrue(dawn.state.player.afterimages.isEmpty(), "an Afterimage resolves once")

        var two = start("vowknight", "london.gaslight_footpad", "london.gaslight_footpad")
            .withPresent("neutral.lingering_strike").withEnergy(NEUTRAL to 2)
        two = two.copy(enemies = two.enemies.mapIndexed { i, e -> if (i == 0) e.copy(hp = 5) else e })
        two = two.ok(PlayCard(two.player.present[0].uid, target = two.enemies[0].uid)).state
        assertTrue(!two.enemies[0].alive)
        assertEquals(footpad - 4, two.ok(EndTurn).state.enemies[1].hp, "the repeat finds the other Footpad")
    }

    @Test
    fun `Penitent's Lash hits harder after a round you lost HP`() {
        val s = start("vowknight", "saltwaste.sandglass_golem").withPresent("order.penitents_lash").withEnergy(FAITH to 1)
        assertEquals(GOLEM - 4, s.ok(PlayCard(s.player.present[0].uid)).state.enemies[0].hp)
        val hurt = s.copy(player = s.player.copy(hpLostLastEnemyPhase = 6))
        assertEquals(GOLEM - 7, hurt.ok(PlayCard(hurt.player.present[0].uid)).state.enemies[0].hp)
    }

    @Test
    fun `the planner plays the expansion decks legally`() {
        val encounters = listOf("lone_golem", "fleet_street", "fraying_cloister", "inquisitor", "the_rent")
        for (op in content.operatives.keys) for (enc in encounters) {
            val setup = CombatSetup(op, content.deck(op, "expansion"), content.encounter(enc).enemies, seed = 21)
            val fight = Runner.fight(engine, setup, PlannerBot(3))
            assertTrue(fight.phase.over, "$op vs $enc didn't end")
            assertEquals(0, fight.stats.botErrors, "the planner chose an illegal action, $op vs $enc")
        }
    }

    @Test
    fun `halving rounds down and drops what reaches zero`() {
        assertEquals(Effect.Damage(Amount(4)), Effect.Damage(Amount(9)).halved())
        assertEquals(Effect.Block(Amount(1)), Effect.Block(Amount(3)).halved())
        assertNull(Effect.Draw(1).halved())
        assertNull(Effect.Delay(1).halved(), "only damage, Block, Plate, Might, energy, draw and statuses repeat")
    }
}
