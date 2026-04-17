package com.xtawa.classingtime.sync

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavHttpClient {
    suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(config, "OPTIONS")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            if (code !in 200..299 && code != 401 && code != 403) {
                error("HTTP $code")
            }
        }
    }

    suspend fun readJson(config: CloudRuntimeConfig): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(config, "GET")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                connection.disconnect()
                return@runCatching null
            }
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                error("GET failed with HTTP $code ${error.take(180)}")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            text
        }
    }

    suspend fun writeJson(config: CloudRuntimeConfig, payload: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(config, "PUT")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299 && code != HttpURLConnection.HTTP_CREATED && code != HttpURLConnection.HTTP_NO_CONTENT) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                error("PUT failed with HTTP $code ${error.take(180)}")
            }
            connection.disconnect()
        }
    }

    private fun openConnection(config: CloudRuntimeConfig, method: String): HttpURLConnection {
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

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        fun buildBasicAuth(username: String, password: String): String {
            val encoded = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "Basic $encoded"
        }
    }
}
