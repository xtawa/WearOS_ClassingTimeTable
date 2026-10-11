package com.xtawa.classingtime.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * React Bits Orb + AnimatedContent-inspired OOBE identity motion.
 *
 * The Orb shader is deliberately not embedded: the welcome orb is one lightweight
 * Canvas with two concentric arc strokes, and the final confirmation has a one-shot
 * spring-like reveal. Nothing runs when the page leaves composition or when the
 * user disables system animations.
 */
@Composable
internal fun ClassingOobeHero(
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val loop = if (animationsEnabled && !completed) rememberInfiniteTransition(label = "oobe_orb") else null
    val position = loop?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6500), RepeatMode.Restart),
        label = "oobe_orb_rotation",
    )?.value ?: 0f
    val reveal = remember(completed) { Animatable(if (animationsEnabled) 0.86f else 1f) }
    LaunchedEffect(completed, animationsEnabled) {
        if (animationsEnabled) reveal.animateTo(1f, animationSpec = tween(480))
        else reveal.snapTo(1f)
    }
    val color = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    Box(modifier = modifier.size(if (completed) 144.dp else 132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * .48f
            val breath = if (animationsEnabled && !completed)
                sin((position * 2 * PI).toFloat()) * .035f else 0f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = .20f), secondary.copy(alpha = .065f), color.copy(alpha = 0f)),
                    center = center,
                    radius = r,
                ),
                radius = r * (1f + breath),
                center = center,
            )
            if (!completed) {
                val diameter = r * 1.6f
                val topLeft = Offset(center.x - diameter / 2f, center.y - diameter / 2f)
                repeat(2) { index ->
                    drawArc(
                        color = if (index == 0) color.copy(alpha = .63f) else secondary.copy(alpha = .42f),
                        startAngle = position * (if (index == 0) 360f else -280f) + index * 165f,
                        sweepAngle = if (index == 0) 92f else 62f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = (if (index == 0) 2.5f else 1.7f).dp.toPx()),
                    )
                }
            } else {
                drawCircle(color = color.copy(alpha = .22f), radius = r * .78f, center = center, style = Stroke(2.dp.toPx()))
                drawCircle(color = secondary.copy(alpha = .40f), radius = r * .96f, center = center, style = Stroke(1.dp.toPx()))
            }
        }
        Box(
            modifier = Modifier
                .scale(reveal.value)
                .size(if (completed) 82.dp else 76.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    RoundedCornerShape(if (completed) 41.dp else 23.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(55.dp),
                    tint = color,
                )
            } else {
                Image(
                    painter = painterResource(
                        if (MaterialTheme.colorScheme.background.luminance() < .5f)
                            R.drawable.classing_icon_dark else R.drawable.classing_icon_light,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                )
            }
        }
    }
}
