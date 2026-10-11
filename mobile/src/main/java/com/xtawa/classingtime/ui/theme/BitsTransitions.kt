package com.xtawa.classingtime.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Native transition grammar inspired by React Bits AnimatedContent/FadeContent.
 *
 * Compose's ContentTransform.using() is a member extension of
 * AnimatedContentTransitionScope and cannot be called in this standalone factory.
 * Construct the ContentTransform explicitly to keep OOBE and date paging reusable.
 */
internal object ClassingBitsTransitions {
    fun horizontal(direction: Int, enabled: Boolean = true): ContentTransform {
        if (!enabled) {
            return ContentTransform(
                targetContentEnter = fadeIn(tween(0)),
                initialContentExit = fadeOut(tween(0)),
                sizeTransform = SizeTransform(clip = false),
            )
        }
        val sign = if (direction >= 0) 1 else -1
        return ContentTransform(
            targetContentEnter =
                slideInHorizontally(tween(ClassingMotion.ContentReveal)) { sign * it / 7 } +
                fadeIn(tween(ClassingMotion.ContentReveal)),
            initialContentExit =
                slideOutHorizontally(tween(ClassingMotion.Exit)) { -sign * it / 11 } +
                fadeOut(tween(ClassingMotion.Exit)),
            sizeTransform = SizeTransform(clip = false),
        )
    }
}
