package com.classing.wear.timetable.sync

import android.content.Context
import android.util.Log
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
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class MobileSyncListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class ApplyResult(
        val success: Boolean,
        val appliedLessonCount: Int,
        val errorMessage: String? = null,
    )

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_SYNC_LESSONS -> handleLessonSync(messageEvent)
            else -> super.onMessageReceived(messageEvent)
        }
    }

    private fun handleLessonSync(messageEvent: MessageEvent) {
        val bytes = messageEvent.data
        val payload = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        if (payload.isBlank()) return

        val parsed = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val lessonCount = parsed.optJSONArray("lessons")?.length() ?: 0
        val sourceNodeId = messageEvent.sourceNodeId

        serviceScope.launch {
            val result = applyPayloadToWearDb(parsed)
            getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_PAYLOAD, payload)
                .putInt(KEY_LAST_LESSON_COUNT, lessonCount)
                .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .putBoolean(KEY_LAST_APPLY_SUCCESS, result.success)
                .apply()
            WearSurfaceUpdateRequester.requestAll(applicationContext)
            sendSyncAckToMobile(sourceNodeId, lessonCount, result)
            Log.i(TAG, "Received mobile sync payload with $lessonCount lessons, applied=${result.success}")
        }
    }

    private suspend fun applyPayloadToWearDb(root: JSONObject): ApplyResult {
        val lessons = root.optJSONArray("lessons")
            ?: return ApplyResult(success = false, appliedLessonCount = 0, errorMessage = "Missing lessons array")
        if (lessons.length() == 0) return ApplyResult(success = false, appliedLessonCount = 0, errorMessage = "No lessons in payload")

        val semesterRemoteId = "mobile-sync-semester"
        val semester = RemoteSemester(
            remoteId = semesterRemoteId,
            name = "Mobile Synced",
            startDate = LocalDate.now().minusWeeks(1),
            endDate = LocalDate.now().plusWeeks(20),
            totalWeeks = 21,
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

            val week = LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear()).coerceAtLeast(1)
            sessions += RemoteSession(
                remoteId = "mobile-session-$index",
                semesterRemoteId = semesterRemoteId,
                courseRemoteId = courseRemoteId,
                dayOfWeek = dayOfWeek,
                timeSlotRemoteId = slot.remoteId,
                startWeek = week,
                endWeek = week + 20,
                weekParity = "ALL",
                version = System.currentTimeMillis(),
            )
        }

        if (courses.isEmpty() || sessions.isEmpty()) {
            return ApplyResult(success = false, appliedLessonCount = 0, errorMessage = "No valid lessons to apply")
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
        nodeId: String,
        requestedLessonCount: Int,
        result: ApplyResult,
    ) {
        val ack = JSONObject()
            .put("success", result.success)
            .put("requestedLessonCount", requestedLessonCount)
            .put("appliedLessonCount", result.appliedLessonCount)
            .put("syncedAt", System.currentTimeMillis())
            .put("source", "WEARABLE_API")
            .put("error", result.errorMessage.orEmpty())
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        Wearable.getMessageClient(this)
            .sendMessage(nodeId, PATH_SYNC_ACK, ack)
            .addOnSuccessListener {
                Log.i(TAG, "Sent sync ACK to mobile node=$nodeId")
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to send sync ACK: ${error.message}")
            }
    }

    private fun parseTime(raw: String): LocalTime? {
        val text = raw.trim()
        if (text.isBlank()) return null
        return runCatching { LocalTime.parse(text) }.getOrNull()
            ?: runCatching { LocalTime.parse(text, java.time.format.DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
    }

    companion object {
        private const val TAG = "MobileSyncListener"
        const val PATH_SYNC_LESSONS = "/classing/mobile_sync_lessons"
        const val PATH_SYNC_ACK = "/classing/wear_sync_ack"

        private const val PREF_NAME = "wear_mobile_sync"
        private const val KEY_LAST_PAYLOAD = "last_payload"
        private const val KEY_LAST_LESSON_COUNT = "last_lesson_count"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_APPLY_SUCCESS = "last_apply_success"
    }
}
