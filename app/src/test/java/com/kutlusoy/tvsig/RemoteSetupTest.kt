package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.models.AppSettings
import com.kutlusoy.tvsig.remote.RemoteHttp
import com.kutlusoy.tvsig.remote.RemoteSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class RemoteSetupTest {

    private fun request(text: String) = RemoteHttp.readRequest(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun parsesAGetRequestWithQuery() {
        val parsed = request("GET /api/folders?t=abc&base=https%3A%2F%2Fh.com%2Fs&path=B%C3%BCro HTTP/1.1\r\nHost: tv\r\nX-Token: abc\r\n\r\n")!!
        assertEquals("GET", parsed.method)
        assertEquals("/api/folders", parsed.path)
        assertEquals("https://h.com/s", parsed.query["base"])
        assertEquals("Büro", parsed.query["path"])
        assertEquals("abc", parsed.headers["x-token"])
    }

    @Test
    fun readsThePostBody() {
        val body = """{"a":1}"""
        val parsed = request("POST /api/save HTTP/1.1\r\nContent-Length: ${body.length}\r\n\r\n$body")!!
        assertEquals(body, String(parsed.body))
    }

    @Test(expected = IOException::class)
    fun rejectsAHugeBody() {
        request("POST /api/save HTTP/1.1\r\nContent-Length: 99999999\r\n\r\n")
    }

    @Test
    fun comparesTheToken() {
        assertTrue(RemoteHttp.tokenMatches("abc123", "abc123"))
        assertFalse(RemoteHttp.tokenMatches("abc123", "abc124"))
        assertFalse(RemoteHttp.tokenMatches("abc123", null))
        assertFalse(RemoteHttp.tokenMatches("abc123", ""))
    }

    @Test
    fun buildsAResponseWithTheLength() {
        val text = String(RemoteHttp.response(200, "text/plain", "hi".toByteArray()))
        assertTrue(text.startsWith("HTTP/1.1 200 OK"))
        assertTrue(text.contains("Content-Length: 2"))
        assertTrue(text.endsWith("hi"))
    }

    @Test
    fun appliesValidSettingsAndKeepsThePin() {
        val current = AppSettings(pin = "4711")
        val updated = RemoteSettings(
            sourceType = 1, driveId = " 1AbC ", drivePath = "/a//b/", slideDelay = 15, transition = 8, orientation = 90
        ).applyTo(current)!!
        assertEquals("4711", updated.pin)
        assertEquals("1AbC", updated.driveId)
        assertEquals("a/b", updated.drivePath)
        assertEquals(8, updated.transition)
    }

    @Test
    fun rejectsInvalidValues() {
        val current = AppSettings()
        assertNull(RemoteSettings(slideDelay = 1).applyTo(current))
        assertNull(RemoteSettings(transition = 99).applyTo(current))
        assertNull(RemoteSettings(orientation = 45).applyTo(current))
        assertNull(RemoteSettings(sourceType = 5).applyTo(current))
        assertNull(RemoteSettings(baseUrl = "x".repeat(3000)).applyTo(current))
        assertNotNull(RemoteSettings().applyTo(current))
    }
}
