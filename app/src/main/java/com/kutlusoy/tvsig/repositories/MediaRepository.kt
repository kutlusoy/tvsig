package com.kutlusoy.tvsig.repositories

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kutlusoy.tvsig.models.Constants
import com.kutlusoy.tvsig.models.MediaFile
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest

class MediaRepository private constructor(private val context: Context) {

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadMutex = Mutex()
    private val syncMutex = Mutex()
    private val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Resolves the playlist of `<baseUrl>/<subfolder>/`. Falls back to the last known
     * playlist when the server cannot be reached.
     */
    suspend fun loadPlaylist(baseUrl: String, subfolder: String): Result<List<MediaFile>> =
        withContext(Dispatchers.IO) {
            val folderUrl = buildFolderUrl(baseUrl, subfolder)
            val cached = getCachedPlaylist(folderUrl)

            if (!NetworkHelper.isNetworkAvailable(context)) {
                return@withContext if (cached.isNotEmpty()) Result.Success(cached)
                else Result.Error(Exception(ERROR_OFFLINE))
            }

            try {
                val files = fetchPlaylist(folderUrl)
                savePlaylist(folderUrl, files)
                startBackgroundSync(files)
                Result.Success(files)
            } catch (e: Exception) {
                Logger.e("Failed to load playlist from $folderUrl", e)
                if (cached.isNotEmpty()) Result.Success(cached) else Result.Error(e)
            }
        }

    /** Returns the local copy of a file, downloading it first when it is missing. */
    suspend fun ensureLocal(file: MediaFile): Result<File> = withContext(Dispatchers.IO) {
        val local = localFile(file)
        if (local.exists() && local.length() > 0) return@withContext Result.Success(local)

        if (!NetworkHelper.isNetworkAvailable(context)) {
            return@withContext Result.Error(Exception(ERROR_OFFLINE))
        }
        try {
            Result.Success(download(file, force = false))
        } catch (e: Exception) {
            Logger.e("Failed to download ${file.name}", e)
            Result.Error(e)
        }
    }

    fun clearLocalMedia() {
        mediaDir.listFiles()?.forEach { it.delete() }
        prefs.edit().clear().apply()
    }

    private fun startBackgroundSync(files: List<MediaFile>) {
        scope.launch {
            if (!syncMutex.tryLock()) return@launch
            try {
                files.forEach { file ->
                    try {
                        if (isRemoteChanged(file)) download(file, force = true)
                        else if (!localFile(file).exists()) download(file, force = false)
                    } catch (e: Exception) {
                        Logger.e("Sync failed for ${file.name}", e)
                    }
                }
                pruneUnused(files)
            } finally {
                syncMutex.unlock()
            }
        }
    }

    private fun pruneUnused(files: List<MediaFile>) {
        val keep = files.map { localFile(it).name }.toSet()
        mediaDir.listFiles()?.forEach { f ->
            val stalePart = f.name.endsWith(PART_SUFFIX) &&
                    System.currentTimeMillis() - f.lastModified() > STALE_PART_MS
            if (stalePart || (!f.name.endsWith(PART_SUFFIX) && f.name !in keep)) f.delete()
        }
    }

    private fun fetchPlaylist(folderUrl: String): List<MediaFile> {
        val playlistText = httpGetText(folderUrl + Constants.PLAYLIST_FILE_NAME, allowNotFound = true)
        val files = if (playlistText != null) {
            parsePlaylistFile(playlistText, folderUrl)
        } else {
            val html = httpGetText(folderUrl, allowNotFound = false)
                ?: throw Exception(ERROR_LISTING)
            parseDirectoryListing(html, folderUrl)
        }
        if (files.isEmpty()) throw Exception(ERROR_NO_MEDIA)
        return files
    }

    private fun parsePlaylistFile(text: String, folderUrl: String): List<MediaFile> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { toMediaFile(it, folderUrl) }
            .distinctBy { it.url }
            .toList()

