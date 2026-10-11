package dcbb.core

import dcbb.core.bot.RunBot
import dcbb.core.bot.RunRunner
import dcbb.core.content.FirstHour
import dcbb.core.model.Faction
import dcbb.core.model.StatusType
import dcbb.core.run.EchoSpec
import dcbb.core.run.Moment
import dcbb.core.run.MomentType
import dcbb.core.run.Perk
import dcbb.core.run.Requirement
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

/** The mission across three acts: act transitions, XP and Imprints, Standing, era scaling and the Nexus (docs/05). */
class ActsTest {
    private val re = RunEngine(FirstHour.run)
    private val rc = FirstHour.run
    private val london = rc.eras[0]
    private val milan = rc.eras[1]
    private val saltWaste = rc.eras[2]

    private fun startRun(op: String = "vowknight", seed: Long = 7) = re.start(op, seed).state

    private fun RunState.ok(a: RunAction): RunState {
        val out = re.apply(this, a)
        assertNull(out.error, "Run engine rejected $a on ${screen::class.simpleName}: ${out.error}")
        return out.state
    }

    private fun RunState.visit(type: MomentType, ref: String?) = re.visit(this, Moment(9_999, type, "test", ref)).state

    private fun end(s: RunState, phase: Phase): RunState {
        val f = s.screen as Screen.Fight
        return s.copy(screen = f.copy(combat = f.combat.copy(phase = phase))).ok(RunAction.Done)
    }

    /** Beats the act boss and takes the first boss Artifact and the first card. */
    private fun clearAct(s: RunState): RunState {
        val era = re.era(s)
        var r = end(s.copy(step = era.steps + 1).visit(MomentType.BOSS, era.boss), Phase.WON)
        r = r.ok(RunAction.TakeArtifact(0)).ok(RunAction.Choose(0))
        while (r.screen is Screen.LevelUp) r = r.ok(RunAction.Choose(0))
        return r
    }

    @Test
    fun `the mission runs London, Milan, then the Salt Waste`() {
        assertEquals(listOf(1, 2, 3), rc.eras.map { it.act })
        assertEquals(listOf(8, 8, 7), rc.eras.map { it.steps })
        assertEquals(3..5, saltWaste.divergenceSteps)
        assertTrue(saltWaste.nexus.keys.containsAll(listOf(Faction.ORDER, Faction.CONVERGENCE, Faction.ERRATA)))
    }

    @Test
    fun `a new act heals you fully, relieves Paradox, sets a new Anchor and starts a fresh Weft`() {
        val s = startRun().copy(hp = 20, paradox = 3, branchPool = startRun().plan[2], anchor = null)
        val note = clearAct(s)
        val n = note.screen as Screen.Note
        assertEquals("Act II: Milan, 1495", n.title)
        val w = note.ok(RunAction.Done)
        assertTrue(w.screen is Screen.Weft)
        assertEquals(2, w.act)
        assertEquals(milan.id, w.eraId)
        assertEquals(1, w.step)
        assertEquals(w.maxHp, w.hp, "fully healed")
        assertEquals(2, w.paradox, "−1 Paradox at the start of the act")
        assertTrue(w.branchPool.isEmpty(), "the Branch Pool belongs to the act")
        val anchor = assertNotNull(w.anchor, "a new Anchor")
        assertEquals(2, anchor.act)
        assertEquals(1, anchor.step)
        assertEquals(listOf(1, 2), w.stats.acts.map { it.act })
        assertEquals(1, w.stats.actsCleared)
    }

    @Test
    fun `beating the Nexus champion wins the run`() {
        var s = clearAct(startRun()).ok(RunAction.Done)
        s = clearAct(s).ok(RunAction.Done)
        assertEquals(3, s.act)
        val nexus = end(s.copy(step = saltWaste.steps + 1).visit(MomentType.BOSS, "aurelian"), Phase.WON)
        val over = nexus.screen as Screen.Over
        assertTrue(over.won)
        assertNotNull(re.apply(nexus, RunAction.Done).error)
    }

    @Test
    fun `the Cavaliere's heart follows the Automaton's Heart`() {
        val s = clearAct(startRun()).ok(RunAction.Done)
        fun bossWith(flag: String?): String {
            val atBoss = s.copy(step = milan.steps, ledger = listOfNotNull(flag), screen = Screen.Rest()).ok(RunAction.Choose(2))
            return (atBoss.screen as Screen.Weft).options.single().ref!!
        }
        assertEquals("automa_hollow", bossWith(null))
        assertEquals("automa_order", bossWith("milan.heart_order"))
        assertEquals("automa_meridian", bossWith("milan.heart_meridian"))
        assertEquals("automa_errata", bossWith("milan.heart_errata"))
    }

    @Test
    fun `the Nexus champion comes from the rival faction you stand lowest with`() {
        var s = clearAct(startRun("vowknight")).ok(RunAction.Done)
        s = clearAct(s).ok(RunAction.Done)
        fun champion(standing: Map<Faction, Int>): String {
            val atBoss = s.copy(step = saltWaste.steps, standing = standing, screen = Screen.Rest()).ok(RunAction.Choose(2))
            return (atBoss.screen as Screen.Weft).options.single().ref!!
        }
        assertEquals("meridian_ascendant", champion(mapOf(Faction.ORDER to -3, Faction.CONVERGENCE to -1, Faction.ERRATA to 0)), "never your own faction")
        assertEquals("thousandfold", champion(mapOf(Faction.CONVERGENCE to 2, Faction.ERRATA to -2)))
        assertTrue(champion(emptyMap()) in setOf("meridian_ascendant", "thousandfold"), "a tie is broken by the seed")
    }

    @Test
    fun `later acts scale their fights, but not their bosses or your Echo`() {
        val s = clearAct(startRun()).ok(RunAction.Done)
        val fight = (s.visit(MomentType.COMBAT, "lone_condottiero").screen as Screen.Fight).combat
        val condottiero = fight.enemies.single()
        assertEquals((content.enemy("milan.condottiero").maxHp * 125 + 50) / 100, condottiero.maxHp)
        assertEquals(1, condottiero.status(StatusType.MIGHT))
        val boss = (s.visit(MomentType.BOSS, "automa_hollow").screen as Screen.Fight).combat.enemies.single()
        assertEquals(content.enemy("milan.automa_hollow").maxHp, boss.maxHp)
        assertEquals(0, boss.status(StatusType.MIGHT))
    }

    @Test
    fun `fights give XP, and each level brings max HP and a choice of Imprints you qualify for`() {
        val s = startRun("vowknight")
        var r = end(s.visit(MomentType.COMBAT, "lone_footpad"), Phase.WON)
        assertEquals(RunEngine.XP_COMBAT, r.xp)
        assertEquals(1, r.level)
        r = end(r.ok(RunAction.Skip).let { it.visit(MomentType.COMBAT, "lone_footpad") }, Phase.WON)
        assertEquals(2, r.level)
        assertEquals(s.maxHp + RunEngine.LEVEL_HP, r.maxHp)
        val up = r.ok(RunAction.Skip)
        val offer = up.screen as Screen.LevelUp
        assertEquals(2, offer.level)
        assertTrue(offer.options.size in 1..3)
        val own = offer.options.map { rc.imprint(it) }.filter { it.operative != null }
        assertTrue(own.all { it.operative == "vowknight" && re.meets(up, it.requires) })
        assertTrue("keeper_of_vows" !in offer.options, "the starter deck holds only one Vow")
        val chosen = up.ok(RunAction.Choose(0))
        assertEquals(listOf(offer.options[0]), chosen.imprints)
        assertTrue(chosen.screen is Screen.Weft)
        assertEquals(0, chosen.levelsPending)
    }

    @Test
    fun `Imprints change the rules - Thick Skin, Quick Study, Pathfinder and Anchorite`() {
        val s = startRun()
        val thick = s.copy(levelsPending = 1, level = 2, screen = Screen.LevelUp(2, listOf("thick_skin"))).ok(RunAction.Choose(0))
        assertEquals(s.maxHp + 8, thick.maxHp)
        val study = s.copy(imprints = listOf("quick_study"))
        val reward = end(study.visit(MomentType.COMBAT, "lone_footpad"), Phase.WON).screen as Screen.Reward
        assertEquals(4, reward.cards.size)
        assertEquals(1, re.perkCount(s.copy(imprints = listOf("pathfinder")), Perk.FORESIGHT_PER_ACT))
        // Anchorite: the first Rewind of the act keeps the Anchor; the second spends it.
        val anchored = s.copy(imprints = listOf("anchorite"))
        val once = anchored.ok(RunAction.Choose(0)).let { if (it.screen is Screen.Fight) end(it, Phase.WON).ok(RunAction.Skip) else it }
        val back = once.ok(RunAction.Rewind)
        assertNotNull(back.anchor)
        assertTrue(back.anchorKept)
        assertNull(back.ok(RunAction.Rewind).anchor)
    }

