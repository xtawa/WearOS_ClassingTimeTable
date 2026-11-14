package com.xtawa.classingtime.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.classing.shared.ai.*
import com.classing.client.byok.*
import com.xtawa.classingtime.R
import com.xtawa.classingtime.account.AiApiClient
import com.xtawa.classingtime.sync.AccountSessionManager
import com.xtawa.classingtime.ui.components.*
import kotlinx.coroutines.*
import java.util.UUID

internal suspend fun requireMobileByokPro(context: android.content.Context, ownerId: String) {
    val token=AccountSessionManager.ensureAccessToken(context) ?: error(context.getString(R.string.ai_import_login))
    val saved=com.xtawa.classingtime.data.MobilePrefsStore.loadSettings(context)
    check(saved.accountSummary.userId==ownerId) { "Account changed; reopen AskAI" }
    check(AiApiClient(appContext=context).usage(token).getOrThrow().isMember) { context.getString(R.string.byok_pro_required) }
}

@Composable
internal fun ByokSettingsPage(padding: PaddingValues, ownerId: String, member: Boolean, onBack: () -> Unit, onChanged: () -> Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var config by remember(ownerId) { mutableStateOf<ByokConfig?>(null) }
    var status by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) };var editing by remember { mutableStateOf<ByokProfile?>(null) }
    var devices by remember { mutableStateOf<List<ByokPairedDevice>?>(null) }
    var pin by remember { mutableStateOf<String?>(null) };var confirmation by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) };var clearing by remember {mutableStateOf(false)}
    DisposableEffect(Unit) { onDispose { confirmation?.complete(false) } }
    LaunchedEffect(ownerId) { if(ownerId.isNotBlank()) runCatching { ByokStore.load(context,ownerId) }.onSuccess { config=it }.onFailure { status="Private settings could not be opened" } }
    fun save(next: ByokConfig) {
        if(busy || !member) return
        busy=true
        scope.launch { try { requireMobileByokPro(context,ownerId);withContext(Dispatchers.IO) {ByokStore.save(context,next)};config=next;onChanged();status="" }
            catch(e: CancellationException) { throw e } catch(e: Exception) { status=e.message.orEmpty() } finally { busy=false } }
    }
    fun chooseDevices() { runCatching { ByokBluetooth.devices(context) }.onSuccess { devices=it }.onFailure { status=it.message.orEmpty() } }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if(allowed) chooseDevices() else status=context.getString(R.string.byok_bluetooth_denied) }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ClassingPageHeader(title=stringResource(R.string.byok_title), onBack=onBack, backLabel=stringResource(R.string.assistant_back), supportingText="Pro · BYOK")
        ClassingInformationIsland {
            Text(stringResource(R.string.byok_privacy),style=MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.byok_provider_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!member) Text(stringResource(R.string.byok_pro_required),color=MaterialTheme.colorScheme.primary)
        if(status.isNotBlank()) Text(status,color=MaterialTheme.colorScheme.error)
        if(config==null && ownerId.isNotBlank()) TextButton(enabled=!busy,onClick={clearing=true}) {Text(stringResource(R.string.byok_clear))}
        config?.let { current ->
            Row { Text(stringResource(R.string.byok_enable),Modifier.weight(1f));Switch(current.enabled,{ save(current.copy(enabled=it)) },enabled=member&&!busy&&current.chatId.isNotBlank()) }
            Text(stringResource(R.string.byok_compatibility),style=MaterialTheme.typography.bodySmall)
            ByokModelPicker(stringResource(R.string.byok_chat),current.chatId,current.profiles.filter { it.role==ByokRole.CHAT },member&&!busy) { save(current.copy(chatId=it,enabled=current.enabled&&it.isNotBlank())) }
            ByokModelPicker(stringResource(R.string.byok_vision),current.visionId,current.profiles.filter { it.role==ByokRole.VISION },member&&!busy) { save(current.copy(visionId=it)) }
            ByokModelPicker(stringResource(R.string.byok_transcription),current.transcriptionId,current.profiles.filter { it.role==ByokRole.TRANSCRIPTION },member&&!busy) { save(current.copy(transcriptionId=it)) }
            ByokModelPicker(stringResource(R.string.byok_speech),current.speechId,current.profiles.filter { it.role==ByokRole.SPEECH },member&&!busy) { save(current.copy(speechId=it)) }
            ClassingInformationIsland {
                ByokModelPicker(stringResource(R.string.byok_wear_model),current.wearId,current.profiles.filter { it.role in setOf(ByokRole.CHAT,ByokRole.VISION) },member&&!busy) { save(current.copy(wearId=it)) }
                Text(stringResource(R.string.byok_wear_notice),style=MaterialTheme.typography.bodySmall)
                Button(enabled=member&&!busy&&current.wearId.isNotBlank(),onClick={
                    if(ByokBluetooth.permitted(context)) chooseDevices() else if(Build.VERSION.SDK_INT>=31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }) { Text(stringResource(R.string.byok_wear_send)) }
            }
            current.profiles.forEach { p -> ClassingInformationIsland {
                Text(p.name,style=MaterialTheme.typography.titleMedium);Text("${byokRoleLabel(p.role)} · ${p.model}",style=MaterialTheme.typography.bodySmall)
                Text(p.baseUrl,style=MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled=member&&!busy,onClick={ editing=p;editor=true }) { Text(stringResource(R.string.exam_home_edit)) }
                    TextButton(enabled=member&&!busy,onClick={ deleting=p.id }) { Text(stringResource(R.string.byok_remove)) }
                }
            } }
            Button(enabled=member&&!busy&&current.profiles.size<20,onClick={ editing=null;editor=true }) { Text(stringResource(R.string.byok_add_model)) }
            TextButton(enabled=!busy,onClick={ clearing=true }) { Text(stringResource(R.string.byok_clear)) }
        }
        if(busy) ClassingBitsLoadingDots()
    }
    if(clearing || deleting!=null) AlertDialog(onDismissRequest={clearing=false;deleting=null},title={Text(stringResource(R.string.byok_remove))},
        text={Text(if(clearing) stringResource(R.string.byok_clear) else config?.profiles?.firstOrNull {it.id==deleting}?.name.orEmpty())},
        confirmButton={TextButton(onClick={
            if(clearing) runCatching {ByokStore.clear(context,ownerId)}.onSuccess {config=ByokConfig(ownerId);onChanged()}.onFailure {status="Could not remove private settings"}
            else deleting?.let {id -> config?.let {save(it.remove(id))}}
            clearing=false;deleting=null
        }){Text(stringResource(R.string.byok_remove))}},dismissButton={TextButton(onClick={clearing=false;deleting=null}){Text(stringResource(R.string.assistant_cancel_voice))}})
    if(editor) ByokModelEditor(editing,{editor=false}) { profile ->
        val c=config ?: return@ByokModelEditor
        val next=if(editing!=null && editing?.role!=profile.role) c.remove(profile.id) else c
        save(next.copy(profiles=next.profiles.filterNot { it.id==profile.id }+profile));editor=false
    }
    devices?.let { items -> AlertDialog(onDismissRequest={ devices=null },title={Text(stringResource(R.string.byok_wear_send))},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if(items.isEmpty()) Text(stringResource(R.string.byok_no_devices))
            items.forEach { device -> TextButton(onClick={
                devices=null;busy=true
                scope.launch { try {
                    requireMobileByokPro(context,ownerId)
                    ByokBluetooth.send(context,device,config ?: error("No configuration")) { code ->
                        val gate=CompletableDeferred<Boolean>()
                        withContext(Dispatchers.Main) { confirmation=gate;pin=code }
                        withTimeout(90_000) { gate.await() }
                    }
                    status=context.getString(R.string.byok_wear_saved)
                } catch(e: TimeoutCancellationException) { status=context.getString(R.string.byok_transfer_timeout) }
                catch(e: CancellationException) { throw e } catch(e: Exception) { status=context.getString(R.string.byok_transfer_failed) }
                finally { confirmation?.complete(false);confirmation=null;pin=null;busy=false }
                }
            }) { Text(device.name) } }
        }
    },confirmButton={TextButton(onClick={devices=null}) {Text(stringResource(R.string.assistant_cancel_voice))}}) }
    pin?.let { code -> AlertDialog(onDismissRequest={ confirmation?.complete(false);pin=null },title={Text(code)},
        text={Text(stringResource(R.string.byok_confirm_code))},confirmButton={TextButton(onClick={confirmation?.complete(true);pin=null}) { Text(stringResource(R.string.byok_confirm_send)) }},
        dismissButton={TextButton(onClick={confirmation?.complete(false);pin=null}) {Text(stringResource(R.string.assistant_cancel_voice))}}) }
}

