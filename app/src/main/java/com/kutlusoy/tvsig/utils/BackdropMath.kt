package com.kutlusoy.tvsig.utils

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The geometry of the blurred background, kept free of Android classes so it can be tested. */
object BackdropMath {
    const val CANVAS_LONG_SIDE = 64

    class Layout(val canvasWidth: Int, val canvasHeight: Int, val imageWidth: Int, val imageHeight: Int, val offsetX: Int, val offsetY: Int)

    /** A small canvas with the aspect ratio of the screen, the image sits fitted in its middle. */
    fun layout(imageWidth: Int, imageHeight: Int, screenAspect: Float): Layout {
        val aspect = if (screenAspect > 0f) screenAspect else 16f / 9f
        val canvasWidth = if (aspect >= 1f) CANVAS_LONG_SIDE else max(1, (CANVAS_LONG_SIDE * aspect).roundToInt())
        val canvasHeight = if (aspect >= 1f) max(1, (CANVAS_LONG_SIDE / aspect).roundToInt()) else CANVAS_LONG_SIDE
        val scale = min(canvasWidth / imageWidth.toFloat(), canvasHeight / imageHeight.toFloat())
        val width = max(1, (imageWidth * scale).roundToInt()).coerceAtMost(canvasWidth)
        val height = max(1, (imageHeight * scale).roundToInt()).coerceAtMost(canvasHeight)
        return Layout(canvasWidth, canvasHeight, width, height, (canvasWidth - width) / 2, (canvasHeight - height) / 2)
    }

    /** Size of the zoomed copy, the aspect ratio of the picture is kept. */
    fun zoomSize(imageWidth: Int, imageHeight: Int): Pair<Int, Int> =
        if (imageWidth >= imageHeight) {
            CANVAS_LONG_SIDE to max(1, (CANVAS_LONG_SIDE * imageHeight / imageWidth.toFloat()).roundToInt())
        } else {
            max(1, (CANVAS_LONG_SIDE * imageWidth / imageHeight.toFloat()).roundToInt()) to CANVAS_LONG_SIDE
        }

    /** Bounces an index back and forth inside 0 until length, like a mirror at both ends. */
    fun reflect(index: Int, length: Int): Int {
        if (length <= 1) return 0
        val period = 2 * length
        val m = Math.floorMod(index, period)
        return if (m < length) m else period - 1 - m
    }

    /** Puts the image into the canvas and continues it mirrored over its edges, so the colors meet without a seam. */
    fun fillMirrored(image: IntArray, layout: Layout): IntArray {
        val canvas = IntArray(layout.canvasWidth * layout.canvasHeight)
        for (y in 0 until layout.canvasHeight) {
            val sourceY = reflect(y - layout.offsetY, layout.imageHeight)
            for (x in 0 until layout.canvasWidth) {
                val sourceX = reflect(x - layout.offsetX, layout.imageWidth)
                canvas[y * layout.canvasWidth + x] = image[sourceY * layout.imageWidth + sourceX]
            }
        }
        return canvas
    }
}
