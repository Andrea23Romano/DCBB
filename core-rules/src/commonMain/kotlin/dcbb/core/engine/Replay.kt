package dcbb.core.engine

import dcbb.core.content.Content
import dcbb.core.state.ShiftPair

/**
 * A fight as text: the setup plus every action, so any fight can be replayed exactly (the engine is deterministic).
 * The web client copies these codes into bug reports; `sim --replay` plays them back.
 *
 * Format: `a1 <operative> <deck> <encounter> <seed> <action> <action> ...`, where each action is one of
 * - `E` (end turn)
 * - `S/<target>/<present>/<other>` (Signature Action)
 * - `P/<card>/<target>/<track>/<cardTarget>/<face>/<A|N>/<0|1>/<recall,..>/<present-other;..>/<replace>` (play a card)
 * - `F/<top,..>/<bottom,..>/<take>` (answer a Foresee)
 *
 * Empty fields mean "none".
 */
data class Replay(val operative: String, val deck: String, val encounter: String, val seed: Long, val actions: List<Action>) {

    fun setup(content: Content) = CombatSetup(operative, content.deck(operative, deck), content.encounter(encounter).enemies, seed)

    fun encode(): String = (listOf(VERSION, operative, deck, encounter, seed.toString()) + actions.map { encode(it) }).joinToString(" ")

    companion object {
        const val VERSION = "a1"

        fun parse(code: String): Replay {
            val parts = code.trim().split(Regex("\\s+"))
            require(parts.size >= 5 && parts[0] == VERSION) { "Not a replay code (it should start with $VERSION)" }
            return Replay(parts[1], parts[2], parts[3], parts[4].toLong(), parts.drop(5).map { decode(it) })
        }

        fun encode(a: Action): String = when (a) {
            EndTurn -> "E"
            is UseSignature -> listOf("S", n(a.target), n(a.shiftPair?.presentUid), n(a.shiftPair?.otherUid)).joinToString("/")
            is PlayCard -> listOf(
                "P", a.cardUid.toString(), n(a.target), n(a.trackTarget), n(a.cardTarget), a.face.toString(),
                if (a.pay == PayStyle.AUTO) "A" else "N", if (a.allowBorrow) "1" else "0",
                a.recallUids.joinToString(","),
                a.shiftPairs.joinToString(";") { "${it.presentUid}-${n(it.otherUid)}" },
                n(a.replaceConstantUid),
            ).joinToString("/")
            is ResolveForesee -> listOf("F", a.top.joinToString(","), a.bottom.joinToString(","), n(a.take)).joinToString("/")
        }

        fun decode(s: String): Action {
            val f = s.split("/")
            return when (f[0]) {
                "E" -> EndTurn
                "S" -> UseSignature(int(f[1]), int(f[2])?.let { ShiftPair(it, int(f[3])) })
                "P" -> PlayCard(
                    cardUid = f[1].toInt(),
                    target = int(f[2]),
                    trackTarget = int(f[3]),
                    cardTarget = int(f[4]),
                    face = f[5].toInt(),
                    pay = if (f[6] == "N") PayStyle.NEUTRAL_FIRST else PayStyle.AUTO,
                    allowBorrow = f[7] == "1",
                    recallUids = ints(f[8]),
                    shiftPairs = if (f[9].isEmpty()) emptyList() else f[9].split(";").map { p ->
                        val (a, b) = p.split("-")
                        ShiftPair(a.toInt(), int(b))
                    },
                    replaceConstantUid = int(f[10]),
                )
                "F" -> ResolveForesee(ints(f[1]), ints(f[2]), int(f[3]))
                else -> throw IllegalArgumentException("Unknown action '$s'")
            }
        }

        private fun n(v: Int?) = v?.toString() ?: ""
        private fun int(s: String): Int? = s.toIntOrNull()
        private fun ints(s: String): List<Int> = if (s.isEmpty()) emptyList() else s.split(",").map { it.toInt() }
    }
}
