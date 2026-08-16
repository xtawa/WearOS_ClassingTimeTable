package com.xtawa.classingtime.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.xtawa.classingtime.ui.home.HomeAssistantUiState
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.theme.ClassingMotion
import com.xtawa.classingtime.ui.theme.ClassingRadii
import com.xtawa.classingtime.ui.theme.ClassingSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeAiPrompt(
    state: HomeAssistantUiState,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onFocusedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val radius = animateDpAsState(
        targetValue = if (state.focused) ClassingRadii.medium else ClassingRadii.pill,
        animationSpec = tween(ClassingMotion.ContentReveal),
        label = "ai_prompt_radius",
    )
    val promptContentDescription = stringResource(R.string.home_ask_schedule)

    LaunchedEffect(state.focused) {
        if (state.focused) focusRequester.requestFocus()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
    ) {
        AnimatedVisibility(
            visible = state.focused,
            enter = fadeIn(tween(ClassingMotion.ContentReveal)) + slideInVertically { it / 3 },
            exit = fadeOut(tween(ClassingMotion.Exit)) + slideOutVertically { it / 3 },
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
            ) {
                suggestions.take(3).forEach { suggestion ->
                    QuickPromptChip(
                        text = suggestion,
                        onClick = {
                            onQueryChange(suggestion)
                            onFocusedChange(true)
                        },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ClassingSpacing.promptHeight)
                .semantics { contentDescription = promptContentDescription },
            shape = RoundedCornerShape(radius.value),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(
                    start = ClassingSpacing.md,
                    end = ClassingSpacing.xxs,
                    top = ClassingSpacing.xs,
                    bottom = ClassingSpacing.xs,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ClassingSpacing.xl),
                )
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = ClassingSpacing.sm)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusedChange(it.isFocused) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = !state.focused,
                    maxLines = if (state.focused) 3 else 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (state.query.isNotBlank()) onSubmit(state.query.trim())
                        },
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.query.isBlank()) {
                                Text(
                                    text = stringResource(
                                        if (state.focused) R.string.home_ask_schedule_placeholder
                                        else R.string.home_ask_classing,
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                AnimatedVisibility(visible = state.focused) {
                    IconButton(
                        enabled = state.query.isNotBlank() && !state.processing,
                        onClick = { onSubmit(state.query.trim()) },
                        modifier = Modifier.size(ClassingSpacing.minimumTouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(R.string.home_send_schedule_question),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPromptChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(ClassingRadii.pill),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = ClassingSpacing.minimumTouchTarget)
                .padding(horizontal = ClassingSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
