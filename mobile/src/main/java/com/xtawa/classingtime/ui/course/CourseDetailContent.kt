package com.xtawa.classingtime.ui.course

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import java.time.Duration
import java.time.format.DateTimeFormatter

private val detailClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun CourseDetailContent(
    state: CourseDetailUiState,
    contentPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsVisible by remember(state.id) { mutableStateOf(false) }
    LaunchedEffect(state.id) { detailsVisible = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            state.accent.copy(alpha = 0.24f),
                            state.accent.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = ClassingSpacing.referenceScreenInset,
                end = ClassingSpacing.referenceScreenInset,
                top = ClassingSpacing.sm,
                bottom = ClassingSpacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = state.dateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(modifier = Modifier.size(ClassingSpacing.minimumTouchTarget))
                }
            }
            item { CourseHero(state = state) }
            item {
                AnimatedVisibility(
                    visible = detailsVisible,
                    enter = fadeIn(tween(ClassingMotion.ContentReveal, delayMillis = ClassingMotion.Stagger)) +
                        slideInVertically(
                            animationSpec = tween(ClassingMotion.ContentReveal, delayMillis = ClassingMotion.Stagger),
                            initialOffsetY = { it / 8 },
                        ),
                ) {
                    CourseFacts(state = state)
                }
            }
            if (!state.note.isNullOrBlank()) {
                item {
                    AnimatedVisibility(
                        visible = detailsVisible,
                        enter = fadeIn(
                            tween(
                                ClassingMotion.ContentReveal,
                                delayMillis = ClassingMotion.Stagger * 2,
                            ),
                        ),
                    ) {
                        DetailIsland(
                            icon = Icons.Rounded.Notes,
                            label = "Notes",
                            value = state.note,
                        )
                    }
                }
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                    shape = RoundedCornerShape(ClassingRadii.pill),
                    contentPadding = PaddingValues(vertical = ClassingSpacing.md),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Text(
                        text = "Edit course",
                        modifier = Modifier.padding(start = ClassingSpacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseHero(state: CourseDetailUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ClassingRadii.extraLarge),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(ClassingSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(state.accent, CircleShape),
                )
                Text(
                    text = when (state.status) {
                        CourseDetailStatus.Upcoming -> "UPCOMING"
                        CourseDetailStatus.InClass -> "IN CLASS"
                        CourseDetailStatus.Finished -> "FINISHED"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = state.accent,
                )
            }
            Text(
                text = state.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "${state.startTime.format(detailClockFormatter)}–${state.endTime.format(detailClockFormatter)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = state.temporalLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .semantics {
                            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progress, 0f..1f)
                        },
                    color = state.accent,
                    trackColor = state.accent.copy(alpha = 0.16f),
                )
            }
            if (!state.location.isNullOrBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.location,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseFacts(state: CourseDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm)) {
        DetailIsland(
            icon = Icons.Rounded.Person,
            label = "Teacher",
            value = state.teacher ?: "Not provided",
        )
        DetailIsland(
            icon = Icons.Rounded.Repeat,
            label = "Schedule",
            value = state.recurrenceLabel,
        )
        DetailIsland(
            icon = Icons.Rounded.Notes,
            label = "Duration",
            value = "${Duration.between(state.startTime, state.endTime).toMinutes()} minutes",
        )
    }
}

@Composable
private fun DetailIsland(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ClassingRadii.medium),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(ClassingSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
