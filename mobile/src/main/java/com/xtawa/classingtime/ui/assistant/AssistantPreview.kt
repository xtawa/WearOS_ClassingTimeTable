package com.xtawa.classingtime.ui.assistant

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xtawa.classingtime.ui.theme.ClassingTheme

private val assistantPreviewState = AssistantUiState(
    loggedIn = true,
    member = true,
    hasSchedule = true,
    contextLabel = "星期一 · 4 节课 · 下一节是物理",
    question = "",
    sending = false,
    status = "",
    selectedModelId = "fast",
    models = listOf(AssistantModelUiModel("fast", "快速", "课程表简洁回答")),
    conversations = emptyList(),
    messages = listOf(
        AssistantMessageUiModel("q1", AssistantMessageRole.User, "下午安排怎么样？"),
        AssistantMessageUiModel(
            "a1",
            AssistantMessageRole.Assistant,
            "你 14:10 在 A302 上物理，15:05 在 A205 上英语。最长空闲时间是 12:05–14:10。",
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
