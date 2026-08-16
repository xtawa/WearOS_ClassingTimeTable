package com.classing.wear.timetable.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.wear.timetable.R
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.domain.model.NextLessonHint
import com.classing.wear.timetable.domain.model.SyncState
import com.classing.wear.timetable.ui.PreviewSamples
import com.classing.wear.timetable.ui.component.ClassingIsland
import com.classing.wear.timetable.ui.component.ClassingWearBackground
import com.classing.wear.timetable.ui.component.CourseHeatmapGrid
import com.classing.wear.timetable.ui.component.EmptyState
import com.classing.wear.timetable.ui.component.ErrorState
import com.classing.wear.timetable.ui.component.LessonCard
import com.classing.wear.timetable.ui.component.LoadingState
import com.classing.wear.timetable.ui.component.WearPageHeader
import com.classing.wear.timetable.ui.component.WearSectionLabel
import com.classing.wear.timetable.ui.component.WearStatusPill
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.state.HomeUiState
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme
import com.classing.wear.timetable.ui.theme.ClassingWearMotion
import com.classing.wear.timetable.ui.theme.ClassingWearRadii
import com.classing.wear.timetable.ui.theme.ClassingWearSpacing
import com.classing.wear.timetable.ui.theme.ClassingWearWarning
import java.time.Duration
import java.time.Instant

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenWeek: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAskAi: () -> Unit,
    onOpenSettings: () -> Unit,
    onLessonClick: (Long) -> Unit,
    onRetrySync: () -> Unit,
) {
    ClassingWearBackground {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberScalingLazyListState(),
            contentPadding = screenPadding(),
            verticalArrangement = Arrangement.spacedBy(ClassingWearSpacing.md),
        ) {
            item {
                HomeHeader(
                    dateLabel = state.dateLabel,
                    weekLabel = state.weekLabel,
                    syncState = state.syncState,
                )
            }
            item {
                PrimaryCourseIsland(
                    hint = state.nextLesson,
                    hasSchedule = state.hasSchedule,
                    onLessonClick = onLessonClick,
                )
            }
            if (state.showAiOnHome) {
                item { AskAiEntryIsland(onClick = onOpenAskAi) }
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
                WearSectionLabel(
                    title = stringResource(R.string.home_today_section_title),
                    trailing = state.todayLessons.size.toString(),
                )
            }
            when {
                state.isLoading -> item {
                    LoadingState(message = stringResource(R.string.home_loading_today_courses))
                }
                state.errorMessage != null -> item {
                    ErrorState(detail = state.errorMessage, onRetry = onRetrySync)
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

            item { WearSectionLabel(title = stringResource(R.string.heatmap_title)) }
            item {
                ClassingIsland {
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
private fun AskAiEntryIsland(onClick: () -> Unit) {
    val actionLabel = stringResource(R.string.home_action_ask_ai)
    ClassingIsland(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = actionLabel },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClassingWearSpacing.xxs),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.ask_ai_welcome_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WearStatusPill(
                label = stringResource(R.string.home_action_ask_ai_short),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HomeHeader(
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
    val syncColor = when (syncState) {
        SyncState.Idle -> MaterialTheme.colorScheme.outline
        SyncState.Syncing -> ClassingWearWarning
        is SyncState.Success -> MaterialTheme.colorScheme.tertiary
        is SyncState.Failed -> MaterialTheme.colorScheme.error
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClassingWearSpacing.sm),
    ) {
        WearPageHeader(
            title = stringResource(R.string.common_date_week_label, dateLabel, weekLabel),
            eyebrow = stringResource(R.string.home_brand_wordmark),
        )
        WearStatusPill(label = syncLabel, color = syncColor)
    }
}

@Composable
private fun PrimaryCourseIsland(
    hint: NextLessonHint,
    hasSchedule: Boolean,
    onLessonClick: (Long) -> Unit,
) {
    val lesson = hint.lesson
    val key = "${lesson?.course?.localId}:${lesson?.status}:${lesson?.startAt}"
    AnimatedContent(
        targetState = key,
        transitionSpec = {
            ((fadeIn(tween(ClassingWearMotion.ContentReveal)) + scaleIn(initialScale = 0.94f)) togetherWith
                (fadeOut(tween(ClassingWearMotion.Exit)) + scaleOut(targetScale = 0.98f)))
                .using(SizeTransform(clip = false))
        },
        label = "wear_primary_course",
    ) {
        ClassingIsland(
            emphasized = true,
            onClick = lesson?.let { { onLessonClick(it.course.localId) } },
        ) {
            if (lesson == null) {
                WearStatusPill(
                    label = if (hasSchedule) {
                        stringResource(R.string.home_status_finished)
                    } else {
                        stringResource(R.string.home_status_setup)
                    },
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = if (hasSchedule) {
                        stringResource(R.string.home_day_clear_title)
                    } else {
                        stringResource(R.string.home_next_lesson_no_data)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = if (hasSchedule) {
                        stringResource(R.string.home_day_clear_subtitle)
                    } else {
                        stringResource(R.string.home_onboarding_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@ClassingIsland
            }

            val inClass = lesson.status == LessonStatus.IN_PROGRESS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WearStatusPill(
                    label = stringResource(
                        if (inClass) R.string.home_status_in_class else R.string.home_status_up_next,
                    ),
                    color = if (inClass) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = lesson.course.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            val meta = listOf(lesson.course.classroom, lesson.course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val countdown = hint.countdown
            if (countdown != null) {
                Text(
                    text = if (inClass) {
                        stringResource(
                            R.string.home_minutes_remaining,
                            countdown.toMinutes().coerceAtLeast(0),
                        )
                    } else {
                        TimeFormatters.formatCountdown(countdown, lesson.status)
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = if (inClass) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
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
        QuickAction(
            label = stringResource(R.string.home_action_this_week),
            icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            onClick = onOpenWeek,
        )
        QuickAction(
            label = stringResource(R.string.home_action_search),
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            onClick = onOpenSearch,
        )
        QuickAction(
            label = stringResource(R.string.home_action_settings),
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        ),
    ) {
        Box(
            modifier = Modifier.size(ClassingWearSpacing.minimumTouchTarget),
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
    ClassingIsland {
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
            shape = RoundedCornerShape(ClassingWearRadii.pill),
        ) {
            Text(stringResource(R.string.home_action_sync_now))
        }
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(ClassingWearRadii.pill),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(stringResource(R.string.home_action_settings))
        }
    }
}

@Preview(showBackground = true, widthDp = 220, heightDp = 220)
@Composable
private fun HomeUpcomingPreview() {
    ClassingTimetableTheme(useDynamicColor = false) {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                hasSchedule = true,
                dateLabel = "4月17日 周四",
                weekLabel = "第8周",
                syncState = SyncState.Success(Instant.now()),
                nextLesson = NextLessonHint(PreviewSamples.sampleLesson(), Duration.ofMinutes(14)),
                todayLessons = listOf(PreviewSamples.sampleLesson()),
            ),
            onOpenWeek = {},
            onOpenSearch = {},
            onOpenAskAi = {},
            onOpenSettings = {},
            onLessonClick = {},
            onRetrySync = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 192, heightDp = 192)
@Composable
private fun HomeNoClassesPreview() {
    ClassingTimetableTheme(useDynamicColor = false) {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                hasSchedule = true,
                dateLabel = "4月17日 周四",
                weekLabel = "第8周",
                syncState = SyncState.Idle,
            ),
            onOpenWeek = {},
            onOpenSearch = {},
            onOpenAskAi = {},
            onOpenSettings = {},
            onLessonClick = {},
            onRetrySync = {},
        )
    }
}
