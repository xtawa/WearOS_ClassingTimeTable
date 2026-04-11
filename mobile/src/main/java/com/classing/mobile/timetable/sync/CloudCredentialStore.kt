package com.xtawa.classingtime.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CloudCredentialStore {
    private const val PREF_NAME = "mobile_cloud_credentials"
    private const val KEY_PASSWORD = "webdav_password"

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

    fun savePassword(context: Context, password: String) {
        prefs(context).edit().putString(KEY_PASSWORD, password).apply()
    }

    fun loadPassword(context: Context): String {
        return prefs(context).getString(KEY_PASSWORD, "").orEmpty()
    }

    fun clearPassword(context: Context) {
        prefs(context).edit().remove(KEY_PASSWORD).apply()
    }
}
