import SwiftUI
import ClassingCore
struct ChatMessage: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var role: String, content: String, reasoning = ""
    var attachments: [JSONValue] = []
    var createdAt: Int64 = Wire.milliseconds()
}
struct PendingAttachment: Identifiable { var id: String, name: String, mime: String; var data: Data? }
@MainActor final class AssistantModel: ObservableObject {
    @Published var messages: [ChatMessage] = []
    @Published var conversations: [JSONValue] = []
    @Published var models: [JSONValue] = []
    @Published var prompts: [String] = []
    @Published var draft = ""
    @Published var model = ""
    @Published var busy = false
    @Published var conversationId: String?
    @Published var usage: JSONValue = .null
    @Published var preferences: JSONValue = .null
    @Published var attachments: [PendingAttachment] = []
    @Published var courseProposal: JSONValue?
    @Published var examProposal: JSONValue?
    @Published var expected: AppState?
    var task: Task<Void, Never>?
    private var store: AppStore?
    private var context = ""
    private var activeRequest: UUID?
    var byok: Bool { store?.byok.enabled == true }
    func attach(_ store: AppStore) async {
        self.store = store
        let nextContext = byok ? "byok:" + store.byokOwner : "official:" + store.account["userId"].string
        if nextContext != context { cancel(); messages = []; conversationId = nil; attachments = []; courseProposal = nil; examProposal = nil; context = nextContext }
        if byok { await loadLocal(); return }; guard store.signedIn else { return }
        do {
            conversations = try await store.api.request("GET", "/api/v1/ai/conversations?limit=30")["conversations"].array
            let catalog = try await store.api.request("GET", "/api/v1/ai/models"); models = catalog["models"].array
            if model.isEmpty { model = catalog["defaultModel"].string }; usage = try await store.api.request("GET", "/api/v1/ai/usage/me")
            preferences = try await store.api.request("GET", "/api/v1/ai/preferences")["preferences"]
            let favorites = Set(preferences["favoriteModels"].array.map(\.string))
            models.sort { a, b in favorites.contains(a["id"].string) && !favorites.contains(b["id"].string) }
            if models.contains(where: { $0["id"].string == preferences["defaultModel"].string }) { model = preferences["defaultModel"].string }
            if preferences["showPromptSuggestions"].bool { prompts = try await store.api.request("POST", "/api/v1/ai/prompts", body: .object(["timetableSnapshot": try store.state.aiSnapshot(now: store.now), "force": .bool(false)]))["prompts"].array.map(\.string) }
        } catch { store.error = error.localizedDescription }
    }
    func open(_ id: String) async {
        guard let store = store else { return }; cancel(); conversationId = id; attachments = []
        do { let items = try await store.api.request("GET", "/api/v1/ai/conversations/\(id)/messages")["messages"].array; messages = items.map { ChatMessage(id: $0["messageId"].string, role: $0["role"].string, content: $0["content"].string, reasoning: $0["reasoning"].string, attachments: $0["attachments"].array, createdAt: $0["createdAt"].int) } } catch { store.error = error.localizedDescription }
    }
    func newConversation() { cancel(); messages = []; conversationId = nil; courseProposal = nil; examProposal = nil; attachments = []; if byok { Task { await saveLocal() } } }
    func delete(_ id: String) async { guard let store = store else { return }; do { _ = try await store.api.request("DELETE", "/api/v1/ai/conversations/\(id)"); conversations.removeAll { $0["conversationId"].string == id }; if conversationId == id { newConversation() } } catch { store.error = error.localizedDescription } }
    func send() {
        guard !busy, let store = store, !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let token = UUID(); activeRequest = token
        let prompt = draft, base = store.state, previous = messages, pending = attachments; expected = base; busy = true; courseProposal = nil; examProposal = nil
        messages.append(ChatMessage(role: "user", content: prompt, attachments: pending.map { .object(["attachmentId": .string($0.id), "name": .string($0.name), "mimeType": .string($0.mime)]) })); let replyID = UUID().uuidString; messages.append(ChatMessage(id: replyID, role: "assistant", content: ""))
        task = Task {
            defer { if self.activeRequest == token { self.busy = false; self.task = nil; self.activeRequest = nil } }
            do {
                if byok {
                    let role = pending.contains(where: { $0.mime.hasPrefix("image/") }) && !store.byok.visionId.isEmpty ? "VISION" : "CHAT"
                    let client = try await store.byokClient(store.byok.selected(role))
                    var content: JSONValue = .string(prompt)
                    if !pending.isEmpty {
                        var parts: [JSONValue] = [.object(["type": .string("text"), "text": .string(prompt)])]
                        for file in pending {
                            guard let data = file.data else { continue }
                            if file.mime.hasPrefix("image/") { parts.append(.object(["type": .string("image_url"), "image_url": .object(["url": .string("data:\(file.mime);base64,\(data.base64EncodedString())")])])) }
                            else { guard let text = String(data: data, encoding: .utf8), data.count <= 131072 else { throw ClassingError.invalid("BYOK 文本附件需为 UTF-8，最多 128 KiB；PDF 请使用提取后的文本。") }; parts.append(.object(["type": .string("text"), "text": .string("附件：\(file.name)\n\(text)")])) }
                        }; content = .array(parts)
                    }
                    let history = previous.map { JSONValue.object(["role": .string($0.role), "content": .string($0.content)]) } + [.object(["role": .string("user"), "content": content])]
                    let reply = try await client.chat(history, snapshot: base.aiSnapshot(now: store.now)) { [weak self] delta in await self?.append(delta, id: replyID, reasoning: false) }
                    guard activeRequest == token else { return }; let proposal = try ByokActions.parse(reply); courseProposal = proposal.course; examProposal = proposal.exam
                    if let i = messages.firstIndex(where: { $0.id == replyID }) { messages[i].content = proposal.display }
                    await saveLocal()
                } else {
                    let done = try await store.api.chat(message: prompt, conversationId: conversationId, snapshot: base.aiSnapshot(now: store.now), model: model, attachments: pending.map(\.id)) { [weak self] event in await self?.event(event, replyID: replyID) }
                    guard activeRequest == token else { return }; courseProposal = done["courseProposal"] == .null ? nil : done["courseProposal"]; examProposal = done["examProposal"] == .null ? nil : done["examProposal"]
                    if let i = messages.firstIndex(where: { $0.id == replyID }), messages[i].content.isEmpty { messages[i].content = done["reply"].string }
                    usage = try await store.api.request("GET", "/api/v1/ai/usage/me")
                }
                draft = ""; attachments = []
            } catch { if activeRequest == token { if !Task.isCancelled { store.error = error.localizedDescription }; draft = prompt } }
        }
    }
    func append(_ delta: String, id: String, reasoning: Bool) { guard let i = messages.firstIndex(where: { $0.id == id }) else { return }; if reasoning { messages[i].reasoning += delta } else { messages[i].content += delta } }
    func event(_ event: SSEEvent, replyID: String) {
        guard activeRequest != nil, messages.contains(where: { $0.id == replyID }), let value = try? JSONValue.decode(Data(event.data.utf8)) else { return }
        if event.event == "conversation" { conversationId = value["conversationId"].string }
        if event.event == "delta" || event.event == "reasoning" { append(value["text"].string, id: replyID, reasoning: event.event == "reasoning") }
    }
    func cancel() { activeRequest = nil; task?.cancel(); task = nil; busy = false }
    func apply(exams: Bool) {
        guard let store = store, let expected = expected, let proposal = exams ? examProposal : courseProposal else { return }
        do { try store.commit(AIProposal.apply(proposal, exams: exams, expected: expected, current: store.state)); courseProposal = nil; examProposal = nil } catch { store.error = error.localizedDescription }
    }
    func attachFile(_ data: Data, name: String, mime: String) async throws {
        guard let store = store, attachments.count < 4, data.count <= 20_971_520 else { throw ClassingError.invalid("最多 4 个附件，每个不超过 20 MiB。") }
        if byok { attachments.append(PendingAttachment(id: UUID().uuidString, name: name, mime: mime, data: data)) }
        else { let value = try await store.api.upload(data, filename: name, mime: mime)["attachment"]; attachments.append(PendingAttachment(id: value["attachmentId"].string, name: name, mime: mime)) }
    }
    func removeAttachment(_ id: String) async { guard let store = store else { return }; do { if !byok { _ = try await store.api.request("DELETE", "/api/v1/ai/attachments/\(id)") }; attachments.removeAll { $0.id == id } } catch { store.error = error.localizedDescription } }
    private func loadLocal() async {
        guard let store = store else { return }
        if let raw = try? await store.vault.secret("byok.history." + store.byokOwner), let items = try? Wire.decoder.decode([ChatMessage].self, from: Data(raw.utf8)) { messages = items }
    }
    private func saveLocal() async {
        guard let store = store else { return }
        do { let data = try Wire.encoder.encode(Array(messages.suffix(100))); guard data.count <= 524288 else { throw ClassingError.invalid("本地 BYOK 对话已超过 512 KiB，请新建对话。") }; try await store.vault.setSecret(String(decoding: data, as: UTF8.self), key: "byok.history." + store.byokOwner) } catch { store.error = error.localizedDescription }
    }
}
