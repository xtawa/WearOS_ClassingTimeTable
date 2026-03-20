package com.classing.wear.timetable.ui.screen.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.wear.timetable.R
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.WeekSchedule
import com.classing.wear.timetable.ui.PreviewSamples
import com.classing.wear.timetable.ui.component.EmptyState
import com.classing.wear.timetable.ui.component.LessonCard
import com.classing.wear.timetable.ui.component.LoadingState
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.state.WeekUiState
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekScreen(
    state: WeekUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onLessonClick: (Long) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            WeekHeader(
                label = state.weekLabel,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onCurrentWeek = onCurrentWeek,
            )
        }

        when {
            state.isLoading -> item {
                LoadingState(message = stringResource(R.string.week_loading))
            }

            state.schedule.days.isEmpty() -> item {
                EmptyState(
                    title = stringResource(R.string.week_empty_title),
                    subtitle = stringResource(R.string.week_empty_subtitle),
                )
            }

            else -> {
                items(state.schedule.days.entries.toList()) { entry ->
                    DayScheduleSection(
                        day = entry.key,
                        lessons = entry.value,
                        onLessonClick = onLessonClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(
    label: String,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CircleActionButton(
                    label = "‹",
                    onClick = onPreviousWeek,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                CircleActionButton(
                    label = "›",
                    onClick = onNextWeek,
                )
            }
            Card(
                onClick = onCurrentWeek,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(999.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.week_action_current),
                        modifier = Modifier.padding(start = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier.size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun DayScheduleSection(
    day: DayOfWeek,
    lessons: List<LessonOccurrence>,
    onLessonClick: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.week_day_lesson_count, dayLabel(day), lessons.size),
                style = MaterialTheme.typography.labelLarge,
                color = if (lessons.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (!lessons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)),
                )
            }
        }
        if (lessons.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.week_no_lessons),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            lessons.forEach { lesson ->
                LessonCard(
                    lesson = lesson,
                    onClick = { onLessonClick(lesson.course.localId) },
                )
            }
        }
    }
}

private fun dayLabel(day: DayOfWeek): String {
    return day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

@Preview(showBackground = true, widthDp = 220, heightDp = 220)
@Composable
private fun WeekScreenPreview() {
    val lesson = PreviewSamples.sampleLesson()
    val week = WeekSchedule(
        weekIndex = 3,
        days = mapOf(
            DayOfWeek.MONDAY to listOf(lesson),
            DayOfWeek.TUESDAY to emptyList(),
        ),
    )
    ClassingTimetableTheme(useDynamicColor = false) {
        WeekScreen(
            state = WeekUiState(isLoading = false, weekLabel = "Week 3", schedule = week),
            onPreviousWeek = {},
            onNextWeek = {},
            onCurrentWeek = {},
            onLessonClick = {},
        )
    }
}
