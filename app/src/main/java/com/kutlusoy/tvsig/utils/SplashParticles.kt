package com.kutlusoy.tvsig.utils

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * A particle in a space with depth. [z] is 0 far away and 1 close. Far particles are small, dim and move
 * little, close ones are a bit larger and move more, which gives the parallax. [x] and [y] are shares of the
 * screen, [size] is in design units. Glitter particles are stars that flash at their own pace.
 */
data class Particle(
    val x: Float,
    val y: Float,
    val z: Float,
    val size: Float,
    val rise: Float,
    val drift: Float,
    val sway: Float,
    val phase: Float,
    val tint: Int,
    val alpha: Float,
    val appear: Float,
    val glitter: Boolean,
    val rate: Float
)

/** A glitter star on the stage, in design units. It flashes when the light sweep passes over it. */
data class Sparkle(val x: Float, val y: Float, val size: Float, val phase: Float, val tint: Int, val trigger: Float)

object SplashParticles {
    const val TINT_WHITE = 0
    const val TINT_BLUE = 1
    const val TINT_PINK = 2

    private const val DUST_COUNT = 190
    private const val GLITTER_COUNT = 54
    private const val SWEEP_START = 0.19f
    private const val SWEEP_END = 0.57f
    private const val SWEEP_LEFT = 655f
    private const val SWEEP_WIDTH = 570f
    private const val FLASH_LENGTH = 0.1f

    /** Particles from far to close. */
    val particles: List<Particle> = build()
    const val FRONT_DEPTH = 0.7f
    val sparkles: List<Sparkle> = buildSparkles()

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
            val z = random.next().let { it * it * 0.4f + it * 0.6f }
            val tint = when { random.next() < 0.5f -> TINT_BLUE; random.next() < 0.5f -> TINT_PINK; else -> TINT_WHITE }
            result += Particle(
                x = random.between(-0.06f, 1.06f),
                y = random.next(),
                z = z,
                size = if (glitter) 9f + z * 15f else 2.5f + z * z * 7.5f,
                rise = 0.02f + z * 0.07f,
                drift = random.between(-0.012f, 0.012f) * (0.4f + z),
                sway = random.between(0.6f, 1.8f),
                phase = random.next(),
                tint = if (glitter && tint == TINT_PINK) TINT_WHITE else tint,
                alpha = if (glitter) 0.55f + z * 0.35f else 0.22f + z * 0.3f,
                appear = random.between(0.0f, 0.3f),
                glitter = glitter,
                rate = random.between(0.18f, 0.5f)
            )
        }
        return result.sortedBy { it.z }
    }

    private fun buildSparkles(): List<Sparkle> {
        val random = Random(23)
        return List(14) {
            val x = random.between(700f, 1230f)
            val y = random.between(130f, 700f)
            val reach = ((x - SWEEP_LEFT) / SWEEP_WIDTH).coerceIn(0f, 1f)
            Sparkle(
                x = x, y = y,
                size = random.between(16f, 44f),
                phase = random.next(),
                tint = if (random.next() < 0.7f) TINT_WHITE else TINT_BLUE,
                trigger = SWEEP_START + (SWEEP_END - SWEEP_START) * reach + 0.03f
            )
        }
    }

    fun x(p: Particle, seconds: Float): Float =
        p.x + p.drift * seconds + 0.02f * (0.5f + p.z) * sin(2.0 * PI * (seconds * 0.2f * p.sway + p.phase)).toFloat()

    fun y(p: Particle, seconds: Float): Float {
        val y = (p.y - p.rise * seconds) % 1f
        return if (y < 0f) y + 1f else y
    }

    /** How far a particle follows the slow camera move. Close ones move more than the logo, far ones less. */
    fun parallax(p: Particle): Float = 0.15f + 1.7f * p.z

    /** Particles fade in one after the other. Dust twinkles softly, glitter flashes. */
    fun alpha(p: Particle, progress: Float, seconds: Float): Float {
        val fade = ((progress - p.appear) / 0.15f).coerceIn(0f, 1f)
        val wave = (0.5f + 0.5f * sin(2.0 * PI * (seconds * p.rate + p.phase * 3f)).toFloat())
        val shine = if (p.glitter) wave.pow(5) else 0.7f + 0.3f * wave
        return (p.alpha * fade * shine).coerceIn(0f, 1f)
    }

    /** A bright flash when the sweep arrives, then a faint slow twinkle. */
    fun sparkleAlpha(s: Sparkle, progress: Float, seconds: Float): Float {
        if (progress < s.trigger) return 0f
        val since = (progress - s.trigger) / FLASH_LENGTH
        val flash = if (since < 1f) (1f - since).pow(2) else 0f
        val twinkle = (0.5f + 0.5f * sin(2.0 * PI * (seconds * 0.22f + s.phase)).toFloat()).pow(6) * 0.5f
        val settle = ((progress - s.trigger) / 0.2f).coerceIn(0f, 1f)
        return (flash + twinkle * settle).coerceIn(0f, 1f)
    }

    /** The star turns a little while it shines. */
    fun sparkleScale(s: Sparkle, alpha: Float): Float = s.size * (0.55f + 0.45f * alpha)
}
