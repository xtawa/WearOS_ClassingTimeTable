package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class MobileSyncRequester(
    private val context: Context,
) {
    suspend fun requestSyncFromPhone(): Result<Int> {
        return runCatching {
            val requestedAt = System.currentTimeMillis()
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()

            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_REQUEST).apply {
                dataMap.putLong(WearDataLayerContracts.KEY_REQUESTED_AT, requestedAt)
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            val payload = JSONObject()
                .put(WearDataLayerContracts.KEY_REQUESTED_AT, requestedAt)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)

            val sentToNodes = coroutineScope {
                nodes.map { node ->
                    async {
                        runCatching {
                            Wearable.getMessageClient(context)
                                .sendMessage(node.id, WearDataLayerContracts.PATH_SYNC_REQUEST, payload)
                                .await()
                            true
                        }.getOrDefault(false)
                    }
                }.awaitAll().count { it }
            }

            sentToNodes
        }
    }
}
