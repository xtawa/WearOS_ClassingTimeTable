package com.xtawa.classingtime.screen

import androidx.test.core.app.ApplicationProvider
import com.classing.shared.importer.IcsImportParser
import com.classing.shared.importer.ScheduleImportAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MobileIcsImportMappingTest {
    @Test
    fun parseToLessons_expandsByDayAndMapsExdateAndRecurrenceId() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:course-1
            SUMMARY:Math
            DTSTART:20260302T080000Z
            DTEND:20260302T090000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=8
            EXDATE:20260309T080000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:course-1
            RECURRENCE-ID:20260311T080000Z
            SUMMARY:Math moved
            DTSTART:20260311T100000Z
            DTEND:20260311T110000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parseToLessons(
            raw, IcsImportParser(), ScheduleImportAdapter(), ZoneId.of("UTC"),
            WeekNumberMode.SEMESTER, LocalDate.of(2026, 3, 2), ApplicationProvider.getApplicationContext(),
        )

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), result.lessons.map { it.dayOfWeek }.toSet())
        assertEquals(2, result.exceptions.size)
        assertTrue(result.exceptions.any { it.type == ScheduleExceptionKind.CANCEL && it.date == LocalDate.of(2026, 3, 9) })
        val moved = result.exceptions.single { it.type == ScheduleExceptionKind.RESCHEDULE }
        assertEquals(LocalTime.of(10, 0), moved.startTime)
        assertEquals("Math moved", moved.title)
    }
}
