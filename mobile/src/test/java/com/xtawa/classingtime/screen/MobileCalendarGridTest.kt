package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MobileCalendarGridTest {
    @Test fun gridAlignsMonthToConfiguredFirstDay() {
        val cells = buildMonthGridDates(YearMonth.of(2026, 7), DayOfWeek.MONDAY)
        assertEquals(35, cells.size)
        assertNull(cells[0])
        assertNull(cells[1])
        assertEquals(LocalDate.of(2026, 7, 1), cells[2])
        assertEquals(LocalDate.of(2026, 7, 31), cells[32])
    }
}
