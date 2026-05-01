package com.classing.shared.ui.heatmap

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class HeatmapCell(
    val dayOfWeek: DayOfWeek,
    val timeSlotIndex: Int,
    val timeSlotLabel: String,
    val lessonCount: Int,
)

data class HeatmapLessonInput(
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun buildHeatmapCells(
    lessons: List<HeatmapLessonInput>,
): List<HeatmapCell> {
    if (lessons.isEmpty()) return emptyList()

    val slotMap = linkedMapOf<String, Int>()
    lessons.forEach { lesson ->
        val key = "${lesson.startTime.format(timeFmt)}-${lesson.endTime.format(timeFmt)}"
        if (key !in slotMap) {
            slotMap[key] = slotMap.size
        }
    }

    val counts = mutableMapOf<Pair<DayOfWeek, String>, Int>()
    lessons.forEach { lesson ->
        val key = "${lesson.startTime.format(timeFmt)}-${lesson.endTime.format(timeFmt)}"
        val pair = lesson.dayOfWeek to key
        counts[pair] = (counts[pair] ?: 0) + 1
    }

    val days = DayOfWeek.entries
    return buildList {
        days.forEach { day ->
            slotMap.forEach { (slotKey, slotIndex) ->
                val count = counts[day to slotKey] ?: 0
                val label = slotKey.split("-").firstOrNull() ?: slotKey
                add(
                    HeatmapCell(
                        dayOfWeek = day,
                        timeSlotIndex = slotIndex,
                        timeSlotLabel = label,
                        lessonCount = count,
                    ),
                )
            }
        }
    }
}
