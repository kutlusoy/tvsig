package com.kutlusoy.tvsig.utils

import android.content.Context
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.models.ErrorMessages

object ErrorText {
    /** The message of an error in the language of the app. Unknown messages are shown as they are. */
    fun of(context: Context, error: Throwable?): String {
        val resource = when (ErrorMessages.codeOf(error?.message)) {
            "offline" -> R.string.error_offline
            "listing" -> R.string.error_listing
            "no_media" -> R.string.error_no_media
            "blocked" -> R.string.error_blocked
            "drive_link" -> R.string.error_drive_link
            "drive_folder" -> R.string.error_drive_folder
            else -> null
        }
        return if (resource != null) context.getString(resource) else error?.message ?: error?.javaClass?.simpleName.orEmpty()
    }
}
