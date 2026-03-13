package com.classing.wear.timetable.core.time

import com.classing.wear.timetable.domain.model.LessonStatus
import java.time.LocalDateTime

object LessonStatusResolver {
    fun resolve(now: LocalDateTime, start: LocalDateTime, end: LocalDateTime): LessonStatus {
        return when {
            now.isBefore(start) -> LessonStatus.NOT_STARTED
            now.isAfter(end) -> LessonStatus.FINISHED
            else -> LessonStatus.IN_PROGRESS
        }
    }
}
