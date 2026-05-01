package com.classing.wear.timetable.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

object WeekCalculator {
    fun weekIndex(semesterStartDate: LocalDate, targetDate: LocalDate): Int {
        val days = java.time.temporal.ChronoUnit.DAYS.between(semesterStartDate, targetDate)
        return (days / 7L + 1L).toInt().coerceAtLeast(0) // 返回 0 表示学期开始前
    }

    fun weekStart(date: LocalDate, locale: Locale = Locale.getDefault()): LocalDate {
        val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
        return date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }
}
