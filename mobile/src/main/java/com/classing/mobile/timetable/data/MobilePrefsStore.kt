package com.xtawa.classingtime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PersistedLesson(
    val id: String,
    val title: String,
    val teacher: String?,
    val location: String?,
    val note: String?,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekParity: String,
)

data class PersistedScheduleException(
    val id: String,
    val lessonId: String?,
    val type: String,
    val date: String,
    val title: String?,
    val teacher: String?,
    val location: String?,
    val note: String?,
    val dayOfWeek: Int?,
    val startMinute: Int?,
    val endMinute: Int?,
)

data class PersistedScheduleSnapshot(
    val id: String,
    val createdAt: Long,
    val reason: String,
    val weekNumberMode: String,
    val semesterWeekStartDate: String,
    val baseLessons: List<PersistedLesson>,
    val exceptions: List<PersistedScheduleException>,
)

data class PersistedTimetableState(
    val baseLessons: List<PersistedLesson>,
    val exceptions: List<PersistedScheduleException>,
    val snapshots: List<PersistedScheduleSnapshot>,
)

data class MobileSettings(
    val showWeekend: Boolean,
    val reminderEnabled: Boolean,
    val reminderMinutes: Int,
    val keepAliveLevel: String,
    val experimentalAccessibilityKeepAliveEnabled: Boolean,
    val accountSummary: AccountSummary = AccountSummary(),
    val membershipSummary: MembershipSummary = MembershipSummary(),
    val rawIcs: String,
    val parseMessage: String,
    val wearSyncMode: String,
    val weekNumberMode: String,
    val semesterWeekStartDate: String,
    val weekStartDay: String,
    val dailyBriefingEnabled: Boolean = false,
    val dailyBriefingChannel: DailyBriefingChannel = DailyBriefingChannel.APP_NOTIFICATION,
    val dailyBriefingTime: String = "20:00",
    val cloudProvider: String,
    val cloudSyncEnabled: Boolean,
    val cloudServerUrl: String,
    val cloudRemotePath: String,
    val cloudUsername: String,
    val cloudDriveFileName: String,
    val cloudDriveTokenExpireAt: Long,
    val officialSyncFrequency: OfficialSyncFrequency = OfficialSyncFrequency.MANUAL_ONLY,
    val syncScopes: Set<SyncScope> = SyncScope.entries.toSet(),
    val cloudConfigPushStatus: String,
    val cloudLastResult: String,
    val cloudLastSyncedAt: Long,
)

