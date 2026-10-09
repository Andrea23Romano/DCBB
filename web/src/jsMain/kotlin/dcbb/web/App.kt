package dcbb.web

import dcbb.core.bot.PlannerBot
import dcbb.core.bot.RunBot
import dcbb.core.content.Content
import dcbb.core.content.FirstHour
import dcbb.core.content.Prototype
import dcbb.core.engine.Action
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.EndTurn
import dcbb.core.engine.Engine
import dcbb.core.engine.Outcome
import dcbb.core.engine.PayStyle
import dcbb.core.engine.PlayCard
import dcbb.core.engine.Replay
import dcbb.core.engine.ResolveForesee
import dcbb.core.engine.UseSignature
import dcbb.core.run.RunAction
import dcbb.core.run.RunEngine
import dcbb.core.run.RunOutcome
import dcbb.core.run.RunReplay
import dcbb.core.run.RunState
import dcbb.core.run.Screen
import dcbb.core.state.CombatState
import dcbb.core.state.ShiftPair
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import kotlin.random.Random

data class Setup(val op: String, val deck: String, val enc: String, val seed: Long)

/** One combat in progress, with its history for Undo and its action list for replay codes. */
class Session(val setup: Setup, start: Outcome) {
    var state: CombatState = start.state
    val log: MutableList<String> = start.events.map { it.text }.toMutableList()
    val actions = mutableListOf<Action>()
    val undo = mutableListOf<Snap>()

    class Snap(val state: CombatState, val logSize: Int, val actionCount: Int)

    fun replay() = Replay(setup.op, setup.deck, setup.enc, setup.seed, actions.toList())
}

/** The card (or Signature Action, when [uid] is null) being lined up, with the choices made so far. */
class Pick(val uid: Int?) {
    var face = 0
    var target: Int? = null
    var track: Int? = null
    var card: Int? = null
    val recall = mutableListOf<Int>()
    val shifts = mutableListOf<ShiftPair>()
    var shiftOut: Int? = null
    var replace: Int? = null
    var pay = PayStyle.AUTO
    var borrow = false

    fun action(): Action = if (uid == null) {
        UseSignature(target, shifts.firstOrNull())
    } else {
        PlayCard(uid, target, track, card, face, pay, borrow, recall.toList(), shifts.toList(), replace)
    }
}

/** The player's arrangement of a pending Foresee: first = top of the Future. */
class ForeseeDraft(val order: MutableList<Int>, val tutor: Boolean) {
    val bottom = mutableSetOf<Int>()
    var take: Int? = if (tutor) order.firstOrNull() else null

    fun action(): Action = ResolveForesee(
        top = order.filter { it !in bottom && it != take },
        bottom = order.filter { it in bottom && it != take },
        take = take,
    )
}

enum class Drawer { NONE, PAST, FUTURE, ERASED, DECK, LOG, RUN_DECK, RUN_LOG }

/**
 * A run in progress: its state, every action (for run codes and Undo), the run log, and the combat events of the
 * current fight. [bot] keeps the planner's per-fight memory between bot moves.
 */
class RunSession(val op: String, val seed: Long, val era: String, start: RunOutcome) {
    var state: RunState = start.state
    val actions = mutableListOf<RunAction>()
    val log: MutableList<String> = start.log.toMutableList()
    val events = mutableListOf<String>()
    var fightStart = 0
    val undo = mutableListOf<Snap>()
    var bot = RunBot(seed)

    class Snap(val state: RunState, val logSize: Int, val eventsSize: Int, val fightStart: Int, val actionCount: Int)

    fun fightLog(): List<String> = events.drop(fightStart)
    fun replay() = RunReplay(op, seed, era, actions.toList())
}

/** What the bot is playing on its own, one action per tick: the current fight, or the rest of the run. */
enum class Auto { NONE, FIGHT, RUN }

/**
 * The browser client: plain DOM, re-rendered from state after every input. Rules, bots and replay codes all come from
 * core-rules, compiled to JavaScript, so the page plays exactly like the simulator.
 */
class App(private val root: Element) {
    val content: Content = Prototype.content
    val engine = Engine(content)
    val runEngine = RunEngine(FirstHour.run)

