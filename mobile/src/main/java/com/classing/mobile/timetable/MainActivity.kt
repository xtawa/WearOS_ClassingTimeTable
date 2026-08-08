package com.xtawa.classingtime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
import com.xtawa.classingtime.sync.CloudSyncEngine

class MainActivity : ComponentActivity() {
    internal val sharedImportUri = mutableStateOf<Uri?>(null)
    internal val sharedImportMime = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
        CloudSyncEngine.schedulePeriodic(this)
        handleIncomingIntent(intent)
        setContent { MobileApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val mime = intent.type
                val uri = if (mime != null) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null
                if (uri != null) {
                    sharedImportUri.value = uri
                    sharedImportMime.value = mime
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        val isIcs = text.contains("BEGIN:VCALENDAR", ignoreCase = true)
                        sharedImportMime.value = if (isIcs) "text/calendar" else "application/json"
                        sharedImportUri.value = null
                        sharedImportText = text
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                val mime = intent.type
                if (uri != null) {
                    sharedImportUri.value = uri
                    sharedImportMime.value = mime ?: guessMimeFromUri(uri)
                }
            }
        }
    }

    private fun guessMimeFromUri(uri: Uri): String {
        val path = uri.path.orEmpty()
        return when {
            path.endsWith(".ics", ignoreCase = true) -> "text/calendar"
            path.endsWith(".json", ignoreCase = true) -> "application/json"
            else -> "application/octet-stream"
        }
    }

    companion object {
        internal var sharedImportText: String? = null
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
        primary = Color(0xFF6366F1),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE2E2FF),
        onPrimaryContainer = Color(0xFF17185F),
        secondary = Color(0xFF22C3D6),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD3F7FB),
        onSecondaryContainer = Color(0xFF063E45),
        tertiary = Color(0xFFF59E0B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE8B5),
        onTertiaryContainer = Color(0xFF4C2B00),
        error = Color(0xFFEF4444),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF8F9FD),
        onBackground = Color(0xFF17171C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF17171C),
        surfaceVariant = Color(0xFFF5F5FA),
        onSurfaceVariant = Color(0xFF5F606B),
        outline = Color(0xFF8B8C97),
        outlineVariant = Color(0xFFE4E5EC),
        inverseSurface = Color(0xFF2E3038),
        inverseOnSurface = Color(0xFFF2F3F7),
        inversePrimary = Color(0xFF9895FF),
        surfaceTint = Color(0xFF6366F1),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F9FD),
        surfaceContainer = Color(0xFFF2F3F8),
        surfaceContainerHigh = Color(0xFFEDEEF4),
        surfaceContainerHighest = Color(0xFFE7E8EF),
    )
}

private fun stitchDarkColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = Color(0xFF9895FF),
        onPrimary = Color(0xFF19175F),
        primaryContainer = Color(0xFF36346F),
        onPrimaryContainer = Color(0xFFE4E2FF),
        secondary = Color(0xFF22C3D6),
        onSecondary = Color(0xFF00363D),
        secondaryContainer = Color(0xFF173A40),
        onSecondaryContainer = Color(0xFFB4F1F7),
        tertiary = Color(0xFFF59E0B),
        onTertiary = Color(0xFF422B00),
        tertiaryContainer = Color(0xFF563C0C),
        onTertiaryContainer = Color(0xFFFFE2A7),
        error = Color(0xFFFF8A86),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF5C1D20),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0B0D14),
        onBackground = Color(0xFFF2F3F7),
        surface = Color(0xFF171A23),
        onSurface = Color(0xFFF2F3F7),
        surfaceVariant = Color(0xFF1B1E28),
        onSurfaceVariant = Color(0xFFB5B8C4),
        outline = Color(0xFF858995),
        outlineVariant = Color(0xFF292E3B),
        inverseSurface = Color(0xFFF2F3F7),
        inverseOnSurface = Color(0xFF252832),
        inversePrimary = Color(0xFF514FD6),
        surfaceTint = Color(0xFF9895FF),
        surfaceContainerLowest = Color(0xFF0B0D14),
        surfaceContainerLow = Color(0xFF11131A),
        surfaceContainer = Color(0xFF171A23),
        surfaceContainerHigh = Color(0xFF1B1E28),
        surfaceContainerHighest = Color(0xFF1E2230),
    )
}

private fun stitchTypography(): Typography {
    val headline = FontFamily.SansSerif
    val body = FontFamily.SansSerif
    return Typography(
        displayLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
    )
}

private fun stitchShapes(): Shapes {
    return Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    )
}
