package com.kutlusoy.tvsig.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.TVSigApp
import com.kutlusoy.tvsig.databinding.ActivityMainBinding
import com.kutlusoy.tvsig.models.Constants
import com.kutlusoy.tvsig.models.CurrentMedia
import com.kutlusoy.tvsig.models.PresentationState
import com.kutlusoy.tvsig.models.TransitionType
import com.kutlusoy.tvsig.services.ForegroundService
import com.kutlusoy.tvsig.ui.dialogs.PinDialog
import com.kutlusoy.tvsig.ui.views.StageTransitioner
import com.kutlusoy.tvsig.utils.BackdropBlur
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper
import com.kutlusoy.tvsig.utils.gone
import com.kutlusoy.tvsig.utils.visible
import com.kutlusoy.tvsig.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.kutlusoy.tvsig.utils.DisplayScale.wrap(newBase))
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val preferenceHelper by lazy { PreferenceHelper.getInstance(this) }
    private val transitioner = StageTransitioner()
    private var player: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var imageTimerJob: Job? = null
    private var keepAliveJob: Job? = null
    private var lastSource: String? = null
    private var shownVideoPath: String? = null

    /** An image stage shows the picture fitted, with a blurred copy of it filling the rest of the screen. */
    private class ImageStage(val container: View, val fit: ImageView, val blur: ImageView) {
        fun clear() {
            fit.setImageBitmap(null)
            blur.setImageBitmap(null)
        }
    }

    private val stageA by lazy { ImageStage(binding.imageStageA, binding.imageFitA, binding.imageBlurA) }
    private val stageB by lazy { ImageStage(binding.imageStageB, binding.imageFitB, binding.imageBlurB) }
    private var shownImagePath: String? = null
    private var shownImageModified = 0L

    private companion object {
        const val WATCHDOG_INTERVAL_MS = 15_000L
        const val STALLED_CHECKS_LIMIT = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferenceHelper.setExitRequested(false)
        setupSystemUI()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initPlayer()
        setupOrientation()
        observeViewModel()
        binding.statusText.setOnClickListener { showPinDialog() }

        acquireWakeLock()
        ensureForegroundService()
        startKeepAliveTask()
        startPlaybackWatchdog()
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .build().apply {
                playWhenReady = true
                binding.videoView.player = this

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> viewModel.onPlaybackStarted()
                            Player.STATE_ENDED -> viewModel.moveToNextFile()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Logger.e("Player error", error)
                        viewModel.onPlaybackFailed()
                    }
                })
            }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.presentationState.collect { state ->
                when (state) {
                    is PresentationState.Loading, is PresentationState.Success -> binding.statusText.gone()
                    is PresentationState.Error -> {
                        stopPlayback()
                        binding.statusText.text = state.message
                        binding.statusText.visible()
                        openSettingsOnFirstStart()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentMedia.collect { media ->
                if (media == null) return@collect
                if (media.file.isVideo()) playVideo(media) else showImage(media)
            }
        }
    }

    /** Nothing is set up yet on the very first start, so lead the way into the settings. */
    private fun openSettingsOnFirstStart() {
        if (preferenceHelper.getSource().isConfigured || preferenceHelper.wasSetupShown()) return
        preferenceHelper.setSetupShown()
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showPinDialog() {
        PinDialog.show(supportFragmentManager) { enteredPin ->
            if (viewModel.verifyPin(enteredPin)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                Toast.makeText(this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun transitionType() = TransitionType.fromId(preferenceHelper.getTransition())

    private fun isSinglePlaylist() =
        (viewModel.presentationState.value as? PresentationState.Success)?.files?.size == 1

    private fun showImage(media: CurrentMedia) {
        imageTimerJob?.cancel()
        shownVideoPath = null
        imageTimerJob = lifecycleScope.launch {
            val displayMs = preferenceHelper.getSlideDelay()
                .coerceAtLeast(Constants.MIN_SLIDE_DELAY_SECONDS) * 1000L
            val modified = File(media.path).lastModified()
            val stageAspect = stageAspect()

            val unchanged = isSinglePlaylist() &&
                    (transitioner.currentView === stageA.container || transitioner.currentView === stageB.container) &&
                    media.path == shownImagePath && modified == shownImageModified
            if (!unchanged) {
                val images = withContext(Dispatchers.IO) {
                    decodeImage(media.path)?.let { it to BackdropBlur.create(it, stageAspect) }
                }
                if (images == null) {
                    Logger.e("Could not decode image: ${media.path}")
                    viewModel.onPlaybackFailed()
                    return@launch
                }
                val target = if (transitioner.currentView === stageA.container) stageB else stageA
                target.fit.setImageBitmap(images.first)
                target.blur.setImageBitmap(images.second)
                shownImagePath = media.path
                shownImageModified = modified
                transitioner.show(target.container, transitionType(), ::onViewHidden)
            }

            viewModel.onPlaybackStarted()
            delay(displayMs)
            viewModel.moveToNextFile()
        }
    }

    private fun playVideo(media: CurrentMedia) {
        imageTimerJob?.cancel()
        shownImagePath = null
        shownVideoPath = media.path
        binding.videoBlur.setImageBitmap(null)
        transitioner.show(binding.videoContainer, transitionType(), ::onViewHidden)
        val stageAspect = stageAspect()
        lifecycleScope.launch {
            val backdrop = withContext(Dispatchers.IO) { BackdropBlur.fromVideo(media.path, stageAspect) }
            if (backdrop != null && shownVideoPath == media.path) binding.videoBlur.setImageBitmap(backdrop)
        }

        player?.apply {
            repeatMode = if (isSinglePlaylist()) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(media.path))))
            prepare()
            play()
        }
    }

    /** Width to height of the playback area, in the rotated layout when the screen is turned. */
    private fun stageAspect(): Float =
        if (binding.root.height > 0) binding.root.width.toFloat() / binding.root.height else 0f

    private fun onViewHidden(view: View) {
        when {
            view === stageA.container -> stageA.clear()
            view === stageB.container -> stageB.clear()
            view === binding.videoContainer -> {
                player?.stop()
                binding.videoBlur.setImageBitmap(null)
            }
        }
    }

    private fun stopPlayback() {
        imageTimerJob?.cancel()
        transitioner.reset()
        player?.stop()
        shownImagePath = null
        shownVideoPath = null
        binding.videoContainer.gone()
        binding.videoBlur.setImageBitmap(null)
        listOf(stageA, stageB).forEach {
            it.container.gone()
            it.clear()
        }
    }

    /** Decodes at most twice the screen size and applies the EXIF orientation of photos. */
    private fun decodeImage(path: String): Bitmap? {
        val metrics = resources.displayMetrics
        val maxSide = maxOf(metrics.widthPixels, metrics.heightPixels) * 2
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val matrix = exifMatrix(path) ?: return bitmap
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            bitmap
        }
    }

    private fun exifMatrix(path: String): Matrix? {
        val orientation = try {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            return null
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return null
        }
        return matrix
    }

    private fun setupSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Skips a video that stands still for a minute, for example after a decoder or storage problem. */
    private fun startPlaybackWatchdog() {
        lifecycleScope.launch {
            var lastPosition = -1L
            var stalledChecks = 0
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                val current = player
                val watching = current != null && transitioner.currentView === binding.videoContainer &&
                        current.playWhenReady && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (!watching || current == null) {
                    stalledChecks = 0
                    lastPosition = -1L
                    continue
                }
                val position = current.currentPosition
                stalledChecks = if (position == lastPosition && current.playbackState != Player.STATE_ENDED) stalledChecks + 1 else 0
                lastPosition = position
                if (stalledChecks >= STALLED_CHECKS_LIMIT) {
                    Logger.w("Video stalled, skipping")
                    stalledChecks = 0
                    viewModel.onPlaybackFailed()
                }
            }
        }
    }

    private fun startKeepAliveTask() {
        keepAliveJob = lifecycleScope.launch {
            while (true) {
                delay(120_000)
                try {
                    ensureForegroundService()
                    if (wakeLock?.isHeld != true) acquireWakeLock()
                } catch (e: Exception) {
                    Logger.e("Keep-alive task error", e)
                }
            }
        }
    }

    private fun ensureForegroundService() {
        val serviceIntent = Intent(this, ForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock?.takeIf { it.isHeld }?.release()
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TVSig:WakeLock")
            wakeLock?.acquire(24 * 60 * 60 * 1000L)
        } catch (e: Exception) {
            Logger.e("Error acquiring wakelock", e)
        }
    }

    private fun setupOrientation() {
        OrientationHelper.setOrientation(this, preferenceHelper.getOrientation())
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        acquireWakeLock()
        setupOrientation()
        ensureForegroundService()
        player?.play()

        val before = preferenceHelper.getSource().toString()
        if (before != lastSource) {
            val firstResume = lastSource == null
            lastSource = before
            if (!firstResume) viewModel.loadPlaylist()
        }
        viewModel.clearCacheIfRequested()
    }

    override fun onPause() {
        player?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        keepAliveJob?.cancel()
        imageTimerJob?.cancel()
        player?.release()
        player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    fun exit() {
        (application as TVSigApp).requestExit()
        wakeLock?.takeIf { it.isHeld }?.release()
        finishAndRemoveTask()
        exitProcess(0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                showPinDialog()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { viewModel.moveToNextFile(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { viewModel.moveToPreviousFile(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
