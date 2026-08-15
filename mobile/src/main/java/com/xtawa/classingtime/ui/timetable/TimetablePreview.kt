package com.xtawa.classingtime.ui.timetable

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xtawa.classingtime.ui.theme.ClassingColors
import com.xtawa.classingtime.ui.theme.ClassingTheme
import java.time.LocalDate
import java.time.LocalTime

private val previewMonday = LocalDate.of(2026, 8, 17)

private val previewDays = (0L..6L).map { offset ->
    val date = previewMonday.plusDays(offset)
    TimetableDayUiModel(
        date = date,
        dayLabel = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() },
        dateLabel = date.dayOfMonth.toString(),
        courseCount = when (offset) {
            0L -> 4
            1L -> 3
            2L -> 2
            3L -> 4
            4L -> 1
            else -> 0
        },
        isToday = offset == 0L,
    )
}

private val previewCourses = listOf(
    TimetableCourseUiModel(
        id = "math",
        title = "Mathematics",
        teacher = "Dr. Lin",
        location = "Teaching Building A · 201",
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(8, 45),
        accent = ClassingColors.Mathematics,
        status = TimetableCourseStatus.Past,
    ),
    TimetableCourseUiModel(
        id = "physics",
        title = "Physics",
        teacher = "Ms. Chen",
        location = "Teaching Building A · 302",
        startTime = LocalTime.of(10, 20),
        endTime = LocalTime.of(11, 5),
        accent = ClassingColors.Physics,
        status = TimetableCourseStatus.Current,
    ),
    TimetableCourseUiModel(
        id = "english",
        title = "English Language",
        teacher = "Mr. Lee",
        location = "A205",
        startTime = LocalTime.of(15, 5),
        endTime = LocalTime.of(15, 50),
        accent = ClassingColors.Language,
        status = TimetableCourseStatus.Future,
    ),
)

private val timetablePreviewState = TimetableUiState(
    weekLabel = "Aug 17–23",
    selectedDate = previewMonday,
    selectedDateLabel = "Monday, August 17",
    days = previewDays,
    courses = previewCourses,
    hasImportedSchedule = true,
    scheduleChangeCount = 2,
)

@Composable
private fun TimetablePreviewFrame(
    state: TimetableUiState = timetablePreviewState,
    darkTheme: Boolean = false,
) {
    ClassingTheme(darkTheme = darkTheme) {
        TimetableContent(
            state = state,
            onSelectDate = {},
            onOpenCalendar = {},
            onOpenChanges = {},
            onOpenCourse = {},
            onLongPressCourse = {},
        )
    }
}

@Preview(name = "Timetable · Today", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TimetableTodayPreview() = TimetablePreviewFrame()

@Preview(name = "Timetable · Empty day", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TimetableEmptyPreview() = TimetablePreviewFrame(
    state = timetablePreviewState.copy(
        selectedDate = previewMonday.plusDays(5),
        selectedDateLabel = "Saturday, August 22",
        courses = emptyList(),
    ),
)

@Preview(name = "Timetable · Dark", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TimetableDarkPreview() = TimetablePreviewFrame(darkTheme = true)

@Preview(name = "Timetable · Large font", widthDp = 390, heightDp = 844, fontScale = 2f, showBackground = true)
@Composable
private fun TimetableLargeFontPreview() = TimetablePreviewFrame()

@Preview(name = "Timetable · Small device", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
private fun TimetableSmallDevicePreview() = TimetablePreviewFrame()
