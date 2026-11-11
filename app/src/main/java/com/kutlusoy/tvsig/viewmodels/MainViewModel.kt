package com.kutlusoy.tvsig.viewmodels

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.models.Constants
import com.kutlusoy.tvsig.models.CurrentMedia
import com.kutlusoy.tvsig.models.MediaFile
import com.kutlusoy.tvsig.models.PresentationState
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.repositories.MediaRepository
import com.kutlusoy.tvsig.utils.CacheManager
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.NetworkHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository.getInstance(application)
    private val preferenceHelper = PreferenceHelper.getInstance(application)

    private val _presentationState = MutableStateFlow<PresentationState>(PresentationState.Loading)
    val presentationState: StateFlow<PresentationState> = _presentationState.asStateFlow()

    private val _currentMedia = MutableStateFlow<CurrentMedia?>(null)
    val currentMedia: StateFlow<CurrentMedia?> = _currentMedia.asStateFlow()

    private var showJob: Job? = null
    private var retryJob: Job? = null
    private var tokenCounter = 0L
    private var consecutiveFailures = 0
    private var lastShownAt = SystemClock.elapsedRealtime()

    init {
        loadPlaylist()
        startRefreshLoop()
        observeNetwork()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            NetworkHelper.observeNetworkState(getApplication()).drop(1).collect { online ->
                if (!online) return@collect
                if (_presentationState.value is PresentationState.Error) loadPlaylist() else refreshPlaylist()
            }
        }
    }

    private fun startRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                delay(Constants.REFRESH_INTERVAL_MS)
                refreshPlaylist()
                reloadIfStalled()
            }
        }
    }

    fun loadPlaylist() {
        retryJob?.cancel()
        consecutiveFailures = 0
        viewModelScope.launch {
            _presentationState.value = PresentationState.Loading
            _currentMedia.value = null

            val baseUrl = preferenceHelper.getBaseUrl()
            if (baseUrl.isBlank()) {
                _presentationState.value = PresentationState.Error(string(R.string.error_no_base_url))
                return@launch
            }

            when (val result = repository.loadPlaylist(baseUrl, preferenceHelper.getSubfolder())) {
                is Result.Success -> {
                    consecutiveFailures = 0
                    _presentationState.value = PresentationState.Success(result.data, 0)
                    showCurrent()
                }
                is Result.Error -> {
                    _presentationState.value = PresentationState.Error(
                        string(R.string.error_load_failed, result.exception.message ?: "")
                    )
                    scheduleRetry()
                }
                else -> {}
            }
        }
    }

    private suspend fun refreshPlaylist() {
        val state = _presentationState.value as? PresentationState.Success ?: return
        val baseUrl = preferenceHelper.getBaseUrl()
        if (baseUrl.isBlank()) return

        val result = repository.loadPlaylist(baseUrl, preferenceHelper.getSubfolder())
        if (result is Result.Success && result.data.map { it.url } != state.files.map { it.url }) {
            Logger.d("Playlist changed, reloading")
            _presentationState.value = PresentationState.Success(result.data, 0)
            showCurrent()
        }
    }

    /** Last resort: when nothing was shown for hours, start over. */
    private fun reloadIfStalled() {
        val state = _presentationState.value as? PresentationState.Success ?: return
        val loopingVideo = state.files.size == 1 && state.files[0].isVideo()
        if (!loopingVideo && SystemClock.elapsedRealtime() - lastShownAt > Constants.STALL_TIMEOUT_MS) {
            Logger.w("Playback stalled, reloading the playlist")
            loadPlaylist()
        }
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(Constants.RETRY_INTERVAL_MS)
            loadPlaylist()
        }
    }

    private fun showCurrent() {
        val state = _presentationState.value as? PresentationState.Success ?: return
        val file = state.files[state.currentIndex]

        showJob?.cancel()
        showJob = viewModelScope.launch {
            when (val result = repository.ensureLocal(file)) {
                is Result.Success -> {
                    lastShownAt = SystemClock.elapsedRealtime()
                    _currentMedia.value = CurrentMedia(file, result.data.absolutePath, ++tokenCounter)
                    prefetchNext(state)
                }
                is Result.Error -> {
                    Logger.e("Skipping ${file.name}: ${result.exception.message}")
                    onFileFailed(state.files)
                }
                else -> {}
            }
        }
    }

    /** Called when an image is on screen or a video started, resets the failure counter. */
    fun onPlaybackStarted() {
        consecutiveFailures = 0
    }

    private fun prefetchNext(state: PresentationState.Success) {
        val next = state.files[(state.currentIndex + 1) % state.files.size]
        if (next.url == state.files[state.currentIndex].url) return
        viewModelScope.launch { repository.ensureLocal(next) }
    }

    fun onPlaybackFailed() {
        val state = _presentationState.value as? PresentationState.Success ?: return
        onFileFailed(state.files)
    }

    private fun onFileFailed(files: List<MediaFile>) {
        consecutiveFailures++
        if (consecutiveFailures >= files.size) {
            consecutiveFailures = 0
            _currentMedia.value = null
            _presentationState.value = PresentationState.Error(string(R.string.error_no_playable_media))
            scheduleRetry()
        } else {
            moveToNextFile()
        }
    }

    fun moveToNextFile() = moveBy(1)

    fun moveToPreviousFile() = moveBy(-1)

    private fun moveBy(step: Int) {
        val state = _presentationState.value as? PresentationState.Success ?: return
        val size = state.files.size
        if (size == 0) return
        _presentationState.value = state.copy(currentIndex = (state.currentIndex + step + size) % size)
        showCurrent()
    }

    fun verifyPin(enteredPin: String) = enteredPin == preferenceHelper.getPin()

    fun clearCacheIfRequested() {
        if (!preferenceHelper.isCacheClearRequested()) return
        preferenceHelper.setCacheClearRequest(false)
        viewModelScope.launch {
            CacheManager.getInstance(getApplication()).clearCache()
            loadPlaylist()
        }
    }

    private fun string(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)
}
