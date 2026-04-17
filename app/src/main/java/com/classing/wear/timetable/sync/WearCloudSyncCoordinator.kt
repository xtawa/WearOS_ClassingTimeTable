package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.CloudProvider
import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.sync.SyncArbitrator
import com.classing.shared.sync.SyncDomain
import com.classing.shared.sync.SyncSource
import com.classing.shared.sync.SyncStamp
import com.classing.shared.sync.WearDataLayerContracts
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
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object WearCloudSyncCoordinator {
    private const val THROTTLE_MS = 5_000L
    private val mutex = Mutex()
    private var lastRunAt = 0L
    private val webDavStorageClient: WearCloudStorageClient = WearWebDavStorageClient()
    private val googleDriveStorageClient: WearCloudStorageClient = WearGoogleDriveStorageClient()

    suspend fun pullFromCloud(context: Context, trigger: String, force: Boolean = false): Result<String> {
        return mutex.withLock {
            runCatching {
                val now = System.currentTimeMillis()
                if (!force && now - lastRunAt < THROTTLE_MS) {
                    return@runCatching "Cloud pull skipped by throttle"
                }
                val config = WearCloudConfigStore.load(context)
                if (!config.isComplete()) {
                    val message = when (config.provider) {
                        CloudProvider.WEBDAV -> "Cloud config incomplete. Configure on phone first."
                        CloudProvider.GOOGLE_DRIVE -> "Google Drive token missing/expired. Refresh on phone."
                    }
                    WearCloudConfigStore.saveSyncStatus(context, message, now)
                    lastRunAt = now
                    return@runCatching message
                }

                val client = storageClient(config.provider)
                val raw = client.readJson(config).getOrElse { throw it }
                if (raw.isNullOrBlank()) {
                    val message = "Cloud document not found"
                    WearCloudConfigStore.saveSyncStatus(context, message, now)
                    lastRunAt = now
                    return@runCatching message
                }

                val doc = WearCloudDocumentParser.parse(raw)
                applyCloudDocument(context, doc)
                val message = "Cloud pull success (${config.provider.wireValue}:$trigger)"
                WearCloudConfigStore.saveSyncStatus(context, message, now)
                lastRunAt = now
                message
            }.recoverCatching { error ->
                if (error is WearCloudAuthExpiredException) {
                    requestPhoneCloudSyncForTokenRefresh(context, trigger = CloudSyncContracts.TRIGGER_WEAR_REQUEST)
                    val message = "Google Drive token expired on Wear. Requested refresh from phone."
                    WearCloudConfigStore.saveSyncStatus(context, message, System.currentTimeMillis())
                    message
                } else {
                    throw error
                }
            }
        }
    }

    private suspend fun requestPhoneCloudSyncForTokenRefresh(context: Context, trigger: String) {
        val requestedAt = System.currentTimeMillis()
        val payloadObject = JSONObject()
            .put(WearDataLayerContracts.KEY_TRIGGER, trigger)
            .put(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
        val payload = payloadObject.toString().toByteArray(StandardCharsets.UTF_8)
        val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST).apply {
            dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
            dataMap.putString(WearDataLayerContracts.KEY_TRIGGER, trigger)
            dataMap.putString(WearDataLayerContracts.KEY_REQUEST_PAYLOAD, payloadObject.toString())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        nodes.forEach { node ->
            runCatching {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST, payload)
                    .await()
            }
        }
    }

    private fun storageClient(provider: CloudProvider): WearCloudStorageClient {
        return when (provider) {
            CloudProvider.WEBDAV -> webDavStorageClient
            CloudProvider.GOOGLE_DRIVE -> googleDriveStorageClient
        }
    }

    private suspend fun applyCloudDocument(context: Context, doc: WearCloudDocument) {
        val app = context.applicationContext as? ClassingTimetableApplication ?: return
        val settingsRepository = app.appContainer.settingsRepository

        val timetable = doc.timetable
        if (timetable != null) {
            val currentStamp = WearSyncStampStore.load(context, SyncDomain.TIMETABLE)
                ?: WearCloudConfigStore.loadLastTimetableUpdatedAt(context)
                    .takeIf { it > 0L }
                    ?.let {
                        SyncStamp(
                            revision = it,
                            source = SyncSource.UNKNOWN,
                            appliedAt = it,
                        )
                    }
            val incomingStamp = SyncStamp(
                revision = timetable.revision,
                source = timetable.source,
                appliedAt = timetable.updatedAt.takeIf { it > 0L } ?: timetable.revision,
            )
            if (SyncArbitrator.shouldApply(
                    domain = SyncDomain.TIMETABLE,
                    incoming = incomingStamp,
                    current = currentStamp,
                )
            ) {
                val payload = buildPayloadFromCloudTimetable(timetable)
                val applier = SyncPayloadApplier(app.appContainer.database)
                applier.apply(payload, SyncMode.FULL)
                WearSyncStampStore.save(context, SyncDomain.TIMETABLE, incomingStamp)
                WearSyncStampStore.saveDecision(
                    context = context,
                    domain = SyncDomain.TIMETABLE,
                    decision = "applied",
                    reason = "cloud timetable applied revision=${incomingStamp.revision} source=${incomingStamp.source.wireValue}",
                )
                WearCloudConfigStore.saveLastTimetableUpdatedAt(context, incomingStamp.revision)
                WearSurfaceUpdateRequester.requestAll(context)
            } else {
                val reason = "stale_skipped: incoming=$incomingStamp current=$currentStamp"
                WearSyncStampStore.saveDecision(
                    context = context,
                    domain = SyncDomain.TIMETABLE,
                    decision = "stale_skipped",
                    reason = reason,
                )
            }
        }

        val wearSettings = doc.wearSettings
        if (wearSettings != null && wearSettings.settingsPayload.isNotBlank()) {
            val currentStamp = WearSyncStampStore.load(context, SyncDomain.WEAR_SETTINGS)
                ?: WearCloudConfigStore.loadLastWearSettingsUpdatedAt(context)
                    .takeIf { it > 0L }
                    ?.let {
                        SyncStamp(
                            revision = it,
                            source = SyncSource.UNKNOWN,
                            appliedAt = it,
                        )
                    }
            val incomingStamp = SyncStamp(
                revision = wearSettings.revision,
                source = wearSettings.source,
                appliedAt = wearSettings.updatedAt.takeIf { it > 0L } ?: wearSettings.revision,
            )
            if (SyncArbitrator.shouldApply(
                    domain = SyncDomain.WEAR_SETTINGS,
                    incoming = incomingStamp,
                    current = currentStamp,
                )
            ) {
                settingsRepository.applyWearSettingsSnapshot(wearSettings.settingsPayload)
                val pref = settingsRepository.observePreferences().first()
                app.appContainer.reminderWorkController.setPolicy(
                    enabled = pref.remindersEnabled,
                    level = pref.keepAliveLevel,
                )
                WearSyncStampStore.save(context, SyncDomain.WEAR_SETTINGS, incomingStamp)
                WearSyncStampStore.saveDecision(
                    context = context,
                    domain = SyncDomain.WEAR_SETTINGS,
                    decision = "applied",
                    reason = "cloud wearSettings applied revision=${incomingStamp.revision} source=${incomingStamp.source.wireValue}",
                )
                WearCloudConfigStore.saveLastWearSettingsUpdatedAt(context, incomingStamp.revision)
            } else {
                val reason = "stale_skipped: incoming=$incomingStamp current=$currentStamp"
                WearSyncStampStore.saveDecision(
                    context = context,
                    domain = SyncDomain.WEAR_SETTINGS,
                    decision = "stale_skipped",
                    reason = reason,
                )
            }
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
                teacher = lesson.teacher,
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
                startWeek = lesson.startWeek,
                endWeek = lesson.endWeek,
                weekParity = lesson.weekParity,
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
