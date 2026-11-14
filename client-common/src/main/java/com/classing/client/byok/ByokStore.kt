package com.classing.client.byok

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.classing.shared.ai.*
import org.json.JSONArray
import org.json.JSONObject

object ByokJson {
    fun encode(config: ByokConfig): JSONObject = JSONObject().put("version",1).put("ownerId",config.ownerId)
        .put("enabled",config.enabled).put("chatId",config.chatId).put("visionId",config.visionId)
        .put("transcriptionId",config.transcriptionId).put("speechId",config.speechId).put("wearId",config.wearId)
        .put("profiles",JSONArray().also { list -> config.profiles.forEach { p -> list.put(JSONObject().put("id",p.id)
            .put("name",p.name).put("baseUrl",p.baseUrl).put("model",p.model).put("apiKey",p.apiKey).put("role",p.role.name).put("voice",p.voice)) } })
    fun decode(json: JSONObject): ByokConfig {
        require(json.getInt("version")==1)
        val profiles=json.getJSONArray("profiles");require(profiles.length()<=20)
        return ByokConfig(json.getString("ownerId"),(0 until profiles.length()).map { i -> profiles.getJSONObject(i).let {
            ByokProfile(it.getString("id"),it.getString("name"),it.getString("baseUrl"),it.getString("model"),it.getString("apiKey"),ByokRole.valueOf(it.getString("role")),it.optString("voice","alloy"))
        } },json.optBoolean("enabled"),json.optString("chatId"),json.optString("visionId"),json.optString("transcriptionId"),json.optString("speechId"),json.optString("wearId"))
    }
}

/** Deliberately separate from settings/sync/backup serializers. Both modules exclude this file from Android backup. */
object ByokStore {
    const val PREFS = "classing_byok_private"
    private fun prefs(context: Context) = EncryptedSharedPreferences.create(context, PREFS,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun load(context: Context, ownerId: String): ByokConfig = prefs(context).getString("config:$ownerId",null)?.let {
        ByokJson.decode(JSONObject(it)).also { c -> require(c.ownerId==ownerId) }
    } ?: ByokConfig(ownerId)
    fun save(context: Context, config: ByokConfig) { check(prefs(context).edit().putString("config:${config.ownerId}",ByokJson.encode(config).toString()).commit()) { "Could not save private settings" } }
    fun history(context: Context, ownerId: String): JSONArray = JSONArray(prefs(context).getString("history:$ownerId","[]"))
    fun saveHistory(context: Context, ownerId: String, history: JSONArray) {
        require(history.length()<=100 && history.toString().length<=512*1024)
        check(prefs(context).edit().putString("history:$ownerId",history.toString()).commit())
    }
    fun favorites(context: Context, ownerId: String): Set<String> = prefs(context).getStringSet("favorites:$ownerId",emptySet()).orEmpty().toSet()
    fun saveFavorites(context: Context, ownerId: String, ids: Set<String>) { require(ids.size<=20);check(prefs(context).edit().putStringSet("favorites:$ownerId",ids).commit()) }
    fun clear(context: Context, ownerId: String) { check(prefs(context).edit().remove("config:$ownerId").remove("history:$ownerId").remove("favorites:$ownerId").commit()) }
}
