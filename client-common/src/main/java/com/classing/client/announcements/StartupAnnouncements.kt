package com.classing.client.announcements

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.classing.shared.announcements.AnnouncementPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Process sessions exclude rotations and in-app camera/document picker round trips. */
object AnnouncementLaunches {
 private var registered=false
 private var external=false
 private var externalFinished=0L
 val session=MutableStateFlow(0L)
 internal val shown=mutableSetOf<String>()
 fun externalStarted(){external=true}
 fun externalFinished(){external=false;externalFinished=SystemClock.elapsedRealtime()}
 internal fun register(){if(registered)return;registered=true
  ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver{_,event->
   if(event==Lifecycle.Event.ON_START && !external && SystemClock.elapsedRealtime()-externalFinished>2000){session.value++;shown.clear()}
  })
  if(ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && session.value==0L)session.value=1
 }
}
private data class Notice(val id:String,val title:String,val content:String,val html:String,val revision:Long,val publish:Long,val expires:Long,val policy:AnnouncementPolicy)
private fun parseNotices(raw:String):List<Notice>{
 val items=JSONObject(raw).optJSONArray("announcements")?:return emptyList()
 return (0 until minOf(items.length(),20)).mapNotNull{index->
  val n=items.optJSONObject(index)?:return@mapNotNull null
  if(n.optString("presentation")!="LAUNCH")return@mapNotNull null
  val p=n.optJSONObject("deliveryPolicy")?:JSONObject()
  fun strings(key:String):List<String>{val a=p.optJSONArray(key)?:return emptyList();return (0 until a.length()).map{a.optString(it)}}
  val codes=p.optJSONArray("versionCodes")
  Notice(n.optString("announcementId"),n.optString("title"),n.optString("content"),n.optString("contentHtml"),n.optLong("updatedAt"),n.optLong("publishAt"),n.optLong("expiresAt"),
   AnnouncementPolicy(p.optString("frequency","EVERY_LAUNCH"),p.optInt("maxDisplays"),p.optInt("cooldownSeconds"),p.optLong("minVersionCode"),p.optLong("maxVersionCode"),
    if(codes==null)emptyList() else (0 until codes.length()).map{codes.optLong(it)},strings("versionNames"),strings("editions"),
    p.optInt("delaySeconds").coerceIn(0,120),p.optInt("minReadSeconds").coerceIn(0,120),p.optInt("autoCloseSeconds").coerceIn(0,86400),p.optString("style","CENTER"),p.optBoolean("dismissible",true),p.optBoolean("resetOnUpdate"),p.optString("closeLabel"),p.optString("actionLabel"),p.optString("actionUrl")))
 }.filter{it.id.isNotBlank()&&it.title.isNotBlank()}
}
private fun readURL(address:String,max:Int):ByteArray {
 val c=URL(address).openConnection() as HttpURLConnection
 c.connectTimeout=4000;c.readTimeout=5000;c.instanceFollowRedirects=false
 try {require(c.responseCode==200);return c.inputStream.use{input->
  val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=max);out.write(buffer,0,n)};out.toByteArray()
 }}finally{c.disconnect()}
}
@Composable
fun StartupAnnouncements(baseUrl:String,platform:String,versionCode:Long,versionName:String,edition:String,compact:Boolean=false) {
 val context=LocalContext.current
 val prefs=remember(context){context.getSharedPreferences("startup_announcements",Context.MODE_PRIVATE)}
 var notice by remember{mutableStateOf<Notice?>(null)}
 var dismissed by remember{mutableStateOf(false)}
 DisposableEffect(Unit){AnnouncementLaunches.register();onDispose{}}
 LaunchedEffect(baseUrl,platform,versionCode,versionName,edition){
  ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
   AnnouncementLaunches.session.collect{session->
    if(session==0L)return@collect
    val cacheKey="cache:$platform:$edition"
    val query="/api/v1/client/announcements?platform=$platform&versionCode=$versionCode&versionName=${Uri.encode(versionName)}&edition=$edition"
    val raw=withContext(Dispatchers.IO){runCatching{String(readURL(baseUrl.trimEnd('/')+query,512*1024),Charsets.UTF_8)}.getOrNull()}
    if(raw!=null)runCatching{parseNotices(raw)}.onSuccess{prefs.edit().putString(cacheKey,raw).apply()}
    val candidates=runCatching{parseNotices(raw?:prefs.getString(cacheKey,"{}").orEmpty())}.getOrDefault(emptyList())
    for(n in candidates.take(5)){
     val key=n.policy.counterKey(n.id,n.revision,versionCode);val now=System.currentTimeMillis()
     if(n.id in AnnouncementLaunches.shown||n.publish>now||n.expires>0&&n.expires<=now||!n.policy.targets(versionCode,versionName,edition)||!n.policy.eligible(prefs.getInt("count:$key",0),prefs.getLong("time:$key",0),now))continue
     delay(n.policy.delaySeconds*1000L)
     if(n.expires>0&&n.expires<=System.currentTimeMillis())continue
     notice=n;dismissed=false;AnnouncementLaunches.shown.add(n.id)
     prefs.edit().putInt("count:$key",prefs.getInt("count:$key",0)+1).putLong("time:$key",System.currentTimeMillis()).apply()
     while(!dismissed)delay(100)
     notice=null
    }
   }
  }
 }
 // A popup must not linger while the app is in the background.
 DisposableEffect(Unit){val lifecycle=ProcessLifecycleOwner.get().lifecycle;val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){notice=null;dismissed=true}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 notice?.let{n->NoticeDialog(n,baseUrl,compact){dismissed=true;notice=null}}
}
@Composable
private fun NoticeDialog(n:Notice,baseUrl:String,compact:Boolean,onClose:()->Unit){
 val context=LocalContext.current;val chinese=Locale.getDefault().language=="zh"
 var remaining by remember(n.id){mutableIntStateOf(n.policy.minReadSeconds)}
 LaunchedEffect(n.id){while(remaining>0){delay(1000);remaining--}}
 LaunchedEffect(n.id,n.policy.autoCloseSeconds){if(n.policy.autoCloseSeconds>0){delay(maxOf(n.policy.autoCloseSeconds,n.policy.minReadSeconds)*1000L);onClose()}}
 val full=n.policy.style=="FULLSCREEN";val bottom=n.policy.style=="BOTTOM"
 Dialog(onDismissRequest={if(n.policy.dismissible&&remaining==0)onClose()},properties=DialogProperties(dismissOnBackPress=n.policy.dismissible&&remaining==0,dismissOnClickOutside=n.policy.dismissible&&remaining==0,usePlatformDefaultWidth=false)){
  Box(Modifier.fillMaxSize().padding(if(compact)8.dp else 20.dp),contentAlignment=if(bottom)Alignment.BottomCenter else Alignment.Center){
   Surface(Modifier.widthIn(max=600.dp).fillMaxWidth().then(if(full)Modifier.fillMaxHeight() else Modifier.heightIn(max=if(compact)320.dp else 680.dp)),shape=RoundedCornerShape(if(compact)24.dp else 28.dp),color=MaterialTheme.colorScheme.surface){
    Column(Modifier.padding(if(compact)16.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text(n.title,style=if(compact)MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
     Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
      NoticeBody(n.html,n.content,baseUrl,compact)
     }
     if(n.policy.actionUrl.startsWith("https://")&&n.policy.actionLabel.isNotBlank())OutlinedButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(n.policy.actionUrl))) }},modifier=Modifier.fillMaxWidth()) {Text(n.policy.actionLabel)}
     Button(onClick=onClose,enabled=remaining==0,modifier=Modifier.fillMaxWidth()) {Text((n.policy.closeLabel.ifBlank{if(chinese)"知道了" else "Got it"})+(if(remaining>0)" ($remaining)" else ""))}
    }
   }
  }
 }
}
private sealed interface BodyPart {data class Text(val value:Spanned):BodyPart;data class Picture(val source:String):BodyPart}
@Composable
private fun NoticeBody(html:String,plain:String,baseUrl:String,compact:Boolean){
 val parts=remember(html,plain){
  val text=HtmlCompat.fromHtml(html.ifBlank{android.text.TextUtils.htmlEncode(plain).replace("\n","<br>")},HtmlCompat.FROM_HTML_MODE_COMPACT)
  val result=mutableListOf<BodyPart>();var position=0
  for(span in text.getSpans(0,text.length,ImageSpan::class.java).sortedBy{text.getSpanStart(it)}){
   val start=text.getSpanStart(span);val end=text.getSpanEnd(span)
   if(start>position)result.add(BodyPart.Text(text.subSequence(position,start) as Spanned))
   span.source?.let{result.add(BodyPart.Picture(it))};position=end
  }
  if(position<text.length)result.add(BodyPart.Text(text.subSequence(position,text.length) as Spanned));result
 }
 val color=MaterialTheme.colorScheme.onSurface.toArgb();val link=MaterialTheme.colorScheme.primary.toArgb()
 parts.forEach{part->when(part){
  is BodyPart.Text->AndroidView(factory={TextView(it).apply{movementMethod=LinkMovementMethod.getInstance();textSize=if(compact)13f else 16f}},update={it.text=part.value;it.setTextColor(color);it.setLinkTextColor(link)},modifier=Modifier.fillMaxWidth())
  is BodyPart.Picture->{
   val uri=remember(part.source,baseUrl){runCatching{URL(URL(baseUrl),part.source)}.getOrNull()?.takeIf{it.protocol=="https"}}
   var bitmap by remember(uri){mutableStateOf<Bitmap?>(null)}
   LaunchedEffect(uri){if(uri!=null)bitmap=withContext(Dispatchers.IO){runCatching{
    val bytes=readURL(uri.toString(),8*1024*1024);val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    require(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth.toLong()*bounds.outHeight<=24000000)
    val options=BitmapFactory.Options();val max=if(compact)512 else 1024;while(bounds.outWidth/options.inSampleSize.coerceAtLeast(1)>max||bounds.outHeight/options.inSampleSize.coerceAtLeast(1)>max)options.inSampleSize=options.inSampleSize.coerceAtLeast(1)*2
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
   }.getOrNull()}}
   bitmap?.let{Image(it.asImageBitmap(),null,modifier=Modifier.fillMaxWidth().heightIn(max=if(compact)180.dp else 360.dp))}
   if(bitmap==null)Text(if(Locale.getDefault().language=="zh")"图片加载中或暂时不可用" else "Image loading or unavailable",style=MaterialTheme.typography.bodySmall)
  }
 }}
}
