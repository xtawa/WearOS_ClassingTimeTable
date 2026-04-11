package com.xtawa.classingtime.sync

import com.classing.shared.sync.CloudSyncContracts
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
    val settings: JSONObject,
)

data class CloudTimetableSnapshot(
    val updatedAt: Long,
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
                        .put("location", lesson.location.orEmpty())
                        .put("note", lesson.note.orEmpty())
                        .put("dayOfWeek", lesson.dayOfWeek)
                        .put("startMinute", lesson.startMinute)
                        .put("endMinute", lesson.endMinute),
                )
            }
            root.put(
                CloudSyncContracts.KEY_TIMETABLE,
                JSONObject()
                    .put(CloudSyncContracts.KEY_UPDATED_AT, table.updatedAt)
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
                    .put(CloudSyncContracts.KEY_SETTINGS_PAYLOAD, snapshot.settings),
            )
        }
        wearSettings?.let { snapshot ->
            root.put(
                CloudSyncContracts.KEY_WEAR_SETTINGS,
                JSONObject()
                    .put(CloudSyncContracts.KEY_NAMESPACE_UPDATED_AT, snapshot.updatedAt)
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
                        val id = item.optString("id").ifBlank { continue }
                        val title = item.optString("title").ifBlank { continue }
                        add(
                            PersistedLesson(
                                id = id,
                                title = title,
                                location = item.optString("location").ifBlank { null },
                                note = item.optString("note").ifBlank { null },
                                dayOfWeek = item.optInt("dayOfWeek", 1).coerceIn(1, 7),
                                startMinute = item.optInt("startMinute", 8 * 60).coerceIn(0, 24 * 60 - 1),
                                endMinute = item.optInt("endMinute", 9 * 60).coerceIn(1, 24 * 60 - 1),
                            ),
                        )
                    }
                }
                CloudTimetableSnapshot(
                    updatedAt = raw.optLong(CloudSyncContracts.KEY_UPDATED_AT, 0L),
                    weekNumberMode = raw.optString(CloudSyncContracts.KEY_WEEK_NUMBER_MODE, "NATURAL"),
                    semesterWeekStartDate = raw.optString(CloudSyncContracts.KEY_SEMESTER_WEEK_START_DATE, ""),
                    lessons = lessons,
                )
            }

            fun parseNamespace(name: String): CloudNamespaceSnapshot? {
                val obj = json.optJSONObject(name) ?: return null
                return CloudNamespaceSnapshot(
                    updatedAt = obj.optLong(CloudSyncContracts.KEY_NAMESPACE_UPDATED_AT, 0L),
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
        .put("wearSyncMode", wearSyncMode)
        .put("weekNumberMode", weekNumberMode)
        .put("semesterWeekStartDate", semesterWeekStartDate)
}

fun MobileSettings.toCloudConfigPayload(password: String): JSONObject {
    return JSONObject()
        .put("enabled", cloudSyncEnabled)
        .put("serverUrl", cloudServerUrl.trim())
        .put("remotePath", cloudRemotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH })
        .put("username", cloudUsername.trim())
        .put("password", password)
}
