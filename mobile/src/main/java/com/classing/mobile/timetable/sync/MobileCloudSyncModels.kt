package com.xtawa.classingtime.sync

import com.classing.shared.sync.CloudProvider
import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.model.MAX_SCHEDULE_WEEK
import com.classing.shared.model.MIN_SCHEDULE_WEEK
import com.classing.shared.sync.SyncSource
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.account.AccountApiClient
import com.xtawa.classingtime.data.OfficialSyncFrequency
import com.xtawa.classingtime.data.SyncScope
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.data.PersistedLesson
import com.xtawa.classingtime.data.PersistedScheduleException
import com.xtawa.classingtime.data.PersistedScheduleSnapshot
import com.xtawa.classingtime.data.PersistedTimetableState
import org.json.JSONArray
import org.json.JSONObject

data class CloudRuntimeConfig(
    val provider: CloudProvider,
    val enabled: Boolean,
    val serverUrl: String,
    val remotePath: String,
    val username: String,
    val password: String,
    val driveFileName: String,
    val driveAccessToken: String,
    val driveAccessTokenExpireAt: Long,
    val driveAccessTokenRefreshAfterAt: Long,
    val accountAccessToken: String,
    val officialMemberAuthorized: Boolean,
    val clientPackageName: String = "",
    val clientPlatform: String = "",
    val clientVersionCode: Long = 0L,
    val clientSigningCertSha256: String = "",
) {
    fun isComplete(): Boolean {
        if (!enabled) return false
        return when (provider) {
            CloudProvider.WEBDAV -> {
                serverUrl.startsWith("https://", ignoreCase = true) &&
                    remotePath.isNotBlank() &&
                    username.isNotBlank() &&
                    password.isNotBlank()
            }

            CloudProvider.GOOGLE_DRIVE -> {
                driveFileName.isNotBlank() &&
                    driveAccessToken.isNotBlank() &&
                    !isDriveTokenExpired()
            }

            CloudProvider.OFFICIAL -> {
                serverUrl.startsWith("https://", ignoreCase = true) &&
                    accountAccessToken.isNotBlank()
            }
        }
    }

    fun isConfiguredForConnectionTest(): Boolean = when (provider) {
        CloudProvider.WEBDAV -> serverUrl.startsWith("https://", ignoreCase = true) &&
            remotePath.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        CloudProvider.GOOGLE_DRIVE -> driveFileName.isNotBlank() &&
            driveAccessToken.isNotBlank() && !isDriveTokenExpired()
        CloudProvider.OFFICIAL -> serverUrl.startsWith("https://", ignoreCase = true) &&
            accountAccessToken.isNotBlank()
    }

    fun isDriveTokenExpired(
        now: Long = System.currentTimeMillis(),
        skewMs: Long = 60_000L,
    ): Boolean {
        return driveAccessTokenExpireAt <= 0L || now + skewMs >= driveAccessTokenExpireAt
    }

    fun shouldRefreshDriveAccessToken(
        now: Long = System.currentTimeMillis(),
        skewMs: Long = 60_000L,
    ): Boolean {
        return driveAccessTokenRefreshAfterAt <= 0L || now + skewMs >= driveAccessTokenRefreshAfterAt
    }
}

data class CloudNamespaceSnapshot(
    val updatedAt: Long,
    val revision: Long,
    val source: SyncSource,
    val settings: JSONObject,
)

data class CloudTimetableSnapshot(
    val updatedAt: Long,
    val revision: Long,
    val source: SyncSource,
    val weekNumberMode: String,
    val semesterWeekStartDate: String,
    val lessons: List<PersistedLesson>,
    val baseLessons: List<PersistedLesson>? = null,
    val exceptions: List<PersistedScheduleException> = emptyList(),
)

