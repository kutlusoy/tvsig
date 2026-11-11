package com.kutlusoy.tvsig.remote

import com.kutlusoy.tvsig.models.AppSettings
import com.kutlusoy.tvsig.models.BackdropMode
import com.kutlusoy.tvsig.models.FolderPath
import com.kutlusoy.tvsig.models.SourceType
import com.kutlusoy.tvsig.models.TransitionType

/** The settings that can be changed from the phone. The PIN is deliberately not part of it. */
data class RemoteSettings(
    val sourceType: Int = 0,
    val baseUrl: String = "",
    val subfolder: String = "",
    val driveId: String = "",
    val drivePath: String = "",
    val slideDelay: Int = 10,
    val transition: Int = 1,
    val backdrop: Int = 0,
    val orientation: Int = 0
) {
    /** Returns the settings with the values of this request, or null when a value is not valid. */
    fun applyTo(current: AppSettings): AppSettings? {
        if (SourceType.values().none { it.id == sourceType }) return null
        if (TransitionType.values().none { it.id == transition }) return null
        if (BackdropMode.values().none { it.id == backdrop }) return null
        if (orientation !in ORIENTATIONS) return null
        if (slideDelay !in MIN_DELAY..MAX_DELAY) return null
        if (listOf(baseUrl, subfolder, driveId, drivePath).any { it.length > MAX_TEXT }) return null

        return current.copy(
            sourceType = sourceType,
            baseUrl = baseUrl.trim(),
            subfolder = FolderPath.normalize(subfolder),
            driveId = driveId.trim(),
            drivePath = FolderPath.normalize(drivePath),
            slideDelay = slideDelay,
            transition = transition,
            backdrop = backdrop,
            orientation = orientation
        )
    }

    companion object {
        private val ORIENTATIONS = setOf(-1, 0, 90, 180, 270)
        private const val MIN_DELAY = 3
        private const val MAX_DELAY = 3600
        private const val MAX_TEXT = 2000

        fun from(settings: AppSettings) = RemoteSettings(
            sourceType = settings.sourceType,
            baseUrl = settings.baseUrl,
            subfolder = settings.subfolder,
            driveId = settings.driveId,
            drivePath = settings.drivePath,
            slideDelay = settings.slideDelay,
            transition = settings.transition,
            backdrop = settings.backdrop,
            orientation = settings.orientation
        )
    }
}
