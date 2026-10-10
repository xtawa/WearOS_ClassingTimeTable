package com.xtawa.classingtime.account

import org.json.JSONArray
import org.json.JSONObject

data class AiPreferences(
 val defaultModel: String = "",
 val autoRefreshPrompts: Boolean = false,
 val voiceAutoSend: Boolean = false,
 val showReasoning: Boolean = true,
 val imagePreviews: Boolean = true,
 val showTimestamps: Boolean = false,
 val favoriteModels: Set<String> = emptySet(),
 val version: Long = 0,
 val showPromptSuggestions: Boolean = true,
) {
 fun toJson() = JSONObject().put("defaultModel", defaultModel).put("autoRefreshPrompts", autoRefreshPrompts)
  .put("voiceAutoSend", voiceAutoSend).put("showReasoning", showReasoning).put("imagePreviews", imagePreviews)
  .put("showTimestamps", showTimestamps).put("favoriteModels", JSONArray(favoriteModels.toList())).put("version", version).put("showPromptSuggestions", showPromptSuggestions)
 companion object {
  fun fromJson(item: JSONObject): AiPreferences {
   val favorites = item.optJSONArray("favoriteModels")
   return AiPreferences(item.optString("defaultModel"), item.optBoolean("autoRefreshPrompts"), item.optBoolean("voiceAutoSend", false),
    item.optBoolean("showReasoning", true), item.optBoolean("imagePreviews", true), item.optBoolean("showTimestamps"),
    buildSet { if (favorites != null) for (i in 0 until favorites.length()) add(favorites.getString(i)) }, item.optLong("version"), item.optBoolean("showPromptSuggestions", true))
  }
 }
}
data class AiPromptSuggestions(val prompts: List<String>, val cached: Boolean, val costPoints: Int)
data class AiTranscription(val text: String, val costPoints: Int)
