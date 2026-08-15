package com.xtawa.classingtime.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.Locale

internal fun classingCourseAccent(title: String): Color {
    val normalized = title.lowercase(Locale.ROOT)
    return when {
        listOf("physics", "物理", "工程").any(normalized::contains) -> ClassingColors.Physics
        listOf("math", "数学", "數學").any(normalized::contains) -> ClassingColors.Mathematics
        listOf("biology", "生物").any(normalized::contains) -> ClassingColors.Biology
        listOf("chemistry", "化学", "化學").any(normalized::contains) -> ClassingColors.Chemistry
        listOf("english", "language", "英语", "英語", "语文", "語文").any(normalized::contains) -> ClassingColors.Language
        else -> ClassingColors.GeneralCourse
    }
}
