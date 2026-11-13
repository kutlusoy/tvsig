package com.kutlusoy.tvsig.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.kutlusoy.tvsig.utils.Logger

/** Receives the status of the installation. On success the app is replaced and restarts by itself. */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                try {
                    confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                } catch (e: Exception) {
                    Logger.e("Could not open the install confirmation", e)
                    UpdateEvents.installFailure.tryEmit(e.message ?: "confirmation")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Logger.d("Update installed")
            else -> UpdateEvents.installFailure.tryEmit(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
            )
        }
    }
}
