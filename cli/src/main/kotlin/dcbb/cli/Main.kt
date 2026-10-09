package dcbb.cli

import dcbb.core.bot.GreedyBot
import dcbb.core.budget.Budget
import dcbb.core.content.Content
import dcbb.core.content.Prototype
import dcbb.core.engine.Action
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.EndTurn
import dcbb.core.engine.Engine
import dcbb.core.engine.GameEvent
import dcbb.core.engine.PayStyle
import dcbb.core.engine.PlayCard
import dcbb.core.engine.ResolveForesee
import dcbb.core.engine.UseSignature
import dcbb.core.model.EnergyColor
import dcbb.core.model.Signature
import dcbb.core.model.StatusType
import dcbb.core.state.CardInst
import dcbb.core.state.CombatState
import dcbb.core.state.Phase
import dcbb.core.state.ShiftPair
import dcbb.core.state.TrackItem
import dcbb.core.state.TrackKind
import dcbb.core.text.RulesText

/**
 * A text client for the Phase 1 combat prototype: one fight, typed commands, full rules text.
 *
 * Usage: cli [--op vowknight|oracle|splinter] [--deck starter|mid] [--enc ENCOUNTER] [--seed N]
 */
fun main(args: Array<String>) {
    val content = Prototype.content
    val opts = args.toList().chunked(2).filter { it.size == 2 }.associate { (k, v) -> k.removePrefix("--") to v }
    val op = opts["op"] ?: choose("Operative", content.operatives.keys.sorted()) ?: return
    val deck = opts["deck"] ?: choose("Deck", content.decks.getValue(op).keys.toList()) ?: return
    val enc = opts["enc"] ?: choose("Encounter", content.encounters.filter { !it.runOnly }.map { it.id }) { id ->
        val e = content.encounter(id)
        "${e.name}${if (e.elite) " (elite)" else ""}: " + e.enemies.joinToString(", ") { content.enemy(it).name }
    } ?: return
    val seed = opts["seed"]?.toLong() ?: System.currentTimeMillis() % 100_000
    Cli(Engine(content), content).run(CombatSetup(op, content.deck(op, deck), content.encounter(enc).enemies, seed), seed)
}

private fun choose(what: String, options: List<String>, describe: (String) -> String = { it }): String? {
    println("$what:")
    options.forEachIndexed { i, o -> println("  ${i + 1}. ${describe(o)}") }
    while (true) {
        print("> ")
        val line = readlnOrNull()?.trim() ?: return null
        line.toIntOrNull()?.let { if (it in 1..options.size) return options[it - 1] }
        options.firstOrNull { it == line }?.let { return it }
        println("Type a number from 1 to ${options.size}.")
    }
}

private class Cli(val engine: Engine, val content: Content) {
    private val log = mutableListOf<String>()
    private lateinit var s: CombatState

    fun run(setup: CombatSetup, seed: Long) {
        println("Seed $seed. Type 'help' for commands.")
        val start = engine.start(setup)
        s = start.state
        show(start.events)
        while (!s.phase.over) {
            if (s.pending != null) {
                if (!foresee()) return
                continue
            }
            board()
            print("> ")
            val line = readlnOrNull()?.trim() ?: return
            if (line.isEmpty()) continue
            if (!command(line)) return
        }
        board()
        println(
            when (s.phase) {
                Phase.WON -> "Victory in ${s.round} rounds with ${s.player.hp}/${s.player.maxHp} HP."
                Phase.LOST -> "Defeat in round ${s.round}."
                else -> "The fight stalls at the round cap."
            },
        )
    }

    private fun command(line: String): Boolean {
        val words = line.split(Regex("\\s+"))
        try {
            when (words[0].lowercase()) {
                "q", "quit", "exit" -> return false
                "h", "help", "?" -> help()
                "p", "play" -> act(parsePlay(words.drop(1)))
                "sig", "s" -> act(parseSignature(words.drop(1)))
                "end", "e" -> act(EndTurn)
                "i", "inspect" -> inspect(words.getOrNull(1))
                "past" -> listZone("Past", s.player.past)
                "erased" -> listZone("Erased", s.player.erased)
                "future" -> future()
                "log" -> log.takeLast(40).forEach { println("  $it") }
                "hint" -> hint()
                else -> println("Unknown command. Type 'help'.")
            }
        } catch (e: IllegalArgumentException) {
            println("  ${e.message}")
        }
        return true
    }

