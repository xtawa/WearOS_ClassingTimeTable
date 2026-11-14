package com.xtawa.classingtime.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.saveable.rememberSaveable
import com.classing.client.announcements.AnnouncementLaunches
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.classing.client.byok.*
import com.classing.shared.ai.*
import com.xtawa.classingtime.R
import com.xtawa.classingtime.ui.assistant.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Owned by the AskAI route, so opening a settings subpage preserves drafts and context. */
internal class ByokConversationState(initialQuestion: String) {
    val history=mutableStateOf(JSONArray())
    val historyReady=mutableStateOf(false)
    val favorites=mutableStateOf(emptySet<String>())
    val conversationId=mutableStateOf("")
    val messages=mutableStateOf(JSONArray())
    val question=mutableStateOf(initialQuestion)
    val selected=mutableStateOf("")
    val courseProposal=mutableStateOf<JSONObject?>(null)
    val examProposal=mutableStateOf<JSONObject?>(null)
    val proposalFingerprint=mutableStateOf("")
}

/** Same AssistantContent as official AskAI; conversation/model/key data never call official chat endpoints. */
@Composable
internal fun ByokAssistantPage(padding: PaddingValues, config: ByokConfig, snapshot: JSONObject, initialQuestion: String,
    onInitialQuestionConsumed: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit, onOpenAccount: () -> Unit,
    fingerprint: String, validateCourse: (JSONObject) -> Unit, applyCourse: (JSONObject) -> Result<Unit>, session: ByokConversationState) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val provider=remember { ByokProvider() }
    val latestSnapshot by rememberUpdatedState(snapshot)
    LaunchedEffect(config.chatId,config.profiles) {
        if(config.profiles.none {it.id==session.selected.value && it.role==ByokRole.CHAT}) {
            session.selected.value=config.chatId;session.conversationId.value="";session.messages.value=JSONArray()
            session.courseProposal.value=null;session.examProposal.value=null
        }
    }
    var history by session.history
    var historyReady by session.historyReady
    var favorites by session.favorites
    val latestFingerprint by rememberUpdatedState(fingerprint)
    val latestValidateCourse by rememberUpdatedState(validateCourse)
    val latestApplyCourse by rememberUpdatedState(applyCourse)
    var courseProposal by session.courseProposal
    var examProposal by session.examProposal
    var proposalFingerprint by session.proposalFingerprint
    var conversationId by session.conversationId;var messages by session.messages
    var question by session.question;var selected by session.selected
    var sending by remember { mutableStateOf(false) };var status by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<ByokImage>>(emptyList()) };var uploading by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) };var transcribing by remember { mutableStateOf(false) };var level by remember { mutableFloatStateOf(0f) }
    var audioJob by remember { mutableStateOf<Job?>(null) };var player by remember { mutableStateOf<MediaPlayer?>(null) };var speechFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(config.ownerId) {
        historyReady=false
        try { val stored=withContext(Dispatchers.IO) {ByokStore.history(context,config.ownerId) to ByokStore.favorites(context,config.ownerId)}
            history=stored.first;favorites=stored.second;historyReady=true }
        catch(e: Exception) {status="Private conversation storage could not be opened"}
    }
    LaunchedEffect(initialQuestion) { if(initialQuestion.isNotBlank()) { question=initialQuestion;onInitialQuestionConsumed() } }
    suspend fun persist() {
        if(conversationId.isBlank()) conversationId=UUID.randomUUID().toString()
        val entry=JSONObject().put("id",conversationId).put("title",messages.optJSONObject(0)?.optString("content")?.take(60).orEmpty())
            .put("modelId",selected).put("messages",messages)
        val next=JSONArray().put(entry)
        for(i in 0 until history.length()) { val old=history.getJSONObject(i);if(old.getString("id")!=conversationId) next.put(old) }
        withContext(Dispatchers.IO) {ByokStore.saveHistory(context,config.ownerId,next)};history=next
    }
    fun submit() {
        if(!historyReady||sending||recording||transcribing||uploading||question.isBlank()) return
        if(config.profiles.none {it.id==selected && it.role==ByokRole.CHAT}) {status=context.getString(R.string.byok_model_removed);return}
        val sent=question;val photos=images;val baseMessages=JSONArray(messages.toString());val sentSnapshot=latestSnapshot;val sentFingerprint=latestFingerprint
        courseProposal=null;examProposal=null
        sending=true;status="";question=""
        scope.launch { try {
            requireMobileByokPro(context,config.ownerId)
            val profile=if(photos.isNotEmpty()) config.profile(config.visionId) else config.profile(selected)
            val pending=JSONArray(baseMessages.toString()).put(JSONObject().put("role","user").put("content",sent))
            messages=pending
            val answer=provider.chat(profile,pending,sentSnapshot,photos)
            messages=JSONArray(pending.toString()).put(JSONObject().put("role","assistant").put("content",answer))
            images=emptyList()
            runCatching {
                val parsed=ByokActions.parse(answer)
                parsed.first?.let(latestValidateCourse)
                parsed.second?.let {com.classing.client.exam.ExamJson.applyProposal(com.classing.client.exam.ExamJson.list(sentSnapshot.getJSONArray("exams")),it)}
                courseProposal=parsed.first;examProposal=parsed.second;proposalFingerprint=sentFingerprint
            }.onFailure {status="The answer was received, but the change proposal could not be validated"}
            runCatching {persist()}.onFailure {status="The answer is available here, but could not be saved locally"}
        } catch(e: CancellationException) { messages=baseMessages;question=sent;throw e } catch(e: Exception) { messages=baseMessages;question=sent;status=e.message.orEmpty() }
        finally { sending=false } }
    }
    fun loadImage(uri: android.net.Uri, cameraFile: File? = null) {
        scope.launch { uploading=true;try {
            check(config.visionId.isNotBlank()) { context.getString(R.string.byok_select_vision) }
            val bytes=withContext(Dispatchers.IO) { prepareTimetablePhoto(context,uri) }
            require(images.size<4) { "Maximum four images" };images=images+ByokImage("image-${UUID.randomUUID()}",bytes)
        } catch(e: CancellationException) {throw e} catch(e: Exception) {status=e.message.orEmpty()} finally {uploading=false;cameraFile?.delete()} }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        AnnouncementLaunches.externalFinished();if(uri!=null) loadImage(uri)
    }
    var cameraPath by rememberSaveable {mutableStateOf("")}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        AnnouncementLaunches.externalFinished();val file=cameraPath.takeIf(String::isNotBlank)?.let(::File);cameraPath=""
        if(success && file!=null) loadImage(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file),file) else file?.delete()
    }
    fun takePhoto() {
        if(config.visionId.isBlank()) {status=context.getString(R.string.byok_select_vision);return}
        runCatching {val file=createAskAiCameraFile(context);cameraPath=file.absolutePath;AnnouncementLaunches.externalStarted()
            camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file))
        }.onFailure {AnnouncementLaunches.externalFinished();cameraPath.takeIf(String::isNotBlank)?.let {File(it).delete()};cameraPath="";status=context.getString(R.string.assistant_camera_unavailable)}
    }
    val voice=remember(config.ownerId,config.transcriptionId) { AskAiVoiceInput(context,scope,onRecording={recording=it;if(!it)level=0f},onProcessing={transcribing=it},
        onAudioLevel={level=it},onCloudAudio={file -> audioJob=scope.launch { transcribing=true;try {
            requireMobileByokPro(context,config.ownerId)
            val text=provider.transcribe(config.profile(config.transcriptionId),withContext(Dispatchers.IO){file.readBytes()})
            question=listOf(question,text).filter { it.isNotBlank() }.joinToString("\n")
        } catch(e: CancellationException) {throw e} catch(e: Exception){status=e.message.orEmpty()} finally {file.delete();transcribing=false;audioJob=null} }
    },onError={status=it}) }
    val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if(!allowed) status=context.getString(R.string.assistant_mic_denied)
    }
    fun stopAudio() { voice.cancel();audioJob?.cancel();player?.release();player=null;speechFile?.delete();speechFile=null }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(voice,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) stopAudio() }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer);stopAudio() }
    }
    val uiMessages=(0 until messages.length()).map { i -> messages.getJSONObject(i).let {
        AssistantMessageUiModel(i.toString(),if(it.getString("role")=="user") AssistantMessageRole.User else AssistantMessageRole.Assistant,it.getString("content"))
    } }
    val modelOptions=config.profiles.filter { it.role==ByokRole.CHAT }.map { AssistantModelUiModel(it.id,it.name,"BYOK · ${it.model}",it.id in favorites) }
    val ui=AssistantUiState(true,true,true,"BYOK · "+(modelOptions.firstOrNull {it.id==selected}?.name.orEmpty()),question,sending,status,selected,modelOptions,
        (0 until history.length()).map {i->history.getJSONObject(i).let {AssistantConversationUiModel(it.getString("id"),it.getString("title"))}},uiMessages,
        attachments=images.map { AssistantAttachmentUiModel(it.name,stringResource(R.string.byok_local_image),BitmapFactory.decodeByteArray(it.jpeg,0,it.jpeg.size)) },
        recording=recording,uploading=uploading || !historyReady,transcribing=transcribing,voiceLevel=level,showPromptSuggestions=false,
        usageNotice=stringResource(R.string.byok_provider_cost))
    AssistantContent(ui,contentPadding=padding,onBack=onBack,onOpenAccount=onOpenAccount,onQuestionChange={question=it},onSubmit=::submit,
        onSelectModel={ id -> if(id!=selected) { selected=id;conversationId="";messages=JSONArray();images=emptyList();status="";courseProposal=null;examProposal=null } },
        onNewConversation={conversationId="";messages=JSONArray();images=emptyList();status="";courseProposal=null;examProposal=null},
        onOpenConversation={ id -> (0 until history.length()).map {history.getJSONObject(it)}.firstOrNull {it.getString("id")==id}?.let {
            conversationId=id;messages=JSONArray(it.getJSONArray("messages").toString());selected=it.getString("modelId");images=emptyList();courseProposal=null;examProposal=null
            status=if(config.profiles.any {p->p.id==selected && p.role==ByokRole.CHAT}) "" else context.getString(R.string.byok_model_removed)
        } }, onToggleStar={ id ->
            val next=if(id in favorites) favorites-id else favorites+id
            runCatching {ByokStore.saveFavorites(context,config.ownerId,next)}.onSuccess {favorites=next}.onFailure {status="Could not save favorites locally"}
        }, assistantMessage={MarkdownText(ByokActions.display(it))},onOpenSettings=onSettings,onOpenUsage={status=context.getString(R.string.byok_provider_cost)},
        onAttach={AnnouncementLaunches.externalStarted();picker.launch(arrayOf("image/*"))},onChooseImage={AnnouncementLaunches.externalStarted();picker.launch(arrayOf("image/*"))},onTakePhoto=::takePhoto,
        onRemoveAttachment={ id -> images=images.filterNot {it.name==id} },
        onVoiceStart={
            if(config.transcriptionId.isBlank()) status=context.getString(R.string.byok_select_voice)
            else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) voice.start()
            else mic.launch(Manifest.permission.RECORD_AUDIO)
        },onVoiceFinish={ cancelled -> if(cancelled) audioJob?.cancel();voice.finish(cancelled) },courseProposal={
            val proposal=courseProposal ?: examProposal
            if(proposal!=null) Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.assistant_proposal_title),style=MaterialTheme.typography.titleMedium)
                val actions=proposal.getJSONArray("actions")
                for(i in 0 until actions.length()) {
                    val a=actions.getJSONObject(i);val item=a.optJSONObject("lesson") ?: a.optJSONObject("exam")
                    Text(a.getString("operation")+" · "+(item?.optString("title") ?: a.optString("examId")))
                    if(item?.has("startTime")==true) Text("${item.getInt("dayOfWeek")} · ${item.getString("startTime")}–${item.getString("endTime")}")
                    if(item?.has("startAt")==true) Text(java.time.Instant.ofEpochMilli(item.getLong("startAt")).atZone(java.time.ZoneId.of(item.getString("timezone"))).toString())
                    item?.let {Text(it.optString("location"));Text(it.optString("note"))}
                }
                Button(enabled=!sending&&!transcribing,onClick={scope.launch {
                    try {
                        requireMobileByokPro(context,config.ownerId)
                        if(proposalFingerprint!=latestFingerprint) error(context.getString(R.string.assistant_proposal_stale))
                        if(courseProposal!=null) latestApplyCourse(proposal).getOrThrow()
                        else {val current=com.classing.client.exam.ExamStore.load(context)
                            check(com.classing.client.exam.ExamJson.array(current).toString()==latestSnapshot.getJSONArray("exams").toString()) {context.getString(R.string.assistant_proposal_stale)}
                            com.classing.client.exam.ExamStore.save(context,com.classing.client.exam.ExamJson.applyProposal(current,proposal))}
                        courseProposal=null;examProposal=null;status=context.getString(R.string.assistant_proposal_applied)
                    }catch(e: CancellationException){throw e}catch(e: Exception){status=e.message.orEmpty()}
                }}) {Text(stringResource(R.string.assistant_proposal_apply))}
                TextButton(onClick={courseProposal=null;examProposal=null}) {Text(stringResource(R.string.assistant_cancel_voice))}
            }}
            if(config.speechId.isNotBlank() && uiMessages.any {it.role==AssistantMessageRole.Assistant}) {
                TextButton(enabled=!sending&&!transcribing,onClick={
                    stopAudio();audioJob=scope.launch {transcribing=true;try {
                        requireMobileByokPro(context,config.ownerId)
                        val bytes=provider.speech(config.profile(config.speechId),ByokActions.display(uiMessages.last {it.role==AssistantMessageRole.Assistant}.content).take(4096))
                        val file=File.createTempFile("byok-speech-",".mp3",context.cacheDir);speechFile=file;withContext(Dispatchers.IO){file.writeBytes(bytes)}
                        val prepared=MediaPlayer();player=prepared
                        withContext(Dispatchers.IO) {prepared.setDataSource(file.absolutePath);prepared.prepare()}
                        ensureActive();prepared.setOnCompletionListener {stopAudio()};prepared.start()
                    }catch(e: CancellationException){throw e}catch(e: Exception){status=e.message.orEmpty()}finally {transcribing=false;audioJob=null}}
                }) {Text(stringResource(R.string.byok_read_reply))}
            }
        })
}