    var setup: Setup = loadSetup()
    var session: Session? = null
    var run: RunSession? = null
    var auto = Auto.NONE
    var confirmRewind = false
    var pick: Pick? = null
    var foresee: ForeseeDraft? = null
    var notice: String? = null
    var hint: Action? = null
    var drawer = Drawer.NONE
    var showResult = true
    var copyText: String? = null
    var busy = false

    private val views = Views(this)
    val runViews = RunViews(this, views)

    /** The fight on screen: the run's current fight, or the lab's. */
    val fightState: CombatState?
        get() {
            val r = run
            return if (r != null) (r.state.screen as? Screen.Fight)?.combat else session?.state
        }

    fun mount() {
        root.addEventListener("click", { e -> onClick(e) })
        root.addEventListener("change", { e -> onChange(e) })
        render()
    }

    fun render() {
        val r = run
        root.innerHTML = when {
            r != null && r.state.screen is Screen.Fight -> views.combat()
            r != null -> runViews.screen(r)
            session != null -> views.combat()
            else -> views.setup()
        }
        if (copyText != null) (root.querySelector("#copy-text") as? HTMLTextAreaElement)?.select()
    }

    // ---- boot and hot reload --------------------------------------------------------------------------------

    /** Restores a run or a fight from its code (hot reload or a pasted code), or opens the setup screen. */
    fun boot(code: String?) {
        if (code != null) {
            try {
                restoreCode(code)
            } catch (e: Throwable) {
                notice = "Couldn't restore that: ${e.message}"
            }
        }
        mount()
    }

    fun snapshot(): String? = run?.replay()?.encode() ?: session?.replay()?.encode()

    private fun restoreCode(code: String) {
        if (code.trim().startsWith(RunReplay.VERSION)) restoreRun(RunReplay.parse(code)) else restore(Replay.parse(code))
    }

    private fun restore(replay: Replay) {
        setup = Setup(replay.operative, replay.deck, replay.encounter, replay.seed)
        start()
        for (a in replay.actions) if (!act(a)) break
    }

    // ---- input ------------------------------------------------------------------------------------------------

    private fun onChange(e: Event) {
        val input = e.target as? HTMLInputElement ?: return
        if (input.id == "seed") input.value.toLongOrNull()?.let { setup = setup.copy(seed = it) }
    }

