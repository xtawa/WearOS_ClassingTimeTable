package com.classing.wear.timetable.core.time

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatters {
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    fun formatDateTime(dateTime: LocalDateTime): String = dateTime.format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.CHINA))

    fun formatTimeRange(start: LocalDateTime, end: LocalDateTime): String {
        return "${start.format(timeFormatter)}-${end.format(timeFormatter)}"
    }

    fun formatCountdown(duration: Duration?): String {
        if (duration == null) return ""
        val minutes = duration.toMinutes()
        if (minutes <= 0) return "即将开始"
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}小时${m}分后" else "${m}分钟后"
    }
}
