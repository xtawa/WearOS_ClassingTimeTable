package com.classing.wear.timetable.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.classing.shared.model.ReminderInstance

class AlarmReminderScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
) : ReminderScheduler {

    override fun schedule(reminders: List<ReminderInstance>) {
        reminders.forEach { reminder ->
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra("courseId", reminder.courseId)
                putExtra("title", reminder.title)
                putExtra("body", reminder.body)
            }
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAt.toEpochMilli(),
                PendingIntent.getBroadcast(
                    context,
                    reminder.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    override fun cancelAll() {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ReminderAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }
}
