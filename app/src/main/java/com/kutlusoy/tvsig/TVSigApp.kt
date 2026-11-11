package com.kutlusoy.tvsig

import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import com.kutlusoy.tvsig.services.ForegroundService
import com.kutlusoy.tvsig.ui.activities.MainActivity
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.PreferenceHelper
import com.kutlusoy.tvsig.utils.RestartBackoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class TVSigApp : Application() {

    companion object {
        private lateinit var instance: TVSigApp
        fun getInstance(): TVSigApp = instance
        fun getAppContext(): Context = instance.applicationContext
        private const val MEMORY_CHECK_INTERVAL = 900000L
        private const val MEM_THRESHOLD_MB = 800

        @Volatile
        private var resumedActivities = 0

        val isUiVisible: Boolean get() = resumedActivities > 0
    }

    private val appScope = CoroutineScope(Dispatchers.Default + Job())
    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    private val exceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        try {
            Logger.e("Fatal Exception", throwable)
            val crashes = PreferenceHelper.getInstance(this).recordCrash(System.currentTimeMillis(), RestartBackoff.CRASH_WINDOW_MS)
            restartApplication(RestartBackoff.delayMs(crashes))
        } catch (e: Exception) {
            Logger.e("Error in crash handler", e)
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)

        val lifecycleHandler = AppLifecycleHandler(this)
        registerActivityLifecycleCallbacks(lifecycleHandler)
        registerComponentCallbacks(lifecycleHandler)
        initializeApp()
        ensureForegroundService()
        requestBatteryOptimizationExemption()
        requestAutoStartPermission()
        startMemoryMonitoring()
    }

    private fun requestAutoStartPermission() {
        val autoStartIntents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
        )

        for (intent in autoStartIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                return
            } catch (e: Exception) {
                continue
            }
        }
    }

    private fun restartApplication(delayMs: Long) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pendingIntent)
        } catch (e: Exception) {
            Logger.e("Error scheduling the restart", e)
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    /** Stops the service and the app for good, until the user starts it again. */
    fun requestExit() {
        PreferenceHelper.getInstance(this).setExitRequested(true)
        stopService(Intent(this, ForegroundService::class.java))
    }

    private fun startMemoryMonitoring() {
        appScope.launch {
            while (true) {
                val runtime = Runtime.getRuntime()
                val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                if (usedMemInMB > MEM_THRESHOLD_MB) {
                    Logger.w("Memory usage high: $usedMemInMB MB")
                    System.gc()
                }
                delay(MEMORY_CHECK_INTERVAL)
            }
        }
    }

    private fun ensureForegroundService() {
        if (PreferenceHelper.getInstance(this).isExitRequested()) return
        try {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Logger.e("Could not start foreground service", e)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val preferences = PreferenceHelper.getInstance(this)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName) && !preferences.wasBatteryPromptShown()) {
            preferences.setBatteryPromptShown()
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                Logger.e("Could not request battery optimization exemption", e)
            }
        }
    }

    private fun initializeApp() {
        val isDarkMode = PreferenceHelper.getInstance(this).isDarkMode()
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }

    class AppLifecycleHandler(private val app: TVSigApp) :
        Application.ActivityLifecycleCallbacks,
        ComponentCallbacks2 {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {
            resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
        }
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
            resumedActivities++
            app.ensureForegroundService()
        }

        override fun onConfigurationChanged(newConfig: Configuration) {}

        override fun onLowMemory() {
            System.gc()
        }

        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) System.gc()
        }
    }
}
