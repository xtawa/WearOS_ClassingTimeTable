package com.xtawa.classingtime.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.xtawa.classingtime.ui.home.HomePhase
import com.xtawa.classingtime.ui.theme.ClassingColors
import com.xtawa.classingtime.ui.theme.ClassingMotion

@Composable
internal fun AmbientBackground(
    phase: HomePhase,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
) {
    val primaryTarget = when (phase) {
        HomePhase.Upcoming -> ClassingColors.AmbientBlue
        HomePhase.InClass -> ClassingColors.AmbientViolet
        HomePhase.Break -> ClassingColors.AmbientBlue
        HomePhase.Finished -> ClassingColors.AmbientPeach
        HomePhase.NoClasses -> ClassingColors.AmbientBlue
    }
    val secondaryTarget = when (phase) {
        HomePhase.Upcoming -> ClassingColors.AmbientViolet
        HomePhase.InClass -> ClassingColors.Physics
        HomePhase.Break -> ClassingColors.AmbientPeach
        HomePhase.Finished -> ClassingColors.SuccessSurfaceLight
        HomePhase.NoClasses -> ClassingColors.AmbientViolet
    }
    val primary by animateColorAsState(
        targetValue = primaryTarget,
        animationSpec = tween(ClassingMotion.Ambient),
        label = "home_ambient_primary",
    )
    val secondary by animateColorAsState(
        targetValue = secondaryTarget,
        animationSpec = tween(ClassingMotion.Ambient),
        label = "home_ambient_secondary",
    )
    val infinite = rememberInfiniteTransition(label = "home_ambient_breath")
    val breath by infinite.animateFloat(
        initialValue = if (motionEnabled) 0.94f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home_ambient_breath_progress",
    )
    val background = MaterialTheme.colorScheme.background

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(background)
        val firstCenter = Offset(size.width * 0.12f, size.height * 0.20f)
        val secondCenter = Offset(size.width * 0.94f, size.height * 0.56f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.24f), Color.Transparent),
                center = firstCenter,
                radius = size.maxDimension * 0.56f * breath,
            ),
            radius = size.maxDimension * 0.56f * breath,
            center = firstCenter,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.16f), Color.Transparent),
                center = secondCenter,
                radius = size.maxDimension * 0.48f,
            ),
            radius = size.maxDimension * 0.48f,
            center = secondCenter,
        )
    }
}
