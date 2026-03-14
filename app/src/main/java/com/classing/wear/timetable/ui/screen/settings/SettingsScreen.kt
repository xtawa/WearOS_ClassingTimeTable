package com.classing.wear.timetable.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.classing.wear.timetable.R
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.ui.component.LoadingState
import com.classing.wear.timetable.ui.component.screenPadding
import com.classing.wear.timetable.ui.state.SettingsUiState
import com.classing.wear.timetable.ui.theme.ClassingTimetableTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onToggleWeekend: (Boolean) -> Unit,
    onForceFullSync: () -> Unit,
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.settings_last_sync, state.syncMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.isLoading) {
            item { LoadingState(message = stringResource(R.string.common_loading)) }
        } else {
            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_dynamic_color),
                    checked = state.preferences.dynamicColor,
                    onCheckedChange = onToggleDynamicColor,
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_reminder),
                    checked = state.preferences.remindersEnabled,
                    onCheckedChange = onToggleReminder,
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_auto_sync),
                    checked = state.preferences.autoSync,
                    onCheckedChange = onToggleAutoSync,
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_show_weekend),
                    checked = state.preferences.showWeekend,
                    onCheckedChange = onToggleWeekend,
                )
            }

            item {
                Button(onClick = onForceFullSync, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_force_full_sync))
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitchCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Preview(showBackground = true, widthDp = 220, heightDp = 220)
@Composable
private fun SettingsScreenPreview() {
    ClassingTimetableTheme(useDynamicColor = false) {
        SettingsScreen(
            state = SettingsUiState(
                isLoading = false,
                preferences = UserPreferences(),
                syncMessage = "Never synced",
            ),
            onToggleDynamicColor = {},
            onToggleReminder = {},
            onToggleAutoSync = {},
            onToggleWeekend = {},
            onForceFullSync = {},
        )
    }
}

