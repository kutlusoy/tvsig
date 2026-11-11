package com.kutlusoy.tvsig.repositories

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kutlusoy.tvsig.models.Constants
import com.kutlusoy.tvsig.models.FolderPath
import com.kutlusoy.tvsig.models.MediaFile
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.models.Source
import com.kutlusoy.tvsig.models.SourceType
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
    private val driveFolderIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Resolves the playlist of the source. Falls back to the last known playlist when it cannot be reached. */
    suspend fun loadPlaylist(source: Source): Result<List<MediaFile>> =
        withContext(Dispatchers.IO) {
            val key = sourceKey(source)
            val cached = getCachedPlaylist(key)

            if (!NetworkHelper.isNetworkAvailable(context)) {
                return@withContext if (cached.isNotEmpty()) Result.Success(cached)
                else Result.Error(Exception(ERROR_OFFLINE))
            }

            try {
                val files = fetchSource(source)
                savePlaylist(key, files)
                startBackgroundSync(files)
                Result.Success(files)
            } catch (e: Exception) {
                Logger.e("Failed to load the playlist", e)
                if (cached.isNotEmpty()) Result.Success(cached) else Result.Error(e)
            }
        }

    /** Lists the subfolders of a folder of the source, [path] is relative to the main folder. */
    suspend fun listSubfolders(source: Source, path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!NetworkHelper.isNetworkAvailable(context)) {
            return@withContext Result.Error(Exception(ERROR_OFFLINE))
        }
        try {
            if (source.type == SourceType.DRIVE) {
                val baseId = DriveSource.folderId(source.base) ?: throw Exception(ERROR_DRIVE_LINK)
                val folderId = resolveDriveFolder(baseId, path)
                val folders = driveEntries(folderId).filter { it.isFolder }
                folders.forEach { driveFolderIds["$baseId/${FolderPath.child(path, it.name)}"] = it.id }
                return@withContext Result.Success(folders.map { it.name }.sortedBy { it.lowercase() })
            }
            val folderUrl = buildFolderUrl(source.base, path)
            val folderFile = httpGetText(folderUrl + Constants.FOLDERS_FILE_NAME, allowNotFound = true)
            val folders = if (folderFile != null) {
                parseFolderFile(folderFile)
            } else {
                parseFolderListing(httpGetText(folderUrl, allowNotFound = false) ?: throw Exception(ERROR_LISTING), folderUrl)
            }
            Result.Success(folders)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Reads the playlist without caching, used by the connection test in the settings. */
    suspend fun testConnection(source: Source): Result<List<MediaFile>> =
        withContext(Dispatchers.IO) {
            if (!NetworkHelper.isNetworkAvailable(context)) {
                return@withContext Result.Error(Exception(ERROR_OFFLINE))
            }
            try {
                Result.Success(fetchSource(source))
            } catch (e: Exception) {
                Result.Error(e)
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

    private fun fetchSource(source: Source): List<MediaFile> =
        if (source.type == SourceType.DRIVE) fetchDrivePlaylist(source)
        else fetchPlaylist(buildFolderUrl(source.base, source.path))

    private fun driveEntries(folderId: String): List<DriveSource.Entry> {
        val html = httpGetText(DriveSource.listUrl(folderId), allowNotFound = false)
            ?: throw Exception(ERROR_DRIVE_FOLDER)
        return DriveSource.parseListing(html).ifEmpty { throw Exception(ERROR_DRIVE_FOLDER) }
    }

    /** Walks down the folder names, remembering the IDs that were already seen. */
    private fun resolveDriveFolder(baseId: String, path: String): String {
        var id = baseId
        var walked = ""
        for (name in FolderPath.segments(path)) {
            walked = FolderPath.child(walked, name)
            id = driveFolderIds["$baseId/$walked"] ?: run {
                val folder = driveEntries(id).firstOrNull { it.isFolder && it.name.equals(name, ignoreCase = true) }
                    ?: throw Exception("Subfolder not found: $name")
                folder.id.also { driveFolderIds["$baseId/$walked"] = it }
            }
        }
        return id
    }

    private fun fetchDrivePlaylist(source: Source): List<MediaFile> {
        val baseId = DriveSource.folderId(source.base) ?: throw Exception(ERROR_DRIVE_LINK)
        val files = driveEntries(resolveDriveFolder(baseId, source.path))
            .filter { !it.isFolder && MediaFile.isSupported(it.name) }
            .sortedBy { it.name.lowercase() }
            .map { MediaFile(it.name, DriveSource.downloadUrl(it.id)) }
        if (files.isEmpty()) throw Exception(ERROR_NO_MEDIA)
        return files
    }

    private fun sourceKey(source: Source): String =
        if (source.type == SourceType.DRIVE) "drive:${DriveSource.folderId(source.base)}/${FolderPath.normalize(source.path)}"
        else buildFolderUrl(source.base, source.path)

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
            if (connection.contentType?.startsWith("text/html", ignoreCase = true) == true) {
                throw Exception(ERROR_BLOCKED)
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
            prefs.edit().putString(metaKey(file), remoteSignature(file.url) ?: "").apply()
            target
        } finally {
            connection.disconnect()
            if (part.exists()) part.delete()
        }
    }

    private fun isRemoteChanged(file: MediaFile): Boolean {
        if (!localFile(file).exists()) return false
        val stored = prefs.getString(metaKey(file), null) ?: return true
        if (stored.isEmpty()) return false
        return try {
            val remote = remoteSignature(file.url)
            remote != null && remote != stored
        } catch (e: Exception) {
            false
        }
    }

    /** ETag, modification time and size from a HEAD request, or from a one byte range request. */
    private fun remoteSignature(url: String): String? {
        val head = openConnection(url, method = "HEAD")
        try {
            if (head.responseCode == HttpURLConnection.HTTP_OK) {
                return signatureOf(head, head.contentLengthLong)
            }
        } finally {
            head.disconnect()
        }

        val range = openConnection(url, headers = mapOf("Range" to "bytes=0-0"))
        try {
            return when (range.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val total = range.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                    signatureOf(range, total)
                }
                HttpURLConnection.HTTP_OK -> signatureOf(range, range.contentLengthLong)
                else -> null
            }
        } finally {
            range.disconnect()
        }
    }

    private fun signatureOf(c: HttpURLConnection, length: Long): String =
        listOf(c.getHeaderField("ETag"), c.getHeaderField("Last-Modified"), length.toString())
            .joinToString("|") { it ?: "" }

    private fun openConnection(url: String, method: String = "GET", headers: Map<String, String> = emptyMap()): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = Constants.CONNECT_TIMEOUT_MS
                readTimeout = Constants.READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgentFor(current))
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
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

    private fun userAgentFor(url: String): String =
        if (url.contains(".google.com")) "Mozilla/5.0 (Linux; Android 10) TVSig" else "TVSig"

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
        const val ERROR_BLOCKED = "The server sent a web page instead of the file (access or download limit)"
        const val ERROR_DRIVE_LINK = "No Google Drive folder ID found in the link"
        const val ERROR_DRIVE_FOLDER = "Drive folder not found or not shared with anyone who has the link"

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
            var base = baseUrl.trim().trimEnd('/')
            if (base.isNotEmpty() && !base.contains("://")) base = "https://$base"
            val sub = subfolder.trim().trim('/')
            return if (sub.isEmpty()) "${encodeUrl(base)}/" else "${encodeUrl(base)}/${encodeUrl(sub)}/"
        }

        /** Percent-encodes characters that are not allowed in a URL and keeps existing escapes. */
        fun encodeUrl(value: String): String {
            val allowed = "-._~!$&'()*+,;=:@/%"
            val out = StringBuilder()
            for (ch in value) {
                if (ch.isLetterOrDigit() && ch.code < 128 || ch in allowed) {
                    out.append(ch)
                } else {
                    ch.toString().toByteArray(Charsets.UTF_8).forEach { out.append("%%%02X".format(it)) }
                }
            }
            return out.toString()
        }

        fun parseFolderFile(text: String): List<String> =
            text.lineSequence()
                .map { it.trim().trimEnd('/') }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .distinct()
                .toList()

        /** Subfolders of a server generated directory index: links that end with a slash, one level below the folder. */
        fun parseFolderListing(html: String, folderUrl: String): List<String> =
            HREF_REGEX.findAll(html)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() && !it.startsWith("?") && !it.startsWith("#") && it.substringBefore('?').endsWith("/") }
                .mapNotNull { runCatching { URI(folderUrl).resolve(it.replace(" ", "%20")).toString() }.getOrNull() }
                .filter { it.startsWith(folderUrl) }
                .map { it.removePrefix(folderUrl).trimEnd('/') }
                .filter { it.isNotEmpty() && !it.contains('/') && !it.startsWith(".") }
                .map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
                .distinct()
                .sortedBy { it.lowercase() }
                .toList()
    }
}
