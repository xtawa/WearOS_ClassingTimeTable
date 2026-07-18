package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.wear.timetable.account.WearDirectAccountStore

/** Persists the explicit cloud-only choice; a valid official-cloud account is always required. */
object WearSyncModeStore {
    const val PREF_NAME = "wear_sync_mode"
    const val KEY_INDEPENDENT_MODE = "independent_mode"

    fun isIndependentModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INDEPENDENT_MODE, false)
    }

    fun setIndependentMode(context: Context, enabled: Boolean): Boolean {
        val accepted = enabled && WearDirectAccountStore.load(context) != null
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INDEPENDENT_MODE, accepted)
            .apply()
        return accepted
    }
}
