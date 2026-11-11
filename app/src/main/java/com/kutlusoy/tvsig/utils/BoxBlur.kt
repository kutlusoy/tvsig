package com.kutlusoy.tvsig.utils

/** Blur on a small ARGB pixel array, several passes of a box blur come close to a gaussian blur. */
object BoxBlur {

    fun apply(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        if (radius < 1 || width < 1 || height < 1) return
        val temp = IntArray(pixels.size)
        repeat(passes) {
            pass(pixels, temp, width, height, radius, horizontal = true)
            pass(temp, pixels, width, height, radius, horizontal = false)
        }
    }

    private fun pass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = 2 * radius + 1

        for (line in 0 until lines) {
            fun index(i: Int) = if (horizontal) line * width + i else i * width + line

            var red = 0
            var green = 0
            var blue = 0
            for (i in -radius..radius) {
                val pixel = src[index(i.coerceIn(0, length - 1))]
                red += pixel shr 16 and 0xFF
                green += pixel shr 8 and 0xFF
                blue += pixel and 0xFF
            }
            for (i in 0 until length) {
                dst[index(i)] = (0xFF shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
                val added = src[index((i + radius + 1).coerceAtMost(length - 1))]
                val removed = src[index((i - radius).coerceAtLeast(0))]
                red += (added shr 16 and 0xFF) - (removed shr 16 and 0xFF)
                green += (added shr 8 and 0xFF) - (removed shr 8 and 0xFF)
                blue += (added and 0xFF) - (removed and 0xFF)
            }
        }
    }
}
