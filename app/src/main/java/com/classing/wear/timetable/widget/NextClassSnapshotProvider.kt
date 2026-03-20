package com.classing.wear.timetable.widget

import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.core.time.TimeFormatters
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NextClassSnapshot(
    val hasLesson: Boolean,
    val courseTitle: String,
    val weekText: String,
    val dateText: String,
    val timeText: String,
    val teacherText: String,
    val locationText: String,
    val countdownText: String,
    val shortComplicationText: String,
    val longComplicationText: String,
    val contentDescription: String,
)

class NextClassSnapshotProvider(
    private val appContainer: AppContainer,
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd EEE", Locale.getDefault())

    suspend fun loadSnapshot(): NextClassSnapshot {
        val today = appContainer.timeProvider.today()
        val preferences = appContainer.settingsRepository.observePreferences().first()
        val next = appContainer.scheduleRepository.observeNextLesson(today).first()
        val lesson = next.lesson

        if (lesson == null) {
            return NextClassSnapshot(
                hasLesson = false,
                courseTitle = WearI18n.tileNoClassTitle(),
                weekText = "",
                dateText = today.format(dateFormatter),
                timeText = WearI18n.tileNoClassSubtitle(),
                teacherText = "",
                locationText = "",
                countdownText = "",
                shortComplicationText = WearI18n.complicationNoClassShortText(),
                longComplicationText = WearI18n.complicationNoClassLongText(),
                contentDescription = WearI18n.complicationNoClassLongText(),
            )
        }

        val courseTitle = if (preferences.tileShowCourseName) lesson.course.name else ""
        val weekText = if (preferences.tileShowCurrentWeek) WearI18n.weekLabel(lesson.weekIndex) else ""
        val timeRange = if (preferences.tileShowTimeRange) {
            TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt)
        } else {
            ""
        }
        val teacher = if (preferences.tileShowTeacher) lesson.course.teacher.trim() else ""
        val location = if (preferences.tileShowLocation) {
            lesson.course.classroom.ifBlank { WearI18n.locationUnknown() }
        } else {
            ""
        }
        val countdown = if (preferences.tileShowCountdown) {
            formatCountdown(next.countdown)
        } else {
            ""
        }
        val short = buildShortComplicationText(
            startTime = lesson.startAt.toLocalTime(),
            title = lesson.course.name,
            timeRange = timeRange,
            countdown = next.countdown,
            preferCountdown = preferences.tileShowCountdown,
        )
        val detailParts = buildList {
            if (weekText.isNotBlank()) add(weekText)
            if (timeRange.isNotBlank()) add(timeRange)
            if (teacher.isNotBlank()) add(teacher)
            if (location.isNotBlank()) add(location)
            if (countdown.isNotBlank()) add(countdown)
        }
        val longTitle = if (courseTitle.isNotBlank()) courseTitle else lesson.course.name
        val long = "$longTitle ${detailParts.joinToString(" · ")}".trim().take(120)
        val contentDescription = buildList {
            add(longTitle)
            if (weekText.isNotBlank()) add(weekText)
            if (timeRange.isNotBlank()) add(timeRange)
            if (teacher.isNotBlank()) add(teacher)
            if (location.isNotBlank()) add(location)
            if (countdown.isNotBlank()) add(countdown)
        }.joinToString(", ")

        return NextClassSnapshot(
            hasLesson = true,
            courseTitle = courseTitle,
            weekText = weekText,
            dateText = lesson.date.format(dateFormatter),
            timeText = timeRange,
            teacherText = teacher,
            locationText = location,
            countdownText = countdown,
            shortComplicationText = short,
            longComplicationText = long,
            contentDescription = contentDescription,
        )
    }

    private fun buildShortComplicationText(
        startTime: LocalTime,
        title: String,
        timeRange: String,
        countdown: Duration?,
        preferCountdown: Boolean,
    ): String {
        if (preferCountdown) {
            val shortCountdown = formatShortCountdown(countdown)
            if (shortCountdown.isNotBlank()) return "$shortCountdown ${title.take(6)}"
        }
        if (timeRange.isNotBlank()) {
            return timeRange.take(16)
        }
        val timeText = startTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        return "$timeText ${title.take(8)}"
    }

    private fun formatCountdown(countdown: Duration?): String {
        val minutes = (countdown?.toMinutes() ?: 0L).coerceAtLeast(0L)
        if (minutes <= 0L) return WearI18n.countdownSoon()
        if (minutes >= 60L) {
            val hours = minutes / 60L
            val remainMinutes = minutes % 60L
            return WearI18n.countdownInHoursAndMinutes(hours, remainMinutes)
        }
        return WearI18n.countdownInMinutes(minutes)
    }

    private fun formatShortCountdown(countdown: Duration?): String {
        val minutes = (countdown?.toMinutes() ?: 0L).coerceAtLeast(0L)
        if (minutes <= 0L) return "0m"
        if (minutes >= 60L) {
            val hours = minutes / 60L
            val remainMinutes = minutes % 60L
            return if (remainMinutes == 0L) "${hours}h" else "${hours}h${remainMinutes}m"
        }
        return "${minutes}m"
    }
}