data class CloudDocument(
    val timetable: CloudTimetableSnapshot?,
    val mobileSettings: CloudNamespaceSnapshot?,
    val wearSettings: CloudNamespaceSnapshot?,
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
            .put(CloudSyncContracts.KEY_FORMAT, CloudSyncContracts.DOCUMENT_FORMAT)
            .put(CloudSyncContracts.KEY_UPDATED_AT, System.currentTimeMillis())

        timetable?.let { table ->
            val lessons = JSONArray()
            table.lessons.forEach { lesson ->
                lessons.put(
                    JSONObject()
                        .put("id", lesson.id)
                        .put("title", lesson.title)
                        .put("teacher", lesson.teacher.orEmpty())
                        .put("location", lesson.location.orEmpty())
                        .put("note", lesson.note.orEmpty())
                        .put("dayOfWeek", lesson.dayOfWeek)
                        .put("startMinute", lesson.startMinute)
                        .put("endMinute", lesson.endMinute)
                        .put("startWeek", lesson.startWeek.coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK))
                        .put(
                            "endWeek",
                            lesson.endWeek.coerceIn(
                                lesson.startWeek.coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK),
                                MAX_SCHEDULE_WEEK,
                            ),
                        )
                        .put("weekParity", lesson.weekParity),
                )
            }
            root.put(
                CloudSyncContracts.KEY_TIMETABLE,
                JSONObject()
                    .put(CloudSyncContracts.KEY_UPDATED_AT, table.updatedAt)
                    .put(CloudSyncContracts.KEY_REVISION, table.revision)
                    .put(CloudSyncContracts.KEY_SOURCE, table.source.wireValue)
                    .put(CloudSyncContracts.KEY_WEEK_NUMBER_MODE, table.weekNumberMode)
                    .put(CloudSyncContracts.KEY_SEMESTER_WEEK_START_DATE, table.semesterWeekStartDate)
                    .put(CloudSyncContracts.KEY_LESSONS, lessons),
            )
            table.baseLessons?.let { baseLessons ->
                val baseLessonArray = JSONArray()
                baseLessons.forEach { lesson ->
                    baseLessonArray.put(
                        JSONObject()
                            .put("id", lesson.id)
                            .put("title", lesson.title)
                            .put("teacher", lesson.teacher.orEmpty())
                            .put("location", lesson.location.orEmpty())
                            .put("note", lesson.note.orEmpty())
                            .put("dayOfWeek", lesson.dayOfWeek)
                            .put("startMinute", lesson.startMinute)
                            .put("endMinute", lesson.endMinute)
                            .put("startWeek", lesson.startWeek.coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK))
                            .put(
                                "endWeek",
                                lesson.endWeek.coerceIn(
                                    lesson.startWeek.coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK),
                                    MAX_SCHEDULE_WEEK,
                                ),
                            )
                            .put("weekParity", lesson.weekParity),
                    )
                }
                root.getJSONObject(CloudSyncContracts.KEY_TIMETABLE)
                    .put("baseLessons", baseLessonArray)
            }
            if (table.exceptions.isNotEmpty()) {
                val exceptions = JSONArray()
                table.exceptions.forEach { exception ->
                    exceptions.put(
                        JSONObject()
                            .put("id", exception.id)
                            .put("lessonId", exception.lessonId.orEmpty())
                            .put("type", exception.type)
                            .put("date", exception.date)
                            .put("title", exception.title.orEmpty())
                            .put("teacher", exception.teacher.orEmpty())
                            .put("location", exception.location.orEmpty())
                            .put("note", exception.note.orEmpty())
                            .put("dayOfWeek", exception.dayOfWeek ?: JSONObject.NULL)
                            .put("startMinute", exception.startMinute ?: JSONObject.NULL)
                            .put("endMinute", exception.endMinute ?: JSONObject.NULL),
                    )
                }
                root.getJSONObject(CloudSyncContracts.KEY_TIMETABLE)
                    .put("exceptions", exceptions)
            }
        }

        mobileSettings?.let { snapshot ->
            root.put(
                CloudSyncContracts.KEY_MOBILE_SETTINGS,
                JSONObject()
                    .put(CloudSyncContracts.KEY_NAMESPACE_UPDATED_AT, snapshot.updatedAt)
                    .put(CloudSyncContracts.KEY_REVISION, snapshot.revision)
                    .put(CloudSyncContracts.KEY_SOURCE, snapshot.source.wireValue)
                    .put(CloudSyncContracts.KEY_SETTINGS_PAYLOAD, snapshot.settings),
            )
        }
        wearSettings?.let { snapshot ->
            root.put(
                CloudSyncContracts.KEY_WEAR_SETTINGS,
                JSONObject()
                    .put(CloudSyncContracts.KEY_NAMESPACE_UPDATED_AT, snapshot.updatedAt)
                    .put(CloudSyncContracts.KEY_REVISION, snapshot.revision)
                    .put(CloudSyncContracts.KEY_SOURCE, snapshot.source.wireValue)
                    .put(CloudSyncContracts.KEY_SETTINGS_PAYLOAD, snapshot.settings),
            )
        }
        return root
    }

    companion object {
        fun empty(): CloudDocument = CloudDocument(
            timetable = null,
            mobileSettings = null,
            wearSettings = null,
        )

        fun fromJson(json: JSONObject): CloudDocument {
            val timetable = json.optJSONObject(CloudSyncContracts.KEY_TIMETABLE)?.let { raw ->
                val lessonsArray = raw.optJSONArray(CloudSyncContracts.KEY_LESSONS) ?: JSONArray()
                val lessons = buildList {
                    for (i in 0 until lessonsArray.length()) {
                        parsePersistedLesson(lessonsArray.optJSONObject(i))?.let(::add)
                    }
                }
                val baseLessons = raw.optJSONArray("baseLessons")?.let { baseLessonsArray ->
                    buildList {
                        for (i in 0 until baseLessonsArray.length()) {
                            parsePersistedLesson(baseLessonsArray.optJSONObject(i))?.let(::add)
                        }
                    }
                }
                val exceptions = raw.optJSONArray("exceptions")?.let { exceptionsArray ->
                    buildList {
                        for (i in 0 until exceptionsArray.length()) {
                            parsePersistedScheduleException(exceptionsArray.optJSONObject(i))?.let(::add)
                        }
                    }
                }.orEmpty()
                CloudTimetableSnapshot(
                    updatedAt = raw.optLong(CloudSyncContracts.KEY_UPDATED_AT, 0L),
                    revision = raw.optLong(
                        CloudSyncContracts.KEY_REVISION,
                        raw.optLong(CloudSyncContracts.KEY_UPDATED_AT, 0L),
                    ),
                    source = SyncSource.fromWire(raw.optString(CloudSyncContracts.KEY_SOURCE)),
                    weekNumberMode = raw.optString(CloudSyncContracts.KEY_WEEK_NUMBER_MODE, "NATURAL"),
                    semesterWeekStartDate = raw.optString(CloudSyncContracts.KEY_SEMESTER_WEEK_START_DATE, ""),
                    lessons = lessons,
                    baseLessons = baseLessons,
                    exceptions = exceptions,
                )
            }

            fun parseNamespace(name: String): CloudNamespaceSnapshot? {
                val obj = json.optJSONObject(name) ?: return null
                return CloudNamespaceSnapshot(
                    updatedAt = obj.optLong(CloudSyncContracts.KEY_NAMESPACE_UPDATED_AT, 0L),
                    revision = obj.optLong(
                        CloudSyncContracts.KEY_REVISION,
                        obj.optLong(CloudSyncContracts.KEY_NAMESPACE_UPDATED_AT, 0L),
                    ),
                    source = SyncSource.fromWire(obj.optString(CloudSyncContracts.KEY_SOURCE)),
                    settings = obj.optJSONObject(CloudSyncContracts.KEY_SETTINGS_PAYLOAD) ?: JSONObject(),
                )
            }

            return CloudDocument(
                timetable = timetable,
                mobileSettings = parseNamespace(CloudSyncContracts.KEY_MOBILE_SETTINGS),
                wearSettings = parseNamespace(CloudSyncContracts.KEY_WEAR_SETTINGS),
            )
        }
    }
}

