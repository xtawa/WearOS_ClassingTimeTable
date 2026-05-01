package com.classing.wear.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 热力图单元格数据
 */
data class HeatmapCell(
    val dayOfWeek: DayOfWeek,
    val timeSlotIndex: Int,
    val timeSlotLabel: String,
    val lessonCount: Int,
)

/**
 * 计算热力图单元格：按星期和时间段聚合课程数量
 */
fun buildHeatmapCells(
    lessons: List<HeatmapLessonInput>,
): List<HeatmapCell> {
    if (lessons.isEmpty()) return emptyList()

    // 收集所有出现的时间段（按 startTime 排序去重）
    val slotMap = linkedMapOf<String, Int>() // "HH:mm-HH:mm" -> index
    lessons.forEach { lesson ->
        val key = "${lesson.startTime.format(timeFmt)}-${lesson.endTime.format(timeFmt)}"
        if (key !in slotMap) {
            slotMap[key] = slotMap.size
        }
    }

    // 计数：(dayOfWeek, slotKey) -> count
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

/**
 * 热力图课程输入（与平台无关）
 */
data class HeatmapLessonInput(
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

/**
 * GitHub 风格课程热力图
 *
 * 列: 周一~周日
 * 行: 时间段
 * 颜色强度: 基于 MaterialTheme.colorScheme.primary 的 alpha
 */
@Composable
fun CourseHeatmapGrid(
    cells: List<HeatmapCell>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 14.dp,
    cellCornerRadius: Dp = 3.dp,
    cellSpacing: Dp = 3.dp,
) {
    if (cells.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerLow

    val days = remember(cells) { cells.map { it.dayOfWeek }.distinct() }
    val slots = remember(cells) {
        cells.map { it.timeSlotIndex to it.timeSlotLabel }.distinctBy { it.first }
    }

    Column(modifier = modifier) {
        // 列头：星期缩写
        Row(
            modifier = Modifier.padding(start = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        ) {
            days.forEach { day ->
                Text(
                    text = dayShortLabel(day),
                    modifier = Modifier.size(cellSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 每行：时间段标签 + 7 个格子
        slots.forEach { (slotIndex, slotLabel) ->
            Row(
                modifier = Modifier.padding(top = cellSpacing),
                horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = slotLabel,
                    modifier = Modifier.size(width = 36.dp, height = cellSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
                days.forEach { day ->
                    val cell = cells.find { it.dayOfWeek == day && it.timeSlotIndex == slotIndex }
                    val count = cell?.lessonCount ?: 0
                    val color = heatmapColor(count, primaryColor, emptyColor)
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(cellCornerRadius))
                            .background(color),
                    )
                }
            }
        }
    }
}

/**
 * 莫奈动态配色适配：基于 primary 色的 alpha 实现热力效果
 */
private fun heatmapColor(count: Int, primary: Color, empty: Color): Color {
    return when {
        count <= 0 -> empty
        count == 1 -> primary.copy(alpha = 0.18f)
        count == 2 -> primary.copy(alpha = 0.38f)
        count == 3 -> primary.copy(alpha = 0.60f)
        else -> primary.copy(alpha = 0.85f)
    }
}

private fun dayShortLabel(day: DayOfWeek): String {
    return when (day) {
        DayOfWeek.MONDAY -> "M"
        DayOfWeek.TUESDAY -> "T"
        DayOfWeek.WEDNESDAY -> "W"
        DayOfWeek.THURSDAY -> "T"
        DayOfWeek.FRIDAY -> "F"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "S"
    }
}
