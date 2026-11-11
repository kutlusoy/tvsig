package com.kutlusoy.tvsig.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.TVSigApp
import com.kutlusoy.tvsig.ui.activities.MainActivity
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps the process alive and brings the player screen back when it is gone. */
class ForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "tvsig_channel"
        private const val WATCHDOG_INTERVAL_MS = 15_000L
        private const val UI_GONE_GRACE_MS = 20_000L
        private const val RELAUNCH_COOLDOWN_MS = 30_000L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var watchdogJob: Job? = null
    private var uiGoneSince = 0L
    private var lastRelaunch = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (PreferenceHelper.getInstance(this).isExitRequested()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (watchdogJob?.isActive != true) startWatchdog()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!PreferenceHelper.getInstance(this).isExitRequested()) relaunchUi()
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        serviceScope.cancel()
        if (!PreferenceHelper.getInstance(this).isExitRequested()) scheduleServiceRestart()
        super.onDestroy()
    }

    private fun startWatchdog() {
        watchdogJob = serviceScope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                try {
                    checkUi()
                } catch (e: Exception) {
                    Logger.e("Watchdog error", e)
                }
            }
        }
    }

    private fun checkUi() {
        val screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (!screenOn || TVSigApp.isUiVisible) {
            uiGoneSince = 0L
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (uiGoneSince == 0L) uiGoneSince = now
        if (now - uiGoneSince >= UI_GONE_GRACE_MS && now - lastRelaunch >= RELAUNCH_COOLDOWN_MS) {
            lastRelaunch = now
            Logger.w("Player screen is gone, relaunching")
            relaunchUi()
        }
    }

    private fun relaunchUi() {
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            })
        } catch (e: Exception) {
            Logger.e("Could not relaunch the player screen", e)
        }
    }

    /** The system restarts a sticky service on its own, this covers devices that do not. */
    private fun scheduleServiceRestart() {
        try {
            val intent = Intent(applicationContext, ForegroundService::class.java)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(applicationContext, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            } else {
                PendingIntent.getService(applicationContext, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            }
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 2_000L,
                pendingIntent
            )
        } catch (e: Exception) {
            Logger.e("Could not schedule the service restart", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.foreground_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.foreground_service_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.foreground_service_notification_text))
            .setSmallIcon(R.drawable.ic_stat_tvsig)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
