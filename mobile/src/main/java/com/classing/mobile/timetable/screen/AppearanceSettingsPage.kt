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
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Text("09:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.appearance_preview_course), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.appearance_preview_room), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("Mon · 09:00") })
                    AssistChip(onClick = {}, label = { Text("★ Classing") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(shape = RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(stringResource(R.string.appearance_preview_question), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.appearance_preview_reply), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.assistant_title)) }

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
