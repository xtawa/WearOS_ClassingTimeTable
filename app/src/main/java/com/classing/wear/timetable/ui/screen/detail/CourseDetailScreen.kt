package com.classing.wear.timetable.ui.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.ui.PreviewSamples
import com.classing.wear.timetable.ui.component.EmptyState
import com.classing.wear.timetable.ui.component.LoadingState
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.state.CourseDetailUiState
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme

@Composable
fun CourseDetailScreen(
    state: CourseDetailUiState,
    onBack: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.detail_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_back))
                    }
                }
            }
        }

        when {
            state.isLoading -> item { LoadingState(message = stringResource(R.string.detail_loading)) }
            state.course == null -> item {
                EmptyState(
                    title = stringResource(R.string.detail_course_not_found_title),
                    subtitle = stringResource(R.string.detail_course_not_found_subtitle),
                )
            }
            else -> {
                item { CourseSummaryCard(state) }
                item {
                    Text(
                        text = stringResource(R.string.home_action_this_week),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (state.upcomingLessons.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.detail_no_schedule_title),
                            subtitle = stringResource(R.string.detail_no_schedule_subtitle),
                        )
                    }
                } else {
                    items(state.upcomingLessons) { lesson ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = lesson.timeSlot.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = ">",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseSummaryCard(state: CourseDetailUiState) {
    val course = state.course ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleMedium,
            )
            DetailMetaRow(
                icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                text = stringResource(R.string.detail_teacher, course.teacher),
            )
            DetailMetaRow(
                icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                text = stringResource(R.string.detail_location, course.classroom),
            )
            DetailMetaRow(
                iconLabel = "*",
                text = stringResource(
                    R.string.detail_note,
                    course.note.ifBlank { stringResource(R.string.detail_note_empty) },
                ),
            )
        }
    }
}

@Composable
private fun DetailMetaRow(
    iconLabel: String? = null,
    icon: (@Composable () -> Unit)? = null,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            icon != null -> icon()
            !iconLabel.isNullOrBlank() -> {
                Text(
                    text = iconLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
