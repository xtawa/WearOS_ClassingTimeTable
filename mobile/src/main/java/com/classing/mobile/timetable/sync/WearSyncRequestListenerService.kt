package com.xtawa.classingtime.sync

import android.content.Context
import android.util.Log
import com.classing.shared.sync.WearDataLayerContracts
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.xtawa.classingtime.data.MobilePrefsStore
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class WearSyncRequestListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearDataLayerContracts.PATH_SYNC_REQUEST) {
            super.onMessageReceived(messageEvent)
            return
        }

        val requestedAt = parseRequestedAt(messageEvent.data)
        if (requestedAt > 0L && isDuplicateRequest(requestedAt)) {
            Log.i(TAG, "Ignore duplicated sync request message requestedAt=$requestedAt")
            return
        }
        if (requestedAt > 0L) markHandledRequest(requestedAt)

        triggerMobileSyncPublish()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearDataLayerContracts.PATH_SYNC_REQUEST) return@forEach

            val requestedAt = DataMapItem.fromDataItem(event.dataItem).dataMap
                .getLong(WearDataLayerContracts.KEY_REQUESTED_AT, 0L)
            if (requestedAt > 0L && isDuplicateRequest(requestedAt)) {
                Log.i(TAG, "Ignore duplicated sync request data item requestedAt=$requestedAt")
                return@forEach
            }
            if (requestedAt > 0L) markHandledRequest(requestedAt)

            triggerMobileSyncPublish()
        }
    }

    private fun triggerMobileSyncPublish() {
        serviceScope.launch {
            val lessons = MobilePrefsStore.loadLessons(applicationContext)
            val settings = MobilePrefsStore.loadSettings(applicationContext)
            val semesterWeekStartDate = runCatching { LocalDate.parse(settings.semesterWeekStartDate) }
                .getOrDefault(LocalDate.now())
            val result = WearDataLayerSyncPublisher.publishLessonsSnapshot(
                context = applicationContext,
                lessons = lessons,
                zoneId = ZoneId.systemDefault(),
                weekNumberMode = settings.weekNumberMode,
                semesterWeekStartDate = semesterWeekStartDate,
            )
            if (result.isSuccess) {
                Log.i(TAG, "Wear sync request handled, published ${lessons.size} lessons")
            } else {
                Log.w(TAG, "Wear sync request failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun parseRequestedAt(bytes: ByteArray): Long {
        val raw = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull().orEmpty()
        if (raw.isBlank()) return 0L
        return runCatching { JSONObject(raw).optLong(WearDataLayerContracts.KEY_REQUESTED_AT, 0L) }
            .getOrDefault(0L)
    }

    private fun isDuplicateRequest(requestedAt: Long): Boolean {
        return getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_REQUEST_AT, 0L) == requestedAt
    }

    private fun markHandledRequest(requestedAt: Long) {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_REQUEST_AT, requestedAt)
            .apply()
    }

    companion object {
        private const val TAG = "WearSyncRequestSvc"
        private const val PREF_NAME = "mobile_wear_sync_request"
        private const val KEY_LAST_REQUEST_AT = "last_request_at"
    }
}
