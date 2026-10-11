package com.xtawa.classingtime.metrics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R

@Composable fun MetricsSettingsCard(onEnabled: () -> Unit = {}) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ProductMetrics.enabled(context)) }
    var status by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(ProductMetrics.export(context)) } ?: error("Cannot write export") }
            .onSuccess { status = context.getString(R.string.metrics_export_done) }.onFailure { status = context.getString(R.string.metrics_export_failed) }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Row { Text(stringResource(R.string.metrics_title), Modifier.weight(1f)); Switch(enabled, { enabled = it; ProductMetrics.setEnabled(context, it); if (it) onEnabled() }) }
        Text(stringResource(R.string.metrics_description), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = enabled, onClick = { export.launch("classing-metrics.json") }) { Text(stringResource(R.string.metrics_export)) }
        if (status.isNotBlank()) Text(status)
    } }
}
