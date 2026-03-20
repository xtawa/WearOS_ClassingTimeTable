package com.xtawa.classingtime.screen

import com.classing.shared.importer.CourseDraft
import java.time.DayOfWeek
import java.time.LocalTime

internal enum class MobileLayer {
    Schedule,
    Calendar,
    Settings,
}

internal enum class SettingsPage {
    Main,
    Import,
    WeekMode,
    WearCommunication,
    About,
}

internal enum class WeekNumberMode {
    NATURAL,
    SEMESTER,
}

internal enum class ChangeScope {
    Temporary,
    Persistent,
}

internal enum class WearSyncMode {
    WEARABLE_API,
    WEAROS_APP,
}

internal data class LessonUi(
    val id: String,
    val title: String,
    val location: String?,
    val note: String?,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)

internal data class ParseOutcome(
    val lessons: List<LessonUi>,
    val drafts: List<CourseDraft>,
    val message: String,
    val warnings: List<String>,
)

internal data class JsonParseOutcome(
    val lessons: List<LessonUi>,
    val message: String,
    val warnings: List<String>,
)

internal data class LessonConflict(
    val first: LessonUi,
    val second: LessonUi,
)

internal data class WearOsCompanionInfo(
    val packageName: String,
    val versionName: String,
    val isChinaOrLe: Boolean,
)
