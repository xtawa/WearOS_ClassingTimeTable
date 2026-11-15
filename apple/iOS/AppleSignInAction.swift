import SwiftUI
import AuthenticationServices
import CryptoKit
import ClassingCore

struct AppleSignInAction: View {
    @EnvironmentObject var store: AppStore
    var consent = true
    var label: SignInWithAppleButton.Label = .signIn
    var completion: (String, String) async throws -> Void
    @State private var challenge: JSONValue = .null
    @State private var loading = false
    @State private var submitting = false
    @State private var feedback = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SignInWithAppleButton(label) { request in
                request.requestedScopes = [.email, .fullName]
                request.nonce = SHA256.hash(data: Data(challenge["nonce"].string.utf8)).map { String(format: "%02x", $0) }.joined()
                request.state = challenge["challengeId"].string; submitting = true
            } onCompletion: { result in
                Task {
                    defer { submitting = false }
                    do {
                        let authorization = try result.get()
                        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                              credential.state == challenge["challengeId"].string,
                              let data = credential.identityToken, let token = String(data: data, encoding: .utf8) else { throw ClassingError.invalid("未能取得 Apple 登录凭证，请重试。") }
                        feedback = "正在验证 Apple 登录…"; try await completion(token, challenge["challengeId"].string); feedback = ""
                    } catch let error as ASAuthorizationError where error.code == .canceled { feedback = "" }
                    catch { feedback = ClassingError.userMessage(error); store.report(error) }
                    await prepare()
                }
            }.signInWithAppleButtonStyle(.black).frame(height: 48).accessibilityIdentifier("apple-sign-in")
                .disabled(!consent || loading || submitting || challenge["challengeId"].string.isEmpty || store.demo)
            if loading || submitting { ProgressView(loading ? "正在准备 Apple 登录…" : "正在登录…") }
            if !feedback.isEmpty { Text(feedback).font(.caption).foregroundStyle(.secondary); Button("重试 Apple 登录") { Task { await prepare() } }.disabled(loading || submitting) }
        }.task { await prepare() }
    }
    private func prepare() async {
        guard !store.demo else { return }; loading = true; defer { loading = false }
        do { challenge = try await store.api.request("POST", "/api/v1/auth/apple/nonce", body: .object([:]), authorized: false)["challenge"]; feedback = "" }
        catch { challenge = .null; if !Task.isCancelled { feedback = ClassingError.userMessage(error) } }
    }
}
