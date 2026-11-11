package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.utils.BoxBlur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun red(pixel: Int) = pixel shr 16 and 0xFF

    @Test
    fun aUniformImageStaysUniform() {
        val pixels = IntArray(16 * 9) { 0xFF336699.toInt() }
        BoxBlur.apply(pixels, 16, 9, 3)
        pixels.forEach { assertEquals(0xFF336699.toInt(), it) }
    }

    @Test
    fun aHardEdgeBecomesASoftTransition() {
        val width = 40
        val height = 4
        val pixels = IntArray(width * height) { if (it % width < width / 2) black else white }
        BoxBlur.apply(pixels, width, height, 4)

        val row = IntArray(width) { red(pixels[it]) }
        assertTrue("left side stays dark", row[0] < 10)
        assertTrue("right side stays bright", row[width - 1] > 245)
        assertTrue("the edge is no longer a jump", row[width / 2 - 1] in 40..215 && row[width / 2] in 40..215)
        for (x in 1 until width) assertTrue("brightness only rises from left to right", row[x] >= row[x - 1])
    }

    @Test
    fun theAverageBrightnessIsKept() {
        val width = 30
        val height = 20
        val pixels = IntArray(width * height) { if ((it / width + it % width) % 2 == 0) black else white }
        val before = pixels.map { red(it) }.average()
        BoxBlur.apply(pixels, width, height, 5)
        val after = pixels.map { red(it) }.average()
        assertEquals(before, after, 12.0)
    }

    @Test
    fun aRadiusOfZeroChangesNothing() {
        val pixels = IntArray(9) { if (it == 4) white else black }
        val copy = pixels.copyOf()
        BoxBlur.apply(pixels, 3, 3, 0)
        assertTrue(pixels.contentEquals(copy))
    }
}
