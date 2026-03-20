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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    onToggleShowCompletedToday: (Boolean) -> Unit,
    onToggleTileShowTeacher: (Boolean) -> Unit,
    onToggleTileShowLocation: (Boolean) -> Unit,
    onToggleTileShowCountdown: (Boolean) -> Unit,
    onToggleTileShowCourseName: (Boolean) -> Unit,
    onToggleTileShowCurrentWeek: (Boolean) -> Unit,
    onToggleTileShowTimeRange: (Boolean) -> Unit,
    onForceFullSync: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val haptic = LocalHapticFeedback.current

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
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleDynamicColor(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_reminder),
                    checked = state.preferences.remindersEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleReminder(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_auto_sync),
                    checked = state.preferences.autoSync,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleAutoSync(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_show_weekend),
                    checked = state.preferences.showWeekend,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleWeekend(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_show_completed_today),
                    checked = state.preferences.showCompletedToday,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleShowCompletedToday(it)
                    },
                )
            }

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
                        Text(
                            text = stringResource(R.string.settings_tile_template_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_tile_show_course_name),
                    checked = state.preferences.tileShowCourseName,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleTileShowCourseName(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_tile_show_current_week),
                    checked = state.preferences.tileShowCurrentWeek,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleTileShowCurrentWeek(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_tile_show_time_range),
                    checked = state.preferences.tileShowTimeRange,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleTileShowTimeRange(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_tile_show_teacher),
                    checked = state.preferences.tileShowTeacher,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleTileShowTeacher(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_tile_show_location),
                    checked = state.preferences.tileShowLocation,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleTileShowLocation(it)
                    },
                )
            }

            item {
                PreferenceSwitchCard(
                    title = stringResource(R.string.settings_tile_show_countdown),
                    checked = state.preferences.tileShowCountdown,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleTileShowCountdown(it)
                    },
                )
            }

            item {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onForceFullSync()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
            onToggleShowCompletedToday = {},
            onToggleTileShowTeacher = {},
            onToggleTileShowLocation = {},
            onToggleTileShowCountdown = {},
            onToggleTileShowCourseName = {},
            onToggleTileShowCurrentWeek = {},
            onToggleTileShowTimeRange = {},
            onForceFullSync = {},
        )
    }
}

