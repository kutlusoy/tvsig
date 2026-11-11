package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.utils.BackdropMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropMathTest {

    @Test
    fun reflectsAtBothEnds() {
        // 0 1 2 | 2 1 0 | 0 1 2
        assertEquals(listOf(0, 1, 2, 2, 1, 0, 0, 1, 2), (0..8).map { BackdropMath.reflect(it, 3) })
        assertEquals(listOf(0, 0, 1, 2), (-1..2).map { BackdropMath.reflect(it, 3) })
        assertEquals(0, BackdropMath.reflect(5, 1))
    }

    @Test
    fun aLandscapeImageOnAPortraitScreenGetsBarsAtTheTopAndBottom() {
        val layout = BackdropMath.layout(1920, 1080, 9f / 16f)
        assertEquals(36, layout.canvasWidth)
        assertEquals(64, layout.canvasHeight)
        assertEquals(36, layout.imageWidth)
        assertEquals(20, layout.imageHeight)
        assertEquals(0, layout.offsetX)
        assertEquals(22, layout.offsetY)
    }

    @Test
    fun aPortraitImageOnALandscapeScreenGetsBarsAtTheSides() {
        val layout = BackdropMath.layout(1080, 1920, 16f / 9f)
        assertEquals(64, layout.canvasWidth)
        assertEquals(36, layout.canvasHeight)
        assertTrue(layout.offsetX > 0)
        assertEquals(0, layout.offsetY)
    }

    @Test
    fun anImageThatMatchesTheScreenHasNoBars() {
        val layout = BackdropMath.layout(1920, 1080, 16f / 9f)
        assertEquals(0, layout.offsetX)
        assertEquals(0, layout.offsetY)
        assertEquals(layout.canvasWidth, layout.imageWidth)
    }

    @Test
    fun theMirroredFillContinuesTheEdgeColors() {
        val layout = BackdropMath.layout(4, 2, 1f)
        val image = IntArray(layout.imageWidth * layout.imageHeight) { it / layout.imageWidth }
        val canvas = BackdropMath.fillMirrored(image, layout)

        val centerColumn = layout.canvasWidth / 2
        val firstImageRow = layout.offsetY
        val above = canvas[(firstImageRow - 1) * layout.canvasWidth + centerColumn]
        val atEdge = canvas[firstImageRow * layout.canvasWidth + centerColumn]
        assertEquals("the row above the image repeats the edge row", atEdge, above)
    }

    @Test
    fun theUnknownAspectFallsBackToSixteenByNine() {
        val layout = BackdropMath.layout(1920, 1080, 0f)
        assertEquals(64, layout.canvasWidth)
        assertEquals(36, layout.canvasHeight)
    }

    @Test
    fun theZoomedCopyKeepsTheProportionsOfThePicture() {
        assertEquals(64 to 36, BackdropMath.zoomSize(1920, 1080))
        assertEquals(64 to 48, BackdropMath.zoomSize(4000, 3000))
        assertEquals(36 to 64, BackdropMath.zoomSize(1080, 1920))
        assertEquals(64 to 1, BackdropMath.zoomSize(6400, 10))
    }
}
