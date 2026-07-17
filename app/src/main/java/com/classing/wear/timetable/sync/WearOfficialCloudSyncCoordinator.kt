package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.CloudSyncV2
import com.classing.wear.timetable.account.WearDirectAccountSessionManager
import com.classing.wear.timetable.account.WearQrAuthApiClient
import com.classing.wear.timetable.domain.repository.SettingsRepository
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class WearOfficialCloudSyncOutcome(
    val syncedAt: Long,
    val uploaded: Boolean,
    val appliedRemoteSettings: Boolean,
)

class WearOfficialCloudLoginRequiredException : IllegalStateException("Wear official cloud requires login")

class WearOfficialCloudHttpException(
    val statusCode: Int,
    val errorCode: String,
    val retryAfterSeconds: Int = 0,
    message: String,
) : IllegalStateException(message)

class WearOfficialCloudConflictException : IllegalStateException("Official cloud document changed")

data class WearOfficialCloudReadResult(
    val payload: String,
    val versionToken: String,
)

class WearOfficialCloudHttpClient(
    private val baseUrl: String = WearQrAuthApiClient.BASE_URL,
) {
    suspend fun read(accessToken: String): Result<WearOfficialCloudReadResult> = request(
        method = "GET",
        path = "/api/v1/cloud/official/document",
        accessToken = accessToken,
    ).map { response ->
        WearOfficialCloudReadResult(
            payload = response.body.ifBlank { EMPTY_DOCUMENT },
            versionToken = response.etag.ifBlank { "0" },
        )
    }

    suspend fun write(
        accessToken: String,
        payload: String,
        expectedVersion: String,
    ): Result<Unit> = request(
        method = "PUT",
        path = "/api/v1/cloud/official/document",
        accessToken = accessToken,
        payload = payload,
        expectedVersion = expectedVersion,
    ).map { Unit }

    private data class Response(val body: String, val etag: String)

    private suspend fun request(
        method: String,
        path: String,
        accessToken: String,
        payload: String? = null,
        expectedVersion: String? = null,
    ): Result<Response> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("User-Agent", "Classing-WearOS")
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("If-Match", "\"${expectedVersion.trimEtag()}\"")
                    setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
                }
            }
            try {
                if (payload != null) {
                    connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                    .trim()
                if (status == HttpURLConnection.HTTP_CONFLICT || status == HttpURLConnection.HTTP_PRECON_FAILED) {
                    throw WearOfficialCloudConflictException()
                }
                if (status !in 200..299) {
                    val error = runCatching { JSONObject(body) }.getOrNull()
                    val retryAfter = connection.getHeaderField("Retry-After")?.toIntOrNull()?.coerceAtLeast(1) ?: 0
                    throw WearOfficialCloudHttpException(
                        statusCode = status,
                        errorCode = error?.optString("code").orEmpty(),
                        retryAfterSeconds = retryAfter,
                        message = error?.optString("message").orEmpty().ifBlank { "HTTP $status" },
                    )
                }
                Response(body, connection.getHeaderField("ETag").orEmpty().trimEtag())
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun String?.trimEtag(): String = this.orEmpty().trim().trim('"').ifBlank { "0" }

    private companion object {
        const val EMPTY_DOCUMENT =
            "{\"format\":\"classing_cloud_sync_v2\",\"updatedAt\":0,\"records\":{},\"changes\":[],\"devices\":[]}"
    }
}

class WearOfficialCloudSyncCoordinator(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val httpClient: WearOfficialCloudHttpClient = WearOfficialCloudHttpClient(),
    private val authApiClient: WearQrAuthApiClient = WearQrAuthApiClient(),
) {
    private val appContext = context.applicationContext
    private val state = WearOfficialCloudState(appContext)
    private val mutex = Mutex()

    suspend fun sync(trigger: String): Result<WearOfficialCloudSyncOutcome> = mutex.withLock {
        runCatching {
            var lastConflict: Throwable? = null
            repeat(MAX_CAS_ATTEMPTS) { attempt ->
                try {
                    return@runCatching syncOnce(trigger)
                } catch (error: WearOfficialCloudConflictException) {
                    lastConflict = error
                    delay(CAS_BACKOFF_MS * (attempt + 1L))
                } catch (error: WearLocalCloudStateChangedException) {
                    lastConflict = error
                }
            }
            throw lastConflict ?: WearOfficialCloudConflictException()
        }.onSuccess { outcome ->
            state.saveSuccess(outcome.syncedAt)
        }.onFailure { error ->
            state.saveFailure(error.message.orEmpty().ifBlank { error.javaClass.simpleName })
        }
    }

    private suspend fun syncOnce(trigger: String): WearOfficialCloudSyncOutcome {
        val authenticatedRead = withAuthenticatedSession { token -> httpClient.read(token) }
        state.ensureAccount(authenticatedRead.session.userId)
        val read = authenticatedRead.value
        val root = JSONObject(read.payload)
        require(root.optString("format") == CloudSyncV2.DOCUMENT_FORMAT) {
            "Unsupported official cloud document format"
        }
        val localBefore = JSONObject(settingsRepository.exportWearSettingsSnapshot())
        val baseline = state.loadBaseline()
        val merge = mergeWearSettings(root, localBefore, baseline, trigger)

        if (merge.uploaded) {
            withAuthenticatedSession { token ->
                httpClient.write(token, merge.document.toString(), read.versionToken)
            }
        }

        val localAfterNetwork = JSONObject(settingsRepository.exportWearSettingsSnapshot())
        if (!jsonObjectsEqual(localBefore, localAfterNetwork)) {
            // The observed remote snapshot is the correct comparison base for the retry.
            // Without this, a setting changed during the very first sync could be mistaken
            // for an untouched default and then overwritten by the remote value.
            state.saveBaseline(merge.resolvedSettings)
            throw WearLocalCloudStateChangedException()
        }
        settingsRepository.applyWearSettingsSnapshot(merge.resolvedSettings.toString())
        state.saveBaseline(merge.resolvedSettings)
        return WearOfficialCloudSyncOutcome(
            syncedAt = System.currentTimeMillis(),
            uploaded = merge.uploaded,
            appliedRemoteSettings = merge.appliedRemoteSettings,
        )
    }

    private data class AuthenticatedResult<T>(
        val session: com.classing.wear.timetable.account.WearDirectAccountSession,
        val value: T,
    )

    private suspend fun <T> withAuthenticatedSession(
        request: suspend (String) -> Result<T>,
    ): AuthenticatedResult<T> {
        var session = WearDirectAccountSessionManager.ensureSession(appContext, authApiClient)
            ?: throw WearOfficialCloudLoginRequiredException()
        val first = request(session.accessToken)
        if (first.isSuccess) return AuthenticatedResult(session, first.getOrThrow())
        val firstError = first.exceptionOrNull()
        if (firstError !is WearOfficialCloudHttpException ||
            firstError.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED
        ) {
            throw firstError ?: IllegalStateException("Official cloud request failed")
        }
        session = WearDirectAccountSessionManager.refreshAfterUnauthorized(
            context = appContext,
            rejectedAccessToken = session.accessToken,
            apiClient = authApiClient,
        ) ?: throw WearOfficialCloudLoginRequiredException()
        return AuthenticatedResult(session, request(session.accessToken).getOrElse { throw it })
    }

    private data class MergeResult(
        val document: JSONObject,
        val resolvedSettings: JSONObject,
        val uploaded: Boolean,
        val appliedRemoteSettings: Boolean,
    )

    private fun mergeWearSettings(
        document: JSONObject,
        local: JSONObject,
        baseline: JSONObject?,
        trigger: String,
    ): MergeResult {
        val recordsRoot = document.optJSONObject("records") ?: JSONObject().also { document.put("records", it) }
        val records = latestRecords(recordsRoot.optJSONArray(CloudSyncV2.DOMAIN_WEAR_SETTINGS) ?: JSONArray())
        val resolved = JSONObject(local.toString())
        var uploaded = false
        var appliedRemote = false
        val now = System.currentTimeMillis()
        val maxRemoteCounter = records.values.maxOfOrNull(CloudSettingRecord::counter) ?: 0L
        val changes = document.optJSONArray("changes") ?: JSONArray().also { document.put("changes", it) }

        val keys = local.keys().asSequence().toList()
        keys.forEach { key ->
            val localValue = local.opt(key)
            val baselineHasValue = baseline?.has(key) == true
            val baselineValue = baseline?.opt(key)
            val remote = records[key]?.takeUnless { it.deleted }
            val localChanged = baselineHasValue && !jsonValuesEqual(localValue, baselineValue)

            when {
                localChanged || remote == null -> {
                    val version = state.nextVersion(maxRemoteCounter)
                    records[key] = CloudSettingRecord(
                        json = JSONObject()
                            .put("id", key)
                            .put("payload", valuePayload(localValue))
                            .put("version", version.toJson())
                            .put("deletedAt", JSONObject.NULL)
                            .put("recoverableUntil", JSONObject.NULL),
                        value = localValue,
                        counter = version.counter,
                        deviceId = version.deviceId,
                        deleted = false,
                    )
                    changes.put(
                        JSONObject()
                            .put("id", "${version.deviceId}:${version.counter}:${CloudSyncV2.DOMAIN_WEAR_SETTINGS}:$key")
                            .put("domain", CloudSyncV2.DOMAIN_WEAR_SETTINGS)
                            .put("recordId", key)
                            .put("action", if (remote == null) "upserted" else "updated")
                            .put("version", version.toJson())
                            .put("occurredAt", now)
                            .put("detail", "Wear direct sync $trigger"),
                    )
                    uploaded = true
                }
                else -> {
                    if (!jsonValuesEqual(localValue, remote.value)) appliedRemote = true
                    resolved.put(key, remote.value ?: JSONObject.NULL)
                }
            }
        }

        if (uploaded) {
            val array = JSONArray()
            records.toSortedMap().values.forEach { array.put(it.json) }
            recordsRoot.put(CloudSyncV2.DOMAIN_WEAR_SETTINGS, array)
            document.put("updatedAt", maxOf(document.optLong("updatedAt"), now))
            compactChanges(document)
            updateDeviceMetadata(document, now)
        }
        return MergeResult(document, resolved, uploaded, appliedRemote)
    }

    private fun latestRecords(array: JSONArray): MutableMap<String, CloudSettingRecord> {
        val records = mutableMapOf<String, CloudSettingRecord>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val version = item.optJSONObject("version") ?: JSONObject()
            val candidate = CloudSettingRecord(
                json = item,
                value = item.optString("payload").takeIf { it.isNotBlank() }
                    ?.let { runCatching { JSONObject(it).opt("value") }.getOrNull() },
                counter = version.optLong("counter"),
                deviceId = version.optString("deviceId"),
                deleted = item.has("deletedAt") && !item.isNull("deletedAt"),
            )
            val current = records[id]
            if (current == null || candidate.counter > current.counter ||
                candidate.counter == current.counter && candidate.deviceId > current.deviceId
            ) {
                records[id] = candidate
            }
        }
        return records
    }

    private fun updateDeviceMetadata(document: JSONObject, now: Long) {
        val devices = document.optJSONArray("devices") ?: JSONArray()
        val replacement = JSONArray()
        val ownId = state.deviceId()
        var replaced = false
        for (index in 0 until devices.length()) {
            val item = devices.optJSONObject(index) ?: continue
            if (item.optString("deviceId") == ownId) {
                replacement.put(
                    JSONObject()
                        .put("deviceId", ownId)
                        .put("lastCounter", state.currentCounter())
                        .put("lastChangedAt", now),
                )
                replaced = true
            } else {
                replacement.put(item)
            }
        }
        if (!replaced) {
            replacement.put(
                JSONObject()
                    .put("deviceId", ownId)
                    .put("lastCounter", state.currentCounter())
                    .put("lastChangedAt", now),
            )
        }
        val compacted = (0 until replacement.length())
            .mapNotNull(replacement::optJSONObject)
            .sortedByDescending { it.optLong("lastChangedAt") }
            .take(MAX_DEVICES)
        document.put("devices", JSONArray().also { target -> compacted.forEach { target.put(it) } })
    }

    private fun compactChanges(document: JSONObject) {
        val source = document.optJSONArray("changes") ?: return
        val compacted = (0 until source.length())
            .mapNotNull(source::optJSONObject)
            .sortedWith(
                compareByDescending<JSONObject> { it.optLong("occurredAt") }
                    .thenByDescending { it.optString("id") },
            )
            .take(MAX_CHANGES)
        document.put("changes", JSONArray().also { target -> compacted.forEach { target.put(it) } })
    }

    private data class CloudSettingRecord(
        val json: JSONObject,
        val value: Any?,
        val counter: Long,
        val deviceId: String,
        val deleted: Boolean,
    )

    private data class WearLogicalVersion(
        val counter: Long,
        val deviceId: String,
        val changedAt: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("counter", counter)
            .put("deviceId", deviceId)
            .put("changedAt", changedAt)
    }

    private inner class WearOfficialCloudState(context: Context) {
        private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        fun loadBaseline(): JSONObject? = prefs.getString(KEY_BASELINE, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

        fun saveBaseline(value: JSONObject) {
            prefs.edit().putString(KEY_BASELINE, value.toString()).apply()
        }

        fun ensureAccount(userId: String) {
            if (prefs.getString(KEY_USER_ID, "").orEmpty() == userId) return
            prefs.edit()
                .putString(KEY_USER_ID, userId)
                .remove(KEY_BASELINE)
                .remove(KEY_DEVICE_ID)
                .remove(KEY_COUNTER)
                .remove(KEY_LAST_SYNC_AT)
                .remove(KEY_LAST_ERROR)
                .commit()
        }

        fun deviceId(): String = prefs.getString(KEY_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).commit() }

        fun currentCounter(): Long = prefs.getLong(KEY_COUNTER, 0L)

        fun nextVersion(minimum: Long): WearLogicalVersion {
            val now = System.currentTimeMillis()
            val next = maxOf(currentCounter(), minimum, now) + 1L
            prefs.edit().putLong(KEY_COUNTER, next).commit()
            return WearLogicalVersion(next, deviceId(), now)
        }

        fun saveSuccess(syncedAt: Long) {
            prefs.edit()
                .putLong(KEY_LAST_SYNC_AT, syncedAt)
                .putString(KEY_LAST_ERROR, "")
                .apply()
        }

        fun saveFailure(message: String) {
            prefs.edit().putString(KEY_LAST_ERROR, message.take(240)).apply()
        }
    }

    private class WearLocalCloudStateChangedException :
        IllegalStateException("Wear settings changed during cloud sync")

    companion object {
        const val PREF_NAME = "wear_official_cloud_sync"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_ERROR = "last_error"
        private const val KEY_BASELINE = "baseline"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_COUNTER = "logical_counter"
        private const val MAX_CAS_ATTEMPTS = 3
        private const val CAS_BACKOFF_MS = 250L
        private const val MAX_CHANGES = 100
        private const val MAX_DEVICES = 64

        private fun valuePayload(value: Any?): String = JSONObject()
            .put("value", value ?: JSONObject.NULL)
            .toString()

        private fun jsonObjectsEqual(left: JSONObject, right: JSONObject): Boolean {
            val leftKeys = left.keys().asSequence().toSet()
            val rightKeys = right.keys().asSequence().toSet()
            return leftKeys == rightKeys && leftKeys.all { jsonValuesEqual(left.opt(it), right.opt(it)) }
        }

        internal fun jsonValuesEqual(left: Any?, right: Any?): Boolean {
            if (left === JSONObject.NULL || left == null) return right === JSONObject.NULL || right == null
            if (right === JSONObject.NULL || right == null) return false
            if (left is Number && right is Number) return left.toString().toBigDecimalOrNull() == right.toString().toBigDecimalOrNull()
            return left == right
        }
    }
}
