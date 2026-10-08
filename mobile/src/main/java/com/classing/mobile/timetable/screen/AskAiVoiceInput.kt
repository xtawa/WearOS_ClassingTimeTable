package com.xtawa.classingtime.screen

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlinx.coroutines.*

/** Local recognition is used only when Android explicitly guarantees an on-device recognizer. */
internal class AskAiVoiceInput(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onRecording: (Boolean) -> Unit,
    private val onProcessing: (Boolean) -> Unit,
    private val onText: (String) -> Unit,
    private val onCloudAudio: (File) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null
    private var file: File? = null
    @Volatile private var active = false
    private var generation = 0
    private var released = false
    private var localResult: String? = null
    private var timeout: Job? = null

    fun start() {
        cancel()
        generation++
        val current = generation
        active = true; released = false; localResult = null
        onRecording(true)
        try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { speech ->
                    speech.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                        override fun onError(error: Int) {
                            if (current != generation) return
                            cancel(); onError("Voice recognition failed ($error). Please try again.")
                        }
                        override fun onResults(results: Bundle?) {
                            if (current != generation) return
                            localResult = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                            if (released) completeLocal()
                        }
                    })
                    speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    })
                }
            } else {
                val bufferSize = maxOf(4096, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
                val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                recorder = audio
                check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
                val output = File.createTempFile("ask-voice-", ".wav", context.cacheDir)
                file = output
                audio.startRecording()
                recordingJob = scope.launch(Dispatchers.IO) {
                    try {
                    RandomAccessFile(output, "rw").use { wav ->
                        wav.write(ByteArray(44))
                        val buffer = ByteArray(bufferSize)
                        var total = 0
                        while (active && current == generation && total < 16000 * 2 * 60) {
                            val n = audio.read(buffer, 0, minOf(buffer.size, 16000 * 2 * 60 - total))
                            if (n <= 0) break
                            wav.write(buffer, 0, n); total += n
                        }
                        wav.seek(0); wav.write(wavHeader(total))
                    }
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            if (current == generation) { cancel(); onError(e.message ?: "Microphone unavailable") }
                        }
                    }
                }
            }
            timeout = scope.launch { delay(60_000); if (current == generation && active) { cancel(); onError("Voice input is limited to 60 seconds.") } }
        } catch (e: Exception) { cancel(); onError(e.message ?: "Microphone unavailable") }
    }

    fun finish(cancelled: Boolean) {
        if (cancelled) { cancel(); return }
        if (!active) return
        released = true; active = false; timeout?.cancel(); onRecording(false)
        if (recognizer != null) {
            if (localResult != null) completeLocal() else {
                onProcessing(true)
                recognizer?.stopListening()
                val current = generation
                timeout = scope.launch { delay(12_000); if (current == generation) { cancel(); onError("Voice recognition timed out.") } }
            }
        } else {
            onProcessing(true)
            val audio = recorder
            runCatching { audio?.stop() }
            val output = file
            val job = recordingJob
            val current = generation
            scope.launch {
                job?.join(); audio?.release()
                if (recorder === audio) recorder = null
                if (current != generation) { output?.delete(); return@launch }
                file = null
                if (output != null && output.length() > 44) onCloudAudio(output) else { output?.delete(); onProcessing(false); onError("No speech recorded") }
            }
        }
    }

    private fun completeLocal() {
        val text = localResult.orEmpty().trim()
        cancel()
        if (text.isNotBlank()) onText(text) else onError("No speech recognized")
    }

    fun cancel() {
        generation++; active = false; timeout?.cancel(); timeout = null
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        val audio = recorder; recorder = null
        runCatching { audio?.stop() }
        val output = file; file = null
        val job = recordingJob; recordingJob = null
        audio?.release()
        if (job != null) { job.cancel(); job.invokeOnCompletion { output?.delete() } }
        else output?.delete()
        onRecording(false)
        onProcessing(false)
    }
}

internal fun wavHeader(length: Int): ByteArray = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
    put("RIFF".toByteArray()); putInt(length + 36); put("WAVEfmt ".toByteArray()); putInt(16)
    putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
    put("data".toByteArray()); putInt(length)
}.array()
