package com.xtawa.classingtime.sync

import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfficialCloudHttpClient {
    suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> {
        return request(config, "GET", "/api/v1/cloud/official/ping", null, null).map { Unit }
    }

    suspend fun readJson(config: CloudRuntimeConfig): Result<CloudReadResult> {
        return request(config, "GET", "/api/v1/cloud/official/document", null, null).map { response ->
            CloudReadResult(
                payload = response.body.ifBlank { null },
                versionToken = response.versionToken,
            )
        }
    }

    suspend fun writeJson(config: CloudRuntimeConfig, payload: String, expectedVersion: String?): Result<Unit> {
        return request(
            config = config,
            method = "PUT",
            path = "/api/v1/cloud/official/document",
            payload = payload,
            expectedVersion = expectedVersion,
        ).map { Unit }
    }

    private data class Response(
        val body: String,
        val versionToken: String?,
    )

    private suspend fun request(
        config: CloudRuntimeConfig,
        method: String,
        path: String,
        payload: String?,
        expectedVersion: String?,
    ): Result<Response> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(config.serverUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Authorization", "Bearer ${config.accountAccessToken}")
				if (payload != null) {
					val version = expectedVersion?.trim()?.trim('"').orEmpty().ifBlank { "0" }
					setRequestProperty("If-Match", "\"$version\"")
				}
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
                }
            }
            try {
                if (payload != null) {
                    connection.outputStream.use { output ->
                        output.write(payload.toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (status == HttpURLConnection.HTTP_PRECON_FAILED || status == HttpURLConnection.HTTP_CONFLICT) {
                    throw CloudWriteConflictException()
                }
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw CloudAuthExpiredException("Official cloud authorization expired")
                }
                if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw CloudPermissionDeniedException("Official cloud permission denied")
                }
                if (status == 429) {
                    val errorCode = parseErrorCode(body)
                    val retryAfter = connection.getHeaderField("Retry-After")?.toIntOrNull()?.coerceAtLeast(1) ?: 60
                    throw CloudRateLimitedException(
                        retryAfterSeconds = retryAfter,
                        errorCode = errorCode,
                        message = "Official cloud rate limited; retry after ${retryAfter}s",
                    )
                }
                if (status !in 200..299) {
                    error("HTTP $status ${body.take(160)}".trim())
                }
                Response(
                    body = body.trim(),
                    versionToken = connection.getHeaderField("ETag")?.trim('"'),
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseErrorCode(body: String): String = runCatching {
        org.json.JSONObject(body).optString("code")
    }.getOrDefault("")
}
