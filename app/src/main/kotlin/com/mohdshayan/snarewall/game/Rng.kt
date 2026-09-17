package com.mohdshayan.snarewall.game

/** SplitMix64. Tiny, fast, and identical on every device, so the daily map is the same for everyone. */
class Rng(var state: Long) {
    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in 0 until [bound]. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun nextFloat(): Float = ((nextLong() ushr 40).toFloat() / (1L shl 24).toFloat())

    companion object {
        /** A stable seed from text, for example a UTC date key. */
        fun seedOf(text: String): Long {
            var h = 1125899906842597L
            for (c in text) h = 31 * h + c.code
            return h
        }
    }
}
