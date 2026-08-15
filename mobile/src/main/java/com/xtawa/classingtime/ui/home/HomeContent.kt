package com.xtawa.classingtime.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.home.components.AmbientBackground
import com.xtawa.classingtime.ui.home.components.HomeAiPrompt
import com.xtawa.classingtime.ui.home.components.HomeCourseIsland
import com.xtawa.classingtime.ui.home.components.HomeTimeline
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun HomeContent(
    state: HomeUiState,
    assistantState: HomeAssistantUiState,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
    onAssistantFocusedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onCourseClick: (HomeCourseUiModel) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenInset = when {
            maxWidth <= 360.dp -> ClassingSpacing.compactScreenInset
            maxWidth >= 412.dp -> ClassingSpacing.spaciousScreenInset
            else -> ClassingSpacing.referenceScreenInset
        }
        val contextScale by animateFloatAsState(
            targetValue = if (assistantState.focused) 0.94f else 1f,
            animationSpec = if (motionEnabled) ClassingMotion.settledSpring() else tween(0),
            label = "home_context_scale",
        )
        val contextAlpha by animateFloatAsState(
            targetValue = if (assistantState.focused) 0.58f else 1f,
            animationSpec = tween(if (motionEnabled) ClassingMotion.LayoutReflow else 0),
            label = "home_context_alpha",
        )

        AmbientBackground(
            phase = state.phase,
            motionEnabled = motionEnabled,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = screenInset)
                .verticalScroll(rememberScrollState())
                .padding(bottom = ClassingSpacing.promptHeight + ClassingSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xl),
        ) {
            GreetingHeader(
                state = state,
                onOpenTimetable = onOpenTimetable,
                onOpenSettings = onOpenSettings,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = contextScale
                        scaleY = contextScale
                        alpha = contextAlpha
                    },
            ) {
                PrimaryHomeIsland(
                    state = state,
                    compact = assistantState.focused,
                    motionEnabled = motionEnabled,
                    onCourseClick = onCourseClick,
                )
            }

            HomeTimeline(
                courses = state.futureCourses,
                visible = !assistantState.focused && state.phase != HomePhase.Finished && state.phase != HomePhase.NoClasses,
                onCourseClick = onCourseClick,
            )
            Spacer(Modifier.height(ClassingSpacing.sm))
        }

        HomeAiPrompt(
            state = assistantState,
            suggestions = quickPrompts(state.phase),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 430.dp)
                .fillMaxWidth()
                .padding(horizontal = screenInset)
                .navigationBarsPadding()
                .padding(bottom = contentPadding.calculateBottomPadding() + ClassingSpacing.sm),
            onFocusedChange = onAssistantFocusedChange,
            onQueryChange = onQueryChange,
            onSubmit = onSubmitQuery,
        )
    }
}

@Composable
private fun GreetingHeader(
    state: HomeUiState,
    onOpenTimetable: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val greeting = when (state.now.hour) {
        in 5..11 -> stringResource(R.string.home_greeting_morning)
        in 12..17 -> stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_evening)
    }
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ClassingSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs)) {
            Text(
                text = state.date.format(dateFormatter),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs)) {
            IconButton(
                onClick = onOpenTimetable,
                modifier = Modifier.size(ClassingSpacing.minimumTouchTarget),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.home_open_timetable))
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(ClassingSpacing.minimumTouchTarget),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_open_settings))
            }
        }
    }
}

@Composable
private fun PrimaryHomeIsland(
    state: HomeUiState,
    compact: Boolean,
    motionEnabled: Boolean,
    onCourseClick: (HomeCourseUiModel) -> Unit,
) {
    AnimatedContent(
        targetState = when (state.phase) {
            HomePhase.Upcoming, HomePhase.InClass, HomePhase.Break -> "course"
            HomePhase.Finished -> "finished"
            HomePhase.NoClasses -> "no_classes"
        },
        transitionSpec = {
            (if (motionEnabled) {
                (fadeIn(tween(ClassingMotion.ContentReveal)) + scaleIn(initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(ClassingMotion.Exit)) + scaleOut(targetScale = 0.98f))
            } else {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            }).using(SizeTransform(clip = false))
        },
        label = "home_primary_island",
    ) { kind ->
        when (kind) {
            "course" -> HomeCourseIsland(
                state = state,
                compact = compact,
                onClick = { state.primaryCourse?.let(onCourseClick) },
            )
            "finished" -> FinishedDayIsland(state, onCourseClick)
            else -> NoClassIsland(state, onCourseClick)
        }
    }
}

@Composable
private fun FinishedDayIsland(state: HomeUiState, onCourseClick: (HomeCourseUiModel) -> Unit) {
    StateIsland {
        Text(
            text = stringResource(R.string.home_finished_title),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_classes_today, state.todayCourseCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.nextCourse?.let { next ->
            NextAcademicAnchor(next, onCourseClick)
        }
    }
}

@Composable
private fun NoClassIsland(state: HomeUiState, onCourseClick: (HomeCourseUiModel) -> Unit) {
    StateIsland {
        Text(
            text = stringResource(
                if (state.hasImportedSchedule) R.string.home_day_open else R.string.home_add_timetable,
            ),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = if (state.hasImportedSchedule) {
                stringResource(R.string.home_no_classes_today)
            } else {
                stringResource(R.string.home_import_first_schedule)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.nextCourse?.let { next -> NextAcademicAnchor(next, onCourseClick) }
    }
}

@Composable
private fun StateIsland(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ClassingRadii.large),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(ClassingSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.md),
            content = content,
        )
    }
}

@Composable
private fun NextAcademicAnchor(course: HomeCourseUiModel, onCourseClick: (HomeCourseUiModel) -> Unit) {
    Surface(
        onClick = { onCourseClick(course) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ClassingRadii.medium),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
    ) {
        Column(
            modifier = Modifier.padding(ClassingSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs),
        ) {
            Text(
                text = if (course.date == java.time.LocalDate.now().plusDays(1)) {
                    stringResource(R.string.home_tomorrow)
                } else {
                    course.date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${course.startTime}  ${course.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            course.location?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun quickPrompts(phase: HomePhase): List<String> = when (phase) {
    HomePhase.Upcoming -> listOf(
        stringResource(R.string.prompt_whats_next),
        stringResource(R.string.prompt_next_class_location),
        stringResource(R.string.prompt_afternoon),
    )
    HomePhase.InClass -> listOf(
        stringResource(R.string.prompt_after_this),
        stringResource(R.string.prompt_lunch),
        stringResource(R.string.prompt_today_homework),
    )
    HomePhase.Break -> listOf(
        stringResource(R.string.prompt_free_time),
        stringResource(R.string.prompt_next_class_location),
        stringResource(R.string.prompt_show_today),
    )
    HomePhase.Finished -> listOf(
        stringResource(R.string.prompt_tomorrow_morning),
        stringResource(R.string.prompt_homework_due),
        stringResource(R.string.prompt_lightest_day),
    )
    HomePhase.NoClasses -> listOf(
        stringResource(R.string.prompt_show_week),
        stringResource(R.string.prompt_biology),
        stringResource(R.string.prompt_prepare),
    )
}
