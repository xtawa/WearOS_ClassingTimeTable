package com.classing.wear.timetable.sync

import android.util.Log
import com.classing.shared.sync.CloudSyncContracts
import com.classing.shared.sync.WearDataLayerContracts
import com.classing.wear.timetable.R
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

class CloudConfigListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearDataLayerContracts.PATH_CLOUD_CONFIG) {
            val payload = runCatching { String(messageEvent.data, StandardCharsets.UTF_8) }.getOrNull().orEmpty()
            applyConfigPayload(payload)
            return
        }
        super.onMessageReceived(messageEvent)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearDataLayerContracts.PATH_CLOUD_CONFIG) return@forEach
            val payload = DataMapItem.fromDataItem(event.dataItem).dataMap
                .getString(WearDataLayerContracts.KEY_CONFIG_PAYLOAD)
                .orEmpty()
            applyConfigPayload(payload)
        }
    }

    private fun applyConfigPayload(payload: String) {
        if (payload.isBlank()) return
        val parsed = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val current = WearCloudConfigStore.load(applicationContext)
        val config = WearCloudConfig(
            enabled = parsed.optBoolean("enabled", false),
            serverUrl = parsed.optString("serverUrl", ""),
            remotePath = parsed.optString("remotePath", CloudSyncContracts.DEFAULT_REMOTE_PATH),
            username = parsed.optString("username", ""),
            password = parsed.optString("password", ""),
            updatedAt = parsed.optLong(WearDataLayerContracts.KEY_UPDATED_AT, System.currentTimeMillis()),
        )
        WearCloudConfigStore.save(applicationContext, config)
        if (current.normalized() != config.normalized()) {
            WearCloudConfigStore.saveConfigUpdateStatus(
                context = applicationContext,
                message = applicationContext.getString(R.string.settings_cloud_sync_wear_webdav_updated_from_mobile),
            )
        }
        serviceScope.launch {
            val result = WearCloudSyncCoordinator.pullFromCloud(
                context = applicationContext,
                trigger = CloudSyncContracts.TRIGGER_APP_START,
                force = true,
            )
            if (result.isFailure) {
                Log.w(TAG, "Cloud pull after config push failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    companion object {
        private const val TAG = "CloudConfigListenerSvc"
    }
}

private data class NormalizedWearWebDav(
    val enabled: Boolean,
    val serverUrl: String,
    val remotePath: String,
    val username: String,
    val password: String,
)

private fun WearCloudConfig.normalized(): NormalizedWearWebDav {
    val normalizedServer = serverUrl.trim().trimEnd('/')
    val rawPath = remotePath.trim().ifBlank { CloudSyncContracts.DEFAULT_REMOTE_PATH }
    val normalizedPath = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
    return NormalizedWearWebDav(
        enabled = enabled,
        serverUrl = normalizedServer,
        remotePath = normalizedPath,
        username = username.trim(),
        password = password,
    )
}
