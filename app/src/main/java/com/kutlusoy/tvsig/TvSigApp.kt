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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class TvSigApp : Application() {

    companion object {
        private lateinit var instance: TvSigApp
        fun getInstance(): TvSigApp = instance
        fun getAppContext(): Context = instance.applicationContext
        private const val RESTART_DELAY = 5000L
        private const val MEMORY_CHECK_INTERVAL = 900000L
        private const val MEM_THRESHOLD_MB = 800
    }

    private val appScope = CoroutineScope(Dispatchers.Default + Job())
    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    private val exceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
        try {
            Logger.e("Fatal Exception", throwable)
            restartApplication()
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

    private fun restartApplication() {
        try {
            ensureForegroundService()

            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("restart", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + RESTART_DELAY,
                pendingIntent
            )

            android.os.Handler(mainLooper).postDelayed({
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }, 1000)
        } catch (e: Exception) {
            Logger.e("Error restarting application", e)
        }
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
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
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

    class AppLifecycleHandler(private val app: TvSigApp) :
        Application.ActivityLifecycleCallbacks,
        ComponentCallbacks2 {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
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
