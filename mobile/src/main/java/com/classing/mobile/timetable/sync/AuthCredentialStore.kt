package com.xtawa.classingtime.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AuthCredentialStore {
    private const val PREF_NAME = "mobile_auth_credentials"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
    private const val KEY_REFRESH_EXPIRES_AT = "refresh_expires_at"

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

    fun saveSession(
        context: Context,
        accessToken: String,
        refreshToken: String,
        accessExpiresAt: Long,
        refreshExpiresAt: Long,
    ) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, accessExpiresAt)
            .putLong(KEY_REFRESH_EXPIRES_AT, refreshExpiresAt)
            .apply()
    }

    fun loadAccessToken(context: Context): String {
        return prefs(context).getString(KEY_ACCESS_TOKEN, "").orEmpty()
    }

    fun loadRefreshToken(context: Context): String {
        return prefs(context).getString(KEY_REFRESH_TOKEN, "").orEmpty()
    }

    fun isAccessTokenUsable(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val token = loadAccessToken(context)
        val expiresAt = prefs(context).getLong(KEY_ACCESS_EXPIRES_AT, 0L)
        return token.isNotBlank() && expiresAt > now + 60_000L
    }

    fun isRefreshTokenUsable(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val token = loadRefreshToken(context)
        val expiresAt = prefs(context).getLong(KEY_REFRESH_EXPIRES_AT, Long.MAX_VALUE)
        return token.isNotBlank() && expiresAt > now
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCESS_EXPIRES_AT)
            .remove(KEY_REFRESH_EXPIRES_AT)
            .apply()
    }
}