object MobilePrefsStore {
    private const val PREF_NAME = "mobile_timetable_prefs"
    private const val KEY_SHOW_WEEKEND = "show_weekend"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_MINUTES = "reminder_minutes"
    private const val KEY_KEEP_ALIVE_LEVEL = "keep_alive_level"
    private const val KEY_EXPERIMENTAL_ACCESSIBILITY_KEEP_ALIVE_ENABLED = "experimental_accessibility_keep_alive_enabled"
    private const val KEY_ACCOUNT_SUMMARY_JSON = "account_summary_json"
    private const val KEY_MEMBERSHIP_SUMMARY_JSON = "membership_summary_json"
    private const val KEY_RAW_ICS = "raw_ics"
    private const val KEY_PARSE_MESSAGE = "parse_message"
    private const val KEY_WEAR_SYNC_MODE = "wear_sync_mode"
    private const val KEY_WEEK_NUMBER_MODE = "week_number_mode"
    private const val KEY_SEMESTER_WEEK_START_DATE = "semester_week_start_date"
    private const val KEY_WEEK_START_DAY = "week_start_day"
    private const val KEY_DAILY_BRIEFING_ENABLED = "daily_briefing_enabled"
    private const val KEY_DAILY_BRIEFING_CHANNEL = "daily_briefing_channel"
    private const val KEY_DAILY_BRIEFING_TIME = "daily_briefing_time"
    private const val KEY_CLOUD_PROVIDER = "cloud_provider"
    private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    private const val KEY_CLOUD_SERVER_URL = "cloud_server_url"
    private const val KEY_CLOUD_REMOTE_PATH = "cloud_remote_path"
    private const val KEY_CLOUD_USERNAME = "cloud_username"
    private const val KEY_CLOUD_DRIVE_FILE_NAME = "cloud_drive_file_name"
    private const val KEY_CLOUD_DRIVE_TOKEN_EXPIRE_AT = "cloud_drive_token_expire_at"
    private const val KEY_OFFICIAL_SYNC_FREQUENCY = "official_sync_frequency"
    private const val KEY_SYNC_SCOPES = "sync_scopes"
    private const val KEY_CLOUD_CONFIG_PUSH_STATUS = "cloud_config_push_status"
    private const val KEY_CLOUD_LAST_RESULT = "cloud_last_result"
    private const val KEY_CLOUD_LAST_SYNCED_AT = "cloud_last_synced_at"
    private const val KEY_LOCAL_TIMETABLE_UPDATED_AT = "local_timetable_updated_at"
    private const val KEY_LOCAL_MOBILE_SETTINGS_UPDATED_AT = "local_mobile_settings_updated_at"
    private const val KEY_WEAR_SETTINGS_SNAPSHOT = "wear_settings_snapshot"
    private const val KEY_WEAR_SETTINGS_UPDATED_AT = "wear_settings_updated_at"
    private const val KEY_LESSONS_JSON = "lessons_json"
    private const val KEY_BASE_LESSONS_JSON = "base_lessons_json"
    private const val KEY_SCHEDULE_EXCEPTIONS_JSON = "schedule_exceptions_json"
    private const val KEY_SCHEDULE_SNAPSHOTS_JSON = "schedule_snapshots_json"
    private const val KEY_LAST_SNAPSHOT_AT = "last_snapshot_at"
    private const val KEY_LAST_WEAR_PUSH_AT = "last_wear_push_at"
    private const val KEY_LAST_WEAR_PUSH_RESULT = "last_wear_push_result"
    private const val KEY_LAST_WEAR_ACK_AT = "last_wear_ack_at"
    private const val KEY_LAST_WEAR_ACK_RESULT = "last_wear_ack_result"
    private const val KEY_LAST_CLOUD_SYNC_AT = "last_cloud_sync_at"
    private const val KEY_LAST_CLOUD_SYNC_RESULT = "last_cloud_sync_result"
    private const val KEY_LAST_CONFIG_PUSH_AT = "last_config_push_at"
    private const val KEY_LAST_CONFIG_PUSH_RESULT = "last_config_push_result"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadSettings(context: Context): MobileSettings {
        val p = prefs(context)
        return MobileSettings(
            showWeekend = p.getBoolean(KEY_SHOW_WEEKEND, true),
            reminderEnabled = p.getBoolean(KEY_REMINDER_ENABLED, false),
            reminderMinutes = p.getInt(KEY_REMINDER_MINUTES, 15).coerceIn(5, 60),
            keepAliveLevel = p.getString(KEY_KEEP_ALIVE_LEVEL, "BALANCED") ?: "BALANCED",
            experimentalAccessibilityKeepAliveEnabled = p.getBoolean(KEY_EXPERIMENTAL_ACCESSIBILITY_KEEP_ALIVE_ENABLED, false),
            accountSummary = parseAccountSummary(p.getString(KEY_ACCOUNT_SUMMARY_JSON, null)),
            membershipSummary = parseMembershipSummary(p.getString(KEY_MEMBERSHIP_SUMMARY_JSON, null)),
            rawIcs = p.getString(KEY_RAW_ICS, "") ?: "",
            parseMessage = p.getString(KEY_PARSE_MESSAGE, "") ?: "",
            wearSyncMode = p.getString(KEY_WEAR_SYNC_MODE, "AUTO") ?: "AUTO",
            weekNumberMode = p.getString(KEY_WEEK_NUMBER_MODE, "NATURAL") ?: "NATURAL",
            semesterWeekStartDate = p.getString(KEY_SEMESTER_WEEK_START_DATE, "") ?: "",
            weekStartDay = p.getString(KEY_WEEK_START_DAY, "MONDAY") ?: "MONDAY",
            dailyBriefingEnabled = p.getBoolean(KEY_DAILY_BRIEFING_ENABLED, false),
            dailyBriefingChannel = DailyBriefingChannel.fromRaw(p.getString(KEY_DAILY_BRIEFING_CHANNEL, DailyBriefingChannel.APP_NOTIFICATION.name)),
            dailyBriefingTime = p.getString(KEY_DAILY_BRIEFING_TIME, "20:00") ?: "20:00",
            cloudProvider = p.getString(KEY_CLOUD_PROVIDER, "WEBDAV") ?: "WEBDAV",
            cloudSyncEnabled = p.getBoolean(KEY_CLOUD_SYNC_ENABLED, false),
            cloudServerUrl = p.getString(KEY_CLOUD_SERVER_URL, "") ?: "",
            cloudRemotePath = p.getString(KEY_CLOUD_REMOTE_PATH, "/classing/classing_sync.json") ?: "/classing/classing_sync.json",
            cloudUsername = p.getString(KEY_CLOUD_USERNAME, "") ?: "",
            cloudDriveFileName = p.getString(KEY_CLOUD_DRIVE_FILE_NAME, "classing_sync.json") ?: "classing_sync.json",
            cloudDriveTokenExpireAt = p.getLong(KEY_CLOUD_DRIVE_TOKEN_EXPIRE_AT, 0L),
            officialSyncFrequency = OfficialSyncFrequency.fromRaw(p.getString(KEY_OFFICIAL_SYNC_FREQUENCY, OfficialSyncFrequency.MANUAL_ONLY.name)),
            syncScopes = parseSyncScopes(p.getString(KEY_SYNC_SCOPES, null)),
            cloudConfigPushStatus = p.getString(KEY_CLOUD_CONFIG_PUSH_STATUS, "") ?: "",
            cloudLastResult = p.getString(KEY_CLOUD_LAST_RESULT, "") ?: "",
            cloudLastSyncedAt = p.getLong(KEY_CLOUD_LAST_SYNCED_AT, 0L),
        )
    }

