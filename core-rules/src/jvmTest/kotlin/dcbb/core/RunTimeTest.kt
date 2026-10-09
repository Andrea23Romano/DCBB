package dcbb.core

import dcbb.core.bot.PlannerBot
import dcbb.core.bot.RunBot
import dcbb.core.bot.RunRunner
import dcbb.core.content.FirstHour
import dcbb.core.engine.Engine
import dcbb.core.model.Faction
import dcbb.core.model.StatusType
import dcbb.core.run.EchoSpec
import dcbb.core.run.Echoes
import dcbb.core.run.Moment
import dcbb.core.run.MomentType
import dcbb.core.run.Returning
import dcbb.core.run.RunAction
import dcbb.core.run.RunEngine
import dcbb.core.run.RunReplay
import dcbb.core.run.RunState
import dcbb.core.run.Screen
import dcbb.core.run.ShopKind
import dcbb.core.state.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Time travel on the map: Returning Moments, Anchors, Rewind and the Echo of You (docs/05). */
class RunTimeTest {
    private val re = RunEngine(FirstHour.run)
    private val era = FirstHour.run.eras.first()

    private fun startRun(op: String = "vowknight", seed: Long = 7) = re.start(op, seed).state

    private fun RunState.ok(a: RunAction): RunState {
        val out = re.apply(this, a)
        assertNull(out.error, "Run engine rejected $a on ${screen::class.simpleName}: ${out.error}")
        return out.state
    }

    private fun RunState.visit(m: Moment) = re.visit(this, m).state

    private fun moment(type: MomentType, ref: String?, returning: Returning? = null, faction: Faction? = null, echo: EchoSpec? = null) =
        Moment(9_000 + type.ordinal, type, "test", ref, faction, 2, returning = returning, ambush = returning == Returning.AMBUSH, echo = echo)

    /** Ends the current fight as a win or a loss, keeping the combat's HP. */
    private fun end(s: RunState, phase: Phase): RunState {
        val f = s.screen as Screen.Fight
        return s.copy(screen = f.copy(combat = f.combat.copy(phase = phase))).ok(RunAction.Done)
    }

    @Test
    fun `the Moments you don't choose wait in the Branch Pool, except rest and treasure`() {
        for (seed in 1L..20L) {
            val s = startRun(seed = seed)
            val w = s.screen as Screen.Weft
            val after = s.ok(RunAction.Choose(0))
            val expected = w.options.drop(1).filter { it.type in setOf(MomentType.COMBAT, MomentType.ELITE, MomentType.EVENT, MomentType.ANTIQUARIAN, MomentType.SHRINE) }
            assertEquals(expected.map { it.id }, after.branchPool.map { it.id }, "seed $seed")
        }
    }

    @Test
    fun `at most one Moment returns per step, changed, in place of a dealt one`() {
        var returned = 0
        for (seed in 1L..20L) {
            val base = startRun(seed = seed)
            val pool = base.plan.drop(3).flatten().filter { it.type == MomentType.COMBAT }.take(8)
            val r = end(base.visit(moment(MomentType.COMBAT, "lone_footpad")), Phase.WON).copy(branchPool = pool)
            val next = r.ok(RunAction.Skip)
            val dealt = (next.screen as Screen.Weft).options
            val back = dealt.filter { it.returning != null }
            assertTrue(back.size <= 1, "seed $seed: ${back.size} returned")
            assertEquals(3, dealt.size)
            if (back.isNotEmpty()) {
                returned++
                assertEquals(Returning.AMBUSH, back.single().returning)
                assertTrue(back.single().ambush)
                assertEquals(pool.size - 1, next.branchPool.size)
                assertTrue(dealt.filter { it.returning == null }.none { it.pinned && it.id !in base.plan[1].map { m -> m.id } })
            }
        }
        assertTrue(returned in 10..20, "with 8 Moments waiting, most steps deal a return ($returned of 20)")
    }

    @Test
    fun `an Ambush strikes first and pays 50 percent more Hours`() {
        val s = startRun().visit(moment(MomentType.COMBAT, "lone_footpad", Returning.AMBUSH))
        val f = s.screen as Screen.Fight
        assertEquals(150, f.hoursPct)
        assertTrue(f.combat.player.hp < s.hp, "the Footpad hit before your first turn")
        val r = end(s, Phase.WON)
        assertTrue(r.hours - s.hours in 22..37, "15-25 Hours, +50%")
    }

