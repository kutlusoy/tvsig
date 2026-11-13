package com.kutlusoy.tvsig.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
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
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.kutlusoy.tvsig.utils.DisplayScale.wrap(newBase))
    }


    private lateinit var binding: ActivitySplashBinding
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

        binding.splashAnimation.start { startMainActivity() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        binding.splashAnimation.skip()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) binding.splashAnimation.skip()
        return true
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