    private fun onClick(e: Event) {
        if (busy) return
        val el = (e.target as? Element)?.closest("[data-act]") ?: return
        val act = el.getAttribute("data-act") ?: return
        val id = el.getAttribute("data-id")
        val n = id?.toIntOrNull()
        when (act) {
            // Setup screen
            "op" -> if (id != null) setup = setup.copy(op = id).also { saveSetup(it) }
            "deck" -> if (id != null) setup = setup.copy(deck = id).also { saveSetup(it) }
            "enc" -> if (id != null) setup = setup.copy(enc = id).also { saveSetup(it) }
            "reroll" -> setup = setup.copy(seed = randomSeed())
            "start" -> {
                readSeed()
                start()
            }
            "paste-code" -> {
                val code = (root.querySelector("#replay-code") as? HTMLTextAreaElement)?.value?.trim().orEmpty()
                val line = code.lines().firstOrNull { it.contains(Replay.VERSION + " ") || it.contains(RunReplay.VERSION + " ") }
                    ?.substringAfter("Replay:")?.trim() ?: code
                try {
                    restoreCode(line)
                } catch (t: Throwable) {
                    notice = "That isn't a replay code: ${t.message}"
                }
            }

            // Runs
            "run-start" -> {
                readSeed()
                startRun()
            }
            "run-resume" -> savedRun()?.let { code ->
                try {
                    restoreRun(RunReplay.parse(code))
                } catch (t: Throwable) {
                    notice = "Couldn't resume the saved run: ${t.message}"
                }
            }
            "run-choose" -> if (n != null) runAct(RunAction.Choose(n))
            "run-foresight" -> runAct(RunAction.Foresight)
            "run-glimpse" -> runAct(RunAction.Glimpse)
            "run-skip" -> runAct(RunAction.Skip)
            "run-done" -> {
                showResult = true
                runAct(RunAction.Done)
            }
            "run-bot" -> run?.let { r -> runAct(r.bot.act(runEngine, r.state)) }
            "run-artifact" -> if (n != null) runAct(RunAction.TakeArtifact(n))
            "run-anchor" -> runAct(RunAction.SetAnchor)
            "run-rewind" -> confirmRewind = true
            "run-rewind-no" -> confirmRewind = false
            "run-rewind-yes" -> {
                confirmRewind = false
                runAct(RunAction.Rewind)
            }
            "auto-fight" -> startAuto(Auto.FIGHT)
            "auto-run" -> startAuto(Auto.RUN)
            "auto-stop" -> auto = Auto.NONE
            "run-new" -> {
                setup = setup.copy(seed = randomSeed())
                startRun()
            }

            // Choosing what to play
            "card" -> n?.let { cardTapped(it) }
            "sig" -> {
                val p = pick
                if (p != null && p.uid == null) act(p.action()) else pick = Pick(null)
            }
            "cancel" -> pick = null
            "face" -> pick?.let { p -> if (n != null) { p.face = n; p.target = null; p.track = null; p.card = null } }
            "target", "enemy" -> if (n != null) pick?.target = n
            "track" -> if (n != null) pick?.track = n
            "card-target" -> if (n != null) pick?.card = n
            "recall" -> pick?.let { p -> if (n != null) { if (n in p.recall) p.recall.remove(n) else p.recall += n } }
            "shift-out" -> pick?.shiftOut = n
            "shift-in" -> pick?.let { p ->
                val out = p.shiftOut
                if (out != null && n != null) {
                    p.shifts += ShiftPair(out, n)
                    p.shiftOut = null
                }
            }
            "shift-top" -> if (n != null) pick?.shifts?.add(ShiftPair(n, null))
            "shift-clear" -> pick?.let { it.shifts.clear(); it.shiftOut = null }
            "replace" -> pick?.replace = n
            "pay" -> pick?.pay = if (id == "N") PayStyle.NEUTRAL_FIRST else PayStyle.AUTO
            "borrow" -> pick?.let { it.borrow = !it.borrow }
            "play" -> pick?.let { act(it.action()) }
            "end" -> act(EndTurn)

            // Foresee
            "fs-up", "fs-down" -> foresee?.let { f ->
                val i = f.order.indexOf(n)
                val j = if (act == "fs-up") i - 1 else i + 1
                if (i >= 0 && j in f.order.indices) {
                    val t = f.order[i]
                    f.order[i] = f.order[j]
                    f.order[j] = t
                }
            }
            "fs-bottom" -> foresee?.let { f -> if (n != null) { if (n in f.bottom) f.bottom.remove(n) else f.bottom += n } }
            "fs-take" -> foresee?.let { f -> f.take = n }
            "fs-ok" -> foresee?.let { act(it.action()) }

            // Tools
            "undo" -> if (run != null) runUndo() else undo()
            "hint" -> hint()
            "do-hint" -> hint?.let { act(it) }
            "dismiss" -> {
                notice = null
                hint = null
            }
            "bot-turn" -> botTurn()
            "restart" -> start()
            "new-seed" -> {
                setup = setup.copy(seed = randomSeed())
                start()
            }
            "to-setup" -> {
                session = null
                run = null
                auto = Auto.NONE
                pick = null
                foresee = null
                notice = null
                hint = null
                drawer = Drawer.NONE
            }
            "drawer" -> {
                val d = id?.let { runCatching { Drawer.valueOf(it) }.getOrNull() } ?: Drawer.NONE
                drawer = if (drawer == d) Drawer.NONE else d
            }
            "copy" -> if (run != null) copyRun() else copyLog()
            "copy-close" -> copyText = null
            "result-close" -> showResult = false
        }
        render()
    }

    /** First tap lines a card up; tapping the lined-up card again plays it. */
    private fun cardTapped(uid: Int) {
        val p = pick
        if (p != null && p.uid == uid) act(p.action()) else pick = Pick(uid)
    }

    private fun readSeed() {
        (root.querySelector("#seed") as? HTMLInputElement)?.value?.toLongOrNull()?.let { setup = setup.copy(seed = it) }
    }

    // ---- playing ----------------------------------------------------------------------------------------------

