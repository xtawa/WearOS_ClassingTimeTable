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
        case .network(let status, let code, _):
            switch code {
            case "AI_RESET_CARD_INVALID": return "重置码无效、已使用或已过期，请核对后重试。"
            case "AI_RESET_BUSY": return "还有 AI 请求正在处理，请等待完成后再重置额度。"
            case "AI_RESET_NOT_NEEDED": return "当前额度无需重置。"
            case "AI_PREFERENCES_INVALID": return "这项助手设置未能保存，请刷新后重试。"
            case "BYOK_PROVIDER_ERROR": return "模型服务暂时无法完成请求，请检查模型配置或稍后重试。"
            case "AI_QUOTA_EXCEEDED", "AI_CREDIT_INSUFFICIENT": return "AI 可用额度不足，请查看用量或补充额度。"
            case "AUTH_INVALID_CREDENTIALS", "INVALID_CREDENTIALS": return "账号或密码不正确，请核对后重试。"
            case "AUTH_APPLE_UNAVAILABLE": return "Apple 登录暂时不可用，请稍后重试或使用邮箱登录。"
            case "AUTH_APPLE_CHALLENGE_INVALID", "AUTH_APPLE_TOKEN_INVALID": return "Apple 登录验证未完成，请重新使用 Apple 登录。"
            case "ACCOUNT_APPLE_CONFLICT": return "这个 Apple 账号已连接其他 Classing 账号，请先核对登录账号。"
            case "ACCOUNT_APPLE_MISMATCH": return "请选择当前 Classing 账号所连接的 Apple 账号。"
            case "ACCOUNT_PASSWORD_CURRENT_INVALID": return "当前密码不正确，请核对后重试。"
            case "ACCOUNT_EMAIL_BINDING_REQUIRED": return "请先在账号管理中连接并验证邮箱。"
            case "ACCOUNT_EMAIL_INVALID": return "邮箱格式不正确，请检查后重试。"
            case "ACCOUNT_EMAIL_CONFLICT": return "该邮箱已用于其他账号，请使用其他邮箱。"
            case "ACCOUNT_PROFILE_CONFLICT": return "这个用户名已被使用，请换一个。"
            case "ACCOUNT_USERNAME_INVALID": return "用户名请使用 3–40 位字母、数字、下划线、点或短横线。"
            case "AUTH_PASSWORD_WEAK", "ACCOUNT_PASSWORD_WEAK", "ACCOUNT_PASSWORD_INVALID": return "密码长度不符合要求，请使用 8–128 位密码。"
            case "AUTH_EMAIL_DELIVERY_FAILED": return "验证码邮件暂时发送失败，请稍后重试。"
            case "BRIEFING_DISABLED": return "每日简报服务暂时不可用，现有设置会保留，请稍后重试。"
            default: break
            }
            switch status {
            case 400, 422: return "提交的信息有误，请检查填写内容后重试。"
            case 401: return "登录已过期，请重新登录。"
            case 403: return "当前账号暂时无法使用此功能，请检查会员或账号权限。"
            case 404: return "内容不存在或已被删除，请刷新后重试。"
            case 409, 412: return "数据已发生变化，请刷新后重试。"
            case 413: return "文件太大，请缩小文件后再试。"
            case 429: return "操作太频繁，请稍后再试。"
            case 500...599: return "服务暂时不可用，请稍后重试。"
            default: return "请求未能完成，请稍后重试。"
            }
        case .conflict: return "云端数据已变更，请重新同步。"
        case .missingSession: return "请先登录账号。"
        }
    }
    public static func userMessage(_ error: Error) -> String {
        if let value = error as? ClassingError { return value.localizedDescription }
        if let value = error as? URLError {
            switch value.code {
            case .notConnectedToInternet, .networkConnectionLost, .cannotConnectToHost, .cannotFindHost: return "网络连接不可用，请检查网络后重试。"
            case .timedOut: return "连接超时，请稍后重试。"
            default: return "网络请求未完成，请稍后重试。"
            }
        }
        if error is DecodingError { return "收到的数据无法识别，请更新应用或稍后重试。" }
        return "操作未完成，请稍后重试。"
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
