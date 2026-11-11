package com.kutlusoy.tvsig.utils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kutlusoy.tvsig.ui.views.RotationLayout

object OrientationHelper {
    const val ORIENTATION_AUTO = -1
    const val ORIENTATION_LANDSCAPE = 0
    const val ORIENTATION_90 = 90
    const val ORIENTATION_180 = 180
    const val ORIENTATION_270 = 270

    private const val DIALOG_MAX_WIDTH_DP = 440

    fun rotationDegrees(orientation: Int): Int = when (orientation) {
        ORIENTATION_90, ORIENTATION_180, ORIENTATION_270 -> orientation
        else -> 0
    }

    /** Call after setContentView. Fixed modes lock the system to landscape and rotate the content instead. */
    fun setOrientation(activity: Activity, orientation: Int) {
        activity.requestedOrientation =
            if (orientation == ORIENTATION_AUTO && !isTvDevice(activity)) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) as? RotationLayout ?: wrapContent(activity, content) ?: return
        root.rotationDegrees = if (orientation == ORIENTATION_AUTO) 0 else rotationDegrees(orientation)
    }

    /** Wraps a dialog view so it rotates like the activities; the dialog window must be full screen. */
    fun wrapDialogView(context: Context, view: View, orientation: Int, maxWidthDp: Int = DIALOG_MAX_WIDTH_DP): RotationLayout {
        (view.parent as? ViewGroup)?.removeView(view)
        return RotationLayout(context).apply {
            maxContentWidth = (maxWidthDp * context.resources.displayMetrics.density).toInt()
            rotationDegrees = rotationDegrees(orientation)
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun wrapContent(activity: Activity, content: ViewGroup): RotationLayout? {
        val child = content.getChildAt(0) ?: return null
        content.removeView(child)
        val rotationLayout = RotationLayout(activity)
        rotationLayout.addView(child, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(rotationLayout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return rotationLayout
    }

    fun isTvDevice(context: Context): Boolean = DisplayScale.isTvLike(context)
}
