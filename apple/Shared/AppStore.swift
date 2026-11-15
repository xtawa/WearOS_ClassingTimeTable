import SwiftUI
import ClassingCore
import WidgetKit
import UserNotifications

@MainActor final class AppStore: ObservableObject {
    @Published private(set) var state = AppState()
    @Published var error: String?
    @Published var status = ""
    @Published var syncing = false
    @Published var signedIn = false
    private(set) var accountResolved = false
    @Published var account: JSONValue = .null
    @Published var membership: JSONValue = .null
    @Published var byok = ByokConfiguration(ownerId: "local")
    let vault = KeychainVault()
    lazy var api = ClassingAPI(baseURL: URL(string: Bundle.main.object(forInfoDictionaryKey: "ClassingAPIBaseURL") as? String ?? "https://api-classing.underflo.ink")!, vault: vault, platform: Self.platform, bundleID: Bundle.main.bundleIdentifier ?? "com.xtawa.classingtime.apple")
    private var realtime: Task<Void, Never>?, refreshServices: Task<Void, Never>?
    private var readFailure = false, started = false, syncingSettings = false
    private var companionPrepared = false
    private var queuedCloudEvent: SSEEvent?
    var demo = false
    var officialCloudAllowed: Bool { signedIn && membership["access"]["cloudSync"].bool && membership["expiresAt"].int > Wire.milliseconds() }
    var byokAllowed: Bool { signedIn && (membership["access"].object["byok"]?.bool ?? membership["access"]["cloudSync"].bool) && membership["expiresAt"].int > Wire.milliseconds() }
    var companionAccountID: String? {
        guard accountResolved else { return nil }; guard signedIn else { return "" }
        let id = account["userId"].string.isEmpty ? account["id"].string : account["userId"].string
        return id.isEmpty ? nil : id
    }
    func report(_ error: Error) {
        guard !Task.isCancelled, !ClassingError.isCancellation(error) else { return }
        self.error = ClassingError.userMessage(error)
    }
    var now: Date { demo ? ISO8601DateFormatter().date(from: "2026-11-16T01:41:00Z")! : Date() }
    static var platform: String {
        #if os(watchOS)
        return "WATCHOS"
        #else
        return "IOS"
        #endif
    }
    static var companionRole: CompanionSync.Role {
        #if os(watchOS)
        return .watch
        #else
        return .phone
        #endif
    }
    init(preview: Bool = false) {
        do { state = try SharedDisk.load() } catch { self.error = "本地文件读取失败，原文件已保留：\(error.localizedDescription)"; readFailure = true }
        #if DEBUG
        if preview || ProcessInfo.processInfo.arguments.contains("--demo") || ProcessInfo.processInfo.arguments.contains("--ui-testing") {
            demo = true
            if let url = Bundle.main.url(forResource: "demo", withExtension: "json"), let data = try? Data(contentsOf: url), let draft = try? ScheduleImport.json(data), let loaded = try? draft.applying(to: AppState(), replace: true) { state = loaded; state.settings["onboardingCompleted"] = .bool(true) }
            if ProcessInfo.processInfo.arguments.contains("--dark") { state.settings["appearance"] = .string("dark") }
            if ProcessInfo.processInfo.arguments.contains("--empty") { state.lessons = []; state.exams = []; state.exceptions = [] }
            if ProcessInfo.processInfo.arguments.contains("--exam") { state.settings["academicMode"] = .string("EXAM") }
            if ProcessInfo.processInfo.arguments.contains("--onboarding") { state.settings["onboardingCompleted"] = .bool(false) }
            if ProcessInfo.processInfo.arguments.contains("--assistant-preview") { signedIn = true }
        }
        #endif
        prepareCompanion()
    }
    private func prepareCompanion() {
        guard !demo, !readFailure, !companionPrepared else { return }
        do {
            var current = state; try CompanionSync.capture(&current, role: Self.companionRole); try SharedDisk.save(current); state = current
            companionPrepared = true; PhoneWatchBridge.shared.attach(self)
        } catch { report(error) }
    }
    func start() async {
        guard !started else { return }; started = true
        if demo { changedServices(); return }
        prepareCompanion()
        do { let session = try await api.session(); signedIn = session != nil; account = session?.account ?? .null; accountResolved = true
            if signedIn { try await refreshAccount() } else { try await loadPrivateByok() }
        } catch { report(error); if (try? await api.session()) == nil { signedIn = false; account = .null } }
        PhoneWatchBridge.shared.accountDidChange()
        changedServices()
        if state.settings["cloudSyncEnabled"].bool && signedIn { await sync(); startRealtime() }
    }
    func commit(_ value: AppState, reason: String? = nil) throws {
        guard !readFailure else { throw ClassingError.invalid("请先导出损坏的本地文件并恢复备份，避免覆盖原始数据。") }
        var candidate = value; if let reason = reason { candidate.backup(reason) }; try candidate.validate(); candidate.settings.values = candidate.settings.values.filter { !$0.key.lowercased().hasPrefix("byok") }; candidate.snapshots = candidate.snapshots.map { snapshot in var s = snapshot; s.settings.values = s.settings.values.filter { !$0.key.lowercased().hasPrefix("byok") }; return s }; try CompanionSync.capture(&candidate, role: Self.companionRole)
        if !demo { try SharedDisk.save(candidate) }; state = candidate; changedServices(); if !demo { PhoneWatchBridge.shared.publish(candidate) }
    }
    func mutate(_ reason: String? = nil, _ body: (inout AppState) throws -> Void) {
        do { var candidate = state; if let reason = reason { candidate.backup(reason) }; try body(&candidate); try commit(candidate) } catch { self.error = error.localizedDescription }
    }
    func setting(_ key: String, _ value: JSONValue) { mutate { $0.settings[key] = value }; if key == "cloudSyncEnabled" { if value.bool { startRealtime() } else { stopRealtime() } } }
    func save(_ lesson: Lesson) { mutate("编辑课程前") { try lesson.validate(); if let i = $0.lessons.firstIndex(where: { $0.id == lesson.id }) { $0.lessons[i] = lesson } else { $0.lessons.append(lesson) } } }
    func save(_ exam: Exam) { mutate("编辑考试前") { try exam.validate(); if let i = $0.exams.firstIndex(where: { $0.id == exam.id }) { $0.exams[i] = exam } else { $0.exams.append(exam) } } }
    func save(_ exception: ScheduleException) { mutate("编辑调课前") { try exception.validate(); if let i = $0.exceptions.firstIndex(where: { $0.id == exception.id }) { $0.exceptions[i] = exception } else { $0.exceptions.append(exception) } } }
    @discardableResult func receive(_ document: CloudDocument) -> Bool { do { var current = state; try CompanionSync.capture(&current, role: Self.companionRole); try CompanionSync.apply(document, to: &current, role: Self.companionRole)
        guard current.cloud != state.cloud || current.lessons != state.lessons || current.exams != state.exams || current.exceptions != state.exceptions || current.settings != state.settings else { return true }
        try commit(current); status = "已与另一设备合并"; return true } catch { report(error); return false } }
    func restore(_ snapshot: ScheduleSnapshot) { mutate("恢复备份前") { $0.lessons = snapshot.lessons; $0.exceptions = snapshot.exceptions; $0.exams = snapshot.exams; $0.settings = snapshot.settings } }
    func occurrences(_ from: String, _ through: String) -> [Occurrence] { (try? ScheduleProjector.project(state, from: from, through: through)) ?? [] }
    var upcoming: [Occurrence] { (try? ScheduleProjector.upcoming(state, now: now)) ?? [] }
    var today: [Occurrence] { let d = SchoolTime.dateString(now, zone: state.settings.timezone); return occurrences(d, d) }
    func agenda(at date: Date) -> AgendaSnapshot {
        (try? AgendaSnapshot.make(state, at: date)) ?? (try! AgendaSnapshot.make(AppState(), at: date))
    }
    func refreshAccount() async throws {
        let profile = try await api.request("GET", "/api/v1/account/me"); account = profile["account"]
        let owner = account["userId"].string.isEmpty ? account["id"].string : account["userId"].string
        if !owner.isEmpty && state.settings["cloudAccountId"].string != owner {
            stopRealtime(); var local = state; local.cloud = CloudDocument()
            local.settings["cloudAccountId"] = .string(owner); local.settings["cloudSyncEnabled"] = .bool(false); local.settings["officialEventVersion"] = .string("")
            try commit(local); status = "账号已切换，请确认课表后重新开启云同步"
        }
        membership = try await api.request("GET", "/api/v1/membership/status")["membership"]
        try await loadPrivateByok()
        accountResolved = true; PhoneWatchBridge.shared.accountDidChange()
    }
    func login(_ identifier: String, _ password: String) async throws { _ = try await api.login(identifier: identifier, password: password); signedIn = true; try await refreshAccount(); startRealtime() }
    func signedInAfterRegistration(_ response: JSONValue) async throws { try await api.setSession(AuthSession(response)); signedIn = true; try await refreshAccount(); startRealtime() }
    func logout() async { stopRealtime(); do { try await api.logout() } catch { report(error) }; signedIn = false; account = .null; membership = .null; accountResolved = true; PhoneWatchBridge.shared.accountDidChange(); do { try await loadPrivateByok() } catch { report(error) } }
    func setCompanionSession(_ session: AuthSession?) async throws {
        stopRealtime(); try await api.setSession(session); signedIn = session != nil; account = session?.account ?? .null; membership = .null; accountResolved = true
        if session != nil { try await refreshAccount(); startRealtime() } else { try await loadPrivateByok() }
    }
    func provider() async throws -> any CloudProvider {
        switch state.settings["cloudProvider"].string {
        case "WEBDAV":
            guard let url = URL(string: state.settings["cloudServerUrl"].string), let target = URL(string: state.settings["cloudRemotePath"].string, relativeTo: url)?.absoluteURL, target.host == url.host else { throw ClassingError.invalid("WebDAV 地址无效。") }
            return try WebDAVCloud(url: target, username: state.settings["cloudUsername"].string, password: await vault.secret("webdav.password"))
        case "GOOGLE_DRIVE":
            #if os(iOS)
            let token = try await DriveAuthorization.shared.accessToken(vault: vault); return DriveCloud(token: token, filename: state.settings["cloudDriveFileName"].string.isEmpty ? "classing_sync.json" : state.settings["cloudDriveFileName"].string)
            #else
            throw ClassingError.invalid("请在 iPhone 上授权 Google Drive，并通过设备同步更新手表。")
            #endif
        default: return OfficialCloud(api: api)
        }
    }
    func sync() async {
        guard !demo else { status = "演示模式不访问云端"; return }
        guard !syncing else { return }; syncing = true; defer { syncing = false; drainQueuedCloudEvent() }
        do {
            if state.settings["cloudProvider"].string == "OFFICIAL" {
                membership = try await api.request("GET", "/api/v1/membership/status")["membership"]
                guard officialCloudAllowed else { throw ClassingError.invalid("当前后端的官方云同步仅向 Pro 开放；本地功能、WebDAV/Drive 与每日简报不因此关闭。") }
            }
            let provider = try await provider()
            for attempt in 0..<3 {
                let (remote, version) = try await provider.read()
                // Rebase on the CURRENT state after each await, so in-flight edits cannot be lost.
                var current = state; try CompanionSync.capture(&current, role: Self.companionRole); try CloudSync.apply(remote, to: &current)
                do {
                    let upload = try CloudSync.upload(current, preserving: remote)
                    if upload != remote { try await provider.write(upload, version: version) }
                    // Incorporate edits made while PUT was in flight before committing the result.
                    var latest = state; try CompanionSync.capture(&latest, role: Self.companionRole); try CloudSync.apply(current.cloud, to: &latest); let hasPendingEdits = latest.cloud.records != current.cloud.records
                    try commit(latest)
                    if hasPendingEdits && attempt < 2 { continue }
                    if hasPendingEdits { status = "新修改已保存在本地，等待下一次同步"; return }
                    if officialCloudAllowed && state.settings["cloudProvider"].string != "OFFICIAL" {
                        do { try await syncOfficialSettings() }
                        catch ClassingError.network(403, _, _) { membership = (try? await api.request("GET", "/api/v1/membership/status")["membership"]) ?? .null }
                    }; status = "同步完成"; await handleCommands(); return
                } catch ClassingError.conflict { if attempt == 2 { throw ClassingError.conflict }; try await Task.sleep(nanoseconds: UInt64(attempt + 1) * 400_000_000) }
            }
        } catch {
            if Task.isCancelled || ClassingError.isCancellation(error) { status = "课表已保留在本机" }
            else { report(error); status = "同步失败" }
        }
    }
    func refreshAgenda() async {
        changedServices()
        guard state.settings["cloudSyncEnabled"].bool else { status = "课表已刷新"; return }
        await sync()
    }
    func syncOfficialSettings() async throws {
        guard !demo, officialCloudAllowed, !syncingSettings else { return }; syncingSettings = true; defer { syncingSettings = false; drainQueuedCloudEvent() }
        let domains: Set<String> = ["mobile.settings", "wear.settings", "cloud.config", "app.commands"]
        let provider = OfficialCloud(api: api)
        for attempt in 0..<3 {
            let (remote, version) = try await provider.read()
            var current = state; try CompanionSync.capture(&current, role: Self.companionRole); try CloudSync.apply(remote, to: &current, domains: domains)
            do {
                let upload = try CloudSync.upload(current, preserving: remote, domains: domains)
                if upload != remote { try await provider.write(upload, version: version) }
                var latest = state; try CompanionSync.capture(&latest, role: Self.companionRole); try CloudSync.apply(current.cloud, to: &latest, domains: domains); try commit(latest); return
            } catch ClassingError.conflict { if attempt == 2 { throw ClassingError.conflict }; try await Task.sleep(nanoseconds: UInt64(attempt + 1) * 400_000_000) }
        }
    }
    func startRealtime() {
        guard realtime == nil, officialCloudAllowed, state.settings["cloudSyncEnabled"].bool, !demo else { return }
        realtime = Task { [weak self] in
            var delay: UInt64 = 1
            while !Task.isCancelled {
                guard let self = self else { return }
                do {
                    let cursor = self.state.settings["officialEventVersion"].string
                    try await self.api.events("/api/v1/cloud/official/events", body: nil, headers: cursor.isEmpty ? [:] : ["Last-Event-ID": cursor]) { [weak self] event in
                        guard event.event == "cloud-document" else { return }; await self?.receiveCloudEvent(event)
                    }; delay = 1
                } catch {
                    if Task.isCancelled { return }
                    if case ClassingError.network(403, _, _) = error { self.membership = (try? await self.api.request("GET", "/api/v1/membership/status")["membership"]) ?? .null; if !self.officialCloudAllowed { self.stopRealtime(); return } }
                    if (try? await self.api.session()) == nil { self.signedIn = false; self.stopRealtime(); return }
                    delay = min(delay * 2, 60)
                }
                try? await Task.sleep(nanoseconds: delay * 1_000_000_000)
            }
        }
    }
    func receiveCloudEvent(_ event: SSEEvent) async {
        guard state.settings["cloudSyncEnabled"].bool, signedIn else { return }
        let incoming = Int64(event.id ?? "") ?? -1, applied = Int64(state.settings["officialEventVersion"].string) ?? -1
        guard incoming > applied else { return }
        if syncing || syncingSettings {
            if queuedCloudEvent == nil || incoming > (Int64(queuedCloudEvent?.id ?? "") ?? -1) { queuedCloudEvent = event }
            return
        }
        var succeeded = false
        if state.settings["cloudProvider"].string == "OFFICIAL" { await sync(); succeeded = status == "同步完成" }
        else { do { try await syncOfficialSettings(); succeeded = true } catch { status = "官方设置同步暂不可用" } }
        if succeeded && incoming > (Int64(state.settings["officialEventVersion"].string) ?? -1) { setting("officialEventVersion", .string(String(incoming))) }
    }
    private func drainQueuedCloudEvent() {
        guard !syncing, !syncingSettings, state.settings["cloudSyncEnabled"].bool, signedIn, let event = queuedCloudEvent else { return }
        queuedCloudEvent = nil
        Task { [weak self] in await self?.receiveCloudEvent(event) }
    }
    func stopRealtime() { realtime?.cancel(); realtime = nil; queuedCloudEvent = nil }
    func becameActive() async { await start(); guard !demo else { return }; signedIn = (try? await api.session()) != nil; if !signedIn { account = .null; accountResolved = true }; PhoneWatchBridge.shared.accountDidChange(); changedServices(); if state.settings["cloudSyncEnabled"].bool { await sync(); startRealtime() }; await handleCommands() }
    func becameInactive() { stopRealtime() }
    private func changedServices() {
        refreshServices?.cancel(); let state = state
        refreshServices = Task { do {
            if !demo {
                try await LocalNotifications.rebuild(state)
                #if os(iOS)
                try await LiveActivityManager.update(agenda(at: now), enabled: state.settings["liveActivitiesEnabled"].bool)
                #endif
            }
            WidgetCenter.shared.reloadAllTimelines()
        } catch { report(error) } }
    }
    func handleCommands() async {
        guard signedIn, !demo else { return }
        do {
            let response = try await api.request("GET", "/api/v1/briefings/daily/notifications")
            for command in response["commands"].array where !state.processedCommands.contains(command["id"].string) && command["createdAt"].int > Wire.milliseconds() - 600_000 {
                try await LocalNotifications.test("每日简报测试", body: "通知链路正常。今日 \(today.count) 节课。")
                mutate { $0.processedCommands.append(command["id"].string); $0.processedCommands = Array($0.processedCommands.suffix(100)) }
            }
        } catch { /* No credentials or timetable are logged; retry when foregrounded. */ }
    }
}

