package com.classing.wear.timetable.ui.screen.week

import com.classing.wear.timetable.domain.model.WeekSchedule

internal fun WeekSchedule.applyWeekendVisibility(showWeekend: Boolean): WeekSchedule {
    if (showWeekend) return this
    return copy(days = days.filterKeys { it.value <= 5 })
}
