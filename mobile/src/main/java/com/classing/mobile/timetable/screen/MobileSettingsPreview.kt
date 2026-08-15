package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.xtawa.classingtime.ui.components.ClassingPageBackground
import com.xtawa.classingtime.ui.theme.ClassingTheme

@Composable
private fun SettingsPreviewFrame(darkTheme: Boolean = false) {
    ClassingTheme(darkTheme = darkTheme) {
        ClassingPageBackground {
            SettingsLayer(
                contentPadding = PaddingValues(),
                onBack = {},
                onOpenAccountPage = {},
                onOpenAskAiPage = {},
                onOpenAppearancePage = {},
                onOpenImportPage = {},
                onOpenBackupRestorePage = {},
                onOpenWeekModePage = {},
                onOpenReminderKeepAlivePage = {},
                onOpenSyncCommunicationPage = {},
                onOpenAboutPage = {},
                onClearAllSchedules = {},
            )
        }
    }
}

@Preview(name = "Settings · Light", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsLightPreview() = SettingsPreviewFrame()

@Preview(name = "Settings · Dark", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsDarkPreview() = SettingsPreviewFrame(darkTheme = true)

@Preview(name = "Settings · Large font", widthDp = 390, heightDp = 844, fontScale = 2f, showBackground = true)
@Composable
private fun SettingsLargeFontPreview() = SettingsPreviewFrame()

@Preview(name = "Settings · Small device", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
private fun SettingsSmallDevicePreview() = SettingsPreviewFrame()
