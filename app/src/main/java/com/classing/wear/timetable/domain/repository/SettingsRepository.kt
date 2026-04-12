package com.classing.wear.timetable.domain.repository

import com.classing.wear.timetable.domain.model.KeepAliveLevel
import kotlinx.coroutines.flow.Flow

data class UserPreferences(
    val dynamicColor: Boolean = true,
    val remindersEnabled: Boolean = true,
    val autoSync: Boolean = true,
    val showWeekend: Boolean = true,
    val showCompletedToday: Boolean = false,
    val keepAliveLevel: KeepAliveLevel = KeepAliveLevel.BALANCED,
    val experimentalAccessibilityKeepAliveEnabled: Boolean = false,
    val tileShowTeacher: Boolean = true,
    val tileShowLocation: Boolean = true,
    val tileShowCountdown: Boolean = true,
    val tileShowCourseName: Boolean = true,
    val tileShowCurrentWeek: Boolean = true,
    val tileShowTimeRange: Boolean = true,
)

interface SettingsRepository {
    fun observePreferences(): Flow<UserPreferences>
    suspend fun exportWearSettingsSnapshot(): String
    suspend fun applyWearSettingsSnapshot(snapshotJson: String)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setAutoSync(enabled: Boolean)
    suspend fun setShowWeekend(enabled: Boolean)
    suspend fun setShowCompletedToday(enabled: Boolean)
    suspend fun setKeepAliveLevel(level: KeepAliveLevel)
    suspend fun setExperimentalAccessibilityKeepAliveEnabled(enabled: Boolean)
    suspend fun setTileShowTeacher(enabled: Boolean)
    suspend fun setTileShowLocation(enabled: Boolean)
    suspend fun setTileShowCountdown(enabled: Boolean)
    suspend fun setTileShowCourseName(enabled: Boolean)
    suspend fun setTileShowCurrentWeek(enabled: Boolean)
    suspend fun setTileShowTimeRange(enabled: Boolean)
}
