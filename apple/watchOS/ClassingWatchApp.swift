import SwiftUI
import ClassingCore
@main @MainActor struct ClassingWatchApp: App {
    @StateObject private var store = AppStore()
    @Environment(\.scenePhase) var phase
    var body: some Scene { WindowGroup { WatchRootView().environmentObject(store).task { await store.start() }.onChange(of: phase) { _, p in if p == .active { Task { await store.becameActive() } } else { store.becameInactive() } } } }
}
struct WatchRootView: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject private var bridge = PhoneWatchBridge.shared
    var body: some View {
        NavigationStack {
            List {
                NavigationLink { WatchTodayView() } label: { NextCourseCard(occurrence: store.upcoming.first, now: store.now) }.listRowBackground(Color.clear)
                NavigationLink("今日课程", systemImage: "sun.max") { WatchTodayView() }
                NavigationLink("本周课表", systemImage: "calendar") { WatchWeekView() }
                NavigationLink("考试", systemImage: "graduationcap") { WatchExamsView() }
                NavigationLink("Ask AI / BYOK", systemImage: "sparkles") { WatchAssistantView() }
                NavigationLink("课程管理", systemImage: "pencil") { WatchCoursesView() }
                NavigationLink("调课与补课", systemImage: "arrow.triangle.swap") { WatchChangesView() }
                NavigationLink("设置与账号", systemImage: "gearshape") { WatchSettings() }
            }.navigationTitle("Classing")
        }.tint(.indigo)
        .alert("操作未完成", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) { Button("知道了", role: .cancel) {} } message: { Text(store.error ?? "") }
        .confirmationDialog("接收来自 iPhone 的 BYOK 密钥？", isPresented: Binding(get: { bridge.pendingBYOK != nil }, set: { if !$0 { bridge.pendingBYOK = nil } })) { Button("接收并存入钥匙串") { Task { await bridge.acceptBYOK() } }; Button("拒绝", role: .cancel) { bridge.pendingBYOK = nil } }
    }
}
struct WatchTodayView: View {
    @EnvironmentObject var store: AppStore
    var body: some View { List { if store.today.isEmpty { Text("今天没有课程，享受自由时间。") }; ForEach(store.today) { o in NavigationLink { WatchOccurrenceDetail(o: o) } label: { CourseRow(occurrence: o) }.listRowBackground(Color.clear) } }.navigationTitle("今日") }
}
struct WatchWeekView: View {
    @EnvironmentObject var store: AppStore
    @State private var offset = 0
    var body: some View {
        List {
            HStack { Button { offset -= 1 } label: { Image(systemName: "chevron.left") }; Spacer(); Text("\(offset == 0 ? "本周" : "第 \(offset) 周")"); Spacer(); Button { offset += 1 } label: { Image(systemName: "chevron.right") } }
            let base = (try? SchoolTime.day(SchoolTime.dateString(store.now, zone: store.state.settings.timezone))) ?? store.now, start = SchoolTime.addDays(SchoolTime.weekStart(base, weekday: store.state.settings.weekStart), offset * 7)
            ForEach(0..<7, id: \.self) { i in let d = SchoolTime.dateString(SchoolTime.addDays(start, i), zone: "UTC"); Section(d) { ForEach(store.occurrences(d, d)) { o in NavigationLink { WatchOccurrenceDetail(o: o) } label: { CourseRow(occurrence: o) }.listRowBackground(Color.clear) } } }
        }.navigationTitle("周课表")
    }
}
struct WatchOccurrenceDetail: View {
    @EnvironmentObject var store: AppStore
    var o: Occurrence
    var body: some View { List { Text(o.title).font(.title3.bold()); Text("\(o.date) \(SchoolTime.clock(o.startMinute))–\(SchoolTime.clock(o.endMinute))"); Text(o.location ?? "未设置地点"); Text(o.teacher ?? "未设置教师"); Text(o.note ?? ""); if let id = o.lessonId { NavigationLink("修改当天安排") { WatchChangeEditor(e: ScheduleException(type: "RESCHEDULE", date: o.date, lessonId: id)) }; Button("取消当天课程", role: .destructive) { store.save(ScheduleException(type: "CANCEL", date: o.date, lessonId: id)) } } }
}
}
struct WatchCoursesView: View {
    @EnvironmentObject var store: AppStore
    var body: some View { List { NavigationLink("添加课程") { WatchLessonEditor(l: Lesson(title: "")) }; ForEach(store.state.lessons) { l in NavigationLink(l.title) { WatchLessonEditor(l: l) } } }.navigationTitle("课程管理") }
}
struct WatchLessonEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var l: Lesson
    @State private var remove = false
    @State private var rule = ""
    var body: some View { Form { TextField("课程名", text: $l.title); TextField("地点", text: Binding(get: { l.location ?? "" }, set: { l.location = $0 })); TextField("教师", text: Binding(get: { l.teacher ?? "" }, set: { l.teacher = $0 })); Picker("星期", selection: $l.dayOfWeek) { ForEach(1...7, id: \.self) { Text("周\($0)").tag($0) } }; Stepper("开始 \(SchoolTime.clock(l.startMinute))", value: $l.startMinute, in: 0...1435, step: 5); Stepper("结束 \(SchoolTime.clock(l.endMinute))", value: $l.endMinute, in: 5...1440, step: 5); Stepper("起始周 \(l.startWeek)", value: $l.startWeek, in: 1...53); Stepper("结束周 \(l.endWeek)", value: $l.endWeek, in: 1...53); Picker("单双周", selection: $l.weekParity) { Text("每周").tag("ALL"); Text("单周").tag("ODD"); Text("双周").tag("EVEN") }; TextField("高级规则 JSON（可用语音或手机编辑）", text: $rule); Button("保存") { do { l.scheduleRuleJson = rule.isEmpty ? nil : rule; try l.validate(); store.save(l); if store.error == nil { dismiss() } } catch { store.error = error.localizedDescription } }; if store.state.lessons.contains(where: { $0.id == l.id }) { Button("删除课程", role: .destructive) { remove = true } } }.navigationTitle("课程").onAppear { rule = l.scheduleRuleJson ?? "" }.confirmationDialog("删除课程？", isPresented: $remove) { Button("删除", role: .destructive) { store.mutate("删除课程前") { $0.lessons.removeAll { $0.id == l.id }; $0.exceptions.removeAll { $0.lessonId == l.id } }; dismiss() } } }
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
struct WatchChangesView: View {
    @EnvironmentObject var store: AppStore
    var body: some View { List { NavigationLink("添加临时安排") { WatchChangeEditor(e: ScheduleException(type: "MAKE_UP", date: SchoolTime.dateString(store.now, zone: store.state.settings.timezone))) }; ForEach(store.state.exceptions) { e in NavigationLink("\(e.date) \(e.title ?? e.type)") { WatchChangeEditor(e: e) } } }.navigationTitle("调课") }
}
struct WatchChangeEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var e: ScheduleException
    var body: some View { Form { Picker("类型", selection: $e.type) { Text("取消").tag("CANCEL"); Text("改时间地点").tag("RESCHEDULE"); Text("补课").tag("MAKE_UP"); Text("放假").tag("HOLIDAY") }; TextField("日期 YYYY-MM-DD", text: $e.date); TextField("名称", text: Binding(get: { e.title ?? "" }, set: { e.title = $0 })); Picker("课程", selection: Binding(get: { e.lessonId ?? "" }, set: { e.lessonId = $0.isEmpty ? nil : $0 })) { Text("无 / 全天").tag(""); ForEach(store.state.lessons) { Text($0.title).tag($0.id) } }; Stepper("开始 \(SchoolTime.clock(e.startMinute ?? 480))", value: Binding(get: { e.startMinute ?? 480 }, set: { e.startMinute = $0 }), in: 0...1435, step: 5); Stepper("结束 \(SchoolTime.clock(e.endMinute ?? 570))", value: Binding(get: { e.endMinute ?? 570 }, set: { e.endMinute = $0 }), in: 5...1440, step: 5); TextField("地点", text: Binding(get: { e.location ?? "" }, set: { e.location = $0 })); Button("保存") { do { if e.startMinute == nil { e.startMinute = 480; e.endMinute = 570 }; try e.validate(); store.save(e); if store.error == nil { dismiss() } } catch { store.error = error.localizedDescription } }; if store.state.exceptions.contains(where: { $0.id == e.id }) { Button("删除临时安排", role: .destructive) { store.mutate("删除调课前") { $0.exceptions.removeAll { $0.id == e.id } }; dismiss() } } }.navigationTitle("临时安排") }
}
