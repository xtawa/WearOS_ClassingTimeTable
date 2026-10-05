package com.xtawa.classingtime.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.account.AccountApiClient
import com.xtawa.classingtime.ui.components.ClassingPageBackground
import com.xtawa.classingtime.ui.theme.ClassingTheme

/** Debug-only native verification preview. It never sends email, creates accounts or submits tokens. */
class VerificationPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val production = intent.getBooleanExtra("production", false)
        val dark = intent.getBooleanExtra("dark", false)
        setContent {
            ClassingTheme(darkTheme = dark) {
                var siteKey by remember { mutableStateOf(if (production) "" else "1x00000000000000000000AA") }
                var open by remember { mutableStateOf(true) }
                var status by remember { mutableStateOf("Loading client verification preview") }
                LaunchedEffect(Unit) {
                    if (production) AccountApiClient().registrationSecurityConfig().fold(
                        onSuccess = { siteKey = it.turnstileSiteKey },
                        onFailure = { status = "Public verification configuration could not load" },
                    )
                }
                ClassingPageBackground {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Native verification preview")
                        Text(status)
                    }
                    if (open && siteKey.isNotBlank()) TurnstileVerificationDialog(
                        siteKey = siteKey,
                        onVerified = { status = "Client verification callback received; no registration request was sent"; open = false },
                        onDismiss = { open = false; status = "Preview dismissed" },
                    )
                }
            }
        }
    }
}
