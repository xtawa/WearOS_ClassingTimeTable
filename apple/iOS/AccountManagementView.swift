import SwiftUI
import ClassingCore

struct AccountManagementView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        List {
            Section("账号") {
                LabeledContent("登录方式", value: store.account["provider"].string == "APPLE" ? "Apple" : "邮箱与密码")
                NavigationLink("个人资料") { ProfileSettingsView() }
                NavigationLink(store.account["emailBound"] == .bool(false) ? "连接邮箱" : "邮箱") { EmailSettingsView() }
            }
            Section("安全") {
                NavigationLink(store.account["hasPassword"] == .bool(false) ? "设置密码" : "修改密码") { AccountPasswordView() }
                if store.account["provider"].string != "APPLE" { NavigationLink("连接 Apple") { LinkAppleView() } }
                NavigationLink("注销账号") { DeleteAccountView() }
            }
        }.navigationTitle("账号管理").onChange(of: store.signedIn) { _, value in if !value { dismiss() } }
    }
}
struct ProfileSettingsView: View {
    @EnvironmentObject var store: AppStore
    @State private var username = ""
    @State private var busy = false
    @State private var feedback = ""
    var body: some View {
        Form {
            Section("用户名") { TextField("用户名", text: $username).textInputAutocapitalization(.never).autocorrectionDisabled() }
            Section { NavigationLink { EmailSettingsView() } label: { LabeledContent("邮箱", value: store.account["email"].string.isEmpty ? "未连接" : store.account["email"].string) } }
            Button("保存资料") { busy = true; feedback = ""; Task { defer { busy = false }; do {
                _ = try await store.api.request("PATCH", "/api/v1/account/me", body: .object(["username": .string(username.trimmingCharacters(in: .whitespacesAndNewlines))])); try await store.refreshAccount(); feedback = "资料已保存"
            } catch { store.report(error) } } }.disabled(busy || username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            if busy { ProgressView("正在保存…") }; if !feedback.isEmpty { Text(feedback).foregroundStyle(.secondary) }
        }.navigationTitle("个人资料").onAppear { username = store.account["username"].string }
    }
}
struct EmailSettingsView: View {
    @EnvironmentObject var store: AppStore
    @State private var email = ""
    @State private var password = ""
    @State private var requestID = ""
    @State private var code = ""
    @State private var busy = false
    @State private var feedback = ""
    private var hasPassword: Bool { store.account["hasPassword"] != .bool(false) }
    private var needsAppleProof: Bool { !hasPassword && store.account["emailBound"].bool }
    var body: some View {
        Form {
            if !store.account["email"].string.isEmpty { LabeledContent("当前邮箱", value: store.account["email"].string) }
            TextField("邮箱", text: $email).keyboardType(.emailAddress).textContentType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
            if requestID.isEmpty {
                if hasPassword { SecureField("当前密码", text: $password).textContentType(.password) }
                if needsAppleProof { AppleSignInAction(label: .continue) { token, challenge in try await requestCode(token: token, challenge: challenge) } }
                else { Button("发送验证码") { Task { do { try await requestCode() } catch { store.report(error) } } }.disabled(busy || email.isEmpty) }
            } else {
                TextField("邮箱验证码", text: $code).keyboardType(.numberPad).textContentType(.oneTimeCode)
                Button("确认连接邮箱") { busy = true; Task { defer { busy = false }; do {
                    _ = try await store.api.request("POST", "/api/v1/account/email/confirm", body: .object(["requestId": .string(requestID), "verificationCode": .string(code)]))
                    try await store.api.setSession(nil); await store.logout(); store.status = "邮箱已连接，请重新登录。"
                } catch { store.report(error) } } }.disabled(busy || code.isEmpty)
            }
            if busy { ProgressView("正在处理…") }; if !feedback.isEmpty { Text(feedback).font(.footnote).foregroundStyle(.secondary) }
        }.navigationTitle("邮箱").onDisappear { password = "" }
    }
    private func requestCode(token: String = "", challenge: String = "") async throws {
        guard !busy else { return }; busy = true; defer { busy = false }
        var body: [String: JSONValue] = ["email": .string(email.trimmingCharacters(in: .whitespacesAndNewlines))]
        if hasPassword { body["currentPassword"] = .string(password) }
        if !token.isEmpty { body["identityToken"] = .string(token); body["challengeId"] = .string(challenge) }
        let response = try await store.api.request("PATCH", "/api/v1/account/me", body: .object(body))
        requestID = response["emailChange"]["requestId"].string; password = ""
        guard !requestID.isEmpty else { throw ClassingError.invalid("邮箱未发生变化，请填写要连接的新邮箱。") }
        feedback = "验证码已发送，请检查新邮箱。"
    }
}
struct AccountPasswordView: View {
    @EnvironmentObject var store: AppStore
    @State private var current = ""
    @State private var next = ""
    @State private var confirmation = ""
    @State private var busy = false
    private var hasPassword: Bool { store.account["hasPassword"] != .bool(false) }
    var body: some View {
        Form {
            if !hasPassword && store.account["emailBound"] == .bool(false) { NavigationLink("先连接邮箱") { EmailSettingsView() } }
            else {
                if hasPassword { SecureField("当前密码", text: $current).textContentType(.password) }
                SecureField("新密码", text: $next).textContentType(.newPassword)
                SecureField("确认新密码", text: $confirmation).textContentType(.newPassword)
                Text("密码需为 8–128 位。").font(.caption).foregroundStyle(.secondary)
                Button(hasPassword ? "修改密码" : "设置密码") { busy = true; Task { defer { busy = false }; do {
                    var body: [String: JSONValue] = ["newPassword": .string(next)]; if hasPassword { body["currentPassword"] = .string(current) }
                    _ = try await store.api.request("PUT", "/api/v1/account/password", body: .object(body)); try await store.api.setSession(nil); await store.logout(); store.status = "密码已更新，请重新登录。"
                } catch { store.report(error) } } }.disabled(busy || next.utf8.count < 8 || next.utf8.count > 128 || next != confirmation)
                if busy { ProgressView("正在更新密码…") }
            }
        }.navigationTitle(hasPassword ? "修改密码" : "设置密码").onDisappear { current = ""; next = ""; confirmation = "" }
    }
}
struct LinkAppleView: View {
    @EnvironmentObject var store: AppStore
    @State private var password = ""
    @State private var feedback = ""
    var body: some View {
        Form {
            if store.account["hasPassword"] != .bool(false) { SecureField("当前密码", text: $password).textContentType(.password) }
            AppleSignInAction(label: .continue) { token, challenge in
                _ = try await store.api.request("POST", "/api/v1/account/providers/apple", body: .object(["identityToken": .string(token), "challengeId": .string(challenge), "currentPassword": .string(password)]))
                password = ""; try await store.refreshAccount(); feedback = "Apple 已连接"
            }
            if !feedback.isEmpty { Text(feedback) }
        }.navigationTitle("连接 Apple").onDisappear { password = "" }
    }
}
struct DeleteAccountView: View {
    @EnvironmentObject var store: AppStore
    @State private var password = ""
    @State private var confirmation = ""
    @State private var confirming = false
    @State private var busy = false
    var body: some View {
        Form {
            Text("注销将删除账号并撤销会话，无法恢复。").font(.footnote).foregroundStyle(.secondary)
            TextField("输入 DELETE 确认", text: $confirmation).autocorrectionDisabled()
            if store.account["hasPassword"] == .bool(false) {
                if confirmation == "DELETE" { AppleSignInAction(label: .continue) { token, challenge in try await remove(token: token, challenge: challenge) } }
            } else {
                SecureField("当前密码", text: $password).textContentType(.password)
                Button("注销账号", role: .destructive) { confirming = true }.disabled(confirmation != "DELETE" || password.isEmpty || busy)
            }
            if busy { ProgressView("正在注销…") }
        }.navigationTitle("注销账号").onDisappear { password = "" }
        .confirmationDialog("永久注销账号？", isPresented: $confirming) { Button("确认注销", role: .destructive) { Task { do { try await remove() } catch { store.report(error) } } } }
    }
    private func remove(token: String = "", challenge: String = "") async throws {
        guard !busy, confirmation == "DELETE" else { return }; busy = true; defer { busy = false }
        var body: [String: JSONValue] = ["confirm": .string("DELETE")]
        if !token.isEmpty { body["identityToken"] = .string(token); body["challengeId"] = .string(challenge) } else { body["currentPassword"] = .string(password) }
        _ = try await store.api.request("POST", "/api/v1/account/delete", body: .object(body)); try await store.api.setSession(nil); await store.logout(); store.status = "账号已注销。"
    }
}
