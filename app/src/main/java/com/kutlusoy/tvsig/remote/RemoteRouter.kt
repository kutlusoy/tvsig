package com.kutlusoy.tvsig.remote

import com.google.gson.Gson
import com.kutlusoy.tvsig.models.AppSettings
import com.kutlusoy.tvsig.models.ErrorMessages
import com.kutlusoy.tvsig.models.MediaFile
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.models.Source
import com.kutlusoy.tvsig.models.SourceType

/** The pages and the interface of the setup from the phone, free of Android classes so it can be tested. */
class RemoteRouter(
    private val token: String,
    private val page: () -> ByteArray,
    private val currentSettings: () -> AppSettings,
    private val onSave: (AppSettings) -> Unit,
    private val listFolders: suspend (Source, String) -> Result<List<String>>,
    private val testSource: suspend (Source) -> Result<List<MediaFile>>
) {
    private val gson = Gson()

    suspend fun route(request: HttpRequest): ByteArray {
        val provided = request.query["t"] ?: request.headers["x-token"]
        if (!RemoteHttp.tokenMatches(token, provided)) {
            return RemoteHttp.response(403, "text/plain; charset=utf-8", "Invalid link. Scan the QR code on the TV again. | Ungueltiger Link. Bitte den QR-Code am Fernseher erneut scannen. | Gecersiz baglanti. QR kodu televizyonda tekrar okutun.".toByteArray())
        }

        return when {
            request.method == "GET" && request.path == "/" -> RemoteHttp.response(200, "text/html; charset=utf-8", page())
            request.method == "GET" && request.path == "/api/state" -> json(200, RemoteSettings.from(currentSettings()))
            request.method == "GET" && request.path == "/api/folders" -> folders(request)
            request.method == "GET" && request.path == "/api/test" -> test(request)
            request.method == "POST" && request.path == "/api/save" -> save(request)
            else -> json(404, mapOf("error" to "not found"))
        }
    }

    fun badRequest(message: String): ByteArray = json(400, mapOf("error" to message))

    private fun sourceOf(request: HttpRequest, path: String): Source {
        val type = SourceType.fromId(request.query["type"]?.toIntOrNull() ?: 0)
        return Source(type, request.query["base"].orEmpty(), path)
    }

    private suspend fun folders(request: HttpRequest): ByteArray {
        val source = sourceOf(request, "")
        if (!source.isConfigured) return json(200, mapOf("ok" to false, "error" to "source_missing"))
        return when (val result = listFolders(source, request.query["path"].orEmpty())) {
            is Result.Success -> json(200, mapOf("ok" to true, "folders" to result.data))
            is Result.Error -> json(200, failure(result.exception))
            else -> json(200, mapOf("ok" to false))
        }
    }

    private suspend fun test(request: HttpRequest): ByteArray {
        val source = sourceOf(request, request.query["path"].orEmpty())
        if (!source.isConfigured) return json(200, mapOf("ok" to false, "error" to "source_missing"))
        return when (val result = testSource(source)) {
            is Result.Success -> json(
                200,
                mapOf("ok" to true, "images" to result.data.count { it.isImage() }, "videos" to result.data.count { it.isVideo() })
            )
            is Result.Error -> json(200, failure(result.exception))
            else -> json(200, mapOf("ok" to false))
        }
    }

    private fun save(request: HttpRequest): ByteArray {
        val remote = try {
            gson.fromJson(String(request.body, Charsets.UTF_8), RemoteSettings::class.java)
        } catch (e: Exception) {
            null
        } ?: return json(400, mapOf("ok" to false, "error" to "invalid_data"))

        val updated = remote.applyTo(currentSettings()) ?: return json(400, mapOf("ok" to false, "error" to "invalid_values"))
        onSave(updated)
        return json(200, mapOf("ok" to true))
    }

    /** The page shows known errors in its own language, the message is the fallback. */
    private fun failure(error: Throwable): Map<String, Any?> =
        mapOf("ok" to false, "error" to (error.message ?: "error"), "code" to ErrorMessages.codeOf(error.message))

    private fun json(status: Int, body: Any): ByteArray =
        RemoteHttp.response(status, "application/json; charset=utf-8", gson.toJson(body).toByteArray(Charsets.UTF_8))
}
