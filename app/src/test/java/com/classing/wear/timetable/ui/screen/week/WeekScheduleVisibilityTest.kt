package com.classing.wear.timetable.ui.screen.week

import com.classing.wear.timetable.domain.model.WeekSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class WeekScheduleVisibilityTest {
    @Test
    fun applyWeekendVisibility_removesWeekend_whenDisabled() {
        val schedule = WeekSchedule(
            weekIndex = 1,
            days = DayOfWeek.values().associateWith { emptyList() },
        )

        val filtered = schedule.applyWeekendVisibility(showWeekend = false)

        assertEquals(5, filtered.days.size)
        assertEquals(false, filtered.days.containsKey(DayOfWeek.SATURDAY))
        assertEquals(false, filtered.days.containsKey(DayOfWeek.SUNDAY))
    }
}
