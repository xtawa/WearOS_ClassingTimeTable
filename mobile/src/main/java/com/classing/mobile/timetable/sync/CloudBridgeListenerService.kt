package com.xtawa.classingtime.sync

import android.util.Log
import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.sync.WearDataLayerContracts
import com.xtawa.classingtime.R
import com.xtawa.classingtime.data.MobilePrefsStore
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
import org.json.JSONObject

class CloudBridgeListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearDataLayerContracts.PATH_WEAR_SETTINGS_SNAPSHOT -> {
                handleWearSettingsPayload(String(messageEvent.data, StandardCharsets.UTF_8))
            }
            WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST -> {
                handlePhoneCloudSyncRequestPayload(
                    String(messageEvent.data, StandardCharsets.UTF_8),
                    fallbackTrigger = CloudSyncContracts.TRIGGER_WEAR_REQUEST,
                )
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            when (event.dataItem.uri.path) {
                WearDataLayerContracts.PATH_WEAR_SETTINGS_SNAPSHOT -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val payload = map.getString(WearDataLayerContracts.KEY_SETTINGS_PAYLOAD).orEmpty()
                    handleWearSettingsPayload(payload)
                }
                WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST -> {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val payload = map.getString(WearDataLayerContracts.KEY_REQUEST_PAYLOAD).orEmpty()
                    val fallbackPayload = if (payload.isBlank()) {
                        JSONObject()
                            .put(
                                WearDataLayerContracts.KEY_TRIGGER,
                                map.getString(WearDataLayerContracts.KEY_TRIGGER)
                                    .orEmpty()
                                    .ifBlank { CloudSyncContracts.TRIGGER_WEAR_REQUEST },
                            )
                            .toString()
                    } else {
                        payload
                    }
                    handlePhoneCloudSyncRequestPayload(
                        fallbackPayload,
                        fallbackTrigger = CloudSyncContracts.TRIGGER_WEAR_REQUEST,
                    )
                }
            }
        }
    }

    private fun handleWearSettingsPayload(payload: String) {
        if (payload.isBlank()) return
        val parsed = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val updatedAt = parsed.optLong(WearDataLayerContracts.KEY_UPDATED_AT, 0L)
            .takeIf { it > 0L } ?: System.currentTimeMillis()
        MobilePrefsStore.saveWearSettingsSnapshot(applicationContext, payload, updatedAt)
        triggerPhoneCloudSync(CloudSyncContracts.TRIGGER_WEAR_REQUEST)
    }

    private fun handlePhoneCloudSyncRequestPayload(payload: String, fallbackTrigger: String) {
        val parsed = runCatching { JSONObject(payload) }.getOrNull()
        val trigger = parsed?.optString(WearDataLayerContracts.KEY_TRIGGER).orEmpty().ifBlank { fallbackTrigger }
        val incomingWearSnapshot = parsed?.optJSONObject(WearDataLayerContracts.KEY_WEAR_WEBDAV_SNAPSHOT)
            ?.let { WearWebDavSnapshot.fromJson(it) }
        serviceScope.launch {
            if (incomingWearSnapshot != null) {
                handleWearWebDavConflictIfNeeded(incomingWearSnapshot, trigger)
            }
            triggerPhoneCloudSyncInternal(trigger)
        }
    }

    private suspend fun handleWearWebDavConflictIfNeeded(incomingWearSnapshot: WearWebDavSnapshot, trigger: String) {
        val settings = MobilePrefsStore.loadSettings(applicationContext)
        val password = CloudCredentialStore.loadPassword(applicationContext)
        val mobileSnapshot = WearWebDavSnapshot.fromMobile(settings, password)
        if (mobileSnapshot.normalized() == incomingWearSnapshot.normalized()) {
            return
        }

        val pushResult = runCatching {
            CloudConfigPublisher.publishToWear(
                context = applicationContext,
                payload = settings.toCloudConfigPayload(password),
                trigger = trigger,
            ).getOrThrow()
        }
        if (pushResult.isSuccess) {
            MobilePrefsStore.setCloudConfigPushStatus(
                applicationContext,
                applicationContext.getString(R.string.settings_cloud_sync_push_status_wear_webdav_updated),
            )
        } else {
            Log.w(TAG, "Failed to push mobile WebDAV config during conflict resolution: ${pushResult.exceptionOrNull()?.message}")
        }
    }

    private fun triggerPhoneCloudSync(trigger: String) {
        serviceScope.launch {
            triggerPhoneCloudSyncInternal(trigger)
        }
    }

    private suspend fun triggerPhoneCloudSyncInternal(trigger: String) {
        val result = MobileCloudSyncCoordinator.requestCloudSync(
            context = applicationContext,
            trigger = trigger,
            force = true,
            alsoPushConfigToWear = false,
        )
        if (result.isFailure) {
            Log.w(TAG, "Cloud sync trigger failed: ${result.exceptionOrNull()?.message}")
        }
    }

    companion object {
        private const val TAG = "CloudBridgeListenerSvc"
    }
}

private data class WearWebDavSnapshot(
    val enabled: Boolean,
    val serverUrl: String,
    val remotePath: String,
    val username: String,
    val password: String,
) {
    fun normalized(): NormalizedWebDavSnapshot {
        val normalizedServer = serverUrl.trim().trimEnd('/')
        val rawPath = remotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH }
        val normalizedPath = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        return NormalizedWebDavSnapshot(
            enabled = enabled,
            serverUrl = normalizedServer,
            remotePath = normalizedPath,
            username = username.trim(),
            password = password,
        )
    }

    companion object {
        fun fromJson(json: JSONObject): WearWebDavSnapshot {
            return WearWebDavSnapshot(
                enabled = json.optBoolean("enabled", false),
                serverUrl = json.optString("serverUrl", ""),
                remotePath = json.optString("remotePath", CloudSyncContracts.DEFAULT_REMOTE_PATH),
                username = json.optString("username", ""),
                password = json.optString("password", ""),
            )
        }

        fun fromMobile(settings: com.xtawa.classingtime.data.MobileSettings, password: String): WearWebDavSnapshot {
            return WearWebDavSnapshot(
                enabled = settings.cloudSyncEnabled,
                serverUrl = settings.cloudServerUrl,
                remotePath = settings.cloudRemotePath,
                username = settings.cloudUsername,
                password = password,
            )
        }
    }
}

private data class NormalizedWebDavSnapshot(
    val enabled: Boolean,
    val serverUrl: String,
    val remotePath: String,
    val username: String,
    val password: String,
)
