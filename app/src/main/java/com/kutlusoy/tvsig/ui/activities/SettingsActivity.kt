package com.kutlusoy.tvsig.ui.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.kutlusoy.tvsig.BuildConfig
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.databinding.ActivitySettingsBinding
import com.kutlusoy.tvsig.models.Constants
import com.kutlusoy.tvsig.models.SourceType
import com.kutlusoy.tvsig.ui.dialogs.ChangePinDialog
import com.kutlusoy.tvsig.ui.dialogs.FolderPickerDialog
import com.kutlusoy.tvsig.ui.dialogs.RemoteSetupDialog
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper
import com.kutlusoy.tvsig.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private var bindingFields = false

    private val delayOptions = listOf(3, 5, 8, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300)
    private val orientationOptions: List<Int> by lazy {
        val all = listOf(
            OrientationHelper.ORIENTATION_AUTO,
            OrientationHelper.ORIENTATION_LANDSCAPE,
            OrientationHelper.ORIENTATION_90,
            OrientationHelper.ORIENTATION_180,
            OrientationHelper.ORIENTATION_270
        )
        if (OrientationHelper.isTvDevice(this)) all.drop(1) else all
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyOrientation(PreferenceHelper.getInstance(this).getOrientation())

        setupViews()
        setupObservers()
    }

    private fun applyOrientation(orientation: Int) {
        OrientationHelper.setOrientation(this, orientation)
    }

    private fun setupViews() {
        val settings = viewModel.settings.value

        with(binding) {
            sourceRow.setOptions(listOf(getString(R.string.source_web), getString(R.string.source_drive)), settings.sourceType)
            sourceRow.onSelectionChanged = {
                viewModel.updateSettings(sourceType = it)
                bindSourceFields()
            }
            bindSourceFields()
            baseUrlInput.doAfterTextChanged {
                if (bindingFields) return@doAfterTextChanged
                if (isDrive()) viewModel.updateSettings(driveId = it.toString()) else viewModel.updateSettings(baseUrl = it.toString())
            }
            subfolderInput.doAfterTextChanged {
                if (bindingFields) return@doAfterTextChanged
                if (isDrive()) viewModel.updateSettings(drivePath = it.toString()) else viewModel.updateSettings(subfolder = it.toString())
            }

            delayRow.setOptions(delayOptions.map { getString(R.string.seconds_format, it) }, nearestDelayIndex(settings.slideDelay))
            delayRow.onSelectionChanged = { viewModel.updateSettings(slideDelay = delayOptions[it]) }

            transitionRow.setOptions(resources.getStringArray(R.array.transition_options).toList(), settings.transition)
            transitionRow.onSelectionChanged = { viewModel.updateSettings(transition = it) }

            val orientationLabels = orientationOptions.map { orientationLabel(it) }
            val orientationIndex = orientationOptions.indexOf(settings.orientation).takeIf { it >= 0 } ?: 0
            orientationRow.setOptions(orientationLabels, orientationIndex)
            orientationRow.onSelectionChanged = {
                val orientation = orientationOptions[it]
                viewModel.updateSettings(orientation = orientation)
                applyOrientation(orientation)
            }

            themeRow.setOptions(listOf(getString(R.string.theme_light), getString(R.string.theme_dark)), if (settings.isDarkMode) 1 else 0)
            themeRow.onSelectionChanged = { viewModel.updateSettings(isDarkMode = it == 1) }

            btnRemoteSetup.setOnClickListener { RemoteSetupDialog.show(supportFragmentManager) }
            btnPickFolder.setOnClickListener {
                val source = viewModel.settings.value.toSource()
                if (!source.isConfigured) {
                    Toast.makeText(this@SettingsActivity, R.string.test_no_url, Toast.LENGTH_SHORT).show()
                } else {
                    FolderPickerDialog.show(supportFragmentManager, source) { folder ->
                        subfolderInput.setText(folder)
                    }
                }
            }
            btnTestConnection.setOnClickListener { viewModel.testConnection() }
            btnChangePin.setOnClickListener {
                ChangePinDialog.show(supportFragmentManager, viewModel::verifyCurrentPin) { newPin ->
                    viewModel.updateSettings(pin = newPin)
                    Toast.makeText(this@SettingsActivity, getString(R.string.pin_changed), Toast.LENGTH_SHORT).show()
                }
            }
            btnClearCache.setOnClickListener {
                viewModel.requestCacheClear()
                finish()
            }
            btnCancel.setOnClickListener { finish() }
            btnSave.setOnClickListener { viewModel.saveSettings() }

            aboutText.text = listOf(
                getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                getString(R.string.about_author, getString(R.string.about_author_name)),
                getString(R.string.about_repo, getString(R.string.about_repo_url)),
                getString(R.string.about_license),
                getString(R.string.about_contact, getString(R.string.app_contact))
            ).joinToString("\n")
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.testStatus.collect { binding.testStatus.text = it }
        }
        lifecycleScope.launch {
            viewModel.saveResult.collect { saved ->
                if (saved == true) {
                    AppCompatDelegate.setDefaultNightMode(
                        if (viewModel.settings.value.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    finish()
                }
            }
        }
    }

    private fun isDrive() = SourceType.fromId(viewModel.settings.value.sourceType) == SourceType.DRIVE

    /** Shows the fields of the chosen source, web server and Google Drive keep their own values. */
    private fun bindSourceFields() {
        val settings = viewModel.settings.value
        val drive = isDrive()
        bindingFields = true
        with(binding) {
            baseUrlLayout.hint = getString(if (drive) R.string.settings_drive_id else R.string.settings_base_url)
            baseUrlLayout.helperText = getString(if (drive) R.string.settings_drive_id_helper else R.string.settings_base_url_helper)
            subfolderLayout.helperText = getString(R.string.settings_folder_helper)
            baseUrlInput.setText(if (drive) settings.driveId else settings.baseUrl)
            subfolderInput.setText(if (drive) settings.drivePath else settings.subfolder)
        }
        bindingFields = false
    }

    private fun nearestDelayIndex(seconds: Int): Int =
        delayOptions.indices.minByOrNull { abs(delayOptions[it] - seconds.coerceAtLeast(Constants.MIN_SLIDE_DELAY_SECONDS)) } ?: 0

    private fun orientationLabel(orientation: Int): String = getString(
        when (orientation) {
            OrientationHelper.ORIENTATION_AUTO -> R.string.orientation_auto
            OrientationHelper.ORIENTATION_90 -> R.string.orientation_90
            OrientationHelper.ORIENTATION_180 -> R.string.orientation_180
            OrientationHelper.ORIENTATION_270 -> R.string.orientation_270
            else -> R.string.orientation_landscape
        }
    )
}
