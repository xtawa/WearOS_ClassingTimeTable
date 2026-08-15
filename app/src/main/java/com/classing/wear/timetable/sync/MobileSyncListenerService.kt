package com.classing.wear.timetable.sync

import android.content.Context
import android.util.Log
import com.classing.shared.model.MAX_SCHEDULE_WEEK
import com.classing.shared.model.MIN_SCHEDULE_WEEK
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
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class MobileSyncListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private enum class WeekNumberMode {
        NATURAL,
        SEMESTER,
    }

    private data class ApplyResult(
        val success: Boolean,
        val appliedLessonCount: Int,
        val errorMessage: String? = null,
    )

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (WearSyncModeStore.isIndependentModeEnabled(applicationContext)) return
        if (messageEvent.path != WearDataLayerContracts.PATH_SYNC_LESSONS) {
            super.onMessageReceived(messageEvent)
            return
        }

        val payload = runCatching { String(messageEvent.data, StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        handleLessonSync(payload = payload, sourceNodeId = messageEvent.sourceNodeId, sourceHint = null)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        if (WearSyncModeStore.isIndependentModeEnabled(applicationContext)) return
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearDataLayerContracts.PATH_SYNC_LESSONS) return@forEach

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val payload = dataMap.getString(WearDataLayerContracts.KEY_PAYLOAD).orEmpty()
            val sourceNodeId = event.dataItem.uri.host
            val sourceHint = dataMap.getString(WearDataLayerContracts.KEY_SOURCE).orEmpty()
            handleLessonSync(payload = payload, sourceNodeId = sourceNodeId, sourceHint = sourceHint)
        }
    }

    private fun handleLessonSync(payload: String, sourceNodeId: String?, sourceHint: String?) {
        if (payload.isBlank()) return

        val parsed = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val incomingRevision = parsed.optLong(
            WearDataLayerContracts.KEY_REVISION,
            parsed.optLong(WearDataLayerContracts.KEY_UPDATED_AT, 0L),
        ).takeIf { it > 0L } ?: System.currentTimeMillis()
        val incomingAppliedAt = parsed.optLong(
            WearDataLayerContracts.KEY_UPDATED_AT,
            incomingRevision,
        ).takeIf { it > 0L } ?: incomingRevision
        val lessonCount = parsed.optJSONArray("lessons")?.length() ?: 0
        val requestId = parsed.optString(WearDataLayerContracts.KEY_REQUEST_ID)
        if (requestId.isNotBlank() && !claimRequest(requestId)) {
            Log.i(TAG, "Ignore duplicate mobile sync requestId=$requestId")
            return
        }
        val source = SyncSource.fromWire(parsed.optString(WearDataLayerContracts.KEY_SOURCE))
            .takeUnless { it == SyncSource.UNKNOWN }
            ?: SyncSource.fromWire(sourceHint)
                .takeUnless { it == SyncSource.UNKNOWN }
            ?: SyncSource.PHONE_DIRECT
        val ackSource = parsed.optString(WearDataLayerContracts.KEY_SOURCE)
            .ifBlank { sourceHint.orEmpty() }
            .ifBlank { WearDataLayerContracts.SOURCE_WEARABLE_API }
        val incomingStamp = SyncStamp(
            revision = incomingRevision,
            source = source,
            appliedAt = incomingAppliedAt,
        )
        val currentStamp = WearSyncStampStore.load(applicationContext, SyncDomain.TIMETABLE)
        if (!SyncArbitrator.shouldApply(
                domain = SyncDomain.TIMETABLE,
                incoming = incomingStamp,
                current = currentStamp,
            )
        ) {
            val reason = "stale_skipped: incoming=$incomingStamp current=$currentStamp"
            if (requestId.isNotBlank()) markRequestHandled(requestId)
            persistApplyStatus(
                success = true,
                lessonCount = lessonCount,
                decision = "stale_skipped",
                reason = reason,
            )
            WearSyncStampStore.saveDecision(
                context = applicationContext,
                domain = SyncDomain.TIMETABLE,
                decision = "stale_skipped",
                reason = reason,
            )
            sendSyncAckToMobile(
                sourceNodeId = sourceNodeId,
                requestedLessonCount = lessonCount,
                result = ApplyResult(
                    success = true,
                    appliedLessonCount = 0,
                    errorMessage = "stale_skipped",
                ),
                source = ackSource,
                requestId = requestId,
            )
            Log.i(TAG, "Skip stale mobile sync payload: $reason")
            return
        }

        serviceScope.launch {
            WearTimetableApplyLock.mutex.withLock {
            val latestStamp = WearSyncStampStore.load(applicationContext, SyncDomain.TIMETABLE)
            if (!SyncArbitrator.shouldApply(SyncDomain.TIMETABLE, incomingStamp, latestStamp)) {
                val reason = "stale_skipped_after_queue: incoming=$incomingStamp current=$latestStamp"
                if (requestId.isNotBlank()) markRequestHandled(requestId)
                persistApplyStatus(true, lessonCount, "stale_skipped", reason)
                sendSyncAckToMobile(
                    sourceNodeId,
                    lessonCount,
                    ApplyResult(true, 0, "stale_skipped"),
                    ackSource,
                    requestId,
                )
                return@withLock
            }
            val result = applyPayloadToWearDb(parsed)
            persistApplyStatus(
                success = result.success,
                lessonCount = lessonCount,
                decision = if (result.success) "applied" else "apply_failed",
                reason = result.errorMessage.orEmpty(),
                payload = payload,
            )
            if (result.success) {
                WearSyncStampStore.save(applicationContext, SyncDomain.TIMETABLE, incomingStamp)
                WearSyncStampStore.saveDecision(
                    context = applicationContext,
                    domain = SyncDomain.TIMETABLE,
                    decision = "applied",
                    reason = "applied revision=${incomingStamp.revision} source=${incomingStamp.source.wireValue}",
                )
                val app = applicationContext as? ClassingTimetableApplication
                val pref = app?.appContainer?.settingsRepository?.observePreferences()
                val current = pref?.firstOrNull()
                if (current != null) {
                    com.classing.wear.timetable.worker.WearReminderAlarmScheduler.refresh(
                        context = applicationContext,
                        enabled = current.remindersEnabled,
                        level = current.keepAliveLevel,
                    )
                }
            } else if (parsed.optString(WearDataLayerContracts.KEY_SYNC_MODE).equals("INCREMENTAL", ignoreCase = true)) {
                requestFullRecovery(requestId)
            }

            if (requestId.isNotBlank()) markRequestHandled(requestId)

            WearSurfaceUpdateRequester.requestAll(applicationContext)
            sendSyncAckToMobile(sourceNodeId, lessonCount, result, ackSource, requestId)
            Log.i(TAG, "Received mobile sync payload with $lessonCount lessons, applied=${result.success}")
            }
        }
    }

    private suspend fun applyPayloadToWearDb(root: JSONObject): ApplyResult {
        val lessons = root.optJSONArray("lessons")
            ?: return ApplyResult(success = false, appliedLessonCount = 0, errorMessage = "Missing lessons array")

        val weekNumberMode = WeekNumberMode.entries.firstOrNull {
            it.name == root.optString("weekNumberMode").uppercase()
        } ?: WeekNumberMode.NATURAL
        val semesterWeekStartDate = runCatching {
            LocalDate.parse(root.optString("semesterWeekStartDate"))
        }.getOrNull()

        val today = LocalDate.now()
        val isoWeekStart = LocalDate.of(today.get(WeekFields.ISO.weekBasedYear()), 1, 4)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val semesterStart = when (weekNumberMode) {
            WeekNumberMode.SEMESTER -> semesterWeekStartDate ?: today
            WeekNumberMode.NATURAL -> isoWeekStart
        }
        val semesterTotalWeeks = 520

        val semesterRemoteId = when (weekNumberMode) {
            WeekNumberMode.SEMESTER -> "mobile-sync-semester-semester"
            WeekNumberMode.NATURAL -> "mobile-sync-semester-natural"
        }

        val semester = RemoteSemester(
            remoteId = semesterRemoteId,
            name = "Mobile Synced (${weekNumberMode.name})",
            startDate = semesterStart,
            endDate = semesterStart.plusWeeks(semesterTotalWeeks.toLong()).minusDays(1),
            totalWeeks = semesterTotalWeeks,
            isActive = true,
            version = System.currentTimeMillis(),
        )

        val slotMap = linkedMapOf<String, RemoteTimeSlot>()
        val courses = mutableListOf<RemoteCourse>()
        val sessions = mutableListOf<RemoteSession>()
        val exceptions = mutableListOf<RemoteException>()
        val payloadVersion = root.optLong(WearDataLayerContracts.KEY_REVISION, System.currentTimeMillis())
        val syncMode = if (root.optString(WearDataLayerContracts.KEY_SYNC_MODE).equals("INCREMENTAL", ignoreCase = true)) {
            SyncMode.DELTA
        } else {
            SyncMode.FULL
        }

        for (index in 0 until lessons.length()) {
            val item = lessons.optJSONObject(index) ?: continue
            val title = item.optString("title").ifBlank { "Course-${index + 1}" }
            val dayOfWeek = item.optInt("dayOfWeek", 1).coerceIn(1, 7)
            val start = parseTime(item.optString("startTime")) ?: continue
            val end = parseTime(item.optString("endTime")) ?: continue
            if (!end.isAfter(start)) continue

            val slotKey = "${start}-${end}"
            val slotRemoteId = "mobile-slot-$slotKey"
            val slot = slotMap.getOrPut(slotKey) {
                RemoteTimeSlot(
                    remoteId = slotRemoteId,
                    semesterRemoteId = semesterRemoteId,
                    indexInDay = slotMap.size + 1,
                    label = "${start}-${end}",
                    startTime = start,
                    endTime = end,
                    version = payloadVersion,
                )
            }

            val stableLessonId = item.optString("id").ifBlank { "legacy-$index" }
            val courseRemoteId = "mobile-course-$stableLessonId"
            val startWeek = item.optInt("startWeek", MIN_SCHEDULE_WEEK)
                .coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK)
            val endWeek = item.optInt("endWeek", MAX_SCHEDULE_WEEK)
                .coerceIn(startWeek, MAX_SCHEDULE_WEEK)
            val weekParity = parseWeekParity(item.optString("weekParity", "ALL"))
            courses += RemoteCourse(
                remoteId = courseRemoteId,
                semesterRemoteId = semesterRemoteId,
                name = title,
                teacher = item.optString("teacher"),
                classroom = item.optString("location"),
                note = item.optString("note"),
                colorLabel = "teal",
                isFavorite = false,
                version = payloadVersion,
            )

            sessions += RemoteSession(
                remoteId = "mobile-session-$stableLessonId",
                semesterRemoteId = semesterRemoteId,
                courseRemoteId = courseRemoteId,
                dayOfWeek = dayOfWeek,
                timeSlotRemoteId = slot.remoteId,
                startWeek = startWeek,
                endWeek = endWeek,
                weekParity = weekParity,
                version = payloadVersion,
            )
        }

        val exceptionItems = root.optJSONArray("exceptions")
        if (exceptionItems != null) {
            for (index in 0 until exceptionItems.length()) {
                val item = exceptionItems.optJSONObject(index) ?: continue
                val exceptionId = item.optString("id")
                if (exceptionId.isBlank()) continue
                val type = item.optString("type").uppercase()
                val date = runCatching { LocalDate.parse(item.optString("date")) }.getOrNull() ?: continue
                val lessonId = item.optNullableString("lessonId")
                var syntheticCourseId: String? = null
                var syntheticSlotId: String? = null
                if (type == "MAKE_UP" || type == "RESCHEDULE") {
                    val startMinute = item.optInt("startMinute", -1)
                    val endMinute = item.optInt("endMinute", -1)
                    if (startMinute >= 0 && endMinute > startMinute) {
                        val start = LocalTime.of(startMinute / 60, startMinute % 60)
                        val end = LocalTime.of(endMinute / 60, endMinute % 60)
                        syntheticSlotId = "mobile-exception-slot-$exceptionId"
                        slotMap[syntheticSlotId] = RemoteTimeSlot(
                            remoteId = syntheticSlotId,
                            semesterRemoteId = semesterRemoteId,
                            indexInDay = slotMap.size + 1,
                            label = "$start-$end",
                            startTime = start,
                            endTime = end,
                            version = payloadVersion,
                        )
                        syntheticCourseId = "mobile-exception-course-$exceptionId"
                        courses += RemoteCourse(
                            remoteId = syntheticCourseId,
                            semesterRemoteId = semesterRemoteId,
                            name = item.optNullableString("title").orEmpty().ifBlank { "Adjusted course" },
                            teacher = item.optNullableString("teacher").orEmpty(),
                            classroom = item.optNullableString("location").orEmpty(),
                            note = item.optNullableString("note").orEmpty(),
                            colorLabel = "teal",
                            isFavorite = false,
                            version = payloadVersion,
                        )
                    }
                }

                exceptions += RemoteException(
                    remoteId = "mobile-exception-$exceptionId",
                    semesterRemoteId = semesterRemoteId,
                    sessionRemoteId = lessonId?.let { "mobile-session-$it" },
                    exceptionType = type,
                    date = date,
                    reason = item.optNullableString("note").orEmpty(),
                    courseRemoteId = if (type == "MAKE_UP") syntheticCourseId else null,
                    timeSlotRemoteId = if (type == "MAKE_UP") syntheticSlotId else null,
                    dayOfWeek = item.optInt("dayOfWeek", date.dayOfWeek.value).coerceIn(1, 7),
                    newCourseRemoteId = if (type == "RESCHEDULE") syntheticCourseId else null,
                    newTimeSlotRemoteId = if (type == "RESCHEDULE") syntheticSlotId else null,
                    version = payloadVersion,
                )
            }
        }

        val payload = RemoteSchedulePayload(
            dataVersion = payloadVersion,
            semesters = listOf(semester),
            timeSlots = slotMap.values.toList(),
            courses = courses,
            sessions = sessions,
            exceptions = exceptions,
            deletedCourseRemoteIds = root.stringSet("deletedLessonIds") { "mobile-course-$it" },
            deletedSessionRemoteIds = root.stringSet("deletedLessonIds") { "mobile-session-$it" },
            deletedExceptionRemoteIds = root.stringSet("deletedExceptionIds") { "mobile-exception-$it" },
        )

        val application = applicationContext as? ClassingTimetableApplication
            ?: return ApplyResult(success = false, appliedLessonCount = 0, errorMessage = "Application container unavailable")
        val applier = SyncPayloadApplier(application.appContainer.database)
        return runCatching {
            applier.apply(payload, syncMode)
            ApplyResult(success = true, appliedLessonCount = courses.size)
        }.getOrElse { error ->
            ApplyResult(success = false, appliedLessonCount = courses.size, errorMessage = error.message ?: "Unknown apply error")
        }
    }

    private fun requestFullRecovery(originalRequestId: String) {
        val prefs = getSharedPreferences(RECOVERY_PREFS, Context.MODE_PRIVATE)
        if (originalRequestId.isNotBlank() && prefs.getString(KEY_LAST_RECOVERY_FOR, null) == originalRequestId) return
        prefs.edit().putString(KEY_LAST_RECOVERY_FOR, originalRequestId).apply()
        val requestId = java.util.UUID.randomUUID().toString()
        val requestedAt = System.currentTimeMillis()
        val payload = JSONObject()
            .put(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
            .put(WearDataLayerContracts.KEY_REQUESTED_AT, requestedAt)
            .put("forceFull", true)
            .toString()
        val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_REQUEST).apply {
            dataMap.putString(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
            dataMap.putLong(WearDataLayerContracts.KEY_REQUESTED_AT, requestedAt)
            dataMap.putBoolean("forceFull", true)
            dataMap.putString(WearDataLayerContracts.KEY_REQUEST_PAYLOAD, payload)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(request)
    }

    private fun claimRequest(requestId: String): Boolean = synchronized(requestClaimLock) {
        val alreadyHandled = getSharedPreferences(REQUEST_DEDUPE_PREFS, Context.MODE_PRIVATE).contains(requestId)
        !alreadyHandled && requestIdsInFlight.add(requestId)
    }

    private fun markRequestHandled(requestId: String) {
        val prefs = getSharedPreferences(REQUEST_DEDUPE_PREFS, Context.MODE_PRIVATE)
        val cutoff = System.currentTimeMillis() - REQUEST_DEDUPE_TTL_MS
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if ((value as? Long ?: 0L) < cutoff) editor.remove(key)
        }
        editor.putLong(requestId, System.currentTimeMillis()).commit()
        synchronized(requestClaimLock) { requestIdsInFlight.remove(requestId) }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.stringSet(key: String, transform: (String) -> String): Set<String> {
        val values = optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (index in 0 until values.length()) {
                values.optString(index).takeIf { it.isNotBlank() }?.let { add(transform(it)) }
            }
        }
    }

    private fun sendSyncAckToMobile(
        sourceNodeId: String?,
        requestedLessonCount: Int,
        result: ApplyResult,
        source: String,
        requestId: String,
    ) {
        val syncedAt = System.currentTimeMillis()

        val dataRequest = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_ACK).apply {
            dataMap.putBoolean(WearDataLayerContracts.KEY_SUCCESS, result.success)
            dataMap.putInt(WearDataLayerContracts.KEY_REQUESTED_LESSON_COUNT, requestedLessonCount)
            dataMap.putInt(WearDataLayerContracts.KEY_APPLIED_LESSON_COUNT, result.appliedLessonCount)
            dataMap.putLong(WearDataLayerContracts.KEY_SYNCED_AT, syncedAt)
            dataMap.putString(WearDataLayerContracts.KEY_SOURCE, source)
            dataMap.putString(WearDataLayerContracts.KEY_ERROR, result.errorMessage.orEmpty())
            dataMap.putString(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
            dataMap.putString(WearDataLayerContracts.KEY_ACK_STATUS, when {
                !result.success -> "failed"
                result.errorMessage == "stale_skipped" -> "stale"
                else -> "applied"
            })
            dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, syncedAt)
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(this)
            .putDataItem(dataRequest)
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to publish ACK DataItem: ${error.message}")
            }

        if (!sourceNodeId.isNullOrBlank()) {
            val ack = JSONObject()
                .put(WearDataLayerContracts.KEY_SUCCESS, result.success)
                .put(WearDataLayerContracts.KEY_REQUESTED_LESSON_COUNT, requestedLessonCount)
                .put(WearDataLayerContracts.KEY_APPLIED_LESSON_COUNT, result.appliedLessonCount)
                .put(WearDataLayerContracts.KEY_SYNCED_AT, syncedAt)
                .put(WearDataLayerContracts.KEY_SOURCE, source)
                .put(WearDataLayerContracts.KEY_ERROR, result.errorMessage.orEmpty())
                .put(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
                .put(WearDataLayerContracts.KEY_ACK_STATUS, when {
                    !result.success -> "failed"
                    result.errorMessage == "stale_skipped" -> "stale"
                    else -> "applied"
                })
                .toString()
                .toByteArray(StandardCharsets.UTF_8)

            Wearable.getMessageClient(this)
                .sendMessage(sourceNodeId, WearDataLayerContracts.PATH_SYNC_ACK, ack)
                .addOnSuccessListener {
                    Log.i(TAG, "Sent sync ACK message to mobile node=$sourceNodeId")
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Failed to send sync ACK message: ${error.message}")
                }
        }
    }

    private fun parseTime(raw: String): LocalTime? {
        val text = raw.trim()
        if (text.isBlank()) return null
        return runCatching { LocalTime.parse(text) }.getOrNull()
            ?: runCatching { LocalTime.parse(text, java.time.format.DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
    }

    private fun parseWeekParity(raw: String): String {
        return when (raw.trim().uppercase()) {
            "ODD" -> "ODD"
            "EVEN" -> "EVEN"
            else -> "ALL"
        }
    }

    private fun persistApplyStatus(
        success: Boolean,
        lessonCount: Int,
        decision: String,
        reason: String,
        payload: String = "",
    ) {
        val now = System.currentTimeMillis()
        getSharedPreferences(MobileSyncPrefs.PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(MobileSyncPrefs.KEY_LAST_PAYLOAD, payload)
            .putInt(MobileSyncPrefs.KEY_LAST_LESSON_COUNT, lessonCount)
            .putLong(MobileSyncPrefs.KEY_LAST_SYNC_AT, now)
            .putBoolean(MobileSyncPrefs.KEY_LAST_APPLY_SUCCESS, success)
            .putString(MobileSyncPrefs.KEY_LAST_DECISION, decision)
            .putString(MobileSyncPrefs.KEY_LAST_DECISION_REASON, reason)
            .apply()
    }

    companion object {
        private const val TAG = "MobileSyncListener"
        private const val RECOVERY_PREFS = "wear_sync_full_recovery"
        private const val KEY_LAST_RECOVERY_FOR = "last_recovery_for"
        private const val REQUEST_DEDUPE_PREFS = "wear_sync_request_dedupe"
        private const val REQUEST_DEDUPE_TTL_MS = 24 * 60 * 60 * 1000L
        private val requestClaimLock = Any()
        private val requestIdsInFlight = mutableSetOf<String>()
    }
}