    fun saveSettings(context: Context, settings: MobileSettings) {
        prefs(context).edit()
            .putBoolean(KEY_SHOW_WEEKEND, settings.showWeekend)
            .putBoolean(KEY_REMINDER_ENABLED, settings.reminderEnabled)
            .putInt(KEY_REMINDER_MINUTES, settings.reminderMinutes.coerceIn(5, 60))
            .putString(KEY_KEEP_ALIVE_LEVEL, settings.keepAliveLevel)
            .putBoolean(KEY_EXPERIMENTAL_ACCESSIBILITY_KEEP_ALIVE_ENABLED, settings.experimentalAccessibilityKeepAliveEnabled)
            .putString(KEY_ACCOUNT_SUMMARY_JSON, buildAccountSummaryJson(settings.accountSummary).toString())
            .putString(KEY_MEMBERSHIP_SUMMARY_JSON, buildMembershipSummaryJson(settings.membershipSummary).toString())
            .putString(KEY_RAW_ICS, settings.rawIcs)
            .putString(KEY_PARSE_MESSAGE, settings.parseMessage)
            .putString(KEY_WEAR_SYNC_MODE, settings.wearSyncMode)
            .putString(KEY_WEEK_NUMBER_MODE, settings.weekNumberMode)
            .putString(KEY_SEMESTER_WEEK_START_DATE, settings.semesterWeekStartDate)
            .putString(KEY_WEEK_START_DAY, settings.weekStartDay)
            .putBoolean(KEY_DAILY_BRIEFING_ENABLED, settings.dailyBriefingEnabled)
            .putString(KEY_DAILY_BRIEFING_CHANNEL, settings.dailyBriefingChannel.name)
            .putString(KEY_DAILY_BRIEFING_TIME, settings.dailyBriefingTime)
            .putString(KEY_CLOUD_PROVIDER, settings.cloudProvider)
            .putBoolean(KEY_CLOUD_SYNC_ENABLED, settings.cloudSyncEnabled)
            .putString(KEY_CLOUD_SERVER_URL, settings.cloudServerUrl)
            .putString(KEY_CLOUD_REMOTE_PATH, settings.cloudRemotePath)
            .putString(KEY_CLOUD_USERNAME, settings.cloudUsername)
            .putString(KEY_CLOUD_DRIVE_FILE_NAME, settings.cloudDriveFileName)
            .putLong(KEY_CLOUD_DRIVE_TOKEN_EXPIRE_AT, settings.cloudDriveTokenExpireAt)
            .putString(KEY_OFFICIAL_SYNC_FREQUENCY, settings.officialSyncFrequency.name)
            .putString(KEY_SYNC_SCOPES, buildSyncScopesValue(settings.syncScopes))
            .putString(KEY_CLOUD_CONFIG_PUSH_STATUS, settings.cloudConfigPushStatus)
            .putString(KEY_CLOUD_LAST_RESULT, settings.cloudLastResult)
            .putLong(KEY_CLOUD_LAST_SYNCED_AT, settings.cloudLastSyncedAt)
            .apply()
    }

