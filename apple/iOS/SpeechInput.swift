import SwiftUI
import AVFoundation
import Speech
import ClassingCore
@MainActor final class SpeechInput: ObservableObject {
    @Published var recording = false
    private var engine: AVAudioEngine?, request: SFSpeechAudioBufferRecognitionRequest?, recognizerTask: SFSpeechRecognitionTask?, recorder: AVAudioRecorder?, file: URL?, deadline: Task<Void, Never>?
    private var text = "", onDevice = false
    var requiresServer: Bool { !onDevice }
    func start(byok: Bool, allowsBYOKTranscription: Bool = false) async throws {
        cancel()
        let permitted = await withCheckedContinuation { c in AVAudioSession.sharedInstance().requestRecordPermission { c.resume(returning: $0) } }
        guard permitted else { throw ClassingError.invalid("请在系统设置中允许麦克风权限。") }
        let recognizer = SFSpeechRecognizer(locale: Locale(identifier: Locale.current.identifier))
        onDevice = recognizer?.supportsOnDeviceRecognition == true
        if onDevice {
            let auth = await withCheckedContinuation { c in SFSpeechRecognizer.requestAuthorization { c.resume(returning: $0) } }
            onDevice = auth == .authorized
        }
        guard !byok || onDevice || allowsBYOKTranscription else { throw ClassingError.invalid("本机不支持离线语音识别；BYOK 模式不会将录音发送到 Classing 云端。") }
        let audio = AVAudioSession.sharedInstance(); try audio.setCategory(.record, mode: .measurement); try audio.setActive(true)
        text = ""
        if onDevice, let recognizer = recognizer {
            let request = SFSpeechAudioBufferRecognitionRequest(); request.requiresOnDeviceRecognition = true; request.shouldReportPartialResults = true
            let engine = AVAudioEngine(); let input = engine.inputNode, format = input.outputFormat(forBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in request.append(buffer) }
            recognizerTask = recognizer.recognitionTask(with: request) { [weak self] result, error in Task { @MainActor in if let result = result { self?.text = result.bestTranscription.formattedString }; if error != nil { self?.cancel() } } }
            self.request = request; self.engine = engine; engine.prepare(); try engine.start()
        } else {
            let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".m4a"); self.file = file
            // AAC frame padding must remain under the server's strict 60-second limit.
            let recorder = try AVAudioRecorder(url: file, settings: [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 16000, AVNumberOfChannelsKey: 1, AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue]); self.recorder = recorder
            guard recorder.record(forDuration: 59) else { throw ClassingError.invalid("录音启动失败。") }
        }
        recording = true
        deadline = Task { try? await Task.sleep(nanoseconds: 59_000_000_000); if !Task.isCancelled { self.engine?.stop(); self.recorder?.stop() } }
    }
    func finish(api: ClassingAPI, byok: Bool, transcriber: BYOKClient? = nil) async throws -> String {
        guard recording else { throw ClassingError.invalid("请先开始录音。") }; recording = false; deadline?.cancel(); engine?.stop(); request?.endAudio(); recorder?.stop()
        defer { cancel() }
        if onDevice { try await Task.sleep(nanoseconds: 700_000_000); guard !text.isEmpty else { throw ClassingError.invalid("未识别到语音，请重试。") }; return text }
        guard let file = file else { throw ClassingError.invalid("没有可转写的录音。") }
        if byok { guard let transcriber = transcriber else { throw ClassingError.invalid("请配置 BYOK 转写模型。"); }; return try await transcriber.transcribe(Data(contentsOf: file)) }
        let result = try await api.upload(Data(contentsOf: file), filename: "voice.m4a", mime: "audio/mp4", transcription: true); return result["text"].string
    }
    func cancel() {
        deadline?.cancel(); deadline = nil; engine?.stop(); if let engine = engine { engine.inputNode.removeTap(onBus: 0) }; engine = nil; request?.endAudio(); request = nil; recognizerTask?.cancel(); recognizerTask = nil; recorder?.stop(); recorder = nil
        if let file = file { try? FileManager.default.removeItem(at: file) }; file = nil; recording = false; try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
