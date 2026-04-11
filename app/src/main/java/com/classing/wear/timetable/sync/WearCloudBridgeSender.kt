package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class WearCloudBridgeSender(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun publishWearSettingsSnapshot(trigger: String): Result<Int> {
        return runCatching {
            val updatedAt = System.currentTimeMillis()
            val snapshot = settingsRepository.exportWearSettingsSnapshot()
            val payload = JSONObject(snapshot)
                .put(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
                .put(WearDataLayerContracts.KEY_TRIGGER, trigger)
                .toString()

            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_WEAR_SETTINGS_SNAPSHOT).apply {
                dataMap.putString(WearDataLayerContracts.KEY_SETTINGS_PAYLOAD, payload)
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
                dataMap.putString(WearDataLayerContracts.KEY_TRIGGER, trigger)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            nodes.count { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WearDataLayerContracts.PATH_WEAR_SETTINGS_SNAPSHOT, bytes)
                        .await()
                    true
                }.getOrDefault(false)
            }
        }
    }

    suspend fun requestPhoneCloudSync(trigger: String): Result<Int> {
        return runCatching {
            val requestedAt = System.currentTimeMillis()
            val payload = JSONObject()
                .put(WearDataLayerContracts.KEY_TRIGGER, trigger)
                .put(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)

            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST).apply {
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
                dataMap.putString(WearDataLayerContracts.KEY_TRIGGER, trigger)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.count { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST, payload)
                        .await()
                    true
                }.getOrDefault(false)
            }
        }
    }
}
