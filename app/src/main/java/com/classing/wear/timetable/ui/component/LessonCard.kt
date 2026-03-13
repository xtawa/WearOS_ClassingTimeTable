package com.classing.wear.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                text = "${lesson.timeSlot.label} ${TimeFormatters.formatTimeRange(lesson.startAt, lesson.endAt)}",
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
    Spacer(modifier = Modifier.height(6.dp))
}

fun colorLabelToColor(label: String): Color {
    return when (label.lowercase()) {
        "red" -> Color(0xFFE53935)
        "blue" -> Color(0xFF1E88E5)
        "orange" -> Color(0xFFFB8C00)
        "green" -> Color(0xFF43A047)
        "teal" -> Color(0xFF00897B)
        else -> Color(0xFF757575)
    }
}
