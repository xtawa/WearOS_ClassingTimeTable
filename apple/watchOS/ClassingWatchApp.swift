import SwiftUI
import ClassingCore
@main @MainActor struct ClassingWatchApp: App {
    @StateObject private var store: AppStore
    init() { let store = AppStore(); _store = StateObject(wrappedValue: store) }
    @Environment(\.scenePhase) var phase
    var body: some Scene { WindowGroup { content.environmentObject(store).task { await store.start() }.onChange(of: phase) { _, p in if p == .active { Task { await store.becameActive() } } else { store.becameInactive() } } } }
    @ViewBuilder private var content: some View {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--demo") && ProcessInfo.processInfo.arguments.contains("--settings") { NavigationStack { WatchSettings() }.tint(.indigo) }
        else if ProcessInfo.processInfo.arguments.contains("--demo") && ProcessInfo.processInfo.arguments.contains("--assistant-preview") { NavigationStack { WatchAssistantView() }.tint(.indigo) }
        else if ProcessInfo.processInfo.arguments.contains("--demo") && ProcessInfo.processInfo.arguments.contains("--week") { NavigationStack { WatchWeekView(initialOffset: ProcessInfo.processInfo.arguments.contains("--next-week") ? 1 : 0) }.tint(.indigo) }
        else { WatchRootView() }
        #else
        WatchRootView()
        #endif
    }
}
struct WatchRootView: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject private var bridge = PhoneWatchBridge.shared
    var body: some View {
        NavigationStack {
            List {
                TimelineView(.periodic(from: .now, by: 60)) { context in
                    NavigationLink { if store.state.settings["academicMode"].string == "EXAM" { WatchExamsView() } else { WatchTodayView() } } label: {
                        AgendaHeroCard(snapshot: store.agenda(at: store.demo ? store.now : context.date), compact: true)
                    }.buttonStyle(.plain)
                }.listRowBackground(Color.clear).listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 6, trailing: 0))
                NavigationLink { WatchTodayView() } label: { Label("今日课程", systemImage: "sun.max") }
                NavigationLink { WatchWeekView() } label: { Label("本周课表", systemImage: "calendar") }
                NavigationLink { WatchExamsView() } label: { Label("考试", systemImage: "graduationcap") }
                NavigationLink { WatchAssistantView() } label: { Label("助手", systemImage: "sparkles") }
                NavigationLink { WatchSettings() } label: { Label("设置", systemImage: "gearshape") }
            }.navigationTitle("Classing")
        }.tint(.indigo)
        .alert("操作未完成", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) { Button("知道了", role: .cancel) {} } message: { Text(store.error ?? "") }
        .confirmationDialog("接收来自 iPhone 的 BYOK 密钥？", isPresented: Binding(get: { bridge.pendingBYOK != nil }, set: { if !$0 { bridge.pendingBYOK = nil } })) { Button("接收并存入钥匙串") { Task { await bridge.acceptBYOK() } }; Button("拒绝", role: .cancel) { bridge.pendingBYOK = nil } }
    }
}
struct WatchTodayView: View {
    @EnvironmentObject var store: AppStore
    var body: some View { List { if store.today.isEmpty { Text("暂无课程") }; ForEach(store.today) { o in NavigationLink { WatchOccurrenceDetail(o: o) } label: { CourseRow(occurrence: o) }.listRowBackground(Color.clear) } }.navigationTitle("今日") }
}
struct WatchWeekView: View {
    @EnvironmentObject var store: AppStore
    @State private var offset: Int
    init(initialOffset: Int = 0) { _offset = State(initialValue: initialOffset) }
    private var dates: [String] { (try? SchoolTime.weekDates(containing: store.now, offset: offset, settings: store.state.settings)) ?? [] }
    private var weekTitle: String {
        guard let first = dates.first, let week = try? SchoolTime.weekIndex(first, settings: store.state.settings), week > 0 else { return offset == 0 ? "本周" : "课表" }
        return "第 \(week) 周"
    }
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 2) {
                Button { offset -= 1 } label: { Image(systemName: "chevron.left").frame(width: 44, height: 44).contentShape(Rectangle()) }
                    .accessibilityLabel("上一周").accessibilityIdentifier("watch-previous-week")
                Button { offset = 0 } label: {
                    VStack(spacing: 2) {
                        Text(weekTitle).font(.headline)
                        if let first = dates.first, let last = dates.last { Text("\(first.suffix(5)) — \(last.suffix(5))").font(.caption2).foregroundStyle(.secondary) }
                    }.frame(maxWidth: .infinity, minHeight: 44)
                }.accessibilityLabel("回到本周")
                Button { offset += 1 } label: { Image(systemName: "chevron.right").frame(width: 44, height: 44).contentShape(Rectangle()) }
                    .accessibilityLabel("下一周").accessibilityIdentifier("watch-next-week")
            }.buttonStyle(.plain).padding(.horizontal, 3)
            List {
                ForEach(dates, id: \.self) { date in
                    Section(date) {
                        let occurrences = store.occurrences(date, date)
                        if occurrences.isEmpty { Text("无课程").font(.caption).foregroundStyle(.secondary) }
                        ForEach(occurrences) { o in NavigationLink { WatchOccurrenceDetail(o: o) } label: { CourseRow(occurrence: o) }.listRowBackground(Color.clear) }
                    }
                }
            }
        }.navigationTitle("周课表")
    }
}
struct WatchOccurrenceDetail: View {
    var o: Occurrence
    var body: some View {
        List {
            Text(o.title).font(.title3.bold())
            Text("\(o.date) \(SchoolTime.clock(o.startMinute))–\(SchoolTime.clock(o.endMinute))")
            if let location = o.location, !location.isEmpty { Label(location, systemImage: "mappin.and.ellipse") }
            if let teacher = o.teacher, !teacher.isEmpty { Label(teacher, systemImage: "person") }
            if let note = o.note, !note.isEmpty { Text(note) }
        }.navigationTitle("课程详情")
    }
}
struct WatchExamsView: View {
    @EnvironmentObject var store: AppStore
    var body: some View { List { NavigationLink("添加考试") { WatchExamEditor(e: Exam(title: "", start: store.now.addingTimeInterval(86400), end: store.now.addingTimeInterval(91800), timezone: store.state.settings.timezone)) }; ForEach(store.state.exams.sorted { $0.startAt < $1.startAt }) { e in NavigationLink { WatchExamEditor(e: e) } label: { VStack(alignment: .leading) { Text(e.title); Text(e.start, style: .date).font(.caption); Text(e.completed ? "已完成" : e.start <= store.now && e.end > store.now ? "进行中" : e.location).font(.caption).foregroundStyle(.secondary) } } } }.navigationTitle("考试") }
}
struct WatchExamEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var e: Exam
    @State private var remove = false
    var body: some View { Form { TextField("考试名称", text: $e.title); TextField("地点", text: $e.location); TextField("时区", text: $e.timezone); DatePicker("开始", selection: Binding(get: { e.start }, set: { e.startAt = Wire.milliseconds($0) })); DatePicker("结束", selection: Binding(get: { e.end }, set: { e.endAt = Wire.milliseconds($0) })); Stepper("提前 \(e.reminderMinutes) 分钟", value: $e.reminderMinutes, in: 0...10080, step: 5); Toggle("已完成", isOn: $e.completed); Button("保存") { do { try e.validate(); store.save(e); if store.error == nil { dismiss() } } catch { store.error = error.localizedDescription } }; Button("删除", role: .destructive) { remove = true } }.navigationTitle("考试").environment(\.timeZone, TimeZone(identifier: e.timezone) ?? .current).confirmationDialog("删除考试？", isPresented: $remove) { Button("删除", role: .destructive) { store.mutate("删除考试前") { $0.exams.removeAll { $0.id == e.id } }; dismiss() } } }
}
