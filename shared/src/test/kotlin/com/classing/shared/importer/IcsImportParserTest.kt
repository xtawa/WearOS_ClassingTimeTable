package com.classing.shared.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant

class IcsImportParserTest {
    private val parser = IcsImportParser()

    @Test
    fun parse_ics_success() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:线性代数
            DTSTART:20260315T080000Z
            DTEND:20260315T094000Z
            LOCATION:A101
            DESCRIPTION:随堂测验
            RRULE:FREQ=WEEKLY;BYDAY=MO
            EXDATE:20260322T080000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parse(raw)
        assertTrue(result is ImportResult.Success)
        val event = (result as ImportResult.Success).payload.events.first()
        assertEquals("线性代数", event.summary)
        assertEquals("A101", event.location)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", event.rRule)
        assertEquals(1, event.exDates.size)
    }

    @Test
    fun parse_preservesTzidAndRecurrenceException() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Math moved
            DTSTART;TZID=Asia/Shanghai:20260315T080000
            DTEND;TZID=Asia/Shanghai:20260315T090000
            EXDATE;TZID=Asia/Shanghai:20260322T080000
            RECURRENCE-ID;TZID=Asia/Shanghai:20260329T080000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = ((parser.parse(raw) as ImportResult.Success).payload.events.single())
        assertEquals(Instant.parse("2026-03-15T00:00:00Z"), event.dtStart)
        assertEquals(Instant.parse("2026-03-22T00:00:00Z"), event.exDates.single())
        assertEquals(Instant.parse("2026-03-29T00:00:00Z"), event.recurrenceId)
        assertEquals("Asia/Shanghai", event.rawFields["DTSTART_TZID"])
    }
}
