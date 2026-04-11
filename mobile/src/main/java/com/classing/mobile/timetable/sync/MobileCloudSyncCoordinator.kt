package com.xtawa.classingtime.sync

import android.content.Context
import com.classing.shared.sync.CloudSyncContracts
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CloudSyncOutcome(
    val success: Boolean,
    val message: String,
    val syncedAt: Long,
    val pushedConfigNodes: Int,
)

object MobileCloudSyncCoordinator {
    private const val THROTTLE_MS = 5_000L
    private val mutex = Mutex()
    private var lastRunAt = 0L
    private val webDavClient = WebDavHttpClient()

    suspend fun requestCloudSync(
        context: Context,
        trigger: String,
        force: Boolean = false,
        alsoPushConfigToWear: Boolean = true,
    ): Result<CloudSyncOutcome> {
        return mutex.withLock {
            runCatching {
                val now = System.currentTimeMillis()
                if (!force && now - lastRunAt < THROTTLE_MS) {
                    return@runCatching CloudSyncOutcome(
                        success = true,
                        message = "Cloud sync skipped by throttle",
                        syncedAt = now,
                        pushedConfigNodes = 0,
                    )
                }

                val current = MobilePrefsStore.loadSettings(context)
                val password = CloudCredentialStore.loadPassword(context)
                val config = current.toWebDavConfig(password)

                val pushedNodes = if (alsoPushConfigToWear) {
                    CloudConfigPublisher.publishToWear(
                        context = context,
                        payload = current.toCloudConfigPayload(password),
                        trigger = trigger,
                    ).getOrDefault(0)
                } else {
                    0
                }

                if (!config.isComplete()) {
                    val message = "Cloud sync disabled or config incomplete"
                    saveCloudStatus(context, current, message, now)
                    lastRunAt = now
                    return@runCatching CloudSyncOutcome(
                        success = false,
                        message = message,
                        syncedAt = now,
                        pushedConfigNodes = pushedNodes,
                    )
                }

                val remoteJson = webDavClient.readJson(config).getOrElse { throw it }
                val remote = remoteJson?.let {
                    runCatching { CloudDocument.fromJson(JSONObject(it)) }.getOrDefault(CloudDocument.empty())
                } ?: CloudDocument.empty()

                val localTimetableUpdatedAt = MobilePrefsStore.loadLocalTimetableUpdatedAt(context)
                    .takeIf { it > 0L } ?: now
                val localMobileSettingsUpdatedAt = MobilePrefsStore.loadLocalMobileSettingsUpdatedAt(context)
                    .takeIf { it > 0L } ?: now
                val localWearSettings = MobilePrefsStore.loadWearSettingsSnapshot(context)?.let { (json, updatedAt) ->
                    CloudNamespaceSnapshot(
                        updatedAt = updatedAt,
                        settings = runCatching { JSONObject(json) }.getOrDefault(JSONObject()),
                    )
                }

                val localTimetable = CloudTimetableSnapshot(
                    updatedAt = localTimetableUpdatedAt,
                    weekNumberMode = current.weekNumberMode,
                    semesterWeekStartDate = current.semesterWeekStartDate,
                    lessons = MobilePrefsStore.loadLessons(context),
                )
                val localMobileSettings = CloudNamespaceSnapshot(
                    updatedAt = localMobileSettingsUpdatedAt,
                    settings = current.toMobileSettingsSnapshotJson(),
                )

                val mergedTimetable = newerTimetable(remote.timetable, localTimetable)
                val mergedMobileSettings = newerNamespace(remote.mobileSettings, localMobileSettings)
                val mergedWearSettings = newerNamespace(remote.wearSettings, localWearSettings)

                val merged = CloudDocument(
                    timetable = mergedTimetable,
                    mobileSettings = mergedMobileSettings,
                    wearSettings = mergedWearSettings,
                )

                applyRemoteToLocalIfNeeded(
                    context = context,
                    current = current,
                    remote = remote,
                    localTimetableUpdatedAt = localTimetableUpdatedAt,
                    localMobileSettingsUpdatedAt = localMobileSettingsUpdatedAt,
                )

                webDavClient.writeJson(config, merged.toJson().toString()).getOrElse { throw it }

                val message = "Cloud sync success ($trigger)"
                saveCloudStatus(context, MobilePrefsStore.loadSettings(context), message, now)
                lastRunAt = now
                CloudSyncOutcome(
                    success = true,
                    message = message,
                    syncedAt = now,
                    pushedConfigNodes = pushedNodes,
                )
            }
        }
    }

    suspend fun testConnection(context: Context): Result<Unit> {
        val settings = MobilePrefsStore.loadSettings(context)
        val config = settings.toWebDavConfig(CloudCredentialStore.loadPassword(context))
        if (!config.isComplete()) return Result.failure(IllegalStateException("Cloud config incomplete"))
        return webDavClient.testConnection(config)
    }

    private fun applyRemoteToLocalIfNeeded(
        context: Context,
        current: MobileSettings,
        remote: CloudDocument,
        localTimetableUpdatedAt: Long,
        localMobileSettingsUpdatedAt: Long,
    ) {
        val remoteTable = remote.timetable
        if (remoteTable != null && remoteTable.updatedAt > localTimetableUpdatedAt) {
            MobilePrefsStore.saveLessons(context, remoteTable.lessons)
            MobilePrefsStore.markLocalTimetableUpdated(context, remoteTable.updatedAt)
        }

        val remoteMobileSettings = remote.mobileSettings
        if (remoteMobileSettings != null && remoteMobileSettings.updatedAt > localMobileSettingsUpdatedAt) {
            val settingsObj = remoteMobileSettings.settings
            MobilePrefsStore.saveSettings(
                context,
                current.copy(
                    showWeekend = settingsObj.optBoolean("showWeekend", current.showWeekend),
                    reminderEnabled = settingsObj.optBoolean("reminderEnabled", current.reminderEnabled),
                    reminderMinutes = settingsObj.optInt("reminderMinutes", current.reminderMinutes),
                    wearSyncMode = settingsObj.optString("wearSyncMode", current.wearSyncMode),
                    weekNumberMode = settingsObj.optString("weekNumberMode", current.weekNumberMode),
                    semesterWeekStartDate = settingsObj.optString("semesterWeekStartDate", current.semesterWeekStartDate),
                ),
            )
            MobilePrefsStore.markLocalMobileSettingsUpdated(context, remoteMobileSettings.updatedAt)
        }
    }

    private fun saveCloudStatus(context: Context, settings: MobileSettings, message: String, syncedAt: Long) {
        MobilePrefsStore.saveSettings(
            context,
            settings.copy(
                cloudLastResult = message,
                cloudLastSyncedAt = syncedAt,
            ),
        )
    }

    private fun newerTimetable(
        remote: CloudTimetableSnapshot?,
        local: CloudTimetableSnapshot?,
    ): CloudTimetableSnapshot? {
        if (remote == null) return local
        if (local == null) return remote
        return if (remote.updatedAt > local.updatedAt) remote else local
    }

    private fun newerNamespace(
        remote: CloudNamespaceSnapshot?,
        local: CloudNamespaceSnapshot?,
    ): CloudNamespaceSnapshot? {
        if (remote == null) return local
        if (local == null) return remote
        return if (remote.updatedAt > local.updatedAt) remote else local
    }
}
