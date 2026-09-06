package com.xtawa.classingtime.sync

import android.content.Context
import com.classing.shared.model.MAX_SCHEDULE_WEEK
import com.classing.shared.model.MIN_SCHEDULE_WEEK
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.xtawa.classingtime.data.MobilePrefsStore
import com.xtawa.classingtime.data.PersistedLesson
import com.xtawa.classingtime.data.PersistedScheduleException
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class WearSyncDispatchResult(
    val connectedNodeCount: Int,
    val queuedForCompanion: Boolean,
)

object WearDataLayerSyncPublisher {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val baselineLock = Any()

    suspend fun publishLessonsSnapshot(
        context: Context,
        lessons: List<PersistedLesson>,
        zoneId: ZoneId,
        source: String = WearDataLayerContracts.SOURCE_WEARABLE_API,
        allowDisconnectedQueue: Boolean = false,
        weekNumberMode: String = "NATURAL",
        semesterWeekStartDate: LocalDate = LocalDate.now(),
        exceptions: List<PersistedScheduleException> = MobilePrefsStore.loadScheduleExceptions(context),
        requestId: String = UUID.randomUUID().toString(),
        forceFull: Boolean = false,
    ): Result<WearSyncDispatchResult> {
        return runCatching {
            val connectedNodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (connectedNodes.isEmpty() && !allowDisconnectedQueue) {
                error("No connected Wear device")
            }

            val connectedNodeIds = connectedNodes.mapTo(linkedSetOf()) { it.id }
            val storedBaseline = loadBaseline(context)
            val previousBaseline = selectWearSyncBaseline(
                connectedNodeIds = connectedNodeIds,
                stored = storedBaseline,
                forceFull = forceFull,
            )
            val plan = planWearSyncPayload(
                lessons = lessons,
                exceptions = exceptions,
                previous = previousBaseline,
                forceFull = forceFull,
            )
            val updatedAt = reserveRevision(context)
            val payload = buildPayload(
                plan = plan,
                zoneId = zoneId,
                source = source,
                updatedAt = updatedAt,
                weekNumberMode = weekNumberMode,
                semesterWeekStartDate = semesterWeekStartDate,
                requestId = requestId,
            )

            // Stage before transport so an extremely fast Wear ACK cannot race ahead of pending
            // state creation. Pending state is harmless if transport fails: it is request-scoped,
            // never selected for future deltas, and expires automatically.
            if (connectedNodeIds.size == 1) {
                savePendingBaseline(
                    context = context,
                    requestId = requestId,
                    nodeId = connectedNodeIds.single(),
                    baseline = plan.nextBaseline,
                    revision = updatedAt,
                )
            }

            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_LESSONS).apply {
                dataMap.putString(WearDataLayerContracts.KEY_PAYLOAD, payload)
                dataMap.putString(WearDataLayerContracts.KEY_FORMAT, "classingtime_mobile_sync_v2")
                dataMap.putString(WearDataLayerContracts.KEY_TIMEZONE, zoneId.id)
                dataMap.putString(WearDataLayerContracts.KEY_SOURCE, source)
                dataMap.putLong(WearDataLayerContracts.KEY_REVISION, updatedAt)
                dataMap.putString(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
                dataMap.putString(WearDataLayerContracts.KEY_SYNC_MODE, plan.mode)
                dataMap.putString(
                    WearDataLayerContracts.KEY_GENERATED_AT,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                )
                // Force DataItem version bump so Wear always receives a changed event.
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()

            val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
            val successfulNodeIds = linkedSetOf<String>()
            connectedNodes.forEach { node ->
                val sent = runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WearDataLayerContracts.PATH_SYNC_LESSONS, payloadBytes)
                        .await()
                    true
                }.getOrDefault(false)
                if (sent) successfulNodeIds += node.id
            }

            WearSyncDispatchResult(
                connectedNodeCount = successfulNodeIds.size,
                queuedForCompanion = allowDisconnectedQueue && successfulNodeIds.isEmpty(),
            ).also { dispatch ->
                val resultLabel = if (dispatch.connectedNodeCount > 0) {
                    "Pushed to ${dispatch.connectedNodeCount} node(s)"
                } else if (dispatch.queuedForCompanion) {
                    "Queued for companion delivery"
                } else {
                    "No connected Wear device"
                }
                MobilePrefsStore.markLastWearPush(context, updatedAt, resultLabel)
            }
        }
    }

    fun confirmBaselineFromAck(context: Context, requestId: String, nodeId: String): Boolean {
        if (requestId.isBlank() || nodeId.isBlank()) return false
        return synchronized(baselineLock) {
            val prefs = context.getSharedPreferences(BASELINE_PREFS, Context.MODE_PRIVATE)
            val key = pendingKey(requestId)
            val raw = prefs.getString(key, null)?.takeIf(String::isNotBlank) ?: return@synchronized false
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@synchronized false
            if (root.optString("nodeId") != nodeId) return@synchronized false
            val baseline = baselineFromJson(root.optJSONObject("baseline")) ?: return@synchronized false
            prefs.edit()
                .putString(KEY_BASELINE_NODE_ID, nodeId)
                .putString(KEY_BASELINE, baselineToJson(baseline).toString())
                .remove(key)
                .commit()
        }
    }

    suspend fun publishWearSettingsSnapshot(context: Context, payload: String, revision: Long): Result<Int> {
        return runCatching {
            val updatedAt = System.currentTimeMillis()
            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_APPLY_WEAR_SETTINGS).apply {
                dataMap.putString(WearDataLayerContracts.KEY_SETTINGS_PAYLOAD, payload)
                dataMap.putLong(WearDataLayerContracts.KEY_REVISION, revision)
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
                dataMap.putString(WearDataLayerContracts.KEY_SOURCE, WearDataLayerContracts.SOURCE_CLOUD_SYNC)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            Wearable.getNodeClient(context).connectedNodes.await().count { node ->
                runCatching {
                    Wearable.getMessageClient(context).sendMessage(
                        node.id,
                        WearDataLayerContracts.PATH_APPLY_WEAR_SETTINGS,
                        bytes,
                    ).await()
                    true
                }.getOrDefault(false)
            }
        }
    }

    suspend fun publishCloudSnapshot(context: Context, payload: String): Result<Int> {
        return runCatching {
            val updatedAt = System.currentTimeMillis()
            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_CLOUD_CONFIG).apply {
                dataMap.putString(WearDataLayerContracts.KEY_WEAR_CLOUD_SNAPSHOT, payload)
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
                dataMap.putLong(WearDataLayerContracts.KEY_REVISION, updatedAt)
                dataMap.putString(WearDataLayerContracts.KEY_SOURCE, WearDataLayerContracts.SOURCE_CLOUD_SYNC)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            Wearable.getNodeClient(context).connectedNodes.await().count { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WearDataLayerContracts.PATH_CLOUD_CONFIG, bytes)
                        .await()
                    true
                }.getOrDefault(false)
            }
        }
    }

    private fun buildPayload(
        plan: WearSyncPayloadPlan,
        zoneId: ZoneId,
        source: String,
        updatedAt: Long,
        weekNumberMode: String,
        semesterWeekStartDate: LocalDate,
        requestId: String,
    ): String {
        val arr = JSONArray()
        plan.lessons.sortedWith(compareBy<PersistedLesson> { it.dayOfWeek }.thenBy { it.startMinute }).forEach { lesson ->
            val safeStart = lesson.startMinute.coerceIn(0, 23 * 60 + 59)
            val safeEnd = lesson.endMinute.coerceAtLeast(safeStart + 1).coerceAtMost(23 * 60 + 59)
            val start = LocalTime.of(safeStart / 60, safeStart % 60)
            val end = LocalTime.of(safeEnd / 60, safeEnd % 60)
            arr.put(
                JSONObject()
                    .put("id", lesson.id)
                    .put("title", lesson.title)
                    .put("teacher", lesson.teacher ?: "")
                    .put("dayOfWeek", lesson.dayOfWeek)
                    .put("startTime", start.format(timeFormatter))
                    .put("endTime", end.format(timeFormatter))
                    .put("location", lesson.location ?: "")
                    .put("note", lesson.note ?: "")
                    .put("startWeek", lesson.startWeek.coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK))
                    .put(
                        "endWeek",
                        lesson.endWeek.coerceIn(
                            lesson.startWeek.coerceIn(MIN_SCHEDULE_WEEK, MAX_SCHEDULE_WEEK),
                            MAX_SCHEDULE_WEEK,
                        ),
                    )
                    .put(
                        "weekParity",
                        lesson.weekParity.uppercase().let {
                            if (it == "ODD" || it == "EVEN") it else "ALL"
                        },
                    ),
            )
        }

        val exceptionArray = JSONArray()
        plan.exceptions.sortedBy(PersistedScheduleException::id).forEach { exception ->
            exceptionArray.put(
                JSONObject()
                    .put("id", exception.id)
                    .put("lessonId", exception.lessonId ?: JSONObject.NULL)
                    .put("type", exception.type.uppercase())
                    .put("date", exception.date)
                    .put("title", exception.title ?: JSONObject.NULL)
                    .put("teacher", exception.teacher ?: JSONObject.NULL)
                    .put("location", exception.location ?: JSONObject.NULL)
                    .put("note", exception.note ?: JSONObject.NULL)
                    .put("dayOfWeek", exception.dayOfWeek ?: JSONObject.NULL)
                    .put("startMinute", exception.startMinute ?: JSONObject.NULL)
                    .put("endMinute", exception.endMinute ?: JSONObject.NULL),
            )
        }

        return JSONObject()
            .put("format", "classingtime_mobile_sync_v2")
            .put("timezone", zoneId.id)
            .put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .put("source", source)
            .put("revision", updatedAt)
            .put(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
            .put(WearDataLayerContracts.KEY_SYNC_MODE, plan.mode)
            .put("updatedAt", updatedAt)
            .put("weekNumberMode", weekNumberMode)
            .put("semesterWeekStartDate", semesterWeekStartDate.toString())
            .put("lessons", arr)
            .put("exceptions", exceptionArray)
            .put("deletedLessonIds", JSONArray(plan.deletedLessonIds.sorted()))
            .put("deletedExceptionIds", JSONArray(plan.deletedExceptionIds.sorted()))
            .toString()
    }

    private fun loadBaseline(context: Context): WearSyncBaselineRecord? = synchronized(baselineLock) {
        val prefs = context.getSharedPreferences(BASELINE_PREFS, Context.MODE_PRIVATE)
        val nodeId = prefs.getString(KEY_BASELINE_NODE_ID, null)?.takeIf(String::isNotBlank) ?: return@synchronized null
        val raw = prefs.getString(KEY_BASELINE, null)?.takeIf(String::isNotBlank) ?: return@synchronized null
        val baseline = runCatching { baselineFromJson(JSONObject(raw)) }.getOrNull() ?: return@synchronized null
        WearSyncBaselineRecord(nodeId = nodeId, baseline = baseline)
    }

    private fun savePendingBaseline(
        context: Context,
        requestId: String,
        nodeId: String,
        baseline: WearSyncBaseline,
        revision: Long,
    ) = synchronized(baselineLock) {
        val prefs = context.getSharedPreferences(BASELINE_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(PENDING_PREFIX)) return@forEach
            val createdAt = (value as? String)
                ?.let { runCatching { JSONObject(it).optLong("createdAt", 0L) }.getOrDefault(0L) }
                ?: 0L
            if (createdAt <= 0L || now - createdAt > PENDING_TTL_MS) editor.remove(key)
        }
        val root = JSONObject()
            .put("nodeId", nodeId)
            .put("revision", revision)
            .put("createdAt", now)
            .put("baseline", baselineToJson(baseline))
        editor.putString(pendingKey(requestId), root.toString()).commit()
    }

    private fun baselineToJson(baseline: WearSyncBaseline): JSONObject = JSONObject()
        .put("lessons", JSONObject(baseline.lessonFingerprints))
        .put("exceptions", JSONObject(baseline.exceptionFingerprints))

    private fun baselineFromJson(root: JSONObject?): WearSyncBaseline? {
        root ?: return null
        return WearSyncBaseline(
            lessonFingerprints = root.optJSONObject("lessons").toStringMap(),
            exceptionFingerprints = root.optJSONObject("exceptions").toStringMap(),
        )
    }

    private fun reserveRevision(context: Context): Long = synchronized(baselineLock) {
        val prefs = context.getSharedPreferences(BASELINE_PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getLong(KEY_LAST_REVISION, 0L)
        val next = maxOf(System.currentTimeMillis(), previous + 1L)
        prefs.edit().putLong(KEY_LAST_REVISION, next).commit()
        next
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { key -> optString(key) }
    }

    private fun pendingKey(requestId: String): String = "$PENDING_PREFIX$requestId"

    private const val BASELINE_PREFS = "wear_sync_payload_baseline"
    private const val KEY_BASELINE = "baseline"
    private const val KEY_BASELINE_NODE_ID = "baseline_node_id"
    private const val KEY_LAST_REVISION = "last_revision"
    private const val PENDING_PREFIX = "pending_baseline_"
    private const val PENDING_TTL_MS = 24 * 60 * 60 * 1000L
}
