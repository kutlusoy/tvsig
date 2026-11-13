package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.update.Checksums
import com.kutlusoy.tvsig.update.ReleaseParser
import com.kutlusoy.tvsig.update.UpdateDownloader
import com.kutlusoy.tvsig.update.UpdateException
import com.kutlusoy.tvsig.update.Versions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.nio.file.Files
import java.security.MessageDigest

class UpdateLogicTest {

    private val releaseJson = """
        {
          "tag_name": "v0.0.3", "draft": false, "prerelease": false, "name": "TVSig v0.0.3", "body": "notes that nobody reads",
          "assets": [
            {"name": "TVSig-0.0.3-mapping.txt", "browser_download_url": "https://github.com/o/r/releases/download/v0.0.3/TVSig-0.0.3-mapping.txt", "size": 10},
            {"name": "TVSig-0.0.3.aab", "browser_download_url": "https://github.com/o/r/releases/download/v0.0.3/TVSig-0.0.3.aab", "size": 20},
            {"name": "TVSig-0.0.3.apk", "browser_download_url": "https://github.com/o/r/releases/download/v0.0.3/TVSig-0.0.3.apk", "size": 12345678},
            {"name": "SHA256SUMS.txt", "browser_download_url": "https://github.com/o/r/releases/download/v0.0.3/SHA256SUMS.txt", "size": 200}
          ]
        }
    """.trimIndent()

    @Test
    fun readsTheNewestRelease() {
        val info = ReleaseParser.parse(releaseJson)!!
        assertEquals("0.0.3", info.version)
        assertEquals(3L, info.versionCode)
        assertEquals("TVSig-0.0.3.apk", info.apkName)
        assertTrue(info.apkUrl!!.endsWith("/TVSig-0.0.3.apk"))
        assertEquals(12345678L, info.apkSize)
        assertTrue(info.checksumsUrl!!.endsWith("/SHA256SUMS.txt"))
    }

