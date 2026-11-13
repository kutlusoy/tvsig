package com.kutlusoy.tvsig.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.databinding.ActivitySplashBinding
import com.kutlusoy.tvsig.ui.dialogs.PinDialog
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper

class SplashActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.kutlusoy.tvsig.utils.DisplayScale.wrap(newBase))
    }


    private lateinit var binding: ActivitySplashBinding
    private var introFinished = false
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
            override fun handleOnBackPressed() {
                if (introFinished) showPinDialog() else binding.splashAnimation.skip()
            }
        })

        binding.setupHint.setOnClickListener { showPinDialog() }
        binding.splashAnimation.start { onIntroFinished() }
    }

    override fun onResume() {
        super.onResume()
        if (introFinished && preferenceHelper.getSource().isConfigured) startMainActivity()
    }

    /** Without a source the splash screen stays and leads into the setup, once it is set up the player starts. */
    private fun onIntroFinished() {
        introFinished = true
        if (preferenceHelper.getSource().isConfigured) {
            startMainActivity()
            return
        }
        binding.splashAnimation.hold()
        binding.setupHint.visibility = View.VISIBLE
        binding.setupHint.requestFocus()
        if (!preferenceHelper.wasSetupShown()) {
            preferenceHelper.setSetupShown()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showPinDialog() {
        PinDialog.show(supportFragmentManager) { enteredPin ->
            if (enteredPin == preferenceHelper.getPin()) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                Toast.makeText(this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (introFinished) {
            if (keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
            showPinDialog()
            return true
        }
        binding.splashAnimation.skip()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!introFinished && event.action == MotionEvent.ACTION_UP) binding.splashAnimation.skip()
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