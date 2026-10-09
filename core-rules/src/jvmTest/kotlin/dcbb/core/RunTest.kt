package dcbb.core

import dcbb.core.bot.RunBot
import dcbb.core.bot.RunRunner
import dcbb.core.content.FirstHour
import dcbb.core.content.Variants
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.EndTurn
import dcbb.core.engine.PlayCard
import dcbb.core.model.CombatMods
import dcbb.core.model.Effect
import dcbb.core.model.EnemyAction
import dcbb.core.model.Faction
import dcbb.core.model.Rarity
import dcbb.core.model.StatusType
import dcbb.core.model.Tgt
import dcbb.core.run.Moment
import dcbb.core.run.MomentType
import dcbb.core.run.Purpose
import dcbb.core.run.RunAction
import dcbb.core.run.RunEngine
import dcbb.core.run.RunReplay
import dcbb.core.run.RunState
import dcbb.core.run.Screen
import dcbb.core.run.ShopItem
import dcbb.core.run.ShopKind
import dcbb.core.state.Phase
import dcbb.core.state.TrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RunTest {
    private val re = RunEngine(FirstHour.run)
    private val era = FirstHour.run.eras.first()

    private fun startRun(op: String = "vowknight", seed: Long = 7) = re.start(op, seed).state

    private fun RunState.ok(a: RunAction): RunState {
        val out = re.apply(this, a)
        assertNull(out.error, "Run engine rejected $a on ${screen::class.simpleName}")
        return out.state
    }

    private fun RunState.visit(type: MomentType, ref: String?) = re.visit(this, Moment(9_999, type, "test", ref)).state

    @Test
    fun `a run starts with the starter deck, 100 Hours, Foresight and Standing`() {
        val s = startRun("oracle")
        val op = content.operative("oracle")
        assertEquals(op.starterDeck, s.deck)
        assertEquals(op.maxHp, s.hp)
        assertEquals(100, s.hours)
        assertEquals(3, s.foresight, "Forecast operatives start with 3 Foresight")
        assertEquals(2, startRun("vowknight").foresight)
        assertEquals(0, s.paradox)
        assertEquals(1, s.standingWith(Faction.CONVERGENCE))
        assertEquals(3, (s.screen as Screen.Weft).options.size)
    }

    @Test
    fun `the Weft honors the dealing rules for every operative and many seeds`() {
        for (op in content.operatives.keys) for (seed in 1L..60L) {
            val plan = startRun(op, seed).plan
            assertEquals(era.steps, plan.size)
            assertTrue(plan.all { it.size == 3 })
            val all = plan.flatten()
            for ((type, n) in era.composition) assertEquals(n, all.count { it.type == type }, "$op/$seed: $type")
            assertTrue(plan.last().any { it.type == MomentType.STILL_POINT }, "$op/$seed: a Still Point before the boss")
            assertTrue((2..5).any { i -> plan[i].any { it.type == MomentType.ANTIQUARIAN } }, "$op/$seed: an Antiquarian in steps 3-6")
            val divergence = plan.indexOfFirst { step -> step.any { it.ref == era.divergence } }
            assertTrue(divergence in 3..6, "$op/$seed: the Divergence in steps 4-7, got ${divergence + 1}")
            assertTrue(plan.take(2).flatten().none { it.type == MomentType.ELITE }, "$op/$seed: no Elite in steps 1-2")
            assertTrue(plan.all { step -> step.map { it.type }.toSet().size > 1 }, "$op/$seed: no step deals three of a kind")
            val own = content.operative(op).faction
            for (m in all.filter { it.type.fight }) {
                assertNotEquals(own, m.faction, "$op/$seed: your own squads don't hunt you (${m.ref})")
            }
            assertTrue(plan.take(3).flatten().filter { it.type == MomentType.COMBAT }.all { it.threat == 1 }, "$op/$seed: easy fights first")
        }
    }

    @Test
    fun `the same seed and the same choices give the same run, and the run code replays it`() {
        val (a, actions) = RunRunner.play(re, "splinter", 11)
        val (b, _) = RunRunner.play(re, "splinter", 11)
        assertEquals(a, b)
        assertTrue(a.over)
        val code = RunReplay("splinter", 11, era.id, actions).encode()
        val parsed = RunReplay.parse(code)
        assertEquals(actions, parsed.actions)
        val (replayed, rejected) = parsed.play(re)
        assertNull(rejected)
        assertEquals(a, replayed)
        assertNotEquals(a.plan, RunRunner.play(re, "splinter", 12).first.plan)
    }

    @Test
    fun `bot runs end in victory or defeat for every operative`() {
        for (op in content.operatives.keys) for (seed in 1L..4L) {
            val (s, _) = RunRunner.play(re, op, seed, RunBot(seed))
            val over = s.screen as? Screen.Over ?: fail("$op/$seed did not finish")
            if (over.won) assertTrue(s.stats.fightRecords.any { it.type == MomentType.BOSS && it.won })
            assertTrue(s.stats.fights >= 2)
        }
    }

    @Test
    fun `HP, Paradox and Debt carry from one fight into the run and the next fight`() {
        var s = startRun().visit(MomentType.COMBAT, "lone_footpad")
        val fight = s.screen as Screen.Fight
        assertEquals(s.hp, fight.combat.player.hp)
        // Win quickly by letting the planner play, then check what the run keeps.
        val bot = dcbb.core.bot.PlannerBot(1)
        while (true) {
            val c = (s.screen as Screen.Fight).combat
            if (c.phase.over) break
            s = s.ok(RunAction.Fight(bot.act(engine, c)))
        }
        val end = s.screen.combat
        assertEquals(Phase.WON, end.phase)
        s = s.ok(RunAction.Done)
        assertEquals(end.player.hp, s.hp)
        assertEquals(end.player.paradox, s.paradox)
        assertEquals(end.player.debt.filterValues { it > 0 }, s.debt)
        val reward = s.screen as Screen.Reward
        assertEquals(3, reward.cards.size)
        assertEquals(3, reward.cards.toSet().size)
        assertTrue(s.hours in 115..125, "15-25 Hours for a normal fight")
        // The next fight starts from the carried HP.
        val next = s.ok(RunAction.Skip).visit(MomentType.COMBAT, "fleet_street")
        assertEquals(s.hp, (next.screen as Screen.Fight).combat.player.hp)
    }

    @Test
    fun `card rewards come from your faction, neutral and era pools, and you can pay for them`() {
        var s = startRun("vowknight")
        repeat(40) { i ->
            val r = s.visit(MomentType.COMBAT, "lone_footpad").let { won(it) }
            val reward = r.screen as Screen.Reward
            for (id in reward.cards) {
                val def = content.card(id)
                assertTrue(def.rarity in setOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE), "$id rarity")
                assertTrue(def.faction == Faction.ORDER || def.faction == Faction.NEUTRAL || id.startsWith("london."), "$id pool")
                assertTrue(re.payable(r, def), "$id payable")
            }
            s = r.ok(if (i % 2 == 0) RunAction.Choose(0) else RunAction.Skip)
        }
    }

    @Test
    fun `skipping gives 10 Hours, and Glimpse shows three more cards for 1 Paradox`() {
        val r = won(startRun().visit(MomentType.COMBAT, "lone_footpad"))
        val hours = r.hours
        assertEquals(hours + 10, r.ok(RunAction.Skip).hours)
        val g = r.ok(RunAction.Glimpse)
        assertEquals(6, (g.screen as Screen.Reward).cards.size)
        assertEquals(r.paradox + 1, g.paradox)
        assertNotNull(re.apply(g, RunAction.Glimpse).error)
        val took = g.ok(RunAction.Choose(5))
        assertEquals(r.deck.size + 1, took.deck.size)
    }

    @Test
    fun `the Antiquarian sells cards and Foresight, Erases and Inscribes for Hours`() {
        val stock = listOf(
            ShopItem(ShopKind.CARD, "order.hold_fast", 45),
            ShopItem(ShopKind.ERASE, null, 75),
            ShopItem(ShopKind.INSCRIBE, null, 60),
            ShopItem(ShopKind.FORESIGHT, null, 40),
        )
        val base = startRun().copy(hours = 300)
        var s = base.copy(screen = Screen.Shop(stock))
        s = s.ok(RunAction.Choose(0))
        assertEquals(255, s.hours)
        assertTrue("order.hold_fast" in s.deck)
        assertNotNull(re.apply(s, RunAction.Choose(0)).error, "sold out")

        val size = s.deck.size
        s = s.ok(RunAction.Choose(1)) // Erase: pick a card
        assertTrue(s.screen is Screen.PickCard)
        s = s.ok(RunAction.Choose(0))
        assertEquals(size - 1, s.deck.size)
        assertEquals(180, s.hours)
        assertEquals(1, s.erases)
        assertEquals(100, re.eraseCost(s), "Erase costs 25 more each time")

        s = s.ok(RunAction.Choose(2)) // Inscribe
        val i = s.deck.indexOf("order.vow_of_the_sword")
        s = s.ok(RunAction.Choose(i))
        assertEquals(Variants.inscribedId("order.vow_of_the_sword"), s.deck[i])
        assertEquals(120, s.hours)
        assertFalse(re.canInscribe(s.deck[i]), "a card is Inscribed once")

        s = s.ok(RunAction.Choose(3))
        assertEquals(base.foresight + 1, s.foresight)
        assertNotNull(re.apply(s.copy(hours = 0, screen = Screen.Shop(stock)), RunAction.Choose(0)).error, "needs Hours")
        assertTrue(s.ok(RunAction.Done).screen is Screen.Weft)
    }

    @Test
    fun `Still Points heal 30 percent, Inscribe, or Steady`() {
        val hurt = startRun().copy(hp = 20, paradox = 5)
        val healed = hurt.visit(MomentType.STILL_POINT, null).ok(RunAction.Choose(0))
        assertEquals(20 + 75 * 30 / 100, healed.hp)
        val steadied = hurt.visit(MomentType.STILL_POINT, null).ok(RunAction.Choose(2))
        assertEquals(3, steadied.paradox)
        var ins = hurt.visit(MomentType.STILL_POINT, null).ok(RunAction.Choose(1))
        ins = ins.ok(RunAction.Choose(0))
        assertTrue(Variants.isInscribed(ins.deck[0]))
        assertTrue(ins.screen is Screen.Weft)
        // Bell of the Still Hour: both.
        val bell = hurt.copy(artifacts = listOf("bell_of_the_still_hour")).visit(MomentType.STILL_POINT, null).ok(RunAction.Choose(0))
        assertTrue((bell.screen as Screen.Rest).healed)
        val both = bell.ok(RunAction.Choose(1)).ok(RunAction.Choose(0))
        assertTrue(Variants.isInscribed(both.deck[0]) && both.screen is Screen.Weft)
    }

    @Test
    fun `The Copyist changes Standing and gives a card you can play`() {
        val s = startRun("vowknight").visit(MomentType.EVENT, "london.the_copyist")
        val ev = s.screen as Screen.Event
        assertEquals(listOf("hide", "report", "read"), ev.options.map { it.choiceId })
        val hid = s.ok(RunAction.Choose(0))
        assertEquals(0, hid.standingWith(Faction.ORDER))
        assertEquals(1, hid.standingWith(Faction.ERRATA))
        val gained = content.card(hid.deck.last())
        assertEquals(Faction.ERRATA, gained.faction)
        assertTrue(re.payable(hid, gained), "an Errata card the Vowknight can pay for")
        assertTrue("scribe_hidden" in hid.ledger)
        val read = s.ok(RunAction.Choose(2))
        assertEquals(2, read.paradox)
        assertEquals(s.foresight + 1, read.foresight)
    }

    @Test
    fun `The Notes shows each choice's Ripple before you choose, and the Ripple changes the run`() {
        val s = startRun("vowknight", 3).visit(MomentType.EVENT, "london.the_notes")
        val ev = s.screen as Screen.Event
        assertTrue(ev.options.all { it.ripple != null })
        val burn = ev.options.first { it.choiceId == "burn" }
        val after = s.ok(RunAction.Choose(ev.options.indexOf(burn)))
        assertEquals(listOf(burn.ripple), after.ripples)
        assertTrue("london.notes_burned" in after.ledger)
        if (burn.ripple == "engine_never_built") {
            val fight = after.visit(MomentType.COMBAT, "stray_proxy").screen as Screen.Fight
            val proxy = content.enemy("conv.brass_proxy").maxHp
            assertEquals((proxy * 90 + 50) / 100, fight.combat.enemies.first().maxHp)
        }
    }

    @Test
    fun `reaching 10 Paradox outside combat Misprints two cards and sends a Tear ambush`() {
        val r = won(startRun().visit(MomentType.COMBAT, "lone_footpad")).copy(paradox = 9)
        val g = r.ok(RunAction.Glimpse)
        assertEquals(5, g.paradox)
        assertEquals(2, g.deck.count { Variants.isMisprinted(it) })
        assertTrue(g.tearAmbush)
        val next = g.ok(RunAction.Skip)
        val weft = next.screen as Screen.Weft
        assertEquals(1, weft.options.size)
        assertTrue(weft.options.single().ambush && weft.options.single().tear)
        val fight = next.ok(RunAction.Choose(0)).screen as Screen.Fight
        assertTrue(fight.combat.round >= 1)
        assertTrue(fight.combat.player.hp < next.hp || fight.combat.enemies.any { it.defId == "tear.loose_end" }, "the Tear struck first")
    }

    @Test
    fun `Frayed shuffles Tear Moments into the rest of the act`() {
        val s = startRun()
        val frayed = won(s.visit(MomentType.COMBAT, "lone_footpad")).copy(paradox = 4).ok(RunAction.Skip)
        assertTrue(frayed.tearAdded)
        assertEquals(2, frayed.plan.flatten().count { it.tear } - s.plan.flatten().count { it.tear })
    }

    @Test
    fun `the Lattice Engine installs Subroutines and its Cascade hits harder for each`() {
        var c = start("vowknight", "london.lattice_engine")
        val boss = c.enemyNamed("london.lattice_engine")
        assertTrue(c.intentOf("london.lattice_engine").label.startsWith("Install Bulwark.sub"))
        val cascade = c.intentOf("london.lattice_engine", "cascade")
        assertTrue(cascade.fixed && cascade.countdown == 3)
        c = c.ok(EndTurn).state.settle()
        assertEquals(1, c.enemy(boss.uid)!!.status(StatusType.SUBROUTINES))
        assertTrue(c.intentOf("london.lattice_engine").actions.contains(EnemyAction.Guard(4)), "Bulwark rides along")
        c = c.ok(EndTurn).state.settle().ok(EndTurn).state.settle()
        // Round 3 enemy phase: the Cascade resolved with one Subroutine (it was set before the second install).
        assertTrue(c.round >= 4)
        val subs = c.enemy(boss.uid)!!.status(StatusType.SUBROUTINES)
        assertEquals(2, subs)
        val next = c.intentOf("london.lattice_engine", "cascade")
        assertEquals(5 * subs, engine.intentDamage(c, next) - 0)
    }

    @Test
    fun `run modifiers reach the fight - Gnomon Splinter, Baghdad Cell, Fire Jar, Lovelace's Notes, Ambush`() {
        fun fight(mods: CombatMods, vararg enemies: String, op: String = "vowknight") =
            engine.start(CombatSetup(op, content.deck(op, "starter"), enemies.toList(), 5, mods = mods)).state

        val inq = fight(CombatMods(fixedDamagePct = 80), "order.inquisitor").settle()
        val purge = inq.intentOf("order.inquisitor", "purge")
        assertEquals(24, engine.intentDamage(inq, purge), "Purge 30 deals 20% less")

        val jar = fight(CombatMods(startEffects = listOf(Effect.ApplyStatus(StatusType.BURN, 3, Tgt.ALL_ENEMIES))), "london.gaslight_footpad", "london.lamplighter")
        assertTrue(jar.enemies.all { it.status(StatusType.BURN) == 3 })

        val notes = fight(CombatMods(installSubroutine = true), "london.gaslight_footpad", op = "oracle")
        assertEquals(listOf("conv.watchdog"), notes.player.constants.map { it.defId })

        val ambushed = fight(CombatMods(ambush = true), "london.gaslight_footpad")
        assertEquals(1, ambushed.round)
        assertTrue(ambushed.player.hp < content.operative("vowknight").maxHp, "the Footpad hit before your first turn")

        val cell = fight(CombatMods(reservoirCap = 8), "saltwaste.sandglass_golem")
        assertEquals(8, cell.mods.reservoirCap)
        val keep = cell.withEnergy(dcbb.core.model.EnergyColor.FAITH to 9).ok(EndTurn).state
        assertEquals(8 + 3, keep.player.energyTotal, "8 carried over, then Dawn income")
    }

    @Test
    fun `Clockwork Sparrow's first Delay adds no Pressure`() {
        val c = engine.start(
            CombatSetup("vowknight", content.deck("vowknight", "starter"), listOf("london.rookery_brawler"), 1, mods = CombatMods(freeFirstDelay = true)),
        ).state.withPresent("neutral.hold_the_hour", "neutral.hold_the_hour").withEnergy(dcbb.core.model.EnergyColor.FAITH to 3)
        val intent = c.track.first { it.kind == TrackKind.INTENT }
        val once = c.ok(PlayCard(c.player.present[0].uid, trackTarget = intent.id)).state.settle()
        assertEquals(0, once.trackItem(intent.id)!!.pressure)
        val twice = once.ok(PlayCard(once.player.present[0].uid, trackTarget = intent.id)).state.settle()
        assertEquals(1, twice.trackItem(intent.id)!!.pressure)
    }

    @Test
    fun `Foresight reveals the Moments on offer and one of the next step`() {
        val s = startRun()
        val f = s.ok(RunAction.Foresight)
        assertEquals(s.foresight - 1, f.foresight)
        val shown = (s.screen as Screen.Weft).options.map { it.id }
        assertTrue(f.revealed.containsAll(shown))
        assertEquals(1, f.revealed.count { id -> s.plan[1].any { it.id == id } })
        assertNotNull(re.apply(f.copy(foresight = 0), RunAction.Foresight).error)
    }

    @Test
    fun `beating the boss ends the act in victory, and falling ends the run`() {
        val boss = startRun().copy(step = era.steps + 1).visit(MomentType.BOSS, era.boss)
        val fight = boss.screen as Screen.Fight
        val dead = boss.copy(anchor = null, screen = fight.copy(combat = fight.combat.copy(phase = Phase.LOST))).ok(RunAction.Done)
        assertEquals(false, (dead.screen as Screen.Over).won, "with no Anchor left, a fall ends the run")
        val won = boss.copy(screen = fight.copy(combat = fight.combat.copy(phase = Phase.WON))).ok(RunAction.Done)
        assertEquals(true, (won.screen as Screen.Over).won)
        assertNotNull(re.apply(won, RunAction.Done).error)
    }

    /** Ends the current fight as a win, keeping the combat's HP. */
    private fun won(s: RunState): RunState {
        val f = s.screen as Screen.Fight
        return s.copy(screen = f.copy(combat = f.combat.copy(phase = Phase.WON))).ok(RunAction.Done)
    }

    private fun assertFalse(b: Boolean, msg: String) = assertTrue(!b, msg)

    @Suppress("unused")
    private val purposes = Purpose.entries
}
