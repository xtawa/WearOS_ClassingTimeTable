import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
public struct AuthSession: Codable, Equatable, Sendable {
    public var accessToken: String, refreshToken: String
    public var accessExpiresAt: Int64, refreshExpiresAt: Int64
    public var account: JSONValue
    public init(_ root: JSONValue) throws {
        let v = root["session"] == .null ? root : root["session"]
        accessToken = v["accessToken"].string; refreshToken = v["refreshToken"].string; accessExpiresAt = v["accessExpiresAt"].int; refreshExpiresAt = v["refreshExpiresAt"].int
        account = root["account"] == .null ? v["account"] : root["account"]
        guard !accessToken.isEmpty, !refreshToken.isEmpty else { throw ClassingError.invalid("登录响应没有令牌。") }
    }
}
public protocol TokenVault: Sendable { func read() async throws -> AuthSession?; func write(_ session: AuthSession?) async throws }
public actor MemoryTokenVault: TokenVault {
    private var value: AuthSession?
    public init() {}
    public func read() -> AuthSession? { value }
    public func write(_ session: AuthSession?) { value = session }
}
public struct HTTPResponse: Sendable { public var status: Int, headers: [String: String], data: Data; public init(status: Int, headers: [String: String] = [:], data: Data) { self.status = status; self.headers = headers; self.data = data } }
public protocol HTTPTransport: Sendable {
    func send(_ request: URLRequest, limit: Int) async throws -> HTTPResponse
    func stream(_ request: URLRequest, onEvent: @escaping @Sendable (SSEEvent) async throws -> Void) async throws
}
public final class URLTransport: NSObject, HTTPTransport, URLSessionTaskDelegate, @unchecked Sendable {
    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral; config.timeoutIntervalForRequest = 60; config.timeoutIntervalForResource = 300
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()
    public override init() { super.init() }
    public func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
    public func send(_ request: URLRequest, limit: Int = 10_485_760) async throws -> HTTPResponse {
        #if os(iOS) || os(watchOS) || os(macOS)
        let (bytes, raw) = try await session.bytes(for: request); var data = Data()
        for try await byte in bytes { guard data.count < limit else { throw ClassingError.invalid("响应超过大小限制。") }; data.append(byte) }
        #else
        let (data, raw) = try await session.data(for: request); guard data.count <= limit else { throw ClassingError.invalid("响应超过大小限制。") }
        #endif
        guard let response = raw as? HTTPURLResponse else { throw ClassingError.invalid("无效的 HTTP 响应。") }
        var headers: [String: String] = [:]; for (key, value) in response.allHeaderFields { headers[String(describing: key).lowercased()] = String(describing: value) }
        return HTTPResponse(status: response.statusCode, headers: headers, data: data)
    }
    public func stream(_ request: URLRequest, onEvent: @escaping @Sendable (SSEEvent) async throws -> Void) async throws {
        #if os(iOS) || os(watchOS) || os(macOS)
        let (bytes, raw) = try await session.bytes(for: request)
        guard let response = raw as? HTTPURLResponse else { throw ClassingError.invalid("无效的 HTTP 响应。") }
        guard (200...299).contains(response.statusCode) else {
            var body = Data(); for try await byte in bytes { if body.count >= 65536 { break }; body.append(byte) }; throw Self.error(response.statusCode, body)
        }
        guard response.value(forHTTPHeaderField: "Content-Type")?.contains("text/event-stream") == true else { throw ClassingError.invalid("服务器未返回事件流。") }
        var parser = SSEParser(), total = 0
        for try await line in bytes.lines { try Task.checkCancellation(); total += line.utf8.count; guard total <= 8_388_608 else { throw ClassingError.invalid("事件流过大。") }; if let event = parser.line(line) { try await onEvent(event) } }
        if let event = parser.finish() { try await onEvent(event) }
        #else
        let response = try await send(request, limit: 8_388_608); guard (200...299).contains(response.status) else { throw Self.error(response.status, response.data) }
        var parser = SSEParser(); for line in String(decoding: response.data, as: UTF8.self).replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n") { if let e = parser.line(line) { try await onEvent(e) } }; if let e = parser.finish() { try await onEvent(e) }
        #endif
    }
    public static func error(_ status: Int, _ data: Data) -> ClassingError { let v = try? JSONValue.decode(data); return .network(status, v?["code"].string ?? "HTTP_ERROR", v?["message"].string.isEmpty == false ? v!["message"].string : "请求失败") }
}
private actor EventResult { var done: JSONValue?; func set(_ value: JSONValue) { done = value }; func get() -> JSONValue? { done } }
public actor ClassingAPI {
    public let baseURL: URL
    public let transport: any HTTPTransport
    private let vault: any TokenVault, platform: String, bundleID: String
    private var refreshTask: Task<AuthSession, Error>?, generation = 0
    public init(baseURL: URL, vault: any TokenVault, platform: String, bundleID: String, transport: any HTTPTransport = URLTransport()) {
        self.baseURL = baseURL; self.vault = vault; self.platform = platform; self.bundleID = bundleID; self.transport = transport
    }
    public func session() async throws -> AuthSession? { try await vault.read() }
    public func setSession(_ value: AuthSession?) async throws { generation += 1; refreshTask?.cancel(); refreshTask = nil; try await vault.write(value) }
    public static func consent(platform: String) -> JSONValue { .object(["privacyPolicy": .bool(true), "termsOfService": .bool(true), "crossBorderTransfer": .bool(true), "acceptedAt": .int(Wire.milliseconds()), "client": .string(platform.lowercased())]) }
    private func make(_ method: String, _ path: String, body: Data?, token: String?, headers: [String: String]) throws -> URLRequest {
        guard path.hasPrefix("/api/v1/"), let url = URL(string: path, relativeTo: baseURL)?.absoluteURL, url.host == baseURL.host else { throw ClassingError.invalid("API 路径无效。") }
        var request = URLRequest(url: url); request.httpMethod = method; request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept"); if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        request.setValue("Classing-Apple/1.0 \(platform)", forHTTPHeaderField: "User-Agent")
        request.setValue(platform, forHTTPHeaderField: "X-Classing-Client-Platform"); request.setValue(bundleID, forHTTPHeaderField: "X-Classing-Apple-Bundle-ID")
        request.setValue("CN", forHTTPHeaderField: "X-Classing-Account-Market"); if let token = token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }; return request
    }
    public func response(_ method: String, _ path: String, body: Data? = nil, authorized: Bool = true, headers: [String: String] = [:]) async throws -> HTTPResponse {
        let epoch = generation
        let session = authorized ? try await validSession() : nil
        guard epoch == generation else { throw CancellationError() }
        var response = try await transport.send(make(method, path, body: body, token: session?.accessToken, headers: headers), limit: 20_971_520)
        guard epoch == generation else { throw CancellationError() }
        if response.status == 401 && authorized {
            let latest = try await vault.read()
            let token: String
            if let latest = latest, latest.accessToken != session?.accessToken { token = latest.accessToken } else { token = try await refreshed().accessToken }
             response = try await transport.send(make(method, path, body: body, token: token, headers: headers), limit: 20_971_520) }
        guard epoch == generation else { throw CancellationError() }
        guard (200...299).contains(response.status) || response.status == 304 else {
            if response.status == 401 { try await setSession(nil) }; if response.status == 409 || response.status == 412 { throw ClassingError.conflict }; throw URLTransport.error(response.status, response.data)
        }; return response
    }
    public func request(_ method: String, _ path: String, body: JSONValue? = nil, authorized: Bool = true) async throws -> JSONValue {
        let response = try await response(method, path, body: try body?.data(), authorized: authorized); return response.data.isEmpty ? .object([:]) : try JSONValue.decode(response.data)
    }
    private func validSession() async throws -> AuthSession { guard let session = try await vault.read() else { throw ClassingError.missingSession }; if session.accessExpiresAt > 0 && session.accessExpiresAt <= Wire.milliseconds() + 30000 { return try await refreshed() }; return session }
    private func refreshed() async throws -> AuthSession {
        if let task = refreshTask { return try await task.value }; let epoch = generation
        guard let old = try await vault.read() else { throw ClassingError.missingSession }
        guard epoch == generation else { throw CancellationError() }
        if let running = refreshTask { return try await running.value }
        let request = try make("POST", "/api/v1/auth/refresh", body: JSONValue.object(["refreshToken": .string(old.refreshToken)]).data(), token: nil, headers: [:]), transport = self.transport
        let task = Task<AuthSession, Error> { let response = try await transport.send(request, limit: 65536); guard (200...299).contains(response.status) else { throw URLTransport.error(response.status, response.data) }; return try AuthSession(JSONValue.decode(response.data)) }
        refreshTask = task
        do { let result = try await task.value; guard epoch == generation else { throw CancellationError() }; try await vault.write(result); refreshTask = nil; return result }
        catch { if epoch == generation { refreshTask = nil; if case ClassingError.network(401, _, _) = error { try await setSession(nil) } }; throw error }
    }
    public func login(identifier: String, password: String) async throws -> AuthSession {
        let v = try await request("POST", "/api/v1/auth/login", body: .object(["identifier": .string(identifier.trimmingCharacters(in: .whitespaces)), "password": .string(password), "consent": Self.consent(platform: platform)]), authorized: false)
        let s = try AuthSession(v); try await setSession(s); return s
    }
    public func logout() async throws { let s = try await vault.read(); do { if let s = s { _ = try await request("POST", "/api/v1/auth/logout", body: .object(["refreshToken": .string(s.refreshToken)])) } } catch { try await setSession(nil); throw error }; try await setSession(nil) }
    public func events(_ path: String, body: JSONValue?, headers: [String: String] = [:], onEvent: @escaping @Sendable (SSEEvent) async throws -> Void) async throws {
        let session = try await validSession(); let request = try make(body == nil ? "GET" : "POST", path, body: try body?.data(), token: session.accessToken, headers: headers)
        do { try await transport.stream(request, onEvent: onEvent) }
        catch ClassingError.network(401, _, _) { let fresh = try await refreshed(); try await transport.stream(make(body == nil ? "GET" : "POST", path, body: try body?.data(), token: fresh.accessToken, headers: headers), onEvent: onEvent) }
    }
    public func chat(message: String, conversationId: String?, snapshot: JSONValue, model: String, attachments: [String], onEvent: @escaping @Sendable (SSEEvent) async throws -> Void) async throws -> JSONValue {
        var body: [String: JSONValue] = ["clientRequestId": .string(UUID().uuidString), "message": .string(message), "model": .string(model), "attachmentIds": .array(attachments.map(JSONValue.string)), "supportsTimetableActions": .bool(true), "supportsExamActions": .bool(true), "currentTimetableSnapshot": snapshot]
        if let id = conversationId { body["conversationId"] = .string(id) } else { body["timetableSnapshot"] = snapshot }
        let result = EventResult(); try await events("/api/v1/ai/chat", body: .object(body)) { event in
            let value = try JSONValue.decode(Data(event.data.utf8)); if event.event == "error" { throw ClassingError.network(502, value["code"].string, value["message"].string) }; if event.event == "done" { await result.set(value) }; try await onEvent(event)
        }; guard let done = await result.get() else { throw ClassingError.invalid("回答中断，草稿已保留。") }; return done
    }
    public func upload(_ data: Data, filename: String, mime: String, transcription: Bool = false) async throws -> JSONValue {
        guard data.count <= 20_971_520, !filename.contains("\r"), !filename.contains("\n"), !mime.contains("\r"), !mime.contains("\n") else { throw ClassingError.invalid("附件过大或文件名无效。") }
        let boundary = "Classing-\(UUID().uuidString)"; var body = Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\(filename.replacingOccurrences(of: "\"", with: "_"))\"\r\nContent-Type: \(mime)\r\n\r\n".utf8); body.append(data); body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        let response = try await response("POST", transcription ? "/api/v1/ai/transcriptions" : "/api/v1/ai/attachments", body: body, headers: ["Content-Type": "multipart/form-data; boundary=\(boundary)"]); return try JSONValue.decode(response.data)
    }
}
