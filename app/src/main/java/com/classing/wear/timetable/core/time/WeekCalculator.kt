package com.classing.wear.timetable.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object WeekCalculator {
    fun weekIndex(semesterStartDate: LocalDate, targetDate: LocalDate): Int {
        val days = java.time.temporal.ChronoUnit.DAYS.between(semesterStartDate, targetDate)
        return (days / 7L + 1L).toInt().coerceAtLeast(1)
    }

    fun weekStart(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
