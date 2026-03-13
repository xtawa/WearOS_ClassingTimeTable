package com.classing.wear.timetable.reminder

import com.classing.shared.model.Course
import com.classing.shared.model.ReminderRule
import com.classing.shared.model.TimeSlot
import java.time.LocalDate
import java.time.ZoneId

class ReminderRebuildUseCase(
    private val calculator: ReminderCalculator,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend fun rebuild(
        courses: List<Course>,
        slots: Map<Long, TimeSlot>,
        rules: List<ReminderRule>,
        weekIndexProvider: (LocalDate) -> Int,
        zoneId: ZoneId,
    ) {
        val reminders = calculator.calculate(
            courses = courses,
            slots = slots,
            rules = rules,
            weekIndexProvider = weekIndexProvider,
            start = LocalDate.now(zoneId),
            end = LocalDate.now(zoneId).plusDays(14),
            zoneId = zoneId,
        )
        repository.clearAll()
        repository.replaceAll(reminders)
        scheduler.cancelAll()
        scheduler.schedule(reminders)
    }
}
