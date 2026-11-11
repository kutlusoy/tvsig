package com.kutlusoy.tvsig.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.kutlusoy.tvsig.models.BackdropMode
import com.kutlusoy.tvsig.models.FolderPath
import com.kutlusoy.tvsig.models.Source
import com.kutlusoy.tvsig.models.SourceType
import com.kutlusoy.tvsig.models.TransitionType

class PreferenceHelper private constructor(context: Context) {

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SUBFOLDER = "subfolder"
        private const val KEY_SOURCE_TYPE = "source_type"
        private const val KEY_DRIVE_ID = "drive_id"
        private const val KEY_DRIVE_PATH = "drive_path"
        private const val KEY_SLIDE_DELAY = "slide_delay"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_TRANSITION = "transition"
        private const val KEY_BACKDROP = "backdrop"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_PIN = "pin"
        private const val KEY_CLEAR_CACHE = "clear_cache"
        private const val KEY_EXIT_REQUESTED = "exit_requested"
        private const val KEY_BATTERY_PROMPTED = "battery_prompted"
        private const val KEY_SETUP_SHOWN = "setup_shown"
        private const val KEY_CRASH_TIMES = "crash_times"

        private const val DEFAULT_SLIDE_DELAY = 10
        private const val DEFAULT_PIN = "0000"

        @Volatile
        private var instance: PreferenceHelper? = null

        fun getInstance(context: Context): PreferenceHelper {
            return instance ?: synchronized(this) {
                instance ?: PreferenceHelper(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun getBaseUrl(): String = preferences.getString(KEY_BASE_URL, "") ?: ""

    fun setBaseUrl(url: String) {
        preferences.edit { putString(KEY_BASE_URL, url.trim()) }
    }

    fun getSubfolder(): String = preferences.getString(KEY_SUBFOLDER, "") ?: ""

    fun setSubfolder(subfolder: String) {
        preferences.edit { putString(KEY_SUBFOLDER, FolderPath.normalize(subfolder)) }
    }

    fun getSourceType(): Int = preferences.getInt(KEY_SOURCE_TYPE, SourceType.WEB.id)

    fun setSourceType(id: Int) {
        preferences.edit { putInt(KEY_SOURCE_TYPE, id) }
    }

    fun getDriveId(): String = preferences.getString(KEY_DRIVE_ID, "") ?: ""

    fun setDriveId(id: String) {
        preferences.edit { putString(KEY_DRIVE_ID, id.trim()) }
    }

    fun getDrivePath(): String = preferences.getString(KEY_DRIVE_PATH, "") ?: ""

    fun setDrivePath(path: String) {
        preferences.edit { putString(KEY_DRIVE_PATH, FolderPath.normalize(path)) }
    }

    /** The configured source, web server or Google Drive. */
    fun getSource(): Source =
        if (SourceType.fromId(getSourceType()) == SourceType.DRIVE) Source(SourceType.DRIVE, getDriveId(), getDrivePath())
        else Source(SourceType.WEB, getBaseUrl(), getSubfolder())

    fun getSlideDelay(): Int = preferences.getInt(KEY_SLIDE_DELAY, DEFAULT_SLIDE_DELAY)

    fun setSlideDelay(seconds: Int) {
        preferences.edit {
            putInt(KEY_SLIDE_DELAY, seconds)
        }
    }

    fun getTransition(): Int = preferences.getInt(KEY_TRANSITION, TransitionType.DEFAULT.id)

    fun setTransition(id: Int) {
        preferences.edit { putInt(KEY_TRANSITION, id) }
    }

    fun getBackdrop(): Int = preferences.getInt(KEY_BACKDROP, BackdropMode.DEFAULT.id)

    fun setBackdrop(id: Int) {
        preferences.edit { putInt(KEY_BACKDROP, id) }
    }

    fun getOrientation(): Int = preferences.getInt(KEY_ORIENTATION, OrientationHelper.ORIENTATION_LANDSCAPE)

    fun setOrientation(orientation: Int) {
        preferences.edit {
            putInt(KEY_ORIENTATION, orientation)
        }
    }

    fun isDarkMode(): Boolean = preferences.getBoolean(KEY_DARK_MODE, true)

    fun setDarkMode(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_DARK_MODE, enabled)
        }
    }

    fun getPin(): String = preferences.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setPin(pin: String) {
        preferences.edit {
            putString(KEY_PIN, pin)
        }
    }

    fun isCacheClearRequested(): Boolean = preferences.getBoolean(KEY_CLEAR_CACHE, false)

    fun setCacheClearRequest(clearRequested: Boolean) {
        preferences.edit {
            putBoolean(KEY_CLEAR_CACHE, clearRequested)
        }
    }

    fun isExitRequested(): Boolean = preferences.getBoolean(KEY_EXIT_REQUESTED, false)

    fun setExitRequested(requested: Boolean) {
        preferences.edit(commit = true) { putBoolean(KEY_EXIT_REQUESTED, requested) }
    }

    fun wasSetupShown(): Boolean = preferences.getBoolean(KEY_SETUP_SHOWN, false)

    fun setSetupShown() {
        preferences.edit { putBoolean(KEY_SETUP_SHOWN, true) }
    }

    fun wasBatteryPromptShown(): Boolean = preferences.getBoolean(KEY_BATTERY_PROMPTED, false)

    fun setBatteryPromptShown() {
        preferences.edit { putBoolean(KEY_BATTERY_PROMPTED, true) }
    }

    /** Records a crash and returns how many crashes happened inside the recent window. */
    fun recordCrash(now: Long, windowMs: Long): Int {
        val times = (preferences.getString(KEY_CRASH_TIMES, "") ?: "")
            .split(',').mapNotNull { it.toLongOrNull() }
            .filter { now - it < windowMs } + now
        preferences.edit(commit = true) { putString(KEY_CRASH_TIMES, times.joinToString(",")) }
        return times.size
    }
}
