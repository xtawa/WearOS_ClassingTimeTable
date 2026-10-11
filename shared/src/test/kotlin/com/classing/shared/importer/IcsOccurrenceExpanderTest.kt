package com.classing.shared.importer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

class IcsOccurrenceExpanderTest {
    private val zone = ZoneId.of("UTC")
    private fun expand(rule: String = "", extras: String = "", overrides: String = "", start: String = "DTSTART:20260223T090000Z", end: String = "DTEND:20260223T100000Z", from: String = "2026-02-23", through: String = "2026-03-15", target: ZoneId = zone): ExpandedImport {
        val raw = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:class-1\nSUMMARY:Math\n$start\n$end\n$rule\n$extras\nEND:VEVENT\n$overrides\nEND:VCALENDAR"
        val parsed = IcsImportParser().parse(raw)
        val events = when(parsed) { is ImportResult.Success -> parsed.payload.events; is ImportResult.PartialSuccess -> parsed.payload.events; else -> error("Fixture parse failed") }
        return IcsOccurrenceExpander().expand(events, LocalDate.parse(from), LocalDate.parse(through), target)
    }
    private fun dates(result: ExpandedImport) = result.occurrences.map { it.start.atZone(zone).toLocalDate().toString() }
    @Test fun weeklyByday() = assertEquals(listOf("2026-02-23","2026-02-26","2026-03-02","2026-03-05","2026-03-09","2026-03-12"), dates(expand("RRULE:FREQ=WEEKLY;BYDAY=MO,TH")))
    @Test fun intervalTwo() = assertEquals(listOf("2026-02-23","2026-03-09"), dates(expand("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO")))
    @Test fun untilInclusive() = assertEquals(listOf("2026-02-23","2026-03-02"), dates(expand("RRULE:FREQ=WEEKLY;UNTIL=20260302T090000Z")))
    @Test fun countIncludesExcludedDate() = assertEquals(listOf("2026-02-23","2026-02-25"), dates(expand("RRULE:FREQ=DAILY;COUNT=3", "EXDATE:20260224T090000Z")))
    @Test fun exceptionCancelsWithoutStartOrEnd() {
        val cancel = "BEGIN:VEVENT\nUID:class-1\nRECURRENCE-ID:20260302T090000Z\nSTATUS:CANCELLED\nEND:VEVENT"
        assertEquals(listOf("2026-02-23","2026-03-09"), dates(expand("RRULE:FREQ=WEEKLY", overrides=cancel)))
    }
    @Test fun movedSourceOutsideWindow() {
        val move = "BEGIN:VEVENT\nUID:class-1\nSUMMARY:Math moved\nRECURRENCE-ID:20260223T090000Z\nDTSTART:20260304T110000Z\nDTEND:20260304T120000Z\nEND:VEVENT"
        assertEquals(listOf("2026-03-02","2026-03-04"), dates(expand("RRULE:FREQ=WEEKLY;COUNT=2", overrides=move, from="2026-03-01", through="2026-03-07")))
    }
    @Test fun movedFutureSourceIntoWindow() {
        val move = "BEGIN:VEVENT\nUID:class-1\nSUMMARY:Math moved\nRECURRENCE-ID:20260309T090000Z\nDTSTART:20260224T110000Z\nDTEND:20260224T120000Z\nEND:VEVENT"
        assertEquals(listOf("2026-02-23","2026-02-24"), dates(expand("RRULE:FREQ=WEEKLY;COUNT=3", overrides=move, through="2026-02-28")))
    }
    @Test fun springDstKeepsSchoolWallTime() {
        val result = expand("RRULE:FREQ=WEEKLY;COUNT=3", start="DTSTART;TZID=America/New_York:20260302T090000", end="DTEND;TZID=America/New_York:20260302T100000", from="2026-03-01", through="2026-03-20")
        assertEquals(listOf("2026-03-02T14:00:00Z","2026-03-09T13:00:00Z","2026-03-16T13:00:00Z"), result.occurrences.map { it.start.toString() })
    }
    @Test fun generatedDstGapIsSkippedAndDoesNotConsumeCount() {
        val result = expand("RRULE:FREQ=WEEKLY;COUNT=3",
            start="DTSTART;TZID=America/New_York:20260301T023000",
            end="DTEND;TZID=America/New_York:20260301T033000", from="2026-03-01", through="2026-03-22")
        assertEquals(listOf("2026-03-01T07:30:00Z", "2026-03-15T06:30:00Z", "2026-03-22T06:30:00Z"),
            result.occurrences.map { it.start.toString() })
        assertTrue(result.warnings.any { it.contains("2026-03-08") && it.contains("DST gap") })
    }
    @Test fun dtEndDurationRemainsExactAcrossDstTransition() {
        val result = expand("RRULE:FREQ=WEEKLY;COUNT=2",
            start="DTSTART;TZID=America/New_York:20260301T013000",
            end="DTEND;TZID=America/New_York:20260301T033000", from="2026-03-01", through="2026-03-08")
        assertEquals(listOf("2026-03-01T08:30:00Z", "2026-03-08T08:30:00Z"), result.occurrences.map { it.end.toString() })
        assertTrue(result.occurrences.all { java.time.Duration.between(it.start, it.end).toHours() == 2L })
    }
    @Test fun fallDstKeepsSchoolWallTime() {
        val result = expand("RRULE:FREQ=WEEKLY;COUNT=2", start="DTSTART;TZID=America/New_York:20261026T090000", end="DTEND;TZID=America/New_York:20261026T100000", from="2026-10-26", through="2026-11-03")
        assertEquals(listOf(Instant.parse("2026-10-26T13:00:00Z"),Instant.parse("2026-11-02T14:00:00Z")), result.occurrences.map { it.start })
    }
    @Test fun utcRecurrenceDoesNotAdoptTargetZone() {
        val result = expand("RRULE:FREQ=WEEKLY;COUNT=3", start="DTSTART:20260302T090000Z", end="DTEND:20260302T100000Z", from="2026-03-01", through="2026-03-20", target=ZoneId.of("America/New_York"))
        assertEquals(listOf(9,9,9), result.occurrences.map { it.start.atZone(zone).hour })
    }
    @Test fun unsupportedMonthlyIsExplicitlyRejected() {
        val result = expand("RRULE:FREQ=MONTHLY;BYDAY=1MO")
        assertTrue(result.occurrences.isEmpty()); assertTrue(result.warnings.single().contains("Unsupported"))
    }
    @Test fun wkstChangesIntervalBoundary() {
        val result = expand("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=SU,MO;WKST=SU")
        assertEquals(listOf("2026-02-23","2026-03-08","2026-03-09"), dates(result))
    }
    @Test fun infiniteRuleRequiresVisibleHorizonWarning() = assertTrue(expand("RRULE:FREQ=WEEKLY").warnings.any { it.contains("through 2026-03-15") })
}
