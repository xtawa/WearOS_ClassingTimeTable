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
    BackupRestore,
    WeekMode,
    WearCommunication,
    CloudSync,
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
    AUTO,
    WEARABLE_API,
    WEAROS_APP,
}

internal data class MobileBackState(
    val layer: MobileLayer,
    val settingsPage: SettingsPage,
    val previousMainLayer: MobileLayer,
    val showImportJsonPromptPage: Boolean,
)

internal fun reduceBackState(state: MobileBackState): MobileBackState? {
    if (state.layer != MobileLayer.Settings) return null
    if (state.settingsPage == SettingsPage.Import && state.showImportJsonPromptPage) {
        return state.copy(showImportJsonPromptPage = false)
    }
    if (state.settingsPage != SettingsPage.Main) {
        return state.copy(
            settingsPage = SettingsPage.Main,
            showImportJsonPromptPage = false,
        )
    }
    val targetMain = if (state.previousMainLayer == MobileLayer.Settings) {
        MobileLayer.Schedule
    } else {
        state.previousMainLayer
    }
    return state.copy(
        layer = targetMain,
        settingsPage = SettingsPage.Main,
        showImportJsonPromptPage = false,
    )
}

internal const val DEFAULT_START_WEEK = 1
internal const val DEFAULT_END_WEEK = 30

internal enum class LessonWeekParity {
    ALL,
    ODD,
    EVEN,
    ;

    companion object {
        fun fromRaw(raw: String?): LessonWeekParity {
            return entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: ALL
        }
    }
}

internal data class LessonUi(
    val id: String,
    val title: String,
    val teacher: String? = null,
    val location: String?,
    val note: String?,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startWeek: Int = DEFAULT_START_WEEK,
    val endWeek: Int = DEFAULT_END_WEEK,
    val weekParity: LessonWeekParity = LessonWeekParity.ALL,
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

internal enum class WearAutoVariant {
    CN_LE,
    GLOBAL,
    UNKNOWN,
}

internal data class WearAutoDetectionResult(
    val companionInfo: WearOsCompanionInfo?,
    val variant: WearAutoVariant,
    val effectiveMode: WearSyncMode,
)

internal data class WearSyncModeResolution(
    val selectedMode: WearSyncMode,
    val effectiveMode: WearSyncMode,
    val autoDetection: WearAutoDetectionResult?,
)
