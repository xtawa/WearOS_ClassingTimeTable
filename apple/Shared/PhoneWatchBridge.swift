import Foundation
import WatchConnectivity
import CryptoKit
import ClassingCore

@MainActor final class PhoneWatchBridge: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = PhoneWatchBridge()
    @Published var reachable = false
    @Published var pendingAuthorization: String?
    @Published var pendingBYOK: JSONValue?
    private weak var store: AppStore?
    private let pairingKey = Curve25519.KeyAgreement.PrivateKey()
    private var peerKey: Data?
    func attach(_ store: AppStore) {
        self.store = store; guard WCSession.isSupported() else { return }; let s = WCSession.default; s.delegate = self; s.activate()
    }
    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        Task { @MainActor in self.reachable = session.isReachable; if activationState == .activated { self.announceKey(); if let state = self.store?.state { self.publish(state) } } }
    }
    nonisolated func sessionReachabilityDidChange(_ session: WCSession) { Task { @MainActor in self.reachable = session.isReachable; if session.isReachable { self.announceKey() } } }
    #if os(iOS)
    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}
    nonisolated func sessionDidDeactivate(_ session: WCSession) { session.activate() }
    #endif
    private func announceKey() {
        guard WCSession.default.activationState == .activated else { return }
        try? WCSession.default.updateApplicationContext(["byokPublicKey": pairingKey.publicKey.rawRepresentation])
    }
    func publish(_ state: AppState) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated else { return }
        #if os(iOS)
        guard WCSession.default.isWatchAppInstalled else { return }
        #endif
        // File transfer handles documents larger than WatchConnectivity's message/context limits.
        do {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent("classing-transfers"); try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            for transfer in WCSession.default.outstandingFileTransfers where transfer.file.metadata?["kind"] as? String == "cloud-v2" { transfer.cancel() }
            let url = directory.appendingPathComponent(UUID().uuidString + ".json"); try Wire.encoder.encode(CloudSync.outbound(state)).write(to: url, options: .atomic)
            WCSession.default.transferFile(url, metadata: ["kind": "cloud-v2"])
        } catch { store?.error = error.localizedDescription }
    }
    nonisolated func session(_ session: WCSession, didFinish fileTransfer: WCSessionFileTransfer, error: Error?) { try? FileManager.default.removeItem(at: fileTransfer.file.fileURL) }
    nonisolated func session(_ session: WCSession, didReceive file: WCSessionFile) {
        guard file.metadata?["kind"] as? String == "cloud-v2", let data = try? Data(contentsOf: file.fileURL), data.count <= 2_097_152 else { return }
        Task { @MainActor in do { self.store?.receive(try Wire.decoder.decode(CloudDocument.self, from: data)) } catch { self.store?.error = error.localizedDescription } }
    }
    nonisolated func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) { let key = applicationContext["byokPublicKey"] as? Data; Task { @MainActor in self.peerKey = key } }
    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        let auth = message["authorizationId"] as? String, encrypted = message["encryptedBYOK"] as? Data, sender = message["senderKey"] as? Data
        Task { @MainActor in
            if let auth = auth { self.pendingAuthorization = auth }
            if let encrypted = encrypted, let sender = sender {
                do {
                    let key = try self.symmetric(peer: sender), box = try AES.GCM.SealedBox(combined: encrypted), clear = try AES.GCM.open(box, using: key)
                    self.pendingBYOK = try JSONValue.decode(clear)
                } catch { self.store?.error = "BYOK 设备传输校验失败。" }
            }
        }
    }
    func requestApproval(_ id: String) throws { guard WCSession.default.isReachable else { throw ClassingError.invalid("请在 iPhone 上打开 Classing 后重试，或扫描手表二维码。") }; WCSession.default.sendMessage(["authorizationId": id], replyHandler: nil, errorHandler: { _ in }) }
    func approve() async {
        guard let id = pendingAuthorization, let store = store else { return }; pendingAuthorization = nil
        do { _ = try await store.api.request("POST", "/api/v1/auth/device/qr/approve", body: .object(["authorizationId": .string(id)])); store.status = "已授权，手表将建立独立会话" } catch { store.error = error.localizedDescription }
    }
    private func symmetric(peer: Data) throws -> SymmetricKey {
        let publicKey = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: peer), shared = try pairingKey.sharedSecretFromKeyAgreement(with: publicKey)
        return shared.hkdfDerivedSymmetricKey(using: SHA256.self, salt: Data("Classing Apple BYOK v1".utf8), sharedInfo: Data(), outputByteCount: 32)
    }
    func sendBYOK() async throws {
        guard let store = store, let peer = peerKey, WCSession.default.isReachable else { throw ClassingError.invalid("请先在两台设备上打开 Classing。") }
        let config = try store.byok.forWatch(), profile = try config.selected("CHAT"), secret = try await store.vault.secret(store.byokKeyName(profile.id))
        guard !secret.isEmpty else { throw ClassingError.invalid("未配置 BYOK 密钥。") }
        let value = JSONValue.object(["config": try JSONValue.encode(config), "key": .string(secret), "sentAt": .int(Wire.milliseconds())])
        let encrypted = try AES.GCM.seal(value.data(), using: symmetric(peer: peer)).combined!
        WCSession.default.sendMessage(["encryptedBYOK": encrypted, "senderKey": pairingKey.publicKey.rawRepresentation], replyHandler: nil, errorHandler: { _ in })
    }
    func acceptBYOK() async {
        guard let value = pendingBYOK, let store = store else { return }; pendingBYOK = nil
        do { guard abs(Wire.milliseconds() - value["sentAt"].int) < 120_000 else { throw ClassingError.invalid("传输已过期。") }; let config = try ByokConfiguration.decode(value["config"])
            guard config.ownerId == store.byokOwner else { throw ClassingError.invalid("两端须使用同一账号，或都未登录后再传输。") }
            let profile = try config.selected("CHAT"); _ = try BYOKClient(endpoint: URL(string: profile.baseUrl)!, model: profile.model, key: value["key"].string)
            try await store.vault.setSecret(value["key"].string, key: store.byokKeyName(profile.id)); try await store.savePrivateByok(config) } catch { store.error = error.localizedDescription }
    }
}
