package com.classing.wear.timetable.reminder

import com.classing.shared.model.ReminderInstance

interface ReminderScheduler {
    fun schedule(reminders: List<ReminderInstance>)
    fun cancelAll()
}
