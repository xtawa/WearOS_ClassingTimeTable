package com.xtawa.classingtime.screen

import com.classing.shared.importer.CourseDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MobileWeekRuleInferenceTest {
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun inferWeekRuleFromDraft_defaults_whenNoRecurrence() {
        val draft = CourseDraft(
            title = "Math",
            location = "A101",
            note = null,
            start = Instant.parse("2026-03-02T00:00:00Z"),
            end = Instant.parse("2026-03-02T01:40:00Z"),
            recurrence = null,
            excludes = emptyList(),
            sourceRaw = emptyMap(),
        )

        val rule = inferWeekRuleFromDraft(
            draft = draft,
            startDate = LocalDate.of(2026, 3, 2),
            zoneId = zoneId,
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = LocalDate.of(2026, 3, 2),
        )

        assertEquals(DEFAULT_START_WEEK, rule.startWeek)
        assertEquals(DEFAULT_END_WEEK, rule.endWeek)
        assertEquals(LessonWeekParity.ALL, rule.weekParity)
    }

    @Test
    fun inferWeekRuleFromDraft_parsesIntervalTwoAndUntil() {
        val draft = CourseDraft(
            title = "Physics",
            location = null,
            note = null,
            start = Instant.parse("2026-03-09T00:00:00Z"),
            end = Instant.parse("2026-03-09T01:40:00Z"),
            recurrence = "FREQ=WEEKLY;INTERVAL=2;UNTIL=20260525",
            excludes = emptyList(),
            sourceRaw = emptyMap(),
        )

        val rule = inferWeekRuleFromDraft(
            draft = draft,
            startDate = LocalDate.of(2026, 3, 9),
            zoneId = zoneId,
            weekNumberMode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = LocalDate.of(2026, 3, 2),
        )

        assertEquals(2, rule.startWeek)
        assertEquals(13, rule.endWeek)
        assertEquals(LessonWeekParity.EVEN, rule.weekParity)
    }

    @Test
    fun parseRRuleUntilDate_supportsUtcDateTime() {
        val localDate = parseRRuleUntilDate("20260401T000000Z", zoneId)

        assertEquals(LocalDate.of(2026, 4, 1), localDate)
    }

    @Test
    fun weekIndexForMode_usesSemesterAnchor() {
        val week = weekIndexForMode(
            date = LocalDate.of(2026, 3, 16),
            mode = WeekNumberMode.SEMESTER,
            semesterWeekStartDate = LocalDate.of(2026, 3, 2),
        )

        assertEquals(3, week)
    }
}
