package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xtawa.classingtime.R
import com.xtawa.classingtime.account.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

@Composable
private fun AskAiSubpage(title: String, padding: PaddingValues, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
 Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically) {
   IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.assistant_back)) }
   Text(title, style = MaterialTheme.typography.titleLarge)
  }
  content()
 }
}
@Composable
internal fun AskAiPreferencesPage(padding: PaddingValues, preferences: AiPreferences, models: List<AiModelOption>, saving: Boolean, syncStatus: String, onBack: () -> Unit, onPatch: (JSONObject) -> Unit) {
 AskAiSubpage(stringResource(R.string.assistant_settings), padding, onBack) {
  Text(stringResource(R.string.assistant_settings_sync), style = MaterialTheme.typography.bodyMedium)
  Text(syncStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
  var expanded by remember { mutableStateOf(false) }
  Box {
   OutlinedButton(onClick = { expanded = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
    Text(stringResource(R.string.assistant_default_model) + ": " + (models.firstOrNull { it.id == preferences.defaultModel }?.name ?: models.firstOrNull()?.name.orEmpty()))
   }
   DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    models.sortedByDescending { it.id in preferences.favoriteModels }.forEach { model ->
     DropdownMenuItem(text = { Text((if (model.id in preferences.favoriteModels) "★ " else "") + model.name) }, onClick = { expanded = false; onPatch(JSONObject().put("defaultModel", model.id)) })
    }
   }
  }
  PreferenceSwitch(stringResource(R.string.assistant_auto_prompts), stringResource(R.string.assistant_auto_prompts_warning), preferences.autoRefreshPrompts, !saving) { onPatch(JSONObject().put("autoRefreshPrompts", it)) }
  Text(stringResource(R.string.assistant_voice_cloud), style = MaterialTheme.typography.bodySmall)
  PreferenceSwitch(stringResource(R.string.assistant_image_previews), "", preferences.imagePreviews, !saving) { onPatch(JSONObject().put("imagePreviews", it)) }
  PreferenceSwitch(stringResource(R.string.assistant_show_reasoning), "", preferences.showReasoning, !saving) { onPatch(JSONObject().put("showReasoning", it)) }
  PreferenceSwitch(stringResource(R.string.assistant_timestamps), "", preferences.showTimestamps, !saving) { onPatch(JSONObject().put("showTimestamps", it)) }
 }
}
@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
 Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
  Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
   Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
   Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
  }
 }
}
@Composable
internal fun AskAiUsagePage(padding: PaddingValues, usage: AiUsageSummary?, status: String, onBack: () -> Unit) {
 AskAiSubpage(stringResource(R.string.assistant_usage_limits), padding, onBack) {
  usage?.let {
   val remaining = if (it.limit < 0) -1 else (it.limit + it.creditAvailable - it.used - it.reserved).coerceAtLeast(0)
   Text(if (remaining < 0) stringResource(R.string.account_ai_quota_unlimited) else stringResource(R.string.account_ai_quota_remaining, remaining, it.limit), style = MaterialTheme.typography.headlineSmall)
   LinearProgressIndicator(progress = { if (it.limit <= 0) 0f else ((it.used + it.reserved).toFloat() / it.limit).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
   Text(stringResource(R.string.account_ai_credit_balance, it.creditBalance))
   if (it.creditFrozen) Text(stringResource(R.string.account_ai_credit_frozen), color = MaterialTheme.colorScheme.error)
   if (!it.isMember) Text(stringResource(R.string.account_ai_free_quota_hint))
   Text(stringResource(R.string.account_ai_quota_reset, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it.resetAt))))
   HorizontalDivider()
   Text(stringResource(R.string.assistant_transcription_cost, it.transcriptionPoints))
   Text(stringResource(R.string.assistant_suggestion_cost, it.suggestionPoints))
  } ?: Text(status.ifBlank { stringResource(R.string.account_ai_quota_loading) })
 }
}
