import Foundation
public enum AIProposal {
    /// Apply atomically and only to the snapshot the assistant actually saw.
    public static func apply(_ proposal: JSONValue, exams: Bool, expected: AppState, current: AppState) throws -> AppState {
        guard expected.lessons == current.lessons, expected.exams == current.exams, expected.exceptions == current.exceptions, expected.settings.timezone == current.settings.timezone, expected.settings.semesterStart == current.settings.semesterStart, expected.settings.weekStart == current.settings.weekStart, expected.settings["weekNumberMode"] == current.settings["weekNumberMode"] else { throw ClassingError.invalid("课表已发生变化，请重新向 AI 提问。") }
        guard proposal["version"].int == 1, (1...20).contains(proposal["actions"].array.count) else { throw ClassingError.invalid("AI 修改建议无效。") }
        var result = current; result.backup("应用 AI 建议前"); var touched: Set<String> = []
        for action in proposal["actions"].array {
            let operation = action["operation"].string
            if exams {
                guard ["create", "update", "delete"].contains(operation) else { throw ClassingError.invalid("考试操作无效。") }
                let id = operation == "create" ? UUID().uuidString : action["examId"].string, index = result.exams.firstIndex { $0.id == id }
                guard operation == "create" || (index != nil && touched.insert(id).inserted) else { throw ClassingError.invalid("考试已变更。") }
                if operation == "delete" { result.exams.remove(at: index!); continue }
                var object = action["exam"].object; object["id"] = .string(id)
                let e = try Wire.decoder.decode(Exam.self, from: JSONValue.object(object).data())
                if let index = index { result.exams[index] = e } else { result.exams.append(e) }
            } else {
                guard ["create", "update"].contains(operation) else { throw ClassingError.invalid("课程操作无效。") }
                let id = operation == "create" ? UUID().uuidString : action["lessonId"].string, index = result.lessons.firstIndex { $0.id == id }
                guard operation == "create" || (index != nil && touched.insert(id).inserted) else { throw ClassingError.invalid("课程已变更。") }
                var object: [String: JSONValue] = [:]
                if let index = index { object = try JSONValue.encode(result.lessons[index]).object }
                for (key, value) in action["lesson"].object { object[key] = value }; object["id"] = .string(id)
                let lesson = try Lesson.fromImport(.object(object)); if let index = index { result.lessons[index] = lesson } else { result.lessons.append(lesson) }
            }
        }
        try result.validate(); return result
    }
}
public struct SSEEvent: Equatable, Sendable { public var event: String, data: String, id: String? }
public struct SSEParser: Sendable {
    private var event = "message", data: [String] = [], id: String?
    private var bytes: [UInt8] = [], previousWasCR = false, firstLine = true
    public init() {}
    /// Preserve blank lines: Foundation's AsyncBytes.lines coalesces line breaks,
    /// but an empty line is the event boundary in SSE.
    public mutating func byte(_ byte: UInt8) -> SSEEvent? {
        if byte == 10 && previousWasCR { previousWasCR = false; return nil }
        previousWasCR = byte == 13
        guard byte == 10 || byte == 13 else { bytes.append(byte); return nil }
        return bufferedLine()
    }
    private mutating func bufferedLine() -> SSEEvent? {
        var text = String(decoding: bytes, as: UTF8.self); bytes.removeAll(keepingCapacity: true)
        if firstLine { firstLine = false; if text.hasPrefix("\u{FEFF}") { text.removeFirst() } }
        return line(text)
    }
    public mutating func line(_ line: String) -> SSEEvent? {
        if line.isEmpty { guard !data.isEmpty else { event = "message"; return nil }; let result = SSEEvent(event: event, data: data.joined(separator: "\n"), id: id); event = "message"; data = []; return result }
        if line.hasPrefix(":") { return nil }; let split = line.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)
        let name = String(split[0]); var value = split.count > 1 ? String(split[1]) : ""; if value.hasPrefix(" ") { value.removeFirst() }
        switch name { case "event": event = value; case "data": data.append(value); case "id": if !value.contains("\0") { id = value }; default: break }; return nil
    }
    public mutating func finish() -> SSEEvent? {
        if !bytes.isEmpty, let event = bufferedLine() { return event }
        return line("")
    }
}
