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
                triggerPhoneCloudSync(CloudSyncContracts.TRIGGER_WEAR_REQUEST)
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
                    triggerPhoneCloudSync(CloudSyncContracts.TRIGGER_WEAR_REQUEST)
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
        triggerPhoneCloudSync(CloudSyncContracts.TRIGGER_WEAR_REQUEST)
    }

    private fun triggerPhoneCloudSync(trigger: String) {
        serviceScope.launch {
            val result = MobileCloudSyncCoordinator.requestCloudSync(
                context = applicationContext,
                trigger = trigger,
                force = true,
                alsoPushConfigToWear = false,
            )
            if (result.isFailure) {
                Log.w(TAG, "Cloud sync trigger failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    companion object {
        private const val TAG = "CloudBridgeListenerSvc"
    }
}
