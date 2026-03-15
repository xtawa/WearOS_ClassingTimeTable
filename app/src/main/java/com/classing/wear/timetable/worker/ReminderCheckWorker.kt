package com.classing.wear.timetable.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.classing.wear.timetable.R
import com.classing.wear.timetable.sync.MobileSyncPrefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

class ReminderCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val payload = applicationContext.getSharedPreferences(MobileSyncPrefs.PREF_NAME, Context.MODE_PRIVATE)
            .getString(MobileSyncPrefs.KEY_LAST_PAYLOAD, "")
            .orEmpty()
        if (payload.isBlank()) return Result.success()

        val lessons = parseLessons(payload)
        if (lessons.isEmpty()) return Result.success()

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val notified = loadNotifiedSet(today).toMutableSet()
        val due = ReminderCheckLogic.dueLessons(lessons, now, notified)
        if (due.isEmpty()) return Result.success()

        if (!canNotify()) return Result.success()
        ensureChannel()

        due.forEach { lesson ->
            val key = ReminderCheckLogic.reminderKey(today, lesson)
            postNotification(lesson, key.hashCode())
            notified += key
        }
        saveNotifiedSet(today, notified)
        return Result.success()
    }

    private fun parseLessons(payload: String): List<SyncedLesson> {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return emptyList()
        val lessons = root.optJSONArray("lessons") ?: return emptyList()

        return buildList {
            for (i in 0 until lessons.length()) {
                val item = lessons.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank { "lesson-$i" }
                val title = item.optString("title").ifBlank { "Class" }
                val day = item.optInt("dayOfWeek", 1).coerceIn(1, 7)
                val start = parseTime(item.optString("startTime")) ?: continue

                add(
                    SyncedLesson(
                        id = id,
                        title = title,
                        dayOfWeek = day,
                        startTime = start,
                        location = item.optString("location").ifBlank { null },
                    ),
                )
            }
        }
    }

    private fun parseTime(raw: String): LocalTime? {
        val text = raw.trim()
        if (text.isBlank()) return null

        return runCatching { LocalTime.parse(text, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
            ?: runCatching { LocalTime.parse(text, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    private fun postNotification(lesson: SyncedLesson, notificationId: Int) {
        val startText = lesson.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        val content = listOfNotNull(lesson.title, startText, lesson.location?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .ifBlank { applicationContext.getString(R.string.reminder_notification_default_content) }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.reminder_notification_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    private fun loadNotifiedSet(date: LocalDate): Set<String> {
        val prefs = applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_NOTIFIED_DATE, null) != date.toString()) {
            return emptySet()
        }
        val raw = prefs.getString(KEY_NOTIFIED_KEYS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
        }.getOrDefault(emptySet())
    }

    private fun saveNotifiedSet(date: LocalDate, keys: Set<String>) {
        val arr = JSONArray()
        keys.forEach(arr::put)
        applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_NOTIFIED_DATE, date.toString())
            .putString(KEY_NOTIFIED_KEYS, arr.toString())
            .apply()
    }

    companion object {
        private const val CHANNEL_ID = "classing_reminder_channel"
        private const val PREF_NAME = "wear_reminder_check"
        private const val KEY_NOTIFIED_DATE = "notified_date"
        private const val KEY_NOTIFIED_KEYS = "notified_keys"
    }
}
