package com.xtawa.classingtime.ui.components

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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * React Bits animated-loading-dots inspired indicator for indeterminate work.
 * Designed for scoped, in-flight operations (import/sync) only.
 */
@Composable
internal fun ClassingBitsLoadingDots(modifier: Modifier = Modifier) {
    val enabled = ValueAnimator.areAnimatorsEnabled()
    val loop = if (enabled) rememberInfiniteTransition(label = "bits_loading") else null
    val phase = loop?.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(tween(1050), RepeatMode.Restart),
        label = "bits_loading_phase",
    )?.value ?: 0f
    val tint = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(width = 42.dp, height = 24.dp).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }) {
        val gap = size.width / 3.7f
        repeat(3) { index ->
            val strength = if (enabled) {
                (sin((phase * 2 * PI - index * 1.25f).toFloat()) + 1f) / 2f
            } else .7f
            drawCircle(
                color = tint.copy(alpha = .35f + .65f * strength),
                radius = (3.0f + strength * 1.4f).dp.toPx(),
                center = Offset(size.width * .5f + (index - 1) * gap, size.height * .5f),
            )
        }
    }
}
