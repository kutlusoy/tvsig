package com.kutlusoy.tvsig.remote

import android.content.Context
import com.google.gson.Gson
import com.kutlusoy.tvsig.models.AppSettings
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.models.Source
import com.kutlusoy.tvsig.models.SourceType
import com.kutlusoy.tvsig.repositories.MediaRepository
import com.kutlusoy.tvsig.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

/**
 * Serves the setup page for the phone. It only runs while the setup dialog is open and every
 * request needs the one-time token from the QR code.
 */
class RemoteSetupServer(
    private val context: Context,
    private val token: String,
    private val currentSettings: () -> AppSettings,
    private val onSave: (AppSettings) -> Unit
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    /** Starts listening on the first free port and returns it. */
    fun start(): Int {
        var socket: ServerSocket? = null
        for (candidate in PORTS) {
            try {
                socket = ServerSocket(candidate)
                break
            } catch (e: IOException) {
                continue
            }
        }
        val server = socket ?: throw IOException("No free port")
        serverSocket = server

        scope.launch {
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                launch { handle(client) }
            }
        }
        return server.localPort
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Logger.e("Error closing the setup server", e)
        }
        scope.cancel()
    }

    private suspend fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val reply = try {
                val request = RemoteHttp.readRequest(socket.getInputStream().buffered())
                if (request == null) json(400, mapOf("error" to "bad request")) else route(request)
            } catch (e: IOException) {
                json(400, mapOf("error" to "bad request"))
            } catch (e: Exception) {
                Logger.e("Setup request failed", e)
                json(400, mapOf("error" to (e.message ?: "error")))
            }
            try {
                socket.getOutputStream().apply {
                    write(reply)
                    flush()
                }
            } catch (e: IOException) {
                Logger.d("The phone closed the connection")
            }
        }
    }

    private suspend fun route(request: HttpRequest): ByteArray {
        val provided = request.query["t"] ?: request.headers["x-token"]
        if (!RemoteHttp.tokenMatches(token, provided)) {
            return RemoteHttp.response(403, "text/plain; charset=utf-8", "Link ungueltig. Bitte den QR-Code am Fernseher erneut scannen.".toByteArray())
        }

        return when {
            request.method == "GET" && request.path == "/" -> {
                val page = context.assets.open(PAGE_ASSET).use { it.readBytes() }
                RemoteHttp.response(200, "text/html; charset=utf-8", page)
            }
            request.method == "GET" && request.path == "/api/state" -> json(200, RemoteSettings.from(currentSettings()))
            request.method == "GET" && request.path == "/api/folders" -> folders(request)
            request.method == "GET" && request.path == "/api/test" -> test(request)
            request.method == "POST" && request.path == "/api/save" -> save(request)
            else -> json(404, mapOf("error" to "not found"))
        }
    }

    private fun sourceOf(request: HttpRequest, path: String): Source {
        val type = SourceType.fromId(request.query["type"]?.toIntOrNull() ?: 0)
        return Source(type, request.query["base"].orEmpty(), path)
    }

    private suspend fun folders(request: HttpRequest): ByteArray {
        val source = sourceOf(request, "")
        if (!source.isConfigured) return json(200, mapOf("ok" to false, "error" to "Quelle fehlt"))
        return when (val result = MediaRepository.getInstance(context).listSubfolders(source, request.query["path"].orEmpty())) {
            is Result.Success -> json(200, mapOf("ok" to true, "folders" to result.data))
            is Result.Error -> json(200, mapOf("ok" to false, "error" to (result.exception.message ?: "Fehler")))
            else -> json(200, mapOf("ok" to false))
        }
    }

    private suspend fun test(request: HttpRequest): ByteArray {
        val source = sourceOf(request, request.query["path"].orEmpty())
        if (!source.isConfigured) return json(200, mapOf("ok" to false, "error" to "Quelle fehlt"))
        return when (val result = MediaRepository.getInstance(context).testConnection(source)) {
            is Result.Success -> json(
                200,
                mapOf("ok" to true, "images" to result.data.count { it.isImage() }, "videos" to result.data.count { it.isVideo() })
            )
            is Result.Error -> json(200, mapOf("ok" to false, "error" to (result.exception.message ?: "Fehler")))
            else -> json(200, mapOf("ok" to false))
        }
    }

    private fun save(request: HttpRequest): ByteArray {
        val remote = try {
            gson.fromJson(String(request.body, Charsets.UTF_8), RemoteSettings::class.java)
        } catch (e: Exception) {
            null
        } ?: return json(400, mapOf("ok" to false, "error" to "Ungueltige Daten"))

        val updated = remote.applyTo(currentSettings()) ?: return json(400, mapOf("ok" to false, "error" to "Ungueltige Werte"))
        onSave(updated)
        return json(200, mapOf("ok" to true))
    }

    private fun json(status: Int, body: Any): ByteArray =
        RemoteHttp.response(status, "application/json; charset=utf-8", gson.toJson(body).toByteArray(Charsets.UTF_8))

    private companion object {
        val PORTS = 8080..8099
        const val SOCKET_TIMEOUT_MS = 15_000
        const val PAGE_ASSET = "remote_setup.html"
    }
}