    fun start() {
        val s = setup
        val deck = content.deck(s.op, s.deck)
        val enemies = content.encounter(s.enc).enemies
        session = Session(s, engine.start(CombatSetup(s.op, deck, enemies, s.seed)))
        pick = null
        foresee = null
        notice = null
        hint = null
        showResult = true
        drawer = Drawer.NONE
        saveSetup(s)
        syncForesee()
    }

    /** Applies [a]; on a rule error, keeps the state and shows the engine's explanation. */
    fun act(a: Action): Boolean {
        if (run != null) return runAct(RunAction.Fight(a))
        val s = session ?: return false
        val out = engine.apply(s.state, a)
        if (out.error != null) {
            notice = out.error
            return false
        }
        s.undo += Session.Snap(s.state, s.log.size, s.actions.size)
        s.state = out.state
        s.actions += a
        s.log += out.events.map { it.text }
        pick = null
        hint = null
        notice = null
        foresee = null
        syncForesee()
        return true
    }

    private fun syncForesee() {
        val pending = fightState?.pending ?: return
        if (foresee == null) foresee = ForeseeDraft(pending.uids.toMutableList(), pending.tutor)
    }

    private fun undo() {
        val s = session ?: return
        val snap = s.undo.removeLastOrNull() ?: return
        s.state = snap.state
        while (s.log.size > snap.logSize) s.log.removeAt(s.log.size - 1)
        while (s.actions.size > snap.actionCount) s.actions.removeAt(s.actions.size - 1)
        pick = null
        hint = null
        notice = null
        foresee = null
        showResult = true
        syncForesee()
    }

    private fun botSeed(): Long = run?.seed ?: session?.setup?.seed ?: 0

    private fun hint() {
        val st = fightState ?: return
        if (st.phase.over) return
        hint = PlannerBot(botSeed()).act(engine, st)
        notice = null
    }

    /** The planner bot plays the rest of this turn, one action at a time (each one can be undone). */
    private fun botTurn() {
        val bot = PlannerBot(botSeed())
        val round = fightState?.round ?: return
        var guard = 0
        while (true) {
            val st = fightState ?: break
            if (st.phase.over || st.round != round || guard++ >= 60) break
            val a = bot.act(engine, st)
            if (!act(a) || a == EndTurn) break
        }
    }

    // ---- runs -------------------------------------------------------------------------------------------------

    fun startRun() {
        val s = setup
        val era = runEngine.rc.eras.first().id
        run = RunSession(s.op, s.seed, era, runEngine.start(s.op, s.seed, era))
        session = null
        resetUi()
        saveSetup(s)
        saveRun()
    }

    private fun restoreRun(code: RunReplay) {
        setup = setup.copy(op = code.operative, seed = code.seed)
        run = RunSession(code.operative, code.seed, code.era, runEngine.start(code.operative, code.seed, code.era))
        session = null
        resetUi()
        for (a in code.actions) if (!runAct(a, save = false)) break
        saveRun()
    }

    private fun resetUi() {
        pick = null
        foresee = null
        notice = null
        hint = null
        showResult = true
        drawer = Drawer.NONE
        auto = Auto.NONE
        syncForesee()
    }

    /** Applies a run action; on an error, keeps the state and shows the engine's explanation. */
    fun runAct(a: RunAction, save: Boolean = true): Boolean {
        val r = run ?: return false
        val out = runEngine.apply(r.state, a)
        if (out.error != null) {
            notice = out.error
            return false
        }
        r.undo += RunSession.Snap(r.state, r.log.size, r.events.size, r.fightStart, r.actions.size)
        val wasFight = r.state.screen is Screen.Fight
        if (!wasFight && out.state.screen is Screen.Fight) {
            r.fightStart = r.events.size
            showResult = true
        }
        r.state = out.state
        r.actions += a
        r.log += out.log
        confirmRewind = false
        r.events += out.events.map { it.text }
        pick = null
        hint = null
        notice = null
        foresee = null
        syncForesee()
        if (save) saveRun()
        return true
    }

