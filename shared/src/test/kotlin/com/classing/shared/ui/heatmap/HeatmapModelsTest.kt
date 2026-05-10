package com.classing.shared.ui.heatmap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.DayOfWeek
import java.time.LocalTime

class HeatmapModelsTest {
    @Test
    fun buildHeatmapCells_returnsEmptyList_whenInputIsEmpty() {
        assertTrue(buildHeatmapCells(emptyList()).isEmpty())
    }

    @Test
    fun buildHeatmapCells_aggregatesRepeatedLessonsIntoTheSameCell() {
        val cells = buildHeatmapCells(
            listOf(
                HeatmapLessonInput(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                HeatmapLessonInput(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                HeatmapLessonInput(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)),
            ),
        )

        val mondayMorning = cells.first { it.dayOfWeek == DayOfWeek.MONDAY && it.timeSlotLabel == "08:00" }
        val tuesdayLateMorning = cells.first { it.dayOfWeek == DayOfWeek.TUESDAY && it.timeSlotLabel == "10:00" }

        assertEquals(2, mondayMorning.lessonCount)
        assertEquals(1, tuesdayLateMorning.lessonCount)
    }

    @Test
    fun buildHeatmapCells_buildsASevenDayGridForEachDistinctTimeSlot() {
        val cells = buildHeatmapCells(
            listOf(
                HeatmapLessonInput(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0)),
                HeatmapLessonInput(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)),
            ),
        )

        assertEquals(14, cells.size)
        assertEquals(7, cells.count { it.timeSlotIndex == 0 })
        assertEquals(7, cells.count { it.timeSlotIndex == 1 })
    }
}
