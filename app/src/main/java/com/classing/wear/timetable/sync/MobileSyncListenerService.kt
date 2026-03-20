package com.classing.wear.timetable.sync

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.launch
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
        if (messageEvent.path != WearDataLayerContracts.PATH_SYNC_LESSONS) {
            super.onMessageReceived(messageEvent)
            return
        }

        val payload = runCatching { String(messageEvent.data, StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        handleLessonSync(payload = payload, sourceNodeId = messageEvent.sourceNodeId, sourceHint = null)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
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
        val updatedAt = parsed.optLong(WearDataLayerContracts.KEY_UPDATED_AT, 0L)
        if (updatedAt > 0L && isDuplicatePayload(updatedAt)) {
            Log.i(TAG, "Ignore duplicated mobile sync payload updatedAt=$updatedAt")
            return
        }
        if (updatedAt > 0L) markHandledPayload(updatedAt)

        val lessonCount = parsed.optJSONArray("lessons")?.length() ?: 0
        val source = parsed.optString(WearDataLayerContracts.KEY_SOURCE)
            .ifBlank { sourceHint.orEmpty() }
            .ifBlank { WearDataLayerContracts.SOURCE_WEARABLE_API }

        serviceScope.launch {
            val result = applyPayloadToWearDb(parsed)
            getSharedPreferences(MobileSyncPrefs.PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(MobileSyncPrefs.KEY_LAST_PAYLOAD, payload)
                .putInt(MobileSyncPrefs.KEY_LAST_LESSON_COUNT, lessonCount)
                .putLong(MobileSyncPrefs.KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .putBoolean(MobileSyncPrefs.KEY_LAST_APPLY_SUCCESS, result.success)
                .apply()

            WearSurfaceUpdateRequester.requestAll(applicationContext)
            sendSyncAckToMobile(sourceNodeId, lessonCount, result, source)
            Log.i(TAG, "Received mobile sync payload with $lessonCount lessons, applied=${result.success}")
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
                    version = System.currentTimeMillis(),
                )
            }

            val courseRemoteId = "mobile-course-$index"
            courses += RemoteCourse(
                remoteId = courseRemoteId,
                semesterRemoteId = semesterRemoteId,
                name = title,
                teacher = "",
                classroom = item.optString("location"),
                note = item.optString("note"),
                colorLabel = "teal",
                isFavorite = false,
                version = System.currentTimeMillis(),
            )

            sessions += RemoteSession(
                remoteId = "mobile-session-$index",
                semesterRemoteId = semesterRemoteId,
                courseRemoteId = courseRemoteId,
                dayOfWeek = dayOfWeek,
                timeSlotRemoteId = slot.remoteId,
                startWeek = 1,
                endWeek = semesterTotalWeeks,
                weekParity = "ALL",
                version = System.currentTimeMillis(),
            )
        }

        val payload = RemoteSchedulePayload(
            dataVersion = System.currentTimeMillis(),
            semesters = listOf(semester),
            timeSlots = slotMap.values.toList(),
            courses = courses,
            sessions = sessions,
            exceptions = emptyList<RemoteException>(),
        )

        val application = applicationContext as? ClassingTimetableApplication
            ?: return ApplyResult(success = false, appliedLessonCount = 0, errorMessage = "Application container unavailable")
        val applier = SyncPayloadApplier(application.appContainer.database)
        return runCatching {
            applier.apply(payload, SyncMode.FULL)
            ApplyResult(success = true, appliedLessonCount = courses.size)
        }.getOrElse { error ->
            ApplyResult(success = false, appliedLessonCount = courses.size, errorMessage = error.message ?: "Unknown apply error")
        }
    }

    private fun sendSyncAckToMobile(
        sourceNodeId: String?,
        requestedLessonCount: Int,
        result: ApplyResult,
        source: String,
    ) {
        val syncedAt = System.currentTimeMillis()

        val dataRequest = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_ACK).apply {
            dataMap.putBoolean(WearDataLayerContracts.KEY_SUCCESS, result.success)
            dataMap.putInt(WearDataLayerContracts.KEY_REQUESTED_LESSON_COUNT, requestedLessonCount)
            dataMap.putInt(WearDataLayerContracts.KEY_APPLIED_LESSON_COUNT, result.appliedLessonCount)
            dataMap.putLong(WearDataLayerContracts.KEY_SYNCED_AT, syncedAt)
            dataMap.putString(WearDataLayerContracts.KEY_SOURCE, source)
            dataMap.putString(WearDataLayerContracts.KEY_ERROR, result.errorMessage.orEmpty())
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

    private fun isDuplicatePayload(updatedAt: Long): Boolean {
        return getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(MobileSyncPrefs.KEY_LAST_HANDLED_UPDATED_AT, 0L) == updatedAt
    }

    private fun markHandledPayload(updatedAt: Long) {
        getSharedPreferences(MobileSyncPrefs.PREF_NAME, Context.MODE_PRIVATE).edit()
            .putLong(MobileSyncPrefs.KEY_LAST_HANDLED_UPDATED_AT, updatedAt)
            .apply()
    }

    companion object {
        private const val TAG = "MobileSyncListener"
        private const val PREF_NAME = MobileSyncPrefs.PREF_NAME
    }
}
