package com.xtawa.classingtime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ClassingLightColorScheme = lightColorScheme(
    primary = ClassingColors.AccentLight,
    onPrimary = ClassingColors.OnAccentLight,
    primaryContainer = ClassingColors.AmbientViolet,
    onPrimaryContainer = ClassingColors.TextPrimaryLight,
    secondary = ClassingColors.GeneralCourse,
    onSecondary = ClassingColors.OnAccentLight,
    secondaryContainer = ClassingColors.SurfaceSecondaryLight,
    onSecondaryContainer = ClassingColors.TextPrimaryLight,
    tertiary = ClassingColors.SuccessLight,
    onTertiary = ClassingColors.OnAccentLight,
    tertiaryContainer = ClassingColors.SuccessSurfaceLight,
    onTertiaryContainer = ClassingColors.TextPrimaryLight,
    error = ClassingColors.ErrorLight,
    background = ClassingColors.BackgroundLight,
    onBackground = ClassingColors.TextPrimaryLight,
    surface = ClassingColors.SurfaceLight,
    onSurface = ClassingColors.TextPrimaryLight,
    surfaceVariant = ClassingColors.SurfaceSecondaryLight,
    onSurfaceVariant = ClassingColors.TextSecondaryLight,
    outline = ClassingColors.TextTertiaryLight,
    outlineVariant = ClassingColors.OutlineSoftLight,
    surfaceTint = ClassingColors.AccentLight,
)

private val ClassingDarkColorScheme = darkColorScheme(
    primary = ClassingColors.AccentDark,
    onPrimary = ClassingColors.OnAccentDark,
    primaryContainer = ClassingColors.SurfaceStrongDark,
    onPrimaryContainer = ClassingColors.TextPrimaryDark,
    secondary = ClassingColors.TextSecondaryDark,
    onSecondary = ClassingColors.BackgroundDark,
    secondaryContainer = ClassingColors.SurfaceSecondaryDark,
    onSecondaryContainer = ClassingColors.TextPrimaryDark,
    tertiary = ClassingColors.SuccessDark,
    onTertiary = ClassingColors.BackgroundDark,
    tertiaryContainer = ClassingColors.SuccessSurfaceDark,
    onTertiaryContainer = ClassingColors.TextPrimaryDark,
    error = ClassingColors.ErrorDark,
    background = ClassingColors.BackgroundDark,
    onBackground = ClassingColors.TextPrimaryDark,
    surface = ClassingColors.SurfaceDark,
    onSurface = ClassingColors.TextPrimaryDark,
    surfaceVariant = ClassingColors.SurfaceSecondaryDark,
    onSurfaceVariant = ClassingColors.TextSecondaryDark,
    outline = ClassingColors.TextTertiaryDark,
    outlineVariant = ClassingColors.OutlineSoftDark,
    surfaceTint = ClassingColors.AccentDark,
)

@Composable
internal fun ClassingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> ClassingDarkColorScheme
        else -> ClassingLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClassingTypography,
        shapes = ClassingShapes,
        content = content,
    )
}
