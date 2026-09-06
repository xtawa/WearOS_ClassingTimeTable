package com.xtawa.classingtime.sync

import android.util.Log
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class MobileSyncAckListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncAckStore.PATH_SYNC_ACK -> handleAck(messageEvent.data, messageEvent.sourceNodeId)
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearDataLayerContracts.PATH_SYNC_ACK) return@forEach

            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val syncedAt = map.getLong(WearDataLayerContracts.KEY_SYNCED_AT, 0L)
            if (syncedAt <= 0L) return@forEach

            val ack = WearSyncAckInfo(
                syncedAtMillis = syncedAt,
                success = map.getBoolean(WearDataLayerContracts.KEY_SUCCESS, false),
                appliedLessonCount = map.getInt(WearDataLayerContracts.KEY_APPLIED_LESSON_COUNT, 0).coerceAtLeast(0),
                source = map.getString(WearDataLayerContracts.KEY_SOURCE).orEmpty(),
                errorMessage = map.getString(WearDataLayerContracts.KEY_ERROR).orEmpty(),
            )
            WearSyncAckStore.save(applicationContext, ack)
            confirmBaselineIfApplied(
                requestId = map.getString(WearDataLayerContracts.KEY_REQUEST_ID).orEmpty(),
                nodeId = event.dataItem.uri.host.orEmpty(),
                ackStatus = map.getString(WearDataLayerContracts.KEY_ACK_STATUS).orEmpty(),
                success = ack.success,
            )
            Log.i(TAG, "Received wear sync ACK(DataItem) success=${ack.success} count=${ack.appliedLessonCount}")
        }
    }

    private fun handleAck(bytes: ByteArray, sourceNodeId: String) {
        val raw = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        val ack = WearSyncAckStore.parse(raw) ?: return
        WearSyncAckStore.save(applicationContext, ack)
        val json = runCatching { JSONObject(raw) }.getOrNull()
        confirmBaselineIfApplied(
            requestId = json?.optString(WearDataLayerContracts.KEY_REQUEST_ID).orEmpty(),
            nodeId = sourceNodeId,
            ackStatus = json?.optString(WearDataLayerContracts.KEY_ACK_STATUS).orEmpty(),
            success = ack.success,
        )
        Log.i(TAG, "Received wear sync ACK success=${ack.success} count=${ack.appliedLessonCount}")
    }

    private fun confirmBaselineIfApplied(
        requestId: String,
        nodeId: String,
        ackStatus: String,
        success: Boolean,
    ) {
        if (!success || !ackStatus.equals("applied", ignoreCase = true)) return
        if (WearDataLayerSyncPublisher.confirmBaselineFromAck(applicationContext, requestId, nodeId)) {
            Log.i(TAG, "Committed Wear sync baseline requestId=$requestId node=$nodeId")
        }
    }

    companion object {
        private const val TAG = "MobileSyncAckListener"
    }
}
