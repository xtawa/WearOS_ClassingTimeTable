package com.xtawa.classingtime.sync

import android.util.Log
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets

class MobileSyncAckListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearSyncAckStore.PATH_SYNC_ACK -> handleAck(messageEvent.data)
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
            Log.i(TAG, "Received wear sync ACK(DataItem) success=${ack.success} count=${ack.appliedLessonCount}")
        }
    }

    private fun handleAck(bytes: ByteArray) {
        val raw = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        val ack = WearSyncAckStore.parse(raw) ?: return
        WearSyncAckStore.save(applicationContext, ack)
        Log.i(TAG, "Received wear sync ACK success=${ack.success} count=${ack.appliedLessonCount}")
    }

    companion object {
        private const val TAG = "MobileSyncAckListener"
    }
}
