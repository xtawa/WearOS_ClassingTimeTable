package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.components.ClassingInformationIsland
import com.xtawa.classingtime.ui.components.ClassingPageBackground
import com.xtawa.classingtime.ui.components.ClassingPageHeader
import com.xtawa.classingtime.ui.components.ClassingSectionLabel
import com.xtawa.classingtime.ui.theme.ClassingAppearanceState
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import com.xtawa.classingtime.ui.theme.ClassingTheme
import com.xtawa.classingtime.ui.theme.ClassingThemeMode

@Composable
internal fun AppearanceSettingsPage(
    contentPadding: PaddingValues,
    state: ClassingAppearanceState,
    onStateChange: (ClassingAppearanceState) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = ClassingSpacing.referenceScreenInset)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.lg),
    ) {
        ClassingPageHeader(
            title = stringResource(R.string.settings_appearance_title),
            eyebrow = "Classing",
            supportingText = stringResource(R.string.settings_appearance_desc),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_about_back_button),
            modifier = Modifier.padding(top = ClassingSpacing.sm),
        )

        Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm)) {
            ClassingSectionLabel(stringResource(R.string.settings_theme_mode_title))
            ClassingInformationIsland {
                Text(
                    text = stringResource(R.string.settings_theme_mode_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xs),
                ) {
                    ThemeModeChip(
                        label = stringResource(R.string.settings_theme_system),
                        selected = state.themeMode == ClassingThemeMode.System,
                        onClick = { onStateChange(state.copy(themeMode = ClassingThemeMode.System)) },
                    )
                    ThemeModeChip(
                        label = stringResource(R.string.settings_theme_light),
                        selected = state.themeMode == ClassingThemeMode.Light,
                        onClick = { onStateChange(state.copy(themeMode = ClassingThemeMode.Light)) },
                    )
                    ThemeModeChip(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = state.themeMode == ClassingThemeMode.Dark,
                        onClick = { onStateChange(state.copy(themeMode = ClassingThemeMode.Dark)) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm)) {
            ClassingSectionLabel(stringResource(R.string.settings_dynamic_color_title))
            ClassingInformationIsland {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClassingSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.xxs),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.dynamicColor,
                        onCheckedChange = { onStateChange(state.copy(dynamicColor = it)) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(bottom = ClassingSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm),
        ) {
            ClassingSectionLabel(stringResource(R.string.settings_appearance_preview_title))
            ClassingInformationIsland(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Text(
                    text = stringResource(R.string.settings_appearance_preview_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_appearance_preview_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ClassingSpacing.minimumTouchTarget),
    )
}

@Preview(name = "Appearance · Light", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AppearanceLightPreview() {
    ClassingTheme(darkTheme = false) {
        ClassingPageBackground {
            AppearanceSettingsPage(
                contentPadding = PaddingValues(),
                state = ClassingAppearanceState(themeMode = ClassingThemeMode.Light),
                onStateChange = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Appearance · Dark", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AppearanceDarkPreview() {
    ClassingTheme(darkTheme = true) {
        ClassingPageBackground {
            AppearanceSettingsPage(
                contentPadding = PaddingValues(),
                state = ClassingAppearanceState(themeMode = ClassingThemeMode.Dark),
                onStateChange = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Appearance · Large font", widthDp = 390, heightDp = 844, fontScale = 1.6f, showBackground = true)
@Composable
private fun AppearanceLargeFontPreview() = AppearanceLightPreview()

@Preview(name = "Appearance · Small device", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
private fun AppearanceSmallDevicePreview() = AppearanceLightPreview()
