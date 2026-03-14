package com.xtawa.classingtime.sync

import android.content.Context
import org.json.JSONObject

data class WearSyncAckInfo(
    val syncedAtMillis: Long,
    val success: Boolean,
    val appliedLessonCount: Int,
    val source: String,
    val errorMessage: String,
)

object WearSyncAckStore {
    const val PATH_SYNC_ACK = "/classing/wear_sync_ack"

    private const val PREF_NAME = "mobile_wear_sync_ack"
    private const val KEY_SYNCED_AT = "synced_at"
    private const val KEY_SUCCESS = "success"
    private const val KEY_APPLIED_COUNT = "applied_lesson_count"
    private const val KEY_SOURCE = "source"
    private const val KEY_ERROR = "error"

    fun load(context: Context): WearSyncAckInfo? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val syncedAt = prefs.getLong(KEY_SYNCED_AT, 0L)
        if (syncedAt <= 0L) return null
        return WearSyncAckInfo(
            syncedAtMillis = syncedAt,
            success = prefs.getBoolean(KEY_SUCCESS, false),
            appliedLessonCount = prefs.getInt(KEY_APPLIED_COUNT, 0),
            source = prefs.getString(KEY_SOURCE, "").orEmpty(),
            errorMessage = prefs.getString(KEY_ERROR, "").orEmpty(),
        )
    }

    fun save(context: Context, ack: WearSyncAckInfo) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SYNCED_AT, ack.syncedAtMillis)
            .putBoolean(KEY_SUCCESS, ack.success)
            .putInt(KEY_APPLIED_COUNT, ack.appliedLessonCount)
            .putString(KEY_SOURCE, ack.source)
            .putString(KEY_ERROR, ack.errorMessage)
            .apply()
    }

    fun parse(raw: String): WearSyncAckInfo? {
        if (raw.isBlank()) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val syncedAt = json.optLong("syncedAt", 0L).takeIf { it > 0L } ?: return null
        return WearSyncAckInfo(
            syncedAtMillis = syncedAt,
            success = json.optBoolean("success", false),
            appliedLessonCount = json.optInt("appliedLessonCount", 0).coerceAtLeast(0),
            source = json.optString("source", "").orEmpty(),
            errorMessage = json.optString("error", "").orEmpty(),
        )
    }
}
