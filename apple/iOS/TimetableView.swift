import SwiftUI
import ClassingCore

struct TimetableView: View {
    @EnvironmentObject var store: AppStore
    @State private var selected = ""
    @State private var mode = "week"
    @State private var search = ""
    @State private var choosingDate = false
    @State private var jumpDate = Date()
    private var date: String { selected.isEmpty ? SchoolTime.dateString(store.now, zone: store.state.settings.timezone) : selected }
    private var weekDates: [String] {
        guard let instant = try? SchoolTime.instant(date: date, minute: 720, zone: store.state.settings.timezone) else { return [] }
        return (try? SchoolTime.weekDates(containing: instant, settings: store.state.settings)) ?? []
    }
    var body: some View {
        VStack(spacing: 0) {
            Picker("课表视图", selection: $mode) { Text("周课表").tag("week"); Text("日历").tag("month"); Text("课程").tag("list") }
                .pickerStyle(.segmented).padding(.horizontal, 16).padding(.vertical, 8)
            if mode == "list" { courseList }
            else {
                periodBar
                if mode == "month" { monthView } else { weekView }
            }
        }.background(ClassingBackdrop()).navigationTitle("课表").navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    NavigationLink("添加课程") { LessonEditor(lesson: Lesson(title: "")) }
                    NavigationLink("导入课表") { ImportView() }
                    NavigationLink("学期设置") { SemesterSettingsView() }
                } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $choosingDate) {
            NavigationStack {
                DatePicker("选择日期", selection: $jumpDate, displayedComponents: .date).datePickerStyle(.graphical).padding()
                    .navigationTitle("日期").navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { selected = SchoolTime.dateString(jumpDate, zone: store.state.settings.timezone); choosingDate = false } } }
            }.presentationDetents([.medium, .large])
        }
    }
    private var periodBar: some View {
        HStack(spacing: 8) {
            Button { shift(-1) } label: { Image(systemName: "chevron.left").frame(width: 36, height: 36) }.accessibilityLabel(mode == "month" ? "上个月" : "上一周").accessibilityIdentifier("timetable-previous")
            Button {
                jumpDate = (try? SchoolTime.instant(date: date, minute: 720, zone: store.state.settings.timezone)) ?? store.now; choosingDate = true
            } label: { Text(periodTitle).font(.subheadline.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.8).accessibilityIdentifier("timetable-period-label") }
                .frame(maxWidth: .infinity)
            if selected != "" && date != SchoolTime.dateString(store.now, zone: store.state.settings.timezone) { Button("今天") { selected = "" }.font(.caption) }
            Button { shift(1) } label: { Image(systemName: "chevron.right").frame(width: 36, height: 36) }.accessibilityLabel(mode == "month" ? "下个月" : "下一周").accessibilityIdentifier("timetable-next")
        }.buttonStyle(.plain).tint(ClassingPalette.accent).padding(.horizontal, 12).padding(.bottom, 4)
    }
    private var periodTitle: String {
        if mode == "month" { return "\(date.prefix(4)) 年 \(Int(date.dropFirst(5).prefix(2)) ?? 1) 月" }
        guard let first = weekDates.first, let last = weekDates.last else { return date }
        let week = (try? SchoolTime.weekIndex(first, settings: store.state.settings)) ?? 0
        return "\(first.suffix(5)) — \(last.suffix(5))" + (week > 0 ? " · 第 \(week) 周" : "")
    }
    private func shift(_ direction: Int) {
        guard let day = try? SchoolTime.day(date) else { return }
        let changed = mode == "month" ? SchoolTime.calendar().date(byAdding: .month, value: direction, to: day)! : SchoolTime.addDays(day, direction * 7)
        selected = SchoolTime.dateString(changed, zone: "UTC")
    }
    private var courseList: some View {
        List {
            let lessons = store.state.lessons.filter { search.isEmpty || [$0.title, $0.location ?? "", $0.teacher ?? ""].contains { $0.localizedCaseInsensitiveContains(search) } }
            ForEach(lessons) { lesson in
                NavigationLink { LessonEditor(lesson: lesson) } label: {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(lesson.title).font(.headline)
                        Text("周\(lesson.dayOfWeek) \(SchoolTime.clock(lesson.startMinute))–\(SchoolTime.clock(lesson.endMinute)) · \(lesson.startWeek)–\(lesson.endWeek) 周").font(.caption).foregroundStyle(.secondary)
                    }.padding(.vertical, 5)
                }
            }
            if lessons.isEmpty { Text(search.isEmpty ? "暂无课程" : "没有匹配的课程").foregroundStyle(.secondary) }
        }.searchable(text: $search, prompt: "查找课程").scrollContentBackground(.hidden)
    }
    @ViewBuilder private var weekView: some View {
        let dates = weekDates.filter { store.state.settings["showWeekend"].bool || ((try? SchoolTime.day($0)).map { SchoolTime.weekday($0) <= 5 } ?? false) }
        let items = dates.first.flatMap { first in dates.last.map { store.occurrences(first, $0) } } ?? []
        if items.isEmpty { emptyWeek }
        else {
            let firstHour = min(8, items.map { $0.startMinute / 60 }.min() ?? 8)
            let lastHour = max(20, items.map { ($0.endMinute + 59) / 60 }.max() ?? 20)
            GeometryReader { geometry in
                let dayWidth = (geometry.size.width - 34) / CGFloat(max(dates.count, 1)), hourHeight: CGFloat = 58
                ScrollView(.vertical) {
                    LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
                        Section {
                            HStack(alignment: .top, spacing: 0) {
                                ZStack(alignment: .topTrailing) {
                                    ForEach(firstHour...lastHour, id: \.self) { hour in Text(String(format: "%02d", hour)).font(.caption2).foregroundStyle(.secondary).offset(y: CGFloat(hour - firstHour) * hourHeight - 5) }
                                }.frame(width: 34, height: CGFloat(lastHour - firstHour) * hourHeight, alignment: .topTrailing).padding(.trailing, 0)
                                ForEach(dates, id: \.self) { day in
                                    dayColumn(items.filter { $0.date == day }, firstHour: firstHour, lastHour: lastHour, width: dayWidth, hourHeight: hourHeight)
                                }
                            }.padding(.top, 8)
                        } header: {
                            HStack(spacing: 0) {
                                Color.clear.frame(width: 34, height: 40)
                                ForEach(dates, id: \.self) { day in
                                    VStack(spacing: 3) { Text(weekday(day)).font(.caption2); Text(String(Int(day.suffix(2)) ?? 1)).font(.subheadline.weight(.semibold)) }
                                        .foregroundStyle(day == SchoolTime.dateString(store.now, zone: store.state.settings.timezone) ? ClassingPalette.accent : .primary)
                                        .frame(width: dayWidth, height: 40)
                                }
                            }.background(.regularMaterial)
                        }
                    }.padding(.bottom, 20)
                }.accessibilityIdentifier("timetable-week")
            }
        }
    }
    private func dayColumn(_ items: [Occurrence], firstHour: Int, lastHour: Int, width: CGFloat, hourHeight: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(firstHour...lastHour, id: \.self) { hour in Divider().offset(y: CGFloat(hour - firstHour) * hourHeight) }
            ForEach(placements(items)) { position in
                let item = position.item, cardWidth = width / CGFloat(position.columns)
                NavigationLink { OccurrenceDetailView(occurrence: item) } label: {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.title).font(.system(size: width > 70 ? 12 : 10, weight: .semibold)).lineLimit(3)
                        if width > 55, let room = item.location, !room.isEmpty { Text(room).font(.system(size: 9)).lineLimit(2) }
                        Spacer(minLength: 0)
                    }.padding(4).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                        .foregroundStyle(ClassingPalette.course(item.title))
                        .background(ClassingPalette.course(item.title).opacity(0.13), in: RoundedRectangle(cornerRadius: 6))
                }.buttonStyle(.plain).frame(width: max(1, cardWidth - 2), height: max(24, CGFloat(item.endMinute - item.startMinute) / 60 * hourHeight - 2))
                    .offset(x: CGFloat(position.lane) * cardWidth + 1, y: CGFloat(item.startMinute - firstHour * 60) / 60 * hourHeight)
                    .accessibilityLabel("\(item.title)，\(SchoolTime.clock(item.startMinute))至\(SchoolTime.clock(item.endMinute))，\(item.location ?? "")")
                    .accessibilityIdentifier("timetable-week-course")
            }
        }.frame(width: width, height: CGFloat(lastHour - firstHour) * hourHeight).overlay(alignment: .leading) { Rectangle().fill(.secondary.opacity(0.1)).frame(width: 0.5) }
    }
    private var emptyWeek: some View {
        ContentUnavailableView {
            Label("本周暂无课程", systemImage: "calendar")
        } actions: {
            if let next = store.upcoming.first { Button("最近有课周") { selected = next.date } }
            Button("课程列表") { mode = "list" }
            NavigationLink("学期设置") { SemesterSettingsView() }
        }
    }
    private var monthView: some View {
        let dates = (try? SchoolTime.monthDates(containing: date, weekStart: store.state.settings.weekStart)) ?? []
        let items = dates.first.flatMap { first in dates.last.map { store.occurrences(first, $0) } } ?? []
        let grouped = Dictionary(grouping: items, by: \.date)
        return ScrollView {
            VStack(spacing: 12) {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 7), spacing: 3) {
                    ForEach(0..<7, id: \.self) { index in Text(["一", "二", "三", "四", "五", "六", "日"][(store.state.settings.weekStart - 1 + index) % 7]).font(.caption).foregroundStyle(.secondary) }
                    ForEach(dates, id: \.self) { day in
                        Button { selected = day } label: {
                            VStack(spacing: 3) {
                                Text(String(Int(day.suffix(2)) ?? 1)).font(.subheadline.weight(day == date ? .bold : .regular))
                                Circle().fill((grouped[day] ?? []).isEmpty ? .clear : ClassingPalette.accent).frame(width: 4, height: 4)
                            }.frame(maxWidth: .infinity, minHeight: 34)
                                .foregroundStyle(day == date ? Color.white : day.prefix(7) == date.prefix(7) ? Color.primary : Color.secondary)
                                .background(day == date ? ClassingPalette.accent : .clear, in: RoundedRectangle(cornerRadius: 10))
                        }.buttonStyle(.plain).accessibilityLabel("\(day)，\(grouped[day]?.count ?? 0) 节课")
                    }
                }.padding(.horizontal, 16).accessibilityIdentifier("month-calendar")
                Divider().padding(.horizontal, 16)
                HStack { Text(date).font(.subheadline.weight(.semibold)); Spacer(); Text("\(grouped[date]?.count ?? 0) 节课").font(.caption).foregroundStyle(.secondary) }.padding(.horizontal, 20)
                if (grouped[date] ?? []).isEmpty { Text("当天暂无课程").foregroundStyle(.secondary).padding(24) }
                ForEach(grouped[date] ?? []) { occurrence in
                    NavigationLink { OccurrenceDetailView(occurrence: occurrence) } label: { CourseRow(occurrence: occurrence) }.buttonStyle(.plain).padding(.horizontal, 16).accessibilityIdentifier("timetable-day-course")
                }
            }.padding(.bottom, 24)
        }
    }
    private func weekday(_ date: String) -> String { guard let day = try? SchoolTime.day(date) else { return "" }; return "周" + ["一", "二", "三", "四", "五", "六", "日"][SchoolTime.weekday(day) - 1] }
    private struct Placement: Identifiable { var item: Occurrence; var lane: Int; var columns: Int; var id: String { item.id } }
    private func placements(_ items: [Occurrence]) -> [Placement] {
        var groups: [[Occurrence]] = [], end = -1
        for item in items.sorted(by: { $0.startMinute < $1.startMinute }) {
            if groups.isEmpty || item.startMinute >= end { groups.append([item]); end = item.endMinute }
            else { groups[groups.count - 1].append(item); end = max(end, item.endMinute) }
        }
        return groups.flatMap { group -> [Placement] in
            var ends: [Int] = [], result: [Placement] = []
            for item in group {
                let lane = ends.firstIndex(where: { $0 <= item.startMinute }) ?? ends.count
                if lane == ends.count { ends.append(item.endMinute) } else { ends[lane] = item.endMinute }
                result.append(Placement(item: item, lane: lane, columns: 1))
            }
            return result.map { var value = $0; value.columns = ends.count; return value }
        }
    }
}
