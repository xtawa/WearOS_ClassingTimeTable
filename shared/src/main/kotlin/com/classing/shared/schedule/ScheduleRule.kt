package com.classing.shared.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RepeatKind { WEEKLY, ROTATION, DATES }

/** A rule belongs to one meeting (lecture/lab/block), not the whole course. */
data class ScheduleRule(
    val kind: RepeatKind,
    val anchorDate: LocalDate,
    val endDate: LocalDate,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    val cycleLength: Int = 2,
    val cycleDays: Set<Int> = emptySet(),
    val schoolDays: Set<DayOfWeek> = (1..5).map(DayOfWeek::of).toSet(),
    val holidays: Set<LocalDate> = emptySet(),
    val advanceOnHolidays: Boolean = false,
    val dates: Set<LocalDate> = emptySet(),
    val courseGroupId: String? = null,
    val meetingType: String? = null,
    val termName: String? = null,
) {
    init {
        require(!endDate.isBefore(anchorDate)) { "Term ends before its anchor" }
        require(ChronoUnit.DAYS.between(anchorDate, endDate) <= 3660) { "Term exceeds ten years" }
        require(interval in 1..366 && cycleLength in 1..30)
        require(cycleDays.all { it in 1..cycleLength })
        require(kind != RepeatKind.ROTATION || schoolDays.isNotEmpty() && cycleDays.isNotEmpty())
        require(dates.all { it in anchorDate..endDate })
    }

    fun matches(date: LocalDate, fallbackDay: DayOfWeek): Boolean {
        if (date !in anchorDate..endDate || date in holidays) return false
        return when (kind) {
            RepeatKind.DATES -> date in dates
            RepeatKind.WEEKLY -> {
                val firstWeek = anchorDate.minusDays(Math.floorMod(anchorDate.dayOfWeek.value - weekStartDay.value, 7).toLong())
                val thisWeek = date.minusDays(Math.floorMod(date.dayOfWeek.value - weekStartDay.value, 7).toLong())
                date.dayOfWeek in daysOfWeek.ifEmpty { setOf(fallbackDay) } &&
                    ChronoUnit.WEEKS.between(firstWeek, thisWeek) % interval == 0L
            }
            RepeatKind.ROTATION -> {
                if (date.dayOfWeek !in schoolDays) return false
                // Count eligible school days, not elapsed calendar days. Holidays
                // either pause the pointer or advance it, explicitly per school.
                val days = ChronoUnit.DAYS.between(anchorDate, date)
                var elapsed = (days / 7 * schoolDays.size).toInt()
                for (offset in 0 until (days % 7).toInt()) {
                    if (anchorDate.plusDays(offset.toLong()).dayOfWeek in schoolDays) elapsed++
                }
                if (!advanceOnHolidays) elapsed -= holidays.count {
                    it >= anchorDate && it < date && it.dayOfWeek in schoolDays
                }
                elapsed % cycleLength + 1 in cycleDays
            }
        }
    }
}
