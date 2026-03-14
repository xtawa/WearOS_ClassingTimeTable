package com.xtawa.classingtime.sync

import android.util.Log
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
