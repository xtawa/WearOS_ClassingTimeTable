import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
public protocol CloudProvider: Sendable {
    func read() async throws -> (CloudDocument, String?)
    func write(_ document: CloudDocument, version: String?) async throws
}
public struct OfficialCloud: CloudProvider {
    public var api: ClassingAPI
    public init(api: ClassingAPI) { self.api = api }
    public func read() async throws -> (CloudDocument, String?) {
        let r = try await api.response("GET", "/api/v1/cloud/official/document")
        let d = r.data.isEmpty ? CloudDocument() : try Wire.decoder.decode(CloudDocument.self, from: r.data)
        try d.validate()
        var etag = r.headers["etag"] ?? "\"0\""
        if etag.hasPrefix("W/") { etag = String(etag.dropFirst(2)).trimmingCharacters(in: .whitespaces) }
        return (d, etag)
    }
    public func write(_ document: CloudDocument, version: String?) async throws {
        var v = version ?? "\"0\""
        if v.hasPrefix("W/") { v = String(v.dropFirst(2)).trimmingCharacters(in: .whitespaces) }
        _ = try await api.response("PUT", "/api/v1/cloud/official/document", body: Wire.encoder.encode(document), headers: ["If-Match": v, "Idempotency-Key": UUID().uuidString])
    }
}
public struct WebDAVCloud: CloudProvider {
    public var url: URL, username: String, password: String
    public var transport: any HTTPTransport
    public init(url: URL, username: String, password: String, transport: any HTTPTransport = URLTransport()) throws {
        guard url.scheme == "https", url.user == nil, url.password == nil, url.fragment == nil else { throw ClassingError.invalid("WebDAV 必须使用 HTTPS，凭据不要写在 URL 中。") }; self.url = url; self.username = username; self.password = password; self.transport = transport
    }
    private func request(_ method: String) -> URLRequest { var r = URLRequest(url: url); r.httpMethod = method; r.setValue("Basic " + Data("\(username):\(password)".utf8).base64EncodedString(), forHTTPHeaderField: "Authorization"); return r }
    public func read() async throws -> (CloudDocument, String?) {
        let r = try await transport.send(request("GET"), limit: 2_097_152); if r.status == 404 { return (CloudDocument(), nil) }; guard r.status == 200 else { throw URLTransport.error(r.status, r.data) }
        // A server without ETag cannot offer conflict-safe synchronization.
        guard let etag = r.headers["etag"], !etag.hasPrefix("W/") else { throw ClassingError.invalid("WebDAV 服务必须提供强 ETag，避免覆盖其他设备的修改。") }
        let d = try Wire.decoder.decode(CloudDocument.self, from: r.data); try d.validate(); return (d, etag)
    }
    public func write(_ document: CloudDocument, version: String?) async throws {
        var r = request("PUT"); r.httpBody = try Wire.encoder.encode(document); r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let v = version { r.setValue(v, forHTTPHeaderField: "If-Match") } else { r.setValue("*", forHTTPHeaderField: "If-None-Match") }
        let result = try await transport.send(r, limit: 65536); if result.status == 412 || result.status == 409 { throw ClassingError.conflict }; guard (200...299).contains(result.status) else { throw URLTransport.error(result.status, result.data) }
    }
}
public struct DriveCloud: CloudProvider {
    public var token: String, filename: String
    public var transport: any HTTPTransport
    public init(token: String, filename: String = "classing_sync.json", transport: any HTTPTransport = URLTransport()) { self.token = token; self.filename = filename; self.transport = transport }
    private func request(_ method: String, _ url: String) throws -> URLRequest { guard let u = URL(string: url), u.host == "www.googleapis.com", u.scheme == "https" else { throw ClassingError.invalid("Drive URL 无效。") }; var r = URLRequest(url: u); r.httpMethod = method; r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization"); return r }
    public func read() async throws -> (CloudDocument, String?) {
        var components = URLComponents(string: "https://www.googleapis.com/drive/v3/files")!
        components.queryItems = [.init(name: "spaces", value: "appDataFolder"), .init(name: "q", value: "name='\(filename.replacingOccurrences(of: "'", with: "\\'"))' and trashed=false"), .init(name: "fields", value: "files(id,name,version)")]
        let result = try await transport.send(request("GET", components.url!.absoluteString), limit: 1_048_576); guard result.status == 200 else { throw URLTransport.error(result.status, result.data) }
        let files = try JSONValue.decode(result.data)["files"].array.sorted { $0["id"].string < $1["id"].string }
        guard let file = files.first else { return (CloudDocument(), nil) }; let id = file["id"].string
        let content = try await transport.send(request("GET", "https://www.googleapis.com/drive/v3/files/\(id)?alt=media"), limit: 2_097_152); guard content.status == 200 else { throw URLTransport.error(content.status, content.data) }
        guard let etag = content.headers["etag"] else { throw ClassingError.invalid("Drive 未提供条件写入版本，无法安全同步。") }; let d = try Wire.decoder.decode(CloudDocument.self, from: content.data); try d.validate(); return (d, id + "|" + etag)
    }
    public func write(_ document: CloudDocument, version: String?) async throws {
        if let version = version {
            let p = version.split(separator: "|", maxSplits: 1); guard p.count == 2 else { throw ClassingError.invalid("Drive 版本无效。") }
            var r = try request("PATCH", "https://www.googleapis.com/upload/drive/v3/files/\(p[0])?uploadType=media"); r.httpBody = try Wire.encoder.encode(document); r.setValue("application/json", forHTTPHeaderField: "Content-Type"); r.setValue(String(p[1]), forHTTPHeaderField: "If-Match")
            let result = try await transport.send(r, limit: 65536); if result.status == 412 { throw ClassingError.conflict }; guard (200...299).contains(result.status) else { throw URLTransport.error(result.status, result.data) }
        } else {
            // Drive file creation has no name-based CAS. Recheck before creating; subsequent merges remain deterministic.
            let (_, existing) = try await read(); guard existing == nil else { throw ClassingError.conflict }
            let boundary = UUID().uuidString; var r = try request("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            let metadata = try JSONValue.object(["name": .string(filename), "parents": .array([.string("appDataFolder")])]).text(), body = String(decoding: try Wire.encoder.encode(document), as: UTF8.self)
            r.httpBody = Data("--\(boundary)\r\nContent-Type: application/json\r\n\r\n\(metadata)\r\n--\(boundary)\r\nContent-Type: application/json\r\n\r\n\(body)\r\n--\(boundary)--\r\n".utf8); r.setValue("multipart/related; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
            let result = try await transport.send(r, limit: 65536); guard (200...299).contains(result.status) else { throw URLTransport.error(result.status, result.data) }
        }
    }
}
public struct CanvasClient: Sendable {
    public var transport: any HTTPTransport
    public init(transport: any HTTPTransport = URLTransport()) { self.transport = transport }
    public func load(origin: URL, token: String, from: String, through: String, zone: String) async throws -> ImportDraft {
        guard origin.scheme == "https", origin.user == nil, origin.password == nil, origin.path.isEmpty || origin.path == "/", origin.query == nil, origin.fragment == nil, !token.isEmpty else { throw ClassingError.invalid("请输入学校 Canvas HTTPS 根地址。") }
        let days = try SchoolTime.days(SchoolTime.day(from), SchoolTime.day(through)); guard (0...366).contains(days) else { throw ClassingError.invalid("Canvas 日期范围不得超过一年。") }
        func pages(_ initial: URL) async throws -> [JSONValue] {
            var next: URL? = initial, visited: Set<URL> = [], rows: [JSONValue] = []
            while let url = next {
                guard url.scheme == origin.scheme, url.host == origin.host, url.port == origin.port, url.user == nil, url.password == nil, url.path.hasPrefix("/api/v1/"), visited.count < 50, visited.insert(url).inserted else { throw ClassingError.invalid("Canvas 分页地址不安全或形成循环。") }
                var request = URLRequest(url: url); request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization"); let r = try await transport.send(request, limit: 1_048_576); guard r.status == 200 else { throw URLTransport.error(r.status, r.data) }; rows += try JSONValue.decode(r.data).array; guard rows.count <= 5000 else { throw ClassingError.invalid("Canvas 数据超过 5000 条。") }
                next = nil
                if let link = r.headers["link"]?.components(separatedBy: ",").first(where: { $0.contains("rel=\"next\"") || $0.contains("rel=next") }), let a = link.firstIndex(of: "<"), let b = link.firstIndex(of: ">") { next = URL(string: String(link[link.index(after: a)..<b]), relativeTo: url)?.absoluteURL }
            }; return rows
        }
        func endpoint(_ path: String, _ items: [URLQueryItem]) -> URL { var c = URLComponents(url: origin.appendingPathComponent(path), resolvingAgainstBaseURL: false)!; c.queryItems = items; return c.url! }
        let courses = try await pages(endpoint("api/v1/courses", [.init(name: "enrollment_state", value: "active"), .init(name: "per_page", value: "100")]))
        var draft = ImportDraft(); let iso = ISO8601DateFormatter()
        for offset in stride(from: 0, to: courses.count, by: 10) {
            let chunk = courses[offset..<min(offset + 10, courses.count)]
            var items: [URLQueryItem] = [.init(name: "type", value: "event"), .init(name: "start_date", value: from), .init(name: "end_date", value: through), .init(name: "per_page", value: "100")]
            items += chunk.map { .init(name: "context_codes[]", value: "course_\($0["id"].string.isEmpty ? String($0["id"].int) : $0["id"].string)") }
            for v in try await pages(endpoint("api/v1/calendar_events", items)) where v["workflow_state"].string != "deleted" && !v["hidden"].bool {
                guard !v["all_day"].bool, let start = iso.date(from: v["start_at"].string), let end = iso.date(from: v["end_at"].string), end > start, SchoolTime.dateString(start, zone: zone) == SchoolTime.dateString(end, zone: zone) else { draft.warnings.append("\(v["title"].string)：时间需要人工确认。"); continue }
                var e = ScheduleException(type: "MAKE_UP", date: SchoolTime.dateString(start, zone: zone)); e.id = "canvas:\(v["id"].int)"; e.title = v["title"].string; e.location = v["location_name"].string
                let c = SchoolTime.calendar(zone: zone); e.startMinute = c.component(.hour, from: start) * 60 + c.component(.minute, from: start); e.endMinute = c.component(.hour, from: end) * 60 + c.component(.minute, from: end); try e.validate(); draft.exceptions.append(e)
            }
        }; return draft
    }
}
