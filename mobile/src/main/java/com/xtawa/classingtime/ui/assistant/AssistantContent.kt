@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xtawa.classingtime.ui.assistant

import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.*
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ExpandMore
import kotlinx.coroutines.launch
import com.xtawa.classingtime.ui.components.ClassingPageHeader
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing

@Composable
private fun assistantQuickPrompts() = listOf(
    stringResource(R.string.prompt_whats_next),
    stringResource(R.string.prompt_afternoon),
    stringResource(R.string.prompt_biology),
    stringResource(R.string.prompt_lunch),
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
    onToggleStar: (String) -> Unit = {},
    onAttach: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onVoiceStart: () -> Unit = {},
    onVoiceFinish: (Boolean) -> Unit = {},
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val busy = state.sending || state.uploading || state.transcribing || state.recording
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text(stringResource(R.string.assistant_title), Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
                NavigationDrawerItem(label = { Text(stringResource(R.string.assistant_back)) }, selected = false,
                    icon = { Icon(Icons.Rounded.ArrowBack, null) }, onClick = { scope.launch { drawer.close() }; onBack() })
                NavigationDrawerItem(label = { Text(stringResource(R.string.assistant_new_conversation)) }, selected = false,
                    icon = { Icon(Icons.Rounded.Add, null) }, onClick = { if (!busy) { onNewConversation(); scope.launch { drawer.close() } } })
                Text(stringResource(R.string.assistant_recent_context), Modifier.padding(24.dp), style = MaterialTheme.typography.labelLarge)
                LazyColumn { items(state.conversations, key = { it.id }) { conversation ->
                    NavigationDrawerItem(label = { Text(conversation.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, selected = false,
                        onClick = { if (!busy) { onOpenConversation(conversation.id); scope.launch { drawer.close() } } })
                } }
            }
        },
    ) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .navigationBarsPadding().imePadding(),
    ) {
        AssistantHeader(onMenu = { scope.launch { drawer.open() } }, onNewConversation = onNewConversation, enabled = !busy)
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
                                    text = stringResource(R.string.assistant_welcome_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Text(
                                    text = stringResource(R.string.assistant_welcome_hint),
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
                    if (state.models.isNotEmpty()) {
                        item {
                            ModelSelector(
                                models = state.models,
                                selectedModelId = state.selectedModelId,
                                onSelectModel = onSelectModel,
                                onToggleStar = onToggleStar,
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
        }
        if (state.loggedIn) {
            AssistantComposer(
                question = state.question,
                enabled = !state.sending && !state.uploading && !state.transcribing,
                canSubmit = !busy && state.question.isNotBlank() &&
                    state.selectedModelId.isNotBlank() && (state.hasSchedule || state.messages.isNotEmpty() || state.attachments.isNotEmpty()),
                onQuestionChange = onQuestionChange,
                onSubmit = onSubmit,
                attachments = state.attachments,
                recording = state.recording,
                uploading = state.uploading,
                transcribing = state.transcribing,
                onAttach = onAttach,
                onRemoveAttachment = onRemoveAttachment,
                onVoiceStart = onVoiceStart,
                onVoiceFinish = onVoiceFinish,
            )
        }
    }
    }
}

@Composable
private fun AssistantHeader(onMenu: () -> Unit, onNewConversation: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = ClassingSpacing.referenceScreenInset, vertical = ClassingSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, stringResource(R.string.assistant_menu)) }
        Text(stringResource(R.string.assistant_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNewConversation, enabled = enabled) { Icon(Icons.Rounded.Add, stringResource(R.string.assistant_new_conversation)) }
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
                    text = stringResource(R.string.assistant_answer),
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
                text = stringResource(R.string.assistant_reading_schedule),
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
    attachments: List<AssistantAttachmentUiModel>,
    recording: Boolean,
    uploading: Boolean,
    transcribing: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceFinish: (Boolean) -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val composerContentDescription = stringResource(R.string.home_ask_schedule)
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
            if (attachments.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachments.forEach { a -> InputChip(selected = false, onClick = {}, label = { Text(a.name, maxLines = 1) },
                        trailingIcon = { IconButton(onClick = { onRemoveAttachment(a.id) }, enabled = enabled && !recording) { Icon(Icons.Rounded.Close, stringResource(R.string.assistant_remove_file)) } }) }
                }
                Text(stringResource(R.string.assistant_file_retention), style = MaterialTheme.typography.bodySmall)
            }
            if (uploading || transcribing) Text(stringResource(if (uploading) R.string.assistant_uploading else R.string.assistant_transcribing), style = MaterialTheme.typography.bodySmall)
            if (recording) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.assistant_recording_hint), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onVoiceFinish(true) }) { Text(stringResource(R.string.assistant_cancel_voice)) }
            }
            if (question.isBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                ) {
                    assistantQuickPrompts().take(if (largeText) 2 else 3).forEach { prompt ->
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
                    IconButton(onClick = onAttach, enabled = enabled && !recording && attachments.size < 4) { Icon(Icons.Rounded.AttachFile, stringResource(R.string.assistant_attach)) }
                    BasicTextField(
                        value = question,
                        onValueChange = onQuestionChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = ClassingSpacing.md)
                            .semantics {
                                contentDescription = composerContentDescription
                            },
                        enabled = enabled && !recording,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (question.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.home_ask_schedule_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    VoiceInputButton(enabled, recording, onVoiceStart, onVoiceFinish)
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
                                contentDescription = stringResource(R.string.assistant_send_question),
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
            Text(stringResource(R.string.assistant_sign_in_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.assistant_sign_in_hint))
            Button(onClick = onOpenAccount) { Text(stringResource(R.string.assistant_open_account)) }
        }
    }
}

@Composable
private fun MembershipNote() {
    Text(
        text = stringResource(R.string.assistant_free_quota),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MissingScheduleIsland() {
    Surface(shape = RoundedCornerShape(ClassingRadii.large), color = MaterialTheme.colorScheme.surface) {
        Text(
            text = stringResource(R.string.assistant_missing_schedule),
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
    onToggleStar: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val sorted = models.sortedByDescending { it.starred }
    Column {
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(models.firstOrNull { it.id == selectedModelId }?.name ?: stringResource(R.string.assistant_answer_model), Modifier.weight(1f))
                Icon(Icons.Rounded.ExpandMore, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                sorted.forEach { model ->
                    DropdownMenuItem(text = { Text(model.name) }, onClick = { onSelectModel(model.id); expanded = false },
                        trailingIcon = { IconButton(onClick = { onToggleStar(model.id) }) {
                            Icon(if (model.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                stringResource(if (model.starred) R.string.assistant_unstar else R.string.assistant_star), tint = MaterialTheme.colorScheme.primary)
                        } })
                }
            }
        }
        models.firstOrNull { it.id == selectedModelId }?.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VoiceInputButton(enabled: Boolean, recording: Boolean, onStart: () -> Unit, onFinish: (Boolean) -> Unit) {
    val start by rememberUpdatedState(onStart)
    val finish by rememberUpdatedState(onFinish)
    val cancelDistance = with(LocalDensity.current) { 56.dp.toPx() }
    val label = stringResource(R.string.assistant_voice_hold)
    Box(Modifier.size(48.dp).semantics {
        contentDescription = label
        onClick { if (enabled) { if (recording) finish(false) else start() }; true }
    }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(); down.consume(); start()
            var cancelled = false
            var completed = false
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (down.position.y - change.position.y > cancelDistance) cancelled = true
                    change.consume()
                    if (!change.pressed) { finish(cancelled); completed = true; break }
                }
            } finally { if (!completed) finish(true) }
        }
    }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Mic, null, tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}
