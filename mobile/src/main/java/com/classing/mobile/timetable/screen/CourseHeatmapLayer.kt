package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.classing.shared.ui.heatmap.HeatmapLessonInput
import com.classing.shared.ui.heatmap.buildHeatmapCells
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.components.ClassingInformationIsland
import com.xtawa.classingtime.ui.components.ClassingPageHeader
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun CourseHeatmapLayer(
    contentPadding: PaddingValues,
    lessons: List<LessonUi>,
    calendarEvents: List<SystemCalendarEvent> = emptyList(),
    onBack: () -> Unit,
) {
    val cells = remember(lessons, calendarEvents) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val incoming = calendarEvents.filter { !it.allDay }.mapNotNull { event ->
            val start = Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDateTime()
            val end = Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalDateTime()
            if (start.toLocalDate().isBefore(today) || start.toLocalDate().isAfter(today.plusDays(6)) ||
                end.toLocalDate() != start.toLocalDate() || !end.toLocalTime().isAfter(start.toLocalTime())) null
            else HeatmapLessonInput(start.dayOfWeek, start.toLocalTime(), end.toLocalTime())
        }
        buildHeatmapCells(lessons.map { HeatmapLessonInput(it.dayOfWeek, it.startTime, it.endTime) } + incoming)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding)
            .padding(horizontal = ClassingSpacing.referenceScreenInset)
            .navigationBarsPadding()
            .padding(bottom = ClassingSpacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.lg),
    ) {
        ClassingPageHeader(
            title = stringResource(R.string.heatmap_title),
            eyebrow = "Classing",
            supportingText = stringResource(R.string.heatmap_description),
            onBack = onBack,
            backLabel = stringResource(R.string.timetable_title),
        )
        ClassingInformationIsland {
            if (cells.isEmpty()) {
                Text(stringResource(R.string.heatmap_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                CourseHeatmapGrid(cells = cells, cellSize = ClassingSpacing.lg)
                Text(stringResource(R.string.heatmap_legend), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
