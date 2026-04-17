package com.classing.wear.timetable.sync

import android.content.Context
import com.classing.shared.sync.SyncDomain
import com.classing.shared.sync.SyncSource
import com.classing.shared.sync.SyncStamp
import com.classing.shared.sync.WearDataLayerContracts
import com.classing.wear.timetable.domain.repository.SettingsRepository
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class WearCloudBridgeSender(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun publishWearSettingsSnapshot(trigger: String): Result<Int> {
        return runCatching {
            val updatedAt = System.currentTimeMillis()
            val snapshot = settingsRepository.exportWearSettingsSnapshot()
            val payload = JSONObject(snapshot)
                .put(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
                .put(WearDataLayerContracts.KEY_REVISION, updatedAt)
                .put(WearDataLayerContracts.KEY_SOURCE, SyncSource.WEAR_LOCAL.wireValue)
                .put(WearDataLayerContracts.KEY_TRIGGER, trigger)
                .toString()

            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_WEAR_SETTINGS_SNAPSHOT).apply {
                dataMap.putString(WearDataLayerContracts.KEY_SETTINGS_PAYLOAD, payload)
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, updatedAt)
                dataMap.putLong(WearDataLayerContracts.KEY_REVISION, updatedAt)
                dataMap.putString(WearDataLayerContracts.KEY_SOURCE, SyncSource.WEAR_LOCAL.wireValue)
                dataMap.putString(WearDataLayerContracts.KEY_TRIGGER, trigger)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            WearSyncStampStore.save(
                context = context,
                domain = SyncDomain.WEAR_SETTINGS,
                stamp = SyncStamp(
                    revision = updatedAt,
                    source = SyncSource.WEAR_LOCAL,
                    appliedAt = updatedAt,
                ),
            )
            WearSyncStampStore.saveDecision(
                context = context,
                domain = SyncDomain.WEAR_SETTINGS,
                decision = "applied",
                reason = "local wear settings published revision=$updatedAt",
            )

            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            nodes.count { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WearDataLayerContracts.PATH_WEAR_SETTINGS_SNAPSHOT, bytes)
                        .await()
                    true
                }.getOrDefault(false)
            }
        }
    }

    suspend fun requestPhoneCloudSync(trigger: String, wearCloudSnapshot: WearCloudConfig? = null): Result<Int> {
        return runCatching {
            val requestedAt = System.currentTimeMillis()
            val payloadObject = JSONObject()
                .put(WearDataLayerContracts.KEY_TRIGGER, trigger)
                .put(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
            wearCloudSnapshot?.let {
                val snapshot = JSONObject()
                    .put("enabled", it.enabled)
                    .put(WearDataLayerContracts.KEY_CLOUD_PROVIDER, it.provider.wireValue)
                    .put("serverUrl", it.serverUrl)
                    .put("remotePath", it.remotePath)
                    .put("username", it.username)
                    .put("password", it.password)
                    .put(WearDataLayerContracts.KEY_DRIVE_FILE_NAME, it.driveFileName)
                    .put(WearDataLayerContracts.KEY_DRIVE_ACCESS_TOKEN, it.driveAccessToken)
                    .put(WearDataLayerContracts.KEY_DRIVE_ACCESS_TOKEN_EXPIRE_AT, it.driveAccessTokenExpireAt)
                payloadObject.put(WearDataLayerContracts.KEY_WEAR_CLOUD_SNAPSHOT, snapshot)
                // Backward compatible key for older mobile builds.
                payloadObject.put(WearDataLayerContracts.KEY_WEAR_WEBDAV_SNAPSHOT, JSONObject(snapshot.toString()))
            }
            val payload = payloadObject
                .toString()
                .toByteArray(StandardCharsets.UTF_8)

            val request = PutDataMapRequest.create(WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST).apply {
                dataMap.putLong(WearDataLayerContracts.KEY_UPDATED_AT, requestedAt)
                dataMap.putString(WearDataLayerContracts.KEY_TRIGGER, trigger)
                dataMap.putString(
                    WearDataLayerContracts.KEY_REQUEST_PAYLOAD,
                    payloadObject.toString(),
                )
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.count { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WearDataLayerContracts.PATH_PHONE_CLOUD_SYNC_REQUEST, payload)
                        .await()
                    true
                }.getOrDefault(false)
            }
        }
    }
}
