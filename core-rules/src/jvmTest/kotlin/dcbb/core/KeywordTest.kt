package dcbb.core

import dcbb.core.engine.EndTurn
import dcbb.core.engine.GameEvent
import dcbb.core.engine.PlayCard
import dcbb.core.engine.ResolveForesee
import dcbb.core.engine.UseSignature
import dcbb.core.model.EnergyColor.COMPUTE
import dcbb.core.model.EnergyColor.FAITH
import dcbb.core.model.EnergyColor.NEUTRAL
import dcbb.core.state.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeywordTest {

    @Test
    fun `Vow and Relic buff the Strike`() {
        val s = start("vowknight", "saltwaste.sandglass_golem").withConstants("order.vow_of_the_sword", "order.gnomon_blade")
        val out = s.ok(UseSignature())
        assertEquals(GOLEM - 11, out.state.enemies[0].hp, "4 + 4 (Vow) + 3 (Gnomon Blade)")
        assertEquals(2, out.state.player.block)
    }

    @Test
    fun `breaking a Vow Erases it and costs 4 HP of Penance`() {
        var s = start("vowknight", "saltwaste.sandglass_golem")
            .withConstants("order.vow_of_the_sword")
            .withPresent("order.shield_of_the_line", "order.shield_of_the_line")
            .withEnergy(FAITH to 2)
        s = s.ok(PlayCard(s.player.present[0].uid)).state
        assertEquals(1, s.player.constants.size, "one Skill keeps the Vow")
        val out = s.ok(PlayCard(s.player.present[0].uid))
        assertTrue(out.events.any { it is GameEvent.VowBroken })
        assertEquals(75 - 4, out.state.player.hp)
        assertTrue(out.state.player.constants.isEmpty())
        assertEquals("order.vow_of_the_sword", out.state.player.erased.single().defId)
        assertEquals(GOLEM - 4, out.state.ok(UseSignature()).state.enemies[0].hp, "Strike is back to 4")
    }

    @Test
    fun `Vow of Silence breaks on Borrow and on Delay`() {
        val s = start("vowknight", "saltwaste.sandglass_golem")
            .withConstants("order.vow_of_silence")
            .withPresent("order.line_strike", "neutral.hold_the_hour")
        val borrow = s.withEnergy().ok(PlayCard(s.present("order.line_strike").uid, allowBorrow = true))
        assertTrue(borrow.events.any { it is GameEvent.VowBroken })
        val delay = s.withEnergy(NEUTRAL to 1).ok(PlayCard(s.present("neutral.hold_the_hour").uid))
        assertTrue(delay.events.any { it is GameEvent.VowBroken })
    }

    @Test
    fun `Vow of Silence pays Faith at Dawn`() {
        val s = start("vowknight", "saltwaste.sandglass_golem").withConstants("order.vow_of_silence").withEnergy()
        assertEquals(mapOf(FAITH to 3, NEUTRAL to 1), s.ok(EndTurn).state.player.energy)
    }

    @Test
    fun `replacing a Vow in a full row doesn't break it`() {
        var s = start("vowknight", "saltwaste.sandglass_golem")
            .withConstants("order.vow_of_the_sword", "order.gnomon_blade", "order.vow_of_silence")
            .withPresent("order.gnomon_blade")
            .withEnergy(FAITH to 2)
        val sword = s.player.constants.first { it.defId == "order.vow_of_the_sword" }.uid
        val out = s.ok(PlayCard(s.player.present[0].uid, replaceConstantUid = sword))
        assertTrue(out.events.none { it is GameEvent.VowBroken })
        assertEquals(3, out.state.player.constants.size)
        assertTrue(out.state.player.past.any { it.uid == sword })
        assertEquals(75, out.state.player.hp)
    }

    @Test
    fun `Remember counts the right kind of card in the Past`() {
        val base = start("vowknight", "saltwaste.sandglass_golem").withPresent("order.remembered_blow").withEnergy(FAITH to 2)
        val three = base.withPast("order.line_strike", "order.line_strike", "order.line_strike", "order.shield_of_the_line")
        assertEquals(GOLEM - 14, three.ok(PlayCard(three.player.present[0].uid)).state.enemies[0].hp)
        val two = base.withPast("order.line_strike", "order.line_strike", "order.shield_of_the_line", "order.shield_of_the_line")
        assertEquals(GOLEM - 9, two.ok(PlayCard(two.player.present[0].uid)).state.enemies[0].hp)
    }

    @Test
    fun `Litany looks at the previous card played this turn`() {
        val s = start("vowknight", "saltwaste.sandglass_golem")
            .withPresent("order.line_strike", "order.shield_of_the_line", "order.crusaders_charge")
            .withEnergy(FAITH to 3, NEUTRAL to 1)
        val strike = s.present("order.line_strike").uid
        val shield = s.present("order.shield_of_the_line").uid
        val charge = s.present("order.crusaders_charge").uid
        val afterAttack = s.ok(PlayCard(strike, pay = dcbb.core.engine.PayStyle.NEUTRAL_FIRST)).state.ok(PlayCard(charge)).state
        assertEquals(GOLEM - 5 - 14, afterAttack.enemies[0].hp)
        val afterSkill = s.ok(PlayCard(shield, pay = dcbb.core.engine.PayStyle.NEUTRAL_FIRST)).state.ok(PlayCard(charge)).state
        assertEquals(GOLEM - 10, afterSkill.enemies[0].hp)
    }

    @Test
    fun `Calculated rewards Known cards`() {
        val s = start("oracle", "saltwaste.sandglass_golem").withPresent("conv.probability_lance").withEnergy(COMPUTE to 1)
        val lance = s.player.present[0]
        assertEquals(GOLEM - 4, s.ok(PlayCard(lance.uid)).state.enemies[0].hp)
        val known = s.copy(player = s.player.copy(present = listOf(lance.copy(known = true))))
        assertEquals(GOLEM - 8, known.ok(PlayCard(lance.uid)).state.enemies[0].hp)
    }

    @Test
    fun `Iterate grows each time the card is played this combat`() {
        var s = start("oracle", "saltwaste.sandglass_golem").withPresent("conv.recursive_strike").withEnergy(NEUTRAL to 3)
        val card = s.player.present[0]
        s = s.ok(PlayCard(card.uid)).state
        assertEquals(GOLEM - 3, s.enemies[0].hp)
        val again = s.player.past.single { it.uid == card.uid }
        assertEquals(1, again.iterations)
        s = s.copy(player = s.player.copy(present = listOf(again), past = emptyList()))
        assertEquals(GOLEM - 3 - 5, s.ok(PlayCard(card.uid)).state.enemies[0].hp)
    }

    @Test
    fun `Forecast pauses for a Foresee answer and marks the cards Known`() {
        val s = start("oracle", "saltwaste.sandglass_golem")
        val top = s.player.future.take(2).map { it.uid }
        val paused = s.ok(UseSignature()).state
        assertEquals(Phase.AWAIT_FORESEE, paused.phase)
        assertEquals(top, paused.pending!!.uids)
        assertTrue(paused.player.future.take(2).all { it.known })
        assertTrue("Foresee" in paused.rejects(EndTurn))
        paused.rejects(ResolveForesee(listOf(top[0])))

        val answered = paused.ok(ResolveForesee(top = listOf(top[1]), bottom = listOf(top[0]))).state
        assertEquals(Phase.PLAYER_TURN, answered.phase)
        assertNull(answered.pending)
        assertEquals(top[1], answered.player.future.first().uid)
        assertEquals(top[0], answered.player.future.last().uid)
    }

    @Test
    fun `Optimal Path puts one Foreseen card into your Present`() {
        var s = start("oracle", "saltwaste.sandglass_golem").withPresent("conv.optimal_path").withEnergy(COMPUTE to 1)
        s = s.ok(PlayCard(s.player.present[0].uid)).state
        val pending = assertNotNull(s.pending)
        val revealed = pending.uids
        assertTrue(pending.tutor)
        s.rejects(ResolveForesee(revealed))
        val out = s.ok(ResolveForesee(revealed.drop(1), take = revealed[0])).state
        assertEquals(listOf(revealed[0]), out.player.present.map { it.uid })
    }

    @Test
    fun `Recall returns chosen cards and Retain keeps a card through Dusk`() {
        var s = start("vowknight", "saltwaste.sandglass_golem")
            .withPresent("order.recite_the_canon", "order.recite_the_canon")
            .withPast("order.line_strike", "order.line_strike", "order.shield_of_the_line", "order.remembered_blow", "order.line_strike")
            .withEnergy(FAITH to 1)
        val blow = s.player.past.first { it.defId == "order.remembered_blow" }.uid
        val strike = s.player.past.first().uid
        s = s.ok(PlayCard(s.player.present[0].uid, recallUids = listOf(blow, strike))).state
        assertTrue(s.player.present.any { it.uid == blow } && s.player.present.any { it.uid == strike }, "Remember 5: Recall 2")
        val kept = s.ok(EndTurn).state.player.present.count { it.defId == "order.recite_the_canon" }
        assertEquals(1, kept, "the unplayed Recite the Canon has Retain")
    }

    @Test
    fun `reshuffling the Past restores Blank cards`() {
        var s = start("oracle", "saltwaste.sandglass_golem")
        s = s.copy(player = s.player.copy(future = emptyList(), past = s.player.future.map { it.copy(blank = true) }))
        val out = s.ok(EndTurn)
        assertTrue(out.events.any { "restored" in it.text })
        assertTrue(out.state.player.present.none { it.blank })
    }

    @Test
    fun `Debt carried in from the last combat is repaid at the first Dawn`() {
        val setup = dcbb.core.engine.CombatSetup(
            "vowknight", content.deck("vowknight", "starter"), listOf("saltwaste.sandglass_golem"), seed = 3,
            debt = mapOf(FAITH to 2),
        )
        val s = engine.start(setup).state
        assertEquals(mapOf(NEUTRAL to 1), s.player.energy)
        assertTrue(s.player.debt.isEmpty())
    }

    @Test
    fun `reshuffling the Past clears Known`() {
        var s = start("oracle", "saltwaste.sandglass_golem")
        s = s.copy(player = s.player.copy(future = emptyList(), past = s.player.future.map { it.copy(known = true) }))
        val drawn = s.ok(EndTurn).state.player.present
        assertTrue(drawn.isNotEmpty())
        assertFalse(drawn.any { it.known })
    }

    @Test
    fun `Rollback restores the HP lost in the last enemy phase`() {
        var s = start("oracle", "london.rookery_brawler")
        s = s.ok(EndTurn).state
        assertEquals(65 - 9, s.player.hp)
        s = s.withPresent("conv.rollback").withEnergy(COMPUTE to 1)
        val out = s.ok(PlayCard(s.player.present[0].uid)).state
        assertEquals(65, out.player.hp)
        assertNotNull(out.player.erased.firstOrNull { it.defId == "conv.rollback" })
    }
}
