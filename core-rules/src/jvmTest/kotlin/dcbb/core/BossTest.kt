package dcbb.core

import dcbb.core.engine.CombatSetup
import dcbb.core.engine.EndTurn
import dcbb.core.engine.Engine
import dcbb.core.engine.GameEvent
import dcbb.core.engine.PlayCard
import dcbb.core.engine.UseSignature
import dcbb.core.model.AiView
import dcbb.core.model.CardType
import dcbb.core.model.CombatMods
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.EnergyColor
import dcbb.core.model.StatusType
import dcbb.core.state.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The boss machinery (phases, riders, summons, heals, the Chorus) and the rule hooks Imprints and boss Artifacts use. */
class BossTest {

    private fun startWith(mods: CombatMods, op: String = "vowknight", vararg enemies: String = arrayOf("saltwaste.sandglass_golem")) =
        engine.start(CombatSetup(op, content.deck(op, "starter"), enemies.toList(), 1, mods = mods)).state

    private fun view(hp: Int, max: Int, attacks: Int = 0) = AiView(1, emptyList(), 0, 0, hp = hp, maxHp = max, attacksLastTurn = attacks)

    @Test
    fun `a phased boss changes at its threshold, announces it once, and never goes back`() {
        val ai = content.enemy("order.aurelian").ai
        val (first, s1) = ai.next("main", emptyMap(), view(160, 160))
        assertTrue(!first.label.startsWith("The Last Hour"))
        val (opening, s2) = ai.next("main", s1, view(70, 160))
        assertTrue(opening.label.startsWith("The Last Hour"), opening.label)
        assertTrue(opening.actions.any { it is EnemyAction.Summon })
        var st = s2
        repeat(4) {
            val (spec, next) = ai.next("main", st, view(if (it < 2) 70 else 150, 160))
            assertTrue(!spec.label.startsWith("The Last Hour") && spec.fixed, "phase 2: every intent is Fixed, even after a heal")
            st = next
        }
    }

    @Test
    fun `a rider joins the main intent when you played enough Attacks`() {
        val ai = content.enemy("order.aurelian").ai
        val calm = ai.next("main", emptyMap(), view(160, 160, attacks = 2)).first
        val flurry = ai.next("main", emptyMap(), view(160, 160, attacks = 3)).first
        assertTrue("Litany" !in calm.label)
        assertTrue("Litany" in flurry.label && flurry.actions.any { it is EnemyAction.Buff && it.status == StatusType.MIGHT })
    }

    @Test
    fun `the Cavaliere's heart picks its main intent, beside a Fixed Joust`() {
        for (id in listOf("milan.automa_hollow", "milan.automa_meridian", "milan.automa_order", "milan.automa_errata")) {
            val s = start("vowknight", id)
            val joust = s.intentOf(id, "joust")
            assertTrue(joust.fixed && joust.countdown == 3, id)
        }
        assertTrue(start("vowknight", "milan.automa_errata").intentOf("milan.automa_errata").forked)
        assertTrue(content.enemy("milan.automa_meridian").ai.predictive)
    }

    @Test
    fun `summons join the fight, up to five enemies`() {
        // The Thousandfold opens by summoning a Double.
        val s = start("vowknight", "errata.thousandfold").ok(EndTurn).state
        assertEquals(1, s.enemies.count { it.defId == "errata.double" && it.alive })
        val crowded = start("vowknight", "errata.thousandfold", "errata.double", "errata.double", "errata.double", "errata.double").ok(EndTurn).state
        assertEquals(Engine.MAX_ENEMIES, crowded.livingEnemies.size, "no sixth enemy")
    }

    @Test
    fun `the Chorus grows with every Double still standing`() {
        val alone = start("vowknight", "errata.thousandfold")
        val crowd = start("vowknight", "errata.thousandfold", "errata.double", "errata.double")
        val a = engine.intentDamage(alone, alone.intentOf("errata.thousandfold", "sig"))
        val b = engine.intentDamage(crowd, crowd.intentOf("errata.thousandfold", "sig"))
        assertEquals(a * 3, b)
    }

    @Test
    fun `MERIDIAN Recomputes once, healing`() {
        val ai = content.enemy("conv.meridian_ascendant").ai
        val (_, s1) = ai.next("main", emptyMap(), view(70, 150))
        val (recompute, s2) = ai.next("main", s1, view(30, 150))
        assertTrue(recompute.actions.any { it is EnemyAction.Heal }, recompute.label)
        val (again, _) = ai.next("main", s2, view(20, 150))
        assertTrue(again.actions.none { it is EnemyAction.Heal }, "only once")
    }

