package com.xtawa.classingtime.sync

import android.util.Base64
import com.classing.shared.sync.CloudSyncContracts
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class WebDavHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .build(),
) {
    private val resolvedDocumentUrls = ConcurrentHashMap<String, HttpUrl>()

    suspend fun testConnection(config: CloudRuntimeConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = resolveServerUrl(config.serverUrl)
            val probe = propfind(config, baseUrl, RESOURCETYPE_PROPFIND_BODY)
            when {
                probe.code in 200..299 || probe.code == HTTP_MULTI_STATUS -> Unit
                probe.code == 401 || probe.code == 403 -> throw CloudPermissionDeniedException(
                    "WebDAV authentication or permission denied (HTTP ${probe.code})",
                )
                probe.code == 404 -> error("WebDAV endpoint not found (HTTP 404)")
                probe.code == 405 || probe.code == 501 -> Unit // Some minimal WebDAV gateways do not expose PROPFIND on the root.
                else -> error(responseError("WebDAV probe failed", probe))
            }
            readJson(config).getOrThrow()
            Unit
        }
    }

    suspend fun readJson(config: CloudRuntimeConfig): Result<CloudReadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val key = targetCacheKey(config)
            var target = resolvedDocumentUrls[key] ?: resolveTargetUrl(config.serverUrl, config.remotePath)
            var response = get(config, target)

            if (response.code == 405 && target.pathSegments.lastOrNull() != DEFAULT_DOCUMENT_NAME) {
                val collectionChild = appendDefaultDocumentName(target)
                val fallback = get(config, collectionChild)
                if (fallback.code == 404 || fallback.code in 200..299) {
                    target = collectionChild
                    response = fallback
                    resolvedDocumentUrls[key] = collectionChild
                }
            }

            if (response.code == 404) {
                resolvedDocumentUrls[key] = target
                return@runCatching CloudReadResult(null, null)
            }
            if (response.code == 401 || response.code == 403) {
                throw CloudPermissionDeniedException(
                    "WebDAV GET permission denied (HTTP ${response.code})${response.detailSuffix()}",
                )
            }
            if (response.code !in 200..299) {
                error(responseError("GET failed", response))
            }

            resolvedDocumentUrls[key] = target
            val etag = response.etag ?: fetchEtag(config, target)
            if (etag.isNullOrBlank()) {
                throw UnsafeCloudStorageException(
                    "WebDAV server does not provide ETag via GET or PROPFIND; safe multi-device writes are unavailable",
                )
            }
            CloudReadResult(response.body, etag)
        }
    }

    suspend fun writeJson(
        config: CloudRuntimeConfig,
        payload: String,
        expectedVersion: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val key = targetCacheKey(config)
            var target = resolvedDocumentUrls[key] ?: resolveTargetUrl(config.serverUrl, config.remotePath)
            if (expectedVersion == null) {
                ensureParentCollections(config, target)
            }

            var response = put(config, target, payload, expectedVersion)
            if (response.code == 405 && target.pathSegments.lastOrNull() != DEFAULT_DOCUMENT_NAME) {
                target = appendDefaultDocumentName(target)
                resolvedDocumentUrls[key] = target
                if (expectedVersion == null) {
                    ensureParentCollections(config, target)
                }
                response = put(config, target, payload, expectedVersion)
            }

            when {
                response.code == 401 || response.code == 403 -> throw CloudPermissionDeniedException(
                    "WebDAV PUT permission denied (HTTP ${response.code})${response.detailSuffix()}",
                )
                response.code == 412 || response.code == 409 -> throw CloudWriteConflictException()
                response.code in 200..299 -> {
                    resolvedDocumentUrls[key] = target
                    Unit
                }
                else -> error(responseError("PUT failed", response))
            }
        }
    }

    private fun ensureParentCollections(config: CloudRuntimeConfig, target: HttpUrl) {
        val base = resolveServerUrl(config.serverUrl)
        val baseSegments = base.encodedPathSegments.filter { it.isNotBlank() }
        val targetSegments = target.encodedPathSegments.filter { it.isNotBlank() }
        if (targetSegments.size <= 1 || targetSegments.size <= baseSegments.size) return
        if (targetSegments.take(baseSegments.size) != baseSegments) return

        val parentSegments = targetSegments.dropLast(1)
        for (segmentCount in (baseSegments.size + 1)..parentSegments.size) {
            val path = "/${parentSegments.take(segmentCount).joinToString("/")}/"
            val collectionUrl = target.newBuilder().encodedPath(path).build()
            ensureCollection(config, collectionUrl)
        }
    }

    private fun ensureCollection(config: CloudRuntimeConfig, url: HttpUrl) {
        val probe = propfind(config, url, RESOURCETYPE_PROPFIND_BODY)
        when {
            probe.code in 200..299 || probe.code == HTTP_MULTI_STATUS -> return
            probe.code == 401 || probe.code == 403 -> throw CloudPermissionDeniedException(
                "WebDAV cannot access parent collection (HTTP ${probe.code})${probe.detailSuffix()}",
            )
            probe.code != 404 && probe.code != 405 && probe.code != 501 -> {
                error(responseError("PROPFIND failed", probe))
            }
        }

        val create = mkcol(config, url)
        when {
            create.code in 200..299 -> return
            create.code == 405 -> return // RFC 4918: MKCOL on an existing collection commonly returns 405.
            create.code == 401 || create.code == 403 -> throw CloudPermissionDeniedException(
                "WebDAV cannot create parent collection (HTTP ${create.code})${create.detailSuffix()}",
            )
            else -> error(responseError("MKCOL failed", create))
        }
    }

    private fun fetchEtag(config: CloudRuntimeConfig, url: HttpUrl): String? {
        val response = propfind(config, url, ETAG_PROPFIND_BODY)
        if (response.code != HTTP_MULTI_STATUS && response.code !in 200..299) return null
        return ETAG_TAG_REGEX.find(response.body)?.groupValues?.getOrNull(1)
            ?.replace("&quot;", "\"")
            ?.replace("&amp;", "&")
            ?.trim()
            ?.ifBlank { null }
    }

    private fun get(config: CloudRuntimeConfig, url: HttpUrl): WebDavResponse {
        val request = requestBuilder(config, url).get().build()
        return execute(request)
    }

    private fun put(
        config: CloudRuntimeConfig,
        url: HttpUrl,
        payload: String,
        expectedVersion: String?,
    ): WebDavResponse {
        val builder = requestBuilder(config, url)
            .put(payload.toRequestBody(JSON_MEDIA_TYPE))
        if (expectedVersion == null) {
            builder.header("If-None-Match", "*")
        } else {
            builder.header("If-Match", expectedVersion)
        }
        return execute(builder.build())
    }

    private fun propfind(config: CloudRuntimeConfig, url: HttpUrl, xmlBody: String): WebDavResponse {
        val request = requestBuilder(config, url)
            .header("Depth", "0")
            .method("PROPFIND", xmlBody.toRequestBody(XML_MEDIA_TYPE))
            .build()
        return execute(request)
    }

    private fun mkcol(config: CloudRuntimeConfig, url: HttpUrl): WebDavResponse {
        val request = requestBuilder(config, url)
            .method("MKCOL", null)
            .build()
        return execute(request)
    }

    private fun requestBuilder(config: CloudRuntimeConfig, url: HttpUrl): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Authorization", buildBasicAuth(config.username, config.password))
            .header("Accept", "application/json, application/xml;q=0.9, */*;q=0.8")
    }

    private fun execute(request: Request): WebDavResponse {
        return client.newCall(request).execute().use { response ->
            WebDavResponse(
                code = response.code,
                body = response.body?.string().orEmpty(),
                etag = response.header("ETag")?.trim()?.ifBlank { null },
            )
        }
    }

    private fun resolveServerUrl(serverUrl: String): HttpUrl {
        return serverUrl.trim().trimEnd('/').toHttpUrl()
    }

    private fun resolveTargetUrl(serverUrl: String, remotePath: String): HttpUrl {
        val server = serverUrl.trim().trimEnd('/')
        var path = remotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH }
        if (!path.startsWith('/')) path = "/$path"
        if (path.endsWith('/')) path += DEFAULT_DOCUMENT_NAME
        return "$server$path".toHttpUrl()
    }

    private fun appendDefaultDocumentName(url: HttpUrl): HttpUrl {
        return url.newBuilder().addPathSegment(DEFAULT_DOCUMENT_NAME).build()
    }

    private fun targetCacheKey(config: CloudRuntimeConfig): String {
        return buildString {
            append(config.serverUrl.trim().trimEnd('/'))
            append('\n')
            append(config.remotePath.trim())
            append('\n')
            append(config.username)
        }
    }

    private fun responseError(prefix: String, response: WebDavResponse): String {
        return "$prefix with HTTP ${response.code}${response.detailSuffix()}"
    }

    private fun WebDavResponse.detailSuffix(): String {
        val detail = body
            .replace(MARKUP_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .take(180)
        return if (detail.isBlank()) "" else " $detail"
    }

    private data class WebDavResponse(
        val code: Int,
        val body: String,
        val etag: String?,
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val HTTP_MULTI_STATUS = 207
        private val DEFAULT_DOCUMENT_NAME = CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val MARKUP_REGEX = Regex("<[^>]+>")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val ETAG_TAG_REGEX = Regex(
            "<[^>]*getetag[^>]*>(.*?)</[^>]*getetag\\s*>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private const val RESOURCETYPE_PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>"
        private const val ETAG_PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:getetag/></d:prop></d:propfind>"

        fun buildBasicAuth(username: String, password: String): String {
            val encoded = Base64.encodeToString("$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "Basic $encoded"
        }
    }
}
