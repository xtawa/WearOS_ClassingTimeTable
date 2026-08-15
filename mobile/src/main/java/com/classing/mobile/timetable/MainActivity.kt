package com.xtawa.classingtime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import com.xtawa.classingtime.screen.MobileTimetableScreen
import com.xtawa.classingtime.sync.CloudSyncEngine
import com.xtawa.classingtime.ui.theme.ClassingTheme
import com.xtawa.classingtime.ui.theme.ClassingAppearanceState
import com.xtawa.classingtime.ui.theme.ClassingAppearanceStore
import com.xtawa.classingtime.ui.theme.ClassingThemeMode

class MainActivity : ComponentActivity() {
    internal val sharedImportUri = mutableStateOf<Uri?>(null)
    internal val sharedImportMime = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    val context = LocalContext.current
    var appearance by remember { mutableStateOf(ClassingAppearanceStore.load(context)) }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearance.themeMode) {
        ClassingThemeMode.System -> systemDark
        ClassingThemeMode.Light -> false
        ClassingThemeMode.Dark -> true
    }
    fun updateAppearance(state: ClassingAppearanceState) {
        appearance = state
        ClassingAppearanceStore.save(context, state)
    }
    ClassingTheme(
        darkTheme = darkTheme,
        dynamicColor = appearance.dynamicColor,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MobileTimetableScreen(
                appearanceState = appearance,
                onAppearanceStateChange = ::updateAppearance,
            )
        }
    }
}
