package com.kutlusoy.tvsig.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kutlusoy.tvsig.models.AppSettings
import com.kutlusoy.tvsig.utils.PreferenceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceHelper = PreferenceHelper.getInstance(application)

    private val _settings = MutableStateFlow(loadCurrentSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _saveResult = MutableStateFlow<Boolean?>(null)
    val saveResult: StateFlow<Boolean?> = _saveResult.asStateFlow()

    private fun loadCurrentSettings() = AppSettings(
        baseUrl = preferenceHelper.getBaseUrl(),
        subfolder = preferenceHelper.getSubfolder(),
        slideDelay = preferenceHelper.getSlideDelay(),
        orientation = preferenceHelper.getOrientation(),
        isDarkMode = preferenceHelper.isDarkMode(),
        pin = preferenceHelper.getPin()
    )

    fun updateSettings(
        baseUrl: String? = null,
        subfolder: String? = null,
        slideDelay: Int? = null,
        orientation: Int? = null,
        isDarkMode: Boolean? = null,
        pin: String? = null
    ) {
        val current = _settings.value
        _settings.value = current.copy(
            baseUrl = baseUrl ?: current.baseUrl,
            subfolder = subfolder ?: current.subfolder,
            slideDelay = slideDelay ?: current.slideDelay,
            orientation = orientation ?: current.orientation,
            isDarkMode = isDarkMode ?: current.isDarkMode,
            pin = pin ?: current.pin
        )
    }

    fun saveSettings() {
        val s = _settings.value
        with(preferenceHelper) {
            setBaseUrl(s.baseUrl)
            setSubfolder(s.subfolder)
            setSlideDelay(s.slideDelay)
            setOrientation(s.orientation)
            setDarkMode(s.isDarkMode)
            setPin(s.pin)
        }
        _saveResult.value = true
    }

    fun requestCacheClear() {
        preferenceHelper.setCacheClearRequest(true)
    }
}
