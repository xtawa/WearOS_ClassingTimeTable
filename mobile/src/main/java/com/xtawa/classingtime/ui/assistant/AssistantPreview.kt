package com.xtawa.classingtime.ui.assistant

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xtawa.classingtime.ui.theme.ClassingTheme

private val assistantPreviewState = AssistantUiState(
    loggedIn = true,
    member = true,
    hasSchedule = true,
    contextLabel = "Monday · 4 classes · Physics is next",
    question = "",
    sending = false,
    status = "",
    selectedModelId = "fast",
    models = listOf(AssistantModelUiModel("fast", "Fast", "Short schedule answers")),
    conversations = emptyList(),
    messages = listOf(
        AssistantMessageUiModel("q1", AssistantMessageRole.User, "What's my afternoon like?"),
        AssistantMessageUiModel(
            "a1",
            AssistantMessageRole.Assistant,
            "You have Physics at 14:10 in A302, followed by English at 15:05 in A205. Your longest free window is 12:05–14:10.",
        ),
    ),
)

@Preview(name = "AI assistant · Result", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AssistantResultPreview() {
    ClassingTheme {
        AssistantContent(
            state = assistantPreviewState,
            onBack = {},
            onOpenAccount = {},
            onQuestionChange = {},
            onSubmit = {},
            onSelectModel = {},
            onNewConversation = {},
            onOpenConversation = {},
            assistantMessage = { Text(it) },
        )
    }
}

@Preview(name = "AI assistant · Processing", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AssistantProcessingPreview() {
    ClassingTheme(darkTheme = true) {
        AssistantContent(
            state = assistantPreviewState.copy(messages = emptyList(), sending = true),
            onBack = {},
            onOpenAccount = {},
            onQuestionChange = {},
            onSubmit = {},
            onSelectModel = {},
            onNewConversation = {},
            onOpenConversation = {},
            assistantMessage = { Text(it) },
        )
    }
}

@Preview(name = "AI assistant · Large font", widthDp = 390, heightDp = 844, fontScale = 2f, showBackground = true)
@Composable
private fun AssistantLargeFontPreview() {
    ClassingTheme {
        AssistantContent(
            state = assistantPreviewState,
            onBack = {},
            onOpenAccount = {},
            onQuestionChange = {},
            onSubmit = {},
            onSelectModel = {},
            onNewConversation = {},
            onOpenConversation = {},
            assistantMessage = { Text(it) },
        )
    }
}

@Preview(name = "AI assistant · Small device", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
private fun AssistantSmallDevicePreview() {
    ClassingTheme {
        AssistantContent(
            state = assistantPreviewState,
            onBack = {},
            onOpenAccount = {},
            onQuestionChange = {},
            onSubmit = {},
            onSelectModel = {},
            onNewConversation = {},
            onOpenConversation = {},
            assistantMessage = { Text(it) },
        )
    }
}
