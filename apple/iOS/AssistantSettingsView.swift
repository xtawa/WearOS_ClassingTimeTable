import SwiftUI
import ClassingCore
struct AssistantSettingsView: View {
    @EnvironmentObject var store: AppStore
    @State private var preferences: JSONValue = .null, usage: JSONValue = .null, cards: [JSONValue] = [], models: [JSONValue] = [], code = ""
    var body: some View {
        Form {
            if !store.signedIn { Text("请先登录以管理云端助手。") }
            else {
                Section("用量") { let u = usage["usage"] == .null ? usage : usage["usage"]; Text("额度 \(u["limit"].int) · 已用 \(u["used"].int) · 预留 \(u["reserved"].int)"); Text("可用点数 \(u["creditAvailable"].int)"); Text("模式：\(u["mode"].string)") }
                Section("推荐与对话") { Toggle("显示推荐问题", isOn: binding("showPromptSuggestions")); Toggle("自动刷新推荐", isOn: binding("autoRefreshPrompts")); Toggle("显示思考过程", isOn: binding("showReasoning")); Toggle("图片预览", isOn: binding("imagePreviews")); Toggle("显示消息时间", isOn: binding("showTimestamps")); Toggle("转写后自动发送", isOn: binding("voiceAutoSend")); Text("隐藏推荐后，服务端不生成推荐或扣除相应点数。已开始的请求仍可能收费。").font(.caption).foregroundStyle(.secondary) }
                Section("模型偏好") {
                    ForEach(models, id: \.self) { m in Toggle(m["name"].string + " · 星标", isOn: Binding(get: { preferences["favoriteModels"].array.contains(.string(m["id"].string)) }, set: { value in Task { var favorites = Set(preferences["favoriteModels"].array.map(\.string)); if value { favorites.insert(m["id"].string) } else { favorites.remove(m["id"].string) }; do { preferences = try await store.api.request("PATCH", "/api/v1/ai/preferences", body: .object(["favoriteModels": .array(favorites.sorted().map(JSONValue.string))]))["preferences"] } catch { store.error = error.localizedDescription } } })) }
                }
                Section("额度重置卡") { ForEach(cards, id: \.self) { card in Button("使用 \(card["cardId"].string)") { use(card["cardId"].string) } }; TextField("重置卡兑换码", text: $code); Button("使用兑换码") { use("") } }
            }
        }.navigationTitle("助手设置").task { await load() }
    }
    func load() async { guard store.signedIn else { return }; do { preferences = try await store.api.request("GET", "/api/v1/ai/preferences")["preferences"]; usage = try await store.api.request("GET", "/api/v1/ai/usage/me"); cards = try await store.api.request("GET", "/api/v1/ai/reset-cards")["cards"].array; models = try await store.api.request("GET", "/api/v1/ai/models")["models"].array } catch { store.error = error.localizedDescription } }
    func binding(_ key: String) -> Binding<Bool> { Binding(get: { preferences[key].bool }, set: { value in Task { do { preferences = try await store.api.request("PATCH", "/api/v1/ai/preferences", body: .object([key: .bool(value)]))["preferences"] } catch { store.error = error.localizedDescription } } }) }
    func use(_ id: String) { Task { do { _ = try await store.api.request("POST", "/api/v1/ai/reset-cards/use", body: .object(["cardId": .string(id), "code": .string(code)])); code = ""; await load() } catch { store.error = error.localizedDescription } } }
}
struct AnnouncementsView: View {
    @EnvironmentObject var store: AppStore
    @State private var items: [JSONValue] = []
    var body: some View { List { ForEach(items, id: \.self) { item in VStack(alignment: .leading, spacing: 12) { Text(item["title"].string).font(.headline); Text((try? AttributedString(markdown: item["content"].string)) ?? AttributedString(item["content"].string)).textSelection(.enabled); if !item["contentHtml"].string.isEmpty { NavigationLink("查看图文公告") { RichAnnouncementView(html: item["contentHtml"].string, base: store.api.baseURL).navigationTitle(item["title"].string) } } } } }.navigationTitle("公告").task { do { items = try await store.api.request("GET", "/api/v1/client/announcements?edition=CN", authorized: false)["announcements"].array } catch { store.error = error.localizedDescription } } }
}
