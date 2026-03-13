package com.classing.wear.timetable.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.domain.repository.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("settings.preferences_pb") },
    )

    override fun observePreferences(): Flow<UserPreferences> {
        return dataStore.data.map { pref ->
            UserPreferences(
                dynamicColor = pref[KEY_DYNAMIC_COLOR] ?: true,
                remindersEnabled = pref[KEY_REMINDER] ?: true,
                autoSync = pref[KEY_AUTO_SYNC] ?: true,
                showWeekend = pref[KEY_SHOW_WEEKEND] ?: true,
            )
        }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_REMINDER] = enabled }
    }

    override suspend fun setAutoSync(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_SYNC] = enabled }
    }

    override suspend fun setShowWeekend(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_WEEKEND] = enabled }
    }

    companion object {
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_REMINDER = booleanPreferencesKey("reminder_enabled")
        private val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync")
        private val KEY_SHOW_WEEKEND = booleanPreferencesKey("show_weekend")
    }
}