    private fun parseDirectoryListing(html: String, folderUrl: String): List<MediaFile> =
        HREF_REGEX.findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && !it.startsWith("?") && !it.startsWith("#") }
            .mapNotNull { toMediaFile(it, folderUrl) }
            .filter { it.url.startsWith(folderUrl) }
            .distinctBy { it.url }
            .sortedBy { it.name.lowercase() }
            .toList()

    private fun toMediaFile(reference: String, folderUrl: String): MediaFile? {
        return try {
            val resolved = URI(folderUrl).resolve(reference.replace(" ", "%20")).toString()
            val rawName = resolved.substringBefore('?').substringAfterLast('/')
            val name = URLDecoder.decode(rawName.replace("+", "%2B"), "UTF-8")
            if (name.isNotEmpty() && MediaFile.isSupported(name)) MediaFile(name, resolved) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGetText(url: String, allowNotFound: Boolean): String? {
        val connection = openConnection(url)
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            }
            if (allowNotFound && (code == 404 || code == 403 || code == 410)) return null
            throw Exception("HTTP $code ($url)")
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(file: MediaFile, force: Boolean): File = downloadMutex.withLock {
        val target = localFile(file)
        if (!force && target.exists() && target.length() > 0) return@withLock target

        val part = File(mediaDir, target.name + PART_SUFFIX)
        val connection = openConnection(file.url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode} (${file.url})")
            }
            val expected = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            if (part.length() == 0L || (expected > 0 && part.length() != expected)) {
                throw Exception("Incomplete download: ${file.name}")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw Exception("Could not store ${file.name}")
            prefs.edit().putString(metaKey(file), signature(connection)).apply()
            target
        } finally {
            connection.disconnect()
            if (part.exists()) part.delete()
        }
    }

    private fun isRemoteChanged(file: MediaFile): Boolean {
        val local = localFile(file)
        if (!local.exists()) return false
        val stored = prefs.getString(metaKey(file), null) ?: return true
        return try {
            val connection = openConnection(file.url, method = "HEAD")
            try {
                connection.responseCode == HttpURLConnection.HTTP_OK && signature(connection) != stored
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun signature(c: HttpURLConnection): String =
        listOf(c.getHeaderField("ETag"), c.getHeaderField("Last-Modified"), c.contentLengthLong.toString())
            .joinToString("|") { it ?: "" }

    private fun openConnection(url: String, method: String = "GET"): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = Constants.CONNECT_TIMEOUT_MS
                readTimeout = Constants.READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "TvSig")
            }
            val code = connection.responseCode
            val location = connection.getHeaderField("Location")
            if (code in REDIRECT_CODES && location != null) {
                connection.disconnect()
                current = URL(URL(current), location).toString()
            } else {
                return connection
            }
        }
        throw Exception("Too many redirects ($url)")
    }

    private fun localFile(file: MediaFile): File =
        File(mediaDir, "${sha1(file.url)}.${file.extension}")

    private fun metaKey(file: MediaFile) = "meta_${sha1(file.url)}"

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)

    private fun savePlaylist(folderUrl: String, files: List<MediaFile>) {
        prefs.edit()
            .putString(KEY_PLAYLIST_FOLDER, folderUrl)
            .putString(KEY_PLAYLIST, gson.toJson(files))
            .apply()
    }

    private fun getCachedPlaylist(folderUrl: String): List<MediaFile> {
        if (prefs.getString(KEY_PLAYLIST_FOLDER, null) != folderUrl) return emptyList()
        return try {
            val type = object : TypeToken<List<MediaFile>>() {}.type
            gson.fromJson<List<MediaFile>>(prefs.getString(KEY_PLAYLIST, null), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val ERROR_OFFLINE = "Offline"
        const val ERROR_LISTING = "Folder listing not available"
        const val ERROR_NO_MEDIA = "No media found"

        private const val MEDIA_DIR_NAME = "media"
        private const val PREFS_NAME = "media_cache"
        private const val KEY_PLAYLIST = "playlist"
        private const val KEY_PLAYLIST_FOLDER = "playlist_folder"
        private const val PART_SUFFIX = ".part"
        private const val MAX_REDIRECTS = 5
        private const val STALE_PART_MS = 60 * 60 * 1000L
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val HREF_REGEX = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        @Volatile
        private var instance: MediaRepository? = null

        fun getInstance(context: Context): MediaRepository =
            instance ?: synchronized(this) {
                instance ?: MediaRepository(context.applicationContext).also { instance = it }
            }

        fun buildFolderUrl(baseUrl: String, subfolder: String): String {
            val base = baseUrl.trim().trimEnd('/')
            val sub = subfolder.trim().trim('/')
            return if (sub.isEmpty()) "$base/" else "$base/$sub/"
        }
    }
}
