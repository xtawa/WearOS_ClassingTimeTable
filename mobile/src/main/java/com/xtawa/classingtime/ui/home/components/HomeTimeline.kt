package com.xtawa.classingtime.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.ui.home.HomeCourseUiModel
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import java.time.format.DateTimeFormatter

private val TimelineClockFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun HomeTimeline(
    courses: List<HomeCourseUiModel>,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onCourseClick: (HomeCourseUiModel) -> Unit,
) {
    AnimatedVisibility(
        visible = visible && courses.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(tween(ClassingMotion.ContentReveal)) + expandVertically(),
        exit = fadeOut(tween(ClassingMotion.Exit)) + shrinkVertically(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs)) {
            Text(
                text = "Later today",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            courses.take(3).forEachIndexed { index, course ->
                TimelineCourseItem(
                    course = course,
                    emphasized = index == 0,
                    onClick = { onCourseClick(course) },
                )
            }
        }
    }
}

@Composable
private fun TimelineCourseItem(
    course: HomeCourseUiModel,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = ClassingSpacing.xs)
            .semantics { traversalIndex = if (emphasized) 0f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(if (emphasized) ClassingSpacing.sm else ClassingSpacing.xs)
                    .background(course.accent, CircleShape),
            )
            Spacer(
                modifier = Modifier
                    .width(2.dp)
                    .height(ClassingSpacing.xl)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Spacer(Modifier.width(ClassingSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (emphasized) 1f else 0.72f),
            )
            Text(
                text = course.location?.takeIf(String::isNotBlank) ?: "Room not provided",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = course.startTime.format(TimelineClockFormatter),
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) course.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