    fun setCloudConfigPushStatus(context: Context, status: String) {
        prefs(context).edit()
            .putString(KEY_CLOUD_CONFIG_PUSH_STATUS, status)
            .apply()
    }

    fun saveWearSettingsSnapshot(context: Context, snapshotJson: String, updatedAt: Long) {
        prefs(context).edit()
            .putString(KEY_WEAR_SETTINGS_SNAPSHOT, snapshotJson)
            .putLong(KEY_WEAR_SETTINGS_UPDATED_AT, updatedAt)
            .apply()
    }

    fun loadWearSettingsSnapshot(context: Context): Pair<String, Long>? {
        val p = prefs(context)
        val snapshot = p.getString(KEY_WEAR_SETTINGS_SNAPSHOT, "").orEmpty()
        val updatedAt = p.getLong(KEY_WEAR_SETTINGS_UPDATED_AT, 0L)
        if (snapshot.isBlank() || updatedAt <= 0L) return null
        return snapshot to updatedAt
    }

    fun loadLessons(context: Context): List<PersistedLesson> {
        return loadTimetableState(context).baseLessons
    }

    fun loadScheduleExceptions(context: Context): List<PersistedScheduleException> {
        return loadTimetableState(context).exceptions
    }

    fun loadScheduleSnapshots(context: Context): List<PersistedScheduleSnapshot> {
        return loadTimetableState(context).snapshots
    }

    fun loadTimetableState(context: Context): PersistedTimetableState {
        val p = prefs(context)
        val baseRaw = p.getString(KEY_BASE_LESSONS_JSON, null)
        val legacyRaw = p.getString(KEY_LESSONS_JSON, null)
        val baseLessons = when {
            !baseRaw.isNullOrBlank() -> parseLessonList(baseRaw)
            !legacyRaw.isNullOrBlank() -> {
                val migrated = parseLessonList(legacyRaw)
                saveTimetableState(
                    context = context,
                    baseLessons = migrated,
                    exceptions = emptyList(),
                    snapshots = loadScheduleSnapshotsRaw(p),
                )
                migrated
            }

            else -> emptyList()
        }
        val exceptions = parseExceptionList(p.getString(KEY_SCHEDULE_EXCEPTIONS_JSON, null))
        val snapshots = loadScheduleSnapshotsRaw(p)
        return PersistedTimetableState(
            baseLessons = baseLessons,
            exceptions = exceptions,
            snapshots = snapshots,
        )
    }

    fun saveLessons(context: Context, lessons: List<PersistedLesson>) {
        saveTimetableState(
            context = context,
            baseLessons = lessons,
            exceptions = loadScheduleExceptions(context),
            snapshots = loadScheduleSnapshots(context),
        )
    }

