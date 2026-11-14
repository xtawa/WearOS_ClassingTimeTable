import SwiftUI
import ClassingCore
import WebKit
struct AccountView: View {
    @EnvironmentObject var store: AppStore
    @State private var identifier = "", password = "", username = "", email = "", code = "", challenge = "", consent = false, register = false, security: JSONValue = .null, turnstile = "", verify = false, busy = false
    var body: some View {
        Form {
            if store.signedIn {
                Section("账号") { Text(store.account["username"].string); Text(store.account["email"].string); Text("账号类型：\(store.membership["accountType"].string)"); if store.membership["expiresAt"].int > 0 { Text("到期：\(Date(timeIntervalSince1970: Double(store.membership["expiresAt"].int) / 1000).formatted())") }; NavigationLink("修改资料、密码或注销") { AccountManagementView() }; NavigationLink("兑换会员") { RedeemView() }; Button("退出登录", role: .destructive) { Task { await store.logout() } } }
            } else {
                Picker("账号域", selection: .constant("CN")) { Text("国内版").tag("CN"); Text("国际版尚未开放").tag("GLOBAL") }.disabled(true)
                Toggle("注册新账号", isOn: $register)
                if register { TextField("用户名", text: $username).textInputAutocapitalization(.never); TextField("邮箱", text: $email).keyboardType(.emailAddress).textInputAutocapitalization(.never) }
                else { TextField("邮箱或用户名", text: $identifier).textContentType(.username).textInputAutocapitalization(.never) }
                SecureField("密码", text: $password).textContentType(register ? .newPassword : .password)
                Toggle("同意隐私政策、服务条款与跨境数据协议", isOn: $consent)
                ForEach(["privacyPolicy", "termsOfService", "crossBorderTransfer"], id: \.self) { key in if let url = URL(string: security["legalAgreementUrls"][key].string.isEmpty ? "https://lyxyy.notion.site/classing-user-policy" : security["legalAgreementUrls"][key].string) { Link(key == "privacyPolicy" ? "隐私政策" : key == "termsOfService" ? "服务条款" : "跨境数据协议", destination: url) } }
                if register && security["turnstileRequired"].bool { Button(turnstile.isEmpty ? "完成安全验证" : "安全验证已完成") { verify = true } }
                if !challenge.isEmpty { TextField("邮箱验证码", text: $code).keyboardType(.numberPad) }
                Button(challenge.isEmpty ? (register ? "发送验证邮件" : "登录") : "确认注册") { submit() }.disabled(!consent || busy)
                NavigationLink("忘记密码") { PasswordResetView() }
            }
            if busy { ProgressView() }
        }.navigationTitle(store.signedIn ? "账号" : "登录")
        .task { if !store.signedIn { security = (try? await store.api.request("GET", "/api/v1/auth/registration/config", authorized: false)) ?? .null } }
        .sheet(isPresented: $verify) { TurnstileView(siteKey: security["turnstileSiteKey"].string, origin: store.api.baseURL) { token in turnstile = token; verify = false } }
        .onDisappear { password = ""; turnstile = "" }
    }
    func submit() {
        busy = true
        Task { defer { busy = false }; do {
            if !register { try await store.login(identifier, password); password = "" }
            else if challenge.isEmpty {
                if security["turnstileRequired"].bool && turnstile.isEmpty { throw ClassingError.invalid("请先完成安全验证。") }
                let response = try await store.api.request("POST", "/api/v1/auth/register/email/request", body: .object(["username": .string(username), "email": .string(email), "password": .string(password), "turnstileToken": .string(turnstile), "consent": ClassingAPI.consent(platform: "IOS")]), authorized: false); challenge = response["challenge"]["challengeId"].string; password = ""; turnstile = ""
            } else {
                let response = try await store.api.request("POST", "/api/v1/auth/register/email/confirm", body: .object(["challengeId": .string(challenge), "verificationCode": .string(code), "consent": ClassingAPI.consent(platform: "IOS")]), authorized: false); try await store.signedInAfterRegistration(response)
            }
        } catch { store.error = error.localizedDescription } }
    }
}
struct PasswordResetView: View {
    @EnvironmentObject var store: AppStore
    @State private var email = "", token = "", password = "", sent = false
    var body: some View { Form { TextField("邮箱", text: $email).textInputAutocapitalization(.never).keyboardType(.emailAddress); Button("发送重置邮件") { Task { do { _ = try await store.api.request("POST", "/api/v1/auth/password/reset/request", body: .object(["email": .string(email)]), authorized: false); sent = true } catch { store.error = error.localizedDescription } } }; if sent { Text("若邮箱存在，你会收到重置邮件。"); TextField("邮件中的重置令牌", text: $token).autocorrectionDisabled(); SecureField("新密码", text: $password); Button("重置密码") { Task { do { _ = try await store.api.request("POST", "/api/v1/auth/password/reset/confirm", body: .object(["token": .string(token), "newPassword": .string(password)]), authorized: false); password = ""; await store.logout(); store.status = "密码已重置，请重新登录" } catch { store.error = error.localizedDescription } } } } }.navigationTitle("重置密码").onDisappear { password = ""; token = "" } }
}
struct AccountManagementView: View {
    @EnvironmentObject var store: AppStore
    @State private var username = "", email = "", password = "", newPassword = "", emailRequest = "", code = "", confirmText = "", deleting = false
    var body: some View {
        Form {
            Section("资料") { TextField("用户名", text: $username); TextField("邮箱", text: $email).textInputAutocapitalization(.never); SecureField("当前密码", text: $password); Button("保存资料") { run { let response = try await store.api.request("PATCH", "/api/v1/account/me", body: .object(["username": .string(username), "email": .string(email), "currentPassword": .string(password)])); emailRequest = response["emailChange"]["requestId"].string; try await store.refreshAccount() } }; if !emailRequest.isEmpty { TextField("新邮箱验证码", text: $code); Button("确认更换邮箱") { run { _ = try await store.api.request("POST", "/api/v1/account/email/confirm", body: .object(["requestId": .string(emailRequest), "verificationCode": .string(code)])); await store.logout() } } } }
            Section("密码") { SecureField("新密码", text: $newPassword); Button("更改密码并撤销会话") { run { _ = try await store.api.request("PUT", "/api/v1/account/password", body: .object(["currentPassword": .string(password), "newPassword": .string(newPassword)])); await store.logout() } } }
            Section("注销账号") { Text("注销将由服务器清理账号与撤销所有会话，无法从本机恢复。").font(.caption); TextField("输入 DELETE", text: $confirmText); Button("注销账号", role: .destructive) { deleting = true }.disabled(confirmText != "DELETE") }
        }.navigationTitle("账号管理").onAppear { username = store.account["username"].string; email = store.account["email"].string }.onDisappear { password = ""; newPassword = "" }
        .confirmationDialog("永久注销当前账号？", isPresented: $deleting) { Button("确认注销", role: .destructive) { run { _ = try await store.api.request("POST", "/api/v1/account/delete", body: .object(["currentPassword": .string(password), "confirm": .string("DELETE")])); try await store.api.setSession(nil); await store.logout() } } }
    }
    func run(_ action: @escaping @MainActor () async throws -> Void) { Task { do { try await action(); password = ""; newPassword = ""; store.status = "操作完成" } catch { store.error = error.localizedDescription } } }
}
struct RedeemView: View {
    @EnvironmentObject var store: AppStore
    @State private var code = ""
    var body: some View { Form { TextField("兑换码", text: $code).textInputAutocapitalization(.never); Button("兑换") { Task { do { _ = try await store.api.request("POST", "/api/v1/membership/redeem", body: .object(["code": .string(code)])); code = ""; try await store.refreshAccount(); store.status = "兑换成功" } catch { store.error = error.localizedDescription } } } }.navigationTitle("兑换") }
}
struct TurnstileView: UIViewRepresentable {
    var siteKey: String, origin: URL, completion: (String) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(completion) }
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration(); config.userContentController.add(context.coordinator, name: "token")
        let view = WKWebView(frame: .zero, configuration: config)
        let key = (try? JSONValue.string(siteKey).text()) ?? "\"\""
        view.loadHTMLString("<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><script src='https://challenges.cloudflare.com/turnstile/v0/api.js?onload=ready' async defer></script></head><body><div id='challenge'></div><script>function ready(){turnstile.render('#challenge',{sitekey:\(key),callback:function(t){window.webkit.messageHandlers.token.postMessage(t)}})}</script></body></html>", baseURL: origin); return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {}
    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) { view.configuration.userContentController.removeScriptMessageHandler(forName: "token"); view.stopLoading() }
    final class Coordinator: NSObject, WKScriptMessageHandler { let completion: (String) -> Void; init(_ completion: @escaping (String) -> Void) { self.completion = completion }; func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) { if let token = message.body as? String { completion(token) } } }
}