internal fun CloudTimetableSnapshot.toPersistedTimetableState(
    snapshots: List<PersistedScheduleSnapshot>,
): PersistedTimetableState {
    val resolvedBaseLessons = baseLessons ?: lessons
    val resolvedExceptions = if (baseLessons != null) exceptions else emptyList()
    return PersistedTimetableState(
        baseLessons = resolvedBaseLessons,
        exceptions = resolvedExceptions,
        snapshots = snapshots,
    )
}

private fun parsePersistedLesson(item: JSONObject?): PersistedLesson? {
    item ?: return null
    val id = item.optString("id")
    val title = item.optString("title")
    if (id.isBlank() || title.isBlank()) return null
    val startWeek = item.optInt("startWeek", MIN_SCHEDULE_WEEK)
        .coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK)
    return PersistedLesson(
        id = id,
        title = title,
        teacher = item.optString("teacher").ifBlank { null },
        location = item.optString("location").ifBlank { null },
        note = item.optString("note").ifBlank { null },
        dayOfWeek = item.optInt("dayOfWeek", 1).coerceIn(1, 7),
        startMinute = item.optInt("startMinute", 8 * 60).coerceIn(0, 24 * 60 - 1),
        endMinute = item.optInt("endMinute", 9 * 60).coerceIn(1, 24 * 60 - 1),
        startWeek = startWeek,
        endWeek = item.optInt("endWeek", MAX_SCHEDULE_WEEK).coerceIn(startWeek, MAX_SCHEDULE_WEEK),
        weekParity = item.optString("weekParity", "ALL").uppercase().let {
            if (it == "ODD" || it == "EVEN") it else "ALL"
        },
    )
}

