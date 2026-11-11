package com.kutlusoy.tvsig.utils

import android.util.Log
import com.kutlusoy.tvsig.BuildConfig

object Logger {
    private const val TAG = "TVSig"
    private const val MAX_LOG_SIZE = 2000
    private var logCount = 0

    fun d(message: String) {
        if (BuildConfig.ENABLE_LOGGING && logCount < MAX_LOG_SIZE) {
            Log.d(TAG, "[DEBUG] $message")
            logCount++
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.ENABLE_LOGGING && logCount < MAX_LOG_SIZE) {
            Log.e(TAG, "[ERROR] $message", throwable)
            logCount++
        }
    }

    fun i(message: String) {
        if (BuildConfig.ENABLE_LOGGING && logCount < MAX_LOG_SIZE) {
            Log.i(TAG, "[INFO] $message")
            logCount++
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.ENABLE_LOGGING && logCount < MAX_LOG_SIZE) {
            Log.w(TAG, "[WARN] $message", throwable)
            logCount++
        }
    }

    fun error(message: String, exception: Exception) {
        if (BuildConfig.ENABLE_LOGGING && logCount < MAX_LOG_SIZE) {
            Log.e(TAG, "[ERROR] $message", exception)
            logCount++
        }
    }

    fun tv(message: String) {
        if (BuildConfig.ENABLE_LOGGING && logCount < MAX_LOG_SIZE) {
            Log.d(TAG, "[TV] $message")
            logCount++
        }
    }



    fun resetLogCount() {
        logCount = 0
    }
}