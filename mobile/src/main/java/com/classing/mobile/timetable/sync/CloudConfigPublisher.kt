package com.xtawa.classingtime.sync

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

object CloudConfigPublisher {
    suspend fun publishToWear(
        context: Context,
        payload: JSONObject,
        trigger: String,
    ): Result<Int> {
        return runCatching {
            val updatedAt = System.currentTimeMillis()
            val payloadString = payload
                .put(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
                .toString()

            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_CLOUD_CONFIG).apply {
                dataMap.putString(WearDataLayerContracts.KEY_CONFIG_PAYLOAD, payloadString)
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
                dataMap.putString(WearDataLayerContracts.KEY_TRIGGER, trigger)
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(request).await()
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val bytes = payloadString.toByteArray(StandardCharsets.UTF_8)
            nodes.count { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WearDataLayerContracts.PATH_CLOUD_CONFIG, bytes)
                        .await()
                    true
                }.getOrDefault(false)
            }
        }
    }
}
