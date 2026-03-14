package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class MobileSyncRequester(
    private val context: Context,
) {
    suspend fun requestSyncFromPhone(): Result<Int> {
        return runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) error("No connected phone")

            nodes.forEach { node ->
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, WearDataLayerContracts.PATH_SYNC_REQUEST, ByteArray(0))
                    .await()
            }
            nodes.size
        }
    }
}

