package com.kutlusoy.tvsig.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.kutlusoy.tvsig.models.BackdropMode
import kotlin.math.max

/** Very soft background for content that does not fill the screen. */
object BackdropBlur {
    private const val RADIUS = 4
    private const val ZOOM_DIM = 0.85f

    /**
     * A tiny, strongly blurred picture for the area around the content. It is only ever scaled evenly,
     * the ImageView crops it to the screen and never stretches it, which blurs it further.
     */
    fun create(source: Bitmap, screenAspect: Float, mode: BackdropMode): Bitmap =
        if (mode == BackdropMode.MIRROR) mirrored(source, screenAspect) else zoomed(source)

    /** A small copy with the proportions of the picture. The ImageView zooms it evenly until it covers the screen. */
    private fun zoomed(source: Bitmap): Bitmap {
        val (width, height) = BackdropMath.zoomSize(source.width, source.height)
        val small = scaleDown(source, width, height)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== source) small.recycle()
        for (i in pixels.indices) pixels[i] = dim(pixels[i], ZOOM_DIM)
        BoxBlur.apply(pixels, width, height, RADIUS)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun dim(pixel: Int, factor: Float): Int {
        val red = ((pixel shr 16 and 0xFF) * factor).toInt()
        val green = ((pixel shr 8 and 0xFF) * factor).toInt()
        val blue = ((pixel and 0xFF) * factor).toInt()
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun mirrored(source: Bitmap, screenAspect: Float): Bitmap {
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
    fun fromVideo(path: String, screenAspect: Float, mode: BackdropMode): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L) ?: return null
            create(frame, screenAspect, mode).also { frame.recycle() }
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