    @Test
    fun `an Empowered Elite has more HP and Might, and offers two Artifacts to choose from`() {
        val s = startRun().visit(moment(MomentType.ELITE, "the_rent", Returning.EMPOWERED))
        val rent = (s.screen as Screen.Fight).combat.enemies.first { it.defId == "tear.the_rent" }
        assertEquals((content.enemy("tear.the_rent").maxHp * 125 + 50) / 100, rent.maxHp)
        assertEquals(1, rent.status(StatusType.MIGHT))
        val r = end(s, Phase.WON)
        val reward = r.screen as Screen.Reward
        assertEquals(2, reward.artifacts.size)
        assertNotNull(re.apply(r, RunAction.Choose(0)).error, "choose an Artifact first")
        val took = r.ok(RunAction.TakeArtifact(1))
        assertEquals(listOf(reward.artifacts[1]), took.artifacts)
        assertTrue(took.ok(RunAction.Choose(0)).screen is Screen.Weft)
    }

    @Test
    fun `an event returns as its Consequence, a shop Looted, a Shrine Desecrated`() {
        val cell = startRun().visit(moment(MomentType.EVENT, "london.copyist_in_a_cell", Returning.CONSEQUENCE))
        assertEquals("london.copyist_in_a_cell", (cell.screen as Screen.Event).eventId)

        var empty = 0
        var half = 0
        for (seed in 1L..12L) {
            val s = startRun(seed = seed).visit(moment(MomentType.ANTIQUARIAN, null, Returning.LOOTED))
            when (val sc = s.screen) {
                is Screen.Note -> empty++
                is Screen.Shop -> {
                    half++
                    val goods = sc.stock.filter { it.kind == ShopKind.CARD || it.kind == ShopKind.ARTIFACT }
                    assertTrue(goods.size in 1..4, "half the stock: ${goods.size}")
                    val card = goods.first { it.kind == ShopKind.CARD }
                    assertEquals(RunEngine.cardPrice(content.card(card.ref!!).rarity) * 70 / 100, card.price)
                }
                else -> error("unexpected ${sc::class.simpleName}")
            }
        }
        assertTrue(empty > 0 && half > 0, "50/50: $empty empty, $half half")

        // A returning Shrine belongs to another faction than before.
        val s = startRun(seed = 3)
        val shrine = Moment(1, MomentType.SHRINE, "Order Chapel", "shrine.order_chapel", Faction.ORDER)
        val r = end(s.visit(moment(MomentType.COMBAT, "lone_footpad")), Phase.WON).copy(branchPool = listOf(shrine))
        val dealt = generateSequence(r.ok(RunAction.Skip)) { st ->
            if (st.branchPool.isEmpty() || st.step >= era.steps) null else st.copy(screen = Screen.Rest()).ok(RunAction.Done)
        }.firstNotNullOfOrNull { st -> (st.screen as? Screen.Weft)?.options?.firstOrNull { it.returning == Returning.DESECRATED } }
        assertNotNull(dealt, "the Shrine came back within the act")
        assertTrue(dealt.faction != Faction.ORDER && dealt.ref == FirstHour.run.shrines[dealt.faction])
    }

    @Test
    fun `your Anchor is set at the start of the act and moves when you Steady or accept a Shrine's offer`() {
        val s = startRun()
        val a = assertNotNull(s.anchor)
        assertEquals(1, a.step)
        assertNull(a.anchor, "a snapshot never holds an anchor")
        assertNotNull(re.apply(s, RunAction.SetAnchor).error, "only Still Points and Shrines move it")

        val steadied = s.copy(paradox = 4).visit(moment(MomentType.STILL_POINT, null)).ok(RunAction.Choose(2))
        assertEquals(2, steadied.anchor?.step)
        assertEquals(2, steadied.paradox)

        val other = if (s.operativeId == "vowknight") "shrine.errata_seam" else "shrine.order_chapel"
        val shrine = s.visit(moment(MomentType.SHRINE, other, faction = Faction.ERRATA))
        val left = shrine.ok(RunAction.Choose((shrine.screen as Screen.Event).options.indexOfFirst { it.choiceId == "leave" }))
        assertTrue(left.anchorOffer)
        val next = left.ok(RunAction.Done)
        val moved = next.ok(RunAction.SetAnchor)
        assertEquals(next.step, moved.anchor?.step)
        assertTrue(!moved.anchorOffer)
    }

