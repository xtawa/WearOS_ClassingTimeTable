package com.classing.wear.timetable.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.classing.wear.timetable.widget.WearSurfaceUpdateRequester
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.domain.repository.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultSettingsRepository(
    private val context: Context,
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
                showCompletedToday = pref[KEY_SHOW_COMPLETED_TODAY] ?: false,
                tileShowTeacher = pref[KEY_TILE_SHOW_TEACHER] ?: true,
                tileShowLocation = pref[KEY_TILE_SHOW_LOCATION] ?: true,
                tileShowCountdown = pref[KEY_TILE_SHOW_COUNTDOWN] ?: true,
                tileShowCourseName = pref[KEY_TILE_SHOW_COURSE_NAME] ?: true,
                tileShowCurrentWeek = pref[KEY_TILE_SHOW_CURRENT_WEEK] ?: true,
                tileShowTimeRange = pref[KEY_TILE_SHOW_TIME_RANGE] ?: true,
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

    override suspend fun setShowCompletedToday(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_COMPLETED_TODAY] = enabled }
    }

    override suspend fun setTileShowTeacher(enabled: Boolean) {
        dataStore.edit { it[KEY_TILE_SHOW_TEACHER] = enabled }
        WearSurfaceUpdateRequester.requestAll(context)
    }

    override suspend fun setTileShowLocation(enabled: Boolean) {
        dataStore.edit { it[KEY_TILE_SHOW_LOCATION] = enabled }
        WearSurfaceUpdateRequester.requestAll(context)
    }

    override suspend fun setTileShowCountdown(enabled: Boolean) {
        dataStore.edit { it[KEY_TILE_SHOW_COUNTDOWN] = enabled }
        WearSurfaceUpdateRequester.requestAll(context)
    }

    override suspend fun setTileShowCourseName(enabled: Boolean) {
        dataStore.edit { it[KEY_TILE_SHOW_COURSE_NAME] = enabled }
        WearSurfaceUpdateRequester.requestAll(context)
    }

    override suspend fun setTileShowCurrentWeek(enabled: Boolean) {
        dataStore.edit { it[KEY_TILE_SHOW_CURRENT_WEEK] = enabled }
        WearSurfaceUpdateRequester.requestAll(context)
    }

    override suspend fun setTileShowTimeRange(enabled: Boolean) {
        dataStore.edit { it[KEY_TILE_SHOW_TIME_RANGE] = enabled }
        WearSurfaceUpdateRequester.requestAll(context)
    }

    companion object {
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_REMINDER = booleanPreferencesKey("reminder_enabled")
        private val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync")
        private val KEY_SHOW_WEEKEND = booleanPreferencesKey("show_weekend")
        private val KEY_SHOW_COMPLETED_TODAY = booleanPreferencesKey("show_completed_today")
        private val KEY_TILE_SHOW_TEACHER = booleanPreferencesKey("tile_show_teacher")
        private val KEY_TILE_SHOW_LOCATION = booleanPreferencesKey("tile_show_location")
        private val KEY_TILE_SHOW_COUNTDOWN = booleanPreferencesKey("tile_show_countdown")
        private val KEY_TILE_SHOW_COURSE_NAME = booleanPreferencesKey("tile_show_course_name")
        private val KEY_TILE_SHOW_CURRENT_WEEK = booleanPreferencesKey("tile_show_current_week")
        private val KEY_TILE_SHOW_TIME_RANGE = booleanPreferencesKey("tile_show_time_range")
    }
}
