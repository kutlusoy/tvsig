package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.utils.RestartBackoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartBackoffTest {

    @Test
    fun firstCrashRestartsQuickly() {
        assertEquals(3_000L, RestartBackoff.delayMs(1))
    }

    @Test
    fun delayDoublesWithEveryCrashInTheWindow() {
        assertEquals(6_000L, RestartBackoff.delayMs(2))
        assertEquals(12_000L, RestartBackoff.delayMs(3))
    }

    @Test
    fun delayIsCapped() {
        assertEquals(120_000L, RestartBackoff.delayMs(50))
        assertTrue(RestartBackoff.delayMs(0) > 0)
    }
}
