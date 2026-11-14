import Foundation
public enum SchoolTime {
    public static let weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    public static func calendar(zone: String = "UTC") -> Calendar { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: zone) ?? TimeZone(secondsFromGMT: 0)!; return c }
    public static func day(_ string: String) throws -> Date {
        let p = string.split(separator: "-").compactMap { Int($0) }
        guard p.count == 3, string.count == 10, let d = calendar().date(from: DateComponents(year: p[0], month: p[1], day: p[2])), dateString(d, zone: "UTC") == string else { throw ClassingError.invalid("日期应为 YYYY-MM-DD：\(string)") }; return d
    }
    public static func dateString(_ date: Date, zone: String = TimeZone.current.identifier) -> String { let p = calendar(zone: zone).dateComponents([.year, .month, .day], from: date); return String(format: "%04d-%02d-%02d", p.year!, p.month!, p.day!) }
    public static func weekday(_ date: Date) -> Int { (calendar().component(.weekday, from: date) + 5) % 7 + 1 }
    public static func addDays(_ date: Date, _ days: Int) -> Date { calendar().date(byAdding: .day, value: days, to: date)! }
    public static func days(_ a: Date, _ b: Date) -> Int { calendar().dateComponents([.day], from: a, to: b).day! }
    public static func weekStart(_ date: Date, weekday: Int) -> Date { addDays(date, -((self.weekday(date) - weekday + 7) % 7)) }
    public static func weekIndex(_ date: String, semester: String, weekStart: Int) throws -> Int { let n = days(self.weekStart(try day(semester), weekday: weekStart), self.weekStart(try day(date), weekday: weekStart)); return Int(floor(Double(n) / 7)) + 1 }
    public static func weekIndex(_ date: String, settings: Settings) throws -> Int {
        if settings["weekNumberMode"].string == "NATURAL" {
            var c = calendar(); c.firstWeekday = settings.weekStart % 7 + 1; c.minimumDaysInFirstWeek = 4
            return c.component(.weekOfYear, from: try day(date))
        }
        return try weekIndex(date, semester: settings.semesterStart, weekStart: settings.weekStart)
    }
    public static func minute(_ clock: String) throws -> Int { let p = clock.split(separator: ":").compactMap { Int($0) }; guard p.count == 2, (0...23).contains(p[0]), (0...59).contains(p[1]) else { throw ClassingError.invalid("时间应为 HH:mm。") }; return p[0] * 60 + p[1] }
    public static func clock(_ minute: Int) -> String { String(format: "%02d:%02d", minute / 60, minute % 60) }
    /// Reject DST gaps instead of normalizing silently. Repeated times select the first occurrence.
    public static func instant(date: String, minute: Int, zone: String) throws -> Date {
        let d = try day(date), c = calendar(zone: zone), p = calendar().dateComponents([.year, .month, .day], from: d)
        guard let base = c.date(from: DateComponents(year: p.year, month: p.month, day: p.day)) else { throw ClassingError.invalid("日期不存在。") }
        if minute == 1440 { return c.date(byAdding: .day, value: 1, to: base)! }
        let target = DateComponents(hour: minute / 60, minute: minute % 60, second: 0)
        guard let instant = c.nextDate(after: base.addingTimeInterval(-1), matching: target, matchingPolicy: .strict, repeatedTimePolicy: .first, direction: .forward), dateString(instant, zone: zone) == date else { throw ClassingError.invalid("该本地时间因夏令时不存在。") }; return instant
    }
}
public struct ScheduleRule: Codable, Equatable, Sendable {
    public var version = 1, interval = 1, weekStartDay = 1, cycleLength = 2
    public var kind = "WEEKLY", anchorDate: String, endDate: String
    public var daysOfWeek: [Int] = [], cycleDays = [1], schoolDays = [1, 2, 3, 4, 5]
    public var holidays: [String] = [], dates: [String] = []
    public var advanceOnHolidays = false
    public var courseGroupId: String?, meetingType: String?, termName: String?
    public init(anchorDate: String, endDate: String) { self.anchorDate = anchorDate; self.endDate = endDate }
    public init(from decoder: Decoder) throws {
        let v = try JSONValue(from: decoder); self.init(anchorDate: v["anchorDate"].string, endDate: v["endDate"].string)
        version = v.integer("version"); kind = v["kind"].string; interval = v.integer("interval", default: 1); weekStartDay = v.integer("weekStartDay", default: 1); cycleLength = v.integer("cycleLength", default: 2)
        daysOfWeek = v["daysOfWeek"].array.map { Int($0.int) }; cycleDays = v["cycleDays"].array.map { Int($0.int) }
        if v.object["schoolDays"] != nil { schoolDays = v["schoolDays"].array.map { Int($0.int) } }
        holidays = v["holidays"].array.map(\.string); dates = v["dates"].array.map(\.string); advanceOnHolidays = v["advanceOnHolidays"].bool
        courseGroupId = v.object["courseGroupId"]?.string; meetingType = v.object["meetingType"]?.string; termName = v.object["termName"]?.string; try validate()
    }
    public func validate() throws {
        let a = try SchoolTime.day(anchorDate), e = try SchoolTime.day(endDate)
        guard version == 1, ["WEEKLY", "ROTATION", "DATES"].contains(kind), e >= a, SchoolTime.days(a, e) <= 3660, (1...366).contains(interval), (1...30).contains(cycleLength), (1...7).contains(weekStartDay), daysOfWeek.allSatisfy({ (1...7).contains($0) }), schoolDays.allSatisfy({ (1...7).contains($0) }), cycleDays.allSatisfy({ (1...cycleLength).contains($0) }), kind != "ROTATION" || (!schoolDays.isEmpty && !cycleDays.isEmpty) else { throw ClassingError.invalid("重复规则无效。") }
        for date in dates { let d = try SchoolTime.day(date); guard d >= a && d <= e else { throw ClassingError.invalid("指定日期不在学期内。") } }; for date in holidays { _ = try SchoolTime.day(date) }
    }
    public func matches(_ date: String, fallbackDay: Int) throws -> Bool {
        guard date >= anchorDate, date <= endDate, !holidays.contains(date) else { return false }
        let d = try SchoolTime.day(date), a = try SchoolTime.day(anchorDate), weekday = SchoolTime.weekday(d)
        switch kind {
        case "DATES": return dates.contains(date)
        case "WEEKLY": return (daysOfWeek.isEmpty ? [fallbackDay] : daysOfWeek).contains(weekday) && SchoolTime.days(SchoolTime.weekStart(a, weekday: weekStartDay), SchoolTime.weekStart(d, weekday: weekStartDay)) / 7 % interval == 0
        default:
            guard schoolDays.contains(weekday) else { return false }; let n = SchoolTime.days(a, d); var elapsed = n / 7 * Set(schoolDays).count
            for offset in 0..<(n % 7) where schoolDays.contains(SchoolTime.weekday(SchoolTime.addDays(a, offset))) { elapsed += 1 }
            if !advanceOnHolidays { for h in Set(holidays) where h >= anchorDate && h < date { if schoolDays.contains(SchoolTime.weekday(try SchoolTime.day(h))) { elapsed -= 1 } } }
            return cycleDays.contains(elapsed % cycleLength + 1)
        }
    }
}
public struct Occurrence: Codable, Equatable, Identifiable, Sendable {
    public var id: String, lessonId: String?, exceptionId: String?, date: String, title: String, teacher: String?, location: String?, note: String?
    public var start: Date, end: Date, source: String, startMinute: Int, endMinute: Int
    public var isChanged: Bool { source != "REGULAR" }
}
public enum ScheduleProjector {
    public static func project(_ state: AppState, from: String, through: String) throws -> [Occurrence] {
        try state.validate()
        let first = try SchoolTime.day(from), last = try SchoolTime.day(through)
        guard last >= first, SchoolTime.days(first, last) <= 3660 else { throw ClassingError.invalid("查询日期范围过大。") }
        let rules = try Dictionary(uniqueKeysWithValues: state.lessons.compactMap { l -> (String, ScheduleRule)? in guard let raw = l.scheduleRuleJson, !raw.isEmpty else { return nil }; return (l.id, try Wire.decoder.decode(ScheduleRule.self, from: Data(raw.utf8))) })
        var result: [Occurrence] = []
        for offset in 0...SchoolTime.days(first, last) {
            let d = SchoolTime.addDays(first, offset), date = SchoolTime.dateString(d, zone: "UTC"), day = SchoolTime.weekday(d)
            let week = try SchoolTime.weekIndex(date, settings: state.settings), edits = state.exceptions.filter { $0.date == date }
            if !edits.contains(where: { $0.type == "HOLIDAY" && $0.lessonId == nil }) {
                for l in state.lessons {
                    let matches: Bool
                    if let r = rules[l.id] { matches = try r.matches(date, fallbackDay: l.dayOfWeek) }
                    else { matches = day == l.dayOfWeek && week >= l.startWeek && week <= l.endWeek && (l.weekParity == "ALL" || (l.weekParity == "ODD" ? week % 2 != 0 : week % 2 == 0)) }
                    guard matches else { continue }; let changes = edits.filter { $0.lessonId == l.id }
                    if changes.contains(where: { $0.type == "CANCEL" || $0.type == "HOLIDAY" }) { continue }; let moves = changes.filter { $0.type == "RESCHEDULE" }
                    if moves.isEmpty { result.append(try occurrence(l, date: date, zone: state.settings.timezone)) }

                }
            }
            // A dated RESCHEDULE may be a destination on a different weekday. Android
            // represents such moves as CANCEL at source + RESCHEDULE at destination.
            for e in edits where e.type == "RESCHEDULE" {
                guard !edits.contains(where: { ($0.type == "CANCEL" || $0.type == "HOLIDAY") && $0.lessonId == e.lessonId }) else { continue }
                let base = state.lessons.first { $0.id == e.lessonId }
                var l = base ?? Lesson(title: e.title ?? "调课")
                l.startMinute = e.startMinute!; l.endMinute = e.endMinute!; l.teacher = e.teacher ?? l.teacher; l.location = e.location ?? l.location; l.note = e.note ?? l.note
                var o = try occurrence(l, date: date, zone: state.settings.timezone); o.id = e.id; o.lessonId = e.lessonId; o.exceptionId = e.id
                o.source = base.map { $0.startMinute == l.startMinute && $0.endMinute == l.endMinute && $0.dayOfWeek == day ? "REGULAR" : "RESCHEDULED" } ?? "RESCHEDULED"
                result.append(o)
            }
            for e in edits where e.type == "MAKE_UP" { var l = Lesson(title: e.title ?? "补课"); l.startMinute = e.startMinute!; l.endMinute = e.endMinute!; l.teacher = e.teacher; l.location = e.location; l.note = e.note; var o = try occurrence(l, date: date, zone: state.settings.timezone); o.id = e.id; o.lessonId = nil; o.exceptionId = e.id; o.source = "MAKE_UP"; result.append(o) }
        }
        return result.sorted { $0.start == $1.start ? $0.id < $1.id : $0.start < $1.start }
    }
    private static func occurrence(_ l: Lesson, date: String, zone: String) throws -> Occurrence { Occurrence(id: "\(l.id):\(date)", lessonId: l.id, exceptionId: nil, date: date, title: l.title, teacher: l.teacher, location: l.location, note: l.note, start: try SchoolTime.instant(date: date, minute: l.startMinute, zone: zone), end: try SchoolTime.instant(date: date, minute: l.endMinute, zone: zone), source: "REGULAR", startMinute: l.startMinute, endMinute: l.endMinute) }
    public static func upcoming(_ state: AppState, now: Date = Date(), limit: Int = 120) throws -> [Occurrence] {
        try state.validate()
        let today = SchoolTime.dateString(now, zone: state.settings.timezone), first = try SchoolTime.day(today)
        var last = state.settings["weekNumberMode"].string == "NATURAL" ? SchoolTime.addDays(first, 366) : SchoolTime.addDays(try SchoolTime.day(state.settings.semesterStart), 371)
        for lesson in state.lessons {
            if let raw = lesson.scheduleRuleJson, !raw.isEmpty {
                let r = try Wire.decoder.decode(ScheduleRule.self, from: Data(raw.utf8)); last = max(last, try SchoolTime.day(r.endDate))
            }
        }
        // Search until enough actual occurrences exist, not an arbitrary 60-day horizon.
        let days = min(3660, max(0, SchoolTime.days(first, last))); var found: [Occurrence] = []
        for offset in 0...days {
            let date = SchoolTime.dateString(SchoolTime.addDays(first, offset), zone: "UTC")
            found += try project(state, from: date, through: date).filter { $0.end > now }
            if found.count >= limit { return Array(found.prefix(limit)) }
        }
        let searchedThrough = SchoolTime.dateString(SchoolTime.addDays(first, days), zone: "UTC")
        for date in Set(state.exceptions.map(\.date)).filter({ $0 > searchedThrough }).sorted() {
            found += try project(state, from: date, through: date).filter { $0.end > now }
            if found.count >= limit { break }
        }
        return Array(found.sorted { $0.start < $1.start }.prefix(limit))
    }

