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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing

private enum class ChangeFilter(@StringRes val labelRes: Int) {
    All(R.string.schedule_change_filter_all),
    Moved(R.string.schedule_change_filter_moved),
    Cancelled(R.string.schedule_change_filter_cancelled),
    Added(R.string.schedule_change_filter_added),
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
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.assistant_back),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.schedule_change_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.schedule_change_recorded, state.changes.size),
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
                    label = { Text(stringResource(item.labelRes)) },
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
    val filterLabel = stringResource(filter.labelRes)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ClassingSpacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (filter == ChangeFilter.All) {
                stringResource(R.string.schedule_change_empty_all)
            } else {
                stringResource(R.string.schedule_change_empty_filtered, filterLabel)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.schedule_change_empty_hint),
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
        ScheduleChangeType.Moved -> Icons.Rounded.SwapVert to R.string.schedule_change_moved
        ScheduleChangeType.Cancelled -> Icons.Rounded.Cancel to R.string.schedule_change_cancelled
        ScheduleChangeType.Added -> Icons.Rounded.Add to R.string.schedule_change_added
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
                        text = stringResource(status),
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
            change.beforeLabel?.let {
                ComparisonRow(label = stringResource(R.string.schedule_change_before), value = it)
            }
            change.nowLabel?.let {
                ComparisonRow(
                    label = stringResource(R.string.schedule_change_now),
                    value = it,
                    emphasized = true,
                )
            }
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
private fun ComparisonRow(label: String, value: String, emphasized: Boolean = false) {
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
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
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
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
