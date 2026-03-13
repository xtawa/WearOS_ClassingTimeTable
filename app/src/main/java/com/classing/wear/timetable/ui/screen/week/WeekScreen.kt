package com.classing.wear.timetable.ui.screen.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

@Composable
fun WeekScreen(
    state: WeekUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onLessonClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
            state.isLoading -> item { LoadingState(message = "正在加载周课表") }
            state.schedule.days.isEmpty() -> item {
                EmptyState(title = "暂无课表", subtitle = "请先同步或导入课程")
            }
            else -> {
                items(state.schedule.days.entries.toList()) { entry ->
                    DayScheduleCard(
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(modifier = Modifier.weight(1f), onClick = onPreviousWeek) { Text("上周") }
            Button(modifier = Modifier.weight(1f), onClick = onCurrentWeek) { Text("本周") }
            Button(modifier = Modifier.weight(1f), onClick = onNextWeek) { Text("下周") }
        }
    }
}

@Composable
private fun DayScheduleCard(
    day: DayOfWeek,
    lessons: List<LessonOccurrence>,
    onLessonClick: (Long) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "${dayLabel(day)} (${lessons.size}节)", style = MaterialTheme.typography.titleSmall)
            if (lessons.isEmpty()) {
                Text(
                    text = "无课",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
}

private fun dayLabel(day: DayOfWeek): String {
    return when (day) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
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
            state = WeekUiState(isLoading = false, weekLabel = "第3周", schedule = week),
            onPreviousWeek = {},
            onNextWeek = {},
            onCurrentWeek = {},
            onLessonClick = {},
        )
    }
}
