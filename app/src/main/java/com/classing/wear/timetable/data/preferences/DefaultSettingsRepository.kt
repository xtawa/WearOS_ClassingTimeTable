package com.classing.wear.timetable.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.classing.wear.timetable.domain.model.KeepAliveLevel
import com.classing.wear.timetable.widget.WearSurfaceUpdateRequester
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.classing.wear.timetable.domain.repository.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

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
                showAiOnHome = pref[KEY_SHOW_AI_ON_HOME] ?: true,
                keepAliveLevel = KeepAliveLevel.fromRaw(pref[KEY_KEEP_ALIVE_LEVEL]),
                tileShowTeacher = pref[KEY_TILE_SHOW_TEACHER] ?: true,
                tileShowLocation = pref[KEY_TILE_SHOW_LOCATION] ?: true,
                tileShowCountdown = pref[KEY_TILE_SHOW_COUNTDOWN] ?: true,
                tileShowCourseName = pref[KEY_TILE_SHOW_COURSE_NAME] ?: true,
                tileShowCurrentWeek = pref[KEY_TILE_SHOW_CURRENT_WEEK] ?: true,
                tileShowTimeRange = pref[KEY_TILE_SHOW_TIME_RANGE] ?: true,
            )
        }
    }

    override suspend fun exportWearSettingsSnapshot(): String {
        val pref = dataStore.data.first()
        return JSONObject()
            .put("dynamicColor", pref[KEY_DYNAMIC_COLOR] ?: true)
            .put("remindersEnabled", pref[KEY_REMINDER] ?: true)
            .put("autoSync", pref[KEY_AUTO_SYNC] ?: true)
            .put("showWeekend", pref[KEY_SHOW_WEEKEND] ?: true)
            .put("showCompletedToday", pref[KEY_SHOW_COMPLETED_TODAY] ?: false)
            .put("showAiOnHome", pref[KEY_SHOW_AI_ON_HOME] ?: true)
            .put("keepAliveLevel", pref[KEY_KEEP_ALIVE_LEVEL] ?: KeepAliveLevel.BALANCED.name)
            .put("tileShowTeacher", pref[KEY_TILE_SHOW_TEACHER] ?: true)
            .put("tileShowLocation", pref[KEY_TILE_SHOW_LOCATION] ?: true)
            .put("tileShowCountdown", pref[KEY_TILE_SHOW_COUNTDOWN] ?: true)
            .put("tileShowCourseName", pref[KEY_TILE_SHOW_COURSE_NAME] ?: true)
            .put("tileShowCurrentWeek", pref[KEY_TILE_SHOW_CURRENT_WEEK] ?: true)
            .put("tileShowTimeRange", pref[KEY_TILE_SHOW_TIME_RANGE] ?: true)
            .toString()
    }

    override suspend fun applyWearSettingsSnapshot(snapshotJson: String) {
        if (snapshotJson.isBlank()) return
        val raw = runCatching { JSONObject(snapshotJson) }.getOrNull() ?: return
        dataStore.edit {
            it[KEY_DYNAMIC_COLOR] = raw.optBoolean("dynamicColor", it[KEY_DYNAMIC_COLOR] ?: true)
            it[KEY_REMINDER] = raw.optBoolean("remindersEnabled", it[KEY_REMINDER] ?: true)
            it[KEY_AUTO_SYNC] = raw.optBoolean("autoSync", it[KEY_AUTO_SYNC] ?: true)
            it[KEY_SHOW_WEEKEND] = raw.optBoolean("showWeekend", it[KEY_SHOW_WEEKEND] ?: true)
            it[KEY_SHOW_COMPLETED_TODAY] = raw.optBoolean("showCompletedToday", it[KEY_SHOW_COMPLETED_TODAY] ?: false)
            it[KEY_SHOW_AI_ON_HOME] = raw.optBoolean("showAiOnHome", it[KEY_SHOW_AI_ON_HOME] ?: true)
            it[KEY_KEEP_ALIVE_LEVEL] = KeepAliveLevel.fromRaw(
                raw.optString("keepAliveLevel", it[KEY_KEEP_ALIVE_LEVEL] ?: KeepAliveLevel.BALANCED.name),
            ).name
            it[KEY_TILE_SHOW_TEACHER] = raw.optBoolean("tileShowTeacher", it[KEY_TILE_SHOW_TEACHER] ?: true)
            it[KEY_TILE_SHOW_LOCATION] = raw.optBoolean("tileShowLocation", it[KEY_TILE_SHOW_LOCATION] ?: true)
            it[KEY_TILE_SHOW_COUNTDOWN] = raw.optBoolean("tileShowCountdown", it[KEY_TILE_SHOW_COUNTDOWN] ?: true)
            it[KEY_TILE_SHOW_COURSE_NAME] = raw.optBoolean("tileShowCourseName", it[KEY_TILE_SHOW_COURSE_NAME] ?: true)
            it[KEY_TILE_SHOW_CURRENT_WEEK] = raw.optBoolean("tileShowCurrentWeek", it[KEY_TILE_SHOW_CURRENT_WEEK] ?: true)
            it[KEY_TILE_SHOW_TIME_RANGE] = raw.optBoolean("tileShowTimeRange", it[KEY_TILE_SHOW_TIME_RANGE] ?: true)
        }
        WearSurfaceUpdateRequester.requestAll(context)
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

    override suspend fun setShowAiOnHome(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_AI_ON_HOME] = enabled }
    }

    override suspend fun setKeepAliveLevel(level: KeepAliveLevel) {
        dataStore.edit { it[KEY_KEEP_ALIVE_LEVEL] = level.name }
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
        private val KEY_SHOW_AI_ON_HOME = booleanPreferencesKey("show_ai_on_home")
        private val KEY_KEEP_ALIVE_LEVEL = stringPreferencesKey("keep_alive_level")
        private val KEY_TILE_SHOW_TEACHER = booleanPreferencesKey("tile_show_teacher")
        private val KEY_TILE_SHOW_LOCATION = booleanPreferencesKey("tile_show_location")
        private val KEY_TILE_SHOW_COUNTDOWN = booleanPreferencesKey("tile_show_countdown")
        private val KEY_TILE_SHOW_COURSE_NAME = booleanPreferencesKey("tile_show_course_name")
        private val KEY_TILE_SHOW_CURRENT_WEEK = booleanPreferencesKey("tile_show_current_week")
        private val KEY_TILE_SHOW_TIME_RANGE = booleanPreferencesKey("tile_show_time_range")
    }
}
