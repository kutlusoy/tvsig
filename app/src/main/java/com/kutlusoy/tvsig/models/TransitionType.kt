package com.kutlusoy.tvsig.models

import kotlin.random.Random

enum class TransitionType(val id: Int) {
    NONE(0),
    FADE(1),
    SLIDE_LEFT(2),
    SLIDE_RIGHT(3),
    SLIDE_UP(4),
    SLIDE_DOWN(5),
    ZOOM_IN(6),
    ZOOM_OUT(7),
    RANDOM(8);

    companion object {
        val DEFAULT = FADE

        private val effects = values().filter { it != NONE && it != RANDOM }

        fun fromId(id: Int): TransitionType = values().firstOrNull { it.id == id } ?: DEFAULT

        /** Picks a random effect that differs from the previous one. */
        fun pickRandom(last: TransitionType?, random: Random = Random.Default): TransitionType {
            val candidates = effects.filter { it != last }
            return candidates[random.nextInt(candidates.size)]
        }
    }
}
