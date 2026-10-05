package com.xtawa.classingtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.xtawa.classingtime.ui.theme.ClassingColors

/** The same ambient canvas is used by Home and every secondary phone page. */
@Composable
internal fun ClassingAmbientBackdrop(
    modifier: Modifier = Modifier,
    primary: Color = ClassingColors.AmbientBlue,
    secondary: Color = ClassingColors.AmbientViolet,
    breath: Float = 1f,
) {
    val background = MaterialTheme.colorScheme.background
    Canvas(modifier.fillMaxSize()) {
        drawRect(background)
        val firstCenter = Offset(size.width * 0.12f, size.height * 0.20f)
        val secondCenter = Offset(size.width * 0.94f, size.height * 0.56f)
        val firstRadius = size.maxDimension * 0.56f * breath
        val secondRadius = size.maxDimension * 0.48f
        drawCircle(
            brush = Brush.radialGradient(listOf(primary.copy(alpha = 0.24f), Color.Transparent), firstCenter, firstRadius),
            radius = firstRadius,
            center = firstCenter,
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.16f), Color.Transparent), secondCenter, secondRadius),
            radius = secondRadius,
            center = secondCenter,
        )
    }
}
