import SwiftUI
import ClassingCore

struct DailyBriefingSettingsView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.scenePhase) private var phase
    @State private var enabled = false
    @State private var channel = "APP_NOTIFICATION"
    @State private var time = "20:00"
    @State private var zone = "Asia/Shanghai"
    @State private var busy = false
    @State private var dirty = false
    @State private var feedback = ""
    private var owner: String { store.account["userId"].string }
    var body: some View {
        Form {
            Section {
                Toggle("每日简报", isOn: edited($enabled))
                TextField("时间 HH:mm", text: edited($time)).keyboardType(.numbersAndPunctuation)
                Picker("接收方式", selection: edited($channel)) {
                    Text("应用通知").tag("APP_NOTIFICATION")
                    if store.signedIn { Text("邮件").tag("EMAIL"); Text("通知与邮件").tag("BOTH") }
                }
                TextField("时区", text: edited($zone)).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            Section {
                Button(store.signedIn ? "保存并同步" : "保存本机提醒") { Task { await save() } }
                if store.signedIn { Button("试发简报") { Task { await sendTest() } } }
                if busy { ProgressView("正在处理…") }
                if !feedback.isEmpty { Text(feedback).font(.footnote).foregroundStyle(.secondary).accessibilityIdentifier("briefing-feedback") }
            }
        }.disabled(busy).navigationTitle("每日简报")
        .task(id: owner) { await load() }
        .onChange(of: phase) { _, value in if value == .active && !dirty { Task { await load() } } }
        .refreshable { if !dirty { await load() } }
    }
    private func edited<T>(_ binding: Binding<T>) -> Binding<T> {
        Binding(get: { binding.wrappedValue }, set: { binding.wrappedValue = $0; dirty = true; feedback = "尚未保存" })
    }
    private func apply(_ value: JSONValue) {
        enabled = value["enabled"].bool; channel = value["channel"].string; time = value["time"].string; zone = value["timezone"].string; dirty = false
        store.mutate {
            $0.settings["dailyBriefingEnabled"] = .bool(enabled); $0.settings["dailyBriefingChannel"] = .string(channel)
            $0.settings["dailyBriefingTime"] = .string(time); $0.settings["dailyBriefingTimezone"] = .string(zone)
        }
    }
    private func load() async {
        guard !busy else { return }; busy = true; feedback = "正在读取设置…"; defer { busy = false }
        if !store.signedIn || store.demo {
            enabled = store.state.settings["dailyBriefingEnabled"].bool; channel = store.state.settings["dailyBriefingChannel"].string
            time = store.state.settings["dailyBriefingTime"].string; zone = store.state.settings["dailyBriefingTimezone"].string.isEmpty ? store.state.settings.timezone : store.state.settings["dailyBriefingTimezone"].string
            dirty = false; feedback = ""; return
        }
        let expected = owner
        do { let value = try await store.api.request("GET", "/api/v1/briefings/daily")["briefing"]; guard expected == owner else { return }; apply(value); feedback = "已与服务端同步" }
        catch { feedback = ClassingError.userMessage(error); store.report(error) }
    }
    private func save() async {
        guard !busy else { return }
        do { _ = try SchoolTime.minute(time); guard TimeZone(identifier: zone) != nil else { throw ClassingError.invalid("请输入有效的时区，例如 Asia/Shanghai。") } }
        catch { store.report(error); return }
        busy = true; feedback = "正在保存…"; defer { busy = false }
        let value = JSONValue.object(["enabled": .bool(enabled), "channel": .string(channel), "time": .string(time), "timezone": .string(zone)]), expected = owner
        do {
            if store.signedIn && !store.demo { let result = try await store.api.request("PUT", "/api/v1/briefings/daily", body: value)["briefing"]; guard expected == owner else { return }; apply(result); feedback = "已保存并与服务端同步" }
            else { apply(value); feedback = "本机提醒已保存" }
        } catch { feedback = ClassingError.userMessage(error); store.report(error) }
    }
    private func sendTest() async {
        guard !busy else { return }; busy = true; feedback = "正在试发…"; defer { busy = false }
        do { _ = try await store.api.request("POST", "/api/v1/briefings/daily/test", body: .object(["channel": .string(channel)])); await store.handleCommands(); feedback = "试发请求已提交" }
        catch { feedback = ClassingError.userMessage(error); store.report(error) }
    }
}
