package com.xtawa.classingtime.screen

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
import com.classing.shared.ui.heatmap.HeatmapCell
import java.time.DayOfWeek

/**
 * GitHub 风格课程热力图 (Mobile 端)
 *
 * 列: 周一~周日
 * 行: 时间段
 * 颜色强度: 基于 MaterialTheme.colorScheme.primary 的 alpha
 */
@Composable
internal fun CourseHeatmapGrid(
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
