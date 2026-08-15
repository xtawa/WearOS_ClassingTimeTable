package com.xtawa.classingtime.ui.course

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xtawa.classingtime.ui.theme.ClassingColors
import com.xtawa.classingtime.ui.theme.ClassingTheme
import java.time.LocalDate
import java.time.LocalTime

private val courseDetailPreviewState = CourseDetailUiState(
    id = "physics@2026-08-17",
    title = "物理",
    date = LocalDate.of(2026, 8, 17),
    dateLabel = "8 月 17 日，星期一",
    startTime = LocalTime.of(14, 10),
    endTime = LocalTime.of(14, 55),
    location = "教学楼 A · 302",
    teacher = "陈老师",
    note = "请带上实验讲义。",
    recurrenceLabel = "星期一 · 第 1–18 周",
    accent = ClassingColors.Physics,
    status = CourseDetailStatus.InClass,
    temporalLabel = "32 minutes remaining",
    progress = 0.29f,
)

@Composable
private fun CourseDetailPreviewFrame(darkTheme: Boolean = false) {
    ClassingTheme(darkTheme = darkTheme) {
        CourseDetailContent(
            state = courseDetailPreviewState,
            onBack = {},
            onEdit = {},
        )
    }
}

@Preview(name = "Course detail · Active", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CourseDetailActivePreview() = CourseDetailPreviewFrame()

@Preview(name = "Course detail · Dark", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CourseDetailDarkPreview() = CourseDetailPreviewFrame(darkTheme = true)

@Preview(name = "Course detail · Large font", widthDp = 390, heightDp = 844, fontScale = 2f, showBackground = true)
@Composable
private fun CourseDetailLargeFontPreview() = CourseDetailPreviewFrame()

@Preview(name = "Course detail · Small device", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
private fun CourseDetailSmallDevicePreview() = CourseDetailPreviewFrame()
