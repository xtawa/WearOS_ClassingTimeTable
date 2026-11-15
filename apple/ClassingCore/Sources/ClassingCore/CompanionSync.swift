import Foundation

/// Paired-device transport shares the existing V2 merge and tombstones, while
/// keeping cloud-provider scopes and account configuration on each device.
public enum CompanionSync {
    public enum Role { case phone, watch }
    public static let readyKey = "companionBootstrapCompleted"
    public static let payloadLimit = 8_388_608
    public static let domains: Set<String> = ["timetable.lessons", "timetable.exceptions", "timetable.exams", "mobile.settings", "wear.settings"]
    public static func capture(_ state: inout AppState, role: Role, now: Int64 = Wire.milliseconds()) throws {
        let mobileKeys: Set<String>? = role == .phone ? nil : (state.settings[readyKey].bool ? ["academicMode"] : [])
        try CloudSync.capture(&state, now: now, includingCompanionDomains: true, mobileSettingKeys: mobileKeys)
    }
    public static func outbound(_ state: AppState, role: Role) -> CloudDocument {
        var document = state.cloud
        document.records = document.records.filter { domains.contains($0.key) }
        if role == .watch { document.records["mobile.settings"] = (document.records["mobile.settings"] ?? []).filter { state.settings[readyKey].bool && $0.id == "academicMode" } }
        document.changes = document.changes.filter { domains.contains($0.domain) && (role == .phone || $0.domain != "mobile.settings" || (state.settings[readyKey].bool && $0.recordId == "academicMode")) }
        return document
    }
    public static func apply(_ remote: CloudDocument, to state: inout AppState, role: Role, now: Int64 = Wire.milliseconds(), preservingSettings: Set<String> = []) throws {
        var incoming = remote, candidate = state
        if role == .phone {
            incoming.records["mobile.settings"] = (incoming.records["mobile.settings"] ?? []).filter { $0.id == "academicMode" }
        } else if let settings = incoming.records["mobile.settings"] {
            // School dates, timezone and global behavior are managed on iPhone.
            // A fresh/legacy watch must not override them with newer defaults.
            let bootstrapped = candidate.settings[readyKey].bool
            let watchDefaults = Set((candidate.cloud.records["mobile.settings"] ?? []).filter { $0.version.deviceId == candidate.deviceId }.map(\.id))
            // After bootstrap keep the phone's existing logical versions so an
            // older queued file cannot roll back a newer direct-message update.
            let authoritative = Set(settings.filter { (!bootstrapped || watchDefaults.contains($0.id)) && ($0.id != "academicMode" || !bootstrapped) }.map(\.id))
            candidate.cloud.records["mobile.settings"] = (candidate.cloud.records["mobile.settings"] ?? []).filter { !authoritative.contains($0.id) }
        }
        try CloudSync.apply(incoming, to: &candidate, now: now, domains: domains, includingCompanionDomains: true, preservingSettings: preservingSettings)
        if role == .watch { candidate.settings[readyKey] = .bool(true) }
        state = candidate
    }
}
