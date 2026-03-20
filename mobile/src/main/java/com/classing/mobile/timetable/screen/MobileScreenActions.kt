package com.xtawa.classingtime.screen

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.R
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.MobileSettings
import com.xtawa.classingtime.reminder.ReminderScheduler
import com.xtawa.classingtime.sync.WearSyncAckStore
import java.time.LocalDate
import java.time.ZoneId

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

internal fun persistSettings(
    context: Context,
    showWeekend: Boolean,
    reminderEnabled: Boolean,
    reminderMinutes: Int,
    rawIcs: String,
    parseMessage: String,
    wearSyncMode: WearSyncMode,
    weekNumberMode: WeekNumberMode,
    semesterWeekStartDate: LocalDate,
) {
    MobilePrefsStore.saveSettings(
        context,
        MobileSettings(
            showWeekend = showWeekend,
            reminderEnabled = reminderEnabled,
            reminderMinutes = reminderMinutes,
            rawIcs = rawIcs,
            parseMessage = parseMessage,
            wearSyncMode = wearSyncMode.name,
            weekNumberMode = weekNumberMode.name,
            semesterWeekStartDate = semesterWeekStartDate.toString(),
        ),
    )
}

internal fun persistLessons(context: Context, lessons: List<LessonUi>) {
    MobilePrefsStore.saveLessons(context, lessons.map { it.toPersistedLesson() })
}

internal fun applyImportedLessons(importLessons: List<LessonUi>): List<LessonUi> {
    return importLessons.sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
}

internal fun appendManualLesson(lessons: List<LessonUi>, newLesson: LessonUi): List<LessonUi> {
    return (lessons + newLesson).sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
}

internal fun applyLessonEdit(lessons: List<LessonUi>, updatedLesson: LessonUi): List<LessonUi> {
    return lessons.map { if (it.id == updatedLesson.id) updatedLesson else it }
        .sortedWith(compareBy<LessonUi> { it.dayOfWeek.value }.thenBy { it.startTime })
}

internal fun removeLesson(lessons: List<LessonUi>, targetLesson: LessonUi): List<LessonUi> {
    return lessons.filterNot { it.id == targetLesson.id }
}

internal fun syncReminderWork(context: Context, reminderEnabled: Boolean) {
    ReminderScheduler.sync(context, reminderEnabled)
}

internal fun resolveSyncAckUpdate(
    context: Context,
    latestWearAckAtMillis: Long,
    force: Boolean = false,
): SyncAckUpdate? {
    val ack = WearSyncAckStore.load(context) ?: return null
    if (!force && ack.syncedAtMillis <= latestWearAckAtMillis) return null

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
    val (connectedCount, connectionMessage) = when (wearSyncMode) {
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
    val syncMessage = when (wearSyncMode) {
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
    }

    val connectionStatus = computeWearConnectionStatus(
        context = context,
        wearSyncMode = wearSyncMode,
        latestWearAckAtMillis = nextLatestAckAt,
        currentWearSyncMessage = syncMessage,
    )
    return ManualWearSyncResult(
        wearSyncMessage = connectionStatus.wearSyncMessage,
        latestAckAtMillis = connectionStatus.latestAckAtMillis,
        wearConnectedCount = connectionStatus.wearConnectedCount,
        wearConnectionMessage = connectionStatus.wearConnectionMessage,
    )
}
