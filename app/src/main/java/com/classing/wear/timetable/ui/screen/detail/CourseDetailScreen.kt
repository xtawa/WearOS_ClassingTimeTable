package com.classing.wear.timetable.ui.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.ui.PreviewSamples
import com.classing.wear.timetable.ui.component.EmptyState
import com.classing.wear.timetable.ui.component.LessonCard
import com.classing.wear.timetable.ui.component.LoadingState
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.state.CourseDetailUiState
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme

@Composable
fun CourseDetailScreen(
    state: CourseDetailUiState,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "课程详情",
                    style = MaterialTheme.typography.titleSmall,
                )
                Button(onClick = onBack) {
                    Text("返回")
                }
            }
        }

        when {
            state.isLoading -> item { LoadingState(message = "正在加载课程") }
            state.course == null -> item { EmptyState(title = "课程不存在", subtitle = "可能已被删除") }
            else -> {
                item {
                    CourseSummaryCard(state)
                }
                if (state.upcomingLessons.isEmpty()) {
                    item {
                        EmptyState(title = "本周无排课", subtitle = "检查单双周或周次规则")
                    }
                } else {
                    items(state.upcomingLessons) { lesson ->
                        LessonCard(lesson = lesson)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseSummaryCard(state: CourseDetailUiState) {
    val course = state.course ?: return
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = course.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "教师: ${course.teacher}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "地点: ${course.classroom}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "备注: ${course.note.ifBlank { "暂无" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 220, heightDp = 220)
@Composable
private fun CourseDetailPreview() {
    val lesson = PreviewSamples.sampleLesson()
    ClassingTimetableTheme(useDynamicColor = false) {
        CourseDetailScreen(
            state = CourseDetailUiState(
                isLoading = false,
                course = lesson.course,
                upcomingLessons = listOf(lesson),
            ),
            onBack = {},
        )
    }
}
