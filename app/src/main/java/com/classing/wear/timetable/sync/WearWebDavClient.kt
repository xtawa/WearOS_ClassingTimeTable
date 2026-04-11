package com.classing.wear.timetable.sync

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WearWebDavClient {
    suspend fun readJson(config: WearCloudConfig): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(config, "GET")
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                connection.disconnect()
                return@runCatching null
            }
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                error("GET failed HTTP $code ${error.take(180)}")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            text
        }
    }

    private fun openConnection(config: WearCloudConfig, method: String): HttpURLConnection {
        val url = resolveTargetUrl(config.serverUrl, config.remotePath)
        val connection = (url.openConnection() as? HttpURLConnection)
            ?: throw IOException("Unable to open connection")
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", buildBasicAuth(config.username, config.password))
        connection.setRequestProperty("Accept", "application/json")
        return connection
    }

    private fun resolveTargetUrl(serverUrl: String, remotePath: String): URL {
        val server = serverUrl.trimEnd('/')
        val path = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        return URL("$server$path")
    }

    private fun buildBasicAuth(username: String, password: String): String {
        val encoded = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }
}
