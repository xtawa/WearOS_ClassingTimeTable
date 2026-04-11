package com.classing.wear.timetable.sync

import com.classing.shared.sync.CloudSyncContracts
import org.json.JSONObject

data class WearCloudLesson(
    val title: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val location: String,
    val note: String,
)

data class WearCloudTimetable(
    val updatedAt: Long,
    val weekNumberMode: String,
    val semesterWeekStartDate: String,
    val lessons: List<WearCloudLesson>,
)

data class WearCloudDocument(
    val timetable: WearCloudTimetable?,
    val wearSettingsUpdatedAt: Long,
    val wearSettingsPayload: String,
)

object WearCloudDocumentParser {
    fun parse(raw: String): WearCloudDocument {
        val root = JSONObject(raw)
        val timetable = root.optJSONObject(CloudSyncContracts.KEY_TIMETABLE)?.let { t ->
            val lessonsArray = t.optJSONArray(CloudSyncContracts.KEY_LESSONS)
            val lessons = buildList {
                if (lessonsArray != null) {
                    for (i in 0 until lessonsArray.length()) {
                        val item = lessonsArray.optJSONObject(i) ?: continue
                        add(
                            WearCloudLesson(
                                title = item.optString("title").ifBlank { "Course-${i + 1}" },
                                dayOfWeek = item.optInt("dayOfWeek", 1).coerceIn(1, 7),
                                startMinute = item.optInt("startMinute", 8 * 60).coerceIn(0, 24 * 60 - 1),
                                endMinute = item.optInt("endMinute", 9 * 60).coerceIn(1, 24 * 60 - 1),
                                location = item.optString("location", ""),
                                note = item.optString("note", ""),
                            ),
                        )
                    }
                }
            }
            WearCloudTimetable(
                updatedAt = t.optLong(CloudSyncContracts.KEY_UPDATED_AT, 0L),
                weekNumberMode = t.optString(CloudSyncContracts.KEY_WEEK_NUMBER_MODE, "NATURAL"),
                semesterWeekStartDate = t.optString(CloudSyncContracts.KEY_SEMESTER_WEEK_START_DATE, ""),
                lessons = lessons,
            )
        }

        val wearSettingsObject = root.optJSONObject(CloudSyncContracts.KEY_WEAR_SETTINGS)
        val wearSettingsUpdatedAt = wearSettingsObject?.optLong(CloudSyncContracts.KEY_NAMESPACE_UPDATED_AT, 0L) ?: 0L
        val wearSettingsPayload = wearSettingsObject
            ?.optJSONObject(CloudSyncContracts.KEY_SETTINGS_PAYLOAD)
            ?.toString()
            .orEmpty()

        return WearCloudDocument(
            timetable = timetable,
            wearSettingsUpdatedAt = wearSettingsUpdatedAt,
            wearSettingsPayload = wearSettingsPayload,
        )
    }
}
