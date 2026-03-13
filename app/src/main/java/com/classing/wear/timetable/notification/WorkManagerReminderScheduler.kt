package com.classing.wear.timetable.notification

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime

class WorkManagerReminderScheduler(
    private val context: Context,
) : ReminderScheduler {

    override suspend fun scheduleCourseReminder(courseId: Long, triggerAt: LocalDateTime, content: String) {
        val delay = Duration.between(LocalDateTime.now(), triggerAt).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_COURSE_ID to courseId,
                    ReminderWorker.KEY_CONTENT to content,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    override suspend fun cancelCourseReminder(courseId: Long) {
        // 后续可以把 reminder requestId 与 courseId 绑定到表中再做精准取消。
    }
}
