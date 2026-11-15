import SwiftUI
import ClassingCore
struct WatchAssistantView: View {
    @EnvironmentObject var store: AppStore
    @StateObject private var chat = AssistantModel()
    var body: some View {
        List {
            if !store.signedIn && !chat.byok { NavigationLink("同步手机账号") { WatchCompanionView() } }
            else {
                if !chat.models.isEmpty { Picker("模型", selection: $chat.model) { ForEach(chat.models, id: \.self) { Text($0["name"].string).tag($0["id"].string) } } }
                Button("新建对话") { chat.newConversation() }
                ForEach(chat.messages) { m in VStack(alignment: .leading, spacing: 8) { Text(m.role == "user" ? "你" : "Classing").font(.caption).foregroundStyle(.secondary); if m.role == "assistant" { RichReplyView(m.content) } else { Text(m.content) } } }
                if chat.busy { ProgressView(); Button("停止") { chat.cancel() } }
                ForEach(chat.prompts, id: \.self) { p in Button(p) { chat.draft = p } }
                TextField("问问课表", text: $chat.draft)
                Button("发送") { chat.send() }.disabled(chat.busy || chat.draft.isEmpty)
                if chat.examProposal != nil { NavigationLink("考试建议") { ScrollView { VStack(alignment: .leading, spacing: 8) { Text((try? chat.examProposal?.text()) ?? "").font(.caption); Button("确认应用") { chat.apply(exams: true) } } }.navigationTitle("考试建议") } }
            }
        }.navigationTitle("课表助手").task(id: store.account["userId"].string) { await chat.attach(store) }.onDisappear { chat.cancel() }
    }
}
