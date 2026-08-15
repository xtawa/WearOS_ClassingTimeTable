package com.xtawa.classingtime.ui.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.xtawa.classingtime.ui.theme.ClassingColors
import com.xtawa.classingtime.ui.theme.ClassingTheme
import java.time.LocalDate
import java.time.LocalTime

private val PreviewDate: LocalDate = LocalDate.of(2026, 8, 17)

private fun previewCourse(
    id: String,
    title: String,
    start: LocalTime,
    end: LocalTime,
    location: String,
    accent: Color,
    date: LocalDate = PreviewDate,
) = HomeCourseUiModel(
    id = id,
    sourceLessonId = id,
    title = title,
    teacher = "林老师",
    location = location,
    date = date,
    startTime = start,
    endTime = end,
    accent = accent,
)

private val Physics = previewCourse(
    id = "physics",
    title = "物理",
    start = LocalTime.of(14, 10),
    end = LocalTime.of(14, 55),
    location = "教学楼 A · 302",
    accent = ClassingColors.Physics,
)
private val English = previewCourse(
    id = "english",
    title = "英语",
    start = LocalTime.of(15, 5),
    end = LocalTime.of(15, 50),
    location = "A205",
    accent = ClassingColors.Language,
)
private val Biology = previewCourse(
    id = "biology",
    title = "生物",
    start = LocalTime.of(16, 0),
    end = LocalTime.of(16, 45),
    location = "Lab 2",
    accent = ClassingColors.Biology,
)
private val TomorrowMath = previewCourse(
    id = "math-tomorrow",
    title = "数学",
    start = LocalTime.of(8, 0),
    end = LocalTime.of(8, 45),
    location = "B101",
    accent = ClassingColors.Mathematics,
    date = PreviewDate.plusDays(1),
)

private fun upcomingState() = HomeUiState(
    phase = HomePhase.Upcoming,
    now = LocalTime.of(13, 57),
    date = PreviewDate,
    primaryCourse = Physics,
    nextCourse = English,
    futureCourses = listOf(Physics, English, Biology),
    todayCourseCount = 5,
    countdownMinutes = 13,
)

private fun inClassState() = upcomingState().copy(
    phase = HomePhase.InClass,
    now = LocalTime.of(14, 23),
    primaryCourse = Physics,
    nextCourse = English,
    futureCourses = listOf(English, Biology),
    countdownMinutes = null,
    remainingMinutes = 32,
    classProgress = 0.29f,
)

private fun breakState() = upcomingState().copy(
    phase = HomePhase.Break,
    now = LocalTime.of(14, 55),
    primaryCourse = English,
    nextCourse = English,
    futureCourses = listOf(English, Biology),
    countdownMinutes = null,
    breakMinutes = 10,
)

private fun finishedState() = upcomingState().copy(
    phase = HomePhase.Finished,
    now = LocalTime.of(17, 10),
    primaryCourse = null,
    nextCourse = TomorrowMath,
    futureCourses = emptyList(),
    countdownMinutes = null,
)

private fun noClassesState() = upcomingState().copy(
    phase = HomePhase.NoClasses,
    now = LocalTime.of(10, 0),
    primaryCourse = null,
    nextCourse = TomorrowMath,
    futureCourses = emptyList(),
    todayCourseCount = 0,
    countdownMinutes = null,
)

@Composable
private fun HomePreviewSurface(state: HomeUiState, darkTheme: Boolean = false) {
    ClassingTheme(darkTheme = darkTheme) {
        HomeContent(
            state = state,
            assistantState = HomeAssistantUiState(),
            onAssistantFocusedChange = {},
            onQueryChange = {},
            onSubmitQuery = {},
            onCourseClick = {},
            onOpenTimetable = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Home · Upcoming", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
internal fun HomeUpcomingPreview() = HomePreviewSurface(upcomingState())

@Preview(name = "Home · In class", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
internal fun HomeInClassPreview() = HomePreviewSurface(inClassState())

@Preview(name = "Home · Break", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
internal fun HomeBreakPreview() = HomePreviewSurface(breakState())

@Preview(name = "Home · Finished", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
internal fun HomeFinishedPreview() = HomePreviewSurface(finishedState())

@Preview(name = "Home · No classes", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
internal fun HomeNoClassesPreview() = HomePreviewSurface(noClassesState())

@Preview(
    name = "Home · Dark",
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun HomeDarkPreview() = HomePreviewSurface(inClassState(), darkTheme = true)

@Preview(name = "Home · Large font", widthDp = 390, heightDp = 844, fontScale = 2f, showBackground = true)
@Composable
internal fun HomeLargeFontPreview() = HomePreviewSurface(upcomingState())

@Preview(name = "Home · Small device", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
internal fun HomeSmallDevicePreview() = HomePreviewSurface(breakState())
