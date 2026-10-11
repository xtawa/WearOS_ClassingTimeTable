@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xtawa.classingtime.ui.assistant

import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.*
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ExpandMore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.xtawa.classingtime.ui.components.ClassingPageHeader
import androidx.compose.animation.AnimatedVisibility
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
internal fun AssistantContent(
    state: AssistantUiState,
    courseProposal: @Composable () -> Unit = {},
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
    onTakePhoto: () -> Unit = {},
    onChooseImage: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onVoiceStart: () -> Unit = {},
    onVoiceFinish: (Boolean) -> Unit = {},
    onOpenUsage: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onTogglePrompts: () -> Unit = {},
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
                NavigationDrawerItem(label = { Text(stringResource(R.string.assistant_usage_limits)) }, selected = false,
                    icon = { Icon(Icons.Rounded.Speed, null) }, onClick = { scope.launch { drawer.close() }; onOpenUsage() })
                NavigationDrawerItem(label = { Text(stringResource(R.string.assistant_settings)) }, selected = false,
                    icon = { Icon(Icons.Rounded.Settings, null) }, onClick = { scope.launch { drawer.close() }; onOpenSettings() })
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
            .consumeWindowInsets(contentPadding)
            .imePadding(),
    ) {
        AssistantHeader(onMenu = { scope.launch { drawer.open() } }, onNewConversation = onNewConversation, enabled = !busy)
        val listState = rememberLazyListState()
        var followLatest by remember { mutableStateOf(true) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, canForward) ->
                if (!canForward) followLatest = true else if (scrolling) followLatest = false
            }
        }
        LaunchedEffect(state.messages.firstOrNull()?.id) { followLatest = true }
        LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length, state.messages.lastOrNull()?.reasoning?.length, state.sending) {
            if (followLatest && !listState.isScrollInProgress) {
                val last = listState.layoutInfo.totalItemsCount - 1
                if (last >= 0) listState.scrollToItem(last)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize(),
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
                            AssistantMessageRole.User -> QueryAnchor(message, state.showImagePreviews, state.showTimestamps)
                            AssistantMessageRole.Assistant -> if (message.content.isNotBlank() || message.reasoning.isNotBlank() || !state.sending) ResultIsland {
                                if (state.showReasoning && message.reasoning.isNotBlank()) {
                                    var expanded by remember(message.id) { mutableStateOf(state.sending) }
                                    LaunchedEffect(state.sending) { if (!state.sending) expanded = false }
                                    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.ai_photo_reasoning) + if (expanded) " ▴" else " ▾") }
                                    androidx.compose.animation.AnimatedVisibility(expanded) { assistantMessage(message.reasoning) }
                                }
                                assistantMessage(message.content)
                            }
                        }
                    }
                    item { courseProposal() }
                    if (state.sending) item {
                        ProcessingIsland(
                            if (state.messages.lastOrNull()?.content?.isNotBlank() == true) AssistantActivityPhase.Responding
                            else AssistantActivityPhase.Thinking,
                        )
                    }
                    if (state.status.isNotBlank()) item { StatusIsland(state.status) }
                    if (state.usageNotice.isNotBlank()) item {
                        Text(state.usageNotice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                }
            }
        }
        if (!followLatest && state.messages.isNotEmpty()) {
            FilledTonalButton(onClick = {
                followLatest = true
                scope.launch { val last = listState.layoutInfo.totalItemsCount - 1; if (last >= 0) listState.scrollToItem(last) }
            }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Text(stringResource(R.string.assistant_latest_message)) }
        }
        }
        if (state.loggedIn) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = ClassingSpacing.referenceScreenInset), verticalAlignment = Alignment.CenterVertically) {
                if (state.models.isNotEmpty()) ModelSelector(state.models, state.selectedModelId, onSelectModel, onToggleStar, !busy, Modifier.weight(1f))
                if (!state.showPromptSuggestions) TextButton(onClick = onTogglePrompts, enabled = !state.preferencesSaving) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.assistant_show_prompts))
                }
            }
            AssistantComposer(
                question = state.question,
                enabled = !state.sending && !state.uploading && !state.transcribing,
                canSubmit = !busy && state.question.isNotBlank() &&
                    state.selectedModelId.isNotBlank(),
                onQuestionChange = onQuestionChange,
                onSubmit = { followLatest = true; onSubmit() },
                attachments = state.attachments,
                prompts = state.prompts,
                showPromptSuggestions = state.showPromptSuggestions,
                promptNotice = state.promptNotice,
                preferencesSaving = state.preferencesSaving,
                onTogglePrompts = onTogglePrompts,
                showImagePreviews = state.showImagePreviews,
                recording = state.recording,
                uploading = state.uploading,
                transcribing = state.transcribing,
                voiceLevel = state.voiceLevel,
                onAttach = onAttach,
                onTakePhoto = onTakePhoto,
                onChooseImage = onChooseImage,
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
private fun QueryAnchor(message: AssistantMessageUiModel, previewImages: Boolean, timestamps: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.widthIn(max = 320.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message.content, style = MaterialTheme.typography.bodyLarge)
                message.attachments.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (previewImages) a.preview?.let { Image(it.asImageBitmap(), a.name, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                        Text("📎 " + a.name, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (timestamps && message.createdAt > 0) Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(message.createdAt)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking_shimmer")
    val offset by transition.animateFloat(0f, 500f, infiniteRepeatable(tween(1400)), label = "thinking_offset")
    Text("thinking…", style = MaterialTheme.typography.bodyMedium.copy(brush = Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.onSurfaceVariant, Color.LightGray, MaterialTheme.colorScheme.onSurfaceVariant),
        start = Offset(offset - 200f, 0f), end = Offset(offset + 200f, 0f))),
        modifier = Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
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
private fun ProcessingIsland(phase: AssistantActivityPhase) {
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
            AssistantActivityVisual(phase = phase)
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    fadeIn(tween(ClassingMotion.ContentReveal))
                        .togetherWith(fadeOut(tween(ClassingMotion.Exit)))
                },
                label = "assistant_output_phase",
            ) { currentPhase ->
                Text(
                    text = if (currentPhase == AssistantActivityPhase.Responding)
                        stringResource(R.string.assistant_generating_answer)
                    else stringResource(R.string.assistant_reading_schedule),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    prompts: List<String>,
    showPromptSuggestions: Boolean,
    promptNotice: String,
    preferencesSaving: Boolean,
    onTogglePrompts: () -> Unit,
    showImagePreviews: Boolean,
    recording: Boolean,
    uploading: Boolean,
    transcribing: Boolean,
    voiceLevel: Float,
    onAttach: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseImage: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceFinish: (Boolean) -> Unit,
) {
    var attachmentMenuOpen by remember { mutableStateOf(false) }
    val attachmentEnabled = enabled && !recording && attachments.size < 4
    LaunchedEffect(attachmentEnabled) { if (!attachmentEnabled) attachmentMenuOpen = false }
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 80.dp)) {
                    items(attachments, key = { it.id }) { a ->
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.padding(6.dp).widthIn(max = 220.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (showImagePreviews) a.preview?.let { Image(it.asImageBitmap(), a.name, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
                                Text(a.name, Modifier.weight(1f, fill = false).padding(horizontal = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                IconButton(onClick = { onRemoveAttachment(a.id) }, enabled = enabled && !recording) { Icon(Icons.Rounded.Close, stringResource(R.string.assistant_remove_file)) }
                            }
                        }
                    }
                }
            }
            if (uploading) Text(stringResource(R.string.assistant_uploading), style = MaterialTheme.typography.bodySmall)
            AnimatedVisibility(
                visible = recording || transcribing,
                enter = fadeIn(tween(ClassingMotion.ContentReveal)),
                exit = fadeOut(tween(ClassingMotion.Exit)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.sm)) {
                    AssistantActivityVisual(
                        phase = if (recording) AssistantActivityPhase.Recording else AssistantActivityPhase.Transcribing,
                        audioLevel = voiceLevel,
                    )
                    Text(
                        stringResource(if (recording) R.string.assistant_recording_hint else R.string.assistant_transcribing),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { onVoiceFinish(true) }) {
                        Text(stringResource(R.string.assistant_cancel_voice))
                    }
                }
            }
            AnimatedVisibility(showPromptSuggestions && question.isBlank() && !recording && !transcribing && enabled && WindowInsets.ime.getBottom(LocalDensity.current) == 0 && (prompts.isNotEmpty() || promptNotice.isNotBlank())) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.assistant_recommended_prompts), style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(R.string.assistant_prompt_draft_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onTogglePrompts, enabled = !preferencesSaving) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.assistant_hide_prompts), Modifier.size(20.dp))
                            }
                        }
                        if (promptNotice.isNotBlank()) Text(promptNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(prompts, key = { it }) { prompt ->
                                SuggestionChip(onClick = { onQuestionChange(prompt) }, modifier = Modifier.widthIn(max = 260.dp),
                                    label = { Text(prompt, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) })
                            }
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(ClassingRadii.extraLarge),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                if (transcribing) Box(Modifier.fillMaxWidth().padding(ClassingSpacing.md)) {
                    Text(
                        text = stringResource(R.string.assistant_transcribing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ClassingSpacing.md, end = ClassingSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(onClick = { attachmentMenuOpen = true }, enabled = attachmentEnabled) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.assistant_attach))
                        }
                        DropdownMenu(expanded = attachmentMenuOpen, onDismissRequest = { attachmentMenuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.assistant_take_photo)) },
                                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                                onClick = { attachmentMenuOpen = false; onTakePhoto() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.assistant_choose_image)) },
                                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                onClick = { attachmentMenuOpen = false; onChooseImage() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.assistant_choose_file)) },
                                leadingIcon = { Icon(Icons.Rounded.AttachFile, null) },
                                onClick = { attachmentMenuOpen = false; onAttach() })
                        }
                    }
                    BasicTextField(
                        value = question,
                        onValueChange = onQuestionChange,
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = ClassingSpacing.md)
                            .semantics {
                                contentDescription = composerContentDescription
                            },
                        enabled = enabled,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (question.isBlank() && !recording && !transcribing && enabled && WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                                    Text(
                                        text = stringResource(R.string.assistant_message_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    VoiceInputButton(enabled, recording, voiceLevel, onVoiceStart, onVoiceFinish)
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val sorted = models.sortedByDescending { it.starred }
    Column(modifier) {
        Box {
            TextButton(onClick = { expanded = true }, enabled = enabled) {
                Text(models.firstOrNull { it.id == selectedModelId }?.name ?: stringResource(R.string.assistant_answer_model), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.ExpandMore, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                sorted.forEach { model ->
                    DropdownMenuItem(text = { Column { Text(model.name); if (model.description.isNotBlank()) Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) } }, onClick = { onSelectModel(model.id); expanded = false },
                        trailingIcon = { IconButton(onClick = { onToggleStar(model.id) }) {
                            Icon(if (model.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                stringResource(if (model.starred) R.string.assistant_unstar else R.string.assistant_star), tint = MaterialTheme.colorScheme.primary)
                        } })
                }
            }
        }

    }
}

@Composable
private fun VoiceInputButton(enabled: Boolean, recording: Boolean, voiceLevel: Float, onStart: () -> Unit, onFinish: (Boolean) -> Unit) {
    val ringLevel by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (recording) voiceLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(120),
        label = "assistant_voice_ring",
    )
    val ringTint = MaterialTheme.colorScheme.error
    IconButton(
        onClick = { if (recording) onFinish(false) else onStart() },
        enabled = enabled,
        modifier = Modifier.drawBehind {
            if (recording) {
                drawCircle(
                    color = ringTint.copy(alpha = .06f + ringLevel * .13f),
                    radius = size.minDimension * (.34f + ringLevel * .16f),
                )
            }
        },
    ) {
        Icon(
            if (recording) Icons.Rounded.StopCircle else Icons.Rounded.Mic,
            stringResource(if (recording) R.string.assistant_recording_hint else R.string.assistant_voice_hold),
            tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}
