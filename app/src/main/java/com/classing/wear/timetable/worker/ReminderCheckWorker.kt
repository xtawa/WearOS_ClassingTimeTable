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
import com.classing.wear.timetable.sync.WearSyncModeStore
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

class ReminderCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Paired phones bridge their Classing notifications through Wear OS. Keep the watch's
        // local reminder as the offline/independent fallback to avoid two cards for one class.
        if (!WearSyncModeStore.isIndependentModeEnabled(applicationContext)) {
            val phoneConnected = runCatching {
                Wearable.getNodeClient(applicationContext).connectedNodes.await().isNotEmpty()
            }.getOrDefault(false)
            if (phoneConnected) return Result.success()
        }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        // Reminder candidates come from the projected Wear database (week rules and schedule
        // exceptions applied), never from the raw phone payload which may be partial or absent.
        // A transient read failure is not an empty timetable: retry later and leave the alarm alone.
        val occurrences = WearReminderSource.loadOrNull(applicationContext, now) ?: return Result.retry()
        val notified = loadNotifiedSet(today).toMutableSet()

        // An exact alarm carries the key it was armed for. The lesson may have been cancelled or
        // rescheduled between arming and firing, so the key is validated against the current
        // projection and the notification uses the database copy, not the alarm payload.
        val directReminderKey = inputData.getString(KEY_DIRECT_REMINDER_KEY).orEmpty()
        val direct = ReminderCheckLogic.resolveDirectReminder(occurrences, directReminderKey, now)
        if (direct != null && directReminderKey !in notified) {
            if (!canNotify()) return Result.success()
            ensureChannel()
            postNotification(direct.asSyncedLesson(), directReminderKey.hashCode())
            notified += directReminderKey
            saveNotifiedSet(today, notified)
        }

        val due = ReminderCheckLogic.dueOccurrences(occurrences, now, notified)
        if (due.isEmpty()) {
            refreshAlarm()
            return Result.success()
        }

        if (!canNotify()) return Result.success()
        ensureChannel()

        due.forEach { occurrence ->
            val key = ReminderCheckLogic.reminderKey(occurrence)
            postNotification(occurrence.asSyncedLesson(), key.hashCode())
            notified += key
        }
        saveNotifiedSet(today, notified)
        refreshAlarm()
        return Result.success()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked between the check and notify call.
        }
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

        const val KEY_DIRECT_REMINDER_KEY = "direct_reminder_key"
        const val KEY_DIRECT_LESSON_ID = "direct_lesson_id"
        const val KEY_DIRECT_LESSON_TITLE = "direct_lesson_title"
        const val KEY_DIRECT_LESSON_LOCATION = "direct_lesson_location"
        const val KEY_DIRECT_START_MINUTE = "direct_start_minute"
    }

    private suspend fun refreshAlarm() {
        val app = applicationContext as? com.classing.wear.timetable.ClassingTimetableApplication ?: return
        val pref = runCatching { app.appContainer.settingsRepository.observePreferences().first() }
            .getOrNull() ?: return
        WearReminderAlarmScheduler.refresh(
            context = applicationContext,
            enabled = pref.remindersEnabled,
            level = pref.keepAliveLevel,
        )
    }
}
