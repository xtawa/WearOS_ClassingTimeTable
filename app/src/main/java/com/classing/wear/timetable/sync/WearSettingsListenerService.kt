package com.classing.wear.timetable.sync

import com.classing.shared.sync.SyncDomain
import com.classing.shared.sync.SyncSource
import com.classing.shared.sync.SyncStamp
import com.classing.shared.sync.WearDataLayerContracts
import com.classing.wear.timetable.ClassingTimetableApplication
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

class WearSettingsListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearDataLayerContracts.PATH_APPLY_WEAR_SETTINGS -> {
                applySnapshot(String(messageEvent.data, StandardCharsets.UTF_8), System.currentTimeMillis())
            }
            WearDataLayerContracts.PATH_CLOUD_CONFIG -> {
                storeCloudSnapshot(String(messageEvent.data, StandardCharsets.UTF_8), System.currentTimeMillis())
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) {
                return@forEach
            }
            val path = event.dataItem.uri.path ?: return@forEach
            if (path != WearDataLayerContracts.PATH_APPLY_WEAR_SETTINGS && path != WearDataLayerContracts.PATH_CLOUD_CONFIG) {
                return@forEach
            }
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (path) {
                WearDataLayerContracts.PATH_APPLY_WEAR_SETTINGS -> {
                    applySnapshot(
                        map.getString(WearDataLayerContracts.KEY_SETTINGS_PAYLOAD).orEmpty(),
                        map.getLong(WearDataLayerContracts.KEY_REVISION, 0L),
                    )
                }
                WearDataLayerContracts.PATH_CLOUD_CONFIG -> {
                    storeCloudSnapshot(
                        map.getString(WearDataLayerContracts.KEY_WEAR_CLOUD_SNAPSHOT).orEmpty(),
                        map.getLong(WearDataLayerContracts.KEY_UPDATED_AT, 0L),
                    )
                }
            }
        }
    }

    private fun applySnapshot(payload: String, revision: Long) {
        if (payload.isBlank()) return
        scope.launch {
            val app = applicationContext as? ClassingTimetableApplication ?: return@launch
            app.appContainer.settingsRepository.applyWearSettingsSnapshot(payload)
            val stamp = SyncStamp(
                revision = revision.takeIf { it > 0L } ?: System.currentTimeMillis(),
                source = SyncSource.CLOUD_PULL,
                appliedAt = System.currentTimeMillis(),
            )
            WearSyncStampStore.save(applicationContext, SyncDomain.WEAR_SETTINGS, stamp)
            WearSyncStampStore.saveDecision(
                applicationContext,
                SyncDomain.WEAR_SETTINGS,
                "applied",
                "phone-coordinated wear settings applied revision=${stamp.revision}",
            )
        }
    }

    private fun storeCloudSnapshot(payload: String, updatedAt: Long) {
        if (payload.isBlank()) return
        applicationContext.getSharedPreferences(MobileSyncPrefs.PREF_NAME, MODE_PRIVATE)
            .edit()
            .putString(MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT, payload)
            .putLong(
                MobileSyncPrefs.KEY_LAST_PHONE_CLOUD_SNAPSHOT_AT,
                updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
            .apply()
    }
}
