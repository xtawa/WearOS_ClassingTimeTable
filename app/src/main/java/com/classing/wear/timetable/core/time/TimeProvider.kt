package com.classing.wear.timetable.core.time

import java.time.LocalDate
import java.time.LocalDateTime

interface TimeProvider {
    fun nowDateTime(): LocalDateTime
    fun today(): LocalDate = nowDateTime().toLocalDate()
}

class SystemTimeProvider : TimeProvider {
    override fun nowDateTime(): LocalDateTime = LocalDateTime.now()
}
