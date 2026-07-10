package com.xtawa.classingtime.screen

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.R
import com.xtawa.classingtime.data.AccountSummary
import com.xtawa.classingtime.data.DailyBriefingChannel
import com.xtawa.classingtime.data.MembershipSummary
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.data.OfficialSyncFrequency
import com.xtawa.classingtime.data.PersistedTimetableState
import com.xtawa.classingtime.data.SyncScope
import com.xtawa.classingtime.reminder.DailyBriefingScheduler
import com.xtawa.classingtime.reminder.ReminderScheduler
import com.xtawa.classingtime.reminder.KeepAliveLevel
import com.xtawa.classingtime.sync.CloudSyncEngine
import com.xtawa.classingtime.sync.WearDataLayerSyncPublisher
import com.xtawa.classingtime.sync.WearSyncAckStore
import com.xtawa.classingtime.sync.toWearCloudSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal data class SyncAckUpdate(
    val latestAckAtMillis: Long,
    val wearSyncMessage: String,
)

internal data class WearConnectionStatus(
    val wearConnectedCount: Int,
    val wearConnectionMessage: String,
    val latestAckAtMillis: Long,
    val wearSyncMessage: String,
)

internal data class ManualWearSyncResult(
    val wearSyncMessage: String,
    val latestAckAtMillis: Long,
    val wearConnectedCount: Int,
    val wearConnectionMessage: String,
)

internal data class JsonImportApplyResult(
    val baseLessons: List<LessonUi>,
    val exceptions: List<ScheduleExceptionUi>,
    val appliedCount: Int,
    val skippedDuplicateCount: Int,
)

internal data class LoadedScheduleState(
    val baseLessons: List<LessonUi>,
    val exceptions: List<ScheduleExceptionUi>,
    val snapshots: List<ScheduleStateSnapshot>,
)

private data class LessonDuplicateFingerprint(
    val title: String,
    val teacher: String,
    val location: String,
    val note: String,
    val dayOfWeek: DayOfWeek,
    val startTime: java.time.LocalTime,
    val endTime: java.time.LocalTime,
    val startWeek: Int,
    val endWeek: Int,
    val weekParity: LessonWeekParity,
)

private val lessonSortComparator = compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime }

internal fun persistSettings(
    context: Context,
    showWeekend: Boolean,
    reminderEnabled: Boolean,
    reminderMinutes: Int,
    keepAliveLevel: KeepAliveLevel,
    experimentalAccessibilityKeepAliveEnabled: Boolean,
    rawIcs: String,
    parseMessage: String,
    wearSyncMode: WearSyncMode,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
    weekStartDay: DayOfWeek,
    cloudProvider: CloudProviderUi,
    cloudSyncEnabled: Boolean,
    cloudServerUrl: String,
    cloudRemotePath: String,
    cloudUsername: String,
    cloudDriveFileName: String,
    cloudDriveTokenExpireAt: Long,
    cloudConfigPushStatus: String,
    cloudLastResult: String,
    cloudLastSyncedAt: Long,
    accountSummary: AccountSummary = AccountSummary(),
    membershipSummary: MembershipSummary = MembershipSummary(),
    dailyBriefingEnabled: Boolean = false,
    dailyBriefingChannel: DailyBriefingChannel = DailyBriefingChannel.APP_NOTIFICATION,
    dailyBriefingTime: String = "20:00",
    officialSyncFrequency: OfficialSyncFrequency = OfficialSyncFrequency.MANUAL_ONLY,
    syncScopes: Set<SyncScope> = SyncScope.entries.toSet(),
) {
    val settings = MobileSettings(
        showWeekend = showWeekend,
        reminderEnabled = reminderEnabled,
        reminderMinutes = reminderMinutes,
        keepAliveLevel = keepAliveLevel.name,
        experimentalAccessibilityKeepAliveEnabled = experimentalAccessibilityKeepAliveEnabled,
        rawIcs = rawIcs,
        parseMessage = parseMessage,
        wearSyncMode = wearSyncMode.name,
        weekNumberMode = weekNumberMode.name,
        semesterWeekStartDate = semesterWeekStartDate.toString(),
        weekStartDay = weekStartDay.name,
        cloudProvider = cloudProvider.name,
        cloudSyncEnabled = cloudSyncEnabled,
        cloudServerUrl = cloudServerUrl,
        cloudRemotePath = cloudRemotePath,
        cloudUsername = cloudUsername,
        cloudDriveFileName = cloudDriveFileName,
        cloudDriveTokenExpireAt = cloudDriveTokenExpireAt,
        cloudConfigPushStatus = cloudConfigPushStatus,
        cloudLastResult = cloudLastResult,
        cloudLastSyncedAt = cloudLastSyncedAt,
        accountSummary = accountSummary,
        membershipSummary = membershipSummary,
        dailyBriefingEnabled = dailyBriefingEnabled,
        dailyBriefingChannel = dailyBriefingChannel,
        dailyBriefingTime = dailyBriefingTime,
        officialSyncFrequency = officialSyncFrequency,
        syncScopes = syncScopes,
    )
    MobilePrefsStore.saveSettings(
        context,
        settings,
    )
    DailyBriefingScheduler.sync(context, settings)
    CloudSyncEngine.schedulePeriodic(context)
    CoroutineScope(Dispatchers.IO).launch {
        WearDataLayerSyncPublisher.publishCloudSnapshot(
            context = context.applicationContext,
            payload = settings.toWearCloudSnapshot(
                password = "",
                driveAccessToken = "",
                driveAccessTokenExpireAt = cloudDriveTokenExpireAt,
            ).toString(),
        )
    }
    MobilePrefsStore.markLocalMobileSettingsUpdated(context)
}

