package com.xtawa.classingtime.sync

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.xtawa.classingtime.data.PersistedLesson
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class WearSyncDispatchResult(
    val connectedNodeCount: Int,
    val queuedForCompanion: Boolean,
)

object WearDataLayerSyncPublisher {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun publishLessonsSnapshot(
        context: Context,
        lessons: List<PersistedLesson>,
        zoneId: ZoneId,
        source: String = WearDataLayerContracts.SOURCE_WEARABLE_API,
        allowDisconnectedQueue: Boolean = false,
        weekNumberMode: String = "NATURAL",
        semesterWeekStartDate: LocalDate = LocalDate.now(),
    ): Result<WearSyncDispatchResult> {
        return runCatching {
            val connectedNodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (connectedNodes.isEmpty() && !allowDisconnectedQueue) {
                error("No connected Wear device")
            }

            val updatedAt = System.currentTimeMillis()
            val payload = buildPayload(
                lessons = lessons,
                zoneId = zoneId,
                source = source,
                updatedAt = updatedAt,
                weekNumberMode = weekNumberMode,
                semesterWeekStartDate = semesterWeekStartDate,
            )
            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_LESSONS).apply {
                dataMap.putString(WearDataLayerContracts.KEY_PAYLOAD, payload)
                dataMap.putString(WearDataLayerContracts.KEY_FORMAT, "classingtime_mobile_sync_v1")
                dataMap.putString(WearDataLayerContracts.KEY_TIMEZONE, zoneId.id)
                dataMap.putString(WearDataLayerContracts.KEY_SOURCE, source)
                dataMap.putString(
                    WearDataLayerContracts.KEY_GENERATED_AT,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                )
                // Force DataItem version bump so Wear always receives a changed event.
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()

            val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
            val messageNodeCount = connectedNodes.count { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WearDataLayerContracts.PATH_SYNC_LESSONS, payloadBytes)
                        .await()
                    true
                }.getOrDefault(false)
            }

            WearSyncDispatchResult(
                connectedNodeCount = messageNodeCount,
                queuedForCompanion = allowDisconnectedQueue && messageNodeCount == 0,
            )
        }
    }

    private fun buildPayload(
        lessons: List<PersistedLesson>,
        zoneId: ZoneId,
        source: String,
        updatedAt: Long,
        weekNumberMode: String,
        semesterWeekStartDate: LocalDate,
    ): String {
        val arr = JSONArray()
        lessons.sortedWith(compareBy<PersistedLesson> { it.dayOfWeek }.thenBy { it.startMinute }).forEach { lesson ->
            val safeStart = lesson.startMinute.coerceIn(0, 23 * 60 + 59)
            val safeEnd = lesson.endMinute.coerceAtLeast(safeStart + 1).coerceAtMost(23 * 60 + 59)
            val start = LocalTime.of(safeStart / 60, safeStart % 60)
            val end = LocalTime.of(safeEnd / 60, safeEnd % 60)
            arr.put(
                JSONObject()
                    .put("id", lesson.id)
                    .put("title", lesson.title)
                    .put("dayOfWeek", lesson.dayOfWeek)
                    .put("startTime", start.format(timeFormatter))
                    .put("endTime", end.format(timeFormatter))
                    .put("location", lesson.location ?: "")
                    .put("note", lesson.note ?: ""),
            )
        }

        return JSONObject()
            .put("format", "classingtime_mobile_sync_v1")
            .put("timezone", zoneId.id)
            .put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .put("source", source)
            .put("updatedAt", updatedAt)
            .put("weekNumberMode", weekNumberMode)
            .put("semesterWeekStartDate", semesterWeekStartDate.toString())
            .put("lessons", arr)
            .toString()
    }
}

