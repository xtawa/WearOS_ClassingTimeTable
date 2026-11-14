import Foundation
public enum PayloadEquivalence {
    /// Android JSONObject includes optional null keys and preserves insertion order;
    /// Swift Codable normally omits nil keys and sorts keys. These are not edits.
    public static func equal(_ a: String?, _ b: String?) -> Bool {
        if a == b { return true }; guard let a = a, let b = b, let left = try? JSONValue.decode(Data(a.utf8)), let right = try? JSONValue.decode(Data(b.utf8)) else { return false }
        return normalized(left) == normalized(right)
    }
    private static func normalized(_ value: JSONValue) -> JSONValue {
        switch value {
        case .object(let object): return .object(object.filter { $0.value != .null }.mapValues(normalized))
        case .array(let array): return .array(array.map(normalized))
        default: return value
        }
    }
}
