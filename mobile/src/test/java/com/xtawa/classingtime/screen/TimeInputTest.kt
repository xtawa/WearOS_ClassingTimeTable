package com.xtawa.classingtime.screen

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeInputTest {
    @Test fun chineseKeyboardColonCanBeUsedToEditLessonTime() {
        assertEquals(LocalTime.of(9, 30), parseManualTime(normalizeTimeInput("09：30")))
    }

    @Test fun normalizationKeepsAsciiTimesAndRejectsInvalidMinutes() {
        assertEquals("9:30", normalizeTimeInput("9:30"))
        assertNull(parseManualTime(normalizeTimeInput("09：99")))
        assertNull(parseManualTime(normalizeTimeInput("hello")))
    }
}
