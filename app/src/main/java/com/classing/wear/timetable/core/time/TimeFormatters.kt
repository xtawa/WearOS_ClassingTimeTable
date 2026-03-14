package com.classing.wear.timetable.core.time

import com.classing.wear.timetable.core.i18n.WearI18n
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatters {
    private fun dateFormatter(): DateTimeFormatter {
        val locale = Locale.getDefault()
        return if (locale.language == "zh") {
            DateTimeFormatter.ofPattern("M月d日 EEE", locale)
        } else {
            DateTimeFormatter.ofPattern("MMM d, EEE", locale)
        }
    }

    private fun dateTimeFormatter(): DateTimeFormatter {
        val locale = Locale.getDefault()
        return if (locale.language == "zh") {
            DateTimeFormatter.ofPattern("M/d HH:mm", locale)
        } else {
            DateTimeFormatter.ofPattern("MMM d HH:mm", locale)
        }
    }

    private fun timeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun formatDate(date: LocalDate): String = date.format(dateFormatter())

    fun formatDateTime(dateTime: LocalDateTime): String = dateTime.format(dateTimeFormatter())

    fun formatTimeRange(start: LocalDateTime, end: LocalDateTime): String {
        return "${start.format(timeFormatter())}-${end.format(timeFormatter())}"
    }

    fun formatCountdown(duration: Duration?): String {
        if (duration == null) return ""
        val minutes = duration.toMinutes()
        if (minutes <= 0) return WearI18n.countdownSoon()
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) WearI18n.countdownInHoursAndMinutes(h, m) else WearI18n.countdownInMinutes(m)
    }
}
