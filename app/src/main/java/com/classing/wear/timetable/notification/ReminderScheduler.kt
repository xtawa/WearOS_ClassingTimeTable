package com.classing.wear.timetable.notification

import java.time.LocalDateTime

interface ReminderScheduler {
    suspend fun scheduleCourseReminder(courseId: Long, triggerAt: LocalDateTime, content: String)
    suspend fun cancelCourseReminder(courseId: Long)
}
