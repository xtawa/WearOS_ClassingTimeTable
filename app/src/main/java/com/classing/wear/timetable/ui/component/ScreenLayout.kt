package com.classing.wear.timetable.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.ui.theme.isRoundScreen

@Composable
fun screenPadding(): PaddingValues {
    return if (isRoundScreen()) {
        // Reserve extra safe area on round screens so first/last cards are not clipped.
        PaddingValues(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 36.dp)
    } else {
        PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    }
}

fun Modifier.screenContentPadding(): Modifier = this
    .fillMaxSize()
    .padding(horizontal = 12.dp, vertical = 8.dp)
