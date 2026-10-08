package dcbb.core.run

import dcbb.core.engine.Replay

/**
 * A run as text: operative, seed, era, and every action, so a whole run replays exactly (docs/09 "Determinism and
 * saves"). The web client copies these codes into bug reports; `sim --replay` plays them back.
 *
 * Format: `r1 <operative> <seed> <era> <action> <action> ...`, where run actions are lowercase:
 * `c<i>` choose option i · `f` spend Foresight · `g` Glimpse · `k` skip a card reward · `n` done / continue / leave.
 * Fight actions use the combat replay tokens (`E`, `S/…`, `P/…`, `F/…`, see [Replay]).
 */
data class RunReplay(val operative: String, val seed: Long, val era: String, val actions: List<RunAction>) {

    fun encode(): String = (listOf(VERSION, operative, seed.toString(), era) + actions.map { encode(it) }).joinToString(" ")

    /** Replays the actions in order. Returns the final state and the index of the first rejected action, if any. */
    fun play(engine: RunEngine): Pair<RunState, Int?> {
        var s = engine.start(operative, seed, era).state
        for ((i, a) in actions.withIndex()) {
            val out = engine.apply(s, a)
            if (out.error != null) return s to i
            s = out.state
        }
        return s to null
    }

    companion object {
        const val VERSION = "r1"

        fun parse(code: String): RunReplay {
            val parts = code.trim().split(Regex("\\s+"))
            require(parts.size >= 4 && parts[0] == VERSION) { "Not a run code (it should start with $VERSION)" }
            return RunReplay(parts[1], parts[2].toLong(), parts[3], parts.drop(4).map { decode(it) })
        }

        fun encode(a: RunAction): String = when (a) {
            is RunAction.Choose -> "c${a.index}"
            RunAction.Foresight -> "f"
            RunAction.Glimpse -> "g"
            RunAction.Skip -> "k"
            RunAction.Done -> "n"
            is RunAction.Fight -> Replay.encode(a.action)
        }

        fun decode(t: String): RunAction = when {
            t == "f" -> RunAction.Foresight
            t == "g" -> RunAction.Glimpse
            t == "k" -> RunAction.Skip
            t == "n" -> RunAction.Done
            t.length > 1 && t[0] == 'c' && t.drop(1).all { it.isDigit() } -> RunAction.Choose(t.drop(1).toInt())
            else -> RunAction.Fight(Replay.decode(t))
        }
    }
}
