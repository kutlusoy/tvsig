package com.kutlusoy.tvsig.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.kutlusoy.tvsig.databinding.ActivityMainBinding
import com.kutlusoy.tvsig.models.CurrentMedia
import com.kutlusoy.tvsig.models.PresentationState
import com.kutlusoy.tvsig.services.ForegroundService
import com.kutlusoy.tvsig.ui.dialogs.PinDialog
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
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val preferenceHelper by lazy { PreferenceHelper.getInstance(this) }
    private var player: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var imageTimerJob: Job? = null
    private var keepAliveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setupSystemUI()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initPlayer()
        setupOrientation()
        observeViewModel()

        acquireWakeLock()
        ensureForegroundService()
        startKeepAliveTask()
    }

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
                        if (playbackState == Player.STATE_ENDED) viewModel.moveToNextFile()
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
                    is PresentationState.Loading -> binding.statusText.gone()
                    is PresentationState.Success -> binding.statusText.gone()
                    is PresentationState.Error -> {
                        stopPlayback()
                        binding.statusText.text = state.message
                        binding.statusText.visible()
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

    private fun showImage(media: CurrentMedia) {
        imageTimerJob?.cancel()
        player?.stop()
        binding.videoContainer.gone()

        imageTimerJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeScaled(media.path) }
            if (bitmap == null) {
                Logger.e("Could not decode image: ${media.path}")
                viewModel.onPlaybackFailed()
                return@launch
            }

            val imageView = binding.imageView
            imageView.visible()
            imageView.animate().cancel()
            if (imageView.drawable == null) {
                imageView.setImageBitmap(bitmap)
                imageView.alpha = 1f
            } else {
                imageView.animate().alpha(0f).setDuration(300).withEndAction {
                    imageView.setImageBitmap(bitmap)
                    imageView.animate().alpha(1f).setDuration(500).start()
                }.start()
            }

            val displayMs = preferenceHelper.getSlideDelay().coerceAtLeast(
                com.kutlusoy.tvsig.models.Constants.MIN_SLIDE_DELAY_SECONDS
            ) * 1000L
            delay(displayMs)
            viewModel.moveToNextFile()
        }
    }

    private fun playVideo(media: CurrentMedia) {
        imageTimerJob?.cancel()
        binding.imageView.gone()
        binding.videoContainer.visible()

        val single = (viewModel.presentationState.value as? PresentationState.Success)?.files?.size == 1
        player?.apply {
            repeatMode = if (single) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(media.path))))
            prepare()
            play()
        }
    }

    private fun stopPlayback() {
        imageTimerJob?.cancel()
        player?.stop()
        binding.videoContainer.gone()
        binding.imageView.gone()
        binding.imageView.setImageBitmap(null)
    }

    private fun decodeScaled(path: String): Bitmap? {
        val metrics = resources.displayMetrics
        val maxSide = maxOf(metrics.widthPixels, metrics.heightPixels) * 2
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
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
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TvSig:WakeLock")
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

        val before = preferenceHelper.getBaseUrl() + "|" + preferenceHelper.getSubfolder()
        if (before != lastSource) {
            val firstResume = lastSource == null
            lastSource = before
            if (!firstResume) viewModel.loadPlaylist()
        }
        viewModel.clearCacheIfRequested()
    }

    private var lastSource: String? = null

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
        wakeLock?.release()
        stopService(Intent(this, ForegroundService::class.java))
        finish()
        exitProcess(0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                PinDialog.show(supportFragmentManager) { enteredPin ->
                    if (viewModel.verifyPin(enteredPin)) {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { viewModel.moveToNextFile(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { viewModel.moveToPreviousFile(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
