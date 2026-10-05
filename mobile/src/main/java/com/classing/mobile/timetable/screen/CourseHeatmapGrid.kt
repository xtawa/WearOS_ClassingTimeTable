package com.xtawa.classingtime.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.xtawa.classingtime.R
import com.classing.shared.ui.heatmap.HeatmapCell
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
    cellCornerRadius: Dp = 6.dp,
    cellSpacing: Dp = 4.dp,
) {
    if (cells.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerLow

    val days = remember(cells) { cells.map { it.dayOfWeek }.distinct().sortedBy { it.value } }
    val slots = remember(cells) {
        cells.map { it.timeSlotIndex to it.timeSlotLabel }.distinctBy { it.first }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 列头：星期缩写
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp + cellSpacing),
            horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        ) {
            days.forEach { day ->
                Text(
                    text = dayShortLabel(day),
                    modifier = Modifier.weight(1f).heightIn(min = 28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 每行：时间段标签 + 7 个格子
        slots.forEach { (slotIndex, slotLabel) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = cellSpacing).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = slotLabel,
                    modifier = Modifier.width(48.dp).heightIn(min = 28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
                days.forEach { day ->
                    val cell = cells.find { it.dayOfWeek == day && it.timeSlotIndex == slotIndex }
                    val count = cell?.lessonCount ?: 0
                    val color = heatmapColor(count, primaryColor, emptyColor)
                    val description = stringResource(R.string.heatmap_cell_description,
                        day.getDisplayName(TextStyle.FULL, Locale.getDefault()), slotLabel, count)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = cellSize.coerceAtLeast(28.dp))
                            .clip(RoundedCornerShape(cellCornerRadius))
                            .background(color)
                            .semantics { contentDescription = description },
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
    return day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
}
