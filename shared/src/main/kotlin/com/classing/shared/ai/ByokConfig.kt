package com.classing.shared.ai

import java.net.URI

enum class ByokRole { CHAT, VISION, TRANSCRIPTION, SPEECH }

data class ByokProfile(val id: String, val name: String, val baseUrl: String, val model: String, val apiKey: String,
    val role: ByokRole, val voice: String = "alloy") {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid profile id" }
        require(name.isNotBlank() && name.length <= 100) { "Profile name is required" }
        val uri = URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Use an HTTPS API base URL without credentials or query parameters" }
        require(model.isNotBlank() && model.length <= 200 && model.none { it.isISOControl() }) { "Model id is required" }
        require(apiKey.isNotBlank() && apiKey.length <= 8192 && apiKey.all { it.code in 32..126 }) { "API key is required" }
        require(voice.length <= 100 && voice.none { it.isISOControl() }) { "Invalid voice" }
    }
    override fun toString() = "ByokProfile(id=$id, name=$name, role=$role, apiKey=[REDACTED])"
}

data class ByokConfig(val ownerId: String, val profiles: List<ByokProfile> = emptyList(), val enabled: Boolean = false,
    val chatId: String = "", val visionId: String = "", val transcriptionId: String = "", val speechId: String = "", val wearId: String = "") {
    init {
        require(ownerId.isNotBlank() && ownerId.length <= 200)
        require(profiles.size <= 20 && profiles.map { it.id }.distinct().size == profiles.size)
        for ((id, role) in listOf(chatId to ByokRole.CHAT, visionId to ByokRole.VISION, transcriptionId to ByokRole.TRANSCRIPTION, speechId to ByokRole.SPEECH)) {
            require(id.isBlank() || profiles.any { it.id == id && it.role == role }) { "Selected model role does not match" }
        }
        require(wearId.isBlank() || profiles.any { it.id == wearId && it.role in setOf(ByokRole.CHAT, ByokRole.VISION) }) { "Wear requires a chat-capable model" }
        require(!enabled || chatId.isNotBlank()) { "Select a question-answering model first" }
    }
    fun profile(id: String): ByokProfile = profiles.firstOrNull { it.id == id } ?: error("Choose a BYOK model in settings")
    fun remove(id: String) = copy(profiles = profiles.filterNot { it.id == id }, enabled = enabled && chatId != id,
        chatId = chatId.takeUnless { it == id }.orEmpty(), visionId = visionId.takeUnless { it == id }.orEmpty(),
        transcriptionId = transcriptionId.takeUnless { it == id }.orEmpty(), speechId = speechId.takeUnless { it == id }.orEmpty(), wearId = wearId.takeUnless { it == id }.orEmpty())
    fun forWear(): ByokConfig {
        val p = profile(wearId).let { if (it.role == ByokRole.VISION) it.copy(role = ByokRole.CHAT) else it }
        return ByokConfig(ownerId, listOf(p), true, chatId = p.id)
    }
}
