package com.xtawa.classingtime.metrics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ProductEvent { CREATION_STARTED, CREATION_COMPLETED, IMPORT_CORRECTED, SYNC_ATTEMPT, SYNC_FAILED, SYNC_APPLIED, NEXT_CLASS_VIEWED, ACTIVE_DAY }

/** Explicit opt-in, on-device only. No titles, rooms, accounts, tokens or error text. */
object ProductMetrics {
    private const val PREF = "product_metrics_local"
    private val lock = Any()
    fun enabled(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!enabled) prefs.edit().clear().commit()
        else prefs.edit().putBoolean("enabled", true).putString("installation", prefs.getString("installation", null) ?: UUID.randomUUID().toString()).commit()
    }
    fun record(context: Context, event: ProductEvent, elapsedMs: Long = 0, count: Int = 0, now: Long = System.currentTimeMillis(), sessionId: String = "") = synchronized(lock) {
        if (!enabled(context)) return@synchronized
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString("events", "[]")) }.getOrDefault(JSONArray())
        val day = TimeUnit.MILLISECONDS.toDays(now)
        val kept = JSONArray()
        for (i in maxOf(0, old.length() - 999) until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optLong("day") >= day - 28) kept.put(item)
        }
        if (event != ProductEvent.ACTIVE_DAY || (0 until kept.length()).none { kept.getJSONObject(it).let { row -> row.optString("event") == event.name && row.optLong("day") == day } })
            kept.put(JSONObject().put("event", event.name).put("day", day).put("elapsedMs", elapsedMs.coerceIn(0, TimeUnit.HOURS.toMillis(24))).put("count", count.coerceIn(0, 10000)).put("session", sessionId.takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) }.orEmpty()))
        prefs.edit().putString("events", kept.toString()).commit()
    }
    fun export(context: Context): String = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        JSONObject().put("format", "classing_product_metrics_v1").put("installation", prefs.getString("installation", ""))
            .put("events", JSONArray(prefs.getString("events", "[]"))).toString(2)
    }
}
