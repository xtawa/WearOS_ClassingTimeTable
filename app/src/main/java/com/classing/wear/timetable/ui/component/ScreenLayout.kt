package com.classing.wear.timetable.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.ui.theme.isRoundScreen
import com.classing.wear.timetable.ui.theme.ClassingWearSpacing

@Composable
fun screenPadding(): PaddingValues {
    return if (isRoundScreen()) {
        // Reserve extra safe area on round screens so first/last cards are not clipped.
        PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 34.dp)
    } else {
        PaddingValues(horizontal = ClassingWearSpacing.lg, vertical = ClassingWearSpacing.md)
    }
}

fun Modifier.screenContentPadding(): Modifier = this
    .fillMaxSize()
    .padding(horizontal = ClassingWearSpacing.lg, vertical = ClassingWearSpacing.sm)