    @Test
    fun ignoresDraftsPreReleasesAndBrokenAnswers() {
        assertNull(ReleaseParser.parse(releaseJson.replace("\"prerelease\": false", "\"prerelease\": true")))
        assertNull(ReleaseParser.parse(releaseJson.replace("\"draft\": false", "\"draft\": true")))
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("""{"message":"Not Found"}"""))
        assertNull(ReleaseParser.parse(releaseJson.replace("v0.0.3", "nightly")))
    }

    @Test
    fun aReleaseWithoutFilesIsStillReadable() {
        val info = ReleaseParser.parse("""{"tag_name":"v1.2.0","assets":[]}""")!!
        assertEquals(1_002_000L, info.versionCode)
        assertNull(info.apkUrl)
        assertNull(info.checksumsUrl)
    }

    @Test
    fun versionCodesFollowTheSchemeOfTheVersionFile() {
        assertEquals(1L, Versions.code("0.0.1"))
        assertEquals(1000L, Versions.code("0.1.0"))
        assertEquals(1_002_003L, Versions.code("1.2.3"))
        assertNull(Versions.code("1.2"))
        assertNull(Versions.code("v1.2.3"))
        assertNull(Versions.code("1.1000.0"))
        assertTrue(Versions.code("0.0.10")!! > Versions.code("0.0.9")!!)
        assertTrue(Versions.code("0.1.0")!! > Versions.code("0.0.99")!!)
    }

    @Test
    fun findsTheChecksumOfAFile() {
        val hash = "a".repeat(64)
        val other = "b".repeat(64)
        val text = "$other  ./TVSig-0.0.3.aab\n$hash  ./TVSig-0.0.3.apk\n"
        assertEquals(hash, Checksums.find(text, "TVSig-0.0.3.apk"))
        assertEquals(other, Checksums.find(text, "TVSig-0.0.3.aab"))
        assertNull(Checksums.find(text, "TVSig-9.9.9.apk"))
        assertEquals(hash, Checksums.find("${hash.uppercase()} *TVSig-0.0.3.apk", "TVSig-0.0.3.apk"))
        assertNull(Checksums.find("nothex  ./TVSig-0.0.3.apk", "TVSig-0.0.3.apk"))
    }

    // --- download with a local server

    private lateinit var server: ServerSocket
    private lateinit var folder: File
    private val payload = ByteArray(300_000) { (it % 251).toByte() }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Before
    fun startServer() {
        folder = Files.createTempDirectory("update-test").toFile()
        server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) {
                    client.use { socket ->
                        val request = socket.getInputStream().bufferedReader()
                        val path = request.readLine().split(' ')[1]
                        while (request.readLine().orEmpty().isNotEmpty()) { /* skip the headers */ }
                        val out = socket.getOutputStream()
                        fun send(status: String, body: ByteArray, extra: String = "") {
                            out.write("HTTP/1.1 $status\r\n${extra}Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            out.write(body)
                            out.flush()
                        }
                        when (path) {
                            "/file.apk" -> send("200 OK", payload)
                            "/moved.apk" -> send("302 Found", ByteArray(0), "Location: /file.apk\r\n")
                            "/text" -> send("200 OK", "hello".toByteArray())
                            else -> send("404 Not Found", ByteArray(0))
                        }
                    }
                }
            }
        }
    }

    @After
    fun stopServer() {
        server.close()
        folder.deleteRecursively()
    }

    private fun url(path: String) = "http://127.0.0.1:${server.localPort}$path"

    @Test
    fun storesAFileWithTheRightChecksumAndReportsProgress() {
        val progress = mutableListOf<Int>()
        val target = File(folder, "update.apk")
        UpdateDownloader(requireHttps = false).download(url("/file.apk"), target, sha256(payload), 10_000_000) { progress.add(it) }
        assertTrue(target.exists())
        assertEquals(payload.size.toLong(), target.length())
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        assertFalse(File(folder, "update.apk.part").exists())
    }

    @Test
    fun followsRedirects() {
        val target = File(folder, "update.apk")
        UpdateDownloader(requireHttps = false).download(url("/moved.apk"), target, sha256(payload), 10_000_000)
        assertTrue(target.exists())
    }

    @Test
    fun refusesAndDeletesAFileWithAWrongChecksum() {
        val target = File(folder, "update.apk")
        try {
            UpdateDownloader(requireHttps = false).download(url("/file.apk"), target, "0".repeat(64), 10_000_000)
            throw AssertionError("must fail")
        } catch (e: UpdateException) {
            assertEquals(UpdateException.Code.CHECKSUM_MISMATCH, e.code)
        }
        assertFalse(target.exists())
        assertFalse(File(folder, "update.apk.part").exists())
    }

    @Test
    fun refusesAFileThatIsTooLarge() {
        try {
            UpdateDownloader(requireHttps = false).download(url("/file.apk"), File(folder, "update.apk"), sha256(payload), 1000)
            throw AssertionError("must fail")
        } catch (e: UpdateException) {
            assertEquals(UpdateException.Code.TOO_LARGE, e.code)
        }
    }

    @Test
    fun refusesAnAddressThatIsNotHttps() {
        try {
            UpdateDownloader().download(url("/file.apk"), File(folder, "update.apk"), sha256(payload), 10_000_000)
            throw AssertionError("must fail")
        } catch (e: UpdateException) {
            assertEquals(UpdateException.Code.INSECURE_URL, e.code)
        }
    }

    @Test
    fun readsText() {
        assertEquals("hello", UpdateDownloader(requireHttps = false).fetchText(url("/text")))
    }

    @Test
    fun aMissingFileIsReported() {
        try {
            UpdateDownloader(requireHttps = false).fetchText(url("/nothing"))
            throw AssertionError("must fail")
        } catch (e: UpdateException) {
            assertEquals(UpdateException.Code.NOT_FOUND, e.code)
        }
        assertNotNull(folder)
    }
}
