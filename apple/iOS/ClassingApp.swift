import SwiftUI
import ClassingCore

@main @MainActor struct ClassingApp: App {
    @StateObject private var store: AppStore
    @Environment(\.scenePhase) private var phase
    init() {
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
        .alert("授权 Apple Watch 登录？", isPresented: Binding(get: { bridge.pendingAuthorization != nil }, set: { if !$0 { bridge.pendingAuthorization = nil } })) { Button("授权") { Task { await bridge.approve() } }; Button("取消", role: .cancel) { bridge.pendingAuthorization = nil } } message: { Text("仅在你刚刚通过自己的手表发起登录时授权。手表会建立可单独撤销的账号会话。") }
        .fullScreenCover(isPresented: Binding(get: { !store.state.settings["onboardingCompleted"].bool }, set: { _ in })) { OnboardingView() }
        .onOpenURL { url in
            if url.host == "wear-login", let c = URLComponents(url: url, resolvingAgainstBaseURL: false), let id = c.queryItems?.first(where: { $0.name == "authorizationId" })?.value { bridge.pendingAuthorization = id }
            else { tab = url.host == "assistant" ? 2 : url.host == "timetable" ? 1 : 0 }
        }
    }
}
struct HomeView: View {
    @EnvironmentObject var store: AppStore
    @State private var addCourse = false, addExam = false, showChanges = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack { VStack(alignment: .leading) { Text(store.now.formatted(.dateTime.month().day().weekday(.wide))).font(.subheadline).foregroundStyle(.secondary); Text("留一点时间，给自己。").font(.title2.bold()) }; Spacer() }.padding(.top, 12)
                Picker("学习模式", selection: Binding(get: { store.state.settings["academicMode"].string }, set: { store.setting("academicMode", .string($0)) })) { Text("课程").tag("SCHEDULE"); Text("考试").tag("EXAM") }.pickerStyle(.segmented)
                if store.state.settings["academicMode"].string == "EXAM" { ExamListView() }
                else {
                    TimelineView(.periodic(from: .now, by: 60)) { _ in NextCourseCard(occurrence: store.upcoming.first, now: store.now) }
                    HStack { Text("今日安排").font(.title3.bold()); Spacer(); Text("\(store.today.count) 节课").foregroundStyle(.secondary) }
                    if store.today.isEmpty { ContentUnavailableView("今天没有课程", systemImage: "leaf", description: Text("你可以添加课程，或在设置中导入课表。")) }
                    ForEach(store.today) { o in NavigationLink { OccurrenceDetailView(occurrence: o) } label: { CourseRow(occurrence: o) }.buttonStyle(.plain) }
                    let conflicts = ScheduleProjector.conflicts(store.today)
                    if !conflicts.isEmpty { Label("有 \(conflicts.count) 处时间冲突，请在课表中核对。", systemImage: "exclamationmark.triangle").font(.caption).foregroundStyle(.orange) }
                    Button { showChanges = true } label: { Label("调课与临时安排", systemImage: "arrow.triangle.swap").frame(maxWidth: .infinity).padding(16).classingGlass() }
                }
            }.padding(20)
        }.background(ClassingBackdrop()).navigationTitle("Classing").navigationBarTitleDisplayMode(.large)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Menu { Button("添加课程", systemImage: "plus") { addCourse = true }; Button("添加考试", systemImage: "graduationcap") { addExam = true }; NavigationLink("导入课表") { ImportView() } } label: { Image(systemName: "plus") } } }
        .sheet(isPresented: $addCourse) { NavigationStack { LessonEditor(lesson: Lesson(title: "")) } }
        .sheet(isPresented: $addExam) { NavigationStack { ExamEditor(exam: Exam(title: "", start: store.now.addingTimeInterval(86400), end: store.now.addingTimeInterval(91800), timezone: store.state.settings.timezone)) } }
        .sheet(isPresented: $showChanges) { NavigationStack { ChangesView() } }
        .refreshable { await store.sync() }
    }
}
struct TimetableView: View {
    @EnvironmentObject var store: AppStore
    @State private var selected = Date(), mode = "week", search = ""
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
struct OnboardingView: View {
    @EnvironmentObject var store: AppStore
    @State private var step = 0, semester = Date()
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 28) {
                Spacer(); Image(systemName: "calendar.badge.clock").font(.system(size: 64)).foregroundStyle(.indigo)
                Text(step == 0 ? "让课表，轻一点。" : "从你的学期开始。").font(.largeTitle.bold())
                Text("课程、考试和提醒，留在 iPhone 与 Apple Watch 上。无需登录即可使用本地功能。").foregroundStyle(.secondary)
                if step == 1 { DatePicker("第一周开始日期", selection: $semester, displayedComponents: .date) }
                Spacer()
                Button(step == 0 ? "开始" : "进入 Classing") { if step == 0 { step = 1 } else { store.mutate { $0.settings["semesterWeekStartDate"] = .string(SchoolTime.dateString(semester)); $0.settings["onboardingCompleted"] = .bool(true) } } }.buttonStyle(.borderedProminent).controlSize(.large).frame(maxWidth: .infinity)
            }.padding(32).background(ClassingBackdrop())
        }.interactiveDismissDisabled()
    }
}
