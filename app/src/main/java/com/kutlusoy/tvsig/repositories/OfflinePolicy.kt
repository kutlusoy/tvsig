package com.kutlusoy.tvsig.repositories

import com.kutlusoy.tvsig.models.MediaFile

/** The rules for what is played and what is kept when the source cannot be reached. */
object OfflinePolicy {
    /** Files that were not used for this long are removed from the device. */
    const val UNUSED_GRACE_MS = 3L * 24 * 60 * 60 * 1000

    /** Only what is stored on the device can be played without the source. Nothing stored means the list as it was. */
    fun playable(cached: List<MediaFile>, isStored: (MediaFile) -> Boolean): List<MediaFile> =
        cached.filter(isStored).ifEmpty { cached }

    /** Same as [playable], but an empty result when nothing is stored, so the normal start takes over. */
    fun storedOnly(cached: List<MediaFile>, isStored: (MediaFile) -> Boolean): List<MediaFile> =
        cached.filter(isStored)

    /** A file that dropped out of the list is kept for a while, one incomplete answer must not empty the device. */
    fun shouldDelete(unusedSince: Long?, now: Long): Boolean =
        unusedSince != null && now - unusedSince >= UNUSED_GRACE_MS

    /** What the server says about a file: its size, and the markers that change when the content changes. */
    class RemoteInfo(val length: Long, val marker: String, val strong: Boolean) {
        companion object {
            private val MARKER_HEADERS = listOf("ETag", "Last-Modified", "Content-MD5", "X-Goog-Hash", "Digest")

            fun fromHeaders(length: Long, header: (String) -> String?): RemoteInfo {
                val values = MARKER_HEADERS.map { header(it)?.takeIf { value -> value.isNotBlank() } }
                return RemoteInfo(length, values.joinToString("|") { it ?: "" }, values.any { it != null })
            }
        }
    }

    /** What was recorded when the file was downloaded. */
    data class FileMeta(val marker: String = "", val size: Long = 0, val downloadedAt: Long = 0)

    /** Files of servers without a change marker are fetched again after this time, when they are small. */
    const val WEAK_RECHECK_MS = 24L * 60 * 60 * 1000
    const val WEAK_RECHECK_MAX_BYTES = 20L * 1024 * 1024

    /**
     * Decides whether a stored file has to be downloaded again. It compares the size and the change markers
     * of the server with what was recorded, and the file on the device with the size that was recorded.
     */
    fun needsDownload(meta: FileMeta?, localSize: Long, remote: RemoteInfo?, now: Long): Boolean {
        if (meta == null || localSize <= 0) return true
        if (localSize != meta.size) return true
        if (remote == null) return false
        if (remote.length >= 0 && remote.length != meta.size) return true
        if (remote.strong && meta.marker.isNotEmpty() && remote.marker != meta.marker) return true
        if (!remote.strong && meta.size <= WEAK_RECHECK_MAX_BYTES && now - meta.downloadedAt >= WEAK_RECHECK_MS) return true
        return false
    }
}
