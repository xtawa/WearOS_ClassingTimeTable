import Foundation
import WatchConnectivity
import CryptoKit
import ClassingCore

@MainActor final class PhoneWatchBridge: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = PhoneWatchBridge()
    @Published var reachable = false
    @Published var pendingAuthorization: String?
    @Published var pendingBYOK: JSONValue?
    @Published var deliveryStatus = "尚未开始同步"
    @Published var lastAcknowledgedAt: Date?
    @Published var accountStatus = "等待同步手机账号"
    private weak var store: AppStore?
    private let pairingKey = Curve25519.KeyAgreement.PrivateKey()
    private var peerKey: Data?
    private var latestTransferID: String?
    private var lastQueuedDigest: Data?
    private var lastQueuedTransferID: String?
    private var phoneAccountID: String?
    private var accountTask: Task<Void, Never>?
    private var accountRequestID: UUID?
    var connectionSummary: String {
        guard WCSession.isSupported() else { return "当前设备不支持手表连接" }
        let session = WCSession.default
        #if os(iOS)
        if !session.isPaired { return "尚未配对 Apple Watch" }
        if !session.isWatchAppInstalled { return "配对手表尚未安装 Classing" }
        #endif
        guard session.activationState == .activated else { return "连接正在启动" }
        return reachable ? "两端当前可直接通信" : "等待对端连接，后台传输可排队"
    }
    func attach(_ store: AppStore) {
        self.store = store; guard WCSession.isSupported() else { return }; let session = WCSession.default; session.delegate = self
        if session.activationState == .activated { ready() } else { deliveryStatus = "连接正在启动"; session.activate() }
    }
    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        Task { @MainActor in
            self.reachable = session.isReachable
            if let error { self.deliveryStatus = "连接启动失败"; self.store?.report(error) }
            else if activationState == .activated { self.ready() }
        }
    }
    nonisolated func sessionReachabilityDidChange(_ session: WCSession) { Task { @MainActor in self.reachable = session.isReachable; if session.isReachable { self.ready() } } }
    #if os(iOS)
    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}
    nonisolated func sessionDidDeactivate(_ session: WCSession) { session.activate() }
    nonisolated func sessionWatchStateDidChange(_ session: WCSession) { Task { @MainActor in self.reachable = session.isReachable; self.ready() } }
    #endif
    private func ready() {
        reachable = WCSession.default.isReachable; announceKey()
        #if os(watchOS)
        receiveContext(WCSession.default.receivedApplicationContext)
        requestPhoneAccount()
        try? requestTimetable()
        #endif
        accountDidChange()
        if let state = store?.state { publish(state, force: true) }
    }
    private func announceKey() {
        guard WCSession.default.activationState == .activated else { return }
        var context: [String: Any] = ["byokPublicKey": pairingKey.publicKey.rawRepresentation]
        #if os(iOS)
        if let id = store?.companionAccountID { context["phoneAccountId"] = id }
        #endif
        try? WCSession.default.updateApplicationContext(context)
    }
    func accountDidChange() {
        guard store?.demo == false, WCSession.isSupported() else { return }
        #if os(iOS)
        announceKey()
        if let id = store?.companionAccountID, WCSession.default.activationState == .activated, WCSession.default.isReachable {
            WCSession.default.sendMessage(["phoneAccountId": id], replyHandler: nil, errorHandler: { _ in })
        }
        #else
        synchronizeAccount()
        #endif
    }
    private func receiveContext(_ context: [String: Any]) {
        if let key = context["byokPublicKey"] as? Data { peerKey = key }
        #if os(watchOS)
        if let id = context["phoneAccountId"] as? String { receivePhoneAccount(id) }
        #endif
    }
    #if os(watchOS)
    private func requestPhoneAccount() {
        guard WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["requestAccount": true], replyHandler: { [weak self] reply in
            let id = reply["phoneAccountId"] as? String
            Task { @MainActor in if let id { self?.receivePhoneAccount(id) } }
        }, errorHandler: { _ in })
    }
    private func receivePhoneAccount(_ id: String) {
        if phoneAccountID != id { accountTask?.cancel(); accountTask = nil; accountRequestID = nil; phoneAccountID = id }
        synchronizeAccount()
    }
    func synchronizeAccount() {
        guard accountTask == nil, let store, store.accountResolved, let expected = phoneAccountID else { return }
        if store.companionAccountID == expected { accountStatus = expected.isEmpty ? "iPhone 未登录" : "已同步手机账号"; return }
        let request = UUID(); accountRequestID = request
        accountTask = Task {
            defer { if accountRequestID == request { accountTask = nil; accountRequestID = nil } }
            do {
                try await store.setCompanionSession(nil); try Task.checkCancellation()
                guard !expected.isEmpty else { accountStatus = "iPhone 已退出登录"; return }
                guard WCSession.default.isReachable else { accountStatus = "等待 iPhone 连接后同步账号"; return }
                accountStatus = "正在同步手机账号"
                let session = try await store.api.authorizeCompanion { id in try await self.approveOnPhone(id, accountID: expected) }
                try Task.checkCancellation()
                let actual = session.account["userId"].string.isEmpty ? session.account["id"].string : session.account["userId"].string
                guard phoneAccountID == expected, actual == expected else { throw ClassingError.invalid("手机账号已变化，请重新同步。") }
                try await store.setCompanionSession(session); try Task.checkCancellation(); accountStatus = "已同步手机账号"
            } catch { if accountRequestID == request && !Task.isCancelled { accountStatus = ClassingError.userMessage(error) } }
        }
    }
    private func approveOnPhone(_ id: String, accountID: String) async throws {
        try Task.checkCancellation()
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            WCSession.default.sendMessage(["autoAuthorizationId": id, "expectedAccountId": accountID], replyHandler: { [weak self] reply in
                let approved = reply["approved"] as? Bool == true, current = reply["phoneAccountId"] as? String
                Task { @MainActor in
                    if let current { self?.receivePhoneAccount(current) }
                    if approved { continuation.resume() } else { continuation.resume(throwing: ClassingError.invalid("请在 iPhone 登录后重新同步账号。")) }
                }
            }, errorHandler: { continuation.resume(throwing: $0) })
        }
    }
    #endif
    func publish(_ state: AppState, force: Bool = false) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated else { deliveryStatus = "等待连接启动后自动发送"; return }
        #if os(iOS)
        guard WCSession.default.isPaired, WCSession.default.isWatchAppInstalled else { deliveryStatus = "请先在已配对的 Apple Watch 安装 Classing"; return }
        #else
        guard state.settings[CompanionSync.readyKey].bool else { deliveryStatus = "等待 iPhone 初始化课表"; return }
        #endif
        do {
            let data = try Wire.encoder.encode(CompanionSync.outbound(state, role: AppStore.companionRole))
            guard data.count <= CompanionSync.payloadLimit else { throw ClassingError.invalid("课表资料超过当前传输上限，已保留在本机。") }
            let id = UUID().uuidString; latestTransferID = id
            if WCSession.default.isReachable && data.count <= 49_152 {
                deliveryStatus = "正在发送，等待对端保存确认"
                WCSession.default.sendMessageData(data, replyHandler: { [weak self] reply in
                    let value = try? JSONValue.decode(reply), accepted = value?["accepted"].bool ?? false
                    Task { @MainActor in self?.receipt(id, accepted: accepted) }
                }, errorHandler: { [weak self] _ in Task { @MainActor in self?.queue(data, id: id, force: force) } })
            } else { queue(data, id: id, force: force) }
        } catch { deliveryStatus = "课表未能发送"; store?.report(error) }
    }
    private func queue(_ data: Data, id: String, force: Bool) {
        guard latestTransferID == id else { return }
        let digest = Data(SHA256.hash(data: data))
        if !force && digest == lastQueuedDigest { latestTransferID = lastQueuedTransferID; deliveryStatus = "最新课表已在队列中"; return }
        do {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent("classing-transfers"); try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let url = directory.appendingPathComponent(id + ".json"); try data.write(to: url, options: .atomic)
            WCSession.default.transferFile(url, metadata: ["kind": "cloud-v2", "transferID": id]); lastQueuedDigest = digest; lastQueuedTransferID = id
            deliveryStatus = "课表已排队，等待对端接收"
        } catch { deliveryStatus = "课表未能排队"; store?.report(error) }
    }
    private func receipt(_ id: String, accepted: Bool) {
        guard id == latestTransferID else { return }
        if accepted { lastAcknowledgedAt = Date(); deliveryStatus = "对端已确认保存最新课表" }
        else { deliveryStatus = "对端未能保存课表，请重试"; lastQueuedDigest = nil }
    }
    private func acknowledge(_ id: String, accepted: Bool) {
        let message: [String: Any] = ["syncAck": id, "accepted": accepted]
        if WCSession.default.isReachable {
            WCSession.default.sendMessage(message, replyHandler: nil, errorHandler: { _ in
                WCSession.default.transferUserInfo(["syncAck": id, "accepted": accepted])
            })
        } else { WCSession.default.transferUserInfo(message) }
    }
    nonisolated func session(_ session: WCSession, didFinish fileTransfer: WCSessionFileTransfer, error: Error?) {
        guard fileTransfer.file.metadata?["kind"] as? String == "cloud-v2" else { return }
        try? FileManager.default.removeItem(at: fileTransfer.file.fileURL)
        if let error { Task { @MainActor in self.lastQueuedDigest = nil; self.deliveryStatus = "后台传输未完成，请重试"; self.store?.report(error) } }
    }
    private func accept(_ data: Data) -> Bool {
        guard data.count <= CompanionSync.payloadLimit, let store else { return false }
        do {
            let accepted = store.receive(try Wire.decoder.decode(CloudDocument.self, from: data))
            if accepted { deliveryStatus = "已保存来自另一设备的课表" }
            return accepted
        } catch { store.report(error); return false }
    }
    nonisolated func session(_ session: WCSession, didReceive file: WCSessionFile) {
        guard file.metadata?["kind"] as? String == "cloud-v2" else { return }
        let id = file.metadata?["transferID"] as? String
        let size = try? file.fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize
        let data: Data?
        if let size, size <= CompanionSync.payloadLimit { data = try? Data(contentsOf: file.fileURL) } else { data = nil }
        Task { @MainActor in
            let accepted = data.map { self.accept($0) } ?? false
            if let id { self.acknowledge(id, accepted: accepted) }
        }
    }
    nonisolated func session(_ session: WCSession, didReceiveMessageData messageData: Data, replyHandler: @escaping (Data) -> Void) {
        Task { @MainActor in let accepted = self.accept(messageData); replyHandler((try? JSONValue.object(["accepted": .bool(accepted)]).data()) ?? Data()) }
    }
    nonisolated func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        let key = applicationContext["byokPublicKey"] as? Data, account = applicationContext["phoneAccountId"] as? String
        Task { @MainActor in var context: [String: Any] = [:]; if let key { context["byokPublicKey"] = key }; if let account { context["phoneAccountId"] = account }; self.receiveContext(context) }
    }
    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        let requested = message["requestAccount"] as? Bool == true, authorization = message["autoAuthorizationId"] as? String, expected = message["expectedAccountId"] as? String
        Task { @MainActor in
            #if os(iOS)
            if requested { var reply: [String: Any] = [:]; if let id = self.store?.companionAccountID { reply["phoneAccountId"] = id }; replyHandler(reply); return }
            if let authorization, let expected, !expected.isEmpty, let store = self.store, store.companionAccountID == expected {
                do {
                    _ = try await store.api.request("POST", "/api/v1/auth/device/qr/approve", body: .object(["authorizationId": .string(authorization)]))
                    replyHandler(["approved": store.companionAccountID == expected, "phoneAccountId": store.companionAccountID ?? ""])
                } catch { replyHandler(["approved": false, "phoneAccountId": store.companionAccountID ?? ""]) }
                return
            }
            #endif
            replyHandler(["approved": false])
        }
    }
    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        let auth = message["authorizationId"] as? String, encrypted = message["encryptedBYOK"] as? Data, sender = message["senderKey"] as? Data
        let timetableRequested = message["requestTimetable"] as? Bool == true
        let receipt = message["syncAck"] as? String, accepted = message["accepted"] as? Bool == true
        let phoneAccount = message["phoneAccountId"] as? String
        Task { @MainActor in
            #if os(watchOS)
            if let phoneAccount { self.receivePhoneAccount(phoneAccount) }
            #endif
            if let receipt { self.receipt(receipt, accepted: accepted) }
            #if os(iOS)
            if timetableRequested, let state = self.store?.state { self.publish(state, force: true) }
            #endif
            if let auth = auth { self.pendingAuthorization = auth }
            if let encrypted = encrypted, let sender = sender {
                do {
                    let key = try self.symmetric(peer: sender), box = try AES.GCM.SealedBox(combined: encrypted), clear = try AES.GCM.open(box, using: key)
                    self.pendingBYOK = try JSONValue.decode(clear)
                } catch { self.store?.error = "BYOK 设备传输校验失败。" }
            }
        }
    }
    nonisolated func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
        let requested = userInfo["requestTimetable"] as? Bool == true
        let receipt = userInfo["syncAck"] as? String, accepted = userInfo["accepted"] as? Bool == true
        Task { @MainActor in
            if let receipt { self.receipt(receipt, accepted: accepted) }
            #if os(iOS)
            if requested, let state = self.store?.state { self.publish(state, force: true) }
            #endif
        }
    }
    func requestTimetable() throws {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated else { throw ClassingError.invalid("请先在 iPhone 和手表上打开 Classing。") }
        let request: [String: Any] = ["requestTimetable": true]
        deliveryStatus = "已请求 iPhone 更新课表"
        if WCSession.default.isReachable { WCSession.default.sendMessage(request, replyHandler: nil, errorHandler: { [weak self] _ in Task { @MainActor in WCSession.default.transferUserInfo(["requestTimetable": true]); self?.deliveryStatus = "更新请求已排队，等待 iPhone" } }) }
        else { WCSession.default.transferUserInfo(request); deliveryStatus = "更新请求已排队，等待 iPhone" }
    }
    func requestApproval(_ id: String) throws { guard WCSession.default.isReachable else { throw ClassingError.invalid("请在 iPhone 上打开 Classing 后重试。") }; WCSession.default.sendMessage(["authorizationId": id], replyHandler: nil, errorHandler: { [weak self] _ in Task { @MainActor in self?.store?.error = "iPhone 未收到连接请求，请重试。" } }) }
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
        guard store.byokAllowed else { throw ClassingError.invalid("自备 AI 模型仅向有效的 Classing Pro 账号开放。") }
        let config = try store.byok.forWatch(), profile = try config.selected("CHAT"), secret = try await store.vault.secret(store.byokKeyName(profile.id))
        guard !secret.isEmpty else { throw ClassingError.invalid("未配置 BYOK 密钥。") }
        let value = JSONValue.object(["config": try JSONValue.encode(config), "key": .string(secret), "sentAt": .int(Wire.milliseconds())])
        let encrypted = try AES.GCM.seal(value.data(), using: symmetric(peer: peer)).combined!
        WCSession.default.sendMessage(["encryptedBYOK": encrypted, "senderKey": pairingKey.publicKey.rawRepresentation], replyHandler: nil, errorHandler: { _ in })
    }
    func acceptBYOK() async {
        guard let value = pendingBYOK, let store = store else { return }; pendingBYOK = nil
        do { guard abs(Wire.milliseconds() - value["sentAt"].int) < 120_000 else { throw ClassingError.invalid("传输已过期。") }; let config = try ByokConfiguration.decode(value["config"])
            guard store.byokAllowed else { throw ClassingError.invalid("自备 AI 模型仅向有效的 Classing Pro 账号开放。") }
            guard config.ownerId == store.byokOwner else { throw ClassingError.invalid("两端须使用同一账号，或都未登录后再传输。") }
            let profile = try config.selected("CHAT"); _ = try BYOKClient(endpoint: URL(string: profile.baseUrl)!, model: profile.model, key: value["key"].string)
            try await store.vault.setSecret(value["key"].string, key: store.byokKeyName(profile.id)); try await store.savePrivateByok(config) } catch { store.error = error.localizedDescription }
    }
}
