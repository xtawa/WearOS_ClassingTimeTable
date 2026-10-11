package com.classing.client.exam

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.classing.shared.exam.*
import kotlinx.coroutines.*

object ExamReminderScheduler {
    private fun prefs(context: Context) = context.getSharedPreferences("exam_reminders", Context.MODE_PRIVATE)
    private fun alarmIntent(context: Context, receiver: Class<out BroadcastReceiver>, key: String? = null) =
        Intent(context, receiver).setData(Uri.parse("classing://exam-reminder/next")).putExtra("key", key)
    @Synchronized
    fun sync(context: Context, exams: List<Exam>, enabled: Boolean, receiver: Class<out BroadcastReceiver>) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val prefs = prefs(context)
        val previous = PendingIntent.getBroadcast(context, 49031, alarmIntent(context, receiver),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        previous?.let { alarm.cancel(it); it.cancel() }
        prefs.edit().putBoolean("enabled", enabled).apply()
        if (!enabled || (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val fired = prefs.getStringSet("fired", emptySet()).orEmpty()
        val next = nextExamReminder(exams, System.currentTimeMillis(), fired) ?: return
        val pending = PendingIntent.getBroadcast(context, 49031, alarmIntent(context, receiver, next.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
            try { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.fireAt, pending) }
            catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.fireAt, pending) }
        } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.fireAt, pending)
    }

    @Synchronized
    internal fun post(context: Context, exams: List<Exam>, key: String) {
        val prefs = prefs(context)
        if (!prefs.getBoolean("enabled", false)) return
        val exam = exams.firstOrNull { canDeliverExamReminder(it, key, System.currentTimeMillis()) } ?: return
        val fired = prefs.getStringSet("fired", emptySet()).orEmpty()
        if (key in fired) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("exams", examText("考试提醒", "Exam reminders"), NotificationManager.IMPORTANCE_HIGH))
        val builder = NotificationCompat.Builder(context, "exams").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(exam.title).setContentText(examSummary(exam) + " · " + exam.location)
            .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            builder.setContentIntent(PendingIntent.getActivity(context, 49032, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        // Persist before notification, so a duplicate receiver delivery cannot post twice.
        val liveKeys = exams.map(::examReminderKey).toSet()
        if (!prefs.edit().putStringSet("fired", (fired + key).intersect(liveKeys)).commit()) return
        manager.notify("exam:$key", 49033, builder.build())
    }
}

abstract class ExamReminderReceiver : BroadcastReceiver() {
    abstract suspend fun exams(context: Context): List<Exam>
    abstract suspend fun enabled(context: Context): Boolean
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val current = exams(context)
                val active = enabled(context)
                if (active) intent.getStringExtra("key")?.let { ExamReminderScheduler.post(context, current, it) }
                ExamReminderScheduler.sync(context, current, active, this@ExamReminderReceiver.javaClass)
            } finally { pending.finish() }
        }
    }
}
