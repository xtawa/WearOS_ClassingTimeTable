package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.wear.timetable.ClassingTimetableApplication
import com.classing.wear.timetable.data.sync.RemoteCourse
import com.classing.wear.timetable.data.sync.RemoteException
import com.classing.wear.timetable.data.sync.RemoteSchedulePayload
import com.classing.wear.timetable.data.sync.RemoteSemester
import com.classing.wear.timetable.data.sync.RemoteSession
import com.classing.wear.timetable.data.sync.RemoteTimeSlot
import com.classing.wear.timetable.data.sync.SyncPayloadApplier
import com.classing.wear.timetable.domain.model.SyncMode
import com.classing.wear.timetable.widget.WearSurfaceUpdateRequester
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WearCloudSyncCoordinator {
    private const val THROTTLE_MS = 5_000L
    private val mutex = Mutex()
    private var lastRunAt = 0L
    private val client = WearWebDavClient()

    suspend fun pullFromCloud(context: Context, trigger: String, force: Boolean = false): Result<String> {
        return mutex.withLock {
            runCatching {
                val now = System.currentTimeMillis()
                if (!force && now - lastRunAt < THROTTLE_MS) {
                    return@runCatching "Cloud pull skipped by throttle"
                }
                val config = WearCloudConfigStore.load(context)
                if (!config.isComplete()) {
                    val message = "Cloud config incomplete. Configure on phone first."
                    WearCloudConfigStore.saveSyncStatus(context, message, now)
                    lastRunAt = now
                    return@runCatching message
                }

                val raw = client.readJson(config).getOrElse { throw it }
                if (raw.isNullOrBlank()) {
                    val message = "Cloud document not found"
                    WearCloudConfigStore.saveSyncStatus(context, message, now)
                    lastRunAt = now
                    return@runCatching message
                }

                val doc = WearCloudDocumentParser.parse(raw)
                applyCloudDocument(context, doc)
                val message = "Cloud pull success ($trigger)"
                WearCloudConfigStore.saveSyncStatus(context, message, now)
                lastRunAt = now
                message
            }
        }
    }

    private suspend fun applyCloudDocument(context: Context, doc: WearCloudDocument) {
        val app = context.applicationContext as? ClassingTimetableApplication ?: return
        val settingsRepository = app.appContainer.settingsRepository

        val timetable = doc.timetable
        if (timetable != null && timetable.updatedAt > WearCloudConfigStore.loadLastTimetableUpdatedAt(context)) {
            val payload = buildPayloadFromCloudTimetable(timetable)
            val applier = SyncPayloadApplier(app.appContainer.database)
            applier.apply(payload, SyncMode.FULL)
            WearCloudConfigStore.saveLastTimetableUpdatedAt(context, timetable.updatedAt)
            WearSurfaceUpdateRequester.requestAll(context)
        }

        if (doc.wearSettingsPayload.isNotBlank() && doc.wearSettingsUpdatedAt > WearCloudConfigStore.loadLastWearSettingsUpdatedAt(context)) {
            settingsRepository.applyWearSettingsSnapshot(doc.wearSettingsPayload)
            WearCloudConfigStore.saveLastWearSettingsUpdatedAt(context, doc.wearSettingsUpdatedAt)
        }
    }

    private fun buildPayloadFromCloudTimetable(timetable: WearCloudTimetable): RemoteSchedulePayload {
        val weekMode = timetable.weekNumberMode.uppercase()
        val parsedSemesterStart = runCatching { LocalDate.parse(timetable.semesterWeekStartDate) }.getOrNull()
        val today = LocalDate.now()
        val isoWeekStart = LocalDate.of(today.get(WeekFields.ISO.weekBasedYear()), 1, 4)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val semesterStart = if (weekMode == "SEMESTER") (parsedSemesterStart ?: today) else isoWeekStart
        val semesterTotalWeeks = 520
        val semesterRemoteId = if (weekMode == "SEMESTER") "cloud-semester-semester" else "cloud-semester-natural"

        val semester = RemoteSemester(
            remoteId = semesterRemoteId,
            name = "Cloud Synced ($weekMode)",
            startDate = semesterStart,
            endDate = semesterStart.plusWeeks(semesterTotalWeeks.toLong()).minusDays(1),
            totalWeeks = semesterTotalWeeks,
            isActive = true,
            version = System.currentTimeMillis(),
        )

        val slotMap = linkedMapOf<String, RemoteTimeSlot>()
        val courses = mutableListOf<RemoteCourse>()
        val sessions = mutableListOf<RemoteSession>()

        timetable.lessons.forEachIndexed { index, lesson ->
            val safeStart = lesson.startMinute.coerceIn(0, 23 * 60 + 59)
            val safeEnd = lesson.endMinute.coerceAtLeast(safeStart + 1).coerceAtMost(23 * 60 + 59)
            val start = LocalTime.of(safeStart / 60, safeStart % 60)
            val end = LocalTime.of(safeEnd / 60, safeEnd % 60)
            val slotKey = "${start}-${end}"
            val slot = slotMap.getOrPut(slotKey) {
                RemoteTimeSlot(
                    remoteId = "cloud-slot-$slotKey",
                    semesterRemoteId = semesterRemoteId,
                    indexInDay = slotMap.size + 1,
                    label = "$start-$end",
                    startTime = start,
                    endTime = end,
                    version = System.currentTimeMillis(),
                )
            }
            val courseId = "cloud-course-$index"
            courses += RemoteCourse(
                remoteId = courseId,
                semesterRemoteId = semesterRemoteId,
                name = lesson.title,
                teacher = "",
                classroom = lesson.location,
                note = lesson.note,
                colorLabel = "teal",
                isFavorite = false,
                version = System.currentTimeMillis(),
            )
            sessions += RemoteSession(
                remoteId = "cloud-session-$index",
                semesterRemoteId = semesterRemoteId,
                courseRemoteId = courseId,
                dayOfWeek = lesson.dayOfWeek,
                timeSlotRemoteId = slot.remoteId,
                startWeek = 1,
                endWeek = semesterTotalWeeks,
                weekParity = "ALL",
                version = System.currentTimeMillis(),
            )
        }

        return RemoteSchedulePayload(
            dataVersion = System.currentTimeMillis(),
            semesters = listOf(semester),
            timeSlots = slotMap.values.toList(),
            courses = courses,
            sessions = sessions,
            exceptions = emptyList<RemoteException>(),
        )
    }
}
