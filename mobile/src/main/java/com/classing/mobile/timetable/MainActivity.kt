package com.xtawa.classingtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtawa.classingtime.screen.MobileTimetableScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobileApp() }
    }
}

@Composable
private fun MobileApp() {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) stitchDarkColorScheme() else stitchLightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = stitchTypography(),
        shapes = stitchShapes(),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MobileTimetableScreen()
        }
    }
}

private fun stitchLightColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = Color(0xFF24389C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF3F51B5),
        onPrimaryContainer = Color(0xFFCACFFF),
        secondary = Color(0xFF565C84),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC9CFFD),
        onSecondaryContainer = Color(0xFF51577F),
        tertiary = Color(0xFF6C3400),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF8F4700),
        onTertiaryContainer = Color(0xFFFFC7A2),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFFBF8FF),
        onBackground = Color(0xFF1A1B22),
        surface = Color(0xFFFBF8FF),
        onSurface = Color(0xFF1A1B22),
        surfaceVariant = Color(0xFFE3E1EA),
        onSurfaceVariant = Color(0xFF454652),
        outline = Color(0xFF757684),
        outlineVariant = Color(0xFFC5C5D4),
        inverseSurface = Color(0xFF2F3037),
        inverseOnSurface = Color(0xFFF2EFF9),
        inversePrimary = Color(0xFFBAC3FF),
        surfaceTint = Color(0xFF4355B9),
    )
}

private fun stitchDarkColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = Color(0xFFBAC3FF),
        onPrimary = Color(0xFF00105C),
        primaryContainer = Color(0xFF293CA0),
        onPrimaryContainer = Color(0xFFDEE0FF),
        secondary = Color(0xFFBEC4F2),
        onSecondary = Color(0xFF272D53),
        secondaryContainer = Color(0xFF3E446B),
        onSecondaryContainer = Color(0xFFDEE0FF),
        tertiary = Color(0xFFFFB784),
        onTertiary = Color(0xFF4D2500),
        tertiaryContainer = Color(0xFF713700),
        onTertiaryContainer = Color(0xFFFFDCC6),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF121319),
        onBackground = Color(0xFFE3E1EA),
        surface = Color(0xFF121319),
        onSurface = Color(0xFFE3E1EA),
        surfaceVariant = Color(0xFF454652),
        onSurfaceVariant = Color(0xFFC5C5D4),
        outline = Color(0xFF8F909E),
        outlineVariant = Color(0xFF454652),
        inverseSurface = Color(0xFFE3E1EA),
        inverseOnSurface = Color(0xFF2F3037),
        inversePrimary = Color(0xFF4355B9),
        surfaceTint = Color(0xFFBAC3FF),
    )
}

private fun stitchTypography(): Typography {
    val headline = FontFamily.SansSerif
    val body = FontFamily.SansSerif
    return Typography(
        displayLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 56.sp,
            lineHeight = 60.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
    )
}

private fun stitchShapes(): Shapes {
    return Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(40.dp),
    )
}

