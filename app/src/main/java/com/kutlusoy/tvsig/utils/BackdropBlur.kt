package com.kutlusoy.tvsig.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlin.math.max

/** Very soft background for content that does not fill the screen. */
object BackdropBlur {
    private const val RADIUS = 4

    /**
     * A tiny canvas in the aspect of the screen with the image mirrored over its edges and blurred.
     * The ImageView stretches it to the screen, which blurs it further.
     */
    fun create(source: Bitmap, screenAspect: Float): Bitmap {
        val layout = BackdropMath.layout(source.width, source.height, screenAspect)
        val fitted = scaleDown(source, layout.imageWidth, layout.imageHeight)
        val image = IntArray(layout.imageWidth * layout.imageHeight)
        fitted.getPixels(image, 0, layout.imageWidth, 0, 0, layout.imageWidth, layout.imageHeight)
        if (fitted !== source) fitted.recycle()

        val canvas = BackdropMath.fillMirrored(image, layout)
        BoxBlur.apply(canvas, layout.canvasWidth, layout.canvasHeight, RADIUS)
        return Bitmap.createBitmap(canvas, layout.canvasWidth, layout.canvasHeight, Bitmap.Config.ARGB_8888)
    }

    /** One frame of the video, blurred. Returns null when the frame cannot be read. */
    fun fromVideo(path: String, screenAspect: Float): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L) ?: return null
            create(frame, screenAspect).also { frame.recycle() }
        } catch (e: Exception) {
            Logger.e("Could not read a frame of the video", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Logger.d("Could not release the retriever")
            }
        }
    }

    /** Halves the bitmap step by step, a single large reduction would skip most pixels. */
    private fun scaleDown(source: Bitmap, width: Int, height: Int): Bitmap {
        var current = source
        while (current.width > width * 2 && current.height > height * 2) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, max(1, current.height / 2), true)
            if (current !== source) current.recycle()
            current = next
        }
        val result = Bitmap.createScaledBitmap(current, width, height, true)
        if (current !== source && current !== result) current.recycle()
        return result
    }
}
