package com.xtawa.classingtime.ui.course

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.classing.shared.time.nextMinuteDelay
import com.xtawa.classingtime.R
import com.xtawa.classingtime.screen.LessonUi
import com.xtawa.classingtime.ui.theme.classingCourseAccent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun CourseDetailScreen(
    lesson: LessonUi,
    date: LocalDate,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val current = LocalDateTime.now()
            now = current
            delay(nextMinuteDelay(current).toMillis())
        }
    }

    val state = remember(lesson, date, now, context) {
        val startAt = LocalDateTime.of(date, lesson.startTime)
        val endAt = LocalDateTime.of(date, lesson.endTime)
        val status = when {
            now.isBefore(startAt) -> CourseDetailStatus.Upcoming
            now.isBefore(endAt) -> CourseDetailStatus.InClass
            else -> CourseDetailStatus.Finished
        }
        val temporalLabel = when (status) {
            CourseDetailStatus.Upcoming -> {
                val minutes = Duration.between(now, startAt).toMinutes().coerceAtLeast(0L)
                if (minutes >= 60L) {
                    context.getString(
                        R.string.course_detail_starts_in_hours_minutes,
                        minutes / 60,
                        minutes % 60,
                    )
                } else {
                    context.getString(R.string.course_detail_starts_in_minutes, minutes)
                }
            }

            CourseDetailStatus.InClass -> {
                val minutes = Duration.between(now, endAt).toMinutes().coerceAtLeast(0L)
                context.getString(R.string.course_detail_minutes_remaining, minutes)
            }

            CourseDetailStatus.Finished -> context.getString(R.string.course_detail_class_finished)
        }
        val progress = if (status == CourseDetailStatus.InClass) {
            val duration = Duration.between(startAt, endAt).seconds.coerceAtLeast(1L)
            (Duration.between(startAt, now).seconds.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            null
        }
        CourseDetailUiState(
            id = "${lesson.id}@$date",
            title = lesson.title,
            date = date,
            dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
            startTime = lesson.startTime,
            endTime = lesson.endTime,
            location = lesson.location,
            teacher = lesson.teacher,
            note = lesson.note,
            recurrenceLabel = context.getString(
                R.string.course_detail_recurrence,
                lesson.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
                lesson.startWeek,
                lesson.endWeek,
                if (lesson.weekParity.name == "ALL") {
                    ""
                } else {
                    context.getString(
                        R.string.course_detail_recurrence_suffix,
                        context.getString(
                            when (lesson.weekParity.name) {
                                "ODD" -> R.string.week_parity_odd
                                "EVEN" -> R.string.week_parity_even
                                else -> R.string.week_parity_all
                            },
                        ),
                    )
                },
            ),
            accent = classingCourseAccent(lesson.title),
            status = status,
            temporalLabel = temporalLabel,
            progress = progress,
        )
    }

    CourseDetailContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onEdit = onEdit,
    )
}
