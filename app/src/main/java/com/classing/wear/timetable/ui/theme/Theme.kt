package com.classing.wear.timetable.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val FallbackDarkScheme: ColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = IndigoOnPrimary,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = IndigoOnPrimaryContainer,
    secondary = IndigoSecondary,
    onSecondary = IndigoOnSecondary,
    secondaryContainer = IndigoSecondaryContainer,
    onSecondaryContainer = IndigoOnSecondaryContainer,
    tertiary = IndigoTertiary,
    onTertiary = IndigoOnTertiary,
    background = IndigoBackground,
    onBackground = IndigoOnBackground,
    surface = IndigoSurface,
    onSurface = IndigoOnSurface,
    surfaceVariant = IndigoSurfaceVariant,
    onSurfaceVariant = IndigoOnSurfaceVariant,
    outline = IndigoOutline,
    outlineVariant = IndigoOutlineVariant,
    surfaceContainerLow = IndigoSurfaceContainerLow,
    surfaceContainer = IndigoSurfaceContainer,
    surfaceContainerHigh = IndigoSurfaceContainerHigh,
    error = IndigoError,
)

@Composable
fun ClassingTimetableTheme(
    useDynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        FallbackDarkScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
