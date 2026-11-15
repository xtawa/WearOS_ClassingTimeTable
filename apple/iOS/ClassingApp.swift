import SwiftUI
import ClassingCore

@main @MainActor struct ClassingApp: App {
    @StateObject private var store: AppStore
    @Environment(\.scenePhase) private var phase
    init() {
        _ = SubscriptionStore.shared
        let store = AppStore(); _store = StateObject(wrappedValue: store)
        BackgroundRefresh.register(store: store)
    }
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(store).environment(\.timeZone, TimeZone(identifier: store.state.settings.timezone) ?? .current)
                .preferredColorScheme(store.state.settings["appearance"].string == "dark" ? .dark : store.state.settings["appearance"].string == "light" ? .light : nil)
                .task { await store.start() }
                .onChange(of: phase) { _, value in if value == .active { Task { await store.becameActive() } } else { store.becameInactive(); if !store.demo { BackgroundRefresh.schedule() } } }
        }
    }
}
struct RootView: View {
    @EnvironmentObject var store: AppStore
    @ObservedObject private var bridge = PhoneWatchBridge.shared
    @State private var tab = 0
    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { HomeView() }.tabItem { Label("今日", systemImage: "sun.max") }.tag(0)
            NavigationStack { TimetableView() }.tabItem { Label("课表", systemImage: "calendar") }.tag(1)
            NavigationStack { AssistantView() }.tabItem { Label("助手", systemImage: "sparkles") }.tag(2)
            NavigationStack { SettingsView() }.tabItem { Label("设置", systemImage: "slider.horizontal.3") }.tag(3)
        }.tint(.indigo)
        .alert("操作未完成", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) { Button("知道了", role: .cancel) {} } message: { Text(store.error ?? "") }
        .sheet(isPresented: Binding(get: { bridge.pendingAuthorization != nil && store.state.settings["onboardingCompleted"].bool }, set: { if !$0 { bridge.pendingAuthorization = nil } })) { WatchAuthorizationView() }
        .fullScreenCover(isPresented: Binding(get: { !store.state.settings["onboardingCompleted"].bool }, set: { _ in })) { OnboardingView() }
        .onOpenURL { url in
            if url.host == "wear-login", let c = URLComponents(url: url, resolvingAgainstBaseURL: false), let id = c.queryItems?.first(where: { $0.name == "authorizationId" })?.value { bridge.pendingAuthorization = id }
            else { tab = url.host == "assistant" ? 2 : url.host == "timetable" ? 1 : 0 }
        }
    }
}
struct TimetableView: View {
    @EnvironmentObject var store: AppStore
    @State private var selected = Date()
    @State private var mode = "week"
    @State private var search = ""
    var week: Date { let day = (try? SchoolTime.day(SchoolTime.dateString(selected, zone: store.state.settings.timezone))) ?? selected; return SchoolTime.weekStart(day, weekday: store.state.settings.weekStart) }
    var body: some View {
        VStack(spacing: 10) {
            HStack { Button { selected = selected.addingTimeInterval(-604800) } label: { Image(systemName: "chevron.left") }; Spacer(); DatePicker("日期", selection: $selected, displayedComponents: .date).labelsHidden(); Spacer(); Button { selected = selected.addingTimeInterval(604800) } label: { Image(systemName: "chevron.right") } }.padding(.horizontal)
            Picker("课表视图", selection: $mode) { Text("周课表").tag("week"); Text("日历").tag("day"); Text("课程管理").tag("list") }.pickerStyle(.segmented).padding(.horizontal)
            if mode == "list" {
                List { ForEach(store.state.lessons.filter { search.isEmpty || $0.title.localizedCaseInsensitiveContains(search) }) { l in NavigationLink { LessonEditor(lesson: l) } label: { VStack(alignment: .leading) { Text(l.title).font(.headline); Text("周\(l.dayOfWeek) \(SchoolTime.clock(l.startMinute))–\(SchoolTime.clock(l.endMinute)) · \(l.startWeek)–\(l.endWeek) 周").font(.caption).foregroundStyle(.secondary) } } } }.searchable(text: $search, prompt: "查找课程")
            } else if mode == "day" {
                ScrollView { VStack(spacing: 12) { ForEach(store.occurrences(SchoolTime.dateString(selected, zone: store.state.settings.timezone), SchoolTime.dateString(selected, zone: store.state.settings.timezone))) { o in NavigationLink { OccurrenceDetailView(occurrence: o) } label: { CourseRow(occurrence: o) }.buttonStyle(.plain) } }.padding() }
            } else {
                ScrollView([.horizontal, .vertical]) {
                    HStack(alignment: .top, spacing: 10) {
                        ForEach(0..<(store.state.settings["showWeekend"].bool ? 7 : 5), id: \.self) { offset in
                            let d = SchoolTime.addDays(week, offset), date = SchoolTime.dateString(d, zone: "UTC")
                            VStack(spacing: 10) { Text(d.formatted(.dateTime.weekday(.abbreviated).day())).font(.headline).padding(.vertical, 12); ForEach(store.occurrences(date, date)) { o in NavigationLink { OccurrenceDetailView(occurrence: o) } label: { CourseRow(occurrence: o) }.buttonStyle(.plain) }; Spacer(minLength: 20) }.frame(width: 154)
                        }
                    }.padding()
                }
            }
        }.background(ClassingBackdrop()).navigationTitle("课表").toolbar { ToolbarItem(placement: .topBarTrailing) { NavigationLink { LessonEditor(lesson: Lesson(title: "")) } label: { Image(systemName: "plus") } } }
        .onAppear { selected = store.now }
    }
}
struct OccurrenceDetailView: View {
    @EnvironmentObject var store: AppStore
    var occurrence: Occurrence
    @State private var change = false
    var body: some View {
        List {
            Section { Text(occurrence.title).font(.title2.bold()); Label(occurrence.date, systemImage: "calendar"); Label("\(SchoolTime.clock(occurrence.startMinute))–\(SchoolTime.clock(occurrence.endMinute))", systemImage: "clock"); Label(occurrence.location ?? "未设置地点", systemImage: "location"); Label(occurrence.teacher ?? "未设置教师", systemImage: "person"); Text(occurrence.note ?? "") }
            Section { if let id = occurrence.lessonId, let lesson = store.state.lessons.first(where: { $0.id == id }) { NavigationLink("编辑重复课程") { LessonEditor(lesson: lesson) }; Button("仅修改这一天") { change = true }; Button("取消这一天的课程", role: .destructive) { store.save(ScheduleException(type: "CANCEL", date: occurrence.date, lessonId: id)) } } }
        }.navigationTitle("课程详情").sheet(isPresented: $change) { NavigationStack { ChangeEditor(exception: ScheduleException(type: "RESCHEDULE", date: occurrence.date, lessonId: occurrence.lessonId)) } }
    }
}