    public static func conflicts(_ occurrences: [Occurrence]) -> [(Occurrence, Occurrence)] { var result: [(Occurrence, Occurrence)] = []; for (i, a) in occurrences.enumerated() { for b in occurrences.dropFirst(i + 1) where a.start < b.end && b.start < a.end { result.append((a, b)) } }; return result }
}
public struct PlannedReminder: Equatable, Sendable, Identifiable { public var id: String, title: String, body: String, at: Date }
public enum ReminderPlanner {
    /// Reserve four of the 64 system notification slots for other flows.
    public static func plan(_ state: AppState, now: Date = Date(), limit: Int = 60) throws -> [PlannedReminder] {
        var result: [PlannedReminder] = []
        if state.settings["reminderEnabled"].bool { let lead = max(0, min(10080, state.settings["reminderMinutes"].int)); for o in try ScheduleProjector.upcoming(state, now: now) { let at = o.start.addingTimeInterval(-Double(lead) * 60); if at > now { result.append(PlannedReminder(id: "classing.lesson.\(o.id)", title: o.title, body: "\(SchoolTime.clock(o.startMinute)) · \(o.location ?? "未设置地点")", at: at)) } } }
        for e in state.exams where !e.completed { let at = e.start.addingTimeInterval(-Double(e.reminderMinutes) * 60); if at > now { result.append(PlannedReminder(id: "classing.exam.\(e.id)", title: "考试：\(e.title)", body: e.location, at: at)) } }
        if state.settings["dailyBriefingEnabled"].bool && state.settings["dailyBriefingChannel"].string != "EMAIL" {
            let minute = try SchoolTime.minute(state.settings["dailyBriefingTime"].string), today = try SchoolTime.day(SchoolTime.dateString(now, zone: state.settings.timezone))
            for offset in 0..<14 { let date = SchoolTime.dateString(SchoolTime.addDays(today, offset), zone: "UTC"), at = try SchoolTime.instant(date: date, minute: minute, zone: state.settings.timezone), tomorrow = SchoolTime.dateString(SchoolTime.addDays(today, offset + 1), zone: "UTC"); let courses = try ScheduleProjector.project(state, from: tomorrow, through: tomorrow); if at > now { result.append(PlannedReminder(id: "classing.briefing.\(date)", title: "明日课表", body: courses.isEmpty ? "明天没有课程。" : courses.map { "\(SchoolTime.clock($0.startMinute)) \($0.title)" }.joined(separator: " · "), at: at)) } }
        }
        return Array(result.sorted { $0.at < $1.at }.prefix(limit))
    }
}