    private fun runUndo() {
        val r = run ?: return
        val snap = r.undo.removeLastOrNull() ?: return
        r.state = snap.state
        while (r.log.size > snap.logSize) r.log.removeAt(r.log.size - 1)
        while (r.events.size > snap.eventsSize) r.events.removeAt(r.events.size - 1)
        while (r.actions.size > snap.actionCount) r.actions.removeAt(r.actions.size - 1)
        r.fightStart = snap.fightStart
        r.bot = RunBot(r.seed)
        auto = Auto.NONE
        pick = null
        hint = null
        notice = null
        foresee = null
        showResult = true
        syncForesee()
        saveRun()
    }

    /** The bot plays on its own, one action per tick so the page stays responsive and you can watch. */
    private fun startAuto(mode: Auto) {
        if (run == null) return
        auto = mode
        window.setTimeout({ tick() }, 30)
    }

    private fun tick() {
        val r = run
        if (r == null || auto == Auto.NONE || busy) {
            auto = Auto.NONE
            render()
            return
        }
        val fight = r.state.screen as? Screen.Fight
        val done = when (auto) {
            Auto.FIGHT -> fight == null || fight.combat.phase.over
            else -> r.state.over
        }
        if (done) {
            auto = Auto.NONE
            render()
            return
        }
        if (!runAct(r.bot.act(runEngine, r.state))) auto = Auto.NONE
        render()
        if (auto != Auto.NONE) window.setTimeout({ tick() }, 30)
    }

    private fun copyRun() {
        val r = run ?: return
        val text = buildString {
            append("Anachronist run log\n")
            append("Replay: ").append(r.replay().encode()).append("\n\n")
            r.log.forEach { append(it).append('\n') }
            if (r.state.screen is Screen.Fight) {
                append("\nCurrent fight:\n")
                r.fightLog().forEach { append(it).append('\n') }
            }
        }
        writeClipboard(text, "Copied the run code and log. Paste it into our chat to report a bug: I can replay the whole run exactly.")
    }

    private fun copyLog() {
        val s = session ?: return
        val text = buildString {
            append("Anachronist combat log\n")
            append("Replay: ").append(s.replay().encode()).append("\n\n")
            s.log.forEach { append(it).append('\n') }
        }
        writeClipboard(text, "Copied the fight log. Paste it into our chat to report a bug: I can replay it exactly.")
    }

    private fun writeClipboard(text: String, done: String) {
        val clipboard = window.navigator.asDynamic().clipboard
        if (clipboard == null) {
            copyText = text
            return
        }
        try {
            clipboard.writeText(text).then(
                { _: dynamic ->
                    notice = done
                    render()
                },
                { _: dynamic ->
                    copyText = text
                    render()
                },
            )
        } catch (t: Throwable) {
            copyText = text
        }
    }

    // ---- per-viewer conveniences --------------------------------------------------------------------------------

    private fun loadSetup(): Setup {
        val default = Setup("vowknight", "starter", "fleet_street", randomSeed())
        val saved = try {
            localStorage.getItem(SETUP_KEY)
        } catch (t: Throwable) {
            null
        } ?: return default
        val parts = saved.split("|")
        if (parts.size != 3) return default
        val (op, deck, enc) = parts
        val valid = op in content.operatives && content.decks[op]?.containsKey(deck) == true &&
            content.encounters.any { it.id == enc && !it.runOnly }
        return if (valid) Setup(op, deck, enc, default.seed) else default
    }

    /** The run in progress, kept in this browser so a reload resumes it. */
    private fun saveRun() {
        val code = run?.replay()?.encode() ?: return
        try {
            localStorage.setItem(RUN_KEY, code)
        } catch (t: Throwable) {
            // Storage can be blocked; the run just won't survive a reload.
        }
    }

    fun savedRun(): String? = try {
        localStorage.getItem(RUN_KEY)?.takeIf { it.startsWith(RunReplay.VERSION) }
    } catch (t: Throwable) {
        null
    }

    private fun saveSetup(s: Setup) {
        try {
            localStorage.setItem(SETUP_KEY, "${s.op}|${s.deck}|${s.enc}")
        } catch (t: Throwable) {
            // Storage can be blocked (private windows, previews); the page works without it.
        }
    }

    companion object {
        private const val SETUP_KEY = "dcbb.setup"
        private const val RUN_KEY = "dcbb.run"
        fun randomSeed(): Long = Random.nextInt(1, 100_000).toLong()
    }
}
