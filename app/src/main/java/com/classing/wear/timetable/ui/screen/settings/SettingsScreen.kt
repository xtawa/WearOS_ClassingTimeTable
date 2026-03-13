package com.classing.wear.timetable.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.classing.wear.timetable.domain.repository.UserPreferences
import com.classing.wear.timetable.ui.state.SettingsUiState
import com.classing.wear.timetable.ui.component.screenPadding
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(text = "设置", style = MaterialTheme.typography.titleSmall)
        }

        item {
            PreferenceSwitchCard(
                title = "动态配色",
                checked = state.preferences.dynamicColor,
                onCheckedChange = onToggleDynamicColor,
            )
        }

        item {
            PreferenceSwitchCard(
                title = "上课提醒",
                checked = state.preferences.remindersEnabled,
                onCheckedChange = onToggleReminder,
            )
        }

        item {
            PreferenceSwitchCard(
                title = "自动同步",
                checked = state.preferences.autoSync,
                onCheckedChange = onToggleAutoSync,
            )
        }

        item {
            PreferenceSwitchCard(
                title = "显示周末",
                checked = state.preferences.showWeekend,
                onCheckedChange = onToggleWeekend,
            )
        }

        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = "最近同步: ${state.syncMessage}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onForceFullSync, modifier = Modifier.fillMaxWidth()) {
                        Text("立即全量同步")
                    }
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
    Card {
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
                syncMessage = "2026-03-13T10:20:30Z",
            ),
            onToggleDynamicColor = {},
            onToggleReminder = {},
            onToggleAutoSync = {},
            onToggleWeekend = {},
            onForceFullSync = {},
        )
    }
}
