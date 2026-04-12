package com.xtawa.classingtime.sync

import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.sync.SyncSource
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.data.PersistedLesson
import org.json.JSONArray
import org.json.JSONObject

data class WebDavConfig(
    val enabled: Boolean,
    val serverUrl: String,
    val remotePath: String,
    val username: String,
    val password: String,
) {
    fun isComplete(): Boolean {
        return enabled &&
            serverUrl.startsWith("https://", ignoreCase = true) &&
            remotePath.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank()
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
                        .put("startWeek", lesson.startWeek.coerceIn(1, 30))
                        .put("endWeek", lesson.endWeek.coerceIn(lesson.startWeek.coerceIn(1, 30), 30))
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
                        val item = lessonsArray.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank()) continue
                        val title = item.optString("title")
                        if (title.isBlank()) continue
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

fun MobileSettings.toWebDavConfig(password: String): WebDavConfig {
    return WebDavConfig(
        enabled = cloudSyncEnabled,
        serverUrl = cloudServerUrl.trim(),
        remotePath = cloudRemotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH },
        username = cloudUsername.trim(),
        password = password,
    )
}

fun MobileSettings.toMobileSettingsSnapshotJson(): JSONObject {
    return JSONObject()
        .put("showWeekend", showWeekend)
        .put("reminderEnabled", reminderEnabled)
        .put("reminderMinutes", reminderMinutes)
        .put("keepAliveLevel", keepAliveLevel)
        .put("experimentalAccessibilityKeepAliveEnabled", experimentalAccessibilityKeepAliveEnabled)
        .put("wearSyncMode", wearSyncMode)
        .put("weekNumberMode", weekNumberMode)
        .put("semesterWeekStartDate", semesterWeekStartDate)
        .put("weekStartDay", weekStartDay)
}

fun MobileSettings.toCloudConfigPayload(password: String): JSONObject {
    return JSONObject()
        .put("enabled", cloudSyncEnabled)
        .put("serverUrl", cloudServerUrl.trim())
        .put("remotePath", cloudRemotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH })
        .put("username", cloudUsername.trim())
        .put("password", password)
}
