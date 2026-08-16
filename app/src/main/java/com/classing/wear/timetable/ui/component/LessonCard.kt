package com.classing.wear.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.ui.theme.ClassingWearMotion
import com.classing.wear.timetable.ui.theme.ClassingWearRadii
import com.classing.wear.timetable.ui.theme.ClassingWearSpacing

@Composable
fun LessonCard(
    lesson: LessonOccurrence,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val statusColor = when (lesson.status) {
        LessonStatus.NOT_STARTED -> MaterialTheme.colorScheme.tertiary
        LessonStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        LessonStatus.FINISHED -> MaterialTheme.colorScheme.outline
    }
    val courseColor = colorLabelToColor(lesson.course.colorLabel)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = ClassingWearMotion.settledSpring()),
        onClick = { onClick?.invoke() },
        shape = RoundedCornerShape(ClassingWearRadii.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClassingWearSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(ClassingWearSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 46.dp)
                    .background(courseColor, CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClassingWearSpacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = lesson.course.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape),
                    )
                }
                Text(
                    text = TimeFormatters.formatSlotLabelAndTime(
                        label = lesson.timeSlot.label,
                        start = lesson.startAt,
                        end = lesson.endAt,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${lesson.course.teacher} · ${lesson.course.classroom}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

fun colorLabelToColor(label: String): Color {
    return when (label.lowercase()) {
        "red" -> Color(0xFFDF8B94)
        "blue" -> Color(0xFF91A7F2)
        "orange" -> Color(0xFFDDA765)
        "green" -> Color(0xFF78BC99)
        "teal" -> Color(0xFF6DB7B2)
        "purple" -> Color(0xFFA894E6)
        else -> Color(0xFF91A0A8)
    }
}
