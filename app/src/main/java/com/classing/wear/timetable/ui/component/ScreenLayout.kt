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
    val horizontal = if (isRoundScreen()) 18.dp else 12.dp
    return PaddingValues(horizontal = horizontal, vertical = 10.dp)
}

fun Modifier.screenContentPadding(): Modifier = this
    .fillMaxSize()
    .padding(horizontal = 12.dp, vertical = 8.dp)
