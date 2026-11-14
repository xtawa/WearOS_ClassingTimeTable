import Foundation
public struct LogicalVersion: Codable, Equatable, Comparable, Sendable {
    public var counter: Int64, deviceId: String, changedAt: Int64
    public init(counter: Int64, deviceId: String, changedAt: Int64) { self.counter = counter; self.deviceId = deviceId; self.changedAt = changedAt }
    public static func < (a: Self, b: Self) -> Bool { a.counter == b.counter ? a.deviceId < b.deviceId : a.counter < b.counter }
}
public struct VersionedRecord: Codable, Equatable, Identifiable, Sendable {
    public var id: String, payload: String?, version: LogicalVersion, deletedAt: Int64?, recoverableUntil: Int64?
    public init(id: String, payload: String?, version: LogicalVersion, deletedAt: Int64? = nil, recoverableUntil: Int64? = nil) { self.id = id; self.payload = payload; self.version = version; self.deletedAt = deletedAt; self.recoverableUntil = recoverableUntil }
}
public struct SyncChange: Codable, Equatable, Identifiable, Sendable { public var id: String, domain: String, recordId: String, action: String, version: LogicalVersion, occurredAt: Int64, detail: String = "" }
public struct DeviceMetadata: Codable, Equatable, Sendable { public var deviceId: String, lastCounter: Int64, lastChangedAt: Int64 }
public struct CloudDocument: Codable, Equatable, Sendable {
    public var format = "classing_cloud_sync_v2"
    public var updatedAt: Int64 = 0
    /// Domain records and device metadata are ARRAYS on the Android/server wire.
    public var records: [String: [VersionedRecord]] = [:]
    public var changes: [SyncChange] = []
    public var devices: [DeviceMetadata] = []
    public init() {}
    public func validate() throws {
        guard format == "classing_cloud_sync_v2", devices.count <= 1024 else { throw ClassingError.invalid("云文档格式不受支持。") }
        for rows in records.values {
            guard Set(rows.map(\.id)).count == rows.count else { throw ClassingError.invalid("云文档有重复 ID。") }
            for r in rows { guard !r.id.isEmpty, r.version.counter >= 0, !r.version.deviceId.isEmpty, r.payload != nil || r.deletedAt != nil else { throw ClassingError.invalid("云文档记录无效。") } }
        }
    }
    public func merged(with other: Self, now: Int64 = Wire.milliseconds()) throws -> Self {
        try validate(); try other.validate(); var output = self
        for domain in Set(records.keys).union(other.records.keys) {
            var byId = Dictionary(uniqueKeysWithValues: (records[domain] ?? []).map { ($0.id, $0) })
            for b in other.records[domain] ?? [] {
                guard let a = byId[b.id] else { byId[b.id] = b; continue }
                if a.version < b.version { byId[b.id] = b }
                else if a.version == b.version {
                    if a.deletedAt == nil && b.deletedAt != nil { byId[b.id] = b }
                    else if (a.deletedAt == nil) == (b.deletedAt == nil) && (a.payload ?? "") < (b.payload ?? "") { byId[b.id] = b }
                }
            }
            output.records[domain] = byId.values.map { r in var r = r; if r.deletedAt != nil, let expiry = r.recoverableUntil, expiry <= now { r.payload = nil; r.recoverableUntil = nil }; return r }.sorted { $0.id < $1.id }
        }
        var logs: [String: SyncChange] = [:]; for c in changes + other.changes { logs[c.id] = c }
        output.changes = Array(logs.values.sorted { $0.occurredAt == $1.occurredAt ? $0.id > $1.id : $0.occurredAt > $1.occurredAt }.prefix(100))
        var metadata: [String: DeviceMetadata] = [:]
        for d in devices + other.devices { if let a = metadata[d.deviceId], a.lastCounter > d.lastCounter || (a.lastCounter == d.lastCounter && a.lastChangedAt >= d.lastChangedAt) { continue }; metadata[d.deviceId] = d }
        output.devices = Array(metadata.values.sorted { $0.lastChangedAt == $1.lastChangedAt ? $0.deviceId < $1.deviceId : $0.lastChangedAt > $1.lastChangedAt }.prefix(64)); output.updatedAt = max(updatedAt, other.updatedAt); return output
    }
}
public enum CloudSync {
    public static let cloudKeys: Set<String> = ["cloudProvider", "cloudSyncEnabled", "cloudServerUrl", "cloudRemotePath", "cloudUsername", "cloudDriveFileName", "officialSyncFrequency", "syncScopes"]
    public static func enabled(_ domain: String, settings: Settings) -> Bool {
        switch domain {
        case "timetable.lessons", "timetable.exceptions", "timetable.exams": return settings.enabledScopes.contains("TIMETABLE")
        case "mobile.settings", "cloud.config": return settings.enabledScopes.contains("MOBILE_SETTINGS")
        case "wear.settings": return settings.enabledScopes.contains("WEAR_SETTINGS")
        case "app.commands": return true
        default: return false
        }
    }
    public static func capture(_ state: inout AppState, now: Int64 = Wire.milliseconds()) throws {
        try state.validate(); let previous = state.cloud; var domains: [String: [String: String]] = [:]
        domains["timetable.lessons"] = try Dictionary(uniqueKeysWithValues: state.lessons.map { ($0.id, String(decoding: try Wire.encoder.encode($0), as: UTF8.self)) })
        let portableExceptions = state.exceptions.flatMap { e -> [ScheduleException] in
            guard e.type == "HOLIDAY" else { return [e] }
            return state.lessons.filter { e.lessonId == nil || e.lessonId == $0.id }.map { l in
                var cancel = ScheduleException(type: "CANCEL", date: e.date, lessonId: l.id); cancel.id = e.id + ":" + l.id; cancel.note = e.note; return cancel
            }
        }
        domains["timetable.exceptions"] = try Dictionary(uniqueKeysWithValues: portableExceptions.map { ($0.id, String(decoding: try Wire.encoder.encode($0), as: UTF8.self)) })
        domains["timetable.exams"] = try Dictionary(uniqueKeysWithValues: state.exams.map { ($0.id, String(decoding: try Wire.encoder.encode($0), as: UTF8.self)) })
        for (key, value) in state.settings.values where !["onboardingCompleted", "officialEventVersion", "cloudAccountId"].contains(key) && !key.lowercased().hasPrefix("byok") {
            let domain = key.hasPrefix("watch") ? "wear.settings" : (cloudKeys.contains(key) ? "cloud.config" : "mobile.settings")
            domains[domain, default: [:]][key] = try JSONValue.object(["value": value]).text()
        }
        // Secrets are in Keychain and never appear in settings or cloud payloads.
        for (domain, current) in domains where enabled(domain, settings: state.settings) {
            var rows = Dictionary(uniqueKeysWithValues: (state.cloud.records[domain] ?? []).map { ($0.id, $0) })
            for key in Set(current.keys).union(rows.keys) {
                if domain == "wear.settings" && !key.hasPrefix("watch") { continue } // Preserve Android-only watch preferences.
                let old = rows[key], value = current[key]
                if PayloadEquivalence.equal(value, old?.payload) && old?.deletedAt == nil { continue }
                if value == nil && (old == nil || old?.deletedAt != nil) { continue }
                state.counter = max(state.counter, state.cloud.records.values.flatMap { $0 }.map { $0.version.counter }.max() ?? 0) + 1
                let stamp = LogicalVersion(counter: state.counter, deviceId: state.deviceId, changedAt: now)
                rows[key] = VersionedRecord(id: key, payload: value ?? old?.payload, version: stamp, deletedAt: value == nil ? now : nil, recoverableUntil: value == nil ? now + 2_592_000_000 : nil)
                state.cloud.changes.append(SyncChange(id: "\(state.deviceId):\(state.counter):\(domain):\(key)", domain: domain, recordId: key, action: value == nil ? "deleted" : "updated", version: stamp, occurredAt: now))
                state.cloud.updatedAt = now
            }
            state.cloud.records[domain] = rows.values.sorted { $0.id < $1.id }
        }
        if previous.records != state.cloud.records || !state.cloud.devices.contains(where: { $0.deviceId == state.deviceId }) {
            state.cloud.devices.removeAll { $0.deviceId == state.deviceId }
            state.cloud.devices.append(DeviceMetadata(deviceId: state.deviceId, lastCounter: state.counter, lastChangedAt: now))
        }
        state.cloud = try state.cloud.merged(with: CloudDocument(), now: now)
    }
    public static func apply(_ remote: CloudDocument, to state: inout AppState, now: Int64 = Wire.milliseconds(), domains: Set<String>? = nil) throws {
        // Disabled scopes stay untouched and are not echoed back to remote.
        var filtered = remote; filtered.records = remote.records.filter { enabled($0.key, settings: state.settings) && (domains == nil || domains!.contains($0.key)) }
        let document = try state.cloud.merged(with: filtered, now: now)
        var candidate = state
        func decode<T: Decodable>(_ domain: String, _: T.Type) throws -> [T] { try (document.records[domain] ?? []).filter { $0.deletedAt == nil }.map { r in guard let p = r.payload else { throw ClassingError.invalid("记录正文缺失。") }; return try Wire.decoder.decode(T.self, from: Data(p.utf8)) } }
        if enabled("timetable.lessons", settings: state.settings) && (domains == nil || domains!.contains("timetable.lessons")) {
            if document.records["timetable.lessons"] != nil { candidate.lessons = try decode("timetable.lessons", Lesson.self) }
            if document.records["timetable.exceptions"] != nil { candidate.exceptions = try decode("timetable.exceptions", ScheduleException.self) }
            if document.records["timetable.exams"] != nil { candidate.exams = try decode("timetable.exams", Exam.self) }
        }
        if enabled("mobile.settings", settings: state.settings) || enabled("wear.settings", settings: state.settings) { for domain in ["mobile.settings", "wear.settings", "cloud.config"] where enabled(domain, settings: state.settings) && (domains == nil || domains!.contains(domain)) { for r in document.records[domain] ?? [] where r.deletedAt == nil && r.id != "syncScopes" { if domain == "wear.settings" && !r.id.hasPrefix("watch") { continue }; if let p = r.payload { candidate.settings[r.id] = try JSONValue.decode(Data(p.utf8))["value"] } } } }
        try candidate.validate(); candidate.cloud = document; candidate.counter = max(candidate.counter, document.records.values.flatMap { $0 }.map { $0.version.counter }.max() ?? 0); candidate.lastSyncAt = now; state = candidate
    }
    /// Member PUT replaces the entire remote document. Round-trip disabled remote
    /// domains unchanged instead of dropping them, without pushing disabled LOCAL data.
    public static func upload(_ state: AppState, preserving remote: CloudDocument, domains: Set<String>? = nil) throws -> CloudDocument {
        var active = outbound(state)
        if let domains = domains { active.records = active.records.filter { domains.contains($0.key) }; active.changes = active.changes.filter { domains.contains($0.domain) } }
        return try remote.merged(with: active)
    }
    public static func outbound(_ state: AppState) -> CloudDocument { var d = state.cloud; d.records = d.records.filter { enabled($0.key, settings: state.settings) }; d.changes = d.changes.filter { enabled($0.domain, settings: state.settings) }; return d }
}
