package com.xtawa.classingtime.screen

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

/** Konfetti's upstream burst preset, bounded to 100 particles; respects system reduced motion. */
@Composable
internal fun CelebrationOverlay(trigger: Int) {
 var visible by remember { mutableStateOf(false) }
 val parties = remember(trigger) { listOf(Party(speed = 0f, maxSpeed = 30f, damping = 0.9f, spread = 360,
  colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def), position = Position.Relative(0.5, 0.3),
  emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100))) }
 LaunchedEffect(trigger) { if (trigger > 0 && ValueAnimator.areAnimatorsEnabled()) { visible = true; delay(3000); visible = false } }
 if (visible) Popup(properties = PopupProperties(focusable = false, clippingEnabled = false)) {
  KonfettiView(modifier = Modifier.fillMaxSize(), parties = parties)
 }
}
