package com.classing.wear.timetable.core.time

import com.classing.wear.timetable.domain.model.LessonStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class LessonStatusResolverTest {

    @Test
    fun resolve_returns_not_started_before_start() {
        val start = LocalDateTime.of(2026, 3, 13, 10, 0)
        val end = LocalDateTime.of(2026, 3, 13, 11, 35)
        val now = LocalDateTime.of(2026, 3, 13, 9, 59)

        assertEquals(LessonStatus.NOT_STARTED, LessonStatusResolver.resolve(now, start, end))
    }

    @Test
    fun resolve_returns_in_progress_when_between() {
        val start = LocalDateTime.of(2026, 3, 13, 10, 0)
        val end = LocalDateTime.of(2026, 3, 13, 11, 35)
        val now = LocalDateTime.of(2026, 3, 13, 10, 30)

        assertEquals(LessonStatus.IN_PROGRESS, LessonStatusResolver.resolve(now, start, end))
    }

    @Test
    fun resolve_returns_finished_after_end() {
        val start = LocalDateTime.of(2026, 3, 13, 10, 0)
        val end = LocalDateTime.of(2026, 3, 13, 11, 35)
        val now = LocalDateTime.of(2026, 3, 13, 11, 36)

        assertEquals(LessonStatus.FINISHED, LessonStatusResolver.resolve(now, start, end))
    }
}
