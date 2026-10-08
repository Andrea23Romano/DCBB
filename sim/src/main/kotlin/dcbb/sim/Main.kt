package dcbb.sim

import dcbb.core.bot.Bot
import dcbb.core.bot.FightResult
import dcbb.core.bot.GreedyBot
import dcbb.core.bot.RandomBot
import dcbb.core.bot.Runner
import dcbb.core.budget.Budget
import dcbb.core.content.Content
import dcbb.core.content.Prototype
import dcbb.core.engine.CombatSetup
import dcbb.core.engine.Engine
import dcbb.core.model.Encounter
import dcbb.core.state.Phase
import dcbb.core.text.RulesText
import java.io.File
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Batch combat simulation (docs/09 "Testing and QA"): every operative and deck against every encounter, many seeds,
 * with a heuristic bot and a random bot. Results are deterministic for a given configuration, so the report can be
 * committed and diffed when numbers change.
 *
 * Usage: sim [--seeds N] [--threads N] [--bots greedy,random] [--out FILE] [--title TEXT]
 *        sim --trace operative/deck/encounter/index [--bot greedy|random]
 */
fun main(args: Array<String>) {
    Locale.setDefault(Locale.ROOT)
    val opts = parseArgs(args)
    val seeds = opts["seeds"]?.toInt() ?: 100
    val threads = opts["threads"]?.toInt() ?: Runtime.getRuntime().availableProcessors()
    val bots = (opts["bots"] ?: "greedy,random").split(",").map { it.trim() }
    val out = opts["out"]

    val content = Prototype.content
    val engine = Engine(content)
    opts["trace"]?.let { trace(engine, content, it, opts["bot"] ?: "greedy"); return }
    val decks = listOf("starter", "mid")
    val cells = buildList {
        for (bot in bots) for (op in content.operatives.keys.sorted()) for (deck in decks) for (enc in content.encounters) {
            add(CellSpec(bot, op, deck, enc))
        }
    }

    val started = System.nanoTime()
    val pool = Executors.newFixedThreadPool(threads)
    val results = try {
        pool.invokeAll(cells.map { spec -> Callable { runCell(engine, content, spec, seeds) } }).map { it.get() }
    } finally {
        pool.shutdown()
    }
    val seconds = (System.nanoTime() - started) / 1e9

    val report = Report(content, results, seeds, bots, opts["title"] ?: "Combat simulation").render()
    if (out != null) {
        File(out).absoluteFile.parentFile.mkdirs()
        File(out).writeText(report)
        println("Wrote $out (${results.sumOf { it.fights.size }} fights in ${"%.1f".format(seconds)} s on $threads threads)")
    } else {
        println(report)
        System.err.println("${results.sumOf { it.fights.size }} fights in ${"%.1f".format(seconds)} s on $threads threads")
    }
}

/** Prints the full event log of one fight: `--trace operative/deck/encounter/index`. */
private fun trace(engine: Engine, content: Content, spec: String, botName: String) {
    val (op, deck, encId, index) = spec.split("/")
    val cell = CellSpec(botName, op, deck, content.encounter(encId))
    val seed = fightSeed(cell, index.toInt())
    val bot: Bot = if (botName == "random") RandomBot(seed) else GreedyBot(seed)
    val result = Runner.fight(engine, CombatSetup(op, content.deck(op, deck), cell.encounter.enemies, seed), bot, keepLog = true)
    result.log.forEach { println(it) }
    println("Result: ${result.phase} in ${result.rounds} rounds, HP ${result.hpStart} -> ${result.hpEnd}")
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val m = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        require(a.startsWith("--") && i + 1 < args.size) {
            "Usage: sim [--seeds N] [--threads N] [--bots greedy,random] [--out FILE] [--title TEXT]"
        }
        m[a.removePrefix("--")] = args[i + 1]
        i += 2
    }
    return m
}

data class CellSpec(val bot: String, val op: String, val deck: String, val encounter: Encounter)

