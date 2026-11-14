import Foundation
public struct ImportDraft: Sendable {
    public var lessons: [Lesson] = [], exceptions: [ScheduleException] = [], exams: [Exam] = []
    public var warnings: [String] = []
    public var settings: Settings?
    public var snapshots: [ScheduleSnapshot]?
    public init() {}
    public func applying(to state: AppState, replace: Bool) throws -> AppState {
        var result = state; result.backup("导入前")
        if replace { result.lessons = lessons; result.exceptions = exceptions; result.exams = exams }
        else {
            for l in lessons { if let i = result.lessons.firstIndex(where: { $0.id == l.id }) { result.lessons[i] = l } else { result.lessons.append(l) } }
            for e in exceptions { if let i = result.exceptions.firstIndex(where: { $0.id == e.id }) { result.exceptions[i] = e } else { result.exceptions.append(e) } }
            for e in exams { if let i = result.exams.firstIndex(where: { $0.id == e.id }) { result.exams[i] = e } else { result.exams.append(e) } }
        }
        if replace, let s = settings { result.settings = s }
        if replace, let snapshots = snapshots { result.snapshots = Array(result.snapshots.prefix(1)) + Array(snapshots.prefix(19)) }
        try result.validate(); return result
    }
}
public enum ScheduleImport {
    public static func json(_ data: Data) throws -> ImportDraft {
        guard data.count <= 10_485_760 else { throw ClassingError.invalid("导入文件不得超过 10 MiB。") }
        let root = try JSONValue.decode(data); var draft = ImportDraft()
        if root["format"].string == "classing_cloud_sync_v2" {
            let doc = try Wire.decoder.decode(CloudDocument.self, from: data); var s = AppState(); try CloudSync.apply(doc, to: &s); draft.lessons = s.lessons; draft.exceptions = s.exceptions; draft.exams = s.exams; draft.settings = s.settings
        } else {
            let body = root["timetable"] == .null ? root : root["timetable"]
            let lessons = body.object["baseLessons"] ?? body.object["lessons"] ?? (root.array.isEmpty ? .array([]) : root)
            draft.lessons = try lessons.array.map(Lesson.fromImport)
            draft.exceptions = try body["exceptions"].array.map { try Wire.decoder.decode(ScheduleException.self, from: $0.data()) }
            draft.exams = try body["exams"].array.map { try Wire.decoder.decode(Exam.self, from: $0.data()) }
            if let s = root.object["settings"], s["values"] != .null { draft.settings = try Wire.decoder.decode(Settings.self, from: s.data()) }
            if root.object["snapshots"] != nil { draft.snapshots = try Wire.decoder.decode([ScheduleSnapshot].self, from: root["snapshots"].data()) }
        }
        guard !draft.lessons.isEmpty || !draft.exceptions.isEmpty || !draft.exams.isEmpty else { throw ClassingError.invalid("没有找到课程或考试。") }
        var s = AppState(); s.lessons = draft.lessons; s.exceptions = draft.exceptions; s.exams = draft.exams; if let settings = draft.settings { s.settings = settings }; try s.validate(); return draft
    }
    public static func text(_ raw: String, zone: String) throws -> ImportDraft {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("{") || trimmed.hasPrefix("[") { return try json(Data(trimmed.utf8)) }
        if trimmed.contains("BEGIN:VCALENDAR") { return try ICS.parse(trimmed, zone: zone) }
        // TSV: title, weekday(1=Monday), start, end, location, teacher, startWeek, endWeek, parity.
        var draft = ImportDraft()
        for (index, line) in trimmed.components(separatedBy: .newlines).enumerated() where !line.isEmpty {
            let p = line.components(separatedBy: "\t")
            guard p.count >= 4, let day = Int(p[1]) else { throw ClassingError.invalid("第 \(index + 1) 行应为：课程名<TAB>星期<TAB>开始<TAB>结束。") }
            var l = Lesson(title: p[0]); l.dayOfWeek = day; l.startMinute = try SchoolTime.minute(p[2]); l.endMinute = try SchoolTime.minute(p[3])
            if p.count > 4 { l.location = p[4] }; if p.count > 5 { l.teacher = p[5] }; if p.count > 6 { l.startWeek = Int(p[6]) ?? 1 }; if p.count > 7 { l.endWeek = Int(p[7]) ?? 20 }; if p.count > 8 { l.weekParity = p[8] }; try l.validate(); draft.lessons.append(l)
        }
        guard !draft.lessons.isEmpty else { throw ClassingError.invalid("没有可导入的课程。") }; return draft
    }
}
public enum ICS {
    private struct Property { var value: String, zone: String?, allDay: Bool }
    private struct Event { var fields: [String: [Property]]; func first(_ key: String) -> Property? { fields[key]?.first } }
    public static func parse(_ raw: String, zone: String, horizonDays: Int = 366) throws -> ImportDraft {
        guard raw.utf8.count <= 10_485_760, TimeZone(identifier: zone) != nil else { throw ClassingError.invalid("ICS 文件过大或时区无效。") }
        var lines: [String] = []
        for line in raw.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n") { if (line.hasPrefix(" ") || line.hasPrefix("\t")), !lines.isEmpty { lines[lines.count - 1] += line.dropFirst() } else { lines.append(line) } }
        var events: [Event] = [], fields: [String: [Property]] = [:], inside = false
        for line in lines {
            if line == "BEGIN:VEVENT" { fields = [:]; inside = true }
            else if line == "END:VEVENT" && inside { events.append(Event(fields: fields)); inside = false }
            else if inside, let colon = line.firstIndex(of: ":") {
                let header = line[..<colon].components(separatedBy: ";"), name = header[0].uppercased(), value = String(line[line.index(after: colon)...])
                let tz = header.first { $0.uppercased().hasPrefix("TZID=") }.map { String($0.dropFirst(5)).trimmingCharacters(in: CharacterSet(charactersIn: "\"")) }
                fields[name, default: []].append(Property(value: value, zone: tz, allDay: header.contains { $0.uppercased() == "VALUE=DATE" }))
            }
        }
        guard !events.isEmpty, events.count <= 2000 else { throw ClassingError.invalid("ICS 没有事件或事件超过 2000 个。") }
        var draft = ImportDraft(); let overrides = events.filter { $0.first("RECURRENCE-ID") != nil }
        for (index, event) in events.enumerated() where event.first("RECURRENCE-ID") == nil {
            let title = unescape(event.first("SUMMARY")?.value ?? "未命名课程"), uid = event.first("UID")?.value ?? "event-\(index)"
            guard let startProperty = event.first("DTSTART"), !startProperty.allDay, startProperty.value.count > 8 else { draft.warnings.append("\(title)：全天事件需要手动设置时间。"); continue }
            do {
                let start = try instant(startProperty, fallback: zone), sourceZone = startProperty.value.hasSuffix("Z") ? "UTC" : (startProperty.zone ?? zone)
                let end: Date
                if let p = event.first("DTEND") { end = try instant(p, fallback: sourceZone) }
                else if let p = event.first("DURATION") { end = start.addingTimeInterval(try duration(p.value)) }
                else { throw ClassingError.invalid("缺少结束时间") }
                guard end > start, end.timeIntervalSince(start) <= 604800 else { throw ClassingError.invalid("事件时长无效") }
                let c = SchoolTime.calendar(zone: sourceZone), firstDay = c.startOfDay(for: start)
                let rule = Dictionary((event.first("RRULE")?.value ?? "").split(separator: ";").compactMap { pair -> (String, String)? in let p = pair.split(separator: "=", maxSplits: 1); return p.count == 2 ? (String(p[0]), String(p[1])) : nil }, uniquingKeysWith: { _, b in b })
                let supported: Set<String> = ["FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST"]
                guard Set(rule.keys).isSubset(of: supported) else { throw ClassingError.invalid("包含暂不支持的 RRULE 参数，未导入以免产生错误课表") }
                let freq = rule["FREQ"] ?? "ONCE"; guard ["ONCE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"].contains(freq) else { throw ClassingError.invalid("不支持的重复频率") }
                let interval = max(1, Int(rule["INTERVAL"] ?? "1") ?? 1), count = Int(rule["COUNT"] ?? String(Int.max)) ?? Int.max
                guard interval <= 366, count > 0 else { throw ClassingError.invalid("重复次数无效") }
                let until = try rule["UNTIL"].map { try instant(Property(value: $0, zone: sourceZone, allDay: $0.count == 8), fallback: sourceZone).addingTimeInterval($0.count == 8 ? 86399 : 0) }
                let excluded = try Set((event.fields["EXDATE"] ?? []).flatMap { p in try p.value.split(separator: ",").map { try instant(Property(value: String($0), zone: p.zone ?? sourceZone, allDay: p.allDay), fallback: sourceZone) } })
                let recent = c.date(byAdding: .day, value: -90, to: c.startOfDay(for: Date()))!
                let rangeBase = freq == "ONCE" || rule["COUNT"] != nil ? firstDay : max(firstDay, recent)
                let lastDay = c.date(byAdding: .day, value: horizonDays, to: rangeBase)!
                var starts: Set<Date> = [], emitted = 0, current = rangeBase
                while current <= lastDay && emitted < count {
                    if try matches(current, anchor: firstDay, rule: rule, freq: freq, interval: interval, calendar: c) {
                        let h = c.component(.hour, from: start), m = c.component(.minute, from: start)
                        let date = SchoolTime.dateString(current, zone: sourceZone), occurrence = try SchoolTime.instant(date: date, minute: h * 60 + m, zone: sourceZone)
                        if occurrence >= start, until == nil || occurrence <= until! { emitted += 1; starts.insert(occurrence) }
                    }
                    if freq == "ONCE" { break }; current = c.date(byAdding: .day, value: 1, to: current)!
                }
                for p in event.fields["RDATE"] ?? [] { for value in p.value.split(separator: ",") { starts.insert(try instant(Property(value: String(value), zone: p.zone ?? sourceZone, allDay: false), fallback: sourceZone)) } }
                for occurrence in starts.sorted() {
                    if excluded.contains(occurrence) { continue }
                    let replacement = try overrides.first { candidate in guard candidate.first("UID")?.value == uid, let p = candidate.first("RECURRENCE-ID") else { return false }; return try instant(p, fallback: sourceZone) == occurrence }
                    if replacement?.first("STATUS")?.value == "CANCELLED" { continue }
                    let actualStart = try replacement?.first("DTSTART").map { try instant($0, fallback: sourceZone) } ?? occurrence
                    let actualEnd = try replacement?.first("DTEND").map { try instant($0, fallback: sourceZone) } ?? actualStart.addingTimeInterval(end.timeIntervalSince(start))
                    let localDate = SchoolTime.dateString(actualStart, zone: zone), localEnd = SchoolTime.dateString(actualEnd, zone: zone)
                    guard localDate == localEnd else { draft.warnings.append("\(title)：跨午夜事件需要拆分，未导入。"); continue }
                    let target = SchoolTime.calendar(zone: zone); var e = ScheduleException(type: "MAKE_UP", date: localDate)
                    e.id = "ics:\(uid):\(Wire.milliseconds(occurrence))"; e.title = unescape(replacement?.first("SUMMARY")?.value ?? title)
                    e.location = unescape(replacement?.first("LOCATION")?.value ?? event.first("LOCATION")?.value ?? "")
                    e.note = unescape(event.first("DESCRIPTION")?.value ?? "")
                    e.startMinute = target.component(.hour, from: actualStart) * 60 + target.component(.minute, from: actualStart); e.endMinute = target.component(.hour, from: actualEnd) * 60 + target.component(.minute, from: actualEnd)
                    try e.validate(); draft.exceptions.append(e); guard draft.exceptions.count <= 10000 else { throw ClassingError.invalid("ICS 展开超过 10000 节课程。") }
                }
                if freq != "ONCE" { draft.warnings.append("\(title)：重复事件已在以起始日期或最近窗口为基准的 \(horizonDays) 天范围内展开；更远的课程请重新导入。") }
            } catch { draft.warnings.append("\(title)：\(error.localizedDescription)") }
        }
        guard !draft.exceptions.isEmpty else { throw ClassingError.invalid(draft.warnings.joined(separator: "\n")) }; return draft
    }
    private static func instant(_ p: Property, fallback: String) throws -> Date {
        let text = p.value, date = String(text.prefix(8)); guard date.count == 8 else { throw ClassingError.invalid("ICS 日期无效") }
        let formatted = "\(date.prefix(4))-\(date.dropFirst(4).prefix(2))-\(date.suffix(2))"
        if text.count == 8 { return try SchoolTime.instant(date: formatted, minute: 0, zone: p.zone ?? fallback) }
        guard text.count >= 15, let h = Int(text.dropFirst(9).prefix(2)), let m = Int(text.dropFirst(11).prefix(2)), let s = Int(text.dropFirst(13).prefix(2)), (0...23).contains(h), (0...59).contains(m), (0...59).contains(s) else { throw ClassingError.invalid("ICS 时间无效") }
        let zone = text.hasSuffix("Z") ? "UTC" : (p.zone ?? fallback); guard TimeZone(identifier: zone) != nil else { throw ClassingError.invalid("未知 ICS 时区：\(zone)") }; return try SchoolTime.instant(date: formatted, minute: h * 60 + m, zone: zone).addingTimeInterval(Double(s))
    }
    private static func matches(_ d: Date, anchor a: Date, rule: [String: String], freq: String, interval: Int, calendar c: Calendar) throws -> Bool {
        let elapsed = c.dateComponents([.day], from: a, to: d).day!, weekday = (c.component(.weekday, from: d) + 5) % 7 + 1, day = c.component(.day, from: d), month = c.component(.month, from: d)
        let names = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"], byDay = (rule["BYDAY"] ?? "").split(separator: ",").map(String.init)
        if let byMonth = rule["BYMONTH"], !byMonth.split(separator: ",").compactMap({ Int($0) }).contains(month) { return false }
        if let byMonthDay = rule["BYMONTHDAY"] { let maxDay = c.range(of: .day, in: .month, for: d)!.count; if !byMonthDay.split(separator: ",").compactMap({ Int($0) }).contains(where: { $0 > 0 ? $0 == day : maxDay + $0 + 1 == day }) { return false } }
        if !byDay.isEmpty {
            let maxDay = c.range(of: .day, in: .month, for: d)!.count
            guard byDay.contains(where: { spec in guard spec.hasSuffix(names[weekday - 1]) else { return false }; guard let ordinal = Int(spec.dropLast(2)) else { return true }; return ordinal > 0 ? (day - 1) / 7 + 1 == ordinal : -((maxDay - day) / 7 + 1) == ordinal }) else { return false }
        }
        switch freq {
        case "ONCE": return elapsed == 0
        case "DAILY": return elapsed % interval == 0
        case "WEEKLY":
            let wkst = names.firstIndex(of: rule["WKST"] ?? "MO") ?? 0
            let aw = (c.component(.weekday, from: a) + 5) % 7
            let weekOffset = (elapsed + (aw - wkst + 7) % 7) / 7
            return weekOffset % interval == 0 && (!byDay.isEmpty || weekday == (aw + 1))
        case "MONTHLY": return c.dateComponents([.month], from: c.date(from: c.dateComponents([.year, .month], from: a))!, to: c.date(from: c.dateComponents([.year, .month], from: d))!).month! % interval == 0 && (!byDay.isEmpty || rule["BYMONTHDAY"] != nil || day == c.component(.day, from: a))
        default: return (c.component(.year, from: d) - c.component(.year, from: a)) % interval == 0 && (rule["BYMONTH"] != nil || month == c.component(.month, from: a)) && (!byDay.isEmpty || rule["BYMONTHDAY"] != nil || day == c.component(.day, from: a))
        }
    }
    private static func duration(_ value: String) throws -> TimeInterval {
        let regex = try NSRegularExpression(pattern: "^P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")
        guard let m = regex.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)) else { throw ClassingError.invalid("ICS DURATION 无效") }
        let units: [Double] = [604800, 86400, 3600, 60, 1]; var result = 0.0
        for i in 1...5 { if let range = Range(m.range(at: i), in: value) { result += (Double(value[range]) ?? 0) * units[i - 1] } }; return result
    }
    private static func unescape(_ value: String) -> String { value.replacingOccurrences(of: "\\n", with: "\n").replacingOccurrences(of: "\\N", with: "\n").replacingOccurrences(of: "\\,", with: ",").replacingOccurrences(of: "\\;", with: ";").replacingOccurrences(of: "\\\\", with: "\\") }
    private static func escape(_ value: String) -> String { value.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\n", with: "\\n").replacingOccurrences(of: ",", with: "\\,").replacingOccurrences(of: ";", with: "\\;") }
    public static func export(_ state: AppState, from: String, through: String) throws -> String {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime]; f.timeZone = TimeZone(secondsFromGMT: 0)
        func date(_ d: Date) -> String { f.string(from: d).replacingOccurrences(of: "-", with: "").replacingOccurrences(of: ":", with: "") }
        var lines = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Classing//Apple//ZH", "CALSCALE:GREGORIAN"]
        func append(id: String, title: String, start: Date, end: Date, location: String, note: String) { lines += ["BEGIN:VEVENT", "UID:\(escape(id))@classing", "DTSTAMP:\(date(Date()))", "DTSTART:\(date(start))", "DTEND:\(date(end))", "SUMMARY:\(escape(title))", "LOCATION:\(escape(location))", "DESCRIPTION:\(escape(note))", "END:VEVENT"] }
        for o in try ScheduleProjector.project(state, from: from, through: through) { append(id: o.id, title: o.title, start: o.start, end: o.end, location: o.location ?? "", note: o.note ?? "") }
        for e in state.exams where !e.completed { append(id: e.id, title: "考试：\(e.title)", start: e.start, end: e.end, location: e.location, note: e.note) }
        lines.append("END:VCALENDAR")
        // RFC 5545 folds at 75 OCTETS, not 75 UTF-16 code units.
        return lines.flatMap { line -> [String] in var chunks: [String] = [], part = ""; for character in line { if part.utf8.count + String(character).utf8.count > 74 { chunks.append(part); part = " " }; part.append(character) }; chunks.append(part); return chunks }.joined(separator: "\r\n") + "\r\n"
    }
}
