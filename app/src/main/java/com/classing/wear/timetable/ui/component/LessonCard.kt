package com.classing.wear.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.LessonStatus

@Composable
fun LessonCard(
    lesson: LessonOccurrence,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val courseColor = colorLabelToColor(lesson.course.colorLabel)
    val statusColor = when (lesson.status) {
        LessonStatus.NOT_STARTED -> courseColor
        LessonStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
        LessonStatus.FINISHED -> MaterialTheme.colorScheme.outline
    }
    val containerColor = when (lesson.status) {
        LessonStatus.FINISHED -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> courseColor.copy(alpha = 0.16f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = 44.dp)
                    .background(statusColor, CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = lesson.course.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(7.dp)
                            .background(statusColor, CircleShape),
                    )
                }
                Text(
                    text = TimeFormatters.formatSlotLabelAndTime(
                        label = lesson.timeSlot.label,
                        start = lesson.startAt,
                        end = lesson.endAt,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            }
        }
    }
}

fun colorLabelToColor(label: String): Color {
    return when (label.lowercase()) {
        "red" -> Color(0xFFFF8FAE)
        "blue" -> Color(0xFF8C9BFF)
        "orange" -> Color(0xFFFFB454)
        "green" -> Color(0xFF66D19E)
        "teal" -> Color(0xFF4FD8C4)
        else -> Color(0xFFB79BFF)
    }
}
