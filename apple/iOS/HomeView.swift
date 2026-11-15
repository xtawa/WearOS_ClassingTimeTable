import SwiftUI
import ClassingCore

struct HomeView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var addCourse = false
    @State private var addExam = false
    @State private var showChanges = false
    @State private var refreshedAt = Date.distantPast
    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let now = store.demo ? store.now : max(context.date, refreshedAt)
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
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu { Button("添加课程", systemImage: "plus") { addCourse = true }; Button("添加考试", systemImage: "graduationcap") { addExam = true }; NavigationLink("导入课表") { ImportView() } } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $addCourse) { NavigationStack { LessonEditor(lesson: Lesson(title: "")) } }
        .sheet(isPresented: $addExam) { NavigationStack { ExamEditor(exam: Exam(title: "", start: store.now.addingTimeInterval(86400), end: store.now.addingTimeInterval(91800), timezone: store.state.settings.timezone)) } }
        .sheet(isPresented: $showChanges) { NavigationStack { ChangesView() } }
        .refreshable { await store.refreshAgenda(); refreshedAt = Date() }
        .task {
            guard !store.demo else { return }
            while !Task.isCancelled {
                do {
                    try await LiveActivityManager.update(store.agenda(at: Date()), enabled: store.state.settings["liveActivitiesEnabled"].bool)
                    try await Task.sleep(for: .seconds(60))
                } catch is CancellationError { return }
                catch { store.report(error); return }
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
        }.environment(\.timeZone, TimeZone(identifier: snapshot.timezone) ?? .current)
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
            if store.today.isEmpty { Text("暂无课程").font(.subheadline).foregroundStyle(.secondary).padding(.vertical, 12) }
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
