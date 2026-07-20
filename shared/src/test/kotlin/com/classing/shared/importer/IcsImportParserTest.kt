package com.classing.shared.importer

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertEquals(1, result.payload.summary.importedCount)
        assertEquals(0, result.payload.summary.skippedCount)
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

    @Test
    fun parse_supportsValueDate() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:All day class
            DTSTART;VALUE=DATE;TZID=Asia/Shanghai:20260315
            DTEND;VALUE=DATE;TZID=Asia/Shanghai:20260316
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val event = ((parser.parse(raw) as ImportResult.Success).payload.events.single())
        assertEquals(Instant.parse("2026-03-14T16:00:00Z"), event.dtStart)
        assertEquals(Instant.parse("2026-03-15T16:00:00Z"), event.dtEnd)
        assertEquals("DATE", event.rawFields["DTSTART_VALUE"])
    }

    @Test
    fun parse_skipsEventWithInvalidDtStart() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Broken start
            DTSTART:not-a-date
            DTEND:20260315T094000Z
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Valid class
            DTSTART:20260315T100000Z
            DTEND:20260315T110000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parse(raw)
        assertTrue(result is ImportResult.PartialSuccess)
        assertEquals(1, result.payload.events.size)
        assertEquals("Valid class", result.payload.events.single().summary)
        assertEquals(1, result.payload.summary.skippedCount)
        assertEquals("DTSTART", result.payload.summary.warnings.single().field)
    }

    @Test
    fun parse_skipsEventWithInvalidDtEnd() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Broken end
            DTSTART:20260315T080000Z
            DTEND:not-a-date
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parse(raw)
        assertTrue(result is ImportResult.PartialSuccess)
        assertEquals(0, result.payload.events.size)
        assertEquals(1, result.payload.summary.skippedCount)
        assertEquals("DTEND", result.payload.summary.warnings.single().field)
    }

    @Test
    fun parse_skipsEventWithInvalidTzid() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Broken zone
            DTSTART;TZID=Bad/Zone:20260315T080000
            DTEND;TZID=Bad/Zone:20260315T090000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parse(raw)
        assertTrue(result is ImportResult.PartialSuccess)
        assertEquals(0, result.payload.events.size)
        assertEquals(1, result.payload.summary.skippedCount)
        assertEquals("DTSTART_TZID", result.payload.summary.warnings.single().field)
    }

    @Test
    fun parse_keepsValidExdatesAndWarnsAboutInvalidOnes() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Partial exdate
            DTSTART:20260315T080000Z
            DTEND:20260315T090000Z
            EXDATE:20260322T080000Z,not-a-date
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parse(raw)
        assertTrue(result is ImportResult.PartialSuccess)
        val event = result.payload.events.single()
        assertEquals(listOf(Instant.parse("2026-03-22T08:00:00Z")), event.exDates)
        assertEquals("EXDATE", result.payload.summary.warnings.single().field)
    }

    @Test
    fun parse_warnsAboutInvalidRecurrenceId() {
        val raw = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Broken recurrence
            DTSTART:20260315T080000Z
            DTEND:20260315T090000Z
            RECURRENCE-ID:not-a-date
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parse(raw)
        assertTrue(result is ImportResult.PartialSuccess)
        val event = result.payload.events.single()
        assertNull(event.recurrenceId)
        assertEquals("RECURRENCE-ID", result.payload.summary.warnings.single().field)
    }

    @Test
    fun parse_supportsLfCrlfBareCrAndFoldedLines() {
        val lf = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:LF\nDTSTART:20260315T080000Z\nDTEND:20260315T090000Z\nEND:VEVENT\nEND:VCALENDAR"
        val crlf = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:CRLF\r\nDTSTART:20260315T080000Z\r\nDTEND:20260315T090000Z\r\nEND:VEVENT\r\nEND:VCALENDAR"
        val bareCr = "BEGIN:VCALENDAR\rBEGIN:VEVENT\rSUMMARY:CR\rDTSTART:20260315T080000Z\rDTEND:20260315T090000Z\rEND:VEVENT\rEND:VCALENDAR"
        val folded = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:Very \r\n long class\r\nDTSTART:20260315T080000Z\r\nDTEND:20260315T090000Z\r\nEND:VEVENT\r\nEND:VCALENDAR"

        assertEquals("LF", (parser.parse(lf) as ImportResult.Success).payload.events.single().summary)
        assertEquals("CRLF", (parser.parse(crlf) as ImportResult.Success).payload.events.single().summary)
        assertEquals("CR", (parser.parse(bareCr) as ImportResult.Success).payload.events.single().summary)
        assertEquals("Very long class", (parser.parse(folded) as ImportResult.Success).payload.events.single().summary)
    }
}
