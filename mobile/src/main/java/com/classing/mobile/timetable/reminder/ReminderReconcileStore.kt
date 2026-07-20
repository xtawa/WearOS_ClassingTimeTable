package com.xtawa.classingtime.reminder

import android.content.Context
import org.json.JSONObject

data class ReminderReconcileState(
    val desiredReminderKey: String? = null,
    val desiredRequestCode: Int = 0,
    val desiredTriggerAtMillis: Long = 0L,
    val scheduledReminderKey: String? = null,
    val scheduledRequestCode: Int = 0,
    val scheduledTriggerAtMillis: Long = 0L,
    val lastFailure: String? = null,
    val lastReconcileAt: Long = 0L,
)

object ReminderReconcileStore {
    private const val PREF_NAME = "mobile_reminder_reconcile"
    private const val KEY_STATE = "state_json"

    fun load(context: Context): ReminderReconcileState {
        val raw = prefs(context).getString(KEY_STATE, null).orEmpty()
        if (raw.isBlank()) return ReminderReconcileState()
        return runCatching {
            val json = JSONObject(raw)
            ReminderReconcileState(
                desiredReminderKey = json.optString("desiredReminderKey").ifBlank { null },
                desiredRequestCode = json.optInt("desiredRequestCode", 0),
                desiredTriggerAtMillis = json.optLong("desiredTriggerAtMillis", 0L),
                scheduledReminderKey = json.optString("scheduledReminderKey").ifBlank { null },
                scheduledRequestCode = json.optInt("scheduledRequestCode", 0),
                scheduledTriggerAtMillis = json.optLong("scheduledTriggerAtMillis", 0L),
                lastFailure = json.optString("lastFailure").ifBlank { null },
                lastReconcileAt = json.optLong("lastReconcileAt", 0L),
            )
        }.getOrDefault(ReminderReconcileState())
    }

    fun save(context: Context, state: ReminderReconcileState) {
        val json = JSONObject()
            .put("desiredReminderKey", state.desiredReminderKey ?: "")
            .put("desiredRequestCode", state.desiredRequestCode)
            .put("desiredTriggerAtMillis", state.desiredTriggerAtMillis)
            .put("scheduledReminderKey", state.scheduledReminderKey ?: "")
            .put("scheduledRequestCode", state.scheduledRequestCode)
            .put("scheduledTriggerAtMillis", state.scheduledTriggerAtMillis)
            .put("lastFailure", state.lastFailure ?: "")
            .put("lastReconcileAt", state.lastReconcileAt)
        prefs(context).edit().putString(KEY_STATE, json.toString()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_STATE).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
