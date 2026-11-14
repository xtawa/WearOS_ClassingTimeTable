import SwiftUI
import ClassingCore
import ImageIO
struct WatchAssistantView: View {
    @EnvironmentObject var store: AppStore
    @StateObject private var chat = AssistantModel()
    var body: some View {
        List {
            if !store.signedIn && !store.byok.enabled { NavigationLink("登录账号") { WatchLoginView() } }
            else {
                if !chat.models.isEmpty { Picker("模型", selection: $chat.model) { ForEach(chat.models, id: \.self) { Text($0["name"].string).tag($0["id"].string) } } }
                Button("新建对话") { chat.newConversation() }
                ForEach(chat.conversations, id: \.self) { c in Button(c["title"].string) { Task { await chat.open(c["conversationId"].string) } } }
                ForEach(chat.messages) { m in VStack(alignment: .leading) { Text(m.role == "user" ? "你" : "Classing").font(.caption).foregroundStyle(.secondary); Text(m.content) } }
                if chat.busy { ProgressView(); Button("停止") { chat.cancel() } }
                ForEach(chat.prompts, id: \.self) { p in Button(p) { chat.draft = p } }
                TextField("问题（支持系统听写）", text: $chat.draft)
                Button("发送") { chat.send() }.disabled(chat.busy || chat.draft.isEmpty)
                if chat.courseProposal != nil { DisclosureGroup("课程建议") { Text((try? chat.courseProposal?.text()) ?? "").font(.caption); Button("确认应用") { chat.apply(exams: false) } } }
                if chat.examProposal != nil { DisclosureGroup("考试建议") { Text((try? chat.examProposal?.text()) ?? "").font(.caption); Button("确认应用") { chat.apply(exams: true) } } }
            }
        }.navigationTitle("课表助手").task { await chat.attach(store) }.onDisappear { chat.cancel() }
    }
}
struct WatchSettings: View {
    @EnvironmentObject var store: AppStore
    @State private var raw = "", importing = false
    var body: some View {
        List {
            NavigationLink(store.signedIn ? "账号信息" : "登录账号") { WatchLoginView() }
            Toggle("独立通知", isOn: Binding(get: { store.state.settings["watchIndependentReminders"].bool }, set: { store.setting("watchIndependentReminders", .bool($0)) }))
            Button("授权通知") { Task { do { _ = try await LocalNotifications.permission() } catch { store.error = error.localizedDescription } } }
            Toggle("课程提醒", isOn: Binding(get: { store.state.settings["reminderEnabled"].bool }, set: { store.setting("reminderEnabled", .bool($0)) }))
            Stepper("提前 \(store.state.settings["reminderMinutes"].int) 分钟", value: Binding(get: { Int(store.state.settings["reminderMinutes"].int) }, set: { store.setting("reminderMinutes", .int(Int64($0))) }), in: 0...120, step: 5)
            Picker("模式", selection: Binding(get: { store.state.settings["academicMode"].string }, set: { store.setting("academicMode", .string($0)) })) { Text("课程").tag("SCHEDULE"); Text("考试").tag("EXAM") }
            Toggle("官方云同步", isOn: Binding(get: { store.state.settings["cloudSyncEnabled"].bool }, set: { store.setting("cloudSyncEnabled", .bool($0)) }))
            Button("立即同步") { Task { await store.sync() } }.disabled(store.syncing)
            Toggle("显示教师", isOn: Binding(get: { store.state.settings["watchShowTeacher"].bool }, set: { store.setting("watchShowTeacher", .bool($0)) }))
            Toggle("使用 BYOK", isOn: Binding(get: { store.byok.enabled }, set: { value in Task { do { var config = store.byok; config.enabled = value; try await store.savePrivateByok(config) } catch { store.error = error.localizedDescription } } }))
            Text("BYOK 密钥可由 iPhone 加密传入，接收时需要再次确认。")
            NavigationLink("粘贴 JSON / ICS 导入") { Form { TextField("课表文本", text: $raw); Button("解析并合并") { do { let draft = try ScheduleImport.text(raw, zone: store.state.settings.timezone); try store.commit(draft.applying(to: store.state, replace: false)); raw = "" } catch { store.error = error.localizedDescription } } } }
            NavigationLink("恢复本地快照") { List { ForEach(store.state.snapshots) { s in NavigationLink(s.reason) { VStack { Text("恢复 \(s.lessons.count) 门课 / \(s.exams.count) 场考试？"); Button("确认恢复") { store.restore(s) } } } } } }
            Text(store.status).font(.caption)
        }.navigationTitle("设置")
    }
}
struct WatchLoginView: View {
    @EnvironmentObject var store: AppStore
    @State private var authorization: JSONValue = .null, identifier = "", password = "", consent = false, busy = false
    var body: some View {
        List {
            if store.signedIn { Text(store.account["username"].string); Text(store.account["email"].string); Text(store.membership["accountType"].string); Button("退出登录", role: .destructive) { Task { await store.logout() } } }
            else {
                Toggle("同意账号协议与隐私政策", isOn: $consent)
                Text("可在 iPhone 登录页阅读完整隐私政策、服务条款和跨境数据协议。")
                Button("在 iPhone 上授权") { Task { await startAuthorization() } }.disabled(!consent || busy)
                if !authorization["authorizationId"].string.isEmpty { Text("等待 iPhone 确认…"); let value = authorization["qrImage"].string.components(separatedBy: ",").last ?? ""; if let data = Data(base64Encoded: value), let source = CGImageSourceCreateWithData(data as CFData, nil), let image = CGImageSourceCreateImageAtIndex(source, 0, nil) { Image(decorative: image, scale: 1).resizable().scaledToFit().padding(8).background(.white) }; Text("也可用手机扫描二维码。").font(.caption) }
                TextField("邮箱或用户名", text: $identifier); SecureField("密码", text: $password)
                Button("直接登录") { Task { do { guard consent else { return }; try await store.login(identifier, password); password = "" } catch { store.error = error.localizedDescription } } }.disabled(!consent)
            }
        }.navigationTitle("账号")
        .task(id: authorization["authorizationId"].string) {
            let id = authorization["authorizationId"].string; guard !id.isEmpty else { return }
            while !Task.isCancelled && !store.signedIn && Wire.milliseconds() < authorization["expiresAt"].int {
                do {
                    let response = try await store.api.request("POST", "/api/v1/auth/device/qr/poll", body: .object(["authorizationId": .string(id), "pollSecret": authorization["pollSecret"]]), authorized: false)
                    if response["session"] != .null { try await store.signedInAfterRegistration(response); return }
                    try await Task.sleep(nanoseconds: UInt64(max(5, authorization["intervalSeconds"].int)) * 1_000_000_000)
                } catch { if !Task.isCancelled { store.error = error.localizedDescription }; return }
            }
        }.onDisappear { password = ""; authorization = .null }
    }
    func startAuthorization() async {
        busy = true; defer { busy = false }
        do { authorization = try await store.api.request("POST", "/api/v1/auth/device/qr/start", body: .object(["deviceName": .string("Apple Watch")]), authorized: false); try? PhoneWatchBridge.shared.requestApproval(authorization["authorizationId"].string) } catch { store.error = error.localizedDescription }
    }
}
