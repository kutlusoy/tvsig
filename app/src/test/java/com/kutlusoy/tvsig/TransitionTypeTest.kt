package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.models.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TransitionTypeTest {

    @Test
    fun unknownIdFallsBackToDefault() {
        assertEquals(TransitionType.DEFAULT, TransitionType.fromId(99))
        assertEquals(TransitionType.RANDOM, TransitionType.fromId(8))
    }

    @Test
    fun randomNeverPicksNoneOrRandomOrTheLastEffect() {
        val random = Random(42)
        var last: TransitionType? = null
        repeat(500) {
            val next = TransitionType.pickRandom(last, random)
            assertNotEquals(TransitionType.NONE, next)
            assertNotEquals(TransitionType.RANDOM, next)
            assertNotEquals(last, next)
            last = next
        }
    }

    @Test
    fun randomReachesEveryEffect() {
        val random = Random(7)
        val seen = mutableSetOf<TransitionType>()
        var last: TransitionType? = null
        repeat(500) {
            last = TransitionType.pickRandom(last, random).also { seen.add(it) }
        }
        assertTrue(seen.containsAll(TransitionType.values().filter {
            it != TransitionType.NONE && it != TransitionType.RANDOM
        }))
    }
}
