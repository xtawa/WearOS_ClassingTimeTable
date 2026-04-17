package com.xtawa.classingtime.sync

import android.content.Context
import com.classing.shared.sync.CloudProvider
import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.sync.SyncArbitrator
import com.classing.shared.sync.SyncDomain
import com.classing.shared.sync.SyncSource
import com.classing.shared.sync.SyncStamp
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.reminder.KeepAliveLevel
import com.xtawa.classingtime.reminder.ReminderScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

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
    private val webDavStorageClient: MobileCloudStorageClient = WebDavCloudStorageClient()
    private val googleDriveStorageClient: MobileCloudStorageClient = GoogleDriveCloudStorageClient()

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
                val config = resolveRuntimeConfig(context, current, allowSilentDriveRefresh = true)
                val pushedNodes = if (alsoPushConfigToWear) {
                    CloudConfigPublisher.publishToWear(
                        context = context,
                        payload = current.toCloudConfigPayload(
                            password = config.password,
                            driveAccessToken = config.driveAccessToken,
                            driveAccessTokenExpireAt = config.driveAccessTokenExpireAt,
                        ),
                        trigger = trigger,
                    ).getOrDefault(0)
                } else {
                    0
                }

                if (!config.isComplete()) {
                    val message = when (config.provider) {
                        CloudProvider.WEBDAV -> "Cloud sync disabled or WebDAV config incomplete"
                        CloudProvider.GOOGLE_DRIVE -> "Cloud sync disabled or Google Drive auth missing/expired"
                    }
                    saveCloudStatus(context, current, message, now)
                    lastRunAt = now
                    return@runCatching CloudSyncOutcome(
                        success = false,
                        message = message,
                        syncedAt = now,
                        pushedConfigNodes = pushedNodes,
                    )
                }

                val client = storageClient(config.provider)
                val remoteJson = client.readJson(config).getOrElse { throw it }
                val remote = remoteJson?.let {
                    runCatching { CloudDocument.fromJson(JSONObject(it)) }.getOrDefault(CloudDocument.empty())
                } ?: CloudDocument.empty()

                val localTimetableUpdatedAt = MobilePrefsStore.loadLocalTimetableUpdatedAt(context)
                    .takeIf { it > 0L } ?: now
                val localMobileSettingsUpdatedAt = MobilePrefsStore.loadLocalMobileSettingsUpdatedAt(context)
                    .takeIf { it > 0L } ?: now
                val localWearSettings = MobilePrefsStore.loadWearSettingsSnapshot(context)?.let { (json, updatedAt) ->
                    val payload = runCatching { JSONObject(json) }.getOrDefault(JSONObject())
                    CloudNamespaceSnapshot(
                        updatedAt = updatedAt,
                        revision = payload.optLong(
                            CloudSyncContracts.KEY_REVISION,
                            payload.optLong(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt),
                        ),
                        source = SyncSource.fromWire(payload.optString(CloudSyncContracts.KEY_SOURCE))
                            .takeUnless { it == SyncSource.UNKNOWN }
                            ?: SyncSource.WEAR_LOCAL,
                        settings = payload,
                    )
                }

                val localTimetable = CloudTimetableSnapshot(
                    updatedAt = localTimetableUpdatedAt,
                    revision = localTimetableUpdatedAt,
                    source = SyncSource.PHONE_DIRECT,
                    weekNumberMode = current.weekNumberMode,
                    semesterWeekStartDate = current.semesterWeekStartDate,
                    lessons = MobilePrefsStore.loadLessons(context),
                )
                val localMobileSettings = CloudNamespaceSnapshot(
                    updatedAt = localMobileSettingsUpdatedAt,
                    revision = localMobileSettingsUpdatedAt,
                    source = SyncSource.PHONE_LOCAL,
                    settings = current.toMobileSettingsSnapshotJson(),
                )

                val mergedTimetable = newerTimetable(remote.timetable, localTimetable)
                val mergedMobileSettings = newerNamespace(
                    domain = SyncDomain.MOBILE_SETTINGS,
                    remote = remote.mobileSettings,
                    local = localMobileSettings,
                )
                val mergedWearSettings = newerNamespace(
                    domain = SyncDomain.WEAR_SETTINGS,
                    remote = remote.wearSettings,
                    local = localWearSettings,
                )

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
                    localWearSettings = localWearSettings,
                )

                client.writeJson(config, merged.toJson().toString()).getOrElse { throw it }

                val message = "Cloud sync success (${config.provider.wireValue}:$trigger)"
                saveCloudStatus(context, MobilePrefsStore.loadSettings(context), message, now)
                lastRunAt = now
                CloudSyncOutcome(
                    success = true,
                    message = message,
                    syncedAt = now,
                    pushedConfigNodes = pushedNodes,
                )
            }.recoverCatching { error ->
                if (error is CloudAuthExpiredException) {
                    CloudCredentialStore.clearDriveAccessToken(context)
                    val current = MobilePrefsStore.loadSettings(context)
                    saveCloudStatus(context, current, "Google Drive token expired. Reconnect required.", System.currentTimeMillis())
                }
                throw error
            }
        }
    }

    suspend fun testConnection(context: Context): Result<Unit> {
        val settings = MobilePrefsStore.loadSettings(context)
        val config = resolveRuntimeConfig(context, settings, allowSilentDriveRefresh = true)
        if (!config.isComplete()) return Result.failure(IllegalStateException("Cloud config incomplete"))
        return storageClient(config.provider).testConnection(config)
    }

    private suspend fun resolveRuntimeConfig(
        context: Context,
        settings: MobileSettings,
        allowSilentDriveRefresh: Boolean,
    ): CloudRuntimeConfig {
        val password = CloudCredentialStore.loadPassword(context)
        var driveToken = CloudCredentialStore.loadDriveAccessToken(context)
        var driveTokenExpireAt = CloudCredentialStore.loadDriveAccessTokenExpireAt(context)
        val provider = CloudProvider.fromWire(settings.cloudProvider)
        val needsDriveRefresh = provider == CloudProvider.GOOGLE_DRIVE && (
            driveToken.isBlank() || driveTokenExpireAt <= System.currentTimeMillis() + 60_000L
            )
        if (allowSilentDriveRefresh && needsDriveRefresh) {
            val refreshed = GoogleDriveAuthManager.tryRefreshAccessTokenSilently(context).getOrNull()
            if (refreshed != null) {
                driveToken = refreshed.token
                driveTokenExpireAt = refreshed.expireAt
                CloudCredentialStore.saveDriveAccessToken(context, refreshed.token, refreshed.expireAt)
                MobilePrefsStore.saveSettings(
                    context,
                    settings.copy(cloudDriveTokenExpireAt = refreshed.expireAt),
                )
            }
        }
        return settings.toCloudRuntimeConfig(
            password = password,
            driveAccessToken = driveToken,
            driveAccessTokenExpireAt = driveTokenExpireAt,
        )
    }

    private fun storageClient(provider: CloudProvider): MobileCloudStorageClient {
        return when (provider) {
            CloudProvider.WEBDAV -> webDavStorageClient
            CloudProvider.GOOGLE_DRIVE -> googleDriveStorageClient
        }
    }

    private fun applyRemoteToLocalIfNeeded(
        context: Context,
        current: MobileSettings,
        remote: CloudDocument,
        localTimetableUpdatedAt: Long,
        localMobileSettingsUpdatedAt: Long,
        localWearSettings: CloudNamespaceSnapshot?,
    ) {
        val remoteTable = remote.timetable
        val localTimetableStamp = SyncStamp(
            revision = localTimetableUpdatedAt,
            source = SyncSource.PHONE_DIRECT,
            appliedAt = localTimetableUpdatedAt,
        )
        if (remoteTable != null && SyncArbitrator.shouldApply(
                domain = SyncDomain.TIMETABLE,
                incoming = remoteTable.toStamp(),
                current = localTimetableStamp,
            )
        ) {
            MobilePrefsStore.saveLessons(context, remoteTable.lessons)
            MobilePrefsStore.markLocalTimetableUpdated(context, remoteTable.revision)
        }

        val remoteMobileSettings = remote.mobileSettings
        val localMobileStamp = SyncStamp(
            revision = localMobileSettingsUpdatedAt,
            source = SyncSource.PHONE_LOCAL,
            appliedAt = localMobileSettingsUpdatedAt,
        )
        if (remoteMobileSettings != null && SyncArbitrator.shouldApply(
                domain = SyncDomain.MOBILE_SETTINGS,
                incoming = remoteMobileSettings.toStamp(),
                current = localMobileStamp,
            )
        ) {
            val settingsObj = remoteMobileSettings.settings
            MobilePrefsStore.saveSettings(
                context,
                current.copy(
                    showWeekend = settingsObj.optBoolean("showWeekend", current.showWeekend),
                    reminderEnabled = settingsObj.optBoolean("reminderEnabled", current.reminderEnabled),
                    reminderMinutes = settingsObj.optInt("reminderMinutes", current.reminderMinutes),
                    keepAliveLevel = settingsObj.optString("keepAliveLevel", current.keepAliveLevel),
                    experimentalAccessibilityKeepAliveEnabled = settingsObj.optBoolean(
                        "experimentalAccessibilityKeepAliveEnabled",
                        current.experimentalAccessibilityKeepAliveEnabled,
                    ),
                    wearSyncMode = settingsObj.optString("wearSyncMode", current.wearSyncMode),
                    weekNumberMode = settingsObj.optString("weekNumberMode", current.weekNumberMode),
                    semesterWeekStartDate = settingsObj.optString("semesterWeekStartDate", current.semesterWeekStartDate),
                    weekStartDay = settingsObj.optString("weekStartDay", current.weekStartDay),
                    cloudProvider = settingsObj.optString(CloudSyncContracts.KEY_CLOUD_PROVIDER, current.cloudProvider),
                    cloudDriveFileName = settingsObj.optString(
                        CloudSyncContracts.KEY_DRIVE_FILE_NAME,
                        current.cloudDriveFileName.ifBlank { CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME },
                    ),
                ),
            )
            MobilePrefsStore.markLocalMobileSettingsUpdated(context, remoteMobileSettings.revision)
            val applied = MobilePrefsStore.loadSettings(context)
            ReminderScheduler.sync(
                context = context,
                enabled = applied.reminderEnabled,
                keepAliveLevel = KeepAliveLevel.fromRaw(applied.keepAliveLevel),
                reminderMinutes = applied.reminderMinutes,
            )
        }

        val remoteWearSettings = remote.wearSettings
        if (remoteWearSettings != null) {
            val currentWearStamp = localWearSettings?.toStamp()
            if (SyncArbitrator.shouldApply(
                    domain = SyncDomain.WEAR_SETTINGS,
                    incoming = remoteWearSettings.toStamp(),
                    current = currentWearStamp,
                )
            ) {
                val wrapped = JSONObject(remoteWearSettings.settings.toString())
                    .put(CloudSyncContracts.KEY_SOURCE, remoteWearSettings.source.wireValue)
                    .put(CloudSyncContracts.KEY_REVISION, remoteWearSettings.revision)
                    .put(CloudSyncContracts.KEY_UPDATED_AT, remoteWearSettings.updatedAt)
                MobilePrefsStore.saveWearSettingsSnapshot(
                    context = context,
                    snapshotJson = wrapped.toString(),
                    updatedAt = remoteWearSettings.revision,
                )
            }
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
        return if (SyncArbitrator.shouldApply(
                domain = SyncDomain.TIMETABLE,
                incoming = remote.toStamp(),
                current = local.toStamp(),
            )
        ) remote else local
    }

    private fun newerNamespace(
        domain: SyncDomain,
        remote: CloudNamespaceSnapshot?,
        local: CloudNamespaceSnapshot?,
    ): CloudNamespaceSnapshot? {
        if (remote == null) return local
        if (local == null) return remote
        return if (SyncArbitrator.shouldApply(
                domain = domain,
                incoming = remote.toStamp(),
                current = local.toStamp(),
            )
        ) remote else local
    }

    private fun CloudTimetableSnapshot.toStamp(): SyncStamp {
        return SyncStamp(
            revision = revision,
            source = source,
            appliedAt = updatedAt,
        )
    }

    private fun CloudNamespaceSnapshot.toStamp(): SyncStamp {
        return SyncStamp(
            revision = revision,
            source = source,
            appliedAt = updatedAt,
        )
    }
}
