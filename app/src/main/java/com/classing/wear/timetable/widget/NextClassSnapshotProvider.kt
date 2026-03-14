package com.classing.wear.timetable.widget

import com.classing.wear.timetable.core.AppContainer
import com.classing.wear.timetable.core.i18n.WearI18n
import com.classing.wear.timetable.core.time.TimeFormatters
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NextClassSnapshot(
    val hasLesson: Boolean,
    val courseTitle: String,
    val dateText: String,
    val timeText: String,
    val locationText: String,
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
        val next = appContainer.scheduleRepository.observeNextLesson(today).first()
        val lesson = next.lesson

        if (lesson == null) {
            return NextClassSnapshot(
                hasLesson = false,
                courseTitle = WearI18n.tileNoClassTitle(),
                dateText = today.format(dateFormatter),
                timeText = WearI18n.tileNoClassSubtitle(),
                locationText = "",
                shortComplicationText = WearI18n.complicationNoClassShortText(),
                longComplicationText = WearI18n.complicationNoClassLongText(),
                contentDescription = WearI18n.complicationNoClassLongText(),
            )
        }

        val timeRange = TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt)
        val location = lesson.course.classroom.ifBlank { WearI18n.locationUnknown() }
        val short = buildShortComplicationText(lesson.startAt.toLocalTime(), lesson.course.name)
        val long = "${lesson.course.name} $timeRange @ $location".take(120)

        return NextClassSnapshot(
            hasLesson = true,
            courseTitle = lesson.course.name,
            dateText = lesson.date.format(dateFormatter),
            timeText = timeRange,
            locationText = location,
            shortComplicationText = short,
            longComplicationText = long,
            contentDescription = "${lesson.course.name}, $timeRange, $location",
        )
    }

    private fun buildShortComplicationText(startTime: LocalTime, title: String): String {
        val timeText = startTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        return "$timeText ${title.take(8)}"
    }
}
