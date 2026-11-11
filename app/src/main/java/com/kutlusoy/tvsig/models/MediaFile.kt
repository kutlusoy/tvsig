package com.kutlusoy.tvsig.models

data class MediaFile(
    val name: String,
    val url: String
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    fun isImage(): Boolean = extension in IMAGE_EXTENSIONS
    fun isVideo(): Boolean = extension in VIDEO_EXTENSIONS

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val VIDEO_EXTENSIONS = setOf("mp4", "mpeg", "mpg", "webm")

        fun isSupported(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in IMAGE_EXTENSIONS || ext in VIDEO_EXTENSIONS
        }
    }
}