internal fun loadScheduleState(context: Context): LoadedScheduleState {
    val state: PersistedTimetableState = MobilePrefsStore.loadTimetableState(context)
    return LoadedScheduleState(
        baseLessons = state.baseLessons.map { it.toLessonUi() },
        exceptions = state.exceptions.map { it.toUi() },
        snapshots = state.snapshots.map { it.toUi() },
    )
}

internal fun persistScheduleState(
    context: Context,
    baseLessons: List<LessonUi>,
    exceptions: List<ScheduleExceptionUi>,
    snapshots: List<ScheduleStateSnapshot>,
) {
    MobilePrefsStore.saveTimetableState(
        context = context,
        baseLessons = baseLessons.map { it.toPersistedLesson() },
        exceptions = exceptions.map { it.toPersisted() },
        snapshots = snapshots.map { it.toPersisted() },
    )
    MobilePrefsStore.markLocalTimetableUpdated(context)
    val settings = MobilePrefsStore.loadSettings(context)
    ReminderScheduler.sync(
        context = context,
        enabled = settings.reminderEnabled,
        keepAliveLevel = KeepAliveLevel.fromRaw(settings.keepAliveLevel),
        reminderMinutes = settings.reminderMinutes,
    )
}

internal fun applyImportedLessons(importLessons: List<LessonUi>): ScheduleMutationResult {
    return ScheduleMutationResult(
        baseLessons = sortLessons(importLessons),
        exceptions = emptyList(),
    )
}

internal fun applyJsonImport(
    existingBaseLessons: List<LessonUi>,
    existingExceptions: List<ScheduleExceptionUi>,
    importLessons: List<LessonUi>,
    mode: JsonImportMode,
): JsonImportApplyResult {
    return when (mode) {
        JsonImportMode.REPLACE -> JsonImportApplyResult(
            baseLessons = sortLessons(importLessons),
            exceptions = emptyList(),
            appliedCount = importLessons.size,
            skippedDuplicateCount = 0,
        )

        JsonImportMode.APPEND -> appendImportedLessons(existingBaseLessons, existingExceptions, importLessons)
    }
}

internal fun appendImportedLessons(
    existingBaseLessons: List<LessonUi>,
    existingExceptions: List<ScheduleExceptionUi>,
    importLessons: List<LessonUi>,
): JsonImportApplyResult {
    val existingFingerprints = existingBaseLessons
        .asSequence()
        .map(::buildLessonDuplicateFingerprint)
        .toSet()
    val uniqueImports = importLessons.filter { lesson ->
        buildLessonDuplicateFingerprint(lesson) !in existingFingerprints
    }

    return JsonImportApplyResult(
        baseLessons = sortLessons(existingBaseLessons + uniqueImports),
        exceptions = existingExceptions,
        appliedCount = uniqueImports.size,
        skippedDuplicateCount = importLessons.size - uniqueImports.size,
    )
}

internal fun appendManualLesson(baseLessons: List<LessonUi>, newLesson: LessonUi): ScheduleMutationResult {
    return ScheduleMutationResult(
        baseLessons = sortLessons(baseLessons + newLesson),
        exceptions = emptyList(),
    )
}

