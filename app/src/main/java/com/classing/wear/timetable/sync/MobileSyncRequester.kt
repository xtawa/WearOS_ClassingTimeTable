package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.classing.wear.timetable.core.DevicePlatformCapabilities
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

class MobileSyncRequester(
    private val context: Context,
) {
    suspend fun requestSyncFromPhone(): Result<Int> {
        if (WearSyncModeStore.isIndependentModeEnabled(context)) return Result.success(0)
        // Watches without Google Play services have no Data Layer at all; bail out quietly
        // instead of blocking on GMS tasks that can never complete.
        if (!DevicePlatformCapabilities.isDataLayerAvailable(context)) return Result.success(0)
        return runCatching {
            val requestedAt = System.currentTimeMillis()
            val requestId = UUID.randomUUID().toString()
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()

            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_REQUEST).apply {
                dataMap.putLong(WearDataLayerContracts.KEY_REQUESTED_AT, requestedAt)
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
                dataMap.putString(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            val payload = JSONObject()
                .put(WearDataLayerContracts.KEY_REQUESTED_AT, requestedAt)
                .put(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
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
