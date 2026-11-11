package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.models.MediaFile
import com.kutlusoy.tvsig.repositories.MediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileTest {

    @Test
    fun detectsImagesAndVideosByExtension() {
        assertTrue(MediaFile("a.JPG", "http://h/a.JPG").isImage())
        assertTrue(MediaFile("b.webp", "http://h/b.webp").isImage())
        assertTrue(MediaFile("c.mp4", "http://h/c.mp4").isVideo())
        assertFalse(MediaFile("d.txt", "http://h/d.txt").isImage())
        assertFalse(MediaFile("d.txt", "http://h/d.txt").isVideo())
    }

    @Test
    fun supportsOnlyKnownExtensions() {
        assertTrue(MediaFile.isSupported("clip.webm"))
        assertTrue(MediaFile.isSupported("photo.png"))
        assertFalse(MediaFile.isSupported("playlist.txt"))
        assertFalse(MediaFile.isSupported("noextension"))
    }

    @Test
    fun buildsFolderUrl() {
        assertEquals("https://h.com/s/lobby/", MediaRepository.buildFolderUrl("https://h.com/s/", "/lobby/"))
        assertEquals("https://h.com/s/lobby/", MediaRepository.buildFolderUrl(" https://h.com/s ", "lobby"))
        assertEquals("https://h.com/", MediaRepository.buildFolderUrl("https://h.com", ""))
    }

    @Test
    fun assumesHttpsWhenTheSchemeIsMissing() {
        assertEquals("https://h.com/s/lobby/", MediaRepository.buildFolderUrl("h.com/s", "lobby"))
        assertEquals("http://192.168.1.5/tv/", MediaRepository.buildFolderUrl("http://192.168.1.5", "tv"))
    }

    @Test
    fun encodesFolderNamesWithSpacesAndUmlauts() {
        assertEquals("https://h.com/s/My%20Folder/", MediaRepository.buildFolderUrl("https://h.com/s", "My Folder"))
        assertEquals("https://h.com/s/B%C3%BCro/", MediaRepository.buildFolderUrl("https://h.com/s", "B\u00FCro"))
        assertEquals("https://h.com/s/My%20Folder/", MediaRepository.buildFolderUrl("https://h.com/s", "My%20Folder"))
    }

    @Test
    fun listsOnlyDirectSubfoldersFromAnIndexPage() {
        val html = """
            <a href="?C=N;O=D">Name</a>
            <a href="/signage/">Parent Directory</a>
            <a href="lobby/">lobby/</a>
            <a href="Empfang%20Nord/">Empfang Nord/</a>
            <a href="B%C3%BCro/">B&uuml;ro/</a>
            <a href="lobby/deep/">deep</a>
            <a href="photo.jpg">photo.jpg</a>
            <a href="../">../</a>
        """.trimIndent()
        assertEquals(
            listOf("B\u00FCro", "Empfang Nord", "lobby"),
            MediaRepository.parseFolderListing(html, "https://h.com/signage/")
        )
    }

    @Test
    fun parsesAFolderFile() {
        assertEquals(listOf("a", "b c"), MediaRepository.parseFolderFile("# folders\na/\n\nb c\na"))
    }
}
