package com.kutlusoy.tvsig.utils

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Dust that drifts across the whole screen. Positions are shares of the screen, sizes are design units. */
data class DustParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val rise: Float,
    val sway: Float,
    val phase: Float,
    val tint: Int,
    val alpha: Float,
    val appear: Float
)

/** A glitter star on the stage, in design units. It flashes when the light sweep passes over it. */
data class Sparkle(val x: Float, val y: Float, val size: Float, val phase: Float, val tint: Int, val trigger: Float)

object SplashParticles {
    const val TINT_WHITE = 0
    const val TINT_BLUE = 1
    const val TINT_PINK = 2

    private const val SWEEP_START = 0.19f
    private const val SWEEP_END = 0.57f
    private const val SWEEP_LEFT = 655f
    private const val SWEEP_WIDTH = 570f
    private const val FLASH_LENGTH = 0.1f

    val dust: List<DustParticle> = build()
    val sparkles: List<Sparkle> = buildSparkles()

    private class Random(seed: Long) {
        private var state = seed
        fun next(): Float {
            state = (state * 6364136223846793005L + 1442695040888963407L)
            return ((state ushr 33).toInt() and 0x7FFFFFFF) / 2147483648f
        }
        fun between(low: Float, high: Float) = low + next() * (high - low)
    }

    private fun build(): List<DustParticle> {
        val random = Random(11)
        val result = ArrayList<DustParticle>()
        repeat(34) {
            val depth = random.next()
            result += DustParticle(
                x = random.next(), y = random.next(),
                size = 3f + depth * depth * 16f,
                rise = 0.006f + depth * 0.03f,
                sway = random.between(0.6f, 1.8f),
                phase = random.next(),
                tint = when { random.next() < 0.5f -> TINT_BLUE; random.next() < 0.5f -> TINT_PINK; else -> TINT_WHITE },
                alpha = 0.16f + (1f - depth) * 0.3f,
                appear = random.between(0.02f, 0.3f)
            )
        }
        return result
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

    fun dustX(p: DustParticle, seconds: Float): Float =
        p.x + 0.012f * sin(2.0 * PI * (seconds * 0.12f * p.sway + p.phase)).toFloat()

    fun dustY(p: DustParticle, seconds: Float): Float {
        val y = (p.y - p.rise * seconds) % 1f
        return if (y < 0f) y + 1f else y
    }

    /** Dust fades in one after the other and keeps a soft twinkle. */
    fun dustAlpha(p: DustParticle, progress: Float, seconds: Float): Float {
        val fade = ((progress - p.appear) / 0.15f).coerceIn(0f, 1f)
        val twinkle = 0.75f + 0.25f * sin(2.0 * PI * (seconds * 0.35f * p.sway + p.phase * 3f)).toFloat()
        return (p.alpha * fade * twinkle).coerceIn(0f, 1f)
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
