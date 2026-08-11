package com.classing.wear.timetable.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.classing.wear.timetable.ui.component.CourseHeatmapGrid
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            HomeHeader(
                dateLabel = state.dateLabel,
                weekLabel = state.weekLabel,
                syncState = state.syncState,
            )
        }
        item {
            NextLessonHeroCard(
                hint = state.nextLesson,
                hasSchedule = state.hasSchedule,
                onOpenToday = onOpenWeek,
            )
        }
        item {
            QuickActionsRow(
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
                suffix = state.todayLessons.size.toString(),
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

            else -> items(state.todayLessons) { lesson ->
                LessonCard(
                    lesson = lesson,
                    onClick = { onLessonClick(lesson.course.localId) },
                )
            }
        }

        item {
            SectionCaption(
                title = stringResource(R.string.heatmap_title),
                suffix = "",
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.heatmapCells.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.heatmap_empty_title),
                            subtitle = stringResource(R.string.heatmap_empty_desc),
                        )
                    } else {
                        CourseHeatmapGrid(
                            cells = state.heatmapCells,
                            cellSize = 10.dp,
                            cellSpacing = 2.dp,
                            cellCornerRadius = 3.dp,
                        )
                        Text(
                            text = stringResource(R.string.heatmap_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    dateLabel: String,
    weekLabel: String,
    syncState: SyncState,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = stringResource(R.string.home_brand_wordmark),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.common_date_week_label, dateLabel, weekLabel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SyncStatusPill(syncState)
    }
}

@Composable
private fun SyncStatusPill(syncState: SyncState) {
    val label = when (syncState) {
        SyncState.Idle -> stringResource(R.string.home_sync_idle)
        SyncState.Syncing -> stringResource(R.string.home_sync_syncing)
        is SyncState.Success -> stringResource(R.string.home_sync_success)
        is SyncState.Failed -> stringResource(R.string.home_sync_failed)
    }
    val color = when (syncState) {
        SyncState.Idle -> MaterialTheme.colorScheme.outline
        SyncState.Syncing -> MaterialTheme.colorScheme.tertiary
        is SyncState.Success -> MaterialTheme.colorScheme.secondary
        is SyncState.Failed -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NextLessonHeroCard(
    hint: NextLessonHint,
    hasSchedule: Boolean,
    onOpenToday: () -> Unit,
) {
    val lesson = hint.lesson
    val countdown = TimeFormatters.formatCountdown(hint.countdown, lesson?.status)
    val nextLessonLabel = stringResource(R.string.home_next_lesson_title)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = buildString {
                    append(nextLessonLabel)
                    if (countdown.isNotBlank()) append(" · ").append(countdown)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            if (lesson == null) {
                Text(
                    text = if (hasSchedule) {
                        stringResource(R.string.home_next_lesson_empty)
                    } else {
                        stringResource(R.string.home_next_lesson_no_data)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = lesson.course.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(lesson.course.classroom, lesson.course.teacher)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = onOpenToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(stringResource(R.string.home_today_section_title))
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickActionChip(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.home_action_search),
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            onClick = onOpenSearch,
        )
        QuickActionChip(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.home_action_settings),
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun QuickActionChip(
    modifier: Modifier,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
            Text(
                text = label,
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelMedium,
            )
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
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_onboarding_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.home_onboarding_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRetrySync,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(text = stringResource(R.string.home_action_sync_now))
            }
            Card(
                onClick = onOpenSettings,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = RoundedCornerShape(999.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.home_action_settings))
                }
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (suffix.isNotBlank()) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
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
