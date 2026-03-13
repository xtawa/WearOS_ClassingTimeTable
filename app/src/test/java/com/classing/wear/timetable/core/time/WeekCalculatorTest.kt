package com.classing.wear.timetable.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekCalculatorTest {

    @Test
    fun weekIndex_returns_expected_value() {
        val semesterStart = LocalDate.of(2026, 2, 23)

        assertEquals(1, WeekCalculator.weekIndex(semesterStart, LocalDate.of(2026, 2, 23)))
        assertEquals(1, WeekCalculator.weekIndex(semesterStart, LocalDate.of(2026, 3, 1)))
        assertEquals(2, WeekCalculator.weekIndex(semesterStart, LocalDate.of(2026, 3, 2)))
        assertEquals(4, WeekCalculator.weekIndex(semesterStart, LocalDate.of(2026, 3, 16)))
    }
}
