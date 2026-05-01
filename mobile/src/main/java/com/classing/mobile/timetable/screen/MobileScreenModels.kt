package com.xtawa.classingtime.screen

import com.classing.shared.importer.CourseDraft
import java.time.DayOfWeek
import java.time.LocalTime

internal enum class MobileLayer {
    Schedule,
    Dashboard,
    Calendar,
    Settings,
}

internal enum class SettingsPage {
    Main,
    Import,
    BackupRestore,
    WeekMode,
    ReminderKeepAlive,
    SyncCommunication,
    WearCommunication,
    CloudSync,
    About,
}

internal enum class ImportFocusMethod {
    ICS,
    JSON,
    MANUAL,
}

internal data class OnboardingNavigationDecision(
    val targetSettingsPage: SettingsPage?,
    val importFocusMethod: ImportFocusMethod?,
)

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

internal enum class CloudProviderUi {
    WEBDAV,
    GOOGLE_DRIVE,
}

internal data class MobileBackState(
    val layer: MobileLayer,
    val settingsPage: SettingsPage,
    val previousMainLayer: MobileLayer,
    val showImportJsonPromptPage: Boolean,
)

internal fun shouldCompleteOnImportSelection(importTarget: OnboardingImportTarget): Boolean {
    return importTarget != OnboardingImportTarget.NONE
}

internal fun resolveImportFocusMethod(importTarget: OnboardingImportTarget): ImportFocusMethod? {
    return when (importTarget) {
        OnboardingImportTarget.ICS -> ImportFocusMethod.ICS
        OnboardingImportTarget.JSON -> ImportFocusMethod.JSON
        OnboardingImportTarget.MANUAL_ENTRY -> ImportFocusMethod.MANUAL
        else -> null
    }
}

internal fun resolveOnboardingNavigation(completion: OnboardingCompletion): OnboardingNavigationDecision {
    val targetSettingsPage = when {
        completion.openCloudSyncSettingsAfterFinish -> SettingsPage.CloudSync

        completion.openSettingsHomeAfterFinish -> SettingsPage.Main
        else -> null
    }
    return OnboardingNavigationDecision(
        targetSettingsPage = targetSettingsPage,
        importFocusMethod = if (targetSettingsPage == SettingsPage.Import) {
            resolveImportFocusMethod(completion.importTarget)
        } else {
            null
        },
    )
}

internal fun consumeImportFocus(
    pendingFocusMethod: ImportFocusMethod?,
    consumedFocusMethod: ImportFocusMethod,
): ImportFocusMethod? {
    return if (pendingFocusMethod == consumedFocusMethod) null else pendingFocusMethod
}

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

internal data class ImportItemState(
    val lesson: LessonUi,
    val included: Boolean = true,
    val hasConflict: Boolean = false,
    val conflictWithExisting: List<LessonUi> = emptyList(),
    val anomalies: List<String> = emptyList(),
)

internal data class ImportPreviewSummary(
    val total: Int,
    val validCount: Int,
    val conflictCount: Int,
    val anomalyCount: Int,
    val skippedCount: Int,
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
