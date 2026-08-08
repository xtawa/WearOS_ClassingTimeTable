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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.R
import com.classing.wear.timetable.core.time.TimeFormatters
import com.classing.wear.timetable.domain.model.LessonOccurrence
import com.classing.wear.timetable.domain.model.LessonStatus
import com.classing.wear.timetable.ui.theme.CourseEnglish
import com.classing.wear.timetable.ui.theme.CourseMath
import com.classing.wear.timetable.ui.theme.CourseOther
import com.classing.wear.timetable.ui.theme.CoursePhysics
import com.classing.wear.timetable.ui.theme.CoursePolitics
import com.classing.wear.timetable.ui.theme.CourseProgramming

@Composable
fun LessonCard(
    lesson: LessonOccurrence,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val statusColor = when (lesson.status) {
        LessonStatus.NOT_STARTED -> MaterialTheme.colorScheme.tertiary
        LessonStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
        LessonStatus.FINISHED -> MaterialTheme.colorScheme.outline
    }
    val statusLabel = when (lesson.status) {
        LessonStatus.NOT_STARTED -> stringResource(R.string.lesson_status_not_started)
        LessonStatus.IN_PROGRESS -> stringResource(R.string.lesson_status_in_progress)
        LessonStatus.FINISHED -> stringResource(R.string.lesson_status_finished)
    }
    val courseColor = colorLabelToColor(lesson.course.colorLabel, lesson.course.name)

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = courseColor.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 42.dp)
                    .background(courseColor, CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
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
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
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

fun colorLabelToColor(label: String, courseName: String = ""): Color {
    val semantic = "$label $courseName".lowercase()
    return when {
        semantic.contains("math") || semantic.contains("数学") || semantic.contains("代数") || semantic.contains("purple") -> CourseMath
        semantic.contains("english") || semantic.contains("英语") || semantic.contains("green") -> CourseEnglish
        semantic.contains("physics") || semantic.contains("物理") || semantic.contains("blue") || semantic.contains("teal") -> CoursePhysics
        semantic.contains("program") || semantic.contains("程序") || semantic.contains("编程") || semantic.contains("orange") -> CourseProgramming
        semantic.contains("politic") || semantic.contains("政治") || semantic.contains("pink") || semantic.contains("red") -> CoursePolitics
        else -> CourseOther
    }
}
