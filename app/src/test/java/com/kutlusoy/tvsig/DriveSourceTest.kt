package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.repositories.DriveSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSourceTest {

    private val folderId = "1AbCdEfGhIjKlMnOpQrStUvWxYz012345"

    @Test
    fun readsTheFolderIdFromAllLinkForms() {
        assertEquals(folderId, DriveSource.folderId("https://drive.google.com/drive/folders/$folderId?usp=sharing"))
        assertEquals(folderId, DriveSource.folderId("https://drive.google.com/drive/u/0/folders/$folderId"))
        assertEquals(folderId, DriveSource.folderId("https://drive.google.com/open?id=$folderId"))
        assertEquals(folderId, DriveSource.folderId("https://drive.google.com/embeddedfolderview?id=$folderId#list"))
        assertEquals(folderId, DriveSource.folderId(folderId))
        assertNull(DriveSource.folderId("https://drive.google.com/"))
    }

    @Test
    fun recognizesDriveReferencesOnly() {
        assertTrue(DriveSource.isDriveReference("https://drive.google.com/drive/folders/$folderId"))
        assertTrue(DriveSource.isDriveReference(folderId))
        assertFalse(DriveSource.isDriveReference("https://example.com/signage"))
        assertFalse(DriveSource.isDriveReference("signage"))
    }

    @Test
    fun parsesFilesAndFoldersFromTheEmbeddedFolderView() {
        val html = """
            <div class="flip-entries">
            <div class="flip-entry" id="entry-1FileAAAAAAAAAAAAAAAAAAAAAAAAAAAA" tabindex="0">
              <a href="https://drive.google.com/file/d/1FileAAAAAAAAAAAAAAAAAAAAAAAAAAAA/view?usp=drive_web" target="_blank">
                <div class="flip-entry-info"><div class="flip-entry-title">Foto &amp; Text 1.jpg</div></div>
              </a>
            </div>
            <div class="flip-entry" id="entry-1FolderBBBBBBBBBBBBBBBBBBBBBBBBBBB" tabindex="0">
              <a href="https://drive.google.com/drive/folders/1FolderBBBBBBBBBBBBBBBBBBBBBBBBBBB?usp=drive_web" target="_blank">
                <div class="flip-entry-info"><div class="flip-entry-title">Empfang Nord</div></div>
              </a>
            </div>
            </div>
        """.trimIndent()

        val entries = DriveSource.parseListing(html)

        assertEquals(2, entries.size)
        assertEquals(DriveSource.Entry("1FileAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "Foto & Text 1.jpg", false), entries[0])
        assertEquals(DriveSource.Entry("1FolderBBBBBBBBBBBBBBBBBBBBBBBBBBB", "Empfang Nord", true), entries[1])
    }

    @Test
    fun aPageWithoutEntriesYieldsNothing() {
        assertTrue(DriveSource.parseListing("<html><body>Sign in</body></html>").isEmpty())
    }

    @Test
    fun unescapesHtmlEntities() {
        assertEquals("Büro <1> \"x\"", DriveSource.unescape("B&#252;ro &lt;1&gt; &quot;x&quot;"))
    }

    @Test
    fun buildsTheDownloadAddress() {
        assertTrue(DriveSource.downloadUrl("abc").startsWith("https://drive.usercontent.google.com/download?id=abc"))
    }
}
