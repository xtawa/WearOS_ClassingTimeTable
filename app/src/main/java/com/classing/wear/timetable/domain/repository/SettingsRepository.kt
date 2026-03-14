package com.classing.wear.timetable.domain.repository

import kotlinx.coroutines.flow.Flow

data class UserPreferences(
    val dynamicColor: Boolean = true,
    val remindersEnabled: Boolean = true,
    val autoSync: Boolean = true,
    val showWeekend: Boolean = true,
    val showCompletedToday: Boolean = false,
)

interface SettingsRepository {
    fun observePreferences(): Flow<UserPreferences>
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setAutoSync(enabled: Boolean)
    suspend fun setShowWeekend(enabled: Boolean)
    suspend fun setShowCompletedToday(enabled: Boolean)
}