class Cell(val spec: CellSpec, val fights: List<FightResult>) {
    val n get() = fights.size
    val winRate get() = fights.count { it.won }.toDouble() / n
    val draws get() = fights.count { it.phase == Phase.DRAW }
    private val wins get() = fights.filter { it.won }
    val avgRoundsWon get() = wins.map { it.rounds.toDouble() }.averageOrNaN()
    val avgHpLostPctWon get() = wins.map { 100.0 * it.hpLost / it.maxHp }.averageOrNaN()
}

private fun List<Double>.averageOrNaN() = if (isEmpty()) Double.NaN else average()

private fun fightSeed(spec: CellSpec, i: Int): Long {
    var h = 1125899906842597L
    for (ch in "${spec.op}/${spec.deck}/${spec.encounter.id}") h = 31 * h + ch.code
    return h * 1_000_003L + i
}

private fun runCell(engine: Engine, content: Content, spec: CellSpec, seeds: Int): Cell {
    val deck = content.deck(spec.op, spec.deck)
    val fights = (0 until seeds).map { i ->
        val seed = fightSeed(spec, i)
        val bot: Bot = when (spec.bot) {
            "greedy" -> GreedyBot(seed)
            "random" -> RandomBot(seed)
            else -> error("Unknown bot ${spec.bot}")
        }
        Runner.fight(engine, CombatSetup(spec.op, deck, spec.encounter.enemies, seed), bot)
    }
    return Cell(spec, fights)
}

private class Report(val content: Content, val cells: List<Cell>, val seeds: Int, val bots: List<String>, val title: String) {
    private val sb = StringBuilder()
    private fun line(s: String = "") = sb.append(s).append('\n')
    private fun pct(x: Double) = if (x.isNaN()) "–" else "${(x * 100).roundToInt()}%"
    private fun num(x: Double, digits: Int = 1) = if (x.isNaN()) "–" else "%.${digits}f".format(x)
    private val ops = content.operatives.keys.sorted()
    private val decks = listOf("starter", "mid")

    fun render(): String {
        line("# $title")
        line()
        line("Generated by `./gradlew :sim:run` (Phase 1 prototype). Every operative and deck fights every encounter")
        line("with $seeds seeds per cell. Results are deterministic for this configuration: rerun the command and diff.")
        line()
        line("- **Bots:** ${bots.joinToString(", ") { "`$it`" }}. `greedy` plays one action ahead on a determinized copy of")
        line("  the state (no peeking at hidden cards or rolls) with a hand-tuned heuristic; `random` picks uniformly among legal actions.")
        line("- **Fights:** single combats at full HP, Paradox 0, no relics or Imprints. Round cap 60 (counted as a draw).")
        line("- **Read with care:** the greedy bot is a floor for skilled play, not a ceiling. Large gaps between operatives or")
        line("  encounters are the signal; small ones are noise. See [docs/09](../docs/09-tech-architecture.md#testing-and-qa).")
        line()
        summary()
        encounters()
        mechanics()
        cardUsage()
        budget()
        flags()
        return sb.toString()
    }

    private fun cellsFor(bot: String, op: String, deck: String) =
        cells.filter { it.spec.bot == bot && it.spec.op == op && it.spec.deck == deck }

    private fun pooled(cs: List<Cell>) = cs.flatMap { it.fights }

    private fun summary() {
        line("## Summary")
        line()
        line("Win rate over all encounters, then split into normal and elite fights. HP lost is the average share of max HP")
        line("lost in won fights.")
        line()
        line("| Bot | Operative | Deck | Win | Normal | Elite | Rounds (won) | HP lost (won) | Draws |")
        line("|---|---|---|---|---|---|---|---|---|")
        for (bot in bots) for (op in ops) for (deck in decks) {
            val cs = cellsFor(bot, op, deck)
            val all = pooled(cs)
            val normal = pooled(cs.filter { !it.spec.encounter.elite })
            val elite = pooled(cs.filter { it.spec.encounter.elite })
            val won = all.filter { it.won }
            line(
                "| $bot | ${content.operative(op).name} | $deck | ${pct(rate(all))} | ${pct(rate(normal))} | ${pct(rate(elite))} | " +
                    "${num(won.map { it.rounds.toDouble() }.averageOrNaN())} | " +
                    "${pct(won.map { it.hpLost.toDouble() / it.maxHp }.averageOrNaN())} | ${all.count { it.phase == Phase.DRAW }} |",
            )
        }
        line()
    }

