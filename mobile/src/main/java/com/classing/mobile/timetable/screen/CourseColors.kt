package com.xtawa.classingtime.screen

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/*
 * Shared design tokens for the mobile timetable surfaces.
 *
 * The course palette below must stay byte-identical to the Wear palette in
 * app/.../ui/theme/Color.kt so that the same course renders in the same color
 * on phone and watch (design spec §5 and §43).
 */

internal val CourseMathColor = Color(0xFF7C79F7)
internal val CourseEnglishColor = Color(0xFF22C55E)
internal val CoursePhysicsColor = Color(0xFF38BDF8)
internal val CourseProgrammingColor = Color(0xFFF59E0B)
internal val CoursePoliticsColor = Color(0xFFF472B6)
internal val CourseLinearAlgebraColor = Color(0xFFFACC15)
internal val CourseSportsColor = Color(0xFF60A5FA)
internal val CourseOtherColor = Color(0xFF64748B)

/** Course block emphasis (design spec §14): fill 18%, border 32%. */
internal const val COURSE_BLOCK_ALPHA = 0.18f
internal const val COURSE_BLOCK_BORDER_ALPHA = 0.32f

/**
 * Resolves the color of a course block.
 *
 * A user-assigned [colorLabel] always wins; keyword inference from [title] is
 * only a fallback for courses without an explicit color (design spec §5).
 */
internal fun courseColorFor(title: String, colorLabel: String? = null): Color {
    explicitCourseColor(colorLabel)?.let { return it }
    val normalized = title.lowercase()
    return when {
        normalized.contains("线性代数") || normalized.contains("代数") || normalized.contains("algebra") -> CourseLinearAlgebraColor
        normalized.contains("数学") || normalized.contains("math") -> CourseMathColor
        normalized.contains("英语") || normalized.contains("english") -> CourseEnglishColor
        normalized.contains("物理") || normalized.contains("physics") -> CoursePhysicsColor
        normalized.contains("程序") || normalized.contains("编程") || normalized.contains("program") -> CourseProgrammingColor
        normalized.contains("政治") || normalized.contains("politic") -> CoursePoliticsColor
        normalized.contains("体育") || normalized.contains("sport") -> CourseSportsColor
        else -> CourseOtherColor
    }
}

private fun explicitCourseColor(colorLabel: String?): Color? =
    when (colorLabel?.trim()?.lowercase()) {
        null, "" -> null
        "purple", "violet", "indigo" -> CourseMathColor
        "green", "emerald" -> CourseEnglishColor
        "blue", "sky", "teal", "cyan" -> CoursePhysicsColor
        "orange", "amber" -> CourseProgrammingColor
        "pink", "rose", "red" -> CoursePoliticsColor
        "yellow", "gold" -> CourseLinearAlgebraColor
        "lightblue", "light_blue", "light-blue" -> CourseSportsColor
        "gray", "grey", "slate" -> CourseOtherColor
        else -> null
    }

/**
 * Localized date header used by every board header (design spec §45).
 * Never render raw ISO dates or hardcoded English patterns in the UI.
 */
internal fun formatDateHeader(date: LocalDate): String {
    val locale = Locale.getDefault()
    val datePart = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val dayPart = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    return "$datePart · $dayPart"
}
