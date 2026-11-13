package com.kutlusoy.tvsig.ui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import com.kutlusoy.tvsig.R
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
    private val aura = load(R.drawable.splash_aura)
    private val glowWide = load(R.drawable.splash_glow_wide)
    private val glowNear = load(R.drawable.splash_glow_near)
    private val logo = load(R.drawable.splash_logo)
    private val spark = load(R.drawable.splash_spark)
    private val particle = load(R.drawable.splash_particle)
    private val star = load(R.drawable.splash_star)
    private val title = load(R.drawable.splash_title)
    private val subtitle = load(R.drawable.splash_subtitle)

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, SHEEN.width(), 0f,
            intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0xB3FFFFFF.toInt(), 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.36f, 0.5f, 0.64f, 1f), Shader.TileMode.CLAMP
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private val tintPaints = listOf(0xFFFFFFFF.toInt(), 0xFF8EC2FF.toInt(), 0xFFF9A8FF.toInt()).map { color ->
        Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
    }
    private val particleTarget = RectF()
    private val camera = Camera().apply { setLocation(0f, 0f, -CAMERA_DISTANCE / 72f) }
    private val logoMatrix = Matrix()
    private val stage = Matrix()
    private val backgroundTarget = RectF()

    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var onFinished: (() -> Unit)? = null
    private var finished = false

    fun start(onFinished: () -> Unit) {
        this.onFinished = onFinished
        finished = false
        if (animationsDisabled()) {
            progress = 1f
            invalidate()
            postDelayed({ finish() }, REDUCED_MOTION_HOLD_MS)
            return
        }
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
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        onFinished = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val frame = SplashTimeline.frame(progress)
        drawBackground(canvas)

        drawDust(canvas, frame)

        val push = 1f + PUSH_IN * progress
        val scale = min(width / FIT_WIDTH, height / FIT_HEIGHT) * push
        stage.setScale(scale, scale)
        stage.postTranslate(width / 2f - CONTENT_CENTER_X * scale, height / 2f - CONTENT_CENTER_Y * scale)
        canvas.save()
        canvas.concat(stage)

        draw(canvas, aura, AURA, frame.aura)
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
            draw(canvas, spark, SPARK, frame.sparkAlpha)
            canvas.restore()
        }
        draw(canvas, title, TITLE, frame.titleAlpha, frame.titleOffsetY)
        draw(canvas, subtitle, SUBTITLE, frame.subtitleAlpha, frame.subtitleOffsetY)
        drawSparkles(canvas)
        canvas.restore()
    }

    private fun drawDust(canvas: Canvas, frame: SplashFrame) {
        val seconds = progress * SplashTimeline.DURATION_MS / 1000f
        val unit = min(width / FIT_WIDTH, height / FIT_HEIGHT)
        for (dust in SplashParticles.dust) {
            val alpha = SplashParticles.dustAlpha(dust, progress, seconds)
            if (alpha <= 0f) continue
            val radius = dust.size * unit * 0.5f
            val cx = SplashParticles.dustX(dust, seconds) * width
            val cy = SplashParticles.dustY(dust, seconds) * height
            particleTarget.set(cx - radius, cy - radius, cx + radius, cy + radius)
            val tint = tintPaints[dust.tint]
            tint.alpha = (alpha * 255).toInt()
            canvas.drawBitmap(particle, null, particleTarget, tint)
        }
    }

    private fun drawSparkles(canvas: Canvas) {
        val seconds = progress * SplashTimeline.DURATION_MS / 1000f
        for (sparkle in SplashParticles.sparkles) {
            val alpha = SplashParticles.sparkleAlpha(sparkle, progress, seconds)
            if (alpha <= 0.01f) continue
            val half = SplashParticles.sparkleScale(sparkle, alpha) * 1.6f
            particleTarget.set(sparkle.x - half, sparkle.y - half, sparkle.x + half, sparkle.y + half)
            val tint = tintPaints[sparkle.tint]
            tint.alpha = (alpha * 255).toInt()
            canvas.save()
            canvas.rotate(alpha * 18f, sparkle.x, sparkle.y)
            canvas.drawBitmap(star, null, particleTarget, tint)
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
        camera.save()
        camera.rotateX(frame.logoRotateX)
        camera.rotateY(frame.logoRotateY)
        camera.getMatrix(logoMatrix)
        camera.restore()
        logoMatrix.preTranslate(-LOGO_CENTER_X, -LOGO_CENTER_Y)
        logoMatrix.postScale(frame.logoScale, frame.logoScale)
        logoMatrix.postTranslate(LOGO_CENTER_X, LOGO_CENTER_Y + frame.logoOffsetY)
    }

    private fun drawSheen(canvas: Canvas, frame: SplashFrame) {
        val layer = canvas.saveLayer(LOGO, null)
        paint.alpha = 255
        canvas.drawBitmap(logo, null, LOGO, paint)
        canvas.save()
        canvas.translate(SHEEN.left + frame.sheenOffsetX, SHEEN.top)
        canvas.skew(SHEEN_SKEW, 0f)
        sheenPaint.alpha = (frame.sheenAlpha * 255).toInt()
        canvas.drawRect(0f, 0f, SHEEN.width(), SHEEN.height(), sheenPaint)
        canvas.restore()
        canvas.restoreToCount(layer)
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
        const val PUSH_IN = 0.045f
        const val FIT_WIDTH = 900f
        const val FIT_HEIGHT = 700f
        const val CONTENT_CENTER_X = 960f
        const val CONTENT_CENTER_Y = 410f
        const val CAMERA_DISTANCE = 900f
        const val SHEEN_SKEW = -0.25f
        const val REDUCED_MOTION_HOLD_MS = 1500L

        val LOGO = RectF(744f, 147f, 1176f, 465.3f)
        val GLOW = RectF(614f, 17f, 1306f, 595.3f)
        val AURA = RectF(390f, -55f, 1530f, 725f)
        val SPARK = RectF(860f, 168f, 1290f, 252f)
        val TITLE = RectF(740f, 515f, 1180f, 680f)
        val SUBTITLE = RectF(760f, 655f, 1160f, 725f)
        val SHEEN = RectF(655f, 122f, 1225f, 527f)
        const val LOGO_CENTER_X = 960f
        const val LOGO_CENTER_Y = 306f
        const val SPARK_CENTER_X = 1075f
        const val SPARK_CENTER_Y = 210f
    }
}
