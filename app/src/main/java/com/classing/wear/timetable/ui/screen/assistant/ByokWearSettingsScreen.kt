package com.classing.wear.timetable.ui.screen.assistant

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.*
import com.classing.client.byok.*
import com.classing.wear.timetable.R
import com.classing.wear.timetable.account.*
import com.classing.wear.timetable.ui.component.screenPadding
import kotlinx.coroutines.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ByokWearSettingsScreen(onBack: () -> Unit, onOpenAccount: () -> Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var owner by remember {mutableStateOf("")};var name by remember {mutableStateOf("")};var status by remember {mutableStateOf("")}
    var receiving by remember {mutableStateOf(false)};var code by remember {mutableStateOf("")}
    var job by remember {mutableStateOf<Job?>(null)};var clear by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) { WearDirectAccountSessionManager.ensureSession(context)?.let { session ->
        owner=session.userId;runCatching {ByokStore.load(context,owner)}.onSuccess {name=it.profiles.firstOrNull()?.name.orEmpty()}.onFailure {status="Private settings unavailable"}
    } }
    fun receive() { if(receiving)return;receiving=true;code="";status=context.getString(R.string.byok_receiving)
        job=scope.launch {try {
            val session=WearDirectAccountSessionManager.ensureSession(context) ?: error("Please sign in")
            if(!WearAiApiClient(context).checkByokPro(session.accessToken).getOrThrow()) {
                status=context.getString(R.string.byok_pro_required);return@launch
            }
            owner=session.userId
            ByokBluetooth.receive(context,owner) { pin -> scope.launch {code=pin} }
            name=ByokStore.load(context,owner).profiles.firstOrNull()?.name.orEmpty();status=context.getString(R.string.byok_wear_saved)
        }catch(e: TimeoutCancellationException){status=context.getString(R.string.byok_transfer_timeout)}catch(e: CancellationException){throw e}
        catch(e: Exception){status=context.getString(R.string.byok_transfer_failed)}finally {receiving=false;code="";job=null}}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted)receive() else status=context.getString(R.string.byok_bluetooth_denied) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) job?.cancel() }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);job?.cancel()}
    }
    ScalingLazyColumn(contentPadding=screenPadding()) {
        item { Text(stringResource(R.string.byok_title),style=MaterialTheme.typography.titleMedium) }
        item { Text(stringResource(R.string.byok_privacy),style=MaterialTheme.typography.bodySmall) }
        if(name.isNotBlank()) item {Text(name)}
        if(code.isNotBlank()) item { Text(stringResource(R.string.byok_code,code),style=MaterialTheme.typography.titleLarge) }
        if(status.isNotBlank()) item {Text(status,style=MaterialTheme.typography.bodySmall)}
        item { Button(enabled=!receiving&&owner.isNotBlank(),onClick={
            if(ByokBluetooth.permitted(context)) receive() else if(Build.VERSION.SDK_INT>=31)permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        },modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.byok_receive))} }
        if(owner.isBlank()) item {Button(onClick=onOpenAccount){Text(stringResource(R.string.ask_ai_sign_in_required))}}
        if(name.isNotBlank()) item {TextButton(enabled=!receiving,onClick={clear=true}){Text(stringResource(R.string.byok_clear))}}
        item {TextButton(onClick={job?.cancel();onBack()}) {Text(stringResource(R.string.detail_back))}}
    }
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text(stringResource(R.string.byok_clear))},confirmButton={TextButton(onClick={
        runCatching{ByokStore.clear(context,owner)}.onSuccess{name=""}.onFailure{status="Could not remove private settings"};clear=false
    }){Text(stringResource(R.string.byok_remove))}},dismissButton={TextButton(onClick={clear=false}){Text(stringResource(R.string.detail_back))}})
}
