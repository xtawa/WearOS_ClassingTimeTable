package com.xtawa.classingtime.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Native transition grammar based on React Bits AnimatedContent / FadeContent.
 *
 * Shared by onboarding and schedule day paging to avoid two subtly different
 * hand-coded directional fade implementations. No browser/GSAP dependencies.
 */
internal object ClassingBitsTransitions {
    fun horizontal(direction: Int, enabled: Boolean = true): ContentTransform {
        if (!enabled) {
            return (fadeIn(tween(0)) togetherWith fadeOut(tween(0)))
                .using(SizeTransform(clip = false))
        }
        val sign = if (direction >= 0) 1 else -1
        return (
            (slideInHorizontally(tween(ClassingMotion.ContentReveal)) { sign * it / 7 }
                + fadeIn(tween(ClassingMotion.ContentReveal))) togetherWith
            (slideOutHorizontally(tween(ClassingMotion.Exit)) { -sign * it / 11 }
                + fadeOut(tween(ClassingMotion.Exit)))
        ).using(SizeTransform(clip = false))
    }
}
