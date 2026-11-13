package com.kutlusoy.tvsig.models

/** The technical messages of the media source and the codes that stand for them. */
object ErrorMessages {
    const val OFFLINE = "Offline"
    const val LISTING = "Folder listing not available"
    const val NO_MEDIA = "No media found"
    const val BLOCKED = "The server sent a web page instead of the file (access or download limit)"
    const val DRIVE_LINK = "No Google Drive folder ID found in the link"
    const val DRIVE_FOLDER = "Drive folder not found or not shared with anyone who has the link"

    /** A short code for a known message, so the screen and the phone page can show it in their language. */
    fun codeOf(message: String?): String? = when (message) {
        OFFLINE -> "offline"
        LISTING -> "listing"
        NO_MEDIA -> "no_media"
        BLOCKED -> "blocked"
        DRIVE_LINK -> "drive_link"
        DRIVE_FOLDER -> "drive_folder"
        else -> null
    }
}
