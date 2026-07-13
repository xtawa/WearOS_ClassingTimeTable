package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class MobileNextLessonTest {
    @Test
    fun resolveNextLessonForBoard_returnsCurrentThenFutureLesson() {
        val now = LocalDateTime.of(2026, 7, 13, 9, 30)
        val current = lesson("current", now.dayOfWeek, 9, 0, 10, 0)
        val future = lesson("future", now.plusDays(1).dayOfWeek, 8, 0, 9, 0)
        val byDay = listOf(current, future).groupBy { it.dayOfWeek }

        assertEquals("current", resolveNextLessonForBoard({ byDay[it.dayOfWeek].orEmpty() }, now)?.lesson?.id)
        assertEquals("future", resolveNextLessonForBoard({ byDay[it.dayOfWeek].orEmpty() }, now.withHour(10))?.lesson?.id)
    }

    private fun lesson(id: String, day: DayOfWeek, sh: Int, sm: Int, eh: Int, em: Int) = LessonUi(
        id, id, null, null, null, day, LocalTime.of(sh, sm), LocalTime.of(eh, em), 1, 20, LessonWeekParity.ALL,
    )
}
