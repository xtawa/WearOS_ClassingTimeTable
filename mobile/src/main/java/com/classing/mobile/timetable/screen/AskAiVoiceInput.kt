package com.xtawa.classingtime.screen

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import kotlinx.coroutines.*

/** Always encode locally as MP3, then send to the account's configured cloud transcriber. */
internal class AskAiVoiceInput(
 private val context: Context,
 private val scope: CoroutineScope,
 private val onRecording: (Boolean) -> Unit,
 private val onProcessing: (Boolean) -> Unit,
 private val onCloudAudio: (File) -> Unit,
 private val onError: (String) -> Unit,
) {
 private var recorder: AudioRecord? = null
 private var recordingJob: Job? = null
 private var file: File? = null
 @Volatile private var active = false
 @Volatile private var generation = 0
 private var timeout: Job? = null
 fun start() {
  cancel(); val current = generation; active = true; onRecording(true)
  try {
   val bufferSize = maxOf(8192, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
   val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
   recorder = audio; check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
   val output = File.createTempFile("ask-voice-", ".mp3", context.cacheDir); file = output
   audio.startRecording()
   recordingJob = scope.launch(Dispatchers.IO) {
    try {
     output.outputStream().use { out -> VoiceMp3Encoder(out).use { encoder ->
      val buffer = ShortArray(4096); var total = 0
      while (active && current == generation && total < 16000 * 59) {
       ensureActive(); val n = audio.read(buffer, 0, minOf(buffer.size, 16000 * 59 - total))
       if (n <= 0) break
       encoder.write(buffer, n); total += n
      }
     } }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
     withContext(Dispatchers.Main) { if (current == generation) { cancel(); onError(e.message ?: "Microphone unavailable") } }
    }
   }
   timeout = scope.launch { delay(60_000); if (current == generation && active) finish(false) }
  } catch (e: Exception) { cancel(); onError(e.message ?: "Microphone unavailable") }
 }
 fun finish(cancelled: Boolean) {
  if (cancelled) { cancel(); return }; if (!active) return
  active = false; timeout?.cancel(); onRecording(false); onProcessing(true)
  val audio = recorder; val output = file; val job = recordingJob; val current = generation
  runCatching { audio?.stop() }
  scope.launch {
   job?.join(); runCatching { audio?.release() }
   if (recorder === audio) recorder = null
   if (current != generation) { output?.delete(); return@launch }
   file = null
   if (output != null && output.length() > 100) onCloudAudio(output)
   else { output?.delete(); onProcessing(false); onError("No speech recorded") }
  }
 }
 fun cancel() {
  generation++; active = false; timeout?.cancel(); timeout = null
  val audio = recorder; recorder = null; runCatching { audio?.stop() }
  val output = file; file = null; val job = recordingJob; recordingJob = null
  if (job != null) { job.cancel(); job.invokeOnCompletion { runCatching { audio?.release() }; output?.delete() } }
  else { runCatching { audio?.release() }; output?.delete() }
  onRecording(false); onProcessing(false)
 }
}

internal fun wavHeader(length: Int): ByteArray = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
    put("RIFF".toByteArray()); putInt(length + 36); put("WAVEfmt ".toByteArray()); putInt(16)
    putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
    put("data".toByteArray()); putInt(length)
}.array()
