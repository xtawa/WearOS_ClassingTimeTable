package com.classing.shared.reminder

import com.classing.shared.model.Course
import com.classing.shared.model.ReminderRule
import com.classing.shared.model.TimeSlot
import com.classing.shared.model.WeekParity
import com.classing.shared.model.WeekRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderCalculatorTest {
    @Test
    fun calculate_generates_multi_lead_notifications() {
        val calculator = ReminderCalculator()
        val reminders = calculator.calculate(
            courses = listOf(
                Course(
                    id = 1,
                    semesterId = 1,
                    title = "操作系统",
                    teacher = null,
                    location = null,
                    description = null,
                    dayOfWeek = DayOfWeek.MONDAY,
                    timeSlotId = 10,
                    weekRule = WeekRule(1, 18, WeekParity.ALL),
                    reminderEnabled = true,
                ),
            ),
            timeSlots = mapOf(10L to TimeSlot(10, "1-2", LocalTime.of(8, 0), LocalTime.of(9, 35))),
            rules = listOf(ReminderRule(5, true), ReminderRule(10, true), ReminderRule(15, false)),
            weekIndexProvider = { 2 },
            from = LocalDate.of(2026, 3, 16),
            to = LocalDate.of(2026, 3, 16),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )
        assertEquals(2, reminders.size)
    }
}
