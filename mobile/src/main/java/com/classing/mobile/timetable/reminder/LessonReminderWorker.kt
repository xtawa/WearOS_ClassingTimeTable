package com.classing.mobile.timetable.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.classing.mobile.timetable.MainActivity
import com.classing.mobile.timetable.R
import com.classing.mobile.timetable.data.MobilePrefsStore
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray

class LessonReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = MobilePrefsStore.loadSettings(applicationContext)
        if (!settings.reminderEnabled) return Result.success()

        val lessons = MobilePrefsStore.loadLessons(applicationContext)
        if (lessons.isEmpty()) return Result.success()

        val now = LocalDateTime.now()
        val nowMinute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val todayKey = now.toLocalDate().toString()
        val alreadyNotified = loadNotifiedSet(applicationContext, todayKey).toMutableSet()

        var sent = 0
        lessons.filter { it.dayOfWeek == today }.forEach { lesson ->
            val triggerMinute = lesson.startMinute - settings.reminderMinutes
            if (triggerMinute < 0) return@forEach

            val inWindow = nowMinute in triggerMinute until (triggerMinute + CHECK_WINDOW_MINUTES)
            val uniqueKey = "$todayKey:${lesson.id}:${lesson.startMinute}"
            if (!inWindow || alreadyNotified.contains(uniqueKey)) return@forEach

            postNotification(
                context = applicationContext,
                title = lesson.title,
                location = lesson.location,
                startMinute = lesson.startMinute,
                leadMinutes = settings.reminderMinutes,
                notificationId = uniqueKey.hashCode(),
            )
            alreadyNotified.add(uniqueKey)
            sent += 1
        }

        saveNotifiedSet(applicationContext, todayKey, alreadyNotified)
        return if (sent >= 0) Result.success() else Result.retry()
    }

    private fun postNotification(
        context: Context,
        title: String,
        location: String?,
        startMinute: Int,
        leadMinutes: Int,
        notificationId: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)

        val startTime = LocalTime.of(startMinute / 60, startMinute % 60).format(clockFormatter)
        val body = if (location.isNullOrBlank()) {
            context.getString(R.string.reminder_notification_body_no_location, leadMinutes, startTime)
        } else {
            context.getString(R.string.reminder_notification_body_with_location, leadMinutes, startTime, location)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.reminder_notification_title, title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun loadNotifiedSet(context: Context, todayKey: String): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val date = prefs.getString(KEY_NOTIFIED_DATE, null)
        if (date != todayKey) return emptySet()

        val raw = prefs.getString(KEY_NOTIFIED_KEYS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.optString(i))
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun saveNotifiedSet(context: Context, todayKey: String, keys: Set<String>) {
        val arr = JSONArray()
        keys.forEach { arr.put(it) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTIFIED_DATE, todayKey)
            .putString(KEY_NOTIFIED_KEYS, arr.toString())
            .apply()
    }

    companion object {
        private const val CHANNEL_ID = "course_reminder_channel"
        private const val CHECK_WINDOW_MINUTES = 15
        private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private const val PREF_NAME = "mobile_timetable_prefs"
        private const val KEY_NOTIFIED_DATE = "reminder_notified_date"
        private const val KEY_NOTIFIED_KEYS = "reminder_notified_keys"
    }
}
