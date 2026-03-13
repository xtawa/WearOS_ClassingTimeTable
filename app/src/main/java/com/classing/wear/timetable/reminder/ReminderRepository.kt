package com.classing.wear.timetable.reminder

import com.classing.shared.model.ReminderInstance

interface ReminderRepository {
    suspend fun replaceAll(reminders: List<ReminderInstance>)
    suspend fun loadAll(): List<ReminderInstance>
    suspend fun clearAll()
}
