package com.classing.wear.timetable.sync

import com.classing.shared.sync.SyncDomain
import com.classing.shared.sync.SyncArbitrator
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class WearSettingsListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (WearSyncModeStore.isIndependentModeEnabled(applicationContext)) return
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
        if (WearSyncModeStore.isIndependentModeEnabled(applicationContext)) return
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
            applyMutex.withLock {
            val app = applicationContext as? ClassingTimetableApplication ?: return@withLock
            val parsed = runCatching { JSONObject(payload) }.getOrNull()
            val source = SyncSource.fromWire(parsed?.optString(WearDataLayerContracts.KEY_SOURCE).orEmpty())
                .takeUnless { it == SyncSource.UNKNOWN } ?: SyncSource.PHONE_DIRECT
            val stamp = SyncStamp(
                revision = revision.takeIf { it > 0L }
                    ?: parsed?.optLong(WearDataLayerContracts.KEY_REVISION, 0L)?.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                source = source,
                appliedAt = parsed?.optLong(WearDataLayerContracts.KEY_UPDATED_AT, 0L)?.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
            )
            val current = WearSyncStampStore.load(applicationContext, SyncDomain.WEAR_SETTINGS)
            if (!SyncArbitrator.shouldApply(SyncDomain.WEAR_SETTINGS, stamp, current)) {
                WearSyncStampStore.saveDecision(applicationContext, SyncDomain.WEAR_SETTINGS, "stale_skipped", "incoming=$stamp current=$current")
                return@withLock
            }
            app.appContainer.settingsRepository.applyWearSettingsSnapshot(payload)
            WearSyncStampStore.save(applicationContext, SyncDomain.WEAR_SETTINGS, stamp)
            WearSyncStampStore.saveDecision(
                applicationContext,
                SyncDomain.WEAR_SETTINGS,
                "applied",
                "phone-coordinated wear settings applied revision=${stamp.revision}",
            )
            }
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

    companion object {
        private val applyMutex = Mutex()
    }
}
