package com.kutlusoy.tvsig.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class PreferenceHelper private constructor(context: Context) {

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SUBFOLDER = "subfolder"
        private const val KEY_SLIDE_DELAY = "slide_delay"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_PIN = "pin"
        private const val KEY_CLEAR_CACHE = "clear_cache"

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
        preferences.edit { putString(KEY_SUBFOLDER, subfolder.trim()) }
    }

    fun getSlideDelay(): Int = preferences.getInt(KEY_SLIDE_DELAY, DEFAULT_SLIDE_DELAY)

    fun setSlideDelay(seconds: Int) {
        preferences.edit {
            putInt(KEY_SLIDE_DELAY, seconds)
        }
    }

    fun getOrientation(): Int = preferences.getInt(KEY_ORIENTATION, OrientationHelper.ORIENTATION_LANDSCAPE)

    fun setOrientation(orientation: Int) {
        preferences.edit {
            putInt(KEY_ORIENTATION, orientation)
        }
    }

    fun isDarkMode(): Boolean = preferences.getBoolean(KEY_DARK_MODE, false)

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

}