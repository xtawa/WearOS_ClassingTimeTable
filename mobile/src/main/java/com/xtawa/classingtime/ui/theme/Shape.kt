package com.xtawa.classingtime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal object ClassingRadii {
    val small = 12.dp
    val medium = 20.dp
    val large = 28.dp
    val extraLarge = 36.dp
    val pill = 1000.dp
}

internal val ClassingShapes = Shapes(
    extraSmall = RoundedCornerShape(ClassingRadii.small),
    small = RoundedCornerShape(ClassingRadii.small),
    medium = RoundedCornerShape(ClassingRadii.medium),
    large = RoundedCornerShape(ClassingRadii.large),
    extraLarge = RoundedCornerShape(ClassingRadii.extraLarge),
)
