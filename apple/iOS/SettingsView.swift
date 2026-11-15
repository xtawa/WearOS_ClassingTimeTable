import SwiftUI
import ClassingCore
import UniformTypeIdentifiers
struct SemesterSettingsView: View {
    @EnvironmentObject var store: AppStore
    @State private var semester = Date()
    @State private var zone = ""
    var body: some View {
        Form {
            Section("学期") { DatePicker("第一教学周开始", selection: $semester, displayedComponents: .date); Picker("每周开始日", selection: string("weekStartDay")) { ForEach(Array(SchoolTime.weekdays.enumerated()), id: \.offset) { index, day in Text(["周一", "周二", "周三", "周四", "周五", "周六", "周日"][index]).tag(day) } }; Picker("周次模式", selection: string("weekNumberMode")) { Text("学期周").tag("SEMESTER"); Text("自然周").tag("NATURAL") }; Toggle("显示周末", isOn: boolean("showWeekend")) }
            Section("学校时区") { TextField("例如 Asia/Shanghai", text: $zone).autocorrectionDisabled().textInputAutocapitalization(.never) }
        }.navigationTitle("学期设置").environment(\.timeZone, TimeZone(identifier: zone) ?? .current)
        .onAppear { zone = store.state.settings.timezone; semester = (try? SchoolTime.instant(date: store.state.settings.semesterStart, minute: 0, zone: zone)) ?? Date() }
        .toolbar { Button("保存") { store.mutate { $0.settings["semesterWeekStartDate"] = .string(SchoolTime.dateString(semester, zone: zone)); $0.settings["timezone"] = .string(zone) } } }
    }
    func string(_ key: String) -> Binding<String> { Binding(get: { store.state.settings[key].string }, set: { store.setting(key, .string($0)) }) }
    func boolean(_ key: String) -> Binding<Bool> { Binding(get: { store.state.settings[key].bool }, set: { store.setting(key, .bool($0)) }) }
}
struct NotificationSettingsView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        Form {
            Section("课程提醒") { Toggle("启用课程提醒", isOn: bool("reminderEnabled")); Stepper("提前 \(store.state.settings["reminderMinutes"].int) 分钟", value: Binding(get: { Int(store.state.settings["reminderMinutes"].int) }, set: { store.setting("reminderMinutes", .int(Int64($0))) }), in: 0...120, step: 5); Button("授权系统通知") { Task { do { guard try await LocalNotifications.permission() else { throw ClassingError.invalid("通知未获授权，请在系统设置中开启。") }; store.setting("reminderEnabled", .bool(true)) } catch { store.report(error) } } }; Button("发送本地测试通知") { Task { do { try await LocalNotifications.test("Classing 测试", body: "提醒将在这里显示。") } catch { store.report(error) } } } }
            Section { NavigationLink("每日简报") { DailyBriefingSettingsView() } }
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
            if store.state.settings["cloudProvider"].string == "WEBDAV" { TextField("服务器 HTTPS 地址", text: str("cloudServerUrl")).autocorrectionDisabled().textInputAutocapitalization(.never); TextField("文件路径", text: str("cloudRemotePath")); TextField("用户名", text: str("cloudUsername")); SecureField("密码（留空不变）", text: $password); Button("保存 WebDAV 密码") { Task { do { try await store.vault.setSecret(password, key: "webdav.password"); password = "" } catch { store.report(error) } } } }
            if store.state.settings["cloudProvider"].string == "GOOGLE_DRIVE" { TextField("Drive 文件名", text: str("cloudDriveFileName")); Button("授权 Google Drive") { Task { do { try await DriveAuthorization.shared.authorize(vault: store.vault); store.status = "Drive 已授权" } catch { store.report(error) } } } }
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
    var body: some View {
        Form {
            Section("连接") {
                Label(bridge.connectionSummary, systemImage: "applewatch")
                Text(bridge.deliveryStatus).font(.caption).foregroundStyle(.secondary)
                if let confirmed = bridge.lastAcknowledgedAt { LabeledContent("最近接收确认", value: confirmed.formatted(date: .omitted, time: .shortened)) }
                Button("发送最新课表") { bridge.publish(store.state, force: true) }
                Text("账号默认随 iPhone 同步").font(.caption).foregroundStyle(.secondary)
            }
            Section("手表偏好") {
                Toggle("显示教师", isOn: Binding(get: { store.state.settings["watchShowTeacher"].bool }, set: { store.setting("watchShowTeacher", .bool($0)) }))
                Toggle("手表独立提醒", isOn: Binding(get: { store.state.settings["watchIndependentReminders"].bool }, set: { store.setting("watchIndependentReminders", .bool($0)) }))
                Text("默认镜像 iPhone 通知。课前提前时间在“提醒与简报”设置，避免两端重复提醒。").font(.caption).foregroundStyle(.secondary)
            }
            Section { Text("已保存的课表与表盘组件可离线查看。账号、订阅、云服务与 AI 模型均由 iPhone 管理。").font(.caption).foregroundStyle(.secondary) }
        }.navigationTitle("Apple Watch")
    }
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
            Section("导出") { Picker("格式", selection: $format) { Text("完整 JSON 备份").tag("json"); Text("ICS（未来一年）").tag("ics") }; Button("导出文件") { do { let data: Data; if format == "json" { var s = store.state; s.cloud = CloudDocument(); data = try Wire.encoder.encode(s) } else { let from = SchoolTime.dateString(store.now, zone: store.state.settings.timezone), to = SchoolTime.dateString(SchoolTime.addDays(try SchoolTime.day(from), 366), zone: "UTC"); data = Data(try ICS.export(store.state, from: from, through: to).utf8) }; file = ExportDocument(data: data); export = true } catch { store.report(error) } } }
            Section("本地快照（最近 20 份）") { ForEach(store.state.snapshots) { s in Button { restore = s; confirm = true } label: { VStack(alignment: .leading) { Text(s.reason); Text(Date(timeIntervalSince1970: Double(s.createdAt) / 1000).formatted()).font(.caption).foregroundStyle(.secondary) } } } }
        }.navigationTitle("备份与导出").fileExporter(isPresented: $export, document: file, contentType: format == "json" ? .json : .plainText, defaultFilename: "classing.\(format)") { result in if case .failure(let error) = result { store.report(error) } }
        .confirmationDialog("恢复此快照？", isPresented: $confirm) { Button("恢复") { if let s = restore { store.restore(s) } } } message: { Text("当前课表会先保存为新的快照。") }
    }
}
struct ExportDocument: FileDocument { static var readableContentTypes: [UTType] { [.data] }; var data: Data; init(data: Data) { self.data = data }; init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }; func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) } }
