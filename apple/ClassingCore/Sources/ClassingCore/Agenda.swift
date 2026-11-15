import Foundation

/// The same time-aware content is used by the app, widgets and complications.
public struct AgendaItem: Equatable, Identifiable, Sendable {
    public var id: String
    public var title: String
    public var location: String
    public var start: Date
    public var end: Date
    public var timezone: String
    public var changed: Bool
    public var isExam: Bool
}

public struct AgendaSnapshot: Equatable, Sendable {
    public enum Phase: String, Sendable { case upcoming, inProgress, breakTime, finished, free, empty }
    public var date: Date
    public var timezone: String
    public var isExam: Bool
    public var phase: Phase
    public var today: [AgendaItem]
    public var upcoming: [AgendaItem]
    public var primary: AgendaItem? { upcoming.first }
    public var completedCount: Int { today.filter { $0.end <= date }.count }
    public var remainingCount: Int { today.filter { $0.end > date }.count }
    public var target: Date? { primary.map { $0.start <= date ? $0.end : $0.start } }
    public var minutesRemaining: Int { target.map { max(0, Int(ceil($0.timeIntervalSince(date) / 60))) } ?? 0 }
    public var progress: Double {
        guard let item = primary, item.start <= date else { return 0 }
        return min(1, max(0, date.timeIntervalSince(item.start) / item.end.timeIntervalSince(item.start)))
    }
    public var status: String {
        switch phase {
        case .inProgress: return isExam ? "考试进行中" : "正在上课"
        case .breakTime: return "课间休息"
        case .finished: return "今日已结束"
        case .free: return isExam ? "今天没有考试" : "今天没有课程"
        case .empty: return isExam ? "还没有考试安排" : "从一张课表开始"
        case .upcoming: return isExam ? "下一场考试" : "下一节课"
        }
    }
    public var countdownLabel: String { primary.map { $0.start <= date ? "距结束" : "距开始" } ?? "自由时间" }

    public static func make(_ state: AppState, at date: Date) throws -> AgendaSnapshot {
        let zone = state.settings.timezone
        let day = SchoolTime.dateString(date, zone: zone)
        let examMode = state.settings["academicMode"].string == "EXAM"
        let today: [AgendaItem]
        let upcoming: [AgendaItem]
        let hasData: Bool
        if examMode {
            let exams = state.exams.filter { !$0.completed }.sorted { $0.start == $1.start ? $0.id < $1.id : $0.start < $1.start }
            let items = exams.map { AgendaItem(id: $0.id, title: $0.title, location: $0.location, start: $0.start, end: $0.end, timezone: $0.timezone, changed: false, isExam: true) }
            today = items.filter { SchoolTime.dateString($0.start, zone: zone) == day || ($0.start < date && $0.end > date) }
            upcoming = items.filter { $0.end > date }
            hasData = !state.exams.isEmpty
        } else {
            func item(_ o: Occurrence) -> AgendaItem { AgendaItem(id: o.id, title: o.title, location: o.location ?? "", start: o.start, end: o.end, timezone: zone, changed: o.isChanged, isExam: false) }
            today = try ScheduleProjector.project(state, from: day, through: day).map(item)
            upcoming = try ScheduleProjector.upcoming(state, now: date, limit: 30).map(item)
            hasData = !state.lessons.isEmpty || !state.exceptions.isEmpty
        }
        let phase: Phase
        if let first = upcoming.first, first.start <= date { phase = .inProgress }
        else if let first = upcoming.first, SchoolTime.dateString(first.start, zone: zone) == day {
            phase = today.contains { $0.end <= date } ? .breakTime : .upcoming
        } else if !today.isEmpty { phase = .finished }
        else { phase = hasData ? .free : .empty }
        return AgendaSnapshot(date: date, timezone: zone, isExam: examMode, phase: phase, today: today, upcoming: upcoming)
    }

    public var transitionDates: [Date] {
        let calendar = SchoolTime.calendar(zone: timezone)
        let midnight = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: date))!
        let horizon = date.addingTimeInterval(86400)
        return Set([date, midnight] + upcoming.flatMap { [$0.start, $0.end] })
            .filter { $0 >= date && $0 <= horizon }.sorted()
    }
}