private fun parsePersistedScheduleException(item: JSONObject?): PersistedScheduleException? {
    item ?: return null
    val id = item.optString("id")
    val type = item.optString("type")
    val date = item.optString("date")
    if (id.isBlank() || type.isBlank() || date.isBlank()) return null
    return PersistedScheduleException(
        id = id,
        lessonId = item.optString("lessonId").ifBlank { null },
        type = type,
        date = date,
        title = item.optString("title").ifBlank { null },
        teacher = item.optString("teacher").ifBlank { null },
        location = item.optString("location").ifBlank { null },
        note = item.optString("note").ifBlank { null },
        dayOfWeek = item.optNullableInt("dayOfWeek")?.takeIf { it in 1..7 },
        startMinute = item.optNullableInt("startMinute")?.takeIf { it in 0 until (24 * 60) },
        endMinute = item.optNullableInt("endMinute")?.takeIf { it in 1 until (24 * 60) },
    )
}

private fun JSONObject.optNullableInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name)
}

fun MobileSettings.toCloudRuntimeConfig(
    password: String,
    driveAccessToken: String,
    driveAccessTokenExpireAt: Long,
    driveAccessTokenRefreshAfterAt: Long,
    accountAccessToken: String,
    officialMemberAuthorized: Boolean = false,
    clientPackageName: String = "",
    clientPlatform: String = "",
    clientVersionCode: Long = 0L,
    clientSigningCertSha256: String = "",
): CloudRuntimeConfig {
    val provider = CloudProvider.fromWire(cloudProvider)
    return CloudRuntimeConfig(
        provider = provider,
        enabled = cloudSyncEnabled,
        serverUrl = if (provider == CloudProvider.OFFICIAL) {
            AccountApiClient.BASE_URL
        } else {
            cloudServerUrl.trim()
        },
        remotePath = cloudRemotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH },
        username = cloudUsername.trim(),
        password = password,
        driveFileName = cloudDriveFileName.trim().ifBlank { CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME },
        driveAccessToken = driveAccessToken,
        driveAccessTokenExpireAt = driveAccessTokenExpireAt,
        driveAccessTokenRefreshAfterAt = driveAccessTokenRefreshAfterAt,
        accountAccessToken = accountAccessToken,
        officialMemberAuthorized = provider == CloudProvider.OFFICIAL && officialMemberAuthorized,
        clientPackageName = clientPackageName,
        clientPlatform = clientPlatform,
        clientVersionCode = clientVersionCode,
        clientSigningCertSha256 = clientSigningCertSha256,
    )
}

