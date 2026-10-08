package dcbb.core.engine

enum class Stream { SHUFFLE, ENEMY, MISC }

/**
 * Seeded, splittable randomness as a value (SplitMix64 per stream), so combat state stays immutable and replayable.
 * Separate streams keep, for example, shuffles stable when enemy behavior changes.
 */
data class Rng(val shuffle: Long, val enemy: Long, val misc: Long) {

    fun nextLong(stream: Stream): Pair<Long, Rng> {
        val state = when (stream) {
            Stream.SHUFFLE -> shuffle
            Stream.ENEMY -> enemy
            Stream.MISC -> misc
        } + GOLDEN
        val next = when (stream) {
            Stream.SHUFFLE -> copy(shuffle = state)
            Stream.ENEMY -> copy(enemy = state)
            Stream.MISC -> copy(misc = state)
        }
        return mix(state) to next
    }

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int, stream: Stream): Pair<Int, Rng> {
        require(bound > 0)
        val (v, next) = nextLong(stream)
        return ((v ushr 1) % bound).toInt() to next
    }

    fun <T> shuffled(items: List<T>, stream: Stream): Pair<List<T>, Rng> {
        val list = items.toMutableList()
        var rng = this
        for (i in list.indices.reversed()) {
            val (j, r) = rng.nextInt(i + 1, stream)
            rng = r
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
        return list to rng
    }

    companion object {
        private const val GOLDEN = -0x61c8864680b583ebL

        fun seeded(seed: Long): Rng = Rng(mix(seed), mix(seed xor 0x5DEECE66DL), mix(seed + 0x2545F491L))

        private fun mix(x0: Long): Long {
            var z = x0
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
