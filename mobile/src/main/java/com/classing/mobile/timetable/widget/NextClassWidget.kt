package com.xtawa.classingtime.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.work.*
import com.xtawa.classingtime.MainActivity
import com.xtawa.classingtime.R
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.screen.*
import com.xtawa.classingtime.ui.home.resolveHomeUiState
import com.xtawa.classingtime.ui.home.HomePhase
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class NextClassWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
    override fun onEnabled(context: Context) = refresh(context)
    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_UPDATE)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_BOUNDARY)
    }
    companion object {
        private const val WORK_PERIODIC = "next_class_widget_periodic"
        private const val WORK_UPDATE = "next_class_widget_update"
        private const val WORK_BOUNDARY = "next_class_widget_boundary"
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.getAppWidgetIds(ComponentName(context, NextClassWidget::class.java)).isEmpty()) return
            val worker = WorkManager.getInstance(context)
            worker.enqueueUniqueWork(WORK_UPDATE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<NextClassWidgetWorker>().build())
            worker.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NextClassWidgetWorker>(15, TimeUnit.MINUTES).build())
        }
        internal fun updateAll(context: Context, now: LocalDateTime = LocalDateTime.now()) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextClassWidget::class.java))
            if (ids.isEmpty()) return
            val settings = MobilePrefsStore.loadSettings(context)
            val timetable = MobilePrefsStore.loadTimetableState(context)
            val base = timetable.baseLessons.map { it.toLessonUi() }
            val exceptions = timetable.exceptions.map { it.toUi() }
            val mode = WeekNumberMode.entries.firstOrNull { it.name == settings.weekNumberMode } ?: WeekNumberMode.NATURAL
            val semester = runCatching { java.time.LocalDate.parse(settings.semesterWeekStartDate) }.getOrDefault(now.toLocalDate())
            val weekStart = runCatching { java.time.DayOfWeek.valueOf(settings.weekStartDay) }.getOrDefault(java.time.DayOfWeek.MONDAY)
            val state = resolveHomeUiState(now, { date ->
                buildEffectiveOccurrencesForDateRange(base, exceptions, date, date, mode, semester, weekStart).map { it.lesson }
            }, MobilePrefsStore.hasTimetableState(context))
            val showExam = com.classing.client.exam.ExamStore.mode(context) == com.classing.shared.exam.AcademicMode.EXAM
            val exam = if (showExam) com.classing.shared.exam.nextExam(com.classing.client.exam.ExamStore.load(context), now.atZone(ZoneId.systemDefault()).toInstant()) else null
            val course = if (showExam) null else state.primaryCourse ?: state.nextCourse
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.next_class_widget)
                views.setTextViewText(R.id.widget_title, exam?.title ?: if (showExam) (if (java.util.Locale.getDefault().language == "zh") "暂无考试" else "No exams") else course?.title ?: context.getString(
                    if (state.hasImportedSchedule) R.string.widget_no_class else R.string.widget_no_schedule))
                val detail = if (showExam) exam?.let { com.classing.client.exam.examSummary(it, now.atZone(ZoneId.systemDefault()).toInstant()) }.orEmpty() else course?.let {
                    if (state.phase == HomePhase.InClass) context.getString(R.string.widget_in_class, it.endTime.format(formatter))
                    else context.getString(R.string.widget_upcoming, it.date.toString(), it.startTime.format(formatter))
                }.orEmpty()
                views.setTextViewText(R.id.widget_time, detail)
                val target = exam?.let { java.time.Instant.ofEpochMilli(if (it.startAt <= now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) it.endAt else it.startAt) } ?: course?.let { LocalDateTime.of(it.date, if (state.phase == HomePhase.InClass) it.endTime else it.startTime).atZone(ZoneId.systemDefault()).toInstant() }
                views.setViewVisibility(R.id.widget_countdown, if (target != null) View.VISIBLE else View.GONE)
                if (target != null) {
                    views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + java.time.Duration.between(now.atZone(ZoneId.systemDefault()).toInstant(), target).toMillis().coerceAtLeast(0), null, true)
                    views.setBoolean(R.id.widget_countdown, "setCountDown", true)
                }
                views.setTextViewText(R.id.widget_room, exam?.location ?: course?.location.orEmpty())
                views.setViewVisibility(R.id.widget_room, if (manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= 180) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_freshness, scheduleFreshnessText(context, settings.cloudSyncEnabled, settings.cloudLastSyncedAt))
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id,
                    Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(id, views)
            }
            val boundary = exam?.let { java.time.Instant.ofEpochMilli(if (it.startAt <= now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) it.endAt else it.startAt).atZone(ZoneId.systemDefault()).toLocalDateTime() } ?: course?.let { LocalDateTime.of(it.date, if (state.phase == HomePhase.InClass) it.endTime else it.startTime) }
                ?: now.toLocalDate().plusDays(1).atStartOfDay()
            val delay = java.time.Duration.between(now.atZone(ZoneId.systemDefault()).toInstant(), boundary.atZone(ZoneId.systemDefault()).toInstant()).toMillis().coerceAtLeast(1000)
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_BOUNDARY, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<NextClassWidgetWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        }
    }
}

class NextClassWidgetWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        NextClassWidget.updateAll(applicationContext)
        return Result.success()
    }
}

internal fun scheduleFreshnessText(context: Context, enabled: Boolean, lastSync: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (!enabled) return context.getString(R.string.schedule_local)
    if (lastSync <= 0) return context.getString(R.string.schedule_never_synced)
    val timestamp = java.time.Instant.ofEpochMilli(lastSync).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    val text = context.getString(R.string.schedule_last_sync, timestamp)
    return if (nowMillis - lastSync >= TimeUnit.DAYS.toMillis(1)) "$text · ${context.getString(R.string.schedule_stale)}" else text
}