fun MobileSettings.toMobileSettingsSnapshotJson(): JSONObject {
    return JSONObject()
        .put("showWeekend", showWeekend)
        .put("reminderEnabled", reminderEnabled)
        .put("reminderMinutes", reminderMinutes)
        .put("keepAliveLevel", keepAliveLevel)
        .put("wearSyncMode", wearSyncMode)
        .put("weekNumberMode", weekNumberMode)
        .put("semesterWeekStartDate", semesterWeekStartDate)
        .put("weekStartDay", weekStartDay)
        .put("dailyBriefingEnabled", dailyBriefingEnabled)
        .put("dailyBriefingChannel", dailyBriefingChannel.name)
        .put("dailyBriefingTime", dailyBriefingTime)
        .put(CloudSyncContracts.KEY_CLOUD_PROVIDER, cloudProvider)
        .put(CloudSyncContracts.KEY_DRIVE_FILE_NAME, cloudDriveFileName)
}

fun MobileSettings.toCloudConfigPayload(
    password: String,
    driveAccessToken: String,
    driveAccessTokenExpireAt: Long,
): JSONObject {
    return JSONObject()
        .put("enabled", cloudSyncEnabled)
        .put(CloudSyncContracts.KEY_CLOUD_PROVIDER, cloudProvider)
        .put("serverUrl", cloudServerUrl.trim())
        .put("remotePath", cloudRemotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH })
        .put("username", cloudUsername.trim())
        .put(CloudSyncContracts.KEY_DRIVE_FILE_NAME, cloudDriveFileName.trim().ifBlank { CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME })
        .put("officialSyncFrequency", officialSyncFrequency.name)
        .put("syncScopes", JSONArray(syncScopes.sortedBy { it.ordinal }.map(SyncScope::name)))
}

fun MobileSettings.toWearCloudSnapshot(
    password: String,
    driveAccessToken: String,
    driveAccessTokenExpireAt: Long,
): JSONObject {
    return JSONObject()
        .put("enabled", cloudSyncEnabled)
        .put(CloudSyncContracts.KEY_CLOUD_PROVIDER, cloudProvider)
        .put("serverUrl", if (CloudProvider.fromWire(cloudProvider) == CloudProvider.OFFICIAL) AccountApiClient.BASE_URL else cloudServerUrl.trim())
        .put("remotePath", cloudRemotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH })
        .put("username", cloudUsername.trim())
        .put(CloudSyncContracts.KEY_DRIVE_FILE_NAME, cloudDriveFileName.trim().ifBlank { CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME })
        .put("officialSyncFrequency", officialSyncFrequency.name)
        .put("syncScopes", JSONArray(syncScopes.sortedBy { it.ordinal }.map(SyncScope::name)))
        .put("loggedIn", accountSummary.userId.isNotBlank())
        .put("isMember", membershipSummary.isMember)
        .put("membershipTier", membershipSummary.tier)
        .put("membershipExpiresAt", membershipSummary.expiresAt)
        .put("officialAvailable", accountSummary.userId.isNotBlank())
        .put(WearDataLayerContracts.KEY_CLOUD_PROVIDER, cloudProvider)
}
