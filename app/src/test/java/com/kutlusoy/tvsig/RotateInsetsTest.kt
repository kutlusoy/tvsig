package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.utils.OrientationHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RotateInsetsTest {

    // The screen has a status bar on top (10) and a navigation bar on the right (20), left and bottom are free.
    private fun rotate(degrees: Int) = OrientationHelper.rotateInsets(0, 10, 20, 0, degrees)

    @Test
    fun anUnrotatedScreenKeepsTheInsets() {
        assertArrayEquals(intArrayOf(0, 10, 20, 0), rotate(0))
    }

    @Test
    fun turnedByNinetyDegreesTheStatusBarIsOnTheLeftOfTheContent() {
        assertArrayEquals(intArrayOf(10, 20, 0, 0), rotate(90))
    }

    @Test
    fun turnedByOneHundredEightyDegreesTheBarsSwapSides() {
        assertArrayEquals(intArrayOf(20, 0, 0, 10), rotate(180))
    }

    @Test
    fun turnedByTwoHundredSeventyDegreesTheStatusBarIsOnTheRightOfTheContent() {
        assertArrayEquals(intArrayOf(0, 0, 10, 20), rotate(270))
    }
}
