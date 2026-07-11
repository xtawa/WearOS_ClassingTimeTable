package com.xtawa.classingtime.sync

import android.content.Context
import com.classing.shared.sync.CloudProvider
import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.sync.CloudSyncV2
import com.classing.shared.sync.CloudSyncV2Merger
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.account.AccountApiClient
import com.xtawa.classingtime.data.SyncScope
import com.xtawa.classingtime.screen.WeekNumberMode
import com.xtawa.classingtime.screen.buildFlattenedEffectiveLessons
import com.xtawa.classingtime.screen.toLessonUi
import com.xtawa.classingtime.screen.toPersistedLesson
import com.xtawa.classingtime.screen.toUi
import java.time.LocalDate
import java.time.ZoneId
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
    private const val MAX_CAS_ATTEMPTS = 4
    private val mutex = Mutex()
    private val webDavStorageClient: MobileCloudStorageClient = WebDavCloudStorageClient()
    private val googleDriveStorageClient: MobileCloudStorageClient = GoogleDriveCloudStorageClient()
    private val officialCloudStorageClient: MobileCloudStorageClient = OfficialCloudStorageClient()

    suspend fun requestCloudSync(
        context: Context,
        trigger: String,
        force: Boolean = false,
        alsoPushConfigToWear: Boolean = true,
    ): Result<CloudSyncOutcome> = mutex.withLock {
        runCatching {
            @Suppress("UNUSED_VARIABLE") val compatibilityFlags = force to alsoPushConfigToWear
            val startedAt = System.currentTimeMillis()
            val current = MobilePrefsStore.loadSettings(context)
            val config = resolveRuntimeConfig(context, current, allowSilentDriveRefresh = true)
            if (!config.isComplete()) {
                val message = when (config.provider) {
                    CloudProvider.WEBDAV -> "Cloud sync disabled or WebDAV config incomplete"
                    CloudProvider.GOOGLE_DRIVE -> "Cloud sync disabled or Google Drive auth missing/expired"
                    CloudProvider.OFFICIAL -> "Official cloud requires login and active membership"
                }
                saveCloudStatus(context, current, message, startedAt)
                return@runCatching CloudSyncOutcome(false, message, startedAt, 0)
            }

            var runtimeConfig = config
            val syncScopes = effectiveSyncScopes(current, runtimeConfig.provider)
            val client = storageClient(config.provider)
            var conflicts = 0
            var wrote = false
            retryConditionalCloudUpdate(MAX_CAS_ATTEMPTS) { _ ->
                runWithDriveAuthRefreshRetry(context, runtimeConfig) { refreshedConfig ->
                    runtimeConfig = refreshedConfig
                    val read = client.readJson(runtimeConfig).getOrElse { throw it }
                    val remote = parseRemote(context, read.payload, startedAt)
                    val local = MobileCloudSyncV2Store.captureLocal(context, syncScopes = syncScopes)
                    val merge = CloudSyncV2Merger.merge(remote.document, local, System.currentTimeMillis())
                    conflicts += merge.conflicts
                    MobileCloudSyncV2Store.applyMerged(context, merge.document, syncScopes)

                    if (read.payload != null && remote.isV2 && remote.document == merge.document) {
                        publishMergedScheduleToWear(context)
                        return@runWithDriveAuthRefreshRetry
                    }

                    val payload = MobileCloudSyncV2Json.toJson(merge.document).toString()
                    val write = client.writeJson(runtimeConfig, payload, read.versionToken)
                    write.getOrElse { throw it }
                    wrote = true
                    publishMergedScheduleToWear(context)
                }
                Unit
            }

            val finishedAt = System.currentTimeMillis()
            val message = buildString {
                append("Cloud sync success (${runtimeConfig.provider.wireValue}:$trigger, v2")
                if (conflicts > 0) append(", merged=$conflicts")
                append(if (wrote) ", uploaded)" else ", unchanged)")
            }
            saveCloudStatus(context, MobilePrefsStore.loadSettings(context), message, finishedAt)
            CloudSyncOutcome(true, message, finishedAt, 0)
        }.recoverCatching { error ->
            val now = System.currentTimeMillis()
            val settings = MobilePrefsStore.loadSettings(context)
            val provider = CloudProvider.fromWire(settings.cloudProvider)
            if (error is CloudAuthExpiredException && provider == CloudProvider.GOOGLE_DRIVE) {
                val driveAuthExpired = maxOf(
                    settings.cloudDriveTokenExpireAt,
                    CloudCredentialStore.loadDriveAccessTokenExpireAt(context),
                ) <= now + 60_000L
                if (driveAuthExpired) {
                    CloudCredentialStore.clearDriveAccessToken(context)
                }
            }
            val prefix = when (error) {
                is UnsafeCloudStorageException -> "Unsafe cloud storage"
                is CloudWriteConflictException -> "Cloud changed repeatedly; retry queued"
                else -> "Cloud sync failed"
            }
            saveCloudStatus(
                context,
                MobilePrefsStore.loadSettings(context),
                "$prefix: ${error.message ?: "unknown"}",
                now,
            )
            throw error
        }
    }

    suspend fun testConnection(context: Context): Result<Unit> {
        val settings = MobilePrefsStore.loadSettings(context)
        var config = resolveRuntimeConfig(context, settings, allowSilentDriveRefresh = true)
        if (!config.isConfiguredForConnectionTest()) return Result.failure(IllegalStateException("Cloud config incomplete"))
        val client = storageClient(config.provider)
        return runCatching {
            runWithDriveAuthRefreshRetry(context, config) { refreshedConfig ->
                config = refreshedConfig
                client.testConnection(config).getOrElse { throw it }
            }
        }
    }

    suspend fun requestOfficialSettingsSync(
        context: Context,
        trigger: String,
    ): Result<CloudSyncOutcome> = mutex.withLock {
        runCatching {
            val startedAt = System.currentTimeMillis()
            val settings = MobilePrefsStore.loadSettings(context)
            val config = resolveOfficialRuntimeConfig(context, settings)
            if (config.accountAccessToken.isBlank()) {
                return@runCatching CloudSyncOutcome(false, "Official settings sync requires login", startedAt, 0)
            }
            val syncScopes = setOf(SyncScope.MOBILE_SETTINGS)
            val client = officialCloudStorageClient
            var conflicts = 0
            var wrote = false
            retryConditionalCloudUpdate(MAX_CAS_ATTEMPTS) {
                val read = client.readJson(config).getOrElse { throw it }
                val remote = parseRemote(context, read.payload, startedAt)
                val local = MobileCloudSyncV2Store.captureLocal(context, syncScopes = syncScopes)
                val merge = CloudSyncV2Merger.merge(remote.document, local, System.currentTimeMillis())
                conflicts += merge.conflicts
                MobileCloudSyncV2Store.applyMerged(context, merge.document, syncScopes)
                if (read.payload != null && remote.isV2 && remote.document == merge.document) {
                    return@retryConditionalCloudUpdate Unit
                }
                val payload = MobileCloudSyncV2Json.toJson(merge.document).toString()
                client.writeJson(config, payload, read.versionToken).getOrElse { throw it }
                wrote = true
            }
            val finishedAt = System.currentTimeMillis()
            CloudSyncOutcome(
                success = true,
                message = buildString {
                    append("Official settings sync success ($trigger")
                    if (conflicts > 0) append(", merged=$conflicts")
                    append(if (wrote) ", uploaded)" else ", unchanged)")
                },
                syncedAt = finishedAt,
                pushedConfigNodes = 0,
            )
        }
    }

    private data class ParsedRemote(
        val document: com.classing.shared.sync.CloudSyncDocumentV2,
        val isV2: Boolean,
    )

    private fun parseRemote(context: Context, raw: String?, now: Long) = when {
        raw.isNullOrBlank() -> ParsedRemote(com.classing.shared.sync.CloudSyncDocumentV2(), true)
        else -> {
            val json = JSONObject(raw)
            if (json.optString(CloudSyncContracts.KEY_FORMAT) == CloudSyncV2.DOCUMENT_FORMAT) {
                ParsedRemote(MobileCloudSyncV2Json.fromJson(json), true)
            } else {
                ParsedRemote(MobileCloudSyncV2Store.migrateV1(context, CloudDocument.fromJson(json), now), false)
            }
        }
    }

    private suspend fun publishMergedScheduleToWear(context: Context) {
        val settings = MobilePrefsStore.loadSettings(context)
        val state = MobilePrefsStore.loadTimetableState(context)
        val weekMode = WeekNumberMode.entries.firstOrNull { it.name == settings.weekNumberMode } ?: WeekNumberMode.NATURAL
        val semesterStart = runCatching { LocalDate.parse(settings.semesterWeekStartDate) }.getOrDefault(LocalDate.now())
        val lessons = buildFlattenedEffectiveLessons(
            baseLessons = state.baseLessons.map { it.toLessonUi() },
            exceptions = state.exceptions.map { it.toUi() },
            weekNumberMode = weekMode,
            semesterWeekStartDate = semesterStart,
        ).map { it.toPersistedLesson() }
        WearDataLayerSyncPublisher.publishLessonsSnapshot(
            context = context,
            lessons = lessons,
            zoneId = ZoneId.systemDefault(),
            source = WearDataLayerContracts.SOURCE_CLOUD_SYNC,
            allowDisconnectedQueue = true,
            weekNumberMode = settings.weekNumberMode,
            semesterWeekStartDate = semesterStart,
        )
        MobilePrefsStore.loadWearSettingsSnapshot(context)?.let { (payload, revision) ->
            WearDataLayerSyncPublisher.publishWearSettingsSnapshot(context, payload, revision)
        }
    }

    private suspend fun resolveRuntimeConfig(
        context: Context,
        settings: MobileSettings,
        allowSilentDriveRefresh: Boolean,
    ): CloudRuntimeConfig {
        val password = CloudCredentialStore.loadPassword(context)
        var driveToken = CloudCredentialStore.loadDriveAccessToken(context)
        var driveTokenExpireAt = CloudCredentialStore.loadDriveAccessTokenExpireAt(context)
        var driveTokenRefreshAfterAt = CloudCredentialStore.loadDriveAccessTokenRefreshAfterAt(context)
        val provider = CloudProvider.fromWire(settings.cloudProvider)
        var accountAccessToken = AuthCredentialStore.loadAccessToken(context)
        if (provider == CloudProvider.OFFICIAL && !AuthCredentialStore.isAccessTokenUsable(context)) {
            accountAccessToken = if (AuthCredentialStore.isRefreshTokenUsable(context)) {
                AccountApiClient().refresh(AuthCredentialStore.loadRefreshToken(context)).getOrNull()?.let { session ->
                    AuthCredentialStore.saveSession(
                        context,
                        session.accessToken,
                        session.refreshToken,
                        session.accessExpiresAt,
                        session.refreshExpiresAt,
                    )
                    session.accessToken
                }.orEmpty()
            } else {
                ""
            }
            if (accountAccessToken.isBlank()) {
                AuthCredentialStore.clear(context)
            }
        }
        val needsRefresh = provider == CloudProvider.GOOGLE_DRIVE &&
            (driveToken.isBlank() || driveTokenExpireAt <= System.currentTimeMillis() + 60_000L || driveTokenRefreshAfterAt <= System.currentTimeMillis() + 60_000L)
        if (allowSilentDriveRefresh && needsRefresh) {
            GoogleDriveAuthManager.tryRefreshAccessTokenSilently(context).getOrNull()?.let { refreshed ->
                driveToken = refreshed.token
                driveTokenExpireAt = refreshed.expireAt
                driveTokenRefreshAfterAt = refreshed.refreshAfterAt
                CloudCredentialStore.saveDriveAccessToken(context, refreshed.token, refreshed.expireAt, refreshed.refreshAfterAt)
                MobilePrefsStore.saveSettings(context, settings.copy(cloudDriveTokenExpireAt = refreshed.expireAt))
            }
        }
        return settings.toCloudRuntimeConfig(
            password = password,
            driveAccessToken = driveToken,
            driveAccessTokenExpireAt = driveTokenExpireAt,
            driveAccessTokenRefreshAfterAt = driveTokenRefreshAfterAt,
            accountAccessToken = accountAccessToken,
        )
    }

    private suspend fun resolveOfficialRuntimeConfig(
        context: Context,
        settings: MobileSettings,
    ): CloudRuntimeConfig {
        var accessToken = AuthCredentialStore.loadAccessToken(context)
        if (!AuthCredentialStore.isAccessTokenUsable(context)) {
            accessToken = if (AuthCredentialStore.isRefreshTokenUsable(context)) {
                AccountApiClient().refresh(AuthCredentialStore.loadRefreshToken(context)).getOrNull()?.let { session ->
                    AuthCredentialStore.saveSession(
                        context,
                        session.accessToken,
                        session.refreshToken,
                        session.accessExpiresAt,
                        session.refreshExpiresAt,
                    )
                    session.accessToken
                }.orEmpty()
            } else {
                ""
            }
            if (accessToken.isBlank()) {
                AuthCredentialStore.clear(context)
            }
        }
        return CloudRuntimeConfig(
            provider = CloudProvider.OFFICIAL,
            enabled = true,
            serverUrl = AccountApiClient.BASE_URL,
            remotePath = CloudSyncContracts.DEFAULT_REMOTE_PATH,
            username = "",
            password = "",
            driveFileName = settings.cloudDriveFileName,
            driveAccessToken = "",
            driveAccessTokenExpireAt = 0L,
            driveAccessTokenRefreshAfterAt = 0L,
            accountAccessToken = accessToken,
            officialMemberAuthorized = settings.membershipSummary.isMember,
        )
    }

    private fun effectiveSyncScopes(settings: MobileSettings, provider: CloudProvider): Set<SyncScope> {
        val scopes = settings.syncScopes.ifEmpty { SyncScope.entries.toSet() }
        if (provider != CloudProvider.OFFICIAL || settings.membershipSummary.isMember) {
            return scopes
        }
        return (scopes - SyncScope.TIMETABLE).ifEmpty { setOf(SyncScope.MOBILE_SETTINGS) }
    }

    private suspend fun runWithDriveAuthRefreshRetry(
        context: Context,
        config: CloudRuntimeConfig,
        block: suspend (CloudRuntimeConfig) -> Unit,
    ) {
        try {
            block(config)
        } catch (error: CloudAuthExpiredException) {
            if (config.provider != CloudProvider.GOOGLE_DRIVE) throw error
            val refreshed = refreshDriveRuntimeConfigSilently(context, config) ?: throw error
            block(refreshed)
        }
    }

    private suspend fun refreshDriveRuntimeConfigSilently(
        context: Context,
        config: CloudRuntimeConfig,
    ): CloudRuntimeConfig? {
        val refreshed = GoogleDriveAuthManager.tryRefreshAccessTokenSilently(context).getOrNull() ?: return null
        CloudCredentialStore.saveDriveAccessToken(context, refreshed.token, refreshed.expireAt, refreshed.refreshAfterAt)
        val current = MobilePrefsStore.loadSettings(context)
        MobilePrefsStore.saveSettings(context, current.copy(cloudDriveTokenExpireAt = refreshed.expireAt))
        return config.copy(
            driveAccessToken = refreshed.token,
            driveAccessTokenExpireAt = refreshed.expireAt,
            driveAccessTokenRefreshAfterAt = refreshed.refreshAfterAt,
        )
    }

    private fun storageClient(provider: CloudProvider): MobileCloudStorageClient = when (provider) {
        CloudProvider.WEBDAV -> webDavStorageClient
        CloudProvider.GOOGLE_DRIVE -> googleDriveStorageClient
        CloudProvider.OFFICIAL -> officialCloudStorageClient
    }

    private fun saveCloudStatus(context: Context, settings: MobileSettings, message: String, syncedAt: Long) {
        MobilePrefsStore.saveSettings(context, settings.copy(cloudLastResult = message, cloudLastSyncedAt = syncedAt))
        MobilePrefsStore.markLastCloudSync(context, syncedAt, message)
    }
}