    private fun act(action: Action) {
        val out = engine.apply(s, action)
        if (out.error != null) {
            println("  ✗ ${out.error}")
            return
        }
        s = out.state
        show(out.events)
    }

    private fun show(events: List<GameEvent>) {
        for (e in events) {
            log += e.text
            println("  · ${e.text}")
        }
    }

    // ---- parsing -----------------------------------------------------------------------------------------------

    private fun index(token: String, size: Int, what: String): Int {
        val n = token.toIntOrNull() ?: throw IllegalArgumentException("'$token' isn't a number")
        require(n in 1..size) { "No $what $n" }
        return n - 1
    }

    private fun parsePlay(args: List<String>): Action {
        require(args.isNotEmpty()) { "Which card? e.g. 'p 2' or 'p 2 e1'" }
        val p = s.player
        val card = p.present[index(args[0], p.present.size, "card")]
        var target: Int? = null
        var track: Int? = null
        var cardTarget: Int? = null
        var face = 0
        var pay = PayStyle.AUTO
        var borrow = false
        var recall = emptyList<Int>()
        var shifts = emptyList<ShiftPair>()
        var replace: Int? = null
        for (a in args.drop(1)) {
            when {
                a == "a" -> face = 0
                a == "b" -> face = 1
                a == "n" -> pay = PayStyle.NEUTRAL_FIRST
                a == "!" -> borrow = true
                a.startsWith("e") -> target = s.enemies[index(a.drop(1), s.enemies.size, "enemy")].uid
                a.startsWith("t") -> track = trackOrder()[index(a.drop(1), s.track.size, "Track item")].id
                a.startsWith("c") -> cardTarget = p.present[index(a.drop(1), p.present.size, "card")].uid
                a.startsWith("x") -> replace = p.constants[index(a.drop(1), p.constants.size, "Constant")].uid
                a.startsWith("r=") -> recall = a.drop(2).split(",").map { p.past[index(it, p.past.size, "Past card")].uid }
                a.startsWith("s=") -> shifts = a.drop(2).split(",").map { parseShift(it) }
                else -> throw IllegalArgumentException("Don't understand '$a'. Type 'help'.")
            }
        }
        return PlayCard(card.uid, target, track, cardTarget, face, pay, borrow, recall, shifts, replace)
    }

    private fun parseShift(spec: String): ShiftPair {
        val (a, b) = spec.split(":").let { it[0] to it.getOrElse(1) { "-" } }
        val present = s.player.present[index(a, s.player.present.size, "card")].uid
        val other = if (b == "-") null else s.player.otherHand[index(b, s.player.otherHand.size, "Other Hand card")].uid
        return ShiftPair(present, other)
    }

    private fun parseSignature(args: List<String>): Action {
        var target: Int? = null
        var pair: ShiftPair? = null
        for (a in args) {
            when {
                a.startsWith("e") -> target = s.enemies[index(a.drop(1), s.enemies.size, "enemy")].uid
                a.startsWith("s=") -> pair = parseShift(a.drop(2))
                a.contains(":") -> pair = parseShift(a)
                else -> throw IllegalArgumentException("Don't understand '$a'. Type 'help'.")
            }
        }
        return UseSignature(target, pair)
    }

    // ---- Foresee ------------------------------------------------------------------------------------------------

    private fun foresee(): Boolean {
        val pending = s.pending ?: return true
        val revealed = pending.uids.map { uid -> s.player.future.first { it.uid == uid } }
        println()
        println("FORESEE: the top ${revealed.size} of your Future (now Known)")
        revealed.forEachIndexed { i, c -> println("  ${i + 1}. ${cardLine(c)}") }
        println(
            "Order them: numbers for the top, first = top · '-N' sends N to the bottom" +
                (if (pending.tutor) " · '*N' puts N into your Present (required)" else "") + " · Enter keeps this order",
        )
        while (true) {
            print("foresee> ")
            val line = readlnOrNull()?.trim() ?: return false
            val top = mutableListOf<Int>()
            val bottom = mutableListOf<Int>()
            var take: Int? = null
            try {
                for (t in line.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                    when {
                        t.startsWith("-") -> bottom += revealed[index(t.drop(1), revealed.size, "card")].uid
                        t.startsWith("*") -> take = revealed[index(t.drop(1), revealed.size, "card")].uid
                        else -> top += revealed[index(t, revealed.size, "card")].uid
                    }
                }
            } catch (e: IllegalArgumentException) {
                println("  ${e.message}")
                continue
            }
            if (pending.tutor && take == null && line.isEmpty()) take = revealed.first().uid
            val rest = revealed.map { it.uid }.filter { it != take && it !in top && it !in bottom }
            val out = engine.apply(s, ResolveForesee(top + rest, bottom, take))
            if (out.error != null) {
                println("  ✗ ${out.error}")
                continue
            }
            s = out.state
            show(out.events)
            return true
        }
    }

