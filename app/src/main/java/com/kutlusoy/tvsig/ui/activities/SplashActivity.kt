package com.kutlusoy.tvsig.ui.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kutlusoy.tvsig.databinding.ActivitySplashBinding
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDelay = 3000L
    private val preferenceHelper by lazy { PreferenceHelper.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        preferenceHelper.setExitRequested(false)

        setupSystemUI()

        setupOrientation()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupOrientation()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        Handler(Looper.getMainLooper()).postDelayed({
            startMainActivity()
        }, splashDelay)
    }

    private fun setupSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        hideSystemUI()
    }

    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupOrientation() {
        val orientation = preferenceHelper.getOrientation()
        OrientationHelper.setOrientation(this, orientation)
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

}