    private fun rate(fs: List<FightResult>) = if (fs.isEmpty()) Double.NaN else fs.count { it.won }.toDouble() / fs.size

    private fun encounters() {
        val bot = if ("greedy" in bots) "greedy" else bots.first()
        line("## Encounters (`$bot` bot)")
        line()
        line("Each cell: win rate · average share of max HP lost in won fights.")
        line()
        val header = ops.flatMap { op -> decks.map { "${content.operative(op).name} ($it)" } }
        line("| Encounter | Enemies | " + header.joinToString(" | ") + " |")
        line("|---|---|" + header.joinToString("") { "---|" })
        for (enc in content.encounters) {
            val enemies = enc.enemies.groupingBy { content.enemy(it).name }.eachCount()
                .entries.joinToString(", ") { (n, c) -> if (c > 1) "$n ×$c" else n }
            val cols = ops.flatMap { op ->
                decks.map { deck ->
                    val c = cells.first { it.spec.bot == bot && it.spec.op == op && it.spec.deck == deck && it.spec.encounter.id == enc.id }
                    "${pct(c.winRate)} · ${pct(c.avgHpLostPctWon / 100)}"
                }
            }
            line("| ${enc.name}${if (enc.elite) " (elite)" else ""} | $enemies | ${cols.joinToString(" | ")} |")
        }
        line()
    }

    private fun mechanics() {
        val bot = if ("greedy" in bots) "greedy" else bots.first()
        line("## Mechanics in use (`$bot` bot)")
        line()
        line("Per fight unless noted. *Pressure at resolution* averages over intents that resolved with Pressure.")
        line("*Debt carried out* is the Debt still owed when a won fight ends. It is repaid at the next combat's first Dawn.")
        line()
        line("| Operative | Deck | Cards / round | Intent Delays | Pressure at resolution | Borrowed | Debt carried out | Unravels | Vow breaks |")
        line("|---|---|---|---|---|---|---|---|---|")
        for (op in ops) for (deck in decks) {
            val fs = pooled(cellsFor(bot, op, deck))
            val n = fs.size.toDouble()
            val rounds = fs.sumOf { it.rounds }.toDouble()
            val pressured = fs.sumOf { it.stats.pressuredResolutions }
            line(
                "| ${content.operative(op).name} | $deck | ${num(fs.sumOf { it.stats.cardsPlayed } / rounds)} | " +
                    "${num(fs.sumOf { it.stats.intentDelays } / n, 2)} | " +
                    "${if (pressured == 0) "–" else num(fs.sumOf { it.stats.pressureSum }.toDouble() / pressured, 2)} | " +
                    "${num(fs.sumOf { it.stats.borrowed } / n, 2)} | ${num(fs.filter { it.won }.map { it.debtAtEnd.toDouble() }.averageOrNaN(), 2)} | " +
                    "${num(fs.sumOf { it.stats.unravels } / n, 2)} | ${num(fs.sumOf { it.stats.vowBreaks } / n, 2)} |",
            )
        }
        line()
    }

    private fun cardUsage() {
        val bot = if ("greedy" in bots) "greedy" else bots.first()
        line("## Card usage in the mid decks (`$bot` bot)")
        line()
        line("Plays per fight, per copy in the deck. Low numbers mean the bot rarely finds the card worth its energy.")
        line()
        line("| Operative | Card | Copies | Plays per fight per copy |")
        line("|---|---|---|---|")
        for (op in ops) {
            val fs = pooled(cellsFor(bot, op, "mid"))
            val deck = content.deck(op, "mid").groupingBy { it }.eachCount()
            for ((id, copies) in deck.entries.sortedBy { content.card(it.key).name }) {
                val plays = fs.sumOf { it.stats.cardPlays[id] ?: 0 }.toDouble() / fs.size / copies
                line("| ${content.operative(op).name} | ${content.card(id).name} | $copies | ${num(plays, 2)} |")
            }
        }
        line()
    }

