@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xtawa.classingtime.ui.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing

private val assistantQuickPrompts = listOf(
    "What's next?",
    "What's my afternoon like?",
    "When is biology?",
    "Do I have time for lunch?",
)

@Composable
internal fun AssistantContent(
    state: AssistantUiState,
    contentPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSelectModel: (String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    assistantMessage: @Composable (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AssistantHeader(onBack = onBack, onNewConversation = onNewConversation)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = ClassingSpacing.referenceScreenInset,
                end = ClassingSpacing.referenceScreenInset,
                bottom = ClassingSpacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            item { ContextAnchor(label = state.contextLabel) }
            when {
                !state.loggedIn -> item { AccessIsland(onOpenAccount = onOpenAccount) }
                else -> {
                    if (!state.member) item { MembershipNote() }
                    if (!state.hasSchedule && state.messages.isEmpty()) item { MissingScheduleIsland() }
                    if (state.messages.isEmpty() && !state.sending) {
                        item {
                            Column(
                                modifier = Modifier.padding(vertical = ClassingSpacing.xl),
                                verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
                            ) {
                                Text(
                                    text = "What would you like to know?",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Text(
                                    text = "Ask about classes, free time, rooms, or your week.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        when (message.role) {
                            AssistantMessageRole.User -> QueryAnchor(message.content)
                            AssistantMessageRole.Assistant -> ResultIsland {
                                assistantMessage(message.content)
                            }
                        }
                    }
                    if (state.sending) item { ProcessingIsland() }
                    if (state.status.isNotBlank()) item { StatusIsland(state.status) }
                    if (state.conversations.isNotEmpty()) {
                        item {
                            ConversationHistory(
                                conversations = state.conversations,
                                onOpenConversation = onOpenConversation,
                            )
                        }
                    }
                    if (state.models.size > 1) {
                        item {
                            ModelSelector(
                                models = state.models,
                                selectedModelId = state.selectedModelId,
                                onSelectModel = onSelectModel,
                            )
                        }
                    }
                }
            }
        }
        if (state.loggedIn) {
            AssistantComposer(
                question = state.question,
                enabled = !state.sending,
                canSubmit = !state.sending && state.question.isNotBlank() &&
                    state.selectedModelId.isNotBlank() && (state.hasSchedule || state.messages.isNotEmpty()),
                onQuestionChange = onQuestionChange,
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
private fun AssistantHeader(onBack: () -> Unit, onNewConversation: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClassingSpacing.xs, vertical = ClassingSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Ask Classing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        IconButton(onClick = onNewConversation) {
            Icon(Icons.Rounded.Add, contentDescription = "New conversation")
        }
    }
}

@Composable
private fun ContextAnchor(label: String) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ClassingRadii.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ClassingSpacing.md, vertical = ClassingSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (largeText) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QueryAnchor(query: String) {
    Text(
        text = query,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ClassingSpacing.sm),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ResultIsland(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ClassingRadii.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(ClassingSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Classing answer",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            content()
        }
    }
}

@Composable
private fun ProcessingIsland() {
    val transition = rememberInfiniteTransition(label = "assistant_processing")
    val scale by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ClassingMotion.Ambient),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "assistant_processing_scale",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(ClassingRadii.large),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(ClassingSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(scale)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            Text(
                text = "Reading your schedule…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssistantComposer(
    question: String,
    enabled: Boolean,
    canSubmit: Boolean,
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                start = ClassingSpacing.referenceScreenInset,
                end = ClassingSpacing.referenceScreenInset,
                top = ClassingSpacing.sm,
                bottom = ClassingSpacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
        ) {
            if (question.isBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                ) {
                    assistantQuickPrompts.take(if (largeText) 2 else 3).forEach { prompt ->
                        FilterChip(
                            selected = false,
                            onClick = { onQuestionChange(prompt) },
                            label = { Text(prompt) },
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(ClassingRadii.extraLarge),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ClassingSpacing.md, end = ClassingSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = question,
                        onValueChange = onQuestionChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = ClassingSpacing.md)
                            .semantics {
                                contentDescription = "Ask Classing about your schedule"
                            },
                        enabled = enabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (question.isBlank()) {
                                    Text(
                                        text = "Ask about your schedule",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    AnimatedContent(
                        targetState = canSubmit,
                        transitionSpec = {
                            (scaleIn(tween(ClassingMotion.Micro)) + fadeIn())
                                .togetherWith(scaleOut(tween(ClassingMotion.Micro)) + fadeOut())
                                .using(SizeTransform(clip = false))
                        },
                        label = "assistant_send_state",
                    ) { showSend ->
                        IconButton(onClick = onSubmit, enabled = showSend) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = "Send question",
                                tint = if (showSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessIsland(onOpenAccount: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(ClassingRadii.large),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(ClassingSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            Text("Sign in to ask Classing", style = MaterialTheme.typography.headlineSmall)
            Text("Your account keeps AI usage and conversations available across sessions.")
            Button(onClick = onOpenAccount) { Text("Open account") }
        }
    }
}

@Composable
private fun MembershipNote() {
    Text(
        text = "Free account AI quota applies. Membership credit remains available only while membership is active.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MissingScheduleIsland() {
    Surface(shape = RoundedCornerShape(ClassingRadii.large), color = MaterialTheme.colorScheme.surface) {
        Text(
            text = "Import a timetable before starting a new schedule question. Existing conversations remain available.",
            modifier = Modifier.padding(ClassingSpacing.lg),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusIsland(status: String) {
    Text(
        text = status,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ModelSelector(
    models: List<AssistantModelUiModel>,
    selectedModelId: String,
    onSelectModel: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs)) {
        Text("Answer model", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
        ) {
            models.forEach { model ->
                FilterChip(
                    selected = selectedModelId == model.id,
                    onClick = { onSelectModel(model.id) },
                    label = { Text(model.name.removePrefix("DeepSeek ")) },
                )
            }
        }
        models.firstOrNull { it.id == selectedModelId }?.let {
            Text(it.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConversationHistory(
    conversations: List<AssistantConversationUiModel>,
    onOpenConversation: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs)) {
        Text("Recent context", style = MaterialTheme.typography.labelLarge)
        conversations.take(3).forEach { conversation ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenConversation(conversation.id) },
                shape = RoundedCornerShape(ClassingRadii.medium),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = conversation.title,
                    modifier = Modifier.padding(ClassingSpacing.md),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
