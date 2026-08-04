package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.wear.timetable.account.WearDirectAccountSessionManager
import com.classing.wear.timetable.account.WearQrAuthApiClient
import com.classing.wear.timetable.security.ClientIntegrity
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A foreground-only SSE connection. The stream carries versions, never the timetable document. */
class WearOfficialCloudEventClient(
    private val baseUrl: String = WearQrAuthApiClient.BASE_URL,
    context: Context? = null,
) {
    private val appContext = context?.applicationContext

    suspend fun listen(
        accessToken: String,
        lastEventId: Long,
        shouldContinue: () -> Boolean = { true },
        onDocumentVersion: suspend (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        appContext?.let { ClientIntegrity.ensureTrusted(it, baseUrl).getOrThrow() }
        val connection = (URL("$baseUrl/api/v1/cloud/official/events").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 35_000
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "Classing-WearOS")
            appContext?.let { ClientIntegrity.applyHeaders(this, it) }
            if (lastEventId > 0L) setRequestProperty("Last-Event-ID", lastEventId.toString())
        }
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { connection.disconnect() }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val error = runCatching { JSONObject(body) }.getOrNull()
                throw WearOfficialCloudHttpException(
                    statusCode = status,
                    errorCode = error?.optString("code").orEmpty(),
                    retryAfterSeconds = connection.getHeaderField("Retry-After")?.toIntOrNull() ?: 0,
                    message = error?.optString("message").orEmpty().ifBlank { "SSE HTTP $status" },
                )
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var eventName = "message"
                var eventId = ""
                val data = StringBuilder()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (!shouldContinue()) return@use
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) {
                        if (eventName == "cloud-document") {
                            val payloadVersion = runCatching { JSONObject(data.toString()).optLong("version") }.getOrDefault(0L)
                            val version = payloadVersion.takeIf { it > 0L } ?: eventId.toLongOrNull().orEmptyVersion()
                            if (version > 0L) onDocumentVersion(version)
                        }
                        eventName = "message"
                        eventId = ""
                        data.setLength(0)
                    } else when {
                        line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                        line.startsWith("id:") -> eventId = line.substringAfter(':').trim()
                        line.startsWith("data:") -> data.append(line.substringAfter(':').trim())
                    }
                }
            }
        } finally {
            cancellation?.dispose()
            connection.disconnect()
        }
    }

    private fun Long?.orEmptyVersion(): Long = this ?: 0L
}

class WearOfficialCloudEventSubscriber(
    context: Context,
    private val coordinator: WearOfficialCloudSyncCoordinator,
    private val eventClient: WearOfficialCloudEventClient = WearOfficialCloudEventClient(context),
    private val authApiClient: WearQrAuthApiClient = WearQrAuthApiClient(),
) {
    private val appContext = context.applicationContext

    suspend fun runWhileIndependent() {
        var retryDelay = 1_000L
        while (WearSyncModeStore.isIndependentModeEnabled(appContext)) {
            currentCoroutineContext().ensureActive()
            val session = WearDirectAccountSessionManager.ensureSession(appContext, authApiClient)
            if (session == null) {
                WearSyncModeStore.setIndependentMode(appContext, false)
                return
            }
            val result = runCatching {
                eventClient.listen(
                    accessToken = session.accessToken,
                    lastEventId = coordinator.lastDocumentVersion(),
                    shouldContinue = { WearSyncModeStore.isIndependentModeEnabled(appContext) },
                ) { announcedVersion ->
                    if (announcedVersion <= coordinator.lastDocumentVersion()) return@listen
                    coordinator.sync(TRIGGER_SSE).getOrThrow()
                    retryDelay = 1_000L
                }
            }
            val error = result.exceptionOrNull()
            if (error is WearOfficialCloudHttpException && error.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                WearDirectAccountSessionManager.refreshAfterUnauthorized(
                    context = appContext,
                    rejectedAccessToken = session.accessToken,
                    apiClient = authApiClient,
                )
            }
            currentCoroutineContext().ensureActive()
            if (WearSyncModeStore.isIndependentModeEnabled(appContext)) {
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
            }
        }
    }

    private companion object {
        const val TRIGGER_SSE = "official_sse"
    }
}
