package com.classing.shared.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScheduleGoldenTest {
    private val anchor = LocalDate.parse("2026-02-23")
    private fun dates(vararg days: Int) = days.map { anchor.plusDays(it.toLong()) }
    private fun rule(kind: RepeatKind, end: Int = 20) = ScheduleRule(kind, anchor, anchor.plusDays(end.toLong()), cycleDays = if (kind == RepeatKind.ROTATION) setOf(1) else emptySet())
    private fun actual(rule: ScheduleRule?, day: DayOfWeek = DayOfWeek.MONDAY, parity: WeekParity = WeekParity.ALL, from: Int = 0, end: Int = 20, startWeek: Int = 1, endWeek: Int = 4): List<LocalDate> = ScheduleProjector().project(
        ScheduleInput(anchor, courses = listOf(CourseRule("c", "Course", day, LocalTime.of(9, 0), LocalTime.of(10, 0), startWeek, endWeek, parity, scheduleRule = rule))),
        anchor.plusDays(from.toLong()), anchor.plusDays(end.toLong())).map { it.date }

    @Test fun thirtyIndependentCalendarCases() {
        // Expected dates are hand-labelled against a calendar, not produced by
        // the projector or the rotation implementation under test.
        val cases = listOf(
            "weekly Monday" to (actual(null) to dates(0,7,14)),
            "weekly Tuesday" to (actual(null, DayOfWeek.TUESDAY) to dates(1,8,15)),
            "weekly Sunday" to (actual(null, DayOfWeek.SUNDAY) to dates(6,13,20)),
            "odd weeks" to (actual(null, parity=WeekParity.ODD) to dates(0,14)),
            "even weeks" to (actual(null, parity=WeekParity.EVEN) to dates(7)),
            "late semester start" to (actual(null, startWeek=2) to dates(7,14)),
            "semester end" to (actual(null, endWeek=2) to dates(0,7)),
            "single week" to (actual(null, startWeek=2,endWeek=2) to dates(7)),
            "window excludes first week" to (actual(null,from=1) to dates(7,14)),
            "one-day query" to (actual(null,from=7,end=7) to dates(7)),
            "before semester" to (actual(null,from=-7,end=-1) to emptyList()),
            "after semester" to (actual(null,from=28,end=34) to emptyList()),
            "AB week A" to (actual(rule(RepeatKind.WEEKLY).copy(interval=2)) to dates(0,14)),
            "AB week B" to (actual(rule(RepeatKind.WEEKLY).copy(anchorDate=anchor.plusWeeks(1),interval=2)) to dates(7)),
            "three-week interval" to (actual(rule(RepeatKind.WEEKLY,28).copy(interval=3),end=28) to dates(0,21)),
            "lecture and tutorial weekdays" to (actual(rule(RepeatKind.WEEKLY).copy(daysOfWeek=setOf(DayOfWeek.MONDAY,DayOfWeek.THURSDAY))) to dates(0,3,7,10,14,17)),
            "weekly holiday" to (actual(rule(RepeatKind.WEEKLY).copy(holidays=setOf(anchor.plusWeeks(1)))) to dates(0,14)),
            "term includes end date" to (actual(rule(RepeatKind.WEEKLY,7)) to dates(0,7)),
            "term starts midweek" to (actual(rule(RepeatKind.WEEKLY).copy(anchorDate=anchor.plusDays(2))) to dates(7,14)),
            "AB day A" to (actual(rule(RepeatKind.ROTATION).copy(cycleDays=setOf(1))) to dates(0,2,4,8,10,14,16,18)),
            "AB day B" to (actual(rule(RepeatKind.ROTATION).copy(cycleDays=setOf(2))) to dates(1,3,7,9,11,15,17)),
            "five-day rotation" to (actual(rule(RepeatKind.ROTATION).copy(cycleLength=5,cycleDays=setOf(1))) to dates(0,7,14)),
            "six-day rotation" to (actual(rule(RepeatKind.ROTATION).copy(cycleLength=6,cycleDays=setOf(1))) to dates(0,8,16)),
            "eight-day rotation" to (actual(rule(RepeatKind.ROTATION).copy(cycleLength=8,cycleDays=setOf(1))) to dates(0,10)),
            "holiday pauses AB pointer" to (actual(rule(RepeatKind.ROTATION).copy(cycleDays=setOf(1),holidays=setOf(anchor.plusDays(1)))) to dates(0,3,7,9,11,15,17)),
            "holiday advances AB pointer" to (actual(rule(RepeatKind.ROTATION).copy(cycleDays=setOf(1),holidays=setOf(anchor.plusDays(1)),advanceOnHolidays=true)) to dates(0,2,4,8,10,14,16,18)),
            "seven-day rotation includes weekend" to (actual(rule(RepeatKind.ROTATION).copy(cycleLength=6,cycleDays=setOf(1),schoolDays=DayOfWeek.entries.toSet())) to dates(0,6,12,18)),
            "irregular lab" to (actual(rule(RepeatKind.DATES).copy(dates=dates(1,9,17).toSet())) to dates(1,9,17)),
            "quarter bounded lab" to (actual(rule(RepeatKind.DATES,9).copy(dates=dates(1,9).toSet(),meetingType="LAB",termName="Quarter 1")) to dates(1,9)),
            "trimester block" to (actual(rule(RepeatKind.DATES).copy(dates=dates(0,1,2).toSet(),meetingType="BLOCK",termName="Trimester")) to dates(0,1,2)),
        )
        assertEquals(30, cases.size)
        cases.forEach { (name, values) -> assertEquals(values.second, values.first, name) }
    }
    @Test fun rejectsInvalidRotationRatherThanApproximating() {
        assertFailsWith<IllegalArgumentException> { rule(RepeatKind.ROTATION).copy(cycleDays=setOf(3)) }
    }
}
