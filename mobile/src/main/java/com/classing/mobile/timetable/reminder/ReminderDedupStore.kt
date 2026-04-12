package com.xtawa.classingtime.reminder

import android.content.Context
import org.json.JSONArray

object ReminderDedupStore {
    fun load(context: Context, todayKey: String): Set<String> {
        val prefs = context.getSharedPreferences(ReminderRuntime.DEDUP_PREF_NAME, Context.MODE_PRIVATE)
        val date = prefs.getString(ReminderRuntime.DEDUP_KEY_NOTIFIED_DATE, null)
        if (date != todayKey) return emptySet()
        val raw = prefs.getString(ReminderRuntime.DEDUP_KEY_NOTIFIED_KEYS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.optString(i))
                }
            }
        }.getOrDefault(emptySet())
    }

    fun save(context: Context, todayKey: String, keys: Set<String>) {
        val arr = JSONArray()
        keys.forEach { arr.put(it) }
        context.getSharedPreferences(ReminderRuntime.DEDUP_PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ReminderRuntime.DEDUP_KEY_NOTIFIED_DATE, todayKey)
            .putString(ReminderRuntime.DEDUP_KEY_NOTIFIED_KEYS, arr.toString())
            .apply()
    }
}
