package com.xtawa.classingtime.ui.assistant

import android.animation.ValueAnimator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Native Compose interpretation of React Bits Orb / Waves / ShinyText motion vocabulary.
 * https://github.com/DavidHDev/react-bits
 *
 * Unlike a decorative spectrum, Recording is driven by the actual PCM RMS level supplied
 * by AskAiVoiceInput. Other phases are deliberately abstract and never imply audio activity.
 * A single Canvas avoids GLSL/WebGL, network dependencies and allocations of animated views.
 */
internal enum class AssistantActivityPhase { Recording, Transcribing, Thinking, Responding }

@Composable
internal fun AssistantActivityVisual(
    phase: AssistantActivityPhase,
    modifier: Modifier = Modifier,
    audioLevel: Float = 0f,
) {
    // Follow Android's animator accessibility setting. No permanent animation when disabled.
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    val transition = if (motionEnabled) rememberInfiniteTransition(label = "assistant_activity") else null
    val travel = transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "assistant_activity_phase",
    )?.value ?: 0f
    val smoothedLevel by animateFloatAsState(
        targetValue = if (phase == AssistantActivityPhase.Recording) audioLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(if (motionEnabled) 110 else 0),
        label = "assistant_audio_level",
    )
    val accent = when (phase) {
        AssistantActivityPhase.Recording -> MaterialTheme.colorScheme.error
        AssistantActivityPhase.Transcribing -> MaterialTheme.colorScheme.secondary
        AssistantActivityPhase.Thinking -> MaterialTheme.colorScheme.primary
        AssistantActivityPhase.Responding -> MaterialTheme.colorScheme.tertiary
    }
    val background = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier.size(42.dp)) {
        val cx = size.width * .5f
        val cy = size.height * .5f
        val radius = size.minDimension * .44f
        val center = Offset(cx, cy)
        val breath = if (motionEnabled) .03f * sin((travel * 2 * PI).toFloat()) else 0f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = .24f), accent.copy(alpha = .08f), background.copy(alpha = .10f)),
                center = center,
                radius = radius * 1.12f,
            ),
            radius = radius * (1f + breath),
            center = center,
        )
        when (phase) {
            AssistantActivityPhase.Recording -> {
                // Quiet sound => short bars. These amplitudes represent microphone input,
                // with a small phase offset for visibility rather than a fake speech spectrum.
                val barWidth = size.width * .073f
                val gap = size.width * .075f
                repeat(5) { index ->
                    val weight = 1f - abs(index - 2) * .19f
                    val wave = if (motionEnabled) {
                        .78f + .22f * sin((travel * 2 * PI + index * .85f).toFloat())
                    } else 1f
                    val barHeight = size.height * (.11f + .51f * smoothedLevel * weight * wave)
                    val x = cx + (index - 2) * (barWidth + gap) - barWidth / 2f
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(x, cy - barHeight / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
            AssistantActivityPhase.Transcribing -> {
                val diameter = radius * 1.43f
                drawArc(
                    color = accent.copy(alpha = .18f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(cx - diameter / 2f, cy - diameter / 2f),
                    size = Size(diameter, diameter), style = Stroke(width = 3.dp.toPx()),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f + 360f * travel,
                    sweepAngle = 112f, useCenter = false,
                    topLeft = Offset(cx - diameter / 2f, cy - diameter / 2f),
                    size = Size(diameter, diameter), style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(accent, radius = 2.8.dp.toPx(), center = center)
            }
            AssistantActivityPhase.Thinking -> {
                // React Bits Orb-inspired layered breathing, with only two short arcs.
                drawCircle(
                    color = accent.copy(alpha = .29f),
                    radius = radius * (.42f + breath),
                    center = center,
                )
                drawCircle(color = accent, radius = radius * .19f, center = center)
                repeat(2) { i ->
                    drawArc(
                        color = accent.copy(alpha = if (i == 0) .9f else .45f),
                        startAngle = (if (i == 0) -90f else 90f) + travel * (if (i == 0) 250f else -210f),
                        sweepAngle = 85f,
                        useCenter = false,
                        topLeft = Offset(cx - radius * .75f, cy - radius * .75f),
                        size = Size(radius * 1.5f, radius * 1.5f),
                        style = Stroke(width = 2.6.dp.toPx()),
                    )
                }
            }
            AssistantActivityPhase.Responding -> {
                // A subtle streaming cursor motif; does not imply further microphone activity.
                repeat(3) { index ->
                    val pulse = if (motionEnabled) {
                        .4f + .6f * (0.5f + 0.5f * sin((travel * 2 * PI - index * .9f).toFloat()))
                    } else .8f
                    drawCircle(
                        color = accent.copy(alpha = pulse),
                        radius = (3.1f + 1.1f * pulse).dp.toPx(),
                        center = Offset(cx + (index - 1) * radius * .43f, cy),
                    )
                }
            }
        }
    }
}
