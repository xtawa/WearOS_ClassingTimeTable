package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.xtawa.classingtime.ui.home.HomeScreen
import java.time.LocalDate

/**
 * Compatibility route between the existing production state holder and the
 * executable Home design. Preview and production both render HomeContent.
 */
@Composable
internal fun DashboardLayer(
    contentPadding: PaddingValues,
    lessons: List<LessonUi>,
    lessonsForDate: (LocalDate) -> List<LessonUi>,
    onOpenAskAi: (String) -> Unit,
    onOpenCourse: (LessonUi, LocalDate) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    HomeScreen(
        contentPadding = contentPadding,
        lessonsForDate = lessonsForDate,
        hasImportedSchedule = lessons.isNotEmpty(),
        onOpenAskClassing = onOpenAskAi,
        onCourseClick = { course ->
            lessonsForDate(course.date)
                .firstOrNull { it.id == course.sourceLessonId }
                ?.let { onOpenCourse(it, course.date) }
        },
        onOpenTimetable = onOpenTimetable,
        onOpenSettings = onOpenSettings,
    )
}
