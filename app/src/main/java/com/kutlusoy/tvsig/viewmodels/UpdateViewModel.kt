package com.kutlusoy.tvsig.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutlusoy.tvsig.BuildConfig
import com.kutlusoy.tvsig.update.ReleaseInfo
import com.kutlusoy.tvsig.update.UpdateEvents
import com.kutlusoy.tvsig.update.UpdateException
import com.kutlusoy.tvsig.update.UpdateManager
import com.kutlusoy.tvsig.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    enum class Kind {
        IDLE, CHECKING, UP_TO_DATE, AVAILABLE, NOT_FOUND, CHECK_FAILED,
        DOWNLOADING, VERIFYING, INSTALLING, NEEDS_PERMISSION, INSTALL_FAILED, DEBUG_BUILD
    }

    class State(
        val kind: Kind,
        val release: ReleaseInfo? = null,
        val percent: Int = 0,
        val error: UpdateException? = null,
        val detail: String = ""
    ) {
        val busy: Boolean get() = kind in setOf(Kind.CHECKING, Kind.DOWNLOADING, Kind.VERIFYING, Kind.INSTALLING)
        val canInstall: Boolean get() = release != null && kind in setOf(Kind.AVAILABLE, Kind.NEEDS_PERMISSION, Kind.INSTALL_FAILED)
    }

    private val manager = UpdateManager(application)
    private val _state = MutableStateFlow(State(if (BuildConfig.DEBUG) Kind.DEBUG_BUILD else Kind.IDLE))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            UpdateEvents.installFailure.collect { message ->
                _state.value = State(Kind.INSTALL_FAILED, _state.value.release, detail = message)
            }
        }
    }

    fun check() {
        if (_state.value.busy || _state.value.kind == Kind.DEBUG_BUILD) return
        _state.value = State(Kind.CHECKING)
        viewModelScope.launch {
            _state.value = try {
                when (val result = manager.check()) {
                    is UpdateManager.CheckResult.Available -> State(Kind.AVAILABLE, result.release)
                    is UpdateManager.CheckResult.UpToDate -> State(Kind.UP_TO_DATE, detail = result.version)
                }
            } catch (e: UpdateException) {
                if (e.code == UpdateException.Code.NOT_FOUND) State(Kind.NOT_FOUND) else State(Kind.CHECK_FAILED, error = e)
            } catch (e: Exception) {
                Logger.e("Update check failed", e)
                State(Kind.CHECK_FAILED, error = UpdateException(UpdateException.Code.NETWORK, e.message))
            }
        }
    }

    fun permissionRequested() {
        _state.value = State(Kind.NEEDS_PERMISSION, _state.value.release)
    }

    fun install() {
        val release = _state.value.release ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            try {
                manager.downloadAndInstall(release) { phase, percent ->
                    _state.value = State(
                        when (phase) {
                            UpdateManager.Phase.DOWNLOADING -> Kind.DOWNLOADING
                            UpdateManager.Phase.VERIFYING -> Kind.VERIFYING
                            UpdateManager.Phase.INSTALLING -> Kind.INSTALLING
                        },
                        release, percent
                    )
                }
            } catch (e: UpdateException) {
                _state.value = State(Kind.INSTALL_FAILED, release, error = e)
            } catch (e: Exception) {
                Logger.e("Update failed", e)
                _state.value = State(Kind.INSTALL_FAILED, release, detail = e.message.orEmpty())
            }
        }
    }
}
