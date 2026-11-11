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
}
