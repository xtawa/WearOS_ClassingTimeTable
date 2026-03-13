package com.classing.wear.timetable.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun isRoundScreen(): Boolean {
    return (LocalConfiguration.current.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK) == Configuration.SCREENLAYOUT_ROUND_YES
}
