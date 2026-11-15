import SwiftUI
import ClassingCore

struct HomeView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var addCourse = false
    @State private var addExam = false
    @State private var showChanges = false
    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let now = store.demo ? store.now : context.date
            let snapshot = store.agenda(at: now)
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    header(snapshot)
                    Picker("学习模式", selection: Binding(get: { store.state.settings["academicMode"].string }, set: { store.setting("academicMode", .string($0)) })) {
                        Text("课程").tag("SCHEDULE"); Text("考试").tag("EXAM")
                    }.pickerStyle(.segmented)
                    if snapshot.isExam { AgendaHeroCard(snapshot: snapshot); ExamListView() }
                    else {
                        NavigationLink { if let first = store.upcoming.first { OccurrenceDetailView(occurrence: first) } else { ImportView() } } label: { AgendaHeroCard(snapshot: snapshot) }.buttonStyle(.plain)
                        summary(snapshot)
                        timeline(snapshot)
                        Button { showChanges = true } label: {
                            HStack { Label("调课与临时安排", systemImage: "arrow.triangle.swap"); Spacer(); Image(systemName: "chevron.right").font(.caption) }
                                .font(.subheadline).padding(18).classingGlass()
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 20)
                    .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: snapshot.phase)
            }
        }
        .background(ClassingBackdrop()).navigationTitle("Classing").navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            NavigationLink { AssistantView() } label: {
                HStack(spacing: 12) {
                    Image(systemName: "sparkles").font(.title3).foregroundStyle(ClassingPalette.accent)
                    Text("问问 Classing").font(.subheadline.weight(.medium)).lineLimit(1).minimumScaleFactor(0.8)
                    Spacer()
                    if !dynamicTypeSize.isAccessibilitySize { Text("课程 · 空闲时间").font(.caption).foregroundStyle(.secondary) }
                    Image(systemName: "arrow.up.right").font(.caption.weight(.semibold))
                }.padding(16).classingGlass()
            }.buttonStyle(.plain).padding(.horizontal, 20).padding(.bottom, 8)
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu { Button("添加课程", systemImage: "plus") { addCourse = true }; Button("添加考试", systemImage: "graduationcap") { addExam = true }; NavigationLink("导入课表") { ImportView() } } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $addCourse) { NavigationStack { LessonEditor(lesson: Lesson(title: "")) } }
        .sheet(isPresented: $addExam) { NavigationStack { ExamEditor(exam: Exam(title: "", start: store.now.addingTimeInterval(86400), end: store.now.addingTimeInterval(91800), timezone: store.state.settings.timezone)) } }
        .sheet(isPresented: $showChanges) { NavigationStack { ChangesView() } }
        .refreshable { await store.sync() }
        .task {
            guard !store.demo else { return }
            while !Task.isCancelled {
                do {
                    try await LiveActivityManager.update(store.agenda(at: Date()), enabled: store.state.settings["liveActivitiesEnabled"].bool)
                    try await Task.sleep(for: .seconds(60))
                } catch is CancellationError { return }
                catch { store.error = error.localizedDescription; return }
            }
        }
    }
    private func header(_ snapshot: AgendaSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Text(snapshot.date, format: .dateTime.month().day().weekday(.wide)).font(.subheadline).foregroundStyle(.secondary)
                Spacer()
                if let week = try? SchoolTime.weekIndex(SchoolTime.dateString(snapshot.date, zone: snapshot.timezone), settings: store.state.settings), week > 0 {
                    Text("第 \(week) 周").font(.caption.weight(.medium)).foregroundStyle(ClassingPalette.accent)
                }
            }
            Text(greeting(snapshot.phase)).font(.system(.largeTitle, design: .rounded, weight: .bold)).accessibilityAddTraits(.isHeader)
            Text(snapshot.phase == .inProgress ? "专注此刻，下一站已经为你准备好。" : "今天的安排，慢慢来，也来得及。")
                .font(.subheadline).foregroundStyle(.secondary)
        }.environment(\.timeZone, TimeZone(identifier: snapshot.timezone) ?? .current)
    }
    private func greeting(_ phase: AgendaSnapshot.Phase) -> String {
        switch phase { case .inProgress: return "把时间，留给当下。"; case .breakTime: return "课间，松一口气。"; case .finished: return "今天辛苦了。"; case .free: return "今天，自由安排。"; case .empty: return "你好，新的学期。"; case .upcoming: return "下一站，准备好了。" }
    }
    private func summary(_ snapshot: AgendaSnapshot) -> some View {
        HStack(spacing: 0) {
            metric("今日课程", value: "\(snapshot.today.count)")
            Divider().frame(height: 28)
            metric("已经完成", value: "\(snapshot.completedCount)")
            Divider().frame(height: 28)
            metric("还有安排", value: "\(snapshot.remainingCount)")
        }.padding(.vertical, 14).background(.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 22))
    }
    private func metric(_ title: String, value: String) -> some View {
        VStack(spacing: 3) { Text(value).font(.title3.monospacedDigit().weight(.semibold)); Text(title).font(.caption2).foregroundStyle(.secondary) }.frame(maxWidth: .infinity)
    }
    private func timeline(_ snapshot: AgendaSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack { Text("今日时间线").font(.headline); Spacer(); NavigationLink("完整课表") { TimetableView() }.font(.caption) }
            if store.today.isEmpty { Label("没有课的日子，也很值得期待。", systemImage: "leaf").font(.subheadline).foregroundStyle(.secondary).padding(.vertical, 12) }
            ForEach(store.today) { occurrence in
                NavigationLink { OccurrenceDetailView(occurrence: occurrence) } label: {
                    CourseRow(occurrence: occurrence).opacity(occurrence.end <= snapshot.date ? 0.55 : 1)
                }.buttonStyle(.plain)
            }
            let conflicts = ScheduleProjector.conflicts(store.today)
            if !conflicts.isEmpty { Label("\(conflicts.count) 处时间冲突，请在课表中核对。", systemImage: "exclamationmark.triangle").font(.caption).foregroundStyle(.orange) }
        }
    }
}
