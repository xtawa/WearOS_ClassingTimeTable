package com.xtawa.classingtime.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.ui.home.HomeCourseUiModel
import com.xtawa.classingtime.ui.home.HomePhase
import com.xtawa.classingtime.ui.home.HomeUiState
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import com.xtawa.classingtime.ui.theme.ClassingTimeHeroStyle
import java.time.format.DateTimeFormatter

private val HomeClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun HomeCourseIsland(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val course = state.primaryCourse ?: return
    val tintAlpha = when (state.phase) {
        HomePhase.InClass -> 0.15f
        HomePhase.Break -> 0.09f
        else -> 0.08f
    }
    val surfaceColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.78f else 0.90f),
        animationSpec = tween(ClassingMotion.LayoutReflow),
        label = "home_course_surface",
    )
    val animatedAccent by animateColorAsState(
        targetValue = course.accent,
        animationSpec = tween(ClassingMotion.SharedMorph),
        label = "home_course_accent",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = ClassingMotion.settledSpring())
            .clip(RoundedCornerShape(ClassingRadii.large))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = courseStateDescription(state)
            },
        shape = RoundedCornerShape(ClassingRadii.large),
        color = surfaceColor,
        shadowElevation = if (compact) 0.dp else 8.dp,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(animatedAccent.copy(alpha = tintAlpha)),
            )
            Column(
                modifier = Modifier.padding(if (compact) ClassingSpacing.md else ClassingSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
            ) {
                CourseIdentity(course = course, phase = state.phase, accent = animatedAccent)
                if (compact) {
                    Text(
                        text = course.location?.takeIf(String::isNotBlank) ?: "Room not provided",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AnimatedContent(
                        targetState = state.phase,
                        transitionSpec = {
                            fadeIn(tween(ClassingMotion.ContentReveal)) togetherWith
                                fadeOut(tween(ClassingMotion.Exit))
                        },
                        label = "home_course_phase_content",
                    ) { phase ->
                        when (phase) {
                            HomePhase.Upcoming -> UpcomingCourseContent(state)
                            HomePhase.InClass -> InClassCourseContent(state)
                            HomePhase.Break -> BreakCourseContent(state)
                            HomePhase.Finished,
                            HomePhase.NoClasses -> Unit
                        }
                    }
                    CourseMetadata(course)
                }
            }
        }
    }
}

@Composable
private fun CourseIdentity(course: HomeCourseUiModel, phase: HomePhase, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(ClassingSpacing.sm)
                    .background(accent, CircleShape),
            )
            Spacer(Modifier.width(ClassingSpacing.xs))
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    fadeIn(tween(ClassingMotion.ContentReveal)) togetherWith fadeOut(tween(ClassingMotion.Exit))
                },
                label = "home_course_status",
            ) { targetPhase ->
                Text(
                    text = when (targetPhase) {
                        HomePhase.Upcoming -> "NEXT CLASS"
                        HomePhase.InClass -> "IN CLASS"
                        HomePhase.Break -> "NEXT"
                        else -> "CLASS"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = course.startTime.format(HomeClockFormatter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AnimatedContent(
        targetState = course,
        contentKey = { it.id },
        transitionSpec = {
            fadeIn(tween(ClassingMotion.ContentReveal)) togetherWith fadeOut(tween(ClassingMotion.Exit))
        },
        label = "home_course_identity",
    ) { targetCourse ->
        Text(
            text = targetCourse.title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UpcomingCourseContent(state: HomeUiState) {
    Text(
        text = "${state.countdownMinutes ?: 1} min",
        style = ClassingTimeHeroStyle,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "until class starts",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TemporalLine(progress = countdownProgress(state.countdownMinutes))
}

@Composable
private fun InClassCourseContent(state: HomeUiState) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "${state.remainingMinutes ?: 1} min",
            style = ClassingTimeHeroStyle,
        )
        Spacer(Modifier.width(ClassingSpacing.xs))
        Text(
            text = "remaining",
            modifier = Modifier.padding(bottom = ClassingSpacing.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ClassProgress(progress = state.classProgress)
}

@Composable
private fun BreakCourseContent(state: HomeUiState) {
    val minutes = state.breakMinutes ?: 1
    Text(
        text = if (minutes > 30) "Free until" else "Break",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = "$minutes min", style = ClassingTimeHeroStyle)
        Spacer(Modifier.width(ClassingSpacing.xs))
        if (minutes <= 2) {
            Text(
                text = "Head to class",
                modifier = Modifier.padding(bottom = ClassingSpacing.xs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CourseMetadata(course: HomeCourseUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs)) {
        Text(
            text = "${course.startTime.format(HomeClockFormatter)}–${course.endTime.format(HomeClockFormatter)}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = course.location?.takeIf(String::isNotBlank) ?: "Room not provided",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        course.teacher?.takeIf(String::isNotBlank)?.let { teacher ->
            Text(
                text = teacher,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ClassProgress(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = ClassingMotion.settledSpring(),
        label = "class_progress",
    )
    Column(
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun TemporalLine(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.68f), CircleShape),
        )
    }
}

private fun countdownProgress(minutes: Long?): Float {
    val remaining = (minutes ?: 30L).coerceIn(0L, 30L)
    return 1f - (remaining / 30f)
}

private fun courseStateDescription(state: HomeUiState): String {
    val course = state.primaryCourse ?: return ""
    val relation = when (state.phase) {
        HomePhase.Upcoming -> "starts in ${state.countdownMinutes} minutes"
        HomePhase.InClass -> "in class, ${state.remainingMinutes} minutes remaining"
        HomePhase.Break -> "next class, starts in ${state.breakMinutes} minutes"
        else -> ""
    }
    val room = course.location?.takeIf(String::isNotBlank) ?: "room not provided"
    return "${course.title}, $relation, $room"
}
