package com.xtawa.classingtime.screen

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AskAiTimetableSnapshotTest {
    @Test
    fun snapshotIncludesCurrentDateAndConfiguredWeek() {
        val context = buildAskAiScheduleContext(
            currentDate = LocalDate.of(2026, 7, 14),
            currentWeek = 7,
            timezone = "Asia/Shanghai",
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = LocalDate.of(2026, 6, 1),
            weekStartDay = DayOfWeek.MONDAY,
        )

        assertEquals("2026-07-14", context.currentDate)
        assertEquals("TUESDAY", context.currentDayOfWeek)
        assertEquals(7, context.currentWeek)
        assertEquals("Asia/Shanghai", context.timezone)
        assertEquals("SEMESTER", context.weekNumberMode)
        assertEquals("2026-06-01", context.semesterWeekStartDate)
        assertEquals("MONDAY", context.weekStartDay)
    }
}
