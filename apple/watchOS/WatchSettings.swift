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
            } header: { Text("课前提醒") }
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
    var body: some View {
        List {
            Label(bridge.connectionSummary, systemImage: "iphone")
            Text(bridge.deliveryStatus).font(.caption).foregroundStyle(.secondary)
            Section("手机账号") {
                if store.signedIn { Text(store.account["username"].string.isEmpty ? "已登录" : store.account["username"].string) }
                Text(bridge.accountStatus).font(.caption).foregroundStyle(.secondary)
                Button("同步账号") { bridge.synchronizeAccount() }
            }
        }.navigationTitle("连接 iPhone")
    }
}