    // ---- display ----------------------------------------------------------------------------------------------

    private fun trackOrder(): List<TrackItem> =
        s.track.sortedWith(compareBy({ it.countdown }, { if (it.kind == TrackKind.SCHEDULED) 0 else 1 }, { it.order }))

    private fun board() {
        val p = s.player
        val op = content.operative(p.operativeId)
        println()
        println("═══ Round ${s.round} " + "═".repeat(50))
        println("ENEMIES")
        s.enemies.forEachIndexed { i, e ->
            val name = content.enemy(e.defId).name
            if (!e.alive) {
                println("  e${i + 1}  $name — defeated")
            } else {
                val block = if (e.block > 0) "  Block ${e.block}" else ""
                println("  e${i + 1}  $name  ${e.hp}/${e.maxHp}$block${statuses(e.statuses)}")
            }
        }
        println("TRACK")
        if (s.track.isEmpty()) println("  (empty)")
        trackOrder().forEachIndexed { i, item -> println("  t${i + 1}  ${trackLine(item)}") }
        println("YOU  ${op.name}  HP ${p.hp}/${p.maxHp}  Block ${p.block}${statuses(p.statuses)}  Paradox ${p.paradox}/10")
        val energy = EnergyColor.entries.filter { p.energyOf(it) > 0 }.joinToString(" ") { it.symbol.repeat(p.energyOf(it)) }
        val debt = if (p.debtTotal > 0) "  Debt ${p.debtTotal} (repaid next Dawn)" else ""
        println("  Energy ${energy.ifEmpty { "none" }}$debt  Borrow left ${Engine.BORROW_LIMIT - p.borrowedThisTurn}")
        val constants = p.constants.joinToString(" · ") { "${content.card(it.defId).name}${if (p.constantsSilenced) " (snipped)" else ""}" }
        println("  Constants ${p.constants.size}/${Engine.CONSTANT_SLOTS}: ${constants.ifEmpty { "none" }}")
        println("  Future ${p.future.size} (${p.future.count { it.known }} Known) · Past ${p.past.size} · Erased ${p.erased.size}")
        println("PRESENT")
        if (p.present.isEmpty()) println("  (empty)")
        p.present.forEachIndexed { i, c -> println("  ${i + 1}. ${cardLine(c)}") }
        if (op.otherHandSize > 0) {
            println("OTHER HAND")
            p.otherHand.forEachIndexed { i, c -> println("  o${i + 1}. ${cardLine(c)}") }
        }
        val sig = when (op.signature) {
            Signature.STRIKE -> "Strike: deal ${Engine.STRIKE_DAMAGE + p.constants.sumOf { engine.constantSpec(it)?.strikeDamage ?: 0 }}"
            Signature.FORECAST -> "Forecast: Foresee 2"
            Signature.SHIFT -> "Shift: swap a Present card with an Other Hand card"
        }
        println("Signature ${if (p.signatureUsed) "(used)" else "ready"} · $sig")
    }

    private fun statuses(map: Map<StatusType, Int>) =
        map.entries.sortedBy { it.key.ordinal }.joinToString("") { "  ${it.key.label} ${it.value}" }

    private fun trackLine(item: TrackItem): String {
        val clock = "①②③④⑤⑥⑦⑧⑨".getOrNull(item.countdown - 1)?.toString() ?: "(${item.countdown})"
        if (item.kind == TrackKind.SCHEDULED) return "$clock You: ${item.label}"
        val who = item.enemyUid?.let { s.enemy(it) }?.let { content.enemy(it.defId).name } ?: "?"
        val lock = if (item.fixed) " 🔒Fixed" else ""
        val pressure = if (item.pressure > 0) " (Pressure ${item.pressure}: +${25 * item.pressure}%)" else ""
        val body = when {
            item.forked && item.collapsed == null -> "A: ${item.label}${dmg(item, 0)} / B: ${item.altLabel}${dmg(item, 1)}"
            item.forked -> "Observed: ${if (item.collapsed == 0) item.label else item.altLabel}${dmg(item, item.collapsed!!)}"
            else -> "${item.label}${dmg(item, 0)}"
        }
        return "$clock $who: $body$pressure$lock"
    }

