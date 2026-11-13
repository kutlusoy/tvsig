package com.kutlusoy.tvsig.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.utils.ParticlePlacement
import com.kutlusoy.tvsig.utils.SplashFrame
import com.kutlusoy.tvsig.utils.SplashParticles
import com.kutlusoy.tvsig.utils.SplashTimeline
import kotlin.math.min

/**
 * Draws the intro of the app. Everything is a finished bitmap that is only moved and faded, the glow is
 * blurred once when the graphics are generated (design/generate-splash.js), so a frame costs a few draws.
 * The stage has the size of the design, 1920 by 1080 units, and the content is centered and fitted.
 */
class SplashAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val background = load(R.drawable.splash_background)
    private val glowWide = load(R.drawable.splash_glow_wide)
    private val glowNear = load(R.drawable.splash_glow_near)
    private val logo = load(R.drawable.splash_logo)
    private val spark = load(R.drawable.splash_spark)
    private val dot = load(R.drawable.splash_particle)
    private val star = load(R.drawable.splash_star)
    private val dotSharp = load(R.drawable.splash_dot_sharp)
    private val dotSoft = load(R.drawable.splash_dot_soft)
    private val title = load(R.drawable.splash_title)
    private val subtitle = load(R.drawable.splash_subtitle)

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val silhouette = Paint(Paint.FILTER_BITMAP_FLAG)
    private val logoWhite = tinted(logo, 0xFFFFFFFF.toInt())
    private val sheenPath = Path()
    private val tintColors = intArrayOf(0xFFFFFFFF.toInt(), 0xFF8EC2FF.toInt(), 0xFFF9A8FF.toInt())
    private val sprites = HashMap<Bitmap, Array<Bitmap>>()
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val particleTarget = RectF()
    private val placement = ParticlePlacement()
    private val logoMatrix = Matrix()
    private val stage = Matrix()
    private val backgroundTarget = RectF()

    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var onFinished: (() -> Unit)? = null
    private var finished = false
    private var idleMs = 0f
    private var idleAnimator: ValueAnimator? = null
    private var warmedUp = false
    private var pendingStart = false

    fun start(onFinished: () -> Unit) {
        this.onFinished = onFinished
        finished = false
        if (animationsDisabled()) {
            progress = 1f
            invalidate()
            postDelayed({ finish() }, REDUCED_MOTION_HOLD_MS)
            return
        }
        pendingStart = true
        if (warmedUp) beginAnimation() else invalidate()
    }

    private fun beginAnimation() {
        if (!pendingStart) return
        pendingStart = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SplashTimeline.DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedFraction
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
            })
            start()
        }
    }

    /** Keeps the last picture alive: particles keep drifting and the glow keeps breathing until the view goes. */
    fun hold() {
        if (idleAnimator != null || animationsDisabled()) return
        val startedAt = System.nanoTime()
        idleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = IDLE_LOOP_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                idleMs = (System.nanoTime() - startedAt) / 1_000_000f
                invalidate()
            }
            start()
        }
    }

    /** Ends the animation at once and reports it as finished. */
    fun skip() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        onFinished?.invoke()
        onFinished = null
    }

    override fun onDetachedFromWindow() {
        idleAnimator?.cancel()
        idleAnimator = null
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        onFinished = null
        super.onDetachedFromWindow()
    }

    /** Every bitmap is drawn once under the opaque background, so the textures are uploaded before the animation runs. */
    private fun warmUp(canvas: Canvas) {
        val target = RectF(0f, 0f, 16f, 16f)
        listOf(glowWide, glowNear, logoWhite, logo, spark, title, subtitle) + listOf(star, dot, dotSharp, dotSoft).flatMap { sprites.getOrPut(it) { Array(tintColors.size) { tint -> tinted(it, tintColors[tint]) } }.toList() }.forEach {
            canvas.drawBitmap(it, null, target, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!warmedUp) {
            warmedUp = true
            paint.alpha = 255
            warmUp(canvas)
            post { beginAnimation() }
        }
        val frame = SplashTimeline.frame(progress, idleMs)
        drawBackground(canvas)

        drawParticles(canvas, front = false)

        val push = 1f + SplashTimeline.PUSH_IN * progress
        val scale = min(width / SplashTimeline.FIT_WIDTH, height / SplashTimeline.FIT_HEIGHT) * push
        stage.setScale(scale, scale)
        stage.postTranslate(
            width / 2f - CONTENT_CENTER_X * scale - pan() * width * STAGE_PARALLAX,
            height / 2f - CONTENT_CENTER_Y * scale
        )
        canvas.save()
        canvas.concat(stage)

        updateLogoMatrix(frame)
        canvas.save()
        canvas.concat(logoMatrix)
        draw(canvas, glowWide, GLOW, frame.glowWide * frame.logoAlpha)
        draw(canvas, glowNear, GLOW, frame.glowNear * frame.logoAlpha)
        draw(canvas, logo, LOGO, frame.logoAlpha)
        if (frame.sheenAlpha > 0f) drawSheen(canvas, frame)
        canvas.restore()

        if (frame.sparkAlpha > 0f) {
            canvas.save()
            canvas.scale(frame.sparkScale, frame.sparkScale, SPARK_CENTER_X, SPARK_CENTER_Y)
            canvas.rotate((progress - SPARK_PEAK) * SPARK_TURN, SPARK_CENTER_X, SPARK_CENTER_Y)
            draw(canvas, spark, SPARK, frame.sparkAlpha)
            canvas.restore()
        }
        draw(canvas, title, TITLE, frame.titleAlpha, frame.titleOffsetY)
        draw(canvas, subtitle, SUBTITLE, frame.subtitleAlpha, frame.subtitleOffsetY)
        drawSparkles(canvas)
        canvas.restore()
        drawParticles(canvas, front = true)
    }

    /** The slow camera move across the intro, as a share of the screen width. */
    private fun pan(): Float = (progress - 0.5f) * SplashTimeline.PAN

    private fun drawParticles(canvas: Canvas, front: Boolean) {
        val seconds = (progress * SplashTimeline.DURATION_MS + idleMs) / 1000f
        for (particle in SplashParticles.particles) {
            if ((particle.z >= SplashParticles.FRONT_DEPTH) != front) continue
            if (!SplashParticles.place(particle, progress, seconds, width, height, placement)) continue
            particleTarget.set(
                placement.cx - placement.radius, placement.cy - placement.radius,
                placement.cx + placement.radius, placement.cy + placement.radius
            )
            val tint = if (particle.z < HAZE_DEPTH) SplashParticles.TINT_BLUE else particle.tint
            spritePaint.alpha = (placement.alpha * 255).toInt().coerceIn(0, 255)
            if (particle.glitter) {
                canvas.save()
                canvas.rotate(placement.alpha * 20f, placement.cx, placement.cy)
                canvas.drawBitmap(sprite(star, tint), null, particleTarget, spritePaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(sprite(focus(particle.z), tint), null, particleTarget, spritePaint)
            }
        }
    }

    /** The colors are put into the bitmaps once, so drawing needs no color filter. */
    private fun sprite(bitmap: Bitmap, tint: Int): Bitmap =
        sprites.getOrPut(bitmap) { Array(tintColors.size) { tinted(bitmap, tintColors[it]) } }[tint]

    private fun tinted(source: Bitmap, color: Int): Bitmap {
        val copy = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(copy).apply {
            drawBitmap(source, 0f, 0f, null)
            drawColor(color, PorterDuff.Mode.SRC_IN)
        }
        return copy
    }

    /** Depth of field: far particles are soft, the closest ones are almost sharp. */
    private fun focus(z: Float): Bitmap = when {
        z >= SplashParticles.FRONT_DEPTH -> dotSharp
        z >= SHARPER_DEPTH -> dot
        else -> dotSoft
    }

    private fun drawSparkles(canvas: Canvas) {
        val seconds = (progress * SplashTimeline.DURATION_MS + idleMs) / 1000f
        for (sparkle in SplashParticles.sparkles) {
            val alpha = SplashParticles.sparkleAlpha(sparkle, progress, seconds)
            if (alpha <= 0.01f) continue
            val half = SplashParticles.sparkleScale(sparkle, alpha) * 1.6f
            particleTarget.set(sparkle.x - half, sparkle.y - half, sparkle.x + half, sparkle.y + half)
            spritePaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.rotate(alpha * 18f, sparkle.x, sparkle.y)
            canvas.drawBitmap(sprite(star, sparkle.tint), null, particleTarget, spritePaint)
            canvas.restore()
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val bw = background.width.toFloat()
        val bh = background.height.toFloat()
        val fill = maxOf(width / bw, height / bh)
        val w = bw * fill
        val h = bh * fill
        backgroundTarget.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        paint.alpha = 255
        canvas.drawBitmap(background, null, backgroundTarget, paint)
    }

    private fun updateLogoMatrix(frame: SplashFrame) {
        logoMatrix.setScale(frame.logoScale, frame.logoScale, LOGO_CENTER_X, LOGO_CENTER_Y)
        logoMatrix.postTranslate(0f, frame.logoOffsetY)
    }

    /** Light band over the logo: the white silhouette of the logo, clipped to a slanted band. No offscreen layer. */
    private fun drawSheen(canvas: Canvas, frame: SplashFrame) {
        val center = SHEEN.centerX() + frame.sheenOffsetX
        for ((half, share) in SHEEN_BANDS) {
            sheenPath.reset()
            sheenPath.moveTo(center - half + SHEEN_SLANT, LOGO.top - 4f)
            sheenPath.lineTo(center + half + SHEEN_SLANT, LOGO.top - 4f)
            sheenPath.lineTo(center + half - SHEEN_SLANT, LOGO.bottom + 4f)
            sheenPath.lineTo(center - half - SHEEN_SLANT, LOGO.bottom + 4f)
            sheenPath.close()
            canvas.save()
            canvas.clipPath(sheenPath)
            silhouette.alpha = (frame.sheenAlpha * share * 255).toInt()
            canvas.drawBitmap(logoWhite, null, LOGO, silhouette)
            canvas.restore()
        }
    }

    private fun draw(canvas: Canvas, bitmap: Bitmap, target: RectF, alpha: Float, offsetY: Float = 0f) {
        if (alpha <= 0f) return
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        if (offsetY == 0f) {
            canvas.drawBitmap(bitmap, null, target, paint)
        } else {
            canvas.save()
            canvas.translate(0f, offsetY)
            canvas.drawBitmap(bitmap, null, target, paint)
            canvas.restore()
        }
    }

    private fun load(@DrawableRes id: Int): Bitmap =
        BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false })

    private fun animationsDisabled(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private companion object {
        const val HAZE_DEPTH = 0.25f
        const val SHARPER_DEPTH = 0.4f
        const val STAGE_PARALLAX = 1f
        const val CONTENT_CENTER_X = 960f
        const val CONTENT_CENTER_Y = 410f
        const val SHEEN_SLANT = 16f
        val SHEEN_BANDS = listOf(70f to 0.16f, 36f to 0.3f, 12f to 0.5f)
        const val REDUCED_MOTION_HOLD_MS = 1500L
        const val IDLE_LOOP_MS = 1000L

        val LOGO = RectF(744f, 147f, 1176f, 465.3f)
        val GLOW = RectF(614f, 17f, 1306f, 595.3f)
        val SPARK = RectF(1012f, 8f, 1312f, 308f)
        val TITLE = RectF(740f, 515f, 1180f, 680f)
        val SUBTITLE = RectF(760f, 655f, 1160f, 725f)
        val SHEEN = RectF(655f, 122f, 1225f, 527f)
        const val LOGO_CENTER_X = 960f
        const val LOGO_CENTER_Y = 306f
        const val SPARK_PEAK = 0.45f
        const val SPARK_TURN = 130f
        const val SPARK_CENTER_X = 1162f
        const val SPARK_CENTER_Y = 158f
    }
}
