package com.kutlusoy.tvsig.ui.views

import android.view.View
import android.view.animation.DecelerateInterpolator
import com.kutlusoy.tvsig.models.TransitionType

/** Switches between the views of the playback stage (two image views and the video container). */
class StageTransitioner(private val durationMs: Long = 700L) {

    private class Pending(val from: View, val to: View, val onHidden: (View) -> Unit)

    private var pending: Pending? = null
    private var lastEffect: TransitionType? = null

    var currentView: View? = null
        private set

    fun show(to: View, requested: TransitionType, onHidden: (View) -> Unit = {}) {
        finish()
        val from = currentView
        currentView = to

        to.animate().cancel()
        resetView(to)
        to.visibility = View.VISIBLE
        to.bringToFront()
        if (from == null || from === to) return

        val effect = if (requested == TransitionType.RANDOM) {
            TransitionType.pickRandom(lastEffect).also { lastEffect = it }
        } else {
            requested
        }
        if (effect == TransitionType.NONE) {
            hide(from, onHidden)
            return
        }

        val parent = to.parent as View
        val width = parent.width.toFloat()
        val height = parent.height.toFloat()
        val incoming = to.animate().setDuration(durationMs).setInterpolator(DecelerateInterpolator())
        val outgoing = from.animate().setDuration(durationMs).setInterpolator(DecelerateInterpolator())

        when (effect) {
            TransitionType.FADE -> {
                to.alpha = 0f
                incoming.alpha(1f)
            }
            TransitionType.SLIDE_LEFT -> {
                to.translationX = width
                incoming.translationX(0f)
                outgoing.translationX(-width)
            }
            TransitionType.SLIDE_RIGHT -> {
                to.translationX = -width
                incoming.translationX(0f)
                outgoing.translationX(width)
            }
            TransitionType.SLIDE_UP -> {
                to.translationY = height
                incoming.translationY(0f)
                outgoing.translationY(-height)
            }
            TransitionType.SLIDE_DOWN -> {
                to.translationY = -height
                incoming.translationY(0f)
                outgoing.translationY(height)
            }
            TransitionType.ZOOM_IN -> {
                to.alpha = 0f
                to.scaleX = ZOOM_IN_START
                to.scaleY = ZOOM_IN_START
                incoming.alpha(1f).scaleX(1f).scaleY(1f)
            }
            else -> {
                to.alpha = 0f
                to.scaleX = ZOOM_OUT_START
                to.scaleY = ZOOM_OUT_START
                incoming.alpha(1f).scaleX(1f).scaleY(1f)
            }
        }

        pending = Pending(from, to, onHidden)
        incoming.withEndAction { complete() }.start()
        outgoing.start()
    }

    /** Ends a running transition at once. */
    fun finish() {
        val p = pending ?: return
        p.to.animate().cancel()
        p.from.animate().cancel()
        complete()
    }

    fun reset() {
        finish()
        currentView = null
    }

    private fun complete() {
        val p = pending ?: return
        pending = null
        resetView(p.to)
        hide(p.from, p.onHidden)
    }

    private fun hide(view: View, onHidden: (View) -> Unit) {
        view.animate().cancel()
        resetView(view)
        view.visibility = View.GONE
        onHidden(view)
    }

    private fun resetView(view: View) {
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private companion object {
        const val ZOOM_IN_START = 1.3f
        const val ZOOM_OUT_START = 0.7f
    }
}
