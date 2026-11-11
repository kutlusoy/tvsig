package com.kutlusoy.tvsig.models

import com.kutlusoy.tvsig.utils.OrientationHelper

data class AppSettings(
    val baseUrl: String = "",
    val subfolder: String = "",
    val slideDelay: Int = 10,
    val transition: Int = TransitionType.DEFAULT.id,
    val orientation: Int = OrientationHelper.ORIENTATION_LANDSCAPE,
    val isDarkMode: Boolean = false,
    val pin: String = "0000"
)
