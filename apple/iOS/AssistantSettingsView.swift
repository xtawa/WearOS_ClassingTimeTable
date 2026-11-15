import SwiftUI
import ClassingCore
struct AssistantSettingsView: View {
    @EnvironmentObject var store: AppStore
    @State private var preferences: JSONValue = .null
    @State private var usage: JSONValue = .null
    @State private var cards: [JSONValue] = []
    @State private var models: [JSONValue] = []
    @State private var code = ""
    @State private var saving = false
    @State private var loaded = false
    @State private var pending = ""
    @State private var feedback = ""
    var body: some View {
        Form {
            if !store.signedIn { Text("请先登录以管理云端助手。") }
            else {
                Section("用量") { let u = usage["usage"] == .null ? usage : usage["usage"]; Text("额度 \(u["limit"].int) · 已用 \(u["used"].int) · 预留 \(u["reserved"].int)"); Text("可用点数 \(u["creditAvailable"].int)"); Text("模式：\(u["mode"].string)") }
                Section("推荐与对话") { Toggle("显示推荐问题", isOn: binding("showPromptSuggestions")); Toggle("自动刷新推荐", isOn: binding("autoRefreshPrompts")); Toggle("显示思考过程", isOn: binding("showReasoning")); Toggle("图片预览", isOn: binding("imagePreviews")); Toggle("显示消息时间", isOn: binding("showTimestamps")); Toggle("转写后自动发送", isOn: binding("voiceAutoSend")); Text("隐藏推荐后，服务端不生成推荐或扣除相应点数。已开始的请求仍可能收费。").font(.caption).foregroundStyle(.secondary) }
                Section("模型偏好") {
                    ForEach(models, id: \.self) { m in Toggle(m["name"].string + " · 星标", isOn: Binding(get: { preferences["favoriteModels"].array.contains(.string(m["id"].string)) }, set: { value in Task { await favorite(m["id"].string, enabled: value) } })) }
                }
                Section("额度重置卡") {
                    ForEach(cards, id: \.self) { card in Button("使用 \(card["cardId"].string)") { use(card["cardId"].string) } }
                    TextField("重置卡兑换码", text: $code).textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button("使用兑换码") { use("") }.disabled(code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                if saving { ProgressView(pending) }
                if !feedback.isEmpty { Text(feedback).font(.footnote).foregroundStyle(.secondary) }
                if !loaded && !saving { Button("重新加载") { Task { await load() } } }
            }
        }.disabled(saving).navigationTitle("助手设置").task { await load() }
    }
    func load() async {
        guard store.signedIn, !saving else { return }
        saving = true; pending = "正在加载…"; feedback = ""; defer { saving = false }
        do {
            let p = try await store.api.assistantPreferences()
            let u = try await store.api.request("GET", "/api/v1/ai/usage/me")
            let c = try await store.api.request("GET", "/api/v1/ai/reset-cards")["cards"].array
            let m = try await store.api.request("GET", "/api/v1/ai/models")["models"].array
            preferences = p; usage = u; cards = c; models = m; loaded = true
        } catch { feedback = "设置未能加载，请重试"; store.report(error) }
    }
    func binding(_ key: String) -> Binding<Bool> {
        Binding(get: { preferences.object[key]?.bool ?? ["showPromptSuggestions", "showReasoning", "imagePreviews"].contains(key) }, set: { value in
            guard !saving, loaded else { return }; saving = true; pending = "正在保存…"; feedback = ""
            let previous = preferences; var next = preferences.object; next[key] = .bool(value); preferences = .object(next)
            Task { defer { saving = false }; do { preferences = try await store.api.setAssistantPreference(key, to: value) } catch { preferences = previous; store.report(error) } }
        })
    }
    func favorite(_ id: String, enabled: Bool) async {
        guard !saving, loaded else { return }; saving = true; pending = "正在保存…"; feedback = ""; defer { saving = false }
        do { preferences = try await store.api.setFavoriteModel(id, favorite: enabled) } catch { store.report(error) }
    }
    func use(_ id: String) {
        guard !saving else { return }; let value = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty || !value.isEmpty else { return }
        saving = true; pending = "正在兑换…"; feedback = ""
        Task { defer { saving = false }; do {
            let body: JSONValue = id.isEmpty ? .object(["code": .string(value)]) : .object(["cardId": .string(id)])
            let response = try await store.api.request("POST", "/api/v1/ai/reset-cards/use", body: body)
            usage = .object(["usage": response["usage"]]); code = ""; feedback = "额度已重置，原定重置日期不变。"; store.status = feedback
            // Redemption succeeded; a later list refresh must not suggest using the code again.
            do { cards = try await store.api.request("GET", "/api/v1/ai/reset-cards")["cards"].array } catch { }
        } catch { feedback = ClassingError.userMessage(error); store.report(error) } }
    }
}
struct AnnouncementsView: View {
    @EnvironmentObject var store: AppStore
    @State private var items: [JSONValue] = []
    var body: some View { List { ForEach(items, id: \.self) { item in VStack(alignment: .leading, spacing: 12) { Text(item["title"].string).font(.headline); Text((try? AttributedString(markdown: item["content"].string)) ?? AttributedString(item["content"].string)).textSelection(.enabled); if !item["contentHtml"].string.isEmpty { NavigationLink("查看图文公告") { RichAnnouncementView(html: item["contentHtml"].string, base: store.api.baseURL).navigationTitle(item["title"].string) } } } } }.navigationTitle("公告").task { do { items = try await store.api.request("GET", "/api/v1/client/announcements?edition=CN", authorized: false)["announcements"].array } catch { store.report(error) } } }
}
