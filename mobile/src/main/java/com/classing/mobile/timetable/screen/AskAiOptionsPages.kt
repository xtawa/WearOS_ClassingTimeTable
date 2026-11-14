package com.xtawa.classingtime.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateFloatAsState
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
internal fun AskAiPreferencesPage(padding: PaddingValues, preferences: AiPreferences, models: List<AiModelOption>, saving: Boolean, syncStatus: String, onBack: () -> Unit, onPatch: (JSONObject) -> Unit, onOpenByok: () -> Unit = {}) {
 AskAiSubpage(stringResource(R.string.assistant_settings), padding, onBack) {
  OutlinedButton(onClick = onOpenByok, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.byok_title) + " · Pro") }
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
  PreferenceSwitch(stringResource(R.string.assistant_show_prompts), stringResource(R.string.assistant_show_prompts_hint), preferences.showPromptSuggestions, !saving) { onPatch(JSONObject().put("showPromptSuggestions", it)) }
  PreferenceSwitch(stringResource(R.string.assistant_auto_prompts), stringResource(R.string.assistant_auto_prompts_warning), preferences.autoRefreshPrompts, !saving && preferences.showPromptSuggestions) { onPatch(JSONObject().put("autoRefreshPrompts", it)) }
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
internal fun AskAiUsagePage(padding: PaddingValues, usage: AiUsageSummary?, status: String, onBack: () -> Unit,
 cards: List<AiResetCard> = emptyList(), resetting: Boolean = false, resetStatus: String = "", onUseCard: (String, String) -> Unit = { _, _ -> }) {
 var code by rememberSaveable { mutableStateOf("") }
 var pendingCard by remember { mutableStateOf<String?>(null) }
 val canReset = !resetting && usage != null && usage.limit > 0 && usage.used > 0 && usage.reserved == 0

 AskAiSubpage(stringResource(R.string.assistant_usage_limits), padding, onBack) {
  usage?.let {
   val balance = com.classing.shared.ai.pointBalance(it.limit, it.used, it.reserved, it.creditAvailable, blocked = it.mode == "BLOCKED")
   com.xtawa.classingtime.ui.components.ClassingInformationIsland {
    Text(stringResource(R.string.ai_points_weekly), style = MaterialTheme.typography.labelLarge)
    Text(if (balance.weeklyRemaining == null) stringResource(R.string.account_ai_quota_unlimited) else
     stringResource(R.string.account_ai_quota_remaining, balance.weeklyRemaining ?: 0L, it.limit), style = MaterialTheme.typography.headlineSmall)
    if (it.limit > 0) com.xtawa.classingtime.ui.components.ClassingBitsProgressBar(balance.weeklyProgress)
    Text(stringResource(R.string.ai_points_breakdown, it.used, it.reserved), style = MaterialTheme.typography.bodySmall)
   }
   if(it.mode == "BLOCKED") Text(stringResource(R.string.ai_points_blocked),color=MaterialTheme.colorScheme.error)
   Text(stringResource(R.string.ai_points_credits, it.creditBalance, balance.creditsAvailable))
   balance.totalAvailable?.let { total -> Text(stringResource(R.string.ai_points_available, total), style = MaterialTheme.typography.titleMedium) }
   Text(stringResource(R.string.ai_points_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text(stringResource(R.string.ai_points_chat_cost), style = MaterialTheme.typography.bodySmall)
   if (it.creditFrozen) Text(stringResource(R.string.account_ai_credit_frozen), color = MaterialTheme.colorScheme.error)
   if (!it.isMember) Text(stringResource(R.string.account_ai_free_quota_hint))
   Text(stringResource(R.string.account_ai_quota_reset, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it.resetAt))))
   HorizontalDivider()
   Text(stringResource(R.string.assistant_transcription_cost, it.transcriptionPoints))
   Text(stringResource(R.string.assistant_suggestion_cost, it.suggestionPoints))
  } ?: Text(status.ifBlank { stringResource(R.string.account_ai_quota_loading) })
  HorizontalDivider()
  Text(stringResource(R.string.assistant_reset_cards), style = MaterialTheme.typography.titleLarge)
  Text(stringResource(R.string.assistant_reset_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  if ((usage?.reserved ?: 0) > 0) Text(stringResource(R.string.assistant_reset_busy), color = MaterialTheme.colorScheme.onSurfaceVariant)
  if (resetStatus.isNotBlank()) Text(resetStatus, style = MaterialTheme.typography.bodyMedium)
  OutlinedTextField(value = code, onValueChange = { code = it.take(100) }, label = { Text(stringResource(R.string.assistant_reset_code)) }, enabled = !resetting, singleLine = true, modifier = Modifier.fillMaxWidth())
  Button(onClick = { pendingCard = "" }, enabled = canReset && code.isNotBlank()) { Text(stringResource(R.string.assistant_reset_redeem)) }
  Text(stringResource(R.string.assistant_reset_owned, cards.size), style = MaterialTheme.typography.titleSmall)
  cards.forEach { card ->
   Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) {
      Text(card.note.ifBlank { stringResource(R.string.assistant_reset_cards) })
      if (card.expiresAt > 0) Text(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(card.expiresAt)), style = MaterialTheme.typography.bodySmall)
     }
     TextButton(onClick = { pendingCard = card.cardId }, enabled = canReset) { Text(stringResource(R.string.assistant_reset_use)) }
    }
   }
  }
 }
 pendingCard?.let { cardId ->
  AlertDialog(onDismissRequest = { pendingCard = null }, title = { Text(stringResource(R.string.assistant_reset_use)) }, text = { Text(stringResource(R.string.assistant_reset_confirm)) },
   confirmButton = { TextButton(onClick = { pendingCard = null; onUseCard(cardId, if (cardId.isBlank()) code.trim() else "") }, enabled = canReset) { Text(stringResource(R.string.assistant_reset_use)) } },
   dismissButton = { TextButton(onClick = { pendingCard = null }) { Text(stringResource(R.string.assistant_cancel_voice)) } })
 }
}
