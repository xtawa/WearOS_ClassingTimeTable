import AuthenticationServices
import CryptoKit
import ClassingCore
import UIKit
@MainActor final class DriveAuthorization: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = DriveAuthorization()
    private var session: ASWebAuthenticationSession?
    private var clientID: String { Bundle.main.object(forInfoDictionaryKey: "GoogleDriveClientID") as? String ?? "" }
    private var redirect: String { Bundle.main.object(forInfoDictionaryKey: "GoogleDriveRedirectURI") as? String ?? "" }
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow) ?? ASPresentationAnchor() }
    private func random() -> String { var bytes = [UInt8](repeating: 0, count: 32); _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes); return Data(bytes).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
    func authorize(vault: KeychainVault) async throws {
        guard !clientID.isEmpty, !clientID.hasPrefix("$("), let scheme = URL(string: redirect)?.scheme, !scheme.isEmpty else { throw ClassingError.invalid("需在 Apple 配置中填写自己的 Google iOS OAuth Client ID 与回调 URI。") }
        let verifier = random(), state = random(), challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        var c = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        c.queryItems = [.init(name: "client_id", value: clientID), .init(name: "redirect_uri", value: redirect), .init(name: "response_type", value: "code"), .init(name: "scope", value: "https://www.googleapis.com/auth/drive.appdata"), .init(name: "code_challenge", value: challenge), .init(name: "code_challenge_method", value: "S256"), .init(name: "state", value: state), .init(name: "access_type", value: "offline"), .init(name: "prompt", value: "consent")]
        let callback: URL = try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(url: c.url!, callbackURLScheme: scheme) { url, error in if let url = url { continuation.resume(returning: url) } else { continuation.resume(throwing: error ?? CancellationError()) } }; session.presentationContextProvider = self; session.prefersEphemeralWebBrowserSession = true; self.session = session
            if !session.start() { continuation.resume(throwing: ClassingError.invalid("OAuth 页面启动失败。")) }
        }
        session = nil; let query = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
        guard query.first(where: { $0.name == "state" })?.value == state, let code = query.first(where: { $0.name == "code" })?.value else { throw ClassingError.invalid("OAuth 状态校验失败。") }
        let response = try await exchange(["client_id": clientID, "redirect_uri": redirect, "grant_type": "authorization_code", "code": code, "code_verifier": verifier]); try await save(response, vault: vault)
    }
    func accessToken(vault: KeychainVault) async throws -> String {
        let expiry = Int64(try await vault.secret("drive.expires")) ?? 0, access = try await vault.secret("drive.access")
        if expiry > Wire.milliseconds() + 60000, !access.isEmpty { return access }
        let refresh = try await vault.secret("drive.refresh"); guard !refresh.isEmpty else { throw ClassingError.invalid("请先授权 Google Drive。") }
        let response = try await exchange(["client_id": clientID, "grant_type": "refresh_token", "refresh_token": refresh]); try await save(response, vault: vault); return response["access_token"].string
    }
    private func exchange(_ fields: [String: String]) async throws -> JSONValue {
        var encoded = URLComponents(); encoded.queryItems = fields.map { URLQueryItem(name: $0.key, value: $0.value) }
        var request = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!); request.httpMethod = "POST"; request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type"); request.httpBody = Data((encoded.percentEncodedQuery ?? "").replacingOccurrences(of: "+", with: "%2B").utf8)
        let r = try await URLTransport().send(request, limit: 65536); guard r.status == 200 else { throw URLTransport.error(r.status, r.data) }; return try JSONValue.decode(r.data)
    }
    private func save(_ v: JSONValue, vault: KeychainVault) async throws { guard !v["access_token"].string.isEmpty else { throw ClassingError.invalid("OAuth 没有返回访问令牌。") }; try await vault.setSecret(v["access_token"].string, key: "drive.access"); if !v["refresh_token"].string.isEmpty { try await vault.setSecret(v["refresh_token"].string, key: "drive.refresh") }; try await vault.setSecret(String(Wire.milliseconds() + v["expires_in"].int * 1000), key: "drive.expires") }
}
