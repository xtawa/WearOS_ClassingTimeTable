package com.xtawa.classingtime.sync

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CloudAuthExpiredException(message: String) : IOException(message)

class GoogleDriveHttpClient {
    suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = config.driveFileName.ifBlank { "classing_sync.json" }
            findFileId(config.driveAccessToken, fileName)
            Unit
        }
    }

    suspend fun readJson(config: CloudRuntimeConfig): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = config.driveFileName.ifBlank { "classing_sync.json" }
            val fileId = findFileId(config.driveAccessToken, fileName) ?: return@runCatching null
            val connection = openConnection(
                url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
                method = "GET",
                accessToken = config.driveAccessToken,
            )
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                connection.disconnect()
                return@runCatching null
            }
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                throw CloudAuthExpiredException("Google Drive token expired: ${error.take(120)}")
            }
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                error("Drive GET failed with HTTP $code ${error.take(180)}")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            payload
        }
    }

    suspend fun writeJson(config: CloudRuntimeConfig, payload: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = config.driveFileName.ifBlank { "classing_sync.json" }
            val accessToken = config.driveAccessToken
            val fileId = findFileId(accessToken, fileName) ?: createFile(accessToken, fileName)
            val connection = openConnection(
                url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
                method = "PATCH",
                accessToken = accessToken,
            )
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                throw CloudAuthExpiredException("Google Drive token expired: ${error.take(120)}")
            }
            if (code !in 200..299 && code != HttpURLConnection.HTTP_NO_CONTENT) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                error("Drive PATCH failed with HTTP $code ${error.take(180)}")
            }
            connection.disconnect()
        }
    }

    private fun findFileId(accessToken: String, fileName: String): String? {
        val query = "name='$fileName' and trashed=false"
        val url = Uri.Builder()
            .scheme("https")
            .authority("www.googleapis.com")
            .path("drive/v3/files")
            .appendQueryParameter("spaces", "appDataFolder")
            .appendQueryParameter("q", query)
            .appendQueryParameter("fields", "files(id,name)")
            .build()
            .toString()
        val connection = openConnection(url, "GET", accessToken)
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            throw CloudAuthExpiredException("Google Drive token expired: ${error.take(120)}")
        }
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            error("Drive LIST failed with HTTP $code ${error.take(180)}")
        }
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val files = JSONObject(text).optJSONArray("files") ?: JSONArray()
        return files.optJSONObject(0)?.optString("id").orEmpty().ifBlank { null }
    }

    private fun createFile(accessToken: String, fileName: String): String {
        val connection = openConnection(
            url = "https://www.googleapis.com/drive/v3/files?fields=id",
            method = "POST",
            accessToken = accessToken,
        )
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val body = JSONObject()
            .put("name", fileName)
            .put("parents", JSONArray().put("appDataFolder"))
            .toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            throw CloudAuthExpiredException("Google Drive token expired: ${error.take(120)}")
        }
        if (code !in 200..299 && code != HttpURLConnection.HTTP_CREATED) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            error("Drive CREATE failed with HTTP $code ${error.take(180)}")
        }
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return JSONObject(text).optString("id").ifBlank { throw IOException("Drive create file missing id") }
    }

    private fun openConnection(url: String, method: String, accessToken: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as? HttpURLConnection)
            ?: throw IOException("Unable to open Google Drive connection")
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        return connection
    }
}
