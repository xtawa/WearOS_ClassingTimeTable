import SwiftUI
import PostHog
import ClassingCore

@main @MainActor struct ClassingApp: App {
    @StateObject private var store: AppStore
    @Environment(\.scenePhase) private var phase
    init() {
        _ = SubscriptionStore.shared
        AirCrashReports.start(
            apiKey: "ak_production_ZVF6r75cvfvDDFdZeYTz-N9Rf-r5Ofw4Wnu55bi_PGs",
            endpoint: "https://apps.air.build/v1/ingest/crashes"
        )
        PurchaseReporter.start()
        AppAnalytics.start()
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
            NavigationStack { HomeView().postHogScreenView("Today") }.tabItem { Label("今日", systemImage: "sun.max") }.tag(0)
            NavigationStack { TimetableView().postHogScreenView("Timetable") }.tabItem { Label("课表", systemImage: "calendar") }.tag(1)
            NavigationStack { AssistantView().postHogScreenView("Assistant") }.tabItem { Label("助手", systemImage: "sparkles") }.tag(2)
            NavigationStack { SettingsView().postHogScreenView("Settings") }.tabItem { Label("设置", systemImage: "slider.horizontal.3") }.tag(3)
        }.tint(.indigo)
        .alert("操作未完成", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) { Button("知道了", role: .cancel) {} } message: { Text(store.error ?? "") }
        .sheet(isPresented: Binding(get: { bridge.pendingAuthorization != nil && store.state.settings["onboardingCompleted"].bool }, set: { if !$0 { bridge.pendingAuthorization = nil } })) { WatchAuthorizationView() }
        .fullScreenCover(isPresented: Binding(get: { !store.state.settings["onboardingCompleted"].bool }, set: { _ in })) { OnboardingView().postHogScreenView("Onboarding") }
        .onOpenURL { url in
            if url.host == "wear-login", let c = URLComponents(url: url, resolvingAgainstBaseURL: false), let id = c.queryItems?.first(where: { $0.name == "authorizationId" })?.value { bridge.pendingAuthorization = id }
            else { tab = url.host == "assistant" ? 2 : url.host == "timetable" ? 1 : 0 }
        }
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
