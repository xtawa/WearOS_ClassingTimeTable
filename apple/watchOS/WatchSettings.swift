import SwiftUI
import ClassingCore

struct WatchSettings: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject private var bridge = PhoneWatchBridge.shared
    var body: some View {
        List {
            Section("抬腕查看") {
                Picker("显示模式", selection: Binding(get: { store.state.settings["academicMode"].string }, set: { store.setting("academicMode", .string($0)) })) { Text("课程").tag("SCHEDULE"); Text("考试").tag("EXAM") }
                Toggle("显示教师", isOn: Binding(get: { store.state.settings["watchShowTeacher"].bool }, set: { store.setting("watchShowTeacher", .bool($0)) }))
            }
            Section {
                Toggle("手表独立提醒", isOn: Binding(get: { store.state.settings["watchIndependentReminders"].bool }, set: { store.setting("watchIndependentReminders", .bool($0)) }))
                if store.state.settings["watchIndependentReminders"].bool {
                    Button("允许手表通知") { Task { do { guard try await LocalNotifications.permission() else { throw ClassingError.invalid("请在系统设置中允许通知。") }; try await LocalNotifications.rebuild(store.state) } catch { store.error = error.localizedDescription } } }
                }
            } header: { Text("课前提醒") } footer: { Text("默认镜像 iPhone 通知。提前时间与每日简报在手机调整。") }
            Section("iPhone") {
                NavigationLink { WatchCompanionView() } label: { Label("连接与同步", systemImage: "iphone") }
                Button("更新课表") { do { try bridge.requestTimetable() } catch { store.report(error) } }
                Text(bridge.deliveryStatus).font(.caption).foregroundStyle(.secondary)
            }
        }.navigationTitle("设置")
    }
}

struct WatchCompanionView: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject private var bridge = PhoneWatchBridge.shared
    @State private var authorization: JSONValue = .null
    @State private var busy = false
    var body: some View {
        List {
            Label(bridge.connectionSummary, systemImage: "iphone")
            Text(bridge.deliveryStatus).font(.caption).foregroundStyle(.secondary)
            if store.signedIn {
                Label("账号已连接", systemImage: "checkmark.circle")
                Text("账号资料、订阅与助手设置请在 iPhone 管理。").font(.caption).foregroundStyle(.secondary)
            } else {
                Text("账号登录与授权在 iPhone 完成。离线查看已保存的课表无需登录。").font(.caption).foregroundStyle(.secondary)
                Button("在 iPhone 上继续") { Task { await startAuthorization() } }.disabled(busy || !authorization["authorizationId"].string.isEmpty)
                if !authorization["authorizationId"].string.isEmpty { ProgressView("等待 iPhone 确认"); Button("取消连接") { authorization = .null } }
            }
        }.navigationTitle("连接 iPhone")
        .task(id: authorization["authorizationId"].string) {
            let id = authorization["authorizationId"].string; guard !id.isEmpty else { return }
            while !Task.isCancelled && !store.signedIn && Wire.milliseconds() < authorization["expiresAt"].int {
                do {
                    let response = try await store.api.request("POST", "/api/v1/auth/device/qr/poll", body: .object(["authorizationId": .string(id), "pollSecret": authorization["pollSecret"]]), authorized: false)
                    if response["session"] != .null { try await store.signedInAfterRegistration(response); authorization = .null; return }
                    try await Task.sleep(nanoseconds: UInt64(max(5, authorization["intervalSeconds"].int)) * 1_000_000_000)
                } catch { if !Task.isCancelled { authorization = .null; store.error = error.localizedDescription }; return }
            }
            if !Task.isCancelled && !store.signedIn { authorization = .null; store.error = "连接已过期，请在 iPhone 打开 Classing 后重试。" }
        }.onDisappear { authorization = .null }
    }
    private func startAuthorization() async {
        guard bridge.reachable else { store.error = "请先在 iPhone 上打开 Classing。"; return }
        busy = true; defer { busy = false }
        do {
            authorization = try await store.api.request("POST", "/api/v1/auth/device/qr/start", body: .object(["deviceName": .string("Apple Watch")]), authorized: false)
            try bridge.requestApproval(authorization["authorizationId"].string)
        } catch { authorization = .null; store.error = error.localizedDescription }
    }
}
