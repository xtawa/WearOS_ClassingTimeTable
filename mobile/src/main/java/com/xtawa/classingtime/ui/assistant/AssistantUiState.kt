package com.xtawa.classingtime.ui.assistant

import androidx.compose.runtime.Immutable

internal enum class AssistantMessageRole {
    User,
    Assistant,
}

@Immutable
internal data class AssistantModelUiModel(
    val id: String,
    val name: String,
    val description: String,
    val starred: Boolean = false,
)

@Immutable
internal data class AssistantConversationUiModel(
    val id: String,
    val title: String,
)

@Immutable
internal data class AssistantMessageUiModel(
    val id: String,
    val role: AssistantMessageRole,
    val content: String,
    val attachments: List<AssistantAttachmentUiModel> = emptyList(),
    val createdAt: Long = 0,
    val reasoning: String = "",
)

@Immutable
internal data class AssistantUiState(
    val loggedIn: Boolean,
    val member: Boolean,
    val hasSchedule: Boolean,
    val contextLabel: String,
    val question: String,
    val sending: Boolean,
    val status: String,
    val selectedModelId: String,
    val models: List<AssistantModelUiModel>,
    val conversations: List<AssistantConversationUiModel>,
    val messages: List<AssistantMessageUiModel>,
    val attachments: List<AssistantAttachmentUiModel> = emptyList(),
    val recording: Boolean = false,
    val uploading: Boolean = false,
    val transcribing: Boolean = false,
    val prompts: List<String> = emptyList(),
    val showImagePreviews: Boolean = true,
    val showReasoning: Boolean = true,
    val showTimestamps: Boolean = false,
    val showPromptSuggestions: Boolean = true,
    val preferencesSaving: Boolean = false,
    val usageNotice: String = "",
    val promptNotice: String = "",
)

@Immutable
internal data class AssistantAttachmentUiModel(val id: String, val name: String, val preview: android.graphics.Bitmap? = null)
