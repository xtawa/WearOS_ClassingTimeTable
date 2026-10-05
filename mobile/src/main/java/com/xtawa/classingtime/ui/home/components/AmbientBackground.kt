package com.xtawa.classingtime.ui.home.components

import androidx.compose.runtime.getValue
import com.xtawa.classingtime.ui.components.ClassingAmbientBackdrop
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    ClassingAmbientBackdrop(modifier = modifier, primary = primary, secondary = secondary, breath = breath)
}
