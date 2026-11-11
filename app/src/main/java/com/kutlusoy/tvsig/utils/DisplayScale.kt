package com.kutlusoy.tvsig.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Tells phones from TVs and gives TVs the same layout size whatever density they report. */
object DisplayScale {
    /** The short side of a TV screen in dp, a screen of 960 by 540 dp like Android TV recommends. */
    const val TV_SHORT_SIDE_DP = 540

    private const val MIN_DPI = 120
    private const val MAX_DPI = 640
    private const val TOLERANCE_PERCENT = 8

    /** TVs, TV boxes and every device without a touchscreen are used from a distance with a remote. */
    fun isTvLike(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val packageManager = context.packageManager
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    fun desiredDensityDpi(shortSidePx: Int, targetShortSideDp: Int = TV_SHORT_SIDE_DP): Int =
        (shortSidePx * 160f / targetShortSideDp).roundToInt().coerceIn(MIN_DPI, MAX_DPI)

    fun needsChange(currentDpi: Int, desiredDpi: Int): Boolean =
        currentDpi > 0 && abs(desiredDpi - currentDpi) * 100 / currentDpi >= TOLERANCE_PERCENT

    /** Use in attachBaseContext: TVs get the normalized density, phones and tablets keep their own. */
    fun wrap(base: Context): Context {
        if (!isTvLike(base)) return base
        val metrics = base.resources.displayMetrics
        val desired = desiredDensityDpi(min(metrics.widthPixels, metrics.heightPixels))
        val current = base.resources.configuration.densityDpi
        if (!needsChange(current, desired)) return base
        val configuration = Configuration(base.resources.configuration).apply { densityDpi = desired }
        return base.createConfigurationContext(configuration)
    }
}
