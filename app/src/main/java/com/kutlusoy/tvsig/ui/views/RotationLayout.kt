package com.kutlusoy.tvsig.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min

/**
 * Lays out its children as if the screen was rotated by [rotationDegrees] (0, 90, 180, 270).
 * For 90 and 270 the child gets the swapped screen size, so a portrait layout fills a TV
 * that is mounted upright while the system still renders in landscape.
 */
class RotationLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var rotationDegrees: Int = 0
        set(value) {
            val normalized = ((value % 360) + 360) % 360
            if (field != normalized) {
                field = normalized
                requestLayout()
            }
        }

    var maxContentWidth: Int = Int.MAX_VALUE
        set(value) {
            field = value
            requestLayout()
        }

    private val swapped get() = rotationDegrees == 90 || rotationDegrees == 270

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)

        val availableWidth = if (swapped) height else width
        val availableHeight = if (swapped) width else height

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams
            child.measure(
                getChildMeasureSpec(
                    MeasureSpec.makeMeasureSpec(min(availableWidth, maxContentWidth), MeasureSpec.EXACTLY),
                    0, params.width
                ),
                getChildMeasureSpec(
                    MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY),
                    0, params.height
                )
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val parentWidth = right - left
        val parentHeight = bottom - top
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val childLeft = (parentWidth - child.measuredWidth) / 2
            val childTop = (parentHeight - child.measuredHeight) / 2
            child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
            child.rotation = rotationDegrees.toFloat()
        }
    }
}
