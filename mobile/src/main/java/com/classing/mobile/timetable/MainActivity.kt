package com.xtawa.classingtime

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.xtawa.classingtime.screen.MobileTimetableScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobileApp() }
    }
}

@Composable
private fun MobileApp() {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MobileTimetableScreen()
        }
    }
}

