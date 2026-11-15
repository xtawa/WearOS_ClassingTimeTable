import SwiftUI
import UniformTypeIdentifiers
import ClassingCore
import PDFKit
struct AssistantView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.scenePhase) var phase
    @StateObject private var chat = AssistantModel()
    @StateObject private var speech = SpeechInput()
    @StateObject private var playback = SpeechPlayback()
    @State private var filePicker = false
    @State private var proposalKind: Bool?
    @State private var applyDialog = false
    @State private var voiceTask: Task<Void, Never>?
    var body: some View {
        VStack(spacing: 0) {
            if !store.signedIn && !store.byok.enabled {
                ContentUnavailableView { Label("你的课表助手", systemImage: "sparkles") } description: { Text("登录使用云端助手，或在设置中开启 BYOK。本地课表无需登录。") } actions: { NavigationLink("登录账号") { AccountView() }; NavigationLink("配置 BYOK") { BYOKSettingsView() } }
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        if chat.messages.isEmpty { Text("今天，想知道什么？").font(.title2.bold()).padding(.top, 24); Text(chat.byok ? "请求会直接发送到你配置的供应商。" : "回答基于当前课表。AI 可能出错，修改前请核对。 ").font(.caption).foregroundStyle(.secondary) }
                        ForEach(chat.messages) { m in
                            VStack(alignment: .leading, spacing: 6) { Text(m.role == "user" ? "你" : "Classing").font(.caption.bold()).foregroundStyle(.secondary); if m.role == "assistant" { RichReplyView(m.content) } else { Text(m.content).textSelection(.enabled) }; if !m.reasoning.isEmpty && chat.preferences["showReasoning"] != .bool(false) { DisclosureGroup("思考过程") { RichReplyView(m.reasoning).font(.caption).foregroundStyle(.secondary) } }
                                if chat.byok && m.role == "assistant" && !m.content.isEmpty && !store.byok.speechId.isEmpty { Button(playback.playing ? "停止朗读" : "朗读") { if playback.playing { playback.cancel() } else { playback.read(m.content, store: store) } }.font(.caption) }
                                if chat.preferences["showTimestamps"].bool { Text(Date(timeIntervalSince1970: Double(m.createdAt) / 1000).formatted()).font(.caption2).foregroundStyle(.secondary) }
                                if chat.preferences["imagePreviews"] != .bool(false) && !chat.byok { ForEach(m.attachments, id: \.self) { AttachmentThumbnail(attachment: $0) } }
                            }.padding(16).frame(maxWidth: .infinity, alignment: .leading).classingGlass()
                        }
                        if chat.busy { HStack { ProgressView(); Text("正在回答…").font(.caption); Spacer(); Button("停止") { chat.cancel() } } }
                        if chat.courseProposal != nil { proposal(false) }; if chat.examProposal != nil { proposal(true) }
                    }.padding(20)
                }
                if !chat.prompts.isEmpty { ScrollView(.horizontal) { HStack { ForEach(chat.prompts, id: \.self) { p in Button(p) { chat.draft = p }.font(.caption).buttonStyle(.bordered) } }.padding(.horizontal) }.padding(.vertical, 8) }
                if !chat.attachments.isEmpty { ScrollView(.horizontal) { HStack { ForEach(chat.attachments) { a in Button { Task { await chat.removeAttachment(a.id) } } label: { Label(a.name, systemImage: "xmark.circle") }.font(.caption) } }.padding() } }
                HStack(alignment: .bottom, spacing: 12) {
                    Button { filePicker = true } label: { Image(systemName: "paperclip") }.disabled(chat.busy)
                    TextField("问问你的课表", text: $chat.draft, axis: .vertical).lineLimit(1...5).accessibilityIdentifier("chat-input")
                    Button { voice() } label: { Image(systemName: speech.recording ? "stop.circle.fill" : "mic") }.foregroundStyle(speech.recording ? .red : .indigo)
                    Button { chat.send() } label: { Image(systemName: "arrow.up.circle.fill").font(.title) }.disabled(chat.busy || chat.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }.padding(16).background(.regularMaterial)
                if speech.recording { VStack { Text(speech.requiresServer ? (chat.byok ? "停止后直接发往所选 BYOK 转写供应商。" : "停止后发往 Classing 转写，将计入服务端点数。") : "使用本机离线语音识别。").font(.caption2).foregroundStyle(.secondary); Button("取消录音") { speech.cancel(); voiceTask?.cancel() }.font(.caption) }.padding(8) }
            }
        }.background(ClassingBackdrop()).navigationTitle(chat.byok ? "BYOK 助手" : "Ask AI")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { Menu { Button("新建对话", systemImage: "plus") { chat.newConversation() }; ForEach(chat.conversations, id: \.self) { c in Button(c["title"].string) { Task { await chat.open(c["conversationId"].string) } } }; if let id = chat.conversationId { Button("删除当前对话", role: .destructive) { Task { await chat.delete(id) } } } } label: { Image(systemName: "sidebar.left") } }
            ToolbarItem(placement: .topBarTrailing) { Menu {
                if chat.byok { ForEach(store.byok.profiles.filter { $0.role == "CHAT" }) { p in Button(p.name) { Task { do { var config = store.byok; config.chatId = p.id; try await store.savePrivateByok(config) } catch { store.error = error.localizedDescription } } } }; NavigationLink("管理自备模型") { ByokProfilesView() } }
                ForEach(chat.models, id: \.self) { m in Button(m["name"].string) { chat.model = m["id"].string } }; NavigationLink("助手偏好与用量") { AssistantSettingsView() } } label: { Label(chat.model.isEmpty ? "模型" : chat.model, systemImage: "chevron.down").font(.caption) } }
        }.task { await chat.attach(store) }.onDisappear { chat.cancel(); speech.cancel(); playback.cancel(); voiceTask?.cancel() }
        .onChange(of: phase) { _, p in if p != .active { chat.cancel(); speech.cancel(); playback.cancel(); voiceTask?.cancel() } }
        .confirmationDialog("将建议中的修改应用到课表？", isPresented: $applyDialog) { Button("确认应用") { chat.apply(exams: proposalKind ?? false) } } message: { Text("仅在当前课表仍与提问时一致时应用；修改前会自动保存快照。") }
        .fileImporter(isPresented: $filePicker, allowedContentTypes: [.data], allowsMultipleSelection: false) { result in Task { do { let urls = try result.get(); guard let url = urls.first else { return }; let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }; var data = try Data(contentsOf: url), mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
                if chat.byok && url.pathExtension.lowercased() == "pdf" { guard let text = PDFDocument(data: data)?.string else { throw ClassingError.invalid("扫描 PDF 请用图片附件；BYOK 文本 PDF 需能提取文字。") }; data = Data(text.utf8); mime = "text/plain" }
                try await chat.attachFile(data, name: url.lastPathComponent, mime: mime)
            } catch { store.error = error.localizedDescription } } }
    }
    func proposal(_ exams: Bool) -> some View { VStack(alignment: .leading) { Text(exams ? "考试修改建议" : "课程修改建议").font(.headline); Text((try? (exams ? chat.examProposal : chat.courseProposal)?.text()) ?? "").font(.caption.monospaced()).textSelection(.enabled); Button("核对并应用") { proposalKind = exams; applyDialog = true } }.padding().classingGlass() }
    func voice() {
        voiceTask?.cancel()
        voiceTask = Task { do {
            if speech.recording { var transcriber: BYOKClient?
                if chat.byok && speech.requiresServer { transcriber = try await store.byokClient(store.byok.selected("TRANSCRIPTION")) }
                let text = try await speech.finish(api: store.api, byok: chat.byok, transcriber: transcriber); try Task.checkCancellation(); chat.draft = text; if chat.preferences["voiceAutoSend"].bool { chat.send() } }
            else { try await speech.start(byok: chat.byok, allowsBYOKTranscription: !store.byok.transcriptionId.isEmpty) }
        } catch { if !Task.isCancelled { store.error = error.localizedDescription } } }
    }
}
