package dcbb.core

import dcbb.core.engine.EndTurn
import dcbb.core.engine.GameEvent
import dcbb.core.engine.PlayCard
import dcbb.core.engine.UseSignature
import dcbb.core.model.CardType
import dcbb.core.model.EnergyColor.FLUX
import dcbb.core.model.EnergyColor.NEUTRAL
import dcbb.core.model.Faction
import dcbb.core.state.CardInst
import dcbb.core.state.ShiftPair
import dcbb.core.state.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrataTest {

    @Test
    fun `the Other Hand fills to 3 at Dawn and isn't discarded at Dusk`() {
        val s = start("splinter", "saltwaste.sandglass_golem")
        assertEquals(3, s.player.otherHand.size)
        val other = s.player.otherHand.map { it.uid }
        assertEquals(other, s.ok(EndTurn).state.player.otherHand.map { it.uid })
    }

    @Test
    fun `Shift swaps a Present card with an Other Hand card and enables Caret Strike`() {
        var s = start("splinter", "saltwaste.sandglass_golem")
            .withPresent("errata.caret_strike", "errata.double_guard")
            .withOtherHand("errata.splinter_cut")
            .withEnergy(FLUX to 1)
        val guard = s.present("errata.double_guard").uid
        val cut = s.player.otherHand[0].uid
        s = s.ok(UseSignature(shiftPair = ShiftPair(guard, cut))).state
        assertTrue(s.player.present.any { it.uid == cut })
        assertTrue(s.player.otherHand.any { it.uid == guard })
        assertTrue(s.player.shiftedThisTurn)
        val out = s.ok(PlayCard(s.present("errata.caret_strike").uid)).state
        assertEquals(GOLEM - 7, out.enemies[0].hp, "Deal 4, +3 after a Shift")
    }

    @Test
    fun `a card Shifted away acts Elsewhere as it goes`() {
        var s = start("splinter", "saltwaste.sandglass_golem")
            .withPresent("errata.double_guard", "errata.splinter_cut")
            .withOtherHand("errata.stet")
        val guard = s.present("errata.double_guard").uid
        s = s.ok(UseSignature(shiftPair = ShiftPair(guard, s.player.otherHand[0].uid))).state
        assertEquals(3, s.player.block, "Double Guard gains 3 Block as it goes")
        assertEquals(GOLEM, s.enemies[0].hp, "the card that stays doesn't act")
        assertTrue(s.player.otherHand.any { it.uid == guard })
    }

    @Test
    fun `Shift cards trigger Elsewhere too, with or without an Other Hand`() {
        // The Oracle has no Other Hand: Elsewhen Guard swaps Dele with the top card of the Future.
        var s = start("oracle", "saltwaste.sandglass_golem")
            .withPresent("errata.elsewhen_guard", "errata.dele")
            .withEnergy(FLUX to 1)
        val dele = s.present("errata.dele").uid
        s = s.ok(PlayCard(s.present("errata.elsewhen_guard").uid, shiftPairs = listOf(ShiftPair(dele, null)))).state
        assertEquals(GOLEM - 5, s.enemies[0].hp, "Dele deals 5 to the weakest enemy as it goes")
        assertEquals(4, s.player.block)
        assertEquals(dele, s.player.future.first().uid)
    }

    @Test
    fun `Blank cards don't act Elsewhere`() {
        var s = start("splinter", "saltwaste.sandglass_golem")
            .withPresent("errata.double_guard")
            .withOtherHand("errata.splinter_cut")
        s = s.copy(player = s.player.copy(present = s.player.present.map { it.copy(blank = true) }))
        val out = s.ok(UseSignature(shiftPair = ShiftPair(s.player.present[0].uid, s.player.otherHand[0].uid))).state
        assertEquals(0, out.player.block)
    }

    @Test
    fun `Fork gives a card a Misprint face for its type and cost`() {
        var s = start("splinter", "saltwaste.sandglass_golem")
            .withPresent("errata.split_the_moment", "errata.splinter_cut")
            .withEnergy(FLUX to 2)
        val cut = s.present("errata.splinter_cut").uid
        s = s.ok(PlayCard(s.present("errata.split_the_moment").uid, cardTarget = cut)).state
        val forked = s.player.present.first { it.uid == cut }
        val granted = assertNotNull(forked.granted)
        assertTrue(granted in content.misprintFaces(CardType.ATTACK, 1))
        assertEquals(2, engine.faces(forked).size)
        s.ok(PlayCard(cut, face = 1))
    }

    @Test
    fun `Fork cards let you choose a face`() {
        val s = start("splinter", "saltwaste.sandglass_golem").withPresent("errata.two_places_at_once").withEnergy(FLUX to 1)
        val card = s.player.present[0].uid
        assertEquals(GOLEM - 5, s.ok(PlayCard(card, face = 0)).state.enemies[0].hp)
        assertEquals(4, s.ok(PlayCard(card, face = 1)).state.player.block)
        s.rejects(PlayCard(card, face = 2))
    }

    @Test
    fun `The Thousand Doors forks Shifted cards and discounts the first`() {
        var s = start("splinter", "saltwaste.sandglass_golem")
            .withConstants("errata.thousand_doors")
            .withPresent("errata.double_guard")
            .withOtherHand("errata.splinter_cut")
        val cut = s.player.otherHand[0].uid
        s = s.ok(UseSignature(shiftPair = ShiftPair(s.player.present[0].uid, cut))).state
        val shifted = s.player.present.single { it.uid == cut }
        assertNotNull(shifted.granted)
        assertEquals(0, engine.effectiveCost(s, shifted).total)
    }

    @Test
    fun `Misprint cards roll a face when drawn`() {
        val s = start("splinter", "saltwaste.sandglass_golem").withFuture(*Array(6) { "errata.misprinted_edict" }).withPresent()
        val drawn = s.ok(EndTurn).state.player.present
        assertEquals(5, drawn.size)
        val faces = content.card("errata.misprinted_edict").misprintFaces!!
        assertTrue(drawn.all { it.face in faces })
    }

    @Test
    fun `reaching 10 Paradox Unravels the timeline`() {
        var s = start("splinter", "saltwaste.sandglass_golem")
            .withPresent("errata.borrowed_self", "errata.splinter_cut", "errata.double_guard")
        s = s.copy(player = s.player.copy(paradox = 9))
        val out = s.ok(PlayCard(s.present("errata.borrowed_self").uid))
        assertTrue(out.events.any { it == GameEvent.Unravel })
        assertEquals(5, out.state.player.paradox)
        assertTrue(out.state.player.present.all { it.face != null }, "every card in the Present is Misprinted")
        val looseEnd = out.state.enemyNamed("tear.loose_end")
        assertTrue(out.state.track.any { it.kind == TrackKind.INTENT && it.enemyUid == looseEnd.uid })
    }

    @Test
    fun `Bleed-through adds off-color cards that cost 1 less and Erase after play`() {
        var s = start("splinter", "saltwaste.sandglass_golem").withPresent("errata.crosstalk").withEnergy(FLUX to 1, NEUTRAL to 3)
        s = s.ok(PlayCard(s.player.present[0].uid)).state
        assertEquals(1, s.player.paradox)
        val bled = s.player.present
        assertEquals(2, bled.size)
        for (c in bled) {
            val def = content.card(c.defId)
            assertTrue(c.bleed)
            assertTrue(def.faction == Faction.ORDER || def.faction == Faction.CONVERGENCE)
            val cost = engine.effectiveCost(s, c)
            assertEquals(maxOf(0, def.cost.total - 1), cost.total)
            assertEquals(cost.total, cost.generic, "colored pips become generic")
        }
        // A bled card that isn't a Constant is Erased after play. If both bled cards are Constants, bleed a Line Strike in.
        val card = bled.firstOrNull { content.card(it.defId).type != CardType.CONSTANT }
            ?: CardInst(s.nextUid, "order.line_strike", bleed = true, costDelta = -1).also { c ->
                s = s.copy(nextUid = s.nextUid + 1, player = s.player.copy(present = s.player.present + c))
            }
        val target = if (engine.needs(engine.faces(card)[0]).enemy) s.enemies[0].uid else null
        val played = engine.apply(s, PlayCard(card.uid, target = target))
        if (played.error == null) assertTrue(played.state.settle().player.erased.any { it.uid == card.uid })
    }

    @Test
    fun `Unwrite needs Paradox and removes an intent that isn't Fixed`() {
        val s = start("splinter", "london.rookery_brawler").withPresent("errata.unwrite").withEnergy(FLUX to 1)
        assertTrue("Paradox" in s.rejects(PlayCard(s.player.present[0].uid)))
        val rich = s.copy(player = s.player.copy(paradox = 3))
        val out = rich.ok(PlayCard(rich.player.present[0].uid)).state
        assertEquals(1, out.player.paradox)
        assertTrue(out.track.none { it.kind == TrackKind.INTENT })
    }
}
