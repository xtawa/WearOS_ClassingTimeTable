package com.classing.wear.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.ui.theme.ClassingWearAmbientBlue
import com.classing.wear.timetable.ui.theme.ClassingWearAmbientViolet
import com.classing.wear.timetable.ui.theme.ClassingWearRadii
import com.classing.wear.timetable.ui.theme.ClassingWearSpacing

@Composable
fun ClassingWearBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ClassingWearAmbientViolet.copy(alpha = 0.34f),
                            Color.Transparent,
                        ),
                        radius = 360f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ClassingWearAmbientBlue.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = androidx.compose.ui.geometry.Offset(600f, 620f),
                        radius = 460f,
                    ),
                ),
        )
        content()
    }
}

@Composable
fun ClassingIsland(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        },
    )
    val shape = RoundedCornerShape(
        if (emphasized) ClassingWearRadii.large else ClassingWearRadii.medium,
    )
    val islandContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (emphasized) ClassingWearSpacing.xl else ClassingWearSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ClassingWearSpacing.sm),
            content = content,
        )
    }
    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            colors = colors,
            shape = shape,
            content = islandContent,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            shape = shape,
            content = islandContent,
        )
    }
}

@Composable
fun WearPageHeader(
    title: String,
    eyebrow: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClassingWearSpacing.xxs),
    ) {
        eyebrow?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
fun WearSectionLabel(
    title: String,
    trailing: String = "",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClassingWearSpacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing.isNotBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun WearStatusPill(
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ClassingWearRadii.pill))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = ClassingWearSpacing.sm, vertical = ClassingWearSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ClassingWearSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
