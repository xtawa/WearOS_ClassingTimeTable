package com.xtawa.classingtime.sync

import java.net.HttpURLConnection
import java.net.URL

class OfficialCloudHttpClient {
    fun testConnection(config: CloudRuntimeConfig): Result<Unit> {
        return request(config, "GET", "/api/v1/cloud/official/ping", null, null).map { Unit }
    }

    fun readJson(config: CloudRuntimeConfig): Result<CloudReadResult> {
        return request(config, "GET", "/api/v1/cloud/official/document", null, null).map { response ->
            CloudReadResult(
                payload = response.body.ifBlank { null },
                versionToken = response.versionToken,
            )
        }
    }

    fun writeJson(config: CloudRuntimeConfig, payload: String, expectedVersion: String?): Result<Unit> {
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

    private fun request(
        config: CloudRuntimeConfig,
        method: String,
        path: String,
        payload: String?,
        expectedVersion: String?,
    ): Result<Response> {
        return runCatching {
            val connection = (URL(config.serverUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Authorization", "Bearer ${config.accountAccessToken}")
                if (!expectedVersion.isNullOrBlank()) {
                    setRequestProperty("If-Match", expectedVersion)
                }
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
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
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw CloudAuthExpiredException("Official cloud authorization expired")
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
}
