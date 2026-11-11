package com.kutlusoy.tvsig.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.models.AppSettings
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.repositories.MediaRepository
import com.kutlusoy.tvsig.utils.PreferenceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferenceHelper = PreferenceHelper.getInstance(application)
    private val repository = MediaRepository.getInstance(application)

    private val _settings = MutableStateFlow(loadCurrentSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _saveResult = MutableStateFlow<Boolean?>(null)
    val saveResult: StateFlow<Boolean?> = _saveResult.asStateFlow()

    private val _testStatus = MutableStateFlow("")
    val testStatus: StateFlow<String> = _testStatus.asStateFlow()

    private fun loadCurrentSettings() = AppSettings(
        sourceType = preferenceHelper.getSourceType(),
        baseUrl = preferenceHelper.getBaseUrl(),
        subfolder = preferenceHelper.getSubfolder(),
        driveId = preferenceHelper.getDriveId(),
        drivePath = preferenceHelper.getDrivePath(),
        slideDelay = preferenceHelper.getSlideDelay(),
        transition = preferenceHelper.getTransition(),
        orientation = preferenceHelper.getOrientation(),
        isDarkMode = preferenceHelper.isDarkMode(),
        pin = preferenceHelper.getPin()
    )

    fun updateSettings(
        sourceType: Int? = null,
        baseUrl: String? = null,
        subfolder: String? = null,
        driveId: String? = null,
        drivePath: String? = null,
        slideDelay: Int? = null,
        transition: Int? = null,
        orientation: Int? = null,
        isDarkMode: Boolean? = null,
        pin: String? = null
    ) {
        val current = _settings.value
        _settings.value = current.copy(
            sourceType = sourceType ?: current.sourceType,
            baseUrl = baseUrl ?: current.baseUrl,
            subfolder = subfolder ?: current.subfolder,
            driveId = driveId ?: current.driveId,
            drivePath = drivePath ?: current.drivePath,
            slideDelay = slideDelay ?: current.slideDelay,
            transition = transition ?: current.transition,
            orientation = orientation ?: current.orientation,
            isDarkMode = isDarkMode ?: current.isDarkMode,
            pin = pin ?: current.pin
        )
    }

    fun saveSettings() {
        val s = _settings.value
        with(preferenceHelper) {
            setSourceType(s.sourceType)
            setBaseUrl(s.baseUrl)
            setSubfolder(s.subfolder)
            setDriveId(s.driveId)
            setDrivePath(s.drivePath)
            setSlideDelay(s.slideDelay)
            setTransition(s.transition)
            setOrientation(s.orientation)
            setDarkMode(s.isDarkMode)
            setPin(s.pin)
        }
        _saveResult.value = true
    }

    fun verifyCurrentPin(pin: String): Boolean = pin == preferenceHelper.getPin()

    fun requestCacheClear() {
        preferenceHelper.setCacheClearRequest(true)
    }

    fun testConnection() {
        val s = _settings.value
        val app = getApplication<Application>()
        val source = s.toSource()
        if (!source.isConfigured) {
            _testStatus.value = app.getString(R.string.test_no_url)
            return
        }
        _testStatus.value = app.getString(R.string.test_running)
        viewModelScope.launch {
            _testStatus.value = when (val result = repository.testConnection(source)) {
                is Result.Success -> {
                    val files = result.data
                    app.getString(R.string.test_ok, files.size, files.count { it.isImage() }, files.count { it.isVideo() })
                }
                is Result.Error -> app.getString(R.string.test_failed, result.exception.message ?: result.exception.javaClass.simpleName)
                else -> ""
            }
        }
    }
}
