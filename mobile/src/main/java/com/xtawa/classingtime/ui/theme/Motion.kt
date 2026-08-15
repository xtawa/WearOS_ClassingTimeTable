package com.xtawa.classingtime.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

internal object ClassingMotion {
    const val Micro = 140
    const val ContentReveal = 260
    const val SharedMorph = 400
    const val LayoutReflow = 540
    const val Exit = 210
    const val Stagger = 80
    const val Ambient = 1050

    fun <T> settledSpring() = spring<T>(
        dampingRatio = 0.86f,
        stiffness = 420f,
    )

    fun <T> responsiveSpring() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = 560f,
    )

    fun <T> softSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 280f,
    )
}
