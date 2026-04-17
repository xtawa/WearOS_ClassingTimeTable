package com.classing.wear.timetable.sync

import android.net.Uri
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WearCloudAuthExpiredException(message: String) : IOException(message)

class WearGoogleDriveClient {
    suspend fun readJson(config: WearCloudConfig): Result<String?> = withContext(Dispatchers.IO) {
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
                throw WearCloudAuthExpiredException("Google Drive token expired: ${error.take(120)}")
            }
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                error("Drive GET failed HTTP $code ${error.take(180)}")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            text
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
            throw WearCloudAuthExpiredException("Google Drive token expired: ${error.take(120)}")
        }
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            error("Drive LIST failed HTTP $code ${error.take(180)}")
        }
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val files = JSONObject(text).optJSONArray("files") ?: JSONArray()
        return files.optJSONObject(0)?.optString("id").orEmpty().ifBlank { null }
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