    private fun dmg(item: TrackItem, outcome: Int): String {
        val d = engine.intentDamage(s, item, outcome)
        return if (d > 0) " → $d dmg" else ""
    }

    private fun cardLine(c: CardInst): String {
        val def = content.card(c.defId)
        val cost = engine.effectiveCost(s, c)
        val flags = buildList {
            if (c.known) add("Known")
            if (c.blank) add("BLANK")
            if (c.bleed) add("Bleed")
            if (c.retainThisTurn || def.retain) add("Retain")
            if (c.iterations > 0) add("played ×${c.iterations}")
        }.joinToString(", ").let { if (it.isEmpty()) "" else " [$it]" }
        val face = c.face
        val granted = c.granted
        val text = when {
            face != null -> "Misprinted: " + RulesText.face(face)
            granted != null -> "A: ${RulesText.card(def)} B (Fork): ${RulesText.face(granted)}"
            else -> RulesText.card(def)
        }
        return "${def.name.padEnd(20)} ${cost.toString().padEnd(5)} ${RulesText.typeLine(def).padEnd(22)} $text$flags"
    }

    private fun inspect(arg: String?) {
        val p = s.player
        val c = arg?.toIntOrNull()?.let { p.present.getOrNull(it - 1) } ?: run {
            println("  Usage: i N (a card in your Present)")
            return
        }
        val def = content.card(c.defId)
        val r = Budget.report(def)
        println("  ${def.name} — ${def.faction.label} ${RulesText.typeLine(def)}, ${def.rarity.name.lowercase()}, cost ${def.cost}")
        println("  ${RulesText.card(def)}")
        println("  Budget ${"%.1f".format(r.points)} / ${"%.1f".format(r.target)} (${"%.2f".format(r.ratio)}×)")
        engine.faces(c).forEachIndexed { i, f -> println("  Face ${'A' + i}: ${f.type.label}: ${RulesText.face(f)}") }
    }

    private fun listZone(name: String, cards: List<CardInst>) {
        println("  $name (${cards.size}):")
        cards.forEachIndexed { i, c -> println("    ${i + 1}. ${cardLine(c)}") }
    }

    private fun future() {
        val known = s.player.future.withIndex().filter { it.value.known }
        println("  Future: ${s.player.future.size} cards; Known positions from the top:")
        if (known.isEmpty()) println("    none")
        known.forEach { (i, c) -> println("    #${i + 1}: ${cardLine(c)}") }
    }

    private fun hint() {
        val a = GreedyBot(seed = s.round.toLong()).act(engine, s)
        val text = when (a) {
            is PlayCard -> {
                val card = s.player.present.first { it.uid == a.cardUid }
                "play ${content.card(card.defId).name}" + (a.target?.let { t -> " on ${content.enemy(s.enemy(t)!!.defId).name}" } ?: "")
            }
            is UseSignature -> "use your Signature" + (a.target?.let { t -> " on ${content.enemy(s.enemy(t)!!.defId).name}" } ?: "")
            EndTurn -> "end your turn"
            else -> a.toString()
        }
        println("  The greedy bot would $text.")
    }

    private fun help() {
        println(
            """
            |  p N [eK] [tK] [cK] [a|b] [n] [!] [r=I,J] [s=P:O] [xK]   play card N from your Present
            |       eK   target enemy K            tK  target Track item K (Delay, Unwrite, Observe)
            |       cK   target card K in Present  a/b face A or B (Fork cards)
            |       n    pay generic pips with Neutral first (default: pay Attuned when possible)
            |       !    allow Borrow (up to 2 per turn, repaid from next turn's income)
            |       r=I,J  Recall these Past cards (see 'past')    xK  replace Constant K when slots are full
            |       s=P:O  Shift Present card P with Other Hand card O ('P:-' swaps with the top of your Future)
            |  sig [eK | P:O]   Signature Action (Strike needs a target with 2+ enemies; Shift needs a pair)
            |  end              end your turn
            |  i N              inspect card N (full text, faces, budget)
            |  past | future | erased | log | hint | help | quit
            """.trimMargin(),
        )
    }
}