@Composable
private fun ByokModelPicker(label: String, selected: String, profiles: List<ByokProfile>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
            Text("$label · "+(profiles.firstOrNull { it.id==selected }?.name ?: stringResource(R.string.byok_not_selected)))
        }
        DropdownMenu(expanded,{expanded=false}) {
            DropdownMenuItem(text={Text(stringResource(R.string.byok_not_selected))},onClick={expanded=false;onSelect("")})
            profiles.forEach { p -> DropdownMenuItem(text={Text(p.name)},onClick={expanded=false;onSelect(p.id)}) }
        }
    }
}

@Composable
private fun ByokModelEditor(existing: ByokProfile?, onDismiss: () -> Unit, onSave: (ByokProfile) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) };var url by remember { mutableStateOf(existing?.baseUrl ?: "https://api.openai.com/v1") }
    var model by remember { mutableStateOf(existing?.model.orEmpty()) };var key by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var role by remember { mutableStateOf(existing?.role ?: ByokRole.CHAT) };var voice by remember { mutableStateOf(existing?.voice ?: "alloy") }
    var error by remember { mutableStateOf("") };var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.byok_add_model))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name,{name=it},label={Text(stringResource(R.string.byok_name))},singleLine=true)
        Box { OutlinedButton(onClick={expanded=true}) {Text(byokRoleLabel(role))};DropdownMenu(expanded,{expanded=false}) {
            ByokRole.entries.forEach { item -> DropdownMenuItem(text={Text(byokRoleLabel(item))},onClick={role=item;expanded=false}) }
        } }
        OutlinedTextField(url,{url=it},label={Text(stringResource(R.string.byok_url))},singleLine=true)
        OutlinedTextField(model,{model=it},label={Text(stringResource(R.string.byok_model_id))},singleLine=true)
        OutlinedTextField(key,{key=it},label={Text("API key")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password,autoCorrect=false),singleLine=true)
        if(role==ByokRole.SPEECH) OutlinedTextField(voice,{voice=it},label={Text(stringResource(R.string.byok_voice))},singleLine=true)
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={runCatching { onSave(ByokProfile(existing?.id ?: UUID.randomUUID().toString(),name.trim(),url.trim().trimEnd('/'),model.trim(),key.trim(),role,voice.trim())) }
        .onFailure { error=it.message.orEmpty() }}) {Text(stringResource(R.string.byok_save))}},dismissButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.assistant_cancel_voice))}})
}

@Composable
private fun byokRoleLabel(role: ByokRole): String = stringResource(when(role) {
    ByokRole.CHAT -> R.string.byok_chat
    ByokRole.VISION -> R.string.byok_vision
    ByokRole.TRANSCRIPTION -> R.string.byok_transcription
    ByokRole.SPEECH -> R.string.byok_speech
})
