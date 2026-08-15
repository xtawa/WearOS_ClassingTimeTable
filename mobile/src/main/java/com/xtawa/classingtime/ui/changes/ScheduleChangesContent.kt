package com.xtawa.classingtime.ui.changes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing

private enum class ChangeFilter(val label: String) {
    All("All"),
    Moved("Moved"),
    Cancelled("Cancelled"),
    Added("Added"),
}

@Composable
internal fun ScheduleChangesContent(
    state: ScheduleChangesUiState,
    contentPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit,
    onOpenChange: (ScheduleChangeUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(ChangeFilter.All) }
    val filtered = when (filter) {
        ChangeFilter.All -> state.changes
        ChangeFilter.Moved -> state.changes.filter { it.type == ScheduleChangeType.Moved }
        ChangeFilter.Cancelled -> state.changes.filter { it.type == ScheduleChangeType.Cancelled }
        ChangeFilter.Added -> state.changes.filter { it.type == ScheduleChangeType.Added }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ClassingSpacing.xs, vertical = ClassingSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs),
            ) {
                Text(
                    text = "Schedule changes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "${state.changes.size} recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.size(ClassingSpacing.minimumTouchTarget))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = ClassingSpacing.referenceScreenInset),
            horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
        ) {
            items(ChangeFilter.entries) { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item.label) },
                )
            }
        }
        AnimatedContent(
            targetState = filtered,
            transitionSpec = {
                fadeIn(com.xtawa.classingtime.ui.theme.ClassingMotion.settledSpring())
                    .togetherWith(fadeOut())
                    .using(SizeTransform(clip = false))
            },
            contentKey = { list -> list.map { it.id } },
            label = "schedule_change_filter",
        ) { visibleChanges ->
            if (visibleChanges.isEmpty()) {
                EmptyChanges(filter = filter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ClassingSpacing.referenceScreenInset,
                        end = ClassingSpacing.referenceScreenInset,
                        top = ClassingSpacing.lg,
                        bottom = ClassingSpacing.xxxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
                ) {
                    items(visibleChanges, key = { it.id }) { change ->
                        ScheduleChangeCard(change = change, onClick = { onOpenChange(change) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChanges(filter: ChangeFilter) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ClassingSpacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (filter == ChangeFilter.All) "No schedule changes" else "No ${filter.label.lowercase()} changes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Your effective timetable matches the regular schedule.",
            modifier = Modifier.padding(top = ClassingSpacing.xs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleChangeCard(
    change: ScheduleChangeUiModel,
    onClick: () -> Unit,
) {
    val (icon, status) = when (change.type) {
        ScheduleChangeType.Moved -> Icons.Rounded.SwapVert to "MOVED"
        ScheduleChangeType.Cancelled -> Icons.Rounded.Cancel to "CANCELLED"
        ScheduleChangeType.Added -> Icons.Rounded.Add to "ADDED"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = change.type != ScheduleChangeType.Cancelled,
        shape = RoundedCornerShape(ClassingRadii.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(ClassingSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChangeIcon(icon = icon)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = change.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = change.dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            change.beforeLabel?.let { ComparisonRow(label = "Before", value = it) }
            change.nowLabel?.let { ComparisonRow(label = "Now", value = it) }
            change.contextLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChangeIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ComparisonRow(label: String, value: String) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    if (largeText) {
        Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (label == "Now") FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(0.28f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                modifier = Modifier.weight(0.72f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (label == "Now") FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
