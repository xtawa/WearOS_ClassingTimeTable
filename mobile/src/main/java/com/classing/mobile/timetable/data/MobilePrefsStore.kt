package com.xtawa.classingtime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PersistedLesson(
    val id: String,
    val title: String,
    val location: String?,
    val note: String?,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
)

data class MobileSettings(
    val showWeekend: Boolean,
    val reminderEnabled: Boolean,
    val reminderMinutes: Int,
    val rawIcs: String,
    val parseMessage: String,
    val wearSyncMode: String,
)

object MobilePrefsStore {
    private const val PREF_NAME = "mobile_timetable_prefs"
    private const val KEY_SHOW_WEEKEND = "show_weekend"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_MINUTES = "reminder_minutes"
    private const val KEY_RAW_ICS = "raw_ics"
    private const val KEY_PARSE_MESSAGE = "parse_message"
    private const val KEY_WEAR_SYNC_MODE = "wear_sync_mode"
    private const val KEY_LESSONS_JSON = "lessons_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadSettings(context: Context): MobileSettings {
        val p = prefs(context)
        return MobileSettings(
            showWeekend = p.getBoolean(KEY_SHOW_WEEKEND, true),
            reminderEnabled = p.getBoolean(KEY_REMINDER_ENABLED, false),
            reminderMinutes = p.getInt(KEY_REMINDER_MINUTES, 15).coerceIn(5, 60),
            rawIcs = p.getString(KEY_RAW_ICS, "") ?: "",
            parseMessage = p.getString(KEY_PARSE_MESSAGE, "") ?: "",
            wearSyncMode = p.getString(KEY_WEAR_SYNC_MODE, "WEARABLE_API") ?: "WEARABLE_API",
        )
    }

    fun saveSettings(context: Context, settings: MobileSettings) {
        prefs(context).edit()
            .putBoolean(KEY_SHOW_WEEKEND, settings.showWeekend)
            .putBoolean(KEY_REMINDER_ENABLED, settings.reminderEnabled)
            .putInt(KEY_REMINDER_MINUTES, settings.reminderMinutes.coerceIn(5, 60))
            .putString(KEY_RAW_ICS, settings.rawIcs)
            .putString(KEY_PARSE_MESSAGE, settings.parseMessage)
            .putString(KEY_WEAR_SYNC_MODE, settings.wearSyncMode)
            .apply()
    }

    fun loadLessons(context: Context): List<PersistedLesson> {
        val raw = prefs(context).getString(KEY_LESSONS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title")
                    if (id.isBlank() || title.isBlank()) continue
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
        }.getOrDefault(emptyList())
    }

    fun saveLessons(context: Context, lessons: List<PersistedLesson>) {
        val arr = JSONArray()
        lessons.forEach { lesson ->
            arr.put(
                JSONObject()
                    .put("id", lesson.id)
                    .put("title", lesson.title)
                    .put("location", lesson.location ?: "")
                    .put("note", lesson.note ?: "")
                    .put("dayOfWeek", lesson.dayOfWeek)
                    .put("startMinute", lesson.startMinute)
                    .put("endMinute", lesson.endMinute),
            )
        }
        prefs(context).edit().putString(KEY_LESSONS_JSON, arr.toString()).apply()
    }
}

