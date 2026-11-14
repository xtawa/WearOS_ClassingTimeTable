import SwiftUI
import EventKit
import ClassingCore
@MainActor final class CalendarBridge: ObservableObject {
    static let shared = CalendarBridge()
    private let events = EKEventStore()
    @Published var calendars: [EKCalendar] = []
    func authorize() async throws { guard try await events.requestFullAccessToEvents() else { throw ClassingError.invalid("请在系统设置中允许日历访问。") }; calendars = events.calendars(for: .event).filter { $0.title != "Classing" } }
    func export(_ state: AppState) throws -> Int {
        guard EKEventStore.authorizationStatus(for: .event) == .fullAccess else { throw ClassingError.invalid("请先授权日历访问。") }
        let from = SchoolTime.dateString(Date(), zone: state.settings.timezone), through = SchoolTime.dateString(SchoolTime.addDays(try SchoolTime.day(from), 180), zone: "UTC")
        let start = try SchoolTime.instant(date: from, minute: 0, zone: state.settings.timezone), end = try SchoolTime.instant(date: through, minute: 1440, zone: state.settings.timezone)
        let calendar: EKCalendar
        if let existing = events.calendars(for: .event).first(where: { $0.title == "Classing" && $0.allowsContentModifications }) { calendar = existing }
        else {
            calendar = EKCalendar(for: .event, eventStore: events); calendar.title = "Classing"; calendar.cgColor = UIColor.systemIndigo.cgColor
            guard let source = events.defaultCalendarForNewEvents?.source ?? events.sources.first(where: { $0.sourceType == .local }) else { throw ClassingError.invalid("没有可写入的日历来源。") }; calendar.source = source; try events.saveCalendar(calendar, commit: true)
        }
        // Reconcile only Classing-owned events, never another app's calendar entries.
        for event in events.events(matching: events.predicateForEvents(withStart: start, end: end, calendars: [calendar])) where event.url?.scheme == "classing" { try events.remove(event, span: .thisEvent, commit: false) }
        var count = 0
        func add(id: String, title: String, start: Date, end: Date, location: String?, note: String?) throws { let e = EKEvent(eventStore: events); e.calendar = calendar; e.title = title; e.startDate = start; e.endDate = end; e.timeZone = TimeZone(identifier: state.settings.timezone); e.location = location; e.notes = note; var c = URLComponents(); c.scheme = "classing"; c.host = "course"; c.queryItems = [.init(name: "id", value: id)]; e.url = c.url; try events.save(e, span: .thisEvent, commit: false); count += 1 }
        for o in try ScheduleProjector.project(state, from: from, through: through) { try add(id: o.id, title: o.title, start: o.start, end: o.end, location: o.location, note: o.note) }
        for e in state.exams where !e.completed && e.start >= start && e.start <= end { try add(id: e.id, title: "考试：\(e.title)", start: e.start, end: e.end, location: e.location, note: e.note) }
        try events.commit(); return count
    }
    func importCalendar(id: String, zone: String) throws -> ImportDraft {
        guard let calendar = events.calendar(withIdentifier: id), calendar.title != "Classing" else { throw ClassingError.invalid("请选择外部日历。") }
        let start = Date(), end = start.addingTimeInterval(15_552_000), items = events.events(matching: events.predicateForEvents(withStart: start, end: end, calendars: [calendar])); var draft = ImportDraft()
        for event in items {
            guard !event.isAllDay, event.endDate > event.startDate else { draft.warnings.append("\(event.title ?? "事件")：全天日程需手动设置时间。"); continue }
            let date = SchoolTime.dateString(event.startDate, zone: zone); guard date == SchoolTime.dateString(event.endDate, zone: zone) else { draft.warnings.append("\(event.title ?? "事件")：跨日事件需拆分。"); continue }
            let c = SchoolTime.calendar(zone: zone); var e = ScheduleException(type: "MAKE_UP", date: date); e.id = "eventkit:\(event.eventIdentifier ?? UUID().uuidString):\(Wire.milliseconds(event.startDate))"; e.title = event.title; e.location = event.location; e.note = event.notes
            e.startMinute = c.component(.hour, from: event.startDate) * 60 + c.component(.minute, from: event.startDate); e.endMinute = c.component(.hour, from: event.endDate) * 60 + c.component(.minute, from: event.endDate); try e.validate(); draft.exceptions.append(e)
        }; return draft
    }
}
struct CalendarSettingsView: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject var bridge = CalendarBridge.shared
    @State private var selected = ""
    @State private var draft: ImportDraft?
    @State private var confirm = false
    var body: some View {
        Form {
            Button("授权并读取系统日历") { Task { do { try await bridge.authorize() } catch { store.error = error.localizedDescription } } }
            Section("导出") { Button("同步未来 180 天到 Classing 日历") { do { store.status = "已导出 \(try bridge.export(store.state)) 个日程" } catch { store.error = error.localizedDescription } }; Text("只修改由 Classing 创建的日程，不改动你的其他日历。").font(.caption).foregroundStyle(.secondary) }
            Section("导入外部日历") { Picker("日历", selection: $selected) { Text("选择日历").tag(""); ForEach(bridge.calendars, id: \.calendarIdentifier) { Text($0.title).tag($0.calendarIdentifier) } }; Button("预览未来 180 天") { do { draft = try bridge.importCalendar(id: selected, zone: store.state.settings.timezone) } catch { store.error = error.localizedDescription } }; if let draft = draft { Text("\(draft.exceptions.count) 个日程"); ForEach(draft.warnings, id: \.self) { Text($0).font(.caption).foregroundStyle(.orange) }; Button("合并导入") { confirm = true } } }
        }.navigationTitle("系统日历").confirmationDialog("导入这些系统日历日程？", isPresented: $confirm) { Button("导入") { do { if let draft = draft { try store.commit(draft.applying(to: store.state, replace: false)); self.draft = nil } } catch { store.error = error.localizedDescription } } }
    }
}
