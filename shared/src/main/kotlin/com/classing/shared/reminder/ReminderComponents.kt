package com.classing.shared.reminder

import com.classing.shared.model.Course
import com.classing.shared.model.ReminderInstance
import com.classing.shared.model.ReminderRule
import com.classing.shared.model.TimeSlot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderCalculator {
    fun calculate(
        courses: List<Course>,
        timeSlots: Map<Long, TimeSlot>,
        rules: List<ReminderRule>,
        weekIndexProvider: (LocalDate) -> Int,
        from: LocalDate,
        to: LocalDate,
        zoneId: ZoneId,
    ): List<ReminderInstance> {
        return generateSequence(from) { if (it < to) it.plusDays(1) else null }
            .takeWhile { !it.isAfter(to) }
            .flatMap { day ->
                val weekIndex = weekIndexProvider(day)
                courses.asSequence()
                    .filter { it.reminderEnabled && it.dayOfWeek == day.dayOfWeek && matchesWeekRule(it, weekIndex) }
                    .flatMap { course ->
                        val slot = timeSlots[course.timeSlotId] ?: return@flatMap emptySequence()
                        val classStart = LocalDateTime.of(day, slot.startTime).atZone(zoneId).toInstant()
                        rules.asSequence()
                            .filter { it.enabled }
                            .map { rule ->
                                ReminderInstance(
                                    id = "${course.id}-${day}-${rule.leadMinutes}",
                                    courseId = course.id,
                                    triggerAt = classStart.minusSeconds(rule.leadMinutes * 60L),
                                    title = course.title,
                                    body = "${rule.leadMinutes} 分钟后上课",
                                )
                            }
                    }
            }.toList()
    }

    private fun matchesWeekRule(course: Course, weekIndex: Int): Boolean {
        val rule = course.weekRule
        if (weekIndex !in rule.startWeek..rule.endWeek) return false
        return when (rule.parity) {
            com.classing.shared.model.WeekParity.ALL -> true
            com.classing.shared.model.WeekParity.ODD -> weekIndex % 2 == 1
            com.classing.shared.model.WeekParity.EVEN -> weekIndex % 2 == 0
        }
    }
}

interface ReminderScheduler {
    fun schedule(reminders: List<ReminderInstance>)
    fun cancelAll()
}

interface ReminderRepository {
    fun getAll(): List<ReminderInstance>
    fun saveAll(reminders: List<ReminderInstance>)
    fun clear()
}

class ReminderRebuildUseCase(
    private val calculator: ReminderCalculator,
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) {
    fun rebuild(
        courses: List<Course>,
        timeSlots: Map<Long, TimeSlot>,
        rules: List<ReminderRule>,
        weekIndexProvider: (LocalDate) -> Int,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        zoneId: ZoneId,
    ) {
        val reminders = calculator.calculate(
            courses = courses,
            timeSlots = timeSlots,
            rules = rules,
            weekIndexProvider = weekIndexProvider,
            from = windowStart,
            to = windowEnd,
            zoneId = zoneId,
        ).filter { it.triggerAt.isAfter(Instant.now()) }

        scheduler.cancelAll()
        repository.clear()
        repository.saveAll(reminders)
        scheduler.schedule(reminders)
    }
}
