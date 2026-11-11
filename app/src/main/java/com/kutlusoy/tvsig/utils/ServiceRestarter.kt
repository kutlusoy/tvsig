package com.kutlusoy.tvsig.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kutlusoy.tvsig.services.ForegroundService
import com.kutlusoy.tvsig.ui.activities.MainActivity

class ServiceRestarter : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Logger.d("System event received: ${intent.action}")
                startServices(context)
            }
        }
    }

    private fun startServices(context: Context) {
        try {
            PreferenceHelper.getInstance(context).setExitRequested(false)
            val serviceIntent = Intent(context, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(activityIntent)

            Logger.d("Service and activity started after system event")
        } catch (e: Exception) {
            Logger.e("Failed to start after system event", e)
        }
    }
}