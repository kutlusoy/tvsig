package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.models.AppSettings
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.remote.RemoteRouter
import com.kutlusoy.tvsig.remote.RemoteSetupServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

/** Runs the real server on the computer and talks to it over the network like the browser of the phone does. */
class RemoteSetupServerTest {

    private val token = "0123456789abcdef"
    private var saved: AppSettings? = null
    private var current = AppSettings(baseUrl = "https://h.com/s", driveId = "1AbCdEf", pin = "4711")
    private lateinit var server: RemoteSetupServer
    private var port = 0

    @Before
    fun start() {
        server = RemoteSetupServer(
            RemoteRouter(
                token = token,
                page = { "<html>page</html>".toByteArray() },
                currentSettings = { current },
                onSave = { saved = it },
                listFolders = { _, _ -> Result.Success(listOf("a", "b")) },
                testSource = { Result.Success(emptyList()) }
            )
        )
        port = server.start(18080..18180)
    }

    @After
    fun stop() = server.stop()

    private fun call(method: String, path: String, body: String? = null, header: String? = token): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        if (header != null) connection.setRequestProperty("X-Token", header)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = connection.responseCode
        val stream = if (code < 400) connection.inputStream else connection.errorStream
        return code to stream.bufferedReader().use { it.readText() }
    }

    /** The values that the setup page sends when the phone presses save. */
    private val pagePayload = """{"sourceType":0,"baseUrl":"https://h.com/s","subfolder":"Filiale Nord/Etage 2","driveId":"1AbCdEf","drivePath":"Lobby","slideDelay":10,"transition":8,"backdrop":0,"language":2,"orientation":90}"""

    @Test
    fun servesThePageOnlyWithTheToken() {
        assertEquals(200, call("GET", "/?t=$token", header = null).first)
        assertEquals(403, call("GET", "/?t=wrong", header = null).first)
        assertEquals(403, call("GET", "/api/state", header = null).first)
    }

    @Test
    fun theStateUsesTheFieldNamesOfThePage() {
        val (code, body) = call("GET", "/api/state")
        assertEquals(200, code)
        listOf("sourceType", "baseUrl", "subfolder", "driveId", "drivePath", "slideDelay", "transition", "backdrop", "language", "orientation")
            .forEach { assertTrue("missing $it in $body", body.contains("\"$it\"")) }
    }

    @Test
    fun savesWhatThePageSends() {
        val (code, body) = call("POST", "/api/save", pagePayload)
        assertEquals(body, 200, code)
        assertTrue(body.contains("\"ok\":true"))
        val result = saved!!
        assertNotNull(result)
        assertEquals("Filiale Nord/Etage 2", result.subfolder)
        assertEquals(90, result.orientation)
        assertEquals(8, result.transition)
        assertEquals(2, result.language)
        assertEquals("4711", result.pin)
    }

    @Test
    fun savesWhenTheBrowserSendsTheBodyInASecondPacket() {
        Socket("127.0.0.1", port).use { socket ->
            val out = socket.getOutputStream()
            out.write(("POST /api/save HTTP/1.1\r\nHost: tv\r\nX-Token: $token\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${pagePayload.toByteArray().size}\r\n\r\n").toByteArray())
            out.flush()
            Thread.sleep(300)
            out.write(pagePayload.toByteArray())
            out.flush()
            val reply = socket.getInputStream().bufferedReader().readText()
            assertTrue(reply, reply.startsWith("HTTP/1.1 200"))
        }
        assertNotNull(saved)
    }

    @Test
    fun savesWhenAValueIsMissingOrNull() {
        val (code, body) = call("POST", "/api/save", """{"sourceType":1,"driveId":"1AbC","slideDelay":15,"transition":null,"orientation":null}""")
        assertEquals(body, 200, code)
        assertEquals(1, saved?.sourceType)
    }

    @Test
    fun refusesInvalidValues() {
        val (code, _) = call("POST", "/api/save", pagePayload.replace("\"slideDelay\":10", "\"slideDelay\":1"))
        assertEquals(400, code)
        assertNull(saved)
    }

    @Test
    fun listsFolders() {
        val (code, body) = call("GET", "/api/folders?type=0&base=https%3A%2F%2Fh.com&path=")
        assertEquals(200, code)
        assertTrue(body.contains("\"a\""))
    }
}
