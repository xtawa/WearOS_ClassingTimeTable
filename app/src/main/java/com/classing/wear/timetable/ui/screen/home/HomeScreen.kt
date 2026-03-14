package com.classing.wear.timetable.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.R
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.domain.model.NextLessonHint
import com.classing.wear.timetable.domain.model.SyncState
import com.classing.wear.timetable.ui.PreviewSamples
import com.classing.wear.timetable.ui.component.EmptyState
import com.classing.wear.timetable.ui.component.ErrorState
import com.classing.wear.timetable.ui.component.LessonCard
import com.classing.wear.timetable.ui.component.LoadingState
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.state.HomeUiState
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenWeek: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onLessonClick: (Long) -> Unit,
    onRetrySync: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            HeaderSection(
                dateLabel = state.dateLabel,
                weekLabel = state.weekLabel,
                syncState = state.syncState,
            )
        }

        item {
            NavigationQuickActions(
                onOpenWeek = onOpenWeek,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings,
            )
        }

        item {
            NextLessonCard(state.nextLesson)
        }

        when {
            state.isLoading -> item { LoadingState(message = stringResource(R.string.home_loading_today_courses)) }
            state.errorMessage != null -> item {
                ErrorState(
                    detail = state.errorMessage,
                    onRetry = onRetrySync,
                )
            }
            state.todayLessons.isEmpty() -> item {
                EmptyState(
                    title = stringResource(R.string.home_empty_today_title),
                    subtitle = stringResource(R.string.home_empty_today_subtitle),
                )
            }
            else -> {
                items(state.todayLessons) { lesson ->
                    LessonCard(
                        lesson = lesson,
                        onClick = { onLessonClick(lesson.course.localId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(dateLabel: String, weekLabel: String, syncState: SyncState) {
    val syncLabel = when (syncState) {
        SyncState.Idle -> stringResource(R.string.home_sync_idle)
        SyncState.Syncing -> stringResource(R.string.home_sync_syncing)
        is SyncState.Success -> stringResource(R.string.home_sync_success)
        is SyncState.Failed -> stringResource(R.string.home_sync_failed)
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = dateLabel, style = MaterialTheme.typography.titleSmall)
        Text(
            text = "$weekLabel · $syncLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavigationQuickActions(
    onOpenWeek: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onOpenWeek, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.home_action_this_week))
        }
        Button(onClick = onOpenSearch, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.home_action_search))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.home_action_settings))
        }
    }
}

@Composable
private fun NextLessonCard(hint: NextLessonHint) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(R.string.home_next_lesson_title), style = MaterialTheme.typography.labelMedium)
            val lesson = hint.lesson
            if (lesson == null) {
                Text(text = stringResource(R.string.home_next_lesson_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(text = lesson.course.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${lesson.timeSlot.label} ${TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = TimeFormatters.formatCountdown(hint.countdown),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 220, heightDp = 220)
@Composable
private fun HomeScreenPreview() {
    ClassingTimetableTheme(useDynamicColor = false) {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                dateLabel = "3月13日 周五",
                weekLabel = "第3周",
                syncState = SyncState.Success(java.time.Instant.now()),
                nextLesson = NextLessonHint(PreviewSamples.sampleLesson(), java.time.Duration.ofMinutes(35)),
                todayLessons = listOf(PreviewSamples.sampleLesson()),
            ),
            onOpenWeek = {},
            onOpenSearch = {},
            onOpenSettings = {},
            onLessonClick = {},
            onRetrySync = {},
        )
    }
}