    @Test
    fun `extra income and Borrow limits come from the run`() {
        val sealed = startWith(CombatMods(extraIncome = mapOf(EnergyColor.FAITH to 1), borrowLimit = 0))
        assertEquals(4, sealed.player.energyTotal, "2 Faith + 1 Neutral + 1 from the Seal")
        assertEquals(0, engine.borrowLimit(sealed))
        assertEquals(3, engine.borrowLimit(startWith(CombatMods(borrowLimit = 3))))
        assertEquals(0, engine.borrowLimit(startWith(CombatMods(borrowLimit = 3) + CombatMods(borrowLimit = 0))), "the lowest wins")
    }

    @Test
    fun `draw, Foresee and Signature bonuses`() {
        assertEquals(Engine.DRAW_PER_TURN + 1, startWith(CombatMods(drawBonus = 1)).player.present.size)
        val oracle = startWith(CombatMods(signatureBonus = 1, foreseeBonus = 1), op = "oracle")
        val forecast = oracle.ok(UseSignature()).state
        assertEquals(Engine.FORECAST + 2, forecast.pending!!.uids.size, "Precomputed and the Shard both add one")
        val strike = startWith(CombatMods(signatureBonus = 2)).ok(UseSignature()).state
        assertEquals(GOLEM - Engine.STRIKE_DAMAGE - 2, strike.enemies.single().hp)
    }

    @Test
    fun `an Other Hand for anyone, and a bigger one for the Splinter`() {
        assertEquals(0, engine.otherHandSize(start("vowknight", "saltwaste.sandglass_golem")))
        val ribbon = startWith(CombatMods(otherHandBonus = 1))
        assertEquals(2, engine.otherHandSize(ribbon))
        assertEquals(2, ribbon.player.otherHand.size)
        assertEquals(4, engine.otherHandSize(startWith(CombatMods(otherHandBonus = 1), op = "splinter")))
    }

    @Test
    fun `Unstable Equilibrium moves the Unravel to 12`() {
        for ((mods, unravels) in listOf(CombatMods(unravelAt = 12) to false, CombatMods.NONE to true)) {
            val s = startWith(mods, op = "splinter")
            // A Paradox Engine adds 2 at the next Dawn: 9 becomes 11.
            val hot = s.copy(player = s.player.copy(paradox = 9)).withConstants("errata.paradox_engine")
            val out = hot.ok(EndTurn)
            assertEquals(unravels, out.events.any { it is GameEvent.Unravel }, "unravelAt ${mods.unravelAt}")
            if (!unravels) assertEquals(11, out.state.player.paradox)
        }
    }

    @Test
    fun `Bayesian Prior draws once a turn for a Known card`() {
        val s = startWith(CombatMods(knownDraw = 1), op = "oracle").withFuture("conv.pulse", "conv.pulse")
        val known = s.copy(player = s.player.copy(present = s.player.present.map { it.copy(known = true) }))
            .withEnergy(EnergyColor.COMPUTE to 3)
        val first = known.player.present.first { content.card(it.defId).type == CardType.ATTACK || content.card(it.defId).type == CardType.SKILL }
        val after = known.ok(PlayCard(first.uid, target = known.enemies.first().uid)).state.settle()
        assertEquals(known.player.present.size, after.player.present.size, "played one, drew one")
        val second = after.player.present.first { it.known }
        val again = after.ok(PlayCard(second.uid, target = after.enemies.first().uid)).state.settle()
        assertEquals(after.player.present.size - 1, again.player.present.size, "only the first each turn")
    }

    @Test
    fun `Keeper of Vows spares the first Penance`() {
        val s = startWith(CombatMods(firstPenanceFree = true)).withConstants("order.vow_of_the_sword")
            .withPresent("order.shield_of_the_line", "order.shield_of_the_line").withEnergy(EnergyColor.FAITH to 3)
        val a = s.ok(PlayCard(s.player.present[0].uid)).state
        val b = a.ok(PlayCard(a.player.present[0].uid)).state
        assertEquals(s.player.hp, b.player.hp, "Vow of the Sword broke without Penance")
        assertTrue(b.player.penanceSpared)
    }

    @Test
    fun `start effects can give Block`() {
        val s = startWith(CombatMods(startEffects = listOf(Effect.Block(dcbb.core.model.Amount(6)))))
        assertEquals(6, s.player.block)
        assertTrue(s.track.any { it.kind == TrackKind.INTENT })
    }
}
