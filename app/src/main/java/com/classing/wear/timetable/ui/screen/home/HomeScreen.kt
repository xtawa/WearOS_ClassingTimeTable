package com.classing.wear.timetable.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import java.time.Instant

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BrandHeader() }
        item {
            HeaderInfoCard(
                dateLabel = state.dateLabel,
                weekLabel = state.weekLabel,
                syncState = state.syncState,
            )
        }
        item {
            NextLessonHeroCard(
                hint = state.nextLesson,
                hasSchedule = state.hasSchedule,
            )
        }
        item {
            QuickActionsRow(
                onOpenWeek = onOpenWeek,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings,
            )
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
            SectionCaption(
                title = stringResource(R.string.home_today_section_title),
                suffix = "${state.todayLessons.size}",
            )
        }

        when {
            state.isLoading -> item {
                LoadingState(message = stringResource(R.string.home_loading_today_courses))
            }

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

        item {
            Button(
                onClick = onRetrySync,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(stringResource(R.string.home_action_sync_now))
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.home_brand_wordmark),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HeaderInfoCard(
    dateLabel: String,
    weekLabel: String,
    syncState: SyncState,
) {
    val syncLabel = when (syncState) {
        SyncState.Idle -> stringResource(R.string.home_sync_idle)
        SyncState.Syncing -> stringResource(R.string.home_sync_syncing)
        is SyncState.Success -> stringResource(R.string.home_sync_success)
        is SyncState.Failed -> stringResource(R.string.home_sync_failed)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.common_date_week_label, dateLabel, weekLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = when (syncState) {
                                SyncState.Idle -> MaterialTheme.colorScheme.outline
                                SyncState.Syncing -> MaterialTheme.colorScheme.tertiary
                                is SyncState.Success -> MaterialTheme.colorScheme.primary
                                is SyncState.Failed -> MaterialTheme.colorScheme.error
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = syncLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun NextLessonHeroCard(hint: NextLessonHint, hasSchedule: Boolean) {
    val lesson = hint.lesson
    val countdown = TimeFormatters.formatCountdown(hint.countdown)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_next_lesson_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
                Text(
                    text = lesson.course.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Text(
                    text = TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (countdown.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onOpenWeek: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickActionIcon(
            icon = {
                Text(
                    text = stringResource(R.string.home_action_week_short),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = onOpenWeek,
        )
        QuickActionIcon(
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            onClick = onOpenSearch,
        )
        QuickActionIcon(
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun QuickActionIcon(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Composable
private fun FirstRunGuideCard(
    onRetrySync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
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
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(text = stringResource(R.string.home_action_sync_now))
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(text = stringResource(R.string.home_action_settings))
            }
        }
    }
}

@Composable
private fun SectionCaption(title: String, suffix: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = suffix,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
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
                dateLabel = "04-17 Thu",
                weekLabel = "Week 8",
                syncState = SyncState.Success(Instant.now()),
                nextLesson = NextLessonHint(PreviewSamples.sampleLesson(), java.time.Duration.ofMinutes(14)),
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
