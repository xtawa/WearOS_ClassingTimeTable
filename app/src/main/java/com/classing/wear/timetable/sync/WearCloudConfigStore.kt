package com.classing.wear.timetable.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.classing.shared.sync.CloudProvider
import com.classing.shared.sync.CloudSyncContracts

data class WearCloudConfig(
    val provider: CloudProvider,
    val enabled: Boolean,
    val serverUrl: String,
    val remotePath: String,
    val username: String,
    val password: String,
    val driveFileName: String,
    val driveAccessToken: String,
    val driveAccessTokenExpireAt: Long,
    val updatedAt: Long,
) {
    fun isComplete(): Boolean {
        if (!enabled) return false
        return when (provider) {
            CloudProvider.WEBDAV -> {
                serverUrl.startsWith("https://", ignoreCase = true) &&
                    remotePath.isNotBlank() &&
                    username.isNotBlank() &&
                    password.isNotBlank()
            }

            CloudProvider.GOOGLE_DRIVE -> {
                driveFileName.isNotBlank() &&
                    driveAccessToken.isNotBlank() &&
                    driveAccessTokenExpireAt > System.currentTimeMillis() + 60_000L
            }
        }
    }
}

object WearCloudConfigStore {
    private const val PREF_NAME = "wear_cloud_config"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_REMOTE_PATH = "remote_path"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_DRIVE_FILE_NAME = "drive_file_name"
    private const val KEY_DRIVE_ACCESS_TOKEN = "drive_access_token"
    private const val KEY_DRIVE_ACCESS_TOKEN_EXPIRE_AT = "drive_access_token_expire_at"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_LAST_SYNC_RESULT = "last_sync_result"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"
    private const val KEY_LAST_CONFIG_UPDATE_MESSAGE = "last_config_update_message"
    private const val KEY_LAST_CONFIG_UPDATE_AT = "last_config_update_at"
    private const val KEY_LAST_TIMETABLE_UPDATED_AT = "last_timetable_updated_at"
    private const val KEY_LAST_WEAR_SETTINGS_UPDATED_AT = "last_wear_settings_updated_at"

    private fun prefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(context: Context, config: WearCloudConfig) {
        prefs(context).edit()
            .putString(KEY_PROVIDER, config.provider.wireValue)
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_SERVER_URL, config.serverUrl)
            .putString(KEY_REMOTE_PATH, config.remotePath)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_DRIVE_FILE_NAME, config.driveFileName)
            .putString(KEY_DRIVE_ACCESS_TOKEN, config.driveAccessToken)
            .putLong(KEY_DRIVE_ACCESS_TOKEN_EXPIRE_AT, config.driveAccessTokenExpireAt)
            .putLong(KEY_UPDATED_AT, config.updatedAt)
            .apply()
    }

    fun load(context: Context): WearCloudConfig {
        val p = prefs(context)
        return WearCloudConfig(
            provider = CloudProvider.fromWire(p.getString(KEY_PROVIDER, CloudProvider.WEBDAV.wireValue)),
            enabled = p.getBoolean(KEY_ENABLED, false),
            serverUrl = p.getString(KEY_SERVER_URL, "").orEmpty(),
            remotePath = p.getString(KEY_REMOTE_PATH, "/classing/classing_sync.json").orEmpty(),
            username = p.getString(KEY_USERNAME, "").orEmpty(),
            password = p.getString(KEY_PASSWORD, "").orEmpty(),
            driveFileName = p.getString(KEY_DRIVE_FILE_NAME, CloudSyncContracts.DEFAULT_DRIVE_FILE_NAME).orEmpty(),
            driveAccessToken = p.getString(KEY_DRIVE_ACCESS_TOKEN, "").orEmpty(),
            driveAccessTokenExpireAt = p.getLong(KEY_DRIVE_ACCESS_TOKEN_EXPIRE_AT, 0L),
            updatedAt = p.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    fun saveSyncStatus(context: Context, message: String, syncedAt: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_LAST_SYNC_RESULT, message)
            .putLong(KEY_LAST_SYNC_AT, syncedAt)
            .apply()
    }

    fun loadSyncStatus(context: Context): Pair<String, Long> {
        val p = prefs(context)
        return p.getString(KEY_LAST_SYNC_RESULT, "").orEmpty() to p.getLong(KEY_LAST_SYNC_AT, 0L)
    }

    fun saveConfigUpdateStatus(context: Context, message: String, updatedAt: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_LAST_CONFIG_UPDATE_MESSAGE, message)
            .putLong(KEY_LAST_CONFIG_UPDATE_AT, updatedAt)
            .apply()
    }

    fun loadConfigUpdateStatus(context: Context): Pair<String, Long> {
        val p = prefs(context)
        return p.getString(KEY_LAST_CONFIG_UPDATE_MESSAGE, "").orEmpty() to p.getLong(KEY_LAST_CONFIG_UPDATE_AT, 0L)
    }

    fun loadLastTimetableUpdatedAt(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_TIMETABLE_UPDATED_AT, 0L)
    }

    fun saveLastTimetableUpdatedAt(context: Context, updatedAt: Long) {
        prefs(context).edit().putLong(KEY_LAST_TIMETABLE_UPDATED_AT, updatedAt).apply()
    }

    fun loadLastWearSettingsUpdatedAt(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_WEAR_SETTINGS_UPDATED_AT, 0L)
    }

    fun saveLastWearSettingsUpdatedAt(context: Context, updatedAt: Long) {
        prefs(context).edit().putLong(KEY_LAST_WEAR_SETTINGS_UPDATED_AT, updatedAt).apply()
    }
}