    @Test
    fun `your faction's squads hunt you once your Standing with it falls below zero`() {
        for (seed in 1L..10L) {
            val loyal = re.start("vowknight", seed).state
            assertTrue(loyal.plan.flatten().none { it.type.fight && it.faction == Faction.ORDER }, "seed $seed")
        }
        val hunted = (1L..8L).count { seed ->
            val apostate = clearAct(re.start("vowknight", seed).state.copy(standing = mapOf(Faction.ORDER to -1))).ok(RunAction.Done)
            apostate.plan.flatten().any { it.type.fight && it.faction == Faction.ORDER }
        }
        assertTrue(hunted > 0, "an apostate meets the Order's squads")
    }

    @Test
    fun `a hostile faction adds a fight to the act, a friendly one opens its altar to you`() {
        val calm = clearAct(startRun("vowknight", 3)).ok(RunAction.Done)
        val hunted = clearAct(startRun("vowknight", 3).copy(standing = mapOf(Faction.ORDER to 1, Faction.ERRATA to -2))).ok(RunAction.Done)
        val fights = { s: RunState -> s.plan.flatten().count { it.type.fight } }
        assertEquals(fights(calm) + 1, fights(hunted), "one more fight, an Errata squad's")
        assertTrue(hunted.plan.flatten().any { it.type == MomentType.COMBAT && it.faction == Faction.ERRATA })
        // Standing +2 with the Convergence: its Terminal offers the Vowknight its boons.
        val friend = startRun("vowknight").copy(standing = mapOf(Faction.ORDER to 1, Faction.CONVERGENCE to 2))
        val terminal = friend.visit(MomentType.SHRINE, "shrine.convergence_terminal").screen as Screen.Event
        assertTrue(terminal.options.any { it.choiceId == "inscribe" })
        val stranger = startRun("vowknight").visit(MomentType.SHRINE, "shrine.convergence_terminal").screen as Screen.Event
        assertTrue(stranger.options.none { it.choiceId == "inscribe" })
        assertTrue(rc.event("shrine.convergence_terminal").choices.single { it.id == "inscribe" }.requires == Requirement.OWN_FACTION)
    }

    @Test
    fun `friendly factions' cards cost less at the Antiquarian, and boss Artifacts are never for sale`() {
        val s = startRun("vowknight").copy(standing = mapOf(Faction.ORDER to 2))
        val card = re.content.authored.first { it.faction == Faction.ORDER && it.rarity == dcbb.core.model.Rarity.COMMON }
        assertEquals(RunEngine.cardPrice(card.rarity) * RunEngine.FRIEND_PRICE_PCT / 100, re.cardCost(s, card.id))
        for (seed in 1L..30L) {
            val shop = re.start("oracle", seed).state.visit(MomentType.ANTIQUARIAN, null).screen as Screen.Shop
            assertTrue(shop.stock.filter { it.kind == ShopKind.ARTIFACT }.none { rc.artifact(it.ref!!).boss })
        }
    }

    @Test
    fun `an Echo waiting at the end of an act appears in the next`() {
        val echo = EchoSpec(re.content.deck("vowknight", "mid"), 40, 8)
        val s = clearAct(startRun().copy(echoesWaiting = listOf(echo))).ok(RunAction.Done)
        assertTrue(s.echoesWaiting.isEmpty())
        assertEquals(1, s.plan.flatten().count { it.echo == echo })
    }

    @Test
    fun `whole three-act runs replay exactly from their code`() {
        for ((op, seed) in listOf("vowknight" to 2L, "splinter" to 5L, "oracle" to 9L)) {
            val (s, actions) = RunRunner.play(re, op, seed, RunBot(seed))
            assertTrue(s.over)
            val code = RunReplay(op, seed, london.id, actions).encode()
            assertTrue(code.startsWith("r2 "))
            val (replayed, rejected) = RunReplay.parse(code).play(re)
            assertNull(rejected)
            assertEquals(s, replayed)
        }
    }
}
