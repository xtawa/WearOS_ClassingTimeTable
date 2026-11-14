import WidgetKit
import SwiftUI
import ClassingCore
import AppIntents
struct CourseEntry: TimelineEntry {
    var date: Date, title: String, location: String, start: Date?, end: Date?, following: String, exam: Bool
    var timezone = TimeZone.current.identifier
}
struct CourseProvider: TimelineProvider {
    func placeholder(in context: Context) -> CourseEntry { CourseEntry(date: Date(), title: "下一节课", location: "Classing", start: Date().addingTimeInterval(1800), end: Date().addingTimeInterval(5400), following: "", exam: false) }
    func getSnapshot(in context: Context, completion: @escaping (CourseEntry) -> Void) { completion(entry(at: Date())) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<CourseEntry>) -> Void) {
        let state = (try? SharedDisk.load()) ?? AppState(), now = Date(); var dates = [now]
        if state.settings["academicMode"].string == "EXAM" { dates += state.exams.filter { !$0.completed && $0.end > now }.flatMap { [$0.start, $0.end] } }
        else { dates += ((try? ScheduleProjector.upcoming(state, now: now)) ?? []).prefix(30).flatMap { [$0.start, $0.end] } }
        let entries = Set(dates.filter { $0 >= now && $0 <= now.addingTimeInterval(86400) }).sorted().map { entry(at: $0, state: state) }
        completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(3600))))
    }
    func entry(at date: Date, state: AppState? = nil) -> CourseEntry {
        let s = state ?? ((try? SharedDisk.load()) ?? AppState())
        if s.settings["academicMode"].string == "EXAM", let e = s.exams.filter({ !$0.completed && $0.end > date }).sorted(by: { $0.startAt < $1.startAt }).first { return CourseEntry(date: date, title: e.title, location: e.location, start: e.start, end: e.end, following: "考试", exam: true, timezone: e.timezone) }
        let upcoming = (try? ScheduleProjector.upcoming(s, now: date)) ?? []
        guard let o = upcoming.first else { return CourseEntry(date: date, title: "暂时没有课程", location: "享受自由时间", start: nil, end: nil, following: "", exam: false, timezone: s.settings.timezone) }
        return CourseEntry(date: date, title: o.title, location: o.location ?? "", start: o.start, end: o.end, following: upcoming.dropFirst().first?.title ?? "", exam: false, timezone: s.settings.timezone)
    }
}
struct CourseWidgetView: View {
    @Environment(\.widgetFamily) var family
    var entry: CourseEntry
    var body: some View {
        Group {
            switch family {
            case .accessoryInline: Text("\(entry.title) · \(entry.location)")
            case .accessoryCircular:
                VStack(spacing: 2) { Image(systemName: entry.exam ? "graduationcap" : "calendar"); if let start = entry.start { Text(start, style: .time).font(.caption2.monospacedDigit()) } else { Text("空闲").font(.caption) } }
            #if os(watchOS)
            case .accessoryCorner: Text(entry.title).font(.caption).widgetLabel { if let start = entry.start { Text(start, style: .time) } else { Text("暂无课程") } }
            #endif
            case .accessoryRectangular:
                VStack(alignment: .leading) { Text(entry.title).font(.headline).lineLimit(1); Text(entry.location).font(.caption).lineLimit(1); if let start = entry.start { Text(start > entry.date ? start : (entry.end ?? start), style: .timer).font(.caption.monospacedDigit()) } }
            default:
                VStack(alignment: .leading, spacing: 8) { Label(entry.exam ? "下一场考试" : "下一节课", systemImage: "calendar").font(.caption).foregroundStyle(.secondary); Text(entry.title).font(.headline).lineLimit(2); Text(entry.location).font(.caption).lineLimit(1); if let start = entry.start { Text(start, style: .time).font(.title3.monospacedDigit()) }
                    #if os(iOS)
                    if family == .systemMedium && !entry.following.isEmpty { Text("接下来 · \(entry.following)").font(.caption).foregroundStyle(.secondary) }
                    #endif
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        }.environment(\.timeZone, TimeZone(identifier: entry.timezone) ?? .current).containerBackground(.fill.tertiary, for: .widget).widgetURL(URL(string: "classing://today"))
    }
}
struct NextCourseWidget: Widget {
    let kind = "ClassingNextCourse"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: CourseProvider()) { CourseWidgetView(entry: $0) }.configurationDisplayName("下一节课 / 考试").description("查看时间、地点和倒计时。")
        #if os(watchOS)
        .supportedFamilies([.accessoryInline, .accessoryCircular, .accessoryRectangular, .accessoryCorner])
        #else
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryInline, .accessoryCircular, .accessoryRectangular])
        #endif
    }
}
@main struct ClassingWidgetBundle: WidgetBundle { var body: some Widget { NextCourseWidget() } }
struct OpenClassingIntent: AppIntent {
    static var title: LocalizedStringResource = "打开 Classing 课表"
    static var openAppWhenRun = true
    func perform() async throws -> some IntentResult { .result() }
}
