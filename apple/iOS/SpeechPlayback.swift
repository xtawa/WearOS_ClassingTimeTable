import SwiftUI
import AVFoundation
import ClassingCore
@MainActor final class SpeechPlayback: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var playing = false
    private var active: UUID?
    private var player: AVAudioPlayer?, task: Task<Void, Never>?, continuation: CheckedContinuation<Void, Error>?
    func read(_ text: String, store: AppStore) {
        cancel(); let token = UUID(); active = token; playing = true
        task = Task { defer { if active == token { playing = false; task = nil; active = nil; try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) } }
            do {
                let profile = try store.byok.selected("SPEECH"), client = try await store.byokClient(profile)
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: .duckOthers); try AVAudioSession.sharedInstance().setActive(true)
                var remaining = text[...]
                while !remaining.isEmpty {
                    try Task.checkCancellation(); let chunk = String(remaining.prefix(4096)); remaining = remaining.dropFirst(chunk.count)
                    let audio = try await client.speech(chunk, voice: profile.voice); try Task.checkCancellation()
                    try await play(audio)
                }
            } catch { if !Task.isCancelled { store.report(error) } }
        }
    }
    private func play(_ data: Data) async throws {
        let player = try AVAudioPlayer(data: data); player.delegate = self; self.player = player
        try await withCheckedThrowingContinuation { c in continuation = c; if !player.play() { continuation = nil; c.resume(throwing: ClassingError.invalid("无法播放供应商返回的音频。")) } }
    }
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) { Task { @MainActor in guard player === self.player else { return }; let c = self.continuation; self.continuation = nil; if flag { c?.resume() } else { c?.resume(throwing: ClassingError.invalid("朗读音频播放失败。")) } } }
    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) { Task { @MainActor in guard player === self.player else { return }; let c = self.continuation; self.continuation = nil; c?.resume(throwing: ClassingError.invalid("供应商未返回有效音频。")) } }
    func cancel() { active = nil; task?.cancel(); task = nil; player?.stop(); player = nil; let c = continuation; continuation = nil; c?.resume(throwing: CancellationError()); playing = false }
}