    private fun budget() {
        line("## Card budgets")
        line()
        line("Points and targets from [docs/08](../docs/08-card-dsl.md#primitives-and-point-costs), computed by the engine")
        line("from the same data it plays. Authored cards must land within ±10%.")
        line()
        line("| Card | Faction | Type | Cost | Rarity | Text | Pts | Ratio |")
        line("|---|---|---|---|---|---|---|---|")
        for (def in content.cards.values.sortedWith(compareBy({ it.faction.ordinal }, { it.rarity.ordinal }, { it.name }))) {
            val r = Budget.report(def)
            line(
                "| ${def.name} | ${def.faction.label} | ${RulesText.typeLine(def)} | ${def.cost} | ${def.rarity.short} | " +
                    "${RulesText.card(def).replace("|", "\\|")} | ${num(r.points)} / ${num(r.target)} | " +
                    "${num(r.ratio, 2)}${if (r.withinTolerance) "" else if (r.acceptable) " ⚑" else " ⚠"} |",
            )
        }
        line()
    }

    private fun flags() {
        line("## Flags")
        line()
        line("Automatic checks on the `greedy` results. *Hard*: win rate under 50%. *Trivial*: a normal fight won 99%+ of the")
        line("time for under 10% of max HP. *Soft elite*: an elite won 95%+ of the time for under 20% of max HP.")
        line()
        val flags = mutableListOf<String>()
        val greedy = cells.filter { it.spec.bot == "greedy" }
        fun who(c: Cell) = "${content.operative(c.spec.op).name} (${c.spec.deck})"
        for (enc in content.encounters) {
            val cs = greedy.filter { it.spec.encounter.id == enc.id }
            if (cs.isEmpty()) continue
            val hard = cs.filter { it.winRate < 0.5 }
            if (hard.isNotEmpty()) flags += "**Hard:** ${enc.name}: " + hard.joinToString { "${who(it)} wins ${pct(it.winRate)}" }
            if (!enc.elite) {
                val trivial = cs.filter { it.winRate >= 0.99 && it.avgHpLostPctWon < 10 }
                if (trivial.size == cs.size) {
                    flags += "**Trivial:** ${enc.name}, for every operative and deck."
                } else if (trivial.isNotEmpty()) {
                    flags += "**Trivial:** ${enc.name}, for " + trivial.joinToString { who(it) } + "."
                }
            } else {
                val soft = cs.filter { it.winRate >= 0.95 && it.avgHpLostPctWon < 20 }
                if (soft.isNotEmpty()) {
                    flags += "**Soft elite:** ${enc.name}: " + soft.joinToString { "${who(it)} loses ${num(it.avgHpLostPctWon, 0)}%" } + "."
                }
            }
            val stalls = cs.filter { it.draws > 0 }
            if (stalls.isNotEmpty()) flags += "**Stalls:** ${enc.name}: " + stalls.joinToString { "${who(it)} ${it.draws}/${it.n}" } + "."
        }
        val errors = cells.sumOf { c -> c.fights.sumOf { it.stats.botErrors } }
        if (errors > 0) flags += "**Bot errors:** $errors illegal bot choices fell back to a legal move."
        for (def in content.cards.values) {
            val r = Budget.report(def)
            if (!r.acceptable) flags += "**Budget:** ${def.name} is at ${num(r.ratio, 2)}× its target."
            if (!r.withinTolerance && r.acceptable) {
                flags += "**Budget exception ⚑:** ${def.name} is at ${num(r.ratio, 2)}× its target. Reason: ${r.exception}."
            }
        }
        if (flags.isEmpty()) line("None.") else flags.forEach { line("- $it") }
        line()
    }
}
