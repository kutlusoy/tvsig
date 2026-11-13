package com.kutlusoy.tvsig.utils

import android.content.Context
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.update.UpdateException

object UpdateErrorText {
    /** The reason of a failed update in the language of the app. */
    fun of(context: Context, error: UpdateException?, detail: String = ""): String {
        val resource = when (error?.code) {
            UpdateException.Code.NETWORK -> R.string.update_error_network
            UpdateException.Code.NOT_FOUND -> R.string.update_none
            UpdateException.Code.NO_APK -> R.string.update_error_no_apk
            UpdateException.Code.NO_CHECKSUM -> R.string.update_error_no_checksum
            UpdateException.Code.CHECKSUM_MISMATCH -> R.string.update_error_checksum
            UpdateException.Code.INSECURE_URL -> R.string.update_error_insecure
            UpdateException.Code.TOO_LARGE -> R.string.update_error_too_large
            UpdateException.Code.STORAGE -> R.string.update_error_storage
            else -> null
        }
        return if (resource != null) context.getString(resource) else error?.message ?: detail
    }
}
