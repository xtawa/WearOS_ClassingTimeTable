package com.classing.wear.timetable.reminder

import com.classing.shared.model.Course
import com.classing.shared.model.ReminderRule
import com.classing.shared.model.TimeSlot
import java.time.LocalDate
import java.time.ZoneId

class ReminderCalculator(
    private val delegate: com.classing.shared.reminder.ReminderCalculator = com.classing.shared.reminder.ReminderCalculator(),
) {
    fun calculate(
        courses: List<Course>,
        slots: Map<Long, TimeSlot>,
        rules: List<ReminderRule>,
        weekIndexProvider: (LocalDate) -> Int,
        start: LocalDate,
        end: LocalDate,
        zoneId: ZoneId,
    ) = delegate.calculate(courses, slots, rules, weekIndexProvider, start, end, zoneId)
}