internal fun applyLessonEdit(
    baseLessons: List<LessonUi>,
    exceptions: List<ScheduleExceptionUi>,
    editContext: LessonEditContext,
    updatedLesson: LessonUi,
    scope: LessonEditScope,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
): ScheduleMutationResult {
    val targetLesson = editContext.lesson
    return when (scope) {
        LessonEditScope.WholeLesson -> {
            if (editContext.isNewLesson) {
                ScheduleMutationResult(
                    baseLessons = sortLessons(baseLessons + updatedLesson),
                    exceptions = exceptions,
                )
            } else {
                ScheduleMutationResult(
                    baseLessons = baseLessons.map { lesson ->
                        if (lesson.id == targetLesson.id) updatedLesson else lesson
                    }.let(::sortLessons),
                    exceptions = exceptions,
                )
            }
        }

        LessonEditScope.FromThisWeek -> {
            val anchorDate = editContext.anchorDate ?: return ScheduleMutationResult(baseLessons, exceptions)
            val anchorWeek = resolveAnchorWeek(anchorDate, weekNumberMode, semesterWeekStartDate)
            val original = baseLessons.firstOrNull { it.id == targetLesson.id }
                ?: return ScheduleMutationResult(baseLessons, exceptions)
            val keptLessons = mutableListOf<LessonUi>()
            baseLessons.forEach { lesson ->
                if (lesson.id != original.id) {
                    keptLessons += lesson
                    return@forEach
                }
                if (anchorWeek <= lesson.startWeek) {
                    return@forEach
                }
                keptLessons += lesson.copy(endWeek = (anchorWeek - 1).coerceAtLeast(lesson.startWeek))
            }
            keptLessons += updatedLesson.copy(
                id = "${original.id}-from-$anchorWeek",
                startWeek = anchorWeek.coerceAtLeast(original.startWeek),
                endWeek = original.endWeek,
            )
            ScheduleMutationResult(
                baseLessons = sortLessons(keptLessons),
                exceptions = exceptions.filterNot { exception ->
                    exception.lessonId == original.id && !exception.date.isBefore(anchorDate)
                },
            )
        }

        LessonEditScope.SingleOccurrence -> {
            val anchorDate = editContext.anchorDate ?: return ScheduleMutationResult(baseLessons, exceptions)
            val filtered = exceptions.filterNot { exception ->
                exception.date == anchorDate &&
                    when {
                        editContext.isNewLesson -> exception.type == ScheduleExceptionKind.MAKE_UP &&
                            exception.title == updatedLesson.title &&
                            exception.startTime == updatedLesson.startTime

                        else -> exception.lessonId == targetLesson.id
                    }
            }
            val type = if (editContext.isNewLesson) ScheduleExceptionKind.MAKE_UP else ScheduleExceptionKind.RESCHEDULE
            ScheduleMutationResult(
                baseLessons = baseLessons,
                exceptions = filtered + updatedLesson.toScheduleException(
                    type = type,
                    anchorDate = anchorDate,
                    lessonId = if (editContext.isNewLesson) null else targetLesson.id,
                ),
            )
        }
    }
}

internal fun removeLesson(
    baseLessons: List<LessonUi>,
    exceptions: List<ScheduleExceptionUi>,
    editContext: LessonEditContext,
    scope: LessonEditScope,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
): ScheduleMutationResult {
    val targetLesson = editContext.lesson
    return when (scope) {
        LessonEditScope.WholeLesson -> ScheduleMutationResult(
            baseLessons = baseLessons.filterNot { it.id == targetLesson.id },
            exceptions = exceptions.filterNot { it.lessonId == targetLesson.id },
        )

        LessonEditScope.FromThisWeek -> {
            val anchorDate = editContext.anchorDate ?: return ScheduleMutationResult(baseLessons, exceptions)
            val anchorWeek = resolveAnchorWeek(anchorDate, weekNumberMode, semesterWeekStartDate)
            val updatedBase = baseLessons.mapNotNull { lesson ->
                if (lesson.id != targetLesson.id) return@mapNotNull lesson
                if (anchorWeek <= lesson.startWeek) return@mapNotNull null
                lesson.copy(endWeek = (anchorWeek - 1).coerceAtLeast(lesson.startWeek))
            }
            ScheduleMutationResult(
                baseLessons = sortLessons(updatedBase),
                exceptions = exceptions.filterNot { it.lessonId == targetLesson.id && !it.date.isBefore(anchorDate) },
            )
        }

        LessonEditScope.SingleOccurrence -> {
            val anchorDate = editContext.anchorDate ?: return ScheduleMutationResult(baseLessons, exceptions)
            ScheduleMutationResult(
                baseLessons = baseLessons,
                exceptions = exceptions.filterNot { it.lessonId == targetLesson.id && it.date == anchorDate } + ScheduleExceptionUi(
                    id = "cancel-${targetLesson.id}-$anchorDate",
                    lessonId = targetLesson.id,
                    type = ScheduleExceptionKind.CANCEL,
                    date = anchorDate,
                ),
            )
        }
    }
}

