import WidgetKit
import SwiftUI
import ClassingCore

struct CourseEntry: TimelineEntry {
    var date: Date
    var snapshot: AgendaSnapshot
}
struct CourseProvider: TimelineProvider {
    func placeholder(in context: Context) -> CourseEntry { preview() }
    func getSnapshot(in context: Context, completion: @escaping (CourseEntry) -> Void) { completion(context.isPreview ? preview() : entry(at: Date())) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<CourseEntry>) -> Void) {
        let state = (try? SharedDisk.load()) ?? AppState()
        let now = Date(), snapshot = entry(at: now, state: state).snapshot
        let entries = snapshot.transitionDates.map { entry(at: $0, state: state) }
        completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(3600))))
    }
    func entry(at date: Date, state: AppState? = nil) -> CourseEntry {
        let state = state ?? ((try? SharedDisk.load()) ?? AppState())
        let snapshot = (try? AgendaSnapshot.make(state, at: date)) ?? (try! AgendaSnapshot.make(AppState(), at: date))
        return CourseEntry(date: date, snapshot: snapshot)
    }
    private func preview() -> CourseEntry {
        var state = AppState()
        let now = Date(), start = Date().addingTimeInterval(1200)
        var exam = Exam(title: "交互设计", start: start, end: start.addingTimeInterval(5400), timezone: state.settings.timezone)
        exam.location = "设计楼 A203"; state.exams = [exam]; state.settings["academicMode"] = .string("EXAM")
        var snapshot = try! AgendaSnapshot.make(state, at: now); snapshot.isExam = false
        return CourseEntry(date: now, snapshot: snapshot)
    }
}
struct CourseWidgetView: View {
    @Environment(\.widgetFamily) private var family
    var entry: CourseEntry
    var agenda = false
    private var size: AgendaWidgetSize {
        switch family {
        case .accessoryInline: return .inline
        case .accessoryCircular: return .circular
        case .accessoryRectangular: return .rectangular
        #if os(watchOS)
        case .accessoryCorner: return .corner
        #endif
        #if os(iOS)
        case .systemMedium: return .medium
        case .systemLarge: return .large
        #endif
        default: return .small
        }
    }
    var body: some View {
        AgendaWidgetCard(snapshot: entry.snapshot, size: size, agenda: agenda)
            .containerBackground(for: .widget) { ClassingBackdrop() }
            .widgetURL(URL(string: agenda ? "classing://timetable" : "classing://today"))
    }
}
struct NextCourseWidget: Widget {
    let kind = "ClassingNextCourse"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: CourseProvider()) { CourseWidgetView(entry: $0) }
            .configurationDisplayName("下一节课 / 考试").description("时间、地点和自动倒计时，抬眼就看见。")
        #if os(watchOS)
            .supportedFamilies([.accessoryInline, .accessoryCircular, .accessoryRectangular, .accessoryCorner])
        #else
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryInline, .accessoryCircular, .accessoryRectangular])
        #endif
    }
}
#if os(iOS)
struct TodayAgendaWidget: Widget {
    let kind = "ClassingTodayAgenda"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: CourseProvider()) { CourseWidgetView(entry: $0, agenda: true) }
            .configurationDisplayName("今日课表").description("一眼看清今天的剩余课程与地点。")
            .supportedFamilies([.systemLarge])
    }
}
#endif
@main struct ClassingWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextCourseWidget()
        #if os(iOS)
        TodayAgendaWidget()
        CourseLiveActivity()
        #endif
    }
}
