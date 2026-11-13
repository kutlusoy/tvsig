package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.utils.CubicBezier
import com.kutlusoy.tvsig.utils.SplashTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimelineTest {
    @Test
    fun startsWithNothingVisible() {
        val frame = SplashTimeline.frame(0f)
        assertEquals(0f, frame.logoAlpha, 0f)
        assertEquals(0f, frame.titleAlpha, 0f)
        assertEquals(0f, frame.subtitleAlpha, 0f)
        assertEquals(0f, frame.glowWide, 0f)
        assertEquals(0f, frame.sheenAlpha, 0f)
    }

    @Test
    fun endsWithTheSettledLogoAndTexts() {
        val frame = SplashTimeline.frame(1f)
        assertEquals(1f, frame.logoAlpha, 0f)
        assertEquals(1f, frame.titleAlpha, 0f)
        assertEquals(1f, frame.subtitleAlpha, 0f)
        assertEquals(0f, frame.logoOffsetY, 0f)
        assertEquals(1f, frame.logoScale, 0f)
        assertEquals(0f, frame.sheenAlpha, 0f)
        assertEquals(0f, frame.sparkAlpha, 0f)
    }

    @Test
    fun theTextsFollowTheLogoInOrder() {
        fun firstVisible(value: (com.kutlusoy.tvsig.utils.SplashFrame) -> Float): Float =
            (0..1000).map { it / 1000f }.first { value(SplashTimeline.frame(it)) > 0.05f }

        val logo = firstVisible { it.logoAlpha }
        val title = firstVisible { it.titleAlpha }
        val subtitle = firstVisible { it.subtitleAlpha }
        assertTrue(logo < title && title < subtitle)
    }

    @Test
    fun valuesStayInTheirRange() {
        for (i in 0..2000) {
            val f = SplashTimeline.frame(i / 2000f)
            listOf(f.logoAlpha, f.glowWide, f.glowNear, f.sheenAlpha, f.titleAlpha, f.subtitleAlpha, f.sparkAlpha)
                .forEach { assertTrue("alpha out of range at $i: $it", it in 0f..1f) }
        }
    }

    @Test
    fun theGlowKeepsBreathingAfterTheEntrance() {
        val values = (700..1000).map { SplashTimeline.frame(it / 1000f).glowWide }
        assertTrue(values.max() - values.min() > 0.05f)
    }

    @Test
    fun theLogoMotionHasNoJumps() {
        var last = SplashTimeline.frame(0f)
        for (i in 1..1000) {
            val frame = SplashTimeline.frame(i / 1000f)
            assertTrue(kotlin.math.abs(frame.logoScale - last.logoScale) < 0.002f)
            assertTrue(frame.logoScale <= 1f)
            last = frame
        }
    }

    @Test
    fun easingRunsFromZeroToOneAndNeverGoesBack() {
        val ease = CubicBezier(0.22f, 0.72f, 0.25f, 1f)
        assertEquals(0f, ease.ease(0f), 0f)
        assertEquals(1f, ease.ease(1f), 0f)
        var last = 0f
        for (i in 1..100) {
            val value = ease.ease(i / 100f)
            assertTrue(value >= last)
            last = value
        }
    }
}

class SplashParticlesTest {
    @Test
    fun particlesStayOnTheScreenAndInRange() {
        for (particle in com.kutlusoy.tvsig.utils.SplashParticles.particles) {
            for (second in 0..60) {
                val y = com.kutlusoy.tvsig.utils.SplashParticles.y(particle, second.toFloat())
                assertTrue(y in 0f..1f)
                val alpha = com.kutlusoy.tvsig.utils.SplashParticles.alpha(particle, 0.5f, second.toFloat())
                assertTrue(alpha in 0f..1f)
            }
        }
    }

    @Test
    fun particlesAreInvisibleAtTheStart() {
        com.kutlusoy.tvsig.utils.SplashParticles.particles.forEach {
            if (it.appear > 0f) assertEquals(0f, com.kutlusoy.tvsig.utils.SplashParticles.alpha(it, 0f, 0f), 0f)
        }
    }

    @Test
    fun closeParticlesAreLargerAndMoveMore() {
        val particles = com.kutlusoy.tvsig.utils.SplashParticles.particles
        val far = particles.filter { !it.glitter && it.z < 0.3f }
        val close = particles.filter { !it.glitter && it.z > 0.7f }
        assertTrue(far.isNotEmpty() && close.isNotEmpty())
        assertTrue(far.map { it.size }.average() < close.map { it.size }.average())
        assertTrue(far.maxOf { it.size } < 6f && close.maxOf { it.size } < 12f)
        assertTrue(com.kutlusoy.tvsig.utils.SplashParticles.parallax(far.first()) < com.kutlusoy.tvsig.utils.SplashParticles.parallax(close.first()))
    }

    @Test
    fun thereIsDustAndGlitterInEveryDepth() {
        val particles = com.kutlusoy.tvsig.utils.SplashParticles.particles
        assertTrue(particles.count { it.glitter } >= 50)
        assertTrue(particles.count { !it.glitter } >= 180)
        assertTrue(particles.zipWithNext().all { it.first.z <= it.second.z })
    }

    @Test
    fun sparklesFlashWhenTheSweepArrives() {
        for (sparkle in com.kutlusoy.tvsig.utils.SplashParticles.sparkles) {
            assertEquals(0f, com.kutlusoy.tvsig.utils.SplashParticles.sparkleAlpha(sparkle, sparkle.trigger - 0.01f, 1f), 0f)
            assertTrue(com.kutlusoy.tvsig.utils.SplashParticles.sparkleAlpha(sparkle, sparkle.trigger + 0.005f, 1f) > 0.6f)
        }
    }

    @Test
    fun sparklesFollowTheSweepFromLeftToRight() {
        val sorted = com.kutlusoy.tvsig.utils.SplashParticles.sparkles.sortedBy { it.x }
        assertTrue(sorted.zipWithNext().all { it.first.trigger <= it.second.trigger })
    }
}

class SplashMotionTest {
    @Test
    fun particlesVisiblyMoveDuringTheIntro() {
        val particles = com.kutlusoy.tvsig.utils.SplashParticles.particles
        val moved = particles.map {
            val dx = com.kutlusoy.tvsig.utils.SplashParticles.x(it, 5f) - com.kutlusoy.tvsig.utils.SplashParticles.x(it, 0f)
            val dy = com.kutlusoy.tvsig.utils.SplashParticles.y(it, 5f) - com.kutlusoy.tvsig.utils.SplashParticles.y(it, 0f)
            kotlin.math.hypot(dx, dy)
        }
        assertTrue(moved.average() > 0.1)
        assertTrue(moved.min() > 0.05)
    }
}
