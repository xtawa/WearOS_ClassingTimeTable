import SwiftUI
import ClassingCore
import WebKit
struct AccountView: View {
    @EnvironmentObject var store: AppStore
    @State private var identifier = ""
    @State private var password = ""
    @State private var username = ""
    @State private var email = ""
    @State private var code = ""
    @State private var challenge = ""
    @State private var consent = false
    @State private var register = false
    @State private var security: JSONValue = .null
    @State private var turnstile = ""
    @State private var verify = false
    @State private var busy = false
    var body: some View {
        Form {
            if store.signedIn {
                Section("账号") { Text(store.account["username"].string); if !store.account["email"].string.isEmpty { Text(store.account["email"].string) } else { NavigationLink("连接邮箱") { EmailSettingsView() } }; Text(store.membership["accountType"].string == "PRO" ? "Classing Pro" : store.membership["accountType"].string == "LEGACY" ? "早期用户" : "免费账号"); if store.membership["expiresAt"].int > 0 { Text("到期：\(Date(timeIntervalSince1970: Double(store.membership["expiresAt"].int) / 1000).formatted())") }; NavigationLink("账号管理") { AccountManagementView() }; NavigationLink("Classing Pro") { SubscriptionView() }; Button("退出登录", role: .destructive) { Task { await store.logout() } } }
            } else {
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
                Section {
                    AppleSignInAction(consent: consent) { token, challenge in
                        busy = true; defer { busy = false }
                        let response = try await store.api.request("POST", "/api/v1/auth/apple", body: .object(["identityToken": .string(token), "challengeId": .string(challenge), "consent": ClassingAPI.consent(platform: "IOS")]), authorized: false)
                        try await store.signedInAfterRegistration(response); store.status = "Apple 登录成功"
                    }
                }
            }
            if busy { ProgressView("正在处理…") }
            if !store.status.isEmpty { Text(store.status).font(.footnote).foregroundStyle(.secondary) }
        }.navigationTitle(store.signedIn ? "账号" : "登录")
        .task { if !store.signedIn && !store.demo { security = (try? await store.api.request("GET", "/api/v1/auth/registration/config", authorized: false)) ?? .null } }
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
        } catch { store.report(error) } }
    }
}
struct PasswordResetView: View {
    @EnvironmentObject var store: AppStore
    @State private var email = ""
    @State private var token = ""
    @State private var password = ""
    @State private var sent = false
    var body: some View { Form { TextField("邮箱", text: $email).textInputAutocapitalization(.never).keyboardType(.emailAddress); Button("发送重置邮件") { Task { do { _ = try await store.api.request("POST", "/api/v1/auth/password/reset/request", body: .object(["email": .string(email)]), authorized: false); sent = true } catch { store.report(error) } } }; if sent { Text("若邮箱存在，你会收到重置邮件。"); TextField("邮件中的重置令牌", text: $token).autocorrectionDisabled(); SecureField("新密码", text: $password); Button("重置密码") { Task { do { _ = try await store.api.request("POST", "/api/v1/auth/password/reset/confirm", body: .object(["token": .string(token), "newPassword": .string(password)]), authorized: false); password = ""; await store.logout(); store.status = "密码已重置，请重新登录" } catch { store.report(error) } } } } }.navigationTitle("重置密码").onDisappear { password = ""; token = "" } }
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
