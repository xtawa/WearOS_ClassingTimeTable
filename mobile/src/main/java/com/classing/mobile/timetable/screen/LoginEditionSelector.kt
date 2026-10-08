package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R

@Composable
internal fun LoginEditionSelector(edition: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.account_choose_edition), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = edition == "CN", onClick = { onSelect("CN") }, enabled = enabled,
                label = { Text(stringResource(R.string.account_edition_cn)) })
            FilterChip(selected = edition == "GLOBAL", onClick = { onSelect("GLOBAL") }, enabled = enabled,
                label = { Text(stringResource(R.string.account_edition_global)) })
        }
        Text(stringResource(R.string.account_editions_separate), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
