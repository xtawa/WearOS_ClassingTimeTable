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
)
