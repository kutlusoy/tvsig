package com.kutlusoy.tvsig.ui.activities

import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.databinding.ActivitySettingsBinding
import com.kutlusoy.tvsig.ui.dialogs.ChangePinDialog
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private var isUpdatingFromViewModel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupObservers()

        setupOrientation()
    }

    private fun setupOrientation() {
        val preferenceHelper = com.kutlusoy.tvsig.utils.PreferenceHelper.getInstance(this)
        val orientation = preferenceHelper.getOrientation()
        OrientationHelper.setOrientation(this, orientation)
        Logger.d("SettingsActivity: Applied orientation setting: $orientation (without view rotation)")
    }

    private fun setupViews() {
        with(binding) {
            baseUrlInput.doAfterTextChanged { text ->
                viewModel.updateSettings(baseUrl = text.toString())
            }

            subfolderInput.doAfterTextChanged { text ->
                viewModel.updateSettings(subfolder = text.toString())
            }

            delayInput.doAfterTextChanged { text ->
                text?.toString()?.toIntOrNull()?.let { delay ->
                    viewModel.updateSettings(slideDelay = delay)
                }
            }

            orientationGroup.setOnCheckedChangeListener { _, checkedId ->
                if (isUpdatingFromViewModel) {
                    return@setOnCheckedChangeListener
                }

                val orientation = when (checkedId) {
                    R.id.orientationAuto -> OrientationHelper.ORIENTATION_AUTO
                    R.id.orientationLandscape -> OrientationHelper.ORIENTATION_LANDSCAPE
                    R.id.orientation90 -> OrientationHelper.ORIENTATION_90
                    R.id.orientation180 -> OrientationHelper.ORIENTATION_180
                    R.id.orientation270 -> OrientationHelper.ORIENTATION_270
                    else -> OrientationHelper.ORIENTATION_LANDSCAPE
                }
                viewModel.updateSettings(orientation = orientation)

                OrientationHelper.setOrientation(this@SettingsActivity, orientation)
            }

            themeGroup.setOnCheckedChangeListener { _, checkedId ->
                if (isUpdatingFromViewModel) {
                    return@setOnCheckedChangeListener
                }

                val isDarkMode = checkedId == R.id.themeDark
                viewModel.updateSettings(isDarkMode = isDarkMode)
            }

            btnChangePin.setOnClickListener {
                showChangePinDialog()
            }

            btnClearCache.setOnClickListener {
                viewModel.requestCacheClear()
                finish()
            }

            btnCancel.setOnClickListener {
                finish()
            }

            btnSave.setOnClickListener {
                viewModel.saveSettings()
            }

            developerInfo.text = getString(R.string.app_creator_footer,
                getString(R.string.app_creator),
                getString(R.string.app_contact))
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                try {
                    with(binding) {
                        if (baseUrlInput.text.toString() != settings.baseUrl) {
                            baseUrlInput.setText(settings.baseUrl)
                        }

                        if (subfolderInput.text.toString() != settings.subfolder) {
                            subfolderInput.setText(settings.subfolder)
                        }

                        if (delayInput.text.toString() != settings.slideDelay.toString()) {
                            delayInput.setText(settings.slideDelay.toString())
                        }

                        val orientationButton = when (settings.orientation) {
                            OrientationHelper.ORIENTATION_AUTO -> R.id.orientationAuto
                            OrientationHelper.ORIENTATION_LANDSCAPE -> R.id.orientationLandscape
                            OrientationHelper.ORIENTATION_90 -> R.id.orientation90
                            OrientationHelper.ORIENTATION_180 -> R.id.orientation180
                            OrientationHelper.ORIENTATION_270 -> R.id.orientation270
                            else -> R.id.orientationLandscape
                        }
                        isUpdatingFromViewModel = true
                        orientationGroup.check(orientationButton)
                        isUpdatingFromViewModel = false

                        isUpdatingFromViewModel = true
                        themeGroup.check(if (settings.isDarkMode) R.id.themeDark else R.id.themeLight)
                        isUpdatingFromViewModel = false

                        if (OrientationHelper.isTvDevice(this@SettingsActivity)) {
                            orientationAuto.isEnabled = false
                            orientationGroup.findViewById<RadioButton>(R.id.orientationAuto)?.isEnabled = false
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("Error updating settings UI", e)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.saveResult.collect { saved ->
                if (saved == true) {
                    finish()
                }
            }
        }
    }

    private fun showChangePinDialog() {
        ChangePinDialog.show(supportFragmentManager) { newPin ->
            viewModel.updateSettings(pin = newPin)
            Toast.makeText(this, getString(R.string.pin_changed), Toast.LENGTH_SHORT).show()
        }
    }
}