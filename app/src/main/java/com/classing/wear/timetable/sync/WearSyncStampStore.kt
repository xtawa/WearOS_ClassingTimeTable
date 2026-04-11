package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.SyncDomain
import com.classing.shared.sync.SyncSource
import com.classing.shared.sync.SyncStamp

object WearSyncStampStore {
    private const val PREF_NAME = "wear_sync_stamps"

    private const val KEY_LAST_DECISION_DOMAIN = "last_decision_domain"
    private const val KEY_LAST_DECISION = "last_decision"
    private const val KEY_LAST_DECISION_REASON = "last_decision_reason"
    private const val KEY_LAST_DECISION_AT = "last_decision_at"

    fun load(context: Context, domain: SyncDomain): SyncStamp? {
        val prefs = prefs(context)
        val prefix = domain.keyPrefix()
        val revision = prefs.getLong("${prefix}_revision", 0L)
        if (revision <= 0L) return null
        val source = SyncSource.fromWire(prefs.getString("${prefix}_source", SyncSource.UNKNOWN.wireValue))
        val appliedAt = prefs.getLong("${prefix}_applied_at", 0L).takeIf { it > 0L } ?: revision
        return SyncStamp(revision = revision, source = source, appliedAt = appliedAt)
    }

    fun save(context: Context, domain: SyncDomain, stamp: SyncStamp) {
        val prefix = domain.keyPrefix()
        prefs(context).edit()
            .putLong("${prefix}_revision", stamp.revision)
            .putString("${prefix}_source", stamp.source.wireValue)
            .putLong("${prefix}_applied_at", stamp.appliedAt)
            .apply()
    }

    fun saveDecision(
        context: Context,
        domain: SyncDomain,
        decision: String,
        reason: String,
        at: Long = System.currentTimeMillis(),
    ) {
        prefs(context).edit()
            .putString(KEY_LAST_DECISION_DOMAIN, domain.name)
            .putString(KEY_LAST_DECISION, decision)
            .putString(KEY_LAST_DECISION_REASON, reason)
            .putLong(KEY_LAST_DECISION_AT, at)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun SyncDomain.keyPrefix(): String {
        return when (this) {
            SyncDomain.TIMETABLE -> "timetable"
            SyncDomain.MOBILE_SETTINGS -> "mobile_settings"
            SyncDomain.WEAR_SETTINGS -> "wear_settings"
        }
    }
}
