package com.xtawa.classingtime.ui.changes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.xtawa.classingtime.screen.LessonUi
import com.xtawa.classingtime.screen.ScheduleExceptionKind
import com.xtawa.classingtime.screen.ScheduleExceptionUi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val changeClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun ScheduleChangesScreen(
    exceptions: List<ScheduleExceptionUi>,
    baseLessons: List<LessonUi>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenLesson: (LessonUi, LocalDate) -> Unit,
) {
    val state = remember(exceptions, baseLessons) {
        val locale = Locale.getDefault()
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        ScheduleChangesUiState(
            changes = exceptions
                .sortedWith(compareBy<ScheduleExceptionUi> { it.date }.thenBy { it.startTime })
                .map { exception ->
                    val base = baseLessons.firstOrNull { it.id == exception.lessonId }
                    val type = when (exception.type) {
                        ScheduleExceptionKind.CANCEL -> ScheduleChangeType.Cancelled
                        ScheduleExceptionKind.RESCHEDULE -> ScheduleChangeType.Moved
                        ScheduleExceptionKind.MAKE_UP -> ScheduleChangeType.Added
                    }
                    ScheduleChangeUiModel(
                        id = exception.id,
                        lessonId = exception.lessonId,
                        title = exception.title?.takeIf(String::isNotBlank) ?: base?.title ?: "Untitled course",
                        date = exception.date,
                        dateLabel = exception.date.format(dateFormatter),
                        type = type,
                        beforeLabel = when (type) {
                            ScheduleChangeType.Cancelled,
                            ScheduleChangeType.Moved,
                            -> base?.let { "${it.startTime.format(changeClockFormatter)}–${it.endTime.format(changeClockFormatter)} · ${it.location ?: "Room not provided"}" }

                            ScheduleChangeType.Added -> null
                        },
                        nowLabel = when (type) {
                            ScheduleChangeType.Cancelled -> "Cancelled"
                            ScheduleChangeType.Moved,
                            ScheduleChangeType.Added,
                            -> exception.startTime?.let { start ->
                                val end = exception.endTime?.format(changeClockFormatter) ?: "?"
                                "${start.format(changeClockFormatter)}–$end · ${exception.location ?: base?.location ?: "Room not provided"}"
                            }
                        },
                        contextLabel = when (type) {
                            ScheduleChangeType.Added -> "One-off make-up class"
                            ScheduleChangeType.Cancelled -> "The original occurrence remains in change history."
                            ScheduleChangeType.Moved -> "Effective for this occurrence"
                        },
                    )
                },
        )
    }
    ScheduleChangesContent(
        state = state,
        contentPadding = contentPadding,
        onBack = onBack,
        onOpenChange = { change ->
            if (change.type != ScheduleChangeType.Cancelled) {
                val exception = exceptions.firstOrNull { it.id == change.id }
                val effective = exception?.let { source ->
                    val base = baseLessons.firstOrNull { it.id == source.lessonId }
                    LessonUi(
                        id = source.lessonId ?: source.id,
                        title = source.title ?: base?.title ?: change.title,
                        teacher = source.teacher ?: base?.teacher,
                        location = source.location ?: base?.location,
                        note = source.note ?: base?.note,
                        dayOfWeek = source.dayOfWeek ?: source.date.dayOfWeek,
                        startTime = source.startTime ?: base?.startTime ?: return@let null,
                        endTime = source.endTime ?: base?.endTime ?: return@let null,
                        startWeek = base?.startWeek ?: 1,
                        endWeek = base?.endWeek ?: 1,
                        weekParity = base?.weekParity ?: com.xtawa.classingtime.screen.LessonWeekParity.ALL,
                    )
                }
                effective?.let { onOpenLesson(it, change.date) }
            }
        },
    )
}
