package com.xtawa.classingtime.sync

import android.util.Log
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.xtawa.classingtime.data.MobilePrefsStore
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearSyncRequestListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearDataLayerContracts.PATH_SYNC_REQUEST) {
            super.onMessageReceived(messageEvent)
            return
        }

        serviceScope.launch {
            val lessons = MobilePrefsStore.loadLessons(applicationContext)
            val result = WearDataLayerSyncPublisher.publishLessonsSnapshot(
                context = applicationContext,
                lessons = lessons,
                zoneId = ZoneId.systemDefault(),
            )
            if (result.isSuccess) {
                Log.i(TAG, "Wear sync request handled, published ${lessons.size} lessons")
            } else {
                Log.w(TAG, "Wear sync request failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WearSyncRequestSvc"
    }
}

