package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.utils.DisplayScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayScaleTest {

    @Test
    fun a1080pTvGetsAScreenOf960By540Dp() {
        assertEquals(320, DisplayScale.desiredDensityDpi(1080))
    }

    @Test
    fun a4kTvGetsTheSameLayoutSize() {
        assertEquals(640, DisplayScale.desiredDensityDpi(2160))
    }

    @Test
    fun a720pTvGetsTheSameLayoutSize() {
        assertEquals(213, DisplayScale.desiredDensityDpi(720))
    }

    @Test
    fun densityStaysInASaneRange() {
        assertEquals(120, DisplayScale.desiredDensityDpi(100))
        assertEquals(640, DisplayScale.desiredDensityDpi(9000))
    }

    @Test
    fun smallDifferencesAreLeftAlone() {
        assertFalse(DisplayScale.needsChange(320, 320))
        assertFalse(DisplayScale.needsChange(320, 330))
        assertTrue(DisplayScale.needsChange(160, 320))
        assertTrue(DisplayScale.needsChange(213, 320))
    }
}
