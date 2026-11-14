import Foundation
import Security
import ClassingCore

actor KeychainVault: TokenVault {
    private let service: String
    init(service: String = Bundle.main.bundleIdentifier ?? "Classing") { self.service = service }
    func read() throws -> AuthSession? { guard let data = try data("account.session") else { return nil }; return try Wire.decoder.decode(AuthSession.self, from: data) }
    func write(_ session: AuthSession?) throws { try put(session.map { try Wire.encoder.encode($0) }, key: "account.session") }
    func secret(_ key: String) throws -> String { try data(key).map { String(decoding: $0, as: UTF8.self) } ?? "" }
    func setSecret(_ value: String, key: String) throws { try put(value.isEmpty ? nil : Data(value.utf8), key: key) }
    func data(_ key: String) throws -> Data? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key, kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?; let status = SecItemCopyMatching(q as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }; guard status == errSecSuccess else { throw ClassingError.invalid("钥匙串读取失败：\(status)") }; return result as? Data
    }
    func put(_ value: Data?, key: String) throws {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key]
        if let value = value {
            let update = SecItemUpdate(q as CFDictionary, [kSecValueData as String: value] as CFDictionary)
            if update == errSecItemNotFound { var add = q; add[kSecValueData as String] = value; add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly; let status = SecItemAdd(add as CFDictionary, nil); guard status == errSecSuccess else { throw ClassingError.invalid("钥匙串写入失败：\(status)") } }
            else if update != errSecSuccess { throw ClassingError.invalid("钥匙串更新失败：\(update)") }
        } else { let status = SecItemDelete(q as CFDictionary); guard status == errSecSuccess || status == errSecItemNotFound else { throw ClassingError.invalid("钥匙串清除失败：\(status)") } }
    }
}

enum SharedDisk {
    static let group = "group.com.xtawa.classingtime.apple"
    static var directory: URL { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Classing") }
    static var file: URL { directory.appendingPathComponent("state.json") }
    static func load() throws -> AppState {
        guard FileManager.default.fileExists(atPath: file.path) else { return AppState() }
        let data = try Data(contentsOf: file); let state = try Wire.decoder.decode(AppState.self, from: data); try state.validate(); return state
    }
    static func save(_ state: AppState) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        #if os(iOS) || os(watchOS)
        try Wire.encoder.encode(state).write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        #else
        try Wire.encoder.encode(state).write(to: file, options: .atomic)
        #endif
    }
}
