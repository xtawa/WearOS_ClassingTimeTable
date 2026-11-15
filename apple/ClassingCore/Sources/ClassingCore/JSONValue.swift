import Foundation
public enum ClassingError: Error, LocalizedError, Equatable {
    case invalid(String), network(Int, String, String), conflict, missingSession
    public static func isCancellation(_ error: Error) -> Bool {
        if error is CancellationError { return true }
        let value = error as NSError
        return value.domain == NSURLErrorDomain && value.code == NSURLErrorCancelled
    }
    public var errorDescription: String? {
        switch self {
        case .invalid(let m): return m
        case .network(let s, let c, let m): return "\(m) [\(c), HTTP \(s)]"
        case .conflict: return "云端数据已变更，请重新同步。"
        case .missingSession: return "请先登录账号。"
        }
    }
}
/// Preserve integer milliseconds and Lamport counters, including values above 2^53.
public enum JSONValue: Codable, Equatable, Hashable, Sendable {
    case null, bool(Bool), int(Int64), number(Double), string(String), array([JSONValue]), object([String: JSONValue])
    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(Int64.self) { self = .int(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode([JSONValue].self) { self = .array(v) }
        else { self = .object(try c.decode([String: JSONValue].self)) }
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .null: try c.encodeNil()
        case .bool(let v): try c.encode(v)
        case .int(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .string(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .object(let v): try c.encode(v)
        }
    }
    public func hash(into hasher: inout Hasher) {
        switch self {
        case .null: hasher.combine(0)
        case .bool(let v): hasher.combine(1); hasher.combine(v)
        case .int(let v): hasher.combine(2); hasher.combine(v)
        case .number(let v): hasher.combine(3); hasher.combine(v)
        case .string(let v): hasher.combine(4); hasher.combine(v)
        case .array(let v): hasher.combine(5); hasher.combine(v)
        case .object(let v): hasher.combine(6); for key in v.keys.sorted() { hasher.combine(key); hasher.combine(v[key]) }
        }
    }
    public subscript(_ key: String) -> JSONValue { object[key] ?? .null }
    public var object: [String: JSONValue] { if case .object(let v) = self { return v }; return [:] }
    public var array: [JSONValue] { if case .array(let v) = self { return v }; return [] }
    public var string: String { if case .string(let v) = self { return v }; return "" }
    public var int: Int64 { if case .int(let v) = self { return v }; return 0 }
    public var bool: Bool { if case .bool(let v) = self { return v }; return false }
    public func integer(_ key: String, default fallback: Int = 0) -> Int { object[key].map { Int($0.int) } ?? fallback }
    public func text(_ key: String, default fallback: String = "") -> String { object[key]?.string ?? fallback }
    public func data() throws -> Data { try Wire.encoder.encode(self) }
    public func text() throws -> String { String(decoding: try data(), as: UTF8.self) }
    public static func decode(_ data: Data) throws -> JSONValue { try Wire.decoder.decode(Self.self, from: data) }
    public static func encode<T: Encodable>(_ value: T) throws -> JSONValue { try decode(Wire.encoder.encode(value)) }
}
public enum Wire {
    public static var encoder: JSONEncoder { let e = JSONEncoder(); e.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]; return e }
    public static var decoder: JSONDecoder { JSONDecoder() }
    public static func milliseconds(_ date: Date = Date()) -> Int64 { Int64(date.timeIntervalSince1970 * 1000) }
}
