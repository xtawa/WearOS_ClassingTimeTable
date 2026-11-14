import Foundation
public struct Lesson: Codable, Equatable, Identifiable, Sendable {
    public var id = UUID().uuidString
    public var title: String
    public var teacher: String?, location: String?, note: String?
    public var dayOfWeek = 1, startMinute = 480, endMinute = 570, startWeek = 1, endWeek = 20
    public var weekParity = "ALL"
    public var scheduleRuleJson: String?
    public init(title: String) { self.title = title }
    public init(from decoder: Decoder) throws { self = try Self.fromImport(JSONValue(from: decoder)) }
    public func validate() throws {
        guard !id.isEmpty, !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, title.count <= 1000,
              (1...7).contains(dayOfWeek), (0..<1440).contains(startMinute), endMinute > startMinute, endMinute <= 1440,
              (1...53).contains(startWeek), (startWeek...53).contains(endWeek), ["ALL", "ODD", "EVEN"].contains(weekParity) else { throw ClassingError.invalid("课程名称、时间或周次无效。") }
        for t in [teacher, location, note].compactMap({ $0 }) where t.count > 4000 { throw ClassingError.invalid("课程备注过长。") }
        if let raw = scheduleRuleJson, !raw.isEmpty { try Wire.decoder.decode(ScheduleRule.self, from: Data(raw.utf8)).validate() }
    }
    public static func fromImport(_ v: JSONValue) throws -> Lesson {
        var l = Lesson(title: v["title"].string)
        if !v["id"].string.isEmpty { l.id = v["id"].string }
        l.dayOfWeek = v.integer("dayOfWeek", default: 1)
        l.startMinute = v["startTime"].string.isEmpty ? v.integer("startMinute") : try SchoolTime.minute(v["startTime"].string)
        l.endMinute = v["endTime"].string.isEmpty ? v.integer("endMinute") : try SchoolTime.minute(v["endTime"].string)
        l.startWeek = v.integer("startWeek", default: 1); l.endWeek = v.integer("endWeek", default: 20)
        l.weekParity = v.text("weekParity", default: "ALL")
        l.teacher = v["teacher"] == .null ? nil : v["teacher"].string; l.location = v["location"] == .null ? nil : v["location"].string
        l.note = v["note"] == .null ? nil : v["note"].string; l.scheduleRuleJson = v["scheduleRuleJson"] == .null ? nil : v["scheduleRuleJson"].string
        try l.validate(); return l
    }
}
public struct ScheduleException: Codable, Equatable, Identifiable, Sendable {
    public var id = UUID().uuidString
    public var lessonId: String?, title: String?, teacher: String?, location: String?, note: String?
    public var type: String, date: String
    public var dayOfWeek: Int?, startMinute: Int?, endMinute: Int?
    public init(type: String, date: String, lessonId: String? = nil) { self.type = type; self.date = date; self.lessonId = lessonId }
    public func validate() throws {
        _ = try SchoolTime.day(date)
        guard !id.isEmpty, ["CANCEL", "RESCHEDULE", "MAKE_UP", "HOLIDAY"].contains(type) else { throw ClassingError.invalid("调课类型无效。") }
        if type == "RESCHEDULE" || type == "MAKE_UP" { guard let s = startMinute, let e = endMinute, s >= 0, e > s, e <= 1440 else { throw ClassingError.invalid("调课时间无效。") } }
        if type == "CANCEL" || type == "RESCHEDULE" { guard lessonId != nil else { throw ClassingError.invalid("请选择原课程。") } }
        if type == "MAKE_UP" { guard !(title ?? "").isEmpty else { throw ClassingError.invalid("请输入补课名称。") } }
    }
}
public struct Exam: Codable, Equatable, Identifiable, Sendable {
    public var id = UUID().uuidString
    public var title: String, timezone: String
    public var startAt: Int64, endAt: Int64
    public var location = "", note = ""
    public var courseId: String?
    public var reminderMinutes = 10
    public var completed = false
    public init(title: String, start: Date, end: Date, timezone: String) { self.title = title; self.timezone = timezone; startAt = Wire.milliseconds(start); endAt = Wire.milliseconds(end) }
    public init(from decoder: Decoder) throws {
        let v = try JSONValue(from: decoder)
        self.init(title: v["title"].string, start: Date(timeIntervalSince1970: Double(v["startAt"].int) / 1000), end: Date(timeIntervalSince1970: Double(v["endAt"].int) / 1000), timezone: v["timezone"].string)
        id = v["id"].string; location = v["location"].string; note = v["note"].string; courseId = v["courseId"] == .null ? nil : v["courseId"].string
        reminderMinutes = v.integer("reminderMinutes", default: 10); completed = v["completed"].bool; try validate()
    }
    public var start: Date { Date(timeIntervalSince1970: Double(startAt) / 1000) }
    public var end: Date { Date(timeIntervalSince1970: Double(endAt) / 1000) }
    public func validate() throws {
        guard !id.isEmpty, id.count <= 128, !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, title.count <= 1000,
              startAt > 0, endAt > startAt, endAt <= 253402300799000, endAt - startAt <= 604800000,
              TimeZone(identifier: timezone) != nil, (0...10080).contains(reminderMinutes), location.count <= 1000, note.count <= 4000 else { throw ClassingError.invalid("考试日期、时区或提醒无效。") }
    }
}
public struct Settings: Codable, Equatable, Sendable {
    public var values: [String: JSONValue]
    public init() {
        values = ["showWeekend": .bool(true), "reminderEnabled": .bool(false), "reminderMinutes": .int(15), "semesterWeekStartDate": .string(SchoolTime.dateString(Date())), "weekStartDay": .string("MONDAY"), "weekNumberMode": .string("SEMESTER"), "timezone": .string(TimeZone.current.identifier), "dailyBriefingEnabled": .bool(false), "dailyBriefingTime": .string("20:00"), "dailyBriefingChannel": .string("APP_NOTIFICATION"), "cloudProvider": .string("OFFICIAL"), "cloudSyncEnabled": .bool(false), "syncScopes": .string("TIMETABLE,MOBILE_SETTINGS,WEAR_SETTINGS"), "academicMode": .string("SCHEDULE"), "appearance": .string("system"), "onboardingCompleted": .bool(false)]
    }
    public subscript(_ key: String) -> JSONValue { get { values[key] ?? .null } set { values[key] = newValue } }
    public var timezone: String { self["timezone"].string.isEmpty ? TimeZone.current.identifier : self["timezone"].string }
    public var semesterStart: String { self["semesterWeekStartDate"].string }
    public var weekStart: Int { SchoolTime.weekdays.firstIndex(of: self["weekStartDay"].string).map { $0 + 1 } ?? 1 }
    public var enabledScopes: Set<String> { Set(self["syncScopes"].string.split(separator: ",").map(String.init)) }
}
public struct ScheduleSnapshot: Codable, Equatable, Sendable, Identifiable {
    public var id = UUID().uuidString
    public var createdAt = Wire.milliseconds()
    public var reason: String
    public var lessons: [Lesson]
    public var exceptions: [ScheduleException]
    public var exams: [Exam]
    public var settings: Settings
    public init(reason: String, state: AppState) { self.reason = reason; lessons = state.lessons; exceptions = state.exceptions; exams = state.exams; settings = state.settings }
}
public struct AppState: Codable, Equatable, Sendable {
    public var lessons: [Lesson] = []
    public var exceptions: [ScheduleException] = []
    public var exams: [Exam] = []
    public var settings = Settings()
    public var snapshots: [ScheduleSnapshot] = []
    public var cloud = CloudDocument()
    public var deviceId = UUID().uuidString
    public var counter: Int64 = 0, lastSyncAt: Int64 = 0
    public var processedCommands: [String] = []
    public init() {}
    public func validate() throws {
        guard lessons.count <= 5000, exams.count <= 500, exceptions.count <= 10000, Set(lessons.map(\.id)).count == lessons.count, Set(exams.map(\.id)).count == exams.count, Set(exceptions.map(\.id)).count == exceptions.count else { throw ClassingError.invalid("记录数量超限或 ID 重复。") }
        for l in lessons { try l.validate() }; for e in exams { try e.validate() }; for e in exceptions { try e.validate() }
        let forbidden: Set<String> = ["accesstoken", "refreshtoken", "apikey", "password", "secret", "webdavpassword", "driveaccesstoken", "driverefreshtoken"]
        guard settings.values.keys.allSatisfy({ !forbidden.contains($0.lowercased()) }), Set(settings["byokConfig"].object.keys).isSubset(of: ["endpoint", "model"]) else { throw ClassingError.invalid("凭据只能保存在钥匙串，不能放入课表设置或备份。") }
        guard TimeZone(identifier: settings.timezone) != nil else { throw ClassingError.invalid("课表时区无效。") }; _ = try SchoolTime.day(settings.semesterStart)
    }
    public mutating func backup(_ reason: String) { snapshots.insert(ScheduleSnapshot(reason: reason, state: self), at: 0); snapshots = Array(snapshots.prefix(20)) }
    public func aiSnapshot(now: Date = Date()) throws -> JSONValue {
        var v: [String: JSONValue] = ["exceptions": try .encode(exceptions), "exams": try .encode(exams), "timezone": .string(settings.timezone), "weekNumberMode": settings["weekNumberMode"], "semesterWeekStartDate": .string(settings.semesterStart), "currentWeek": .int(Int64(try SchoolTime.weekIndex(SchoolTime.dateString(now, zone: settings.timezone), settings: settings)))]
        v["lessons"] = .array(try lessons.map { l in var item = try JSONValue.encode(l).object; item["startTime"] = .string(SchoolTime.clock(l.startMinute)); item["endTime"] = .string(SchoolTime.clock(l.endMinute)); return .object(item) })
        return .object(v)
    }
}
