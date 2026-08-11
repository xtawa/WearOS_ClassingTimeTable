package com.xtawa.classingtime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
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
        // The app ships a single dark theme, so the status bar icons are always light.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
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
    MaterialTheme(
        colorScheme = classingDarkColorScheme(),
        typography = classingTypography(),
        shapes = classingShapes(),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MobileTimetableScreen()
        }
    }
}

/**
 * The single color scheme of the app.
 *
 * The redesign keeps one dark scheme only: the canvas stays near black so the timetable grid
 * and the course color blocks carry the contrast, while the primary periwinkle marks anything
 * interactive and the amber tertiary marks the lesson that is running or coming up next.
 */
private fun classingDarkColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = Color(0xFF8C9BFF),
        onPrimary = Color(0xFF101638),
        primaryContainer = Color(0xFF3A3F73),
        onPrimaryContainer = Color(0xFFDDE1FF),
        secondary = Color(0xFF4FD8C4),
        onSecondary = Color(0xFF00332C),
        secondaryContainer = Color(0xFF12473F),
        onSecondaryContainer = Color(0xFFB4F1E6),
        tertiary = Color(0xFFFFB454),
        onTertiary = Color(0xFF3D2600),
        tertiaryContainer = Color(0xFF5C3A00),
        onTertiaryContainer = Color(0xFFFFDDB0),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF7A1D1D),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0E0F13),
        onBackground = Color(0xFFF1F2F6),
        surface = Color(0xFF0E0F13),
        onSurface = Color(0xFFF1F2F6),
        surfaceVariant = Color(0xFF22242C),
        onSurfaceVariant = Color(0xFFC6C8D2),
        surfaceContainerLowest = Color(0xFF0A0B0E),
        surfaceContainerLow = Color(0xFF14161C),
        surfaceContainer = Color(0xFF191B21),
        surfaceContainerHigh = Color(0xFF22242C),
        surfaceContainerHighest = Color(0xFF2B2E38),
        outline = Color(0xFF8A8C99),
        outlineVariant = Color(0xFF3A3D47),
        inverseSurface = Color(0xFFF1F2F6),
        inverseOnSurface = Color(0xFF22242C),
        inversePrimary = Color(0xFF3A3F73),
        surfaceTint = Color(0xFF8C9BFF),
        scrim = Color(0xFF000000),
    )
}

/**
 * Typography for the redesign.
 *
 * Headings and body text stay on the system sans-serif family so Chinese and Latin text keep
 * matching metrics. Small labels switch to the monospace family because they carry clock times,
 * week numbers and counters, which have to line up column by column inside the timetable grid.
 */
private fun classingTypography(): Typography {
    val headline = FontFamily.SansSerif
    val body = FontFamily.SansSerif
    val numeric = FontFamily.Monospace
    return Typography(
        displayLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 56.sp,
            lineHeight = 60.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 44.sp,
            lineHeight = 50.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = numeric,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        ),
    )
}

private fun classingShapes(): Shapes {
    return Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(40.dp),
    )
}
