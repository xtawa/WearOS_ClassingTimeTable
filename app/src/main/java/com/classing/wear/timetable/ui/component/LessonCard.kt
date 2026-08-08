package com.classing.wear.timetable.ui.component

import androidx.compose.foundation.BorderStroke
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
import com.classing.wear.timetable.ui.theme.COURSE_BLOCK_ALPHA
import com.classing.wear.timetable.ui.theme.CourseEnglish
import com.classing.wear.timetable.ui.theme.CourseLinearAlgebra
import com.classing.wear.timetable.ui.theme.CourseMath
import com.classing.wear.timetable.ui.theme.CourseOther
import com.classing.wear.timetable.ui.theme.CoursePhysics
import com.classing.wear.timetable.ui.theme.CoursePolitics
import com.classing.wear.timetable.ui.theme.CourseProgramming
import com.classing.wear.timetable.ui.theme.CourseSports
import com.classing.wear.timetable.ui.theme.IndigoSuccess

@Composable
fun LessonCard(
    lesson: LessonOccurrence,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val isInProgress = lesson.status == LessonStatus.IN_PROGRESS
    // Secondary (cyan) is reserved for sync states, so "in progress" uses the
    // success token instead (design spec §2.2).
    val statusColor = when (lesson.status) {
        LessonStatus.NOT_STARTED -> MaterialTheme.colorScheme.tertiary
        LessonStatus.IN_PROGRESS -> IndigoSuccess
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = courseColor.copy(alpha = COURSE_BLOCK_ALPHA)),
        border = if (isInProgress) BorderStroke(2.dp, courseColor) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
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

/**
 * Resolves the color of a course block.
 *
 * The user-assigned [label] always wins (design spec §5); course-name keyword
 * inference is only a fallback for courses that have no explicit color yet.
 * Keep this mapping in sync with `courseColorFor` in the mobile module (§43).
 */
fun colorLabelToColor(label: String, courseName: String = ""): Color {
    explicitCourseColor(label)?.let { return it }
    return inferCourseColor(courseName)
}

private fun explicitCourseColor(label: String): Color? = when (label.trim().lowercase()) {
    "purple", "violet", "indigo" -> CourseMath
    "green", "emerald" -> CourseEnglish
    "blue", "sky", "teal", "cyan" -> CoursePhysics
    "orange", "amber" -> CourseProgramming
    "pink", "rose", "red" -> CoursePolitics
    "yellow", "gold" -> CourseLinearAlgebra
    "lightblue", "light_blue", "light-blue" -> CourseSports
    "gray", "grey", "slate" -> CourseOther
    else -> null
}

private fun inferCourseColor(courseName: String): Color {
    val name = courseName.lowercase()
    return when {
        name.contains("线性代数") || name.contains("代数") || name.contains("algebra") -> CourseLinearAlgebra
        name.contains("数学") || name.contains("math") -> CourseMath
        name.contains("英语") || name.contains("english") -> CourseEnglish
        name.contains("物理") || name.contains("physics") -> CoursePhysics
        name.contains("程序") || name.contains("编程") || name.contains("program") -> CourseProgramming
        name.contains("政治") || name.contains("politic") -> CoursePolitics
        name.contains("体育") || name.contains("sport") -> CourseSports
        else -> CourseOther
    }
}