internal fun syncReminderWork(context: Context, reminderEnabled: Boolean) {
    val settings = MobilePrefsStore.loadSettings(context)
    ReminderScheduler.sync(
        context = context,
        enabled = reminderEnabled,
        keepAliveLevel = KeepAliveLevel.fromRaw(settings.keepAliveLevel),
        reminderMinutes = settings.reminderMinutes,
    )
}

private fun sortLessons(lessons: List<LessonUi>): List<LessonUi> {
    return lessons.sortedWith(lessonSortComparator)
}

private fun LessonUi.toScheduleException(
    type: ScheduleExceptionKind,
    anchorDate: LocalDate,
    lessonId: String?,
): ScheduleExceptionUi {
    return ScheduleExceptionUi(
        id = "${type.name.lowercase()}-${lessonId ?: id}-$anchorDate",
        lessonId = lessonId,
        type = type,
        date = anchorDate,
        title = title,
        teacher = teacher,
        location = location,
        note = note,
        dayOfWeek = anchorDate.dayOfWeek,
        startTime = startTime,
        endTime = endTime,
    )
}

private fun buildLessonDuplicateFingerprint(lesson: LessonUi): LessonDuplicateFingerprint {
    return LessonDuplicateFingerprint(
        title = lesson.title.trim(),
        teacher = lesson.teacher.orEmpty().trim(),
        location = lesson.location.orEmpty().trim(),
        note = lesson.note.orEmpty().trim(),
        dayOfWeek = lesson.dayOfWeek,
        startTime = lesson.startTime,
        endTime = lesson.endTime,
        startWeek = lesson.startWeek,
        endWeek = lesson.endWeek,
        weekParity = lesson.weekParity,
    )
}

internal fun resolveSyncAckUpdate(
    context: Context,
    latestWearAckAtMillis: Long,
    force: Boolean = false,
): SyncAckUpdate? {
    val ack = WearSyncAckStore.load(context) ?: return null
    if (!force && ack.syncedAtMillis <= latestWearAckAtMillis) return null
    MobilePrefsStore.markLastWearAck(
        context = context,
        ackAt = ack.syncedAtMillis,
        result = if (ack.success) {
            "Applied ${ack.appliedLessonCount} lessons"
        } else {
            ack.errorMessage.ifBlank { "Wear apply failed" }
        },
    )

    return SyncAckUpdate(
        latestAckAtMillis = ack.syncedAtMillis,
        wearSyncMessage = formatWearSyncAckMessage(context, ack),
    )
}

internal suspend fun computeWearConnectionStatus(
    context: Context,
    wearSyncMode: WearSyncMode,
    latestWearAckAtMillis: Long,
    currentWearSyncMessage: String,
): WearConnectionStatus {
    val resolvedMode = resolveWearSyncMode(context, wearSyncMode)
    val (connectedCount, baseConnectionMessage) = when (resolvedMode.effectiveMode) {
        WearSyncMode.WEARABLE_API -> {
            val result = fetchConnectedWearNodeCount(context)
            val nodeCount = result.getOrDefault(0)
            val message = if (result.isSuccess) {
                if (nodeCount > 0) {
                    context.getString(R.string.wear_connection_connected, nodeCount)
                } else {
                    context.getString(R.string.wear_connection_disconnected)
                }
            } else {
                context.getString(R.string.wear_connection_error, result.exceptionOrNull()?.message ?: "unknown")
            }
            nodeCount to message
        }

        WearSyncMode.WEAROS_APP -> {
            val companion = findWearOsCompanionInfo(context)
            val result = fetchConnectedWearNodeCount(context)
            val nodeCount = result.getOrDefault(0)
            val message = if (companion == null) {
                context.getString(R.string.wearos_app_unavailable)
            } else if (nodeCount > 0) {
                context.getString(
                    R.string.wearos_app_available_connected,
                    companion.toDisplayLabel(),
                    nodeCount,
                )
            } else {
                context.getString(R.string.wearos_app_available, companion.toDisplayLabel())
            }
            nodeCount to message
        }

        WearSyncMode.AUTO -> {
            0 to context.getString(R.string.wear_connection_error, "AUTO unresolved")
        }
    }

    val connectionMessage = if (wearSyncMode == WearSyncMode.AUTO) {
        val auto = resolvedMode.autoDetection
        val variantLabel = wearAutoVariantLabel(context, auto?.variant ?: WearAutoVariant.UNKNOWN)
        val effectiveLabel = wearSyncModeLabel(context, resolvedMode.effectiveMode)
        context.getString(
            R.string.settings_wear_auto_connection_summary,
            variantLabel,
            effectiveLabel,
            baseConnectionMessage,
        )
    } else {
        baseConnectionMessage
    }

    val syncUpdate = resolveSyncAckUpdate(context, latestWearAckAtMillis)
    return WearConnectionStatus(
        wearConnectedCount = connectedCount,
        wearConnectionMessage = connectionMessage,
        latestAckAtMillis = syncUpdate?.latestAckAtMillis ?: latestWearAckAtMillis,
        wearSyncMessage = syncUpdate?.wearSyncMessage ?: currentWearSyncMessage,
    )
}

