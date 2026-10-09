package dcbb.core

import dcbb.core.engine.EndTurn
import dcbb.core.engine.GameEvent
import dcbb.core.engine.PayStyle
import dcbb.core.engine.PlayCard
import dcbb.core.engine.UseSignature
import dcbb.core.model.EnergyColor.FAITH
import dcbb.core.model.EnergyColor.NEUTRAL
import dcbb.core.model.StatusType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnergyAndDamageTest {

    @Test
    fun `income is two of your color and one Neutral`() {
        val s = start("vowknight", "london.gaslight_footpad")
        assertEquals(mapOf(FAITH to 2, NEUTRAL to 1), s.player.energy)
        assertEquals(5, s.player.present.size)
    }

    @Test
    fun `paying a card fully in faction color makes it Attuned`() {
        val s = start("vowknight", "saltwaste.sandglass_golem").withPresent("order.line_strike").withEnergy(FAITH to 1, NEUTRAL to 1)
        val card = s.present("order.line_strike").uid

        val attuned = s.ok(PlayCard(card)).state
        assertEquals(GOLEM - 7, attuned.enemies[0].hp)
        assertEquals(mapOf(NEUTRAL to 1), attuned.player.energy)

        val plain = s.ok(PlayCard(card, pay = PayStyle.NEUTRAL_FIRST)).state
        assertEquals(GOLEM - 5, plain.enemies[0].hp)
        assertEquals(mapOf(FAITH to 1), plain.player.energy)
    }

    @Test
    fun `colored pips can't be paid with Neutral`() {
        val s = start("vowknight", "saltwaste.sandglass_golem").withPresent("order.remembered_blow").withEnergy(NEUTRAL to 3)
        val error = s.rejects(PlayCard(s.present("order.remembered_blow").uid))
        assertTrue("Not enough energy" in error, error)
    }

    @Test
    fun `Borrow takes up to 2 from next turn's income, plus 1 interest`() {
        var s = start("vowknight", "saltwaste.sandglass_golem")
            .withPresent("order.line_strike", "order.line_strike", "order.line_strike").withEnergy()
        val (a, b, c) = s.player.present.map { it.uid }
        assertTrue("Borrow" in s.rejects(PlayCard(a)))

        val first = s.ok(PlayCard(a, allowBorrow = true))
        assertEquals(1, first.events.filterIsInstance<dcbb.core.engine.GameEvent.Borrowed>().single().interest)
        s = first.state
        assertEquals(mapOf(FAITH to 2), s.player.debt, "Borrow 1, owe 2")
        s = s.ok(PlayCard(b, allowBorrow = true)).state
        assertEquals(mapOf(FAITH to 3), s.player.debt, "interest is charged once per turn")
        assertEquals(GOLEM - 14, s.enemies[0].hp, "borrowed Faith still counts as Attuned")
        s.rejects(PlayCard(c, allowBorrow = true))

        s = s.ok(EndTurn).state
        assertTrue(s.player.energy.isEmpty(), "Debt 3 eats the whole income of 3")
        assertTrue(s.player.debt.isEmpty())
    }

    @Test
    fun `unspent energy carries over up to 6, Neutral evaporating first`() {
        val s = start("vowknight", "saltwaste.sandglass_golem").withEnergy(FAITH to 5, NEUTRAL to 3)
        val out = s.ok(EndTurn)
        assertTrue(out.events.any { it.text.contains("evaporates") })
        assertEquals(mapOf(FAITH to 7, NEUTRAL to 2), out.state.player.energy)
    }

    @Test
    fun `Glitch makes the next cards cost 1 more`() {
        var s = start("vowknight", "saltwaste.sandglass_golem")
            .withPresent("order.line_strike", "order.line_strike").withEnergy(FAITH to 3)
        s = s.copy(player = s.player.copy(statuses = mapOf(StatusType.GLITCH to 1)))
        assertEquals(2, engine.effectiveCost(s, s.player.present[0]).total)
        s = s.ok(PlayCard(s.player.present[0].uid)).state
        assertEquals(1, s.player.energyTotal)
        assertEquals(0, s.player.status(StatusType.GLITCH))
        assertEquals(1, engine.effectiveCost(s, s.player.present[0]).total)
    }

    @Test
    fun `enemy damage applies Might, Pressure, Weak and Exposed in order`() {
        var s = start("oracle", "london.rookery_brawler")
        val item = s.intentOf("london.rookery_brawler").copy(pressure = 1)
        s = s.copy(track = listOf(item))
        assertEquals(12, engine.intentDamage(s, item), "9 x 1.25 = 11.25, rounded up")

        val brawler = s.enemies[0]
        s = s.copy(enemies = listOf(brawler.copy(statuses = mapOf(StatusType.WEAK to 1))))
        s = s.copy(player = s.player.copy(statuses = mapOf(StatusType.EXPOSED to 1)))
        assertEquals(13, engine.intentDamage(s, item), "12 x 0.75 x 1.5 = 13.5, rounded down")

        s = s.copy(enemies = listOf(brawler.copy(statuses = mapOf(StatusType.MIGHT to 2))), player = s.player.copy(statuses = emptyMap()))
        assertEquals(14, engine.intentDamage(s, item), "(9 + 2) x 1.25 = 13.75, rounded up")
    }

    @Test
    fun `your attacks use Weak and Exposed too`() {
        val s = start("vowknight", "saltwaste.sandglass_golem").withPresent("order.line_strike").withEnergy(FAITH to 1)
        val golem = s.enemies[0]
        val exposed = s.copy(enemies = listOf(golem.copy(statuses = mapOf(StatusType.EXPOSED to 1))))
        assertEquals(GOLEM - 10, exposed.ok(PlayCard(s.player.present[0].uid)).state.enemies[0].hp, "7 x 1.5 = 10.5")
        val weak = s.copy(player = s.player.copy(statuses = mapOf(StatusType.WEAK to 1)))
        assertEquals(GOLEM - 5, weak.ok(PlayCard(s.player.present[0].uid)).state.enemies[0].hp, "7 x 0.75 = 5.25")
    }

    @Test
    fun `Block absorbs first and unblocked hits strip a Plate`() {
        var s = start("vowknight", "london.gaslight_footpad")
        s = s.copy(player = s.player.copy(statuses = mapOf(StatusType.PLATE to 2)))
        val out = s.ok(EndTurn)
        val hit = out.events.filterIsInstance<GameEvent.DamageToPlayer>().single()
        assertEquals(7, hit.amount, "the Footpad's Attack 9 against 2 Block from Plate")
        assertEquals(2, hit.blocked)
        assertEquals(1, out.state.player.status(StatusType.PLATE))
        assertEquals(75 - 7, out.state.player.hp)
    }

    @Test
    fun `Burn ticks at the end of its owner's turn`() {
        var s = start("vowknight", "saltwaste.sandglass_golem")
        s = s.copy(player = s.player.copy(statuses = mapOf(StatusType.BURN to 3)))
        val out = s.ok(EndTurn)
        assertEquals(3, out.events.filterIsInstance<GameEvent.HpLost>().single { it.reason == "Burn" }.amount)
        assertEquals(2, out.state.player.status(StatusType.BURN))
    }

    @Test
    fun `Strike deals 4 and auto-targets a lone enemy`() {
        val s = start("vowknight", "saltwaste.sandglass_golem")
        val out = s.ok(UseSignature())
        assertEquals(GOLEM - 4, out.state.enemies[0].hp)
        s.ok(UseSignature()).state.rejects(UseSignature())
    }
}
