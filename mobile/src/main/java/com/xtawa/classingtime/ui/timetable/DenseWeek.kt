package com.xtawa.classingtime.ui.timetable

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import com.xtawa.classingtime.screen.LessonUi

internal fun denseWeekDates(date: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
    val anchor = date.with(TemporalAdjusters.previousOrSame(firstDay))
    return (0L..6L).map(anchor::plusDays)
}

/** Stable order retains every overlapping occurrence; never deduplicate by title. */
internal fun denseDayLessons(lessons: List<LessonUi>): List<LessonUi> =
    lessons.sortedWith(compareBy<LessonUi> { it.startTime }.thenBy { it.endTime }.thenBy { it.id })
