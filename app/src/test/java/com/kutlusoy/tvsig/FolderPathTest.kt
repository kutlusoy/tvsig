package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.models.FolderPath
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPathTest {

    @Test
    fun joinsAndSplitsPaths() {
        assertEquals("Lobby", FolderPath.child("", "Lobby"))
        assertEquals("Lobby/Nord", FolderPath.child("Lobby", "Nord"))
        assertEquals(listOf("a", "b"), FolderPath.segments(" /a/ b /"))
        assertEquals("a/b", FolderPath.normalize("/a//b/"))
    }

    @Test
    fun goesUp() {
        assertEquals("Lobby", FolderPath.parent("Lobby/Nord"))
        assertEquals("", FolderPath.parent("Lobby"))
        assertEquals("", FolderPath.parent(""))
        assertEquals("Nord", FolderPath.name("Lobby/Nord"))
    }

    @Test
    fun countsTheDepth() {
        assertEquals(0, FolderPath.depth(""))
        assertEquals(3, FolderPath.depth("a/b/c"))
    }

    @Test
    fun displaysThePath() {
        assertEquals("Main", FolderPath.display("", "Main"))
        assertEquals("Main / a / b", FolderPath.display("a/b", "Main"))
    }
}