internal suspend fun executeManualWearSync(
    context: Context,
    wearSyncMode: WearSyncMode,
    lessons: List<LessonUi>,
    zoneId: ZoneId,
    latestWearAckAtMillis: Long,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
): ManualWearSyncResult {
    val startedAtMillis = System.currentTimeMillis()
    var nextLatestAckAt = latestWearAckAtMillis
    val resolvedMode = resolveWearSyncMode(context, wearSyncMode)
    val syncMessage = when (resolvedMode.effectiveMode) {
        WearSyncMode.WEARABLE_API -> {
            val result = syncLessonsToWear(
                context = context,
                lessons = lessons,
                zoneId = zoneId,
                source = WearDataLayerContracts.SOURCE_WEARABLE_API,
                allowDisconnectedQueue = false,
                weekNumberMode = weekNumberMode,
                semesterWeekStartDate = semesterWeekStartDate,
            )
            handleStartedWearSync(
                context = context,
                result = result,
                startedAtMillis = startedAtMillis,
                latestAckUpdater = { ack ->
                    nextLatestAckAt = ack.syncedAtMillis
                },
            )
        }

        WearSyncMode.WEAROS_APP -> {
            val companion = findWearOsCompanionInfo(context)
                ?: return computeWearConnectionStatus(
                    context = context,
                    wearSyncMode = wearSyncMode,
                    latestWearAckAtMillis = latestWearAckAtMillis,
                    currentWearSyncMessage = context.getString(
                        R.string.wear_sync_via_wearos_app_failed,
                        "WearOS app not installed",
                    ),
                ).let {
                    ManualWearSyncResult(
                        wearSyncMessage = it.wearSyncMessage,
                        latestAckAtMillis = it.latestAckAtMillis,
                        wearConnectedCount = it.wearConnectedCount,
                        wearConnectionMessage = it.wearConnectionMessage,
                    )
                }

            val result = syncLessonsViaWearOsApp(
                context = context,
                lessons = lessons,
                zoneId = zoneId,
                weekNumberMode = weekNumberMode,
                semesterWeekStartDate = semesterWeekStartDate,
            )
            handleStartedWearSync(
                context = context,
                result = result,
                startedAtMillis = startedAtMillis,
                queuedMessage = context.getString(
                    R.string.wear_sync_queued_via_wearos_app,
                    companion.toDisplayLabel(),
                ),
                latestAckUpdater = { ack ->
                    nextLatestAckAt = ack.syncedAtMillis
                },
            )
        }

        WearSyncMode.AUTO -> {
            context.getString(R.string.wear_sync_failed, "AUTO unresolved")
        }
    }
    val resolvedSyncMessage = if (wearSyncMode == WearSyncMode.AUTO) {
        val variantLabel = wearAutoVariantLabel(
            context,
            resolvedMode.autoDetection?.variant ?: WearAutoVariant.UNKNOWN,
        )
        val effectiveLabel = wearSyncModeLabel(context, resolvedMode.effectiveMode)
        context.getString(
            R.string.settings_wear_auto_sync_summary,
            variantLabel,
            effectiveLabel,
            syncMessage,
        )
    } else {
        syncMessage
    }

    val connectionStatus = computeWearConnectionStatus(
        context = context,
        wearSyncMode = wearSyncMode,
        latestWearAckAtMillis = nextLatestAckAt,
        currentWearSyncMessage = resolvedSyncMessage,
    )
    return ManualWearSyncResult(
        wearSyncMessage = connectionStatus.wearSyncMessage,
        latestAckAtMillis = connectionStatus.latestAckAtMillis,
        wearConnectedCount = connectionStatus.wearConnectedCount,
        wearConnectionMessage = connectionStatus.wearConnectionMessage,
    )
}
