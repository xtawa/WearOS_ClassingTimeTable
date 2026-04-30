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
        val requestCodes = loadRequestCodes().toMutableSet()
        reminders.forEach { reminder ->
            val requestCode = reminder.id.hashCode()
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
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            requestCodes += requestCode.toString()
        }
        saveRequestCodes(requestCodes)
    }

    override fun cancelAll() {
        loadRequestCodes().forEach { rawCode ->
            val requestCode = rawCode.toIntOrNull() ?: return@forEach
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, ReminderAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        saveRequestCodes(emptySet())
    }

    private fun loadRequestCodes(): Set<String> {
        return prefs.getStringSet(KEY_REQUEST_CODES, emptySet()).orEmpty()
    }

    private fun saveRequestCodes(requestCodes: Set<String>) {
        prefs.edit().putStringSet(KEY_REQUEST_CODES, requestCodes).apply()
    }

    private val prefs
        get() = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "alarm_reminder_scheduler"
        private const val KEY_REQUEST_CODES = "request_codes"
    }
}
