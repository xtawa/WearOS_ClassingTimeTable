import SwiftUI
import ClassingCore
struct ByokProfilesView: View {
    @EnvironmentObject var store: AppStore
    @State private var editor: ByokProfile?
    @State private var deleting: String?
    @State private var confirmDelete = false
    @State private var confirmWatch = false
    @State private var busy = false
    @State private var feedback = ""
    var body: some View {
        Form {
            if store.byokAllowed {
                Toggle("使用 BYOK 助手", isOn: Binding(get: { store.byok.enabled }, set: { value in var c = store.byok; c.enabled = value; save(c) }))
                Section("用途分配") {
                    selection("问答", role: "CHAT", key: \.chatId); selection("识图", role: "VISION", key: \.visionId); selection("转写", role: "TRANSCRIPTION", key: \.transcriptionId); selection("朗读", role: "SPEECH", key: \.speechId); selection("Apple Watch", role: "WEAR", key: \.wearId)
                }
                Section("模型配置（最多 20 组）") {
                    ForEach(store.byok.profiles) { p in
                        HStack {
                            Button { editor = p } label: { VStack(alignment: .leading) { Text(p.name); Text("\(p.role) · \(p.model)").font(.caption).foregroundStyle(.secondary) } }
                            Spacer()
                            Button(role: .destructive) { deleting = p.id; confirmDelete = true } label: { Image(systemName: "trash") }.buttonStyle(.borderless).accessibilityLabel("删除 \(p.name)")
                        }
                    }
                    Button("添加模型", systemImage: "plus") { editor = ByokProfile() }.disabled(store.byok.profiles.count >= 20)
                }
                Button("加密发送选中的手表模型") { confirmWatch = true }.disabled(store.byok.wearId.isEmpty)
            } else {
                Section {
                    Label("BYOK 需要有效的 Classing Pro 订阅", systemImage: "lock")
                    if !store.signedIn { NavigationLink("登录账号") { AccountView() } }
                    NavigationLink("开通 Classing Pro") { SubscriptionView() }
                    if store.byok.enabled { Button("使用 Classing 助手") { Task { do { var config = store.byok; config.enabled = false; try await store.savePrivateByok(config) } catch { store.report(error) } } } }
                }
            }
            if busy { ProgressView("正在处理…") }
            if !feedback.isEmpty { Text(feedback).font(.footnote).foregroundStyle(.secondary) }
        }.disabled(busy).navigationTitle("BYOK · 自备模型")
        .sheet(item: $editor) { p in NavigationStack { ByokProfileEditor(profile: p) } }
        .onChange(of: store.byokAllowed) { _, allowed in if !allowed { editor = nil; confirmWatch = false; confirmDelete = false } }
        .confirmationDialog("删除模型和本机密钥？", isPresented: $confirmDelete) {
            Button("删除", role: .destructive) { run("模型已删除") { if let id = deleting { try await store.removeByokProfile(id) } } }
        }
        .confirmationDialog("将所选模型的密钥发送到自己的手表？", isPresented: $confirmWatch) {
            Button("加密发送") { run("已加密发送，等待手表确认") { try await PhoneWatchBridge.shared.sendBYOK() } }
        }
    }
    func selection(_ title: String, role: String, key: WritableKeyPath<ByokConfiguration, String>) -> some View {
        Picker(title, selection: Binding(get: { store.byok[keyPath: key] }, set: { id in var c = store.byok; c[keyPath: key] = id; save(c) })) { Text("未选择").tag(""); ForEach(store.byok.profiles.filter { role == "WEAR" ? ["CHAT", "VISION"].contains($0.role) : $0.role == role }) { Text($0.name).tag($0.id) } }
    }
    func save(_ c: ByokConfiguration) { run("配置已保存") { try await store.savePrivateByok(c) } }
    func run(_ success: String, action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        guard store.byokAllowed else { store.report(ClassingError.invalid("请先开通或续订 Classing Pro。")); return }
        busy = true; feedback = ""
        Task { defer { busy = false }; do { try await action(); feedback = success; store.status = success } catch { feedback = "操作未完成，请重试"; store.report(error) } }
    }
}
struct ByokProfileEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var profile: ByokProfile
    @State private var key = ""
    @State private var busy = false
    var body: some View {
        Form {
            if store.byokAllowed {
                TextField("配置名称", text: $profile.name)
                Picker("用途", selection: $profile.role) { Text("问答").tag("CHAT"); Text("识图").tag("VISION"); Text("转写").tag("TRANSCRIPTION"); Text("朗读").tag("SPEECH") }
                TextField("HTTPS API 根地址", text: $profile.baseUrl).textInputAutocapitalization(.never).autocorrectionDisabled()
                TextField("模型 ID", text: $profile.model).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("API Key（留空保留原密钥）", text: $key).textInputAutocapitalization(.never).autocorrectionDisabled()
                if profile.role == "SPEECH" { TextField("音色，如 alloy", text: $profile.voice) }
            } else { Label("BYOK 需要有效的 Classing Pro 订阅", systemImage: "lock"); NavigationLink("开通 Classing Pro") { SubscriptionView() } }
            if busy { ProgressView("正在保存…") }
        }.disabled(busy).navigationTitle("模型配置").toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("保存") { save() }.disabled(busy || !store.byokAllowed) }
            ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() }.disabled(busy) }
        }.onDisappear { key = "" }
    }
    func save() {
        guard !busy, store.byokAllowed else { return }
        busy = true
        Task { defer { busy = false }; do {
            try profile.validate(); let secret = key.isEmpty ? try await store.vault.secret(store.byokKeyName(profile.id)) : key
            _ = try BYOKClient(endpoint: URL(string: profile.baseUrl)!, model: profile.model, key: secret)
            var c = store.byok; if let i = c.profiles.firstIndex(where: { $0.id == profile.id }) { c.profiles[i] = profile } else { c.profiles.append(profile) }
            if c.chatId.isEmpty && profile.role == "CHAT" { c.chatId = profile.id }
            guard store.byokAllowed else { throw ClassingError.invalid("请先开通或续订 Classing Pro。") }
            try c.validate(); try await store.vault.setSecret(secret, key: store.byokKeyName(profile.id)); try await store.savePrivateByok(c); key = ""; store.status = "模型配置已保存"; dismiss()
        } catch { store.report(error) } }
    }
}
