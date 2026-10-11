package com.xtawa.classingtime.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.ui.theme.ClassingMotion
import kotlinx.coroutines.delay

/**
 * React Bits AnimatedContent / FadeContent + responsive card press idioms for Compose.
 * Layout size never changes during the entrance transition; render-layer-only updates.
 */
@Composable
internal fun Modifier.bitsReveal(index: Int = 0): Modifier {
    val enabled = ValueAnimator.areAnimatorsEnabled()
    val reveal = remember(index) { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(index, enabled) {
        if (!enabled) {
            reveal.snapTo(1f)
        } else {
            delay((index.coerceIn(0, 12) * ClassingMotion.Stagger).toLong())
            reveal.animateTo(1f, tween(ClassingMotion.ContentReveal))
        }
    }
    return this.graphicsLayer {
        alpha = reveal.value
        translationY = (1f - reveal.value) * 12.dp.toPx()
    }
}

/** Reuse one press spring across settings islands, action rows and interactable cards. */
@Composable
internal fun Modifier.bitsPress(pressed: Boolean, enabled: Boolean = true): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .986f else 1f,
        animationSpec = if (ValueAnimator.areAnimatorsEnabled()) ClassingMotion.responsiveSpring()
        else snap(),
        label = "bits_shared_press_scale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
