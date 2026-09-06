package com.xtawa.classingtime.sync

import android.content.Context
import com.xtawa.classingtime.security.ClientIntegrity
import com.classing.shared.sync.CloudProvider
import com.classing.shared.sync.CloudSyncContracts
import com.xtawa.classingtime.account.AccountApiClient
import com.xtawa.classingtime.data.MobilePrefsStore
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

data class OfficialCloudEvent(
    val version: Long,
    val updatedAt: Long,
)

class OfficialCloudEventClient {
    suspend fun listen(
        context: Context,
        baseUrl: String,
        accessToken: String,
        lastVersion: Long,
        onEvent: (OfficialCloudEvent) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ClientIntegrity.ensureTrusted(context, baseUrl).getOrThrow()
            val connection = (URL(baseUrl.trimEnd('/') + EVENTS_PATH).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 35_000
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Authorization", "Bearer $accessToken")
                ClientIntegrity.applyHeaders(this, context)
                if (lastVersion > 0L) setRequestProperty("Last-Event-ID", lastVersion.toString())
            }
            val completion = coroutineContext[Job]?.invokeOnCompletion { connection.disconnect() }
            try {
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> throw CloudAuthExpiredException("Official cloud event authorization expired")
                    HttpURLConnection.HTTP_FORBIDDEN -> throw CloudPermissionDeniedException("Official cloud event permission denied")
                    in 200..299 -> Unit
                    else -> error("Official cloud events HTTP $status")
                }
                val block = StringBuilder()
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) {
                            parseOfficialCloudEvent(block.toString())?.let(onEvent)
                            block.setLength(0)
                        } else if (!line.startsWith(':')) {
                            block.append(line).append('\n')
                        }
                    }
                }
            } finally {
                completion?.dispose()
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val EVENTS_PATH = "/api/v1/cloud/official/events"
    }
}

internal fun parseOfficialCloudEvent(block: String): OfficialCloudEvent? {
    var eventName = "message"
    var eventId = ""
    val data = StringBuilder()
    block.lineSequence().forEach { line ->
        when {
            line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
            line.startsWith("id:") -> eventId = line.substringAfter(':').trim()
            line.startsWith("data:") -> data.append(line.substringAfter(':').trim())
        }
    }
    if (eventName != "cloud-document") return null
    val payload = data.toString()
    val version = Regex("\"version\"\\s*:\\s*(\\d+)")
        .find(payload)?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: eventId.toLongOrNull()
        ?: -1L
    if (version < 0L) return null
    val updatedAt = Regex("\"updatedAt\"\\s*:\\s*(\\d+)")
        .find(payload)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    return OfficialCloudEvent(version = version, updatedAt = updatedAt)
}

object OfficialCloudRealtimeController {
    private val eventClient = OfficialCloudEventClient()

    suspend fun run(
        context: Context,
        onCloudApplied: suspend () -> Unit,
    ) = supervisorScope {
        val appContext = context.applicationContext
        suspend fun syncNow(trigger: String, targetVersion: Long? = null): Boolean {
            val outcome = MobileCloudSyncCoordinator.requestCloudSync(
                context = appContext,
                trigger = trigger,
                force = true,
                alsoPushConfigToWear = true,
            ).getOrNull()
            if (outcome?.success != true) return false
            targetVersion?.let { MobilePrefsStore.saveOfficialCloudEventVersion(appContext, it) }
            onCloudApplied()
            return true
        }

        syncNow(CloudSyncContracts.TRIGGER_FOREGROUND_TICK)
        val events = Channel<Long>(Channel.CONFLATED)
        val streamJob = launch {
            try {
                var failureCount = 0
                while (currentCoroutineContext().isActive) {
                    val settings = MobilePrefsStore.loadSettings(appContext)
                    if (!settings.cloudSyncEnabled || CloudProvider.fromWire(settings.cloudProvider) != CloudProvider.OFFICIAL) break
                    val userId = settings.accountSummary.userId
                    if (userId.isBlank()) break
                    val accessToken = AccountSessionManager.ensureAccessToken(appContext).orEmpty()
                    if (accessToken.isBlank()) break
                    val cursor = MobilePrefsStore.loadOfficialCloudEventVersion(appContext, userId)
                    val result = eventClient.listen(
                        context = appContext,
                        baseUrl = AccountApiClient.BASE_URL,
                        accessToken = accessToken,
                        lastVersion = cursor,
                    ) { event ->
                        failureCount = 0
                        if (event.version > MobilePrefsStore.loadOfficialCloudEventVersion(appContext, userId)) {
                            events.trySend(event.version)
                        }
                    }
                    val error = result.exceptionOrNull()
                    if (error is CloudAuthExpiredException) {
                        AccountSessionManager.refreshAfterUnauthorized(appContext, accessToken)
                    }
                    failureCount = (failureCount + 1).coerceAtMost(5)
                    val baseDelay = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)[failureCount - 1]
                    delay(baseDelay + Random.nextLong(0L, 350L))
                }
            } finally {
                events.close()
            }
        }
        try {
            events.receiveAsFlow().collect { firstVersion ->
                delay(400L)
                var targetVersion = firstVersion
                while (true) {
                    val newer = events.tryReceive().getOrNull() ?: break
                    targetVersion = maxOf(targetVersion, newer)
                }
                if (!syncNow(CloudSyncContracts.TRIGGER_FOREGROUND_TICK, targetVersion)) {
                    CloudSyncEngine.enqueue(appContext, CloudSyncContracts.TRIGGER_FOREGROUND_TICK, markDirty = false)
                }
            }
        } finally {
            streamJob.cancel()
            events.close()
        }
    }
}
