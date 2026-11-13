package com.kutlusoy.tvsig.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads over HTTPS only and refuses a file whose SHA-256 does not match. Free of Android classes. */
class UpdateDownloader(private val requireHttps: Boolean = true) {

    fun fetchText(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = 1_000_000): String {
        val connection = open(url, headers)
        try {
            check(connection)
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > maxBytes) throw UpdateException(UpdateException.Code.TOO_LARGE)
                }
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    /** Stores the file only when it is complete and its checksum is right. */
    fun download(url: String, target: File, expectedSha256: String, maxBytes: Long, onProgress: (Int) -> Unit = {}) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val connection = open(url, emptyMap())
        try {
            check(connection)
            val length = connection.contentLengthLong
            if (length > maxBytes) throw UpdateException(UpdateException.Code.TOO_LARGE)
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw UpdateException(UpdateException.Code.TOO_LARGE)
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        if (length > 0) {
                            val percent = (total * 100 / length).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            if (length > 0 && total != length) throw UpdateException(UpdateException.Code.NETWORK, "Incomplete download")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) throw UpdateException(UpdateException.Code.CHECKSUM_MISMATCH)
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw UpdateException(UpdateException.Code.STORAGE)
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException(if (e.message?.contains("ENOSPC") == true) UpdateException.Code.STORAGE else UpdateException.Code.NETWORK, e.message)
        } finally {
            connection.disconnect()
            if (part.exists()) part.delete()
        }
    }

    private fun check(connection: HttpURLConnection) {
        when (val code = connection.responseCode) {
            HttpURLConnection.HTTP_OK -> Unit
            HttpURLConnection.HTTP_NOT_FOUND -> throw UpdateException(UpdateException.Code.NOT_FOUND)
            else -> throw UpdateException(UpdateException.Code.NETWORK, "HTTP $code")
        }
    }

    /** Follows redirects by hand so that every step is checked to be HTTPS. */
    private fun open(start: String, headers: Map<String, String>): HttpURLConnection {
        var current = start
        repeat(MAX_REDIRECTS + 1) {
            if (requireHttps && !current.startsWith("https://")) throw UpdateException(UpdateException.Code.INSECURE_URL, current)
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "TVSig")
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                connection.disconnect()
                throw UpdateException(UpdateException.Code.NETWORK, e.message)
            }
            val location = connection.getHeaderField("Location")
            if (code in REDIRECTS && location != null) {
                connection.disconnect()
                current = URL(URL(current), location).toString()
            } else {
                return connection
            }
        }
        throw UpdateException(UpdateException.Code.NETWORK, "Too many redirects")
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
