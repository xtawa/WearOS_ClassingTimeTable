import SwiftUI
import ClassingCore
struct ByokProfilesView: View {
    @EnvironmentObject var store: AppStore
    @State private var editor: ByokProfile?
    @State private var deleting: String?
    @State private var confirmDelete = false
    @State private var confirmWatch = false
    var body: some View {
        Form {
            Toggle("使用 BYOK 助手", isOn: Binding(get: { store.byok.enabled }, set: { value in var c = store.byok; c.enabled = value; save(c) }))
            Section("用途分配") {
                selection("问答", role: "CHAT", key: \.chatId); selection("识图", role: "VISION", key: \.visionId); selection("转写", role: "TRANSCRIPTION", key: \.transcriptionId); selection("朗读", role: "SPEECH", key: \.speechId); selection("Apple Watch", role: "WEAR", key: \.wearId)
            }
            Section("模型配置（最多 20 组）") {
                ForEach(store.byok.profiles) { p in HStack { Button { editor = p } label: { VStack(alignment: .leading) { Text(p.name); Text("\(p.role) · \(p.model)").font(.caption).foregroundStyle(.secondary) } }; Spacer(); Button(role: .destructive) { deleting = p.id; confirmDelete = true } label: { Image(systemName: "trash") }.buttonStyle(.borderless) } }
                Button("添加模型", systemImage: "plus") { editor = ByokProfile() }.disabled(store.byok.profiles.count >= 20)
            }
            Button("加密发送选中的手表模型") { confirmWatch = true }
            Section { Text("配置、API Key 和 BYOK 对话历史按账号保存在本机钥匙串，不进入官方云或课表备份。未登录时使用独立的 local 配置。请求直接发送到所选供应商，不消耗 Classing 点数。").font(.caption).foregroundStyle(.secondary); Text("跨设备只发送明确选中的手表模型，经 Curve25519/AES-GCM 加密，两端须处于相同 Classing 账号或都未登录，并在手表再次确认。").font(.caption).foregroundStyle(.secondary) }
        }.navigationTitle("BYOK · 自备模型")
        .sheet(item: $editor) { p in NavigationStack { ByokProfileEditor(profile: p) } }
        .confirmationDialog("删除模型和本机密钥？", isPresented: $confirmDelete) { Button("删除", role: .destructive) { Task { do { if let id = deleting { try await store.removeByokProfile(id) } } catch { store.error = error.localizedDescription } } } }
        .confirmationDialog("将所选模型的密钥发送到自己的手表？", isPresented: $confirmWatch) { Button("加密发送") { Task { do { try await PhoneWatchBridge.shared.sendBYOK() } catch { store.error = error.localizedDescription } } } }
    }
    func selection(_ title: String, role: String, key: WritableKeyPath<ByokConfiguration, String>) -> some View {
        Picker(title, selection: Binding(get: { store.byok[keyPath: key] }, set: { id in var c = store.byok; c[keyPath: key] = id; save(c) })) { Text("未选择").tag(""); ForEach(store.byok.profiles.filter { role == "WEAR" ? ["CHAT", "VISION"].contains($0.role) : $0.role == role }) { Text($0.name).tag($0.id) } }
    }
    func save(_ c: ByokConfiguration) { Task { do { try await store.savePrivateByok(c) } catch { store.error = error.localizedDescription } } }
}
struct ByokProfileEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var profile: ByokProfile
    @State private var key = ""
    @State private var busy = false
    var body: some View {
        Form { TextField("配置名称", text: $profile.name); Picker("用途", selection: $profile.role) { Text("问答").tag("CHAT"); Text("识图").tag("VISION"); Text("转写").tag("TRANSCRIPTION"); Text("朗读").tag("SPEECH") }; TextField("HTTPS API 根地址", text: $profile.baseUrl).textInputAutocapitalization(.never).autocorrectionDisabled(); TextField("模型 ID", text: $profile.model).textInputAutocapitalization(.never).autocorrectionDisabled(); SecureField("API Key（留空保留原密钥）", text: $key); if profile.role == "SPEECH" { TextField("音色，如 alloy", text: $profile.voice) }; Text("接口为 OpenAI-compatible 的 chat/completions、audio/transcriptions、audio/speech。根地址通常包含 /v1。").font(.caption).foregroundStyle(.secondary) }.navigationTitle("模型配置").toolbar { ToolbarItem(placement: .confirmationAction) { Button("保存") { save() }.disabled(busy) }; ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }.onDisappear { key = "" }
    }
    func save() {
        busy = true
        Task { defer { busy = false }; do {
            try profile.validate(); let secret = key.isEmpty ? try await store.vault.secret(store.byokKeyName(profile.id)) : key
            _ = try BYOKClient(endpoint: URL(string: profile.baseUrl)!, model: profile.model, key: secret)
            var c = store.byok; if let i = c.profiles.firstIndex(where: { $0.id == profile.id }) { c.profiles[i] = profile } else { c.profiles.append(profile) }
            if c.chatId.isEmpty && profile.role == "CHAT" { c.chatId = profile.id }
            try c.validate(); try await store.vault.setSecret(secret, key: store.byokKeyName(profile.id)); try await store.savePrivateByok(c); key = ""; dismiss()
        } catch { store.error = error.localizedDescription } }
    }
}
