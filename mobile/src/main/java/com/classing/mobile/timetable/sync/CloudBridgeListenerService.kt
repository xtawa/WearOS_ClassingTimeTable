package com.xtawa.classingtime.sync

import android.util.Log
import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.data.MobilePrefsStore
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class CloudBridgeListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearDataLayerContracts.PATH_WEAR_SETTINGS_SNAPSHOT -> {
                handleWearSettingsPayload(String(messageEvent.data, StandardCharsets.UTF_8))
            }
            WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST -> {
                handlePhoneCloudSyncRequestPayload(
                    String(messageEvent.data, StandardCharsets.UTF_8),
                    fallbackTrigger = CloudSyncContracts.TRIGGER_WEAR_REQUEST,
                )
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            when (event.dataItem.uri.path) {
                WearDataLayerContracts.PATH_WEAR_SETTINGS_SNAPSHOT -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val payload = map.getString(WearDataLayerContracts.KEY_SETTINGS_PAYLOAD).orEmpty()
                    handleWearSettingsPayload(payload)
                }
                WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val payload = map.getString(WearDataLayerContracts.KEY_REQUEST_PAYLOAD).orEmpty()
                    val fallbackPayload = if (payload.isBlank()) {
                        JSONObject()
                            .put(
                                WearDataLayerContracts.KEY_TRIGGER,
                                map.getString(WearDataLayerContracts.KEY_TRIGGER)
                                    .orEmpty()
                                    .ifBlank { CloudSyncContracts.TRIGGER_WEAR_REQUEST },
                            )
                            .toString()
                    } else {
                        payload
                    }
                    handlePhoneCloudSyncRequestPayload(
                        fallbackPayload,
                        fallbackTrigger = CloudSyncContracts.TRIGGER_WEAR_REQUEST,
                    )
                }
            }
        }
    }

    private fun handleWearSettingsPayload(payload: String) {
        if (payload.isBlank()) return
        val parsed = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val updatedAt = parsed.optLong(WearDataLayerContracts.KEY_UPDATED_AT, 0L)
            .takeIf { it > 0L } ?: System.currentTimeMillis()
        MobilePrefsStore.saveWearSettingsSnapshot(applicationContext, payload, updatedAt)
    }

    private fun handlePhoneCloudSyncRequestPayload(payload: String, fallbackTrigger: String) {
        val parsed = runCatching { JSONObject(payload) }.getOrNull()
        val trigger = parsed?.optString(WearDataLayerContracts.KEY_TRIGGER).orEmpty().ifBlank { fallbackTrigger }
        val requestId = parsed?.optString(WearDataLayerContracts.KEY_REQUEST_ID).orEmpty()
        if (requestId.isNotBlank() && !markRequestIfNew(requestId)) return
        serviceScope.launch {
            triggerPhoneCloudSyncInternal(trigger)
        }
    }

    @Synchronized
    private fun markRequestIfNew(requestId: String): Boolean {
        val prefs = getSharedPreferences("wear_cloud_request_dedupe", MODE_PRIVATE)
        if (prefs.contains(requestId)) return false
        val cutoff = System.currentTimeMillis() - REQUEST_DEDUPE_TTL_MS
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if ((value as? Long ?: 0L) < cutoff) editor.remove(key)
        }
        editor.putLong(requestId, System.currentTimeMillis()).commit()
        return true
    }

    private fun triggerPhoneCloudSync(trigger: String) {
        serviceScope.launch {
            triggerPhoneCloudSyncInternal(trigger)
        }
    }

    private suspend fun triggerPhoneCloudSyncInternal(trigger: String) {
        CloudSyncEngine.enqueue(applicationContext, trigger)
    }

    companion object {
        private const val TAG = "CloudBridgeListenerSvc"
        private const val REQUEST_DEDUPE_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
