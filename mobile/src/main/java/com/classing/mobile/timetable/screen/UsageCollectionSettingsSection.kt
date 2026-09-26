package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.components.ClassingInformationIsland
import com.xtawa.classingtime.ui.components.ClassingSectionLabel
import com.xtawa.classingtime.ui.theme.ClassingSpacing
import com.xtawa.classingtime.usage.UsageReporter

@Composable
internal fun UsageCollectionSettingsSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(UsageReporter.isEnabled(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(ClassingSpacing.sm)) {
        ClassingSectionLabel(stringResource(R.string.settings_usage_privacy_title))
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
                        text = stringResource(R.string.settings_usage_collection_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_usage_collection_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { next ->
                        enabled = next
                        UsageReporter.setEnabled(context, next)
                    },
                )
            }
        }
    }
}
