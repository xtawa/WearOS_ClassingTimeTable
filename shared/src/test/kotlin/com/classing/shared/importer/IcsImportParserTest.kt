package com.classing.shared.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
