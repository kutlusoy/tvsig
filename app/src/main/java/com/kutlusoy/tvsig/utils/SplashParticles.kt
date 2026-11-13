package com.kutlusoy.tvsig.utils

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * A particle in a space with depth. [z] is 0 far away and 1 close. Far particles are small, dim and move
 * little, close ones are a bit larger and move more, which gives the parallax. [x] and [y] are shares of the
 * screen, [size] is in design units. Glitter particles are the same dots, but they flash at their own pace.
 */
data class Particle(
    val x: Float,
    val y: Float,
    val z: Float,
    val size: Float,
    val rise: Float,
    val drift: Float,
    val sway: Float,
    val bob: Float,
    val phase: Float,
    val tint: Int,
    val alpha: Float,
    val appear: Float,
    val glitter: Boolean,
    val rate: Float
)

/** Where and how a particle is drawn in one frame, in screen pixels. */
class ParticlePlacement {
    var cx = 0f
    var cy = 0f
    var radius = 0f
    var alpha = 0f
}

object SplashParticles {
    const val TINT_WHITE = 0
    const val TINT_BLUE = 1
    const val TINT_PINK = 2

    private const val MIN_VISIBLE_ALPHA = 0.01f
    private const val DUST_COUNT = 150
    private const val GLITTER_COUNT = 36

    /** Particles from far to close. */
    val particles: List<Particle> = build()
    const val FRONT_DEPTH = 0.7f

    private class Random(seed: Long) {
        private var state = seed
        fun next(): Float {
            state = (state * 6364136223846793005L + 1442695040888963407L)
            return ((state ushr 33).toInt() and 0x7FFFFFFF) / 2147483648f
        }
        fun between(low: Float, high: Float) = low + next() * (high - low)
    }

    private fun build(): List<Particle> {
        val random = Random(11)
        val result = ArrayList<Particle>()
        repeat(DUST_COUNT + GLITTER_COUNT) { index ->
            val glitter = index >= DUST_COUNT
            val z = random.next().pow(3.2f)
            val tint = when { random.next() < 0.5f -> TINT_BLUE; random.next() < 0.5f -> TINT_PINK; else -> TINT_WHITE }
            result += Particle(
                x = random.between(-0.06f, 1.06f),
                y = random.next(),
                z = z,
                size = 2.2f + z * z * 4.8f,
                rise = 0.006f + z * 0.02f,
                drift = random.between(-0.008f, 0.008f) * (0.4f + z),
                sway = random.between(0.7f, 1.7f),
                bob = random.between(0.7f, 1.7f),
                phase = random.next(),
                tint = if (glitter && tint == TINT_PINK) TINT_WHITE else tint,
                alpha = if (glitter) 0.55f + z * 0.4f else 0.32f + z * 0.36f,
                appear = random.between(0.0f, 0.12f),
                glitter = glitter,
                rate = random.between(0.18f, 0.5f)
            )
        }
        return result.sortedBy { it.z }
    }

    fun x(p: Particle, seconds: Float): Float =
        p.x + p.drift * seconds + 0.035f * (0.5f + p.z) * sin(2.0 * PI * (seconds * 0.16f * p.sway + p.phase)).toFloat()

    fun y(p: Particle, seconds: Float): Float {
        val float = 0.03f * (0.5f + p.z) * sin(2.0 * PI * (seconds * 0.13f * p.bob + p.phase * 2.3f)).toFloat()
        val y = (p.y - p.rise * seconds + float) % 1f
        return if (y < 0f) y + 1f else y
    }

    /** How far a particle follows the slow camera move. Close ones move more than the logo, far ones less. */
    fun parallax(p: Particle): Float = 0.15f + 1.7f * p.z

    /** Particles fade in one after the other. Dust twinkles softly, glitter dots light up now and then. */
    fun alpha(p: Particle, progress: Float, seconds: Float): Float {
        val fade = ((progress - p.appear) / 0.1f).coerceIn(0f, 1f)
        val wave = (0.5f + 0.5f * sin(2.0 * PI * (seconds * p.rate + p.phase * 3f)).toFloat())
        val shine = if (p.glitter) wave.pow(5) else 0.7f + 0.3f * wave
        return (p.alpha * fade * shine).coerceIn(0f, 1f)
    }

    /** Fills [out] with the place of the particle. Returns false when it is not visible in this frame. */
    fun place(p: Particle, progress: Float, seconds: Float, width: Int, height: Int, out: ParticlePlacement): Boolean {
        val alpha = alpha(p, progress, seconds)
        if (alpha <= MIN_VISIBLE_ALPHA) return false
        val unit = minOf(width / SplashTimeline.FIT_WIDTH, height / SplashTimeline.FIT_HEIGHT)
        val pan = (progress - 0.5f) * SplashTimeline.PAN
        val zoom = 1f + SplashTimeline.PUSH_IN * progress * (0.5f + p.z)
        out.alpha = alpha
        out.radius = p.size * unit * zoom * 0.75f
        out.cx = (x(p, seconds) - pan * parallax(p)) * width
        out.cy = y(p, seconds) * height
        return true
    }
}
