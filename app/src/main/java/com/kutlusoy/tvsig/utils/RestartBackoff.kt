package com.kutlusoy.tvsig.utils

object RestartBackoff {
    private const val BASE_DELAY_MS = 3_000L
    private const val MAX_DELAY_MS = 120_000L
    const val CRASH_WINDOW_MS = 10 * 60 * 1000L

    /** Delay before the restart after a crash, doubling for every crash inside the recent window. */
    fun delayMs(recentCrashes: Int): Long {
        val steps = (recentCrashes - 1).coerceIn(0, 10)
        return (BASE_DELAY_MS shl steps).coerceAtMost(MAX_DELAY_MS)
    }
}
