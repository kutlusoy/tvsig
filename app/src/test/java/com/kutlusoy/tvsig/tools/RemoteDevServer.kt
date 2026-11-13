package com.kutlusoy.tvsig.tools

import com.kutlusoy.tvsig.models.AppSettings
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.remote.RemoteRouter
import com.kutlusoy.tvsig.remote.RemoteSetupServer
import java.io.File

/**
 * Runs the setup server on the computer with the real page, to try it with a browser:
 * java -cp <classes and libraries> com.kutlusoy.tvsig.tools.RemoteDevServer <port> <page.html> <token>
 */
object RemoteDevServer {
    @JvmStatic
    fun main(args: Array<String>) {
        val page = File(args[1])
        var settings = AppSettings(baseUrl = "https://h.com/s", driveId = "1AbCdEf", drivePath = "Lobby", pin = "4711")
        val server = RemoteSetupServer(
            RemoteRouter(
                token = args[2],
                page = { page.readBytes() },
                currentSettings = { settings },
                onSave = {
                    settings = it
                    println("SAVED $it")
                },
                listFolders = { _, path -> Result.Success(if (path.isEmpty()) listOf("Filiale Nord", "Filiale Sued") else listOf("Etage 1")) },
                testSource = { Result.Success(emptyList()) }
            )
        )
        println("PORT " + server.start(args[0].toInt()..args[0].toInt()))
        Thread.sleep(Long.MAX_VALUE)
    }
}
