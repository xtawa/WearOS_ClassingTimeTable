package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class MobileSyncRequester(
    private val context: Context,
) {
    suspend fun requestSyncFromPhone(): Result<Int> {
        if (WearSyncModeStore.isIndependentModeEnabled(context)) return Result.success(0)
        return runCatching {
            val requestedAt = System.currentTimeMillis()
            val requestId = UUID.randomUUID().toString()
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()

            // A user-initiated sync means "make this watch match the phone now". Always ask for
            // a complete snapshot so a reinstalled/reset watch cannot be stranded behind a phone
            // delta baseline that was created before the watch lost its local database.
            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_SYNC_REQUEST).apply {
                dataMap.putLong(WearDataLayerContracts.KEY_REQUESTED_AT, requestedAt)
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
                dataMap.putString(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
                dataMap.putBoolean(KEY_FORCE_FULL, true)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            val payload = JSONObject()
                .put(WearDataLayerContracts.KEY_REQUESTED_AT, requestedAt)
                .put(WearDataLayerContracts.KEY_REQUEST_ID, requestId)
                .put(KEY_FORCE_FULL, true)
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

    companion object {
        private const val KEY_FORCE_FULL = "forceFull"
    }
}
