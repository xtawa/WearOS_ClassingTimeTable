package com.classing.wear.timetable.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
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
            NextLessonCard(hint = state.nextLesson, hasSchedule = state.hasSchedule)
        }

        if (!state.hasSchedule && !state.isLoading && state.errorMessage == null) {
            item {
                FirstRunGuideCard(
                    onRetrySync = onRetrySync,
                    onOpenSettings = onOpenSettings,
                )
            }
        }

        item {
            SectionCaption(title = stringResource(R.string.home_quick_actions_title))
        }
        item {
            QuickActionCard(
                title = stringResource(R.string.home_action_this_week),
                subtitle = stringResource(R.string.home_action_this_week_subtitle),
                onClick = onOpenWeek,
            )
        }
        item {
            QuickActionCard(
                title = stringResource(R.string.home_action_search),
                subtitle = stringResource(R.string.home_action_search_subtitle),
                onClick = onOpenSearch,
            )
        }
        item {
            QuickActionCard(
                title = stringResource(R.string.home_action_settings),
                subtitle = stringResource(R.string.home_action_settings_subtitle),
                onClick = onOpenSettings,
            )
        }

        if (state.hasSchedule) {
            item { SectionCaption(title = stringResource(R.string.home_today_section_title)) }
        }

        when {
            state.isLoading -> item { LoadingState(message = stringResource(R.string.home_loading_today_courses)) }
            state.errorMessage != null -> item {
                ErrorState(
                    detail = state.errorMessage,
                    onRetry = onRetrySync,
                )
            }
            !state.hasSchedule -> {
                // First-run state is already covered by onboarding card.
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

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = dateLabel, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "$weekLabel · $syncLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextLessonCard(hint: NextLessonHint, hasSchedule: Boolean) {
    val lesson = hint.lesson
    val countdown = TimeFormatters.formatCountdown(hint.countdown)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (lesson == null) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(R.string.home_next_lesson_title), style = MaterialTheme.typography.labelMedium)
            if (lesson == null) {
                Text(
                    text = if (hasSchedule) {
                        stringResource(R.string.home_next_lesson_empty)
                    } else {
                        stringResource(R.string.home_next_lesson_no_data)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(text = lesson.course.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${lesson.timeSlot.label} ${TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (countdown.isNotBlank()) {
                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstRunGuideCard(
    onRetrySync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_onboarding_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.home_onboarding_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRetrySync,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.home_action_sync_now))
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.home_action_settings))
            }
        }
    }
}

@Composable
private fun SectionCaption(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                hasSchedule = true,
                dateLabel = "Mar 13, Fri",
                weekLabel = "Week 3",
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

