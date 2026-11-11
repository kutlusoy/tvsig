package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.models.MediaFile
import com.kutlusoy.tvsig.repositories.OfflinePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePolicyTest {

    private val a = MediaFile("a.jpg", "http://h/a.jpg")
    private val b = MediaFile("b.jpg", "http://h/b.jpg")
    private val c = MediaFile("c.mp4", "http://h/c.mp4")

    @Test
    fun onlyStoredFilesArePlayedWhenSomeAreMissing() {
        val stored = setOf(a, c)
        assertEquals(listOf(a, c), OfflinePolicy.playable(listOf(a, b, c)) { it in stored })
    }

    @Test
    fun theListStaysAsItWasWhenNothingIsStored() {
        assertEquals(listOf(a, b), OfflinePolicy.playable(listOf(a, b)) { false })
    }

    @Test
    fun theFastStartNeedsAtLeastOneStoredFile() {
        assertTrue(OfflinePolicy.storedOnly(listOf(a, b)) { false }.isEmpty())
        assertEquals(listOf(b), OfflinePolicy.storedOnly(listOf(a, b)) { it == b })
    }

    @Test
    fun filesAreKeptDuringTheGracePeriod() {
        val now = 10L * OfflinePolicy.UNUSED_GRACE_MS
        assertFalse(OfflinePolicy.shouldDelete(null, now))
        assertFalse(OfflinePolicy.shouldDelete(now - 1000, now))
        assertFalse(OfflinePolicy.shouldDelete(now - OfflinePolicy.UNUSED_GRACE_MS + 1, now))
        assertTrue(OfflinePolicy.shouldDelete(now - OfflinePolicy.UNUSED_GRACE_MS, now))
    }

    private val now = 100L * OfflinePolicy.WEAK_RECHECK_MS
    private fun info(length: Long, marker: String = "etag1|date1|||", strong: Boolean = true) =
        OfflinePolicy.RemoteInfo(length, marker, strong)
    private fun meta(size: Long = 1000, marker: String = "etag1|date1|||", at: Long = now) =
        OfflinePolicy.FileMeta(marker, size, at)

    @Test
    fun aFileThatIsUnchangedIsKept() {
        assertFalse(OfflinePolicy.needsDownload(meta(), 1000, info(1000), now))
    }

    @Test
    fun aMissingRecordOrFileIsFetched() {
        assertTrue(OfflinePolicy.needsDownload(null, 1000, info(1000), now))
        assertTrue(OfflinePolicy.needsDownload(meta(), 0, info(1000), now))
    }

    @Test
    fun aDamagedFileOnTheDeviceIsFetchedAgain() {
        assertTrue(OfflinePolicy.needsDownload(meta(size = 1000), 640, info(1000), now))
    }

    @Test
    fun aDifferentSizeOnTheServerIsDetected() {
        assertTrue(OfflinePolicy.needsDownload(meta(size = 1000), 1000, info(1500), now))
    }

    @Test
    fun aChangedMarkerIsDetectedEvenWithTheSameSize() {
        assertTrue(OfflinePolicy.needsDownload(meta(), 1000, info(1000, marker = "etag2|date2|||"), now))
    }

    @Test
    fun nothingChangesWhenTheServerCannotBeAsked() {
        assertFalse(OfflinePolicy.needsDownload(meta(), 1000, null, now))
    }

    @Test
    fun smallFilesOfServersWithoutMarkersAreCheckedAgainDaily() {
        val weak = info(1000, marker = "|||", strong = false)
        assertFalse(OfflinePolicy.needsDownload(meta(marker = "|||", at = now - 1000), 1000, weak, now))
        assertTrue(OfflinePolicy.needsDownload(meta(marker = "|||", at = now - OfflinePolicy.WEAK_RECHECK_MS), 1000, weak, now))
    }

    @Test
    fun bigFilesWithoutMarkersAreNotFetchedAgainBlindly() {
        val big = OfflinePolicy.WEAK_RECHECK_MAX_BYTES + 1
        val weak = info(big, marker = "|||", strong = false)
        assertFalse(OfflinePolicy.needsDownload(meta(size = big, marker = "|||", at = 0), big, weak, now))
    }

    @Test
    fun readsTheMarkersFromTheHeaders() {
        val headers = mapOf("ETag" to "\"abc\"", "Last-Modified" to "Tue, 10 Nov 2026", "X-Goog-Hash" to "md5=xyz")
        val parsed = OfflinePolicy.RemoteInfo.fromHeaders(42) { headers[it] }
        assertTrue(parsed.strong)
        assertEquals(42, parsed.length)
        assertTrue(parsed.marker.contains("\"abc\"") && parsed.marker.contains("md5=xyz"))
        assertFalse(OfflinePolicy.RemoteInfo.fromHeaders(42) { null }.strong)
    }
}
