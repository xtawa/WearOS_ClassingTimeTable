import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
public struct BYOKClient: Sendable {
    public var endpoint: URL, model: String, key: String
    public var transport: any HTTPTransport
    public init(endpoint: URL, model: String, key: String, transport: any HTTPTransport = URLTransport()) throws {
        guard endpoint.scheme == "https", endpoint.user == nil, endpoint.password == nil, endpoint.query == nil, endpoint.fragment == nil, endpoint.host?.isEmpty == false,
              !model.isEmpty, model.count <= 200, !model.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }),
              !key.trimmingCharacters(in: .whitespaces).isEmpty, key.count <= 8192, key.utf8.allSatisfy({ (32...126).contains($0) }) else { throw ClassingError.invalid("BYOK 需要 HTTPS 地址、模型和有效 ASCII 密钥。") }
        self.endpoint = endpoint; self.model = model; self.key = key; self.transport = transport
    }
    private func request(_ path: String, body: Data, contentType: String = "application/json") -> URLRequest {
        let url = endpoint.path.hasSuffix("/" + path) ? endpoint : endpoint.appendingPathComponent(path)
        var request = URLRequest(url: url); request.httpMethod = "POST"; request.httpBody = body
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization"); request.setValue(contentType, forHTTPHeaderField: "Content-Type"); return request
    }
    private func send(_ path: String, body: Data, contentType: String = "application/json", limit: Int = 20_971_520) async throws -> HTTPResponse {
        let response = try await transport.send(request(path, body: body, contentType: contentType), limit: limit)
        // Gateways sometimes echo request headers in errors. Never surface raw provider errors.
        guard (200...299).contains(response.status) else { throw ClassingError.network(response.status, "BYOK_PROVIDER_ERROR", "自备模型请求失败") }; return response
    }
    public func chat(_ messages: [JSONValue], snapshot: JSONValue, onDelta: @escaping @Sendable (String) async -> Void) async throws -> String {
        let system = JSONValue.object(["role": .string("system"), "content": .string("你是课程表与考试助手。只依据当前课表回答，不猜测未知信息。\n" + ByokActions.policy + "\nCurrent snapshot:\n" + (try snapshot.text()))])
        let body = try JSONValue.object(["model": .string(model), "stream": .bool(true), "messages": .array([system] + Array(messages.suffix(40)))]).data()
        let collector = BYOKResult(key: key)
        do {
            try await transport.stream(request("chat/completions", body: body)) { event in
                if event.data == "[DONE]" { let tail = await collector.complete(); if !tail.isEmpty { await onDelta(tail) }; return }
                guard let value = try? JSONValue.decode(Data(event.data.utf8)) else { throw ClassingError.invalid("供应商返回了无效的流式回复。") }
                guard value["error"] == .null else { throw ClassingError.invalid("自备模型请求失败。") }
                let delta = value["choices"].array.first?["delta"]["content"].string ?? ""
                if !delta.isEmpty { let safe = await collector.append(delta); if !safe.isEmpty { await onDelta(safe) } }
                if value["choices"].array.first?["finish_reason"].string.isEmpty == false { let tail = await collector.complete(); if !tail.isEmpty { await onDelta(tail) } }
            }
        } catch ClassingError.network(let status, _, _) { throw ClassingError.network(status, "BYOK_PROVIDER_ERROR", "自备模型请求失败") }
        return try await collector.result()
    }
    public func recognize(_ jpeg: Data) async throws -> JSONValue {
        guard !jpeg.isEmpty, jpeg.count <= 3_145_728 else { throw ClassingError.invalid("识图 JPEG 不得超过 3 MiB。") }
        let prompt = "Read the class timetable. Return only JSON {\"lessons\":[{\"title\":\"...\",\"dayOfWeek\":1,\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"location\":\"\",\"teacher\":\"\",\"startWeek\":1,\"endWeek\":20,\"weekParity\":\"ALL\"}]}. Monday=1, Sunday=7. Do not guess unreadable cells. No explanation."
        let body = JSONValue.object(["model": .string(model), "stream": .bool(false), "messages": .array([.object(["role": .string("system"), "content": .string(prompt)]), .object(["role": .string("user"), "content": .array([.object(["type": .string("image_url"), "image_url": .object(["url": .string("data:image/jpeg;base64," + jpeg.base64EncodedString())])])])])])])
        let response = try await send("chat/completions", body: body.data())
        guard let json = try? JSONValue.decode(response.data), var content = json["choices"].array.first?["message"]["content"].string else { throw ClassingError.invalid("识图供应商未返回有效回复。") }
        content = content.replacingOccurrences(of: key, with: "[REDACTED]").trimmingCharacters(in: .whitespacesAndNewlines)
        if content.hasPrefix("```") { content = content.components(separatedBy: "\n").dropFirst().joined(separator: "\n"); if content.hasSuffix("```") { content = String(content.dropLast(3)) } }
        guard let result = try? JSONValue.decode(Data(content.utf8)), !result["lessons"].array.isEmpty else { throw ClassingError.invalid("识图回复没有有效课表，请重新拍摄或手工导入。") }; return result
    }
    public func transcribe(_ audio: Data, filename: String = "voice.m4a", mime: String = "audio/mp4") async throws -> String {
        guard !audio.isEmpty, audio.count <= 12_582_912, !filename.contains("\n"), !filename.contains("\r"), !mime.contains("\n"), !mime.contains("\r") else { throw ClassingError.invalid("语音附件无效或超过 12 MiB。") }
        let boundary = UUID().uuidString
        var body = Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n\(model)\r\n--\(boundary)\r\nContent-Disposition: form-data; name=\"response_format\"\r\n\r\njson\r\n--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\(filename.replacingOccurrences(of: "\"", with: "_"))\"\r\nContent-Type: \(mime)\r\n\r\n".utf8)
        body.append(audio); body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        let r = try await send("audio/transcriptions", body: body, contentType: "multipart/form-data; boundary=\(boundary)", limit: 1_048_576)
        guard let json = try? JSONValue.decode(r.data), !json["text"].string.isEmpty else { throw ClassingError.invalid("转写供应商未返回文字。") }; return json["text"].string.replacingOccurrences(of: key, with: "[REDACTED]")
    }
    public func speech(_ text: String, voice: String) async throws -> Data {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, text.count <= 4096, !voice.isEmpty else { throw ClassingError.invalid("每段朗读需为 1–4096 字，并选择有效音色。") }
        return try await send("audio/speech", body: JSONValue.object(["model": .string(model), "voice": .string(voice), "input": .string(text), "response_format": .string("mp3")]).data()).data
    }
}
private actor BYOKResult {
    var text = "", tail = "", finished = false
    let key: String
    init(key: String) { self.key = key }
    func append(_ delta: String) -> String {
        tail += delta; tail = tail.replacingOccurrences(of: key, with: "[REDACTED]")
        var overlap = 0
        if key.count > 1 { for n in stride(from: min(tail.count, key.count - 1), through: 1, by: -1) { if tail.hasSuffix(String(key.prefix(n))) { overlap = n; break } } }
        let emit = String(tail.dropLast(overlap)); tail = String(tail.suffix(overlap)); text += emit; return emit
    }
    func complete() -> String { finished = true; let emit = tail.count >= 8 ? "[REDACTED]" : tail; tail = ""; text += emit; return emit }
    func result() throws -> String { guard finished else { throw ClassingError.invalid("BYOK 回答中断，草稿已保留。") }; return text }
}
