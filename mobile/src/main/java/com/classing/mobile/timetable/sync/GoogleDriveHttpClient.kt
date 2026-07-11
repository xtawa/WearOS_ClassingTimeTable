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

private data class DriveFileMetadata(
    val id: String,
    val version: String,
)

class GoogleDriveHttpClient {
    suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            readJson(config).getOrThrow()
            Unit
        }
    }

    suspend fun readJson(config: CloudRuntimeConfig): Result<CloudReadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = config.driveFileName.ifBlank { "classing_sync.json" }
            val file = findFileMetadata(config.driveAccessToken, fileName) ?: return@runCatching CloudReadResult(null, null)
            val connection = openConnection(
                url = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media",
                method = "GET",
                accessToken = config.driveAccessToken,
            )
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                connection.disconnect()
                return@runCatching CloudReadResult(null, null)
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
            CloudReadResult(payload, encodeVersion(file.id, file.version))
        }
    }

    suspend fun writeJson(config: CloudRuntimeConfig, payload: String, expectedVersion: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = config.driveFileName.ifBlank { "classing_sync.json" }
            val accessToken = config.driveAccessToken
            if (expectedVersion == null) {
                if (findFileMetadata(accessToken, fileName) != null) throw CloudWriteConflictException()
                createFileWithContent(accessToken, fileName, payload)
                return@runCatching
            }
            val (fileId, expectedDriveVersion) = decodeVersion(expectedVersion)
            val current = getFileMetadata(accessToken, fileId) ?: throw CloudWriteConflictException()
            if (current.version != expectedDriveVersion) {
                throw CloudWriteConflictException()
            }
            val connection = openConnection(
                url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
                method = "PATCH",
                accessToken = accessToken,
            )
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_PRECON_FAILED || code == HttpURLConnection.HTTP_CONFLICT) {
                connection.disconnect()
                throw CloudWriteConflictException()
            }
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

    private fun findFileMetadata(accessToken: String, fileName: String): DriveFileMetadata? {
        val escapedName = fileName.replace("'", "\\'")
        val query = "name='$escapedName' and trashed=false"
        val url = Uri.Builder()
            .scheme("https")
            .authority("www.googleapis.com")
            .path("drive/v3/files")
            .appendQueryParameter("spaces", "appDataFolder")
            .appendQueryParameter("q", query)
            .appendQueryParameter("fields", "files(id,name,version)")
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
        return buildList {
            for (index in 0 until files.length()) {
                val item = files.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val version = item.optString("version")
                if (id.isNotBlank() && version.isNotBlank()) {
                    add(DriveFileMetadata(id, version))
                }
            }
        }.minByOrNull { it.id }
    }

    private fun getFileMetadata(accessToken: String, fileId: String): DriveFileMetadata? {
        val url = Uri.Builder()
            .scheme("https")
            .authority("www.googleapis.com")
            .path("drive/v3/files/$fileId")
            .appendQueryParameter("fields", "id,version")
            .build()
            .toString()
        val connection = openConnection(url, "GET", accessToken)
        val code = connection.responseCode
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            connection.disconnect()
            return null
        }
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            throw CloudAuthExpiredException("Google Drive token expired: ${error.take(120)}")
        }
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            error("Drive METADATA failed with HTTP $code ${error.take(180)}")
        }
        val item = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        connection.disconnect()
        val id = item.optString("id")
        val version = item.optString("version")
        if (id.isBlank() || version.isBlank()) {
            throw UnsafeCloudStorageException("Google Drive did not provide file version metadata")
        }
        return DriveFileMetadata(id, version)
    }

    private fun createFileWithContent(accessToken: String, fileName: String, payload: String) {
        val boundary = "classing-${System.currentTimeMillis()}"
        val connection = openConnection(
            url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
            method = "POST",
            accessToken = accessToken,
        )
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        val metadata = JSONObject()
            .put("name", fileName)
            .put("parents", JSONArray().put("appDataFolder"))
            .toString()
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(payload)
            append("\r\n--$boundary--\r\n")
        }
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
        connection.inputStream.close()
        connection.disconnect()
    }

    private fun encodeVersion(fileId: String, etag: String): String = "$fileId\n$etag"

    private fun decodeVersion(raw: String): Pair<String, String> {
        val separator = raw.indexOf('\n')
        if (separator <= 0 || separator == raw.lastIndex) throw UnsafeCloudStorageException("Invalid Drive version token")
        return raw.substring(0, separator) to raw.substring(separator + 1)
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
