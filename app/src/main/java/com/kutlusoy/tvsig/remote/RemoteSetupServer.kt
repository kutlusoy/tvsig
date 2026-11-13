package com.kutlusoy.tvsig.remote

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
 * request needs the one-time token from the QR code, the [router] checks it.
 */
class RemoteSetupServer(private val router: RemoteRouter) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    /** Starts listening on the first free port and returns it. */
    fun start(ports: IntRange = PORTS): Int {
        var socket: ServerSocket? = null
        for (candidate in ports) {
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
                if (request == null) router.badRequest("bad request") else router.route(request)
            } catch (e: IOException) {
                router.badRequest("bad request")
            } catch (e: Exception) {
                Logger.e("Setup request failed", e)
                router.badRequest(e.message ?: "error")
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

    companion object {
        val PORTS = 8080..8099
        private const val SOCKET_TIMEOUT_MS = 15_000
    }
}
