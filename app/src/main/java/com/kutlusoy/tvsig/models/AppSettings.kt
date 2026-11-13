package com.kutlusoy.tvsig.models

import com.kutlusoy.tvsig.utils.OrientationHelper

data class AppSettings(
    val sourceType: Int = SourceType.WEB.id,
    val baseUrl: String = "",
    val subfolder: String = "",
    val driveId: String = "",
    val drivePath: String = "",
    val slideDelay: Int = 10,
    val transition: Int = TransitionType.DEFAULT.id,
    val backdrop: Int = BackdropMode.DEFAULT.id,
    val language: Int = AppLanguage.DEFAULT.id,
    val orientation: Int = OrientationHelper.ORIENTATION_LANDSCAPE,
    val isDarkMode: Boolean = true,
    val pin: String = "0000"
){
    fun toSource(): Source =
        if (SourceType.fromId(sourceType) == SourceType.DRIVE) Source(SourceType.DRIVE, driveId, drivePath)
        else Source(SourceType.WEB, baseUrl, subfolder)
}
