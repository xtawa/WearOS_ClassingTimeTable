package com.xtawa.classingtime.sync

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.xtawa.classingtime.data.PersistedLesson
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

object WearDataLayerSyncPublisher {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun publishLessonsSnapshot(
        context: Context,
        lessons: List<PersistedLesson>,
        zoneId: ZoneId,
    ): Result<Int> {
        return runCatching {
            val connectedNodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (connectedNodes.isEmpty()) error("No connected Wear device")

            val payload = buildPayload(lessons, zoneId)
            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_LESSONS).apply {
                dataMap.putString(WearDataLayerContracts.KEY_PAYLOAD, payload)
                dataMap.putString(WearDataLayerContracts.KEY_FORMAT, "classingtime_mobile_sync_v1")
                dataMap.putString(WearDataLayerContracts.KEY_TIMEZONE, zoneId.id)
                dataMap.putString(
                    WearDataLayerContracts.KEY_GENERATED_AT,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                )
                // Force DataItem version bump so Wear always receives a changed event.
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            connectedNodes.size
        }
    }

    private fun buildPayload(
        lessons: List<PersistedLesson>,
        zoneId: ZoneId,
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
            .put("lessons", arr)
            .toString()
    }
}

