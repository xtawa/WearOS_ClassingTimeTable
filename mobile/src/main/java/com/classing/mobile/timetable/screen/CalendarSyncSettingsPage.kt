package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.components.ClassingInformationIsland
import com.xtawa.classingtime.ui.components.ClassingPageHeader
import com.xtawa.classingtime.ui.theme.ClassingSpacing

@Composable
internal fun CalendarSyncSettingsPage(
    contentPadding: PaddingValues,
    preferences: CalendarBridgePreferences,
    calendars: List<SystemCalendarChoice>,
    canRead: Boolean,
    canWrite: Boolean,
    busy: Boolean,
    status: String,
    onChange: (CalendarBridgePreferences) -> Unit,
    onRequestRead: () -> Unit,
    onRequestWrite: () -> Unit,
    onSync: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding)
            .padding(horizontal = ClassingSpacing.referenceScreenInset)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClassingSpacing.lg),
    ) {
        ClassingPageHeader(title = stringResource(R.string.calendar_sync_title), eyebrow = "Classing",
            supportingText = stringResource(R.string.calendar_sync_description), onBack = onBack,
            backLabel = stringResource(R.string.settings_title))
        ClassingInformationIsland {
            CalendarOptionRow(stringResource(R.string.calendar_sync_import), preferences.importEnabled) {
                if (it && !canRead) onRequestRead() else onChange(preferences.copy(importEnabled = it))
            }
            CalendarOptionRow(stringResource(R.string.calendar_sync_home), preferences.showOnHome) {
                onChange(preferences.copy(showOnHome = it))
            }
            CalendarOptionRow(stringResource(R.string.calendar_sync_heatmap), preferences.showOnHeatmap) {
                onChange(preferences.copy(showOnHeatmap = it))
            }
        }
        ClassingInformationIsland {
            CalendarOptionRow(stringResource(R.string.calendar_sync_export), preferences.exportEnabled) {
                if (it && !canWrite) onRequestWrite() else onChange(preferences.copy(exportEnabled = it))
            }
            Text(stringResource(R.string.calendar_sync_target), style = MaterialTheme.typography.titleSmall)
            if (calendars.none { it.writable }) {
                Text(stringResource(R.string.calendar_sync_no_calendar), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                calendars.filter { it.writable }.forEach { calendar ->
                    FilterChip(selected = preferences.exportCalendarId == calendar.id,
                        onClick = { onChange(preferences.copy(exportCalendarId = calendar.id)) },
                        label = { Text(calendar.name) })
                }
            }
        }
        ClassingInformationIsland {
            Text(stringResource(R.string.calendar_sync_window), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onSync, enabled = !busy && (preferences.importEnabled || preferences.exportEnabled)) {
                Text(stringResource(R.string.calendar_sync_now))
            }
            if (busy || status.isNotBlank()) Text(
                if (busy) stringResource(R.string.calendar_sync_working) else status,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CalendarOptionRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