@MainActor enum LocalNotifications {
    static func permission() async throws -> Bool { try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) }
    static func rebuild(_ state: AppState) async throws {
        let center = UNUserNotificationCenter.current(), settings = await center.notificationSettings()
        let pending = await center.pendingNotificationRequests(); center.removePendingNotificationRequests(withIdentifiers: pending.filter { $0.identifier.hasPrefix("classing.") }.map(\.identifier))
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
        #if os(watchOS)
        // By default the watch receives mirrored iPhone notifications, avoiding duplicates.
        guard state.settings["watchIndependentReminders"].bool else { return }
        #endif
        for p in try ReminderPlanner.plan(state) {
            try Task.checkCancellation(); let content = UNMutableNotificationContent(); content.title = p.title; content.body = p.body; content.sound = .default; content.userInfo = ["url": "classing://today"]
            let components = SchoolTime.calendar(zone: "UTC").dateComponents([.year, .month, .day, .hour, .minute, .second, .timeZone], from: p.at)
            try await center.add(UNNotificationRequest(identifier: p.id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false)))
        }
    }
    static func test(_ title: String, body: String) async throws { let c = UNMutableNotificationContent(); c.title = title; c.body = body; c.sound = .default; try await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "classing.test.\(UUID().uuidString)", content: c, trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false))) }
}