    fun saveTimetableState(
        context: Context,
        baseLessons: List<PersistedLesson>,
        exceptions: List<PersistedScheduleException>,
        snapshots: List<PersistedScheduleSnapshot>,
    ) {
        prefs(context).edit()
            .putString(KEY_BASE_LESSONS_JSON, buildLessonArray(baseLessons).toString())
            .putString(KEY_SCHEDULE_EXCEPTIONS_JSON, buildExceptionArray(exceptions).toString())
            .putString(KEY_SCHEDULE_SNAPSHOTS_JSON, buildSnapshotArray(snapshots).toString())
            .putString(KEY_LESSONS_JSON, buildLessonArray(baseLessons).toString())
            .putLong(KEY_LAST_SNAPSHOT_AT, snapshots.maxOfOrNull { it.createdAt } ?: 0L)
            .apply()
    }

    fun saveScheduleSnapshots(context: Context, snapshots: List<PersistedScheduleSnapshot>) {
        val state = loadTimetableState(context)
        saveTimetableState(
            context = context,
            baseLessons = state.baseLessons,
            exceptions = state.exceptions,
            snapshots = snapshots,
        )
    }

    fun markLastWearPush(context: Context, attemptedAt: Long, result: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_WEAR_PUSH_AT, attemptedAt)
            .putString(KEY_LAST_WEAR_PUSH_RESULT, result)
            .apply()
    }

    fun loadLastWearPush(context: Context): Pair<Long, String> {
        val p = prefs(context)
        return p.getLong(KEY_LAST_WEAR_PUSH_AT, 0L) to p.getString(KEY_LAST_WEAR_PUSH_RESULT, "").orEmpty()
    }

    fun markLastWearAck(context: Context, ackAt: Long, result: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_WEAR_ACK_AT, ackAt)
            .putString(KEY_LAST_WEAR_ACK_RESULT, result)
            .apply()
    }

    fun loadLastWearAck(context: Context): Pair<Long, String> {
        val p = prefs(context)
        return p.getLong(KEY_LAST_WEAR_ACK_AT, 0L) to p.getString(KEY_LAST_WEAR_ACK_RESULT, "").orEmpty()
    }

    fun markLastCloudSync(context: Context, syncedAt: Long, result: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_CLOUD_SYNC_AT, syncedAt)
            .putString(KEY_LAST_CLOUD_SYNC_RESULT, result)
            .apply()
    }

    fun loadLastCloudSync(context: Context): Pair<Long, String> {
        val p = prefs(context)
        return p.getLong(KEY_LAST_CLOUD_SYNC_AT, 0L) to p.getString(KEY_LAST_CLOUD_SYNC_RESULT, "").orEmpty()
    }

    fun markLastConfigPush(context: Context, pushedAt: Long, result: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_CONFIG_PUSH_AT, pushedAt)
            .putString(KEY_LAST_CONFIG_PUSH_RESULT, result)
            .apply()
    }

    fun loadLastConfigPush(context: Context): Pair<Long, String> {
        val p = prefs(context)
        return p.getLong(KEY_LAST_CONFIG_PUSH_AT, 0L) to p.getString(KEY_LAST_CONFIG_PUSH_RESULT, "").orEmpty()
    }

    fun loadLastSnapshotAt(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SNAPSHOT_AT, 0L)
    }

    private fun parseLessonList(raw: String?): List<PersistedLesson> {
        val normalizedRaw = raw ?: return emptyList()
        return runCatching {
            val arr = JSONArray(normalizedRaw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title")
                    if (id.isBlank() || title.isBlank()) continue
                    val startWeek = item.optInt("startWeek", 1).coerceIn(1, 30)
                    add(
                        PersistedLesson(
                            id = id,
                            title = title,
                            teacher = item.optString("teacher").ifBlank { null },
                            location = item.optString("location").ifBlank { null },
                            note = item.optString("note").ifBlank { null },
                            dayOfWeek = item.optInt("dayOfWeek", 1).coerceIn(1, 7),
                            startMinute = item.optInt("startMinute", 8 * 60).coerceIn(0, 24 * 60 - 1),
                            endMinute = item.optInt("endMinute", 9 * 60).coerceIn(1, 24 * 60 - 1),
                            startWeek = startWeek,
                            endWeek = item.optInt("endWeek", 30).coerceIn(startWeek, 30),
                            weekParity = item.optString("weekParity", "ALL").uppercase().let {
                                if (it == "ODD" || it == "EVEN") it else "ALL"
                            },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildLessonArray(lessons: List<PersistedLesson>): JSONArray {
        val arr = JSONArray()
        lessons.forEach { lesson ->
            arr.put(
                JSONObject()
                    .put("id", lesson.id)
                    .put("title", lesson.title)
                    .put("teacher", lesson.teacher ?: "")
                    .put("location", lesson.location ?: "")
                    .put("note", lesson.note ?: "")
                    .put("dayOfWeek", lesson.dayOfWeek)
                    .put("startMinute", lesson.startMinute)
                    .put("endMinute", lesson.endMinute)
                    .put("startWeek", lesson.startWeek.coerceIn(1, 30))
                    .put("endWeek", lesson.endWeek.coerceIn(lesson.startWeek.coerceIn(1, 30), 30))
                    .put("weekParity", lesson.weekParity),
            )
        }
        return arr
    }

    private fun parseExceptionList(raw: String?): List<PersistedScheduleException> {
        val normalizedRaw = raw ?: return emptyList()
        return runCatching {
            val arr = JSONArray(normalizedRaw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val type = item.optString("type")
                    val date = item.optString("date")
                    if (id.isBlank() || type.isBlank() || date.isBlank()) continue
                    add(
                        PersistedScheduleException(
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
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildExceptionArray(exceptions: List<PersistedScheduleException>): JSONArray {
        val arr = JSONArray()
        exceptions.forEach { exception ->
            arr.put(
                JSONObject()
                    .put("id", exception.id)
                    .put("lessonId", exception.lessonId ?: "")
                    .put("type", exception.type)
                    .put("date", exception.date)
                    .put("title", exception.title ?: "")
                    .put("teacher", exception.teacher ?: "")
                    .put("location", exception.location ?: "")
                    .put("note", exception.note ?: "")
                    .put("dayOfWeek", exception.dayOfWeek ?: JSONObject.NULL)
                    .put("startMinute", exception.startMinute ?: JSONObject.NULL)
                    .put("endMinute", exception.endMinute ?: JSONObject.NULL),
            )
        }
        return arr
    }

    private fun loadScheduleSnapshotsRaw(prefs: android.content.SharedPreferences): List<PersistedScheduleSnapshot> {
        val raw = prefs.getString(KEY_SCHEDULE_SNAPSHOTS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        PersistedScheduleSnapshot(
                            id = id,
                            createdAt = item.optLong("createdAt", 0L),
                            reason = item.optString("reason"),
                            weekNumberMode = item.optString("weekNumberMode", "NATURAL"),
                            semesterWeekStartDate = item.optString("semesterWeekStartDate", ""),
                            baseLessons = parseLessonList(item.optJSONArray("baseLessons")?.toString()),
                            exceptions = parseExceptionList(item.optJSONArray("exceptions")?.toString()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildSnapshotArray(snapshots: List<PersistedScheduleSnapshot>): JSONArray {
        val arr = JSONArray()
        snapshots.forEach { snapshot ->
            arr.put(
                JSONObject()
                    .put("id", snapshot.id)
                    .put("createdAt", snapshot.createdAt)
                    .put("reason", snapshot.reason)
                    .put("weekNumberMode", snapshot.weekNumberMode)
                    .put("semesterWeekStartDate", snapshot.semesterWeekStartDate)
                    .put("baseLessons", buildLessonArray(snapshot.baseLessons))
                    .put("exceptions", buildExceptionArray(snapshot.exceptions)),
            )
        }
        return arr
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name)
    }

    private fun parseAccountSummary(raw: String?): AccountSummary {
        val json = runCatching { JSONObject(raw ?: "") }.getOrNull() ?: return AccountSummary()
        return AccountSummary(
            userId = json.optString("userId"),
            identifier = json.optString("identifier"),
            username = json.optString("username"),
            email = json.optString("email"),
        )
    }

    private fun parseMembershipSummary(raw: String?): MembershipSummary {
        val json = runCatching { JSONObject(raw ?: "") }.getOrNull() ?: return MembershipSummary()
        return MembershipSummary(
            isMember = json.optBoolean("isMember", false),
            tier = json.optString("tier", "FREE").ifBlank { "FREE" },
            expiresAt = json.optLong("expiresAt", 0L),
            lastCheckedAt = json.optLong("lastCheckedAt", 0L),
        )
    }

    private fun buildAccountSummaryJson(summary: AccountSummary): JSONObject {
        return JSONObject()
            .put("userId", summary.userId)
            .put("identifier", summary.identifier)
            .put("username", summary.username)
            .put("email", summary.email)
    }

    private fun buildMembershipSummaryJson(summary: MembershipSummary): JSONObject {
        return JSONObject()
            .put("isMember", summary.isMember)
            .put("tier", summary.tier)
            .put("expiresAt", summary.expiresAt)
            .put("lastCheckedAt", summary.lastCheckedAt)
    }

    private fun parseSyncScopes(raw: String?): Set<SyncScope> {
        val parsed = raw
            ?.split(',')
            ?.mapNotNull { SyncScope.fromRaw(it) }
            ?.toSet()
            .orEmpty()
        return if (parsed.isEmpty()) {
            setOf(SyncScope.TIMETABLE, SyncScope.MOBILE_SETTINGS, SyncScope.WEAR_SETTINGS)
        } else {
            parsed
        }
    }

    private fun buildSyncScopesValue(scopes: Set<SyncScope>): String {
        val safe = if (scopes.isEmpty()) {
            setOf(SyncScope.TIMETABLE, SyncScope.MOBILE_SETTINGS, SyncScope.WEAR_SETTINGS)
        } else {
            scopes
        }
        return safe.sortedBy { it.ordinal }.joinToString(",") { it.name }
    }

    fun markLocalTimetableUpdated(context: Context, updatedAt: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LOCAL_TIMETABLE_UPDATED_AT, updatedAt).apply()
    }

    fun markLocalMobileSettingsUpdated(context: Context, updatedAt: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LOCAL_MOBILE_SETTINGS_UPDATED_AT, updatedAt).apply()
    }

    fun loadLocalTimetableUpdatedAt(context: Context): Long {
        return prefs(context).getLong(KEY_LOCAL_TIMETABLE_UPDATED_AT, 0L)
    }

    fun loadLocalMobileSettingsUpdatedAt(context: Context): Long {
        return prefs(context).getLong(KEY_LOCAL_MOBILE_SETTINGS_UPDATED_AT, 0L)
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean = true) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun ensureOnboardingCompletedForLegacyUser(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_ONBOARDING_COMPLETED, false)) return false
        val hasLegacyData = hasLegacyUserData(p)
        if (!hasLegacyData) return false
        p.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        return true
    }

    private fun hasLegacyUserData(p: android.content.SharedPreferences): Boolean {
        val lessonsJson = p.getString(KEY_LESSONS_JSON, null).orEmpty()
        if (lessonsJson.isNotBlank() && lessonsJson != "[]") return true
        if (p.getString(KEY_RAW_ICS, "").orEmpty().isNotBlank()) return true
        if (p.getString(KEY_CLOUD_SERVER_URL, "").orEmpty().isNotBlank()) return true
        if (p.getString(KEY_CLOUD_USERNAME, "").orEmpty().isNotBlank()) return true
        if (p.contains(KEY_PARSE_MESSAGE) && p.getString(KEY_PARSE_MESSAGE, "").orEmpty().isNotBlank()) return true
        return false
    }
}

