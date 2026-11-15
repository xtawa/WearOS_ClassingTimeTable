import SwiftUI
import ClassingCore
struct WatchAssistantView: View {
    @EnvironmentObject var store: AppStore
    @StateObject private var chat = AssistantModel()
    var body: some View {
        List {
            if !store.signedIn && !store.byok.enabled { Text("账号与助手在 iPhone 设置。离线课表无需登录。").font(.caption).foregroundStyle(.secondary); NavigationLink("在 iPhone 上设置") { WatchCompanionView() } }
            else {
                if !chat.models.isEmpty { Picker("模型", selection: $chat.model) { ForEach(chat.models, id: \.self) { Text($0["name"].string).tag($0["id"].string) } } }
                Button("新建对话") { chat.newConversation() }
                ForEach(chat.conversations, id: \.self) { c in Button(c["title"].string) { Task { await chat.open(c["conversationId"].string) } } }
                ForEach(chat.messages) { m in VStack(alignment: .leading) { Text(m.role == "user" ? "你" : "Classing").font(.caption).foregroundStyle(.secondary); Text(m.content) } }
                if chat.busy { ProgressView(); Button("停止") { chat.cancel() } }
                ForEach(chat.prompts, id: \.self) { p in Button(p) { chat.draft = p } }
                TextField("问题（支持系统听写）", text: $chat.draft)
                Button("发送") { chat.send() }.disabled(chat.busy || chat.draft.isEmpty)
                if chat.courseProposal != nil { NavigationLink("课程建议") { ScrollView { VStack(alignment: .leading, spacing: 8) { Text((try? chat.courseProposal?.text()) ?? "").font(.caption); Button("确认应用") { chat.apply(exams: false) } } }.navigationTitle("课程建议") } }
                if chat.examProposal != nil { NavigationLink("考试建议") { ScrollView { VStack(alignment: .leading, spacing: 8) { Text((try? chat.examProposal?.text()) ?? "").font(.caption); Button("确认应用") { chat.apply(exams: true) } } }.navigationTitle("考试建议") } }
            }
        }.navigationTitle("课表助手").task { await chat.attach(store) }.onDisappear { chat.cancel() }
    }
}
