package com.classing.wear.timetable.ui.component

import android.animation.ValueAnimator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight Wear OS thinking indicator, inspired by React Bits animated text.
 * Only instantiated while a request is sending; single Canvas, no GPU shaders.
 * Honors disabled system animators (including reduced-animation settings).
 */
@Composable
fun WearAiThinkingDots(modifier: Modifier = Modifier) {
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    val infinite = if (motionEnabled) rememberInfiniteTransition(label = "wear_ai_thinking") else null
    val travel = infinite?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), repeatMode = RepeatMode.Restart),
        label = "wear_ai_dot_phase",
    )?.value ?: 0f
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(width = 30.dp, height = 20.dp)) {
        val spacing = size.width / 3.7f
        repeat(3) { index ->
            val wave = if (motionEnabled) {
                ((sin((travel * 2 * PI - index * .8f).toFloat()) + 1f) / 2f)
            } else .55f
            drawCircle(
                color = accent.copy(alpha = .35f + .65f * wave),
                radius = (2.0f + wave * 1.4f).dp.toPx(),
                center = Offset(size.width / 2f + (index - 1) * spacing, size.height / 2f),
            )
        }
    }
}