    @Test
    fun `Rewind restores the run at the Anchor, costs Paradox, keeps Foresight's reveals and leaves an Echo`() {
        val s = startRun(seed = 5)
        var later = s.ok(RunAction.Foresight)
        later = end(later.ok(RunAction.Choose(0)).let { st -> if (st.screen is Screen.Fight) st else st.visit(moment(MomentType.COMBAT, "lone_footpad")) }, Phase.WON)
        later = later.ok(RunAction.Choose(0)) // take a card
        assertTrue(later.deck.size > s.deck.size)
        val back = later.ok(RunAction.Rewind)
        assertEquals(s.deck, back.deck)
        assertEquals(s.hours, back.hours)
        assertEquals(s.step, back.step)
        assertEquals(s.screen, back.screen)
        assertEquals(later.paradox + RunEngine.REWIND_PARADOX, back.paradox)
        assertEquals(later.foresight, back.foresight, "spent Foresight stays spent")
        assertTrue(back.revealed.containsAll(later.revealed), "what you saw stays seen")
        assertNull(back.anchor)
        assertEquals(1, back.stats.rewinds)
        val echo = back.plan.flatten().mapNotNull { it.echo }.single()
        assertEquals(later.deck, echo.deck)
        assertEquals(content.operative("vowknight").maxHp * 60 / 100, echo.hp)
        assertTrue(back.plan.indexOfFirst { step -> step.any { it.echo != null } } >= back.step, "the Echo waits in a later step")
        assertNotNull(re.apply(back, RunAction.Rewind).error, "the Anchor is spent")
    }

    @Test
    fun `falling with an Anchor lets you Rewind for 5 Paradox, or end the run`() {
        val s = startRun().visit(moment(MomentType.COMBAT, "fleet_street"))
        val fallen = end(s, Phase.LOST)
        assertTrue(fallen.screen is Screen.Fallen)
        assertTrue(!fallen.over)
        val back = fallen.ok(RunAction.Rewind)
        assertEquals(s.paradox + RunEngine.REWIND_DEATH_PARADOX, back.paradox)
        assertEquals(content.operative("vowknight").maxHp, back.hp)
        assertTrue(back.screen is Screen.Weft)
        val over = fallen.ok(RunAction.Done)
        assertEquals(false, (over.screen as Screen.Over).won)
        // Without an Anchor, a fall ends the run at once.
        val noAnchor = end(s.copy(anchor = null), Phase.LOST)
        assertTrue(noAnchor.screen is Screen.Over)
    }

    @Test
    fun `the Echo plays your old deck, and beating it returns Lost Weight`() {
        val deck = content.deck("vowknight", "mid")
        val spec = EchoSpec(deck, 45, 3)
        val s = startRun().visit(moment(MomentType.ELITE, Echoes.ENCOUNTER, echo = spec))
        val c = (s.screen as Screen.Fight).combat
        val echo = c.enemies.single()
        assertEquals(Engine.ECHO, echo.defId)
        assertEquals(45, echo.maxHp)
        assertEquals(deck.size / 2, echo.script!!.size, "two cards per round")
        assertTrue(c.track.single { it.enemyUid == echo.uid }.label.startsWith("Echo:"))
        val r = end(s, Phase.WON)
        val reward = r.screen as Screen.Reward
        assertTrue(reward.lostWeight)
        assertTrue(reward.cards.all { it in deck } && reward.cards.size == 3)
        assertEquals(1, reward.artifacts.size)
        val took = r.ok(RunAction.TakeArtifact(0))
        assertTrue(took.screen is Screen.Weft && took.artifacts.size == 1 && took.deck == r.deck, "one or the other")
    }

    @Test
    fun `the Echo turns cards into intents - Attacks attack, Skills guard, Constants plate`() {
        val actions = Echoes.actions(content.card("order.line_strike")) + Echoes.actions(content.card("order.shield_of_the_line")) +
            Echoes.actions(content.card("order.vow_of_the_sword"))
        assertEquals("Attack 5 + Guard 4 + Plate 2", dcbb.core.text.RulesText.intent(actions))
    }

    @Test
    fun `runs with Rewinds replay exactly from their code`() {
        var rewound = 0
        for (seed in 1L..12L) {
            val (s, actions) = RunRunner.play(re, "splinter", seed, RunBot(seed))
            if (RunAction.Rewind !in actions) continue
            rewound++
            val (replayed, rejected) = RunReplay("splinter", seed, era.id, actions).play(re)
            assertNull(rejected)
            assertEquals(s, replayed)
            assertTrue(s.stats.rewinds == 1)
        }
        assertTrue(rewound > 0, "some Splinter runs fall and Rewind")
    }

    @Test
    fun `the planner beats an Echo of a starter deck`() {
        val spec = EchoSpec(content.deck("oracle", "starter"), 39, 4)
        var s = startRun("vowknight").visit(moment(MomentType.ELITE, Echoes.ENCOUNTER, echo = spec))
        val bot = PlannerBot(3)
        while (true) {
            val c = (s.screen as Screen.Fight).combat
            if (c.phase.over) break
            s = s.ok(RunAction.Fight(bot.act(engine, c)))
        }
        assertEquals(Phase.WON, s.screen.combat.phase)
    }
}
