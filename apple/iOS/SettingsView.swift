import SwiftUI
import ClassingCore
import UniformTypeIdentifiers
struct SettingsView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        List {
            Section { NavigationLink { WidgetGalleryView() } label: { Label("小组件", systemImage: "square.grid.2x2") } }
            Section { NavigationLink { AccountView() } label: { Label(store.signedIn ? store.account["username"].string : "登录 Classing 账号", systemImage: "person.crop.circle") } }
            Section("课程与提醒") { NavigationLink("学期与外观") { AppearanceSettingsView() }; NavigationLink("通知与每日简报") { NotificationSettingsView() }; NavigationLink("导入课表") { ImportView() }; NavigationLink("系统日历") { CalendarSettingsView() }; NavigationLink("课程密度热力图") { CourseHeatmapView() }; NavigationLink("调课与临时安排") { ChangesView() }; NavigationLink("备份与导出") { BackupView() } }
            Section("连接") { NavigationLink("云同步") { CloudSettingsView() }; NavigationLink("Apple Watch") { WatchSettingsView() }; NavigationLink("Ask AI 偏好") { AssistantSettingsView() }; NavigationLink("BYOK · 自备 AI 密钥") { BYOKSettingsView() } }
            Section("关于") { NavigationLink("公告") { AnnouncementsView() }; Text("Classing · iOS / watchOS 1.0").foregroundStyle(.secondary); Text("系统更新通过 App Store 分发，不下载 Android APK。").font(.caption).foregroundStyle(.secondary) }
            if !store.status.isEmpty { Text(store.status).font(.caption) }
        }.navigationTitle("设置")
    }
}
struct AppearanceSettingsView: View {
    @EnvironmentObject var store: AppStore
    @State private var semester = ""
    @State private var zone = ""
    var body: some View {
        Form {
            Section("学期") { TextField("第一周开始 YYYY-MM-DD", text: $semester); TextField("课表 IANA 时区", text: $zone).autocorrectionDisabled().textInputAutocapitalization(.never); Picker("每周开始日", selection: string("weekStartDay")) { ForEach(SchoolTime.weekdays, id: \.self) { Text($0).tag($0) } }; Picker("周次模式", selection: string("weekNumberMode")) { Text("学期周").tag("SEMESTER"); Text("自然周").tag("NATURAL") }; Toggle("显示周末", isOn: boolean("showWeekend")) }
            Section("外观") { Picker("主题", selection: string("appearance")) { Text("跟随系统").tag("system"); Text("浅色").tag("light"); Text("深色").tag("dark") }; Text("iOS 26 使用系统 Liquid Glass；较旧系统使用 Material。遵循减少动态效果、减少透明度与动态字体设置。").font(.caption).foregroundStyle(.secondary) }
        }.navigationTitle("学期与外观")
        .onAppear { semester = store.state.settings.semesterStart; zone = store.state.settings.timezone }
        .toolbar { Button("保存日期与时区") { store.mutate { $0.settings["semesterWeekStartDate"] = .string(semester); $0.settings["timezone"] = .string(zone) } } }
    }
    func string(_ key: String) -> Binding<String> { Binding(get: { store.state.settings[key].string }, set: { store.setting(key, .string($0)) }) }
    func boolean(_ key: String) -> Binding<Bool> { Binding(get: { store.state.settings[key].bool }, set: { store.setting(key, .bool($0)) }) }
}
struct NotificationSettingsView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        Form {
            Section("课程提醒") { Toggle("启用课程提醒", isOn: bool("reminderEnabled")); Stepper("提前 \(store.state.settings["reminderMinutes"].int) 分钟", value: Binding(get: { Int(store.state.settings["reminderMinutes"].int) }, set: { store.setting("reminderMinutes", .int(Int64($0))) }), in: 0...120, step: 5); Button("授权系统通知") { Task { do { guard try await LocalNotifications.permission() else { throw ClassingError.invalid("通知未获授权，请在系统设置中开启。") }; store.setting("reminderEnabled", .bool(true)) } catch { store.error = error.localizedDescription } } }; Button("发送本地测试通知") { Task { do { try await LocalNotifications.test("Classing 测试", body: "提醒将在这里显示。") } catch { store.error = error.localizedDescription } } } }
            Section("每日简报") {
                Toggle("启用每日简报", isOn: bool("dailyBriefingEnabled")); TextField("时间 HH:mm", text: str("dailyBriefingTime")); Picker("渠道", selection: str("dailyBriefingChannel")) { Text("本地通知").tag("APP_NOTIFICATION"); Text("邮件").tag("EMAIL"); Text("通知与邮件").tag("BOTH") }
                Button("保存邮件订阅") { Task { do { _ = try await store.api.request("PUT", "/api/v1/briefings/daily", body: .object(["enabled": store.state.settings["dailyBriefingEnabled"], "time": store.state.settings["dailyBriefingTime"], "channel": store.state.settings["dailyBriefingChannel"], "timezone": .string(store.state.settings.timezone)])); store.status = "订阅已保存" } catch { store.error = error.localizedDescription } } }.disabled(!store.signedIn)
                Button("试发简报") { Task { do { _ = try await store.api.request("POST", "/api/v1/briefings/daily/test", body: .object(["channel": store.state.settings["dailyBriefingChannel"]])); await store.handleCommands() } catch { store.error = error.localizedDescription } } }.disabled(!store.signedIn)
                Button("取消邮件订阅", role: .destructive) { Task { do { _ = try await store.api.request("DELETE", "/api/v1/briefings/daily"); store.setting("dailyBriefingEnabled", .bool(false)) } catch { store.error = error.localizedDescription } } }.disabled(!store.signedIn)
                Text("本地通知无需登录。Apple 不保证后台唤醒频率；应用每次打开、同步或编辑后重建最近 60 条提醒。通知与邮件不会使用未获服务端授权的云端课表。").font(.caption).foregroundStyle(.secondary)
            }
        }.navigationTitle("通知与简报")
    }
    func bool(_ key: String) -> Binding<Bool> { Binding(get: { store.state.settings[key].bool }, set: { store.setting(key, .bool($0)) }) }
    func str(_ key: String) -> Binding<String> { Binding(get: { store.state.settings[key].string }, set: { store.setting(key, .string($0)) }) }
}
struct CloudSettingsView: View {
    @EnvironmentObject var store: AppStore
    @State private var password = ""
    @State private var busy = false
    var body: some View {
        Form {
            Toggle("启用云同步", isOn: Binding(get: { store.state.settings["cloudSyncEnabled"].bool }, set: { store.setting("cloudSyncEnabled", .bool($0)) }))
            Picker("提供商", selection: str("cloudProvider")) { Text("Classing 官方云").tag("OFFICIAL"); Text("WebDAV").tag("WEBDAV"); Text("Google Drive").tag("GOOGLE_DRIVE") }
            if store.state.settings["cloudProvider"].string == "WEBDAV" { TextField("服务器 HTTPS 地址", text: str("cloudServerUrl")).autocorrectionDisabled().textInputAutocapitalization(.never); TextField("文件路径", text: str("cloudRemotePath")); TextField("用户名", text: str("cloudUsername")); SecureField("密码（留空不变）", text: $password); Button("保存 WebDAV 密码") { Task { do { try await store.vault.setSecret(password, key: "webdav.password"); password = "" } catch { store.error = error.localizedDescription } } } }
            if store.state.settings["cloudProvider"].string == "GOOGLE_DRIVE" { TextField("Drive 文件名", text: str("cloudDriveFileName")); Button("授权 Google Drive") { Task { do { try await DriveAuthorization.shared.authorize(vault: store.vault); store.status = "Drive 已授权" } catch { store.error = error.localizedDescription } } } }
            Section("同步范围") { ForEach(["TIMETABLE", "MOBILE_SETTINGS", "WEAR_SETTINGS"], id: \.self) { scope in Toggle(scope, isOn: Binding(get: { store.state.settings.enabledScopes.contains(scope) }, set: { value in var scopes = store.state.settings.enabledScopes; if value { scopes.insert(scope) } else { scopes.remove(scope) }; store.setting("syncScopes", .string(scopes.sorted().joined(separator: ","))) })) } }
            Button { Task { await store.sync() } } label: { HStack { Text("立即同步"); if store.syncing { ProgressView() } } }.disabled(store.syncing)
            Text(store.status).font(.caption)
            Text("当前后端的官方云（包括设置同步）仅向 Pro 开放；WebDAV/Drive 本身不要求 Classing 会员。设置与课表采用 V2 逐条合并和删除标记；关闭的同步范围不会参与上传或覆盖。Apple 后台任务为尽力执行，不承诺 Android 式固定周期。").font(.caption).foregroundStyle(.secondary)
        }.navigationTitle("云同步").onDisappear { password = "" }
    }
    func str(_ key: String) -> Binding<String> { Binding(get: { store.state.settings[key].string }, set: { store.setting(key, .string($0)) }) }
}
struct BYOKSettingsView: View { var body: some View { ByokProfilesView() } }
struct WatchSettingsView: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject var bridge = PhoneWatchBridge.shared
    var body: some View { Form { Label(bridge.reachable ? "手表当前可连接" : "手表未在前台，课表将排队传输", systemImage: "applewatch"); Button("发送最新课表") { bridge.publish(store.state) }; Toggle("手表显示教师", isOn: Binding(get: { store.state.settings["watchShowTeacher"].bool }, set: { store.setting("watchShowTeacher", .bool($0)) })); Toggle("手表独立排程通知", isOn: Binding(get: { store.state.settings["watchIndependentReminders"].bool }, set: { store.setting("watchIndependentReminders", .bool($0)) })); Text("默认使用 iPhone 镜像通知以避免重复。表盘复杂功能与智能叠放小组件通过 WidgetKit 读取手表本地课表，无需联网。独立登录在手表发起，并在手机上确认。").font(.caption).foregroundStyle(.secondary) }.navigationTitle("Apple Watch") }
}
struct BackupView: View {
    @EnvironmentObject var store: AppStore
    @State private var file: ExportDocument?
    @State private var export = false
    @State private var format = "json"
    @State private var restore: ScheduleSnapshot?
    @State private var confirm = false
    var body: some View {
        List {
            Section("导出") { Picker("格式", selection: $format) { Text("完整 JSON 备份").tag("json"); Text("ICS（未来一年）").tag("ics") }; Button("导出文件") { do { let data: Data; if format == "json" { var s = store.state; s.cloud = CloudDocument(); data = try Wire.encoder.encode(s) } else { let from = SchoolTime.dateString(store.now, zone: store.state.settings.timezone), to = SchoolTime.dateString(SchoolTime.addDays(try SchoolTime.day(from), 366), zone: "UTC"); data = Data(try ICS.export(store.state, from: from, through: to).utf8) }; file = ExportDocument(data: data); export = true } catch { store.error = error.localizedDescription } } }
            Section("本地快照（最近 20 份）") { ForEach(store.state.snapshots) { s in Button { restore = s; confirm = true } label: { VStack(alignment: .leading) { Text(s.reason); Text(Date(timeIntervalSince1970: Double(s.createdAt) / 1000).formatted()).font(.caption).foregroundStyle(.secondary) } } } }
        }.navigationTitle("备份与导出").fileExporter(isPresented: $export, document: file, contentType: format == "json" ? .json : .plainText, defaultFilename: "classing.\(format)") { result in if case .failure(let error) = result { store.error = error.localizedDescription } }
        .confirmationDialog("恢复此快照？", isPresented: $confirm) { Button("恢复") { if let s = restore { store.restore(s) } } } message: { Text("当前课表会先保存为新的快照。") }
    }
}
struct ExportDocument: FileDocument { static var readableContentTypes: [UTType] { [.data] }; var data: Data; init(data: Data) { self.data = data }; init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }; func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) } }
