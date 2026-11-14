import SwiftUI
import ClassingCore
struct LessonEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var lesson: Lesson
    @State private var startClock = "08:00", endClock = "09:30"
    @State private var ruleMode = "legacy", anchor = "", end = "", interval = 1, cycle = 2, cycleDays = "1", days = "", holidays = "", dates = "", advance = false, term = "", meeting = "", group = "", deleting = false
    var body: some View {
        Form {
            Section("课程信息") { TextField("课程名", text: $lesson.title); optional("教师", $lesson.teacher); optional("地点", $lesson.location); optional("备注", $lesson.note) }
            Section("时间") { Picker("星期", selection: $lesson.dayOfWeek) { ForEach(1...7, id: \.self) { Text("星期\($0)").tag($0) } }; TextField("开始 HH:mm", text: $startClock).keyboardType(.numbersAndPunctuation); TextField("结束 HH:mm", text: $endClock).keyboardType(.numbersAndPunctuation) }
            Section("重复方式") {
                Picker("规则", selection: $ruleMode) { Text("学期周次").tag("legacy"); Text("每 N 周").tag("WEEKLY"); Text("轮换教学日").tag("ROTATION"); Text("指定日期").tag("DATES") }
                if ruleMode == "legacy" { Stepper("开始：第 \(lesson.startWeek) 周", value: $lesson.startWeek, in: 1...53); Stepper("结束：第 \(lesson.endWeek) 周", value: $lesson.endWeek, in: 1...53); Picker("单双周", selection: $lesson.weekParity) { Text("每周").tag("ALL"); Text("单周").tag("ODD"); Text("双周").tag("EVEN") } }
                else {
                    TextField("起始日期 YYYY-MM-DD", text: $anchor); TextField("结束日期 YYYY-MM-DD", text: $end)
                    if ruleMode == "WEEKLY" { Stepper("每 \(interval) 周", value: $interval, in: 1...366); TextField("星期（1,3,5）", text: $days) }
                    if ruleMode == "ROTATION" { Stepper("轮换周期 \(cycle) 个教学日", value: $cycle, in: 1...30); TextField("本课对应周期日（1,2）", text: $cycleDays); TextField("教学日（1,2,3,4,5）", text: $days); Toggle("节假日也推进轮换", isOn: $advance) }
                    if ruleMode == "DATES" { TextField("日期，逗号分隔", text: $dates, axis: .vertical) }
                    TextField("节假日，逗号分隔", text: $holidays, axis: .vertical); TextField("学期名称", text: $term); TextField("课型（讲座 / 实验）", text: $meeting); TextField("课程分组 ID", text: $group)
                }
            }
            if store.state.lessons.contains(where: { $0.id == lesson.id }) { Section { Button("删除课程", role: .destructive) { deleting = true } } }
        }.navigationTitle("编辑课程").toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("保存") { save() } } }
        .confirmationDialog("删除这门课及其调课记录？", isPresented: $deleting, titleVisibility: .visible) { Button("删除", role: .destructive) { store.mutate("删除课程前") { $0.lessons.removeAll { $0.id == lesson.id }; $0.exceptions.removeAll { $0.lessonId == lesson.id } }; dismiss() } }
        .onAppear {
            startClock = SchoolTime.clock(lesson.startMinute); endClock = SchoolTime.clock(lesson.endMinute)
            anchor = store.state.settings.semesterStart; end = SchoolTime.dateString(SchoolTime.addDays((try? SchoolTime.day(anchor)) ?? Date(), 140), zone: "UTC")
            if let raw = lesson.scheduleRuleJson, let r = try? Wire.decoder.decode(ScheduleRule.self, from: Data(raw.utf8)) { ruleMode = r.kind; anchor = r.anchorDate; end = r.endDate; interval = r.interval; cycle = r.cycleLength; cycleDays = r.cycleDays.map(String.init).joined(separator: ","); days = (r.kind == "ROTATION" ? r.schoolDays : r.daysOfWeek).map(String.init).joined(separator: ","); holidays = r.holidays.joined(separator: ","); dates = r.dates.joined(separator: ","); advance = r.advanceOnHolidays; term = r.termName ?? ""; meeting = r.meetingType ?? ""; group = r.courseGroupId ?? "" }
        }
    }
    @ViewBuilder func optional(_ name: String, _ binding: Binding<String?>) -> some View { TextField(name, text: Binding(get: { binding.wrappedValue ?? "" }, set: { binding.wrappedValue = $0.isEmpty ? nil : $0 })) }
    func clock(_ name: String, _ binding: Binding<Int>) -> some View { TextField(name, text: Binding(get: { SchoolTime.clock(binding.wrappedValue) }, set: { if let value = try? SchoolTime.minute($0) { binding.wrappedValue = value } })).keyboardType(.numbersAndPunctuation) }
    func save() {
        do {
            lesson.startMinute = try SchoolTime.minute(startClock); lesson.endMinute = endClock == "24:00" ? 1440 : try SchoolTime.minute(endClock)
            if ruleMode == "legacy" { lesson.scheduleRuleJson = nil }
            else {
                var r = ScheduleRule(anchorDate: anchor, endDate: end); r.kind = ruleMode; r.interval = interval; r.cycleLength = cycle; r.cycleDays = try integers(cycleDays); let parsedDays = try integers(days)
                if ruleMode == "ROTATION" { r.schoolDays = parsedDays.isEmpty ? [1,2,3,4,5] : parsedDays } else { r.daysOfWeek = parsedDays }; r.holidays = strings(holidays); r.dates = strings(dates); r.advanceOnHolidays = advance; r.termName = term; r.meetingType = meeting; r.courseGroupId = group; r.weekStartDay = store.state.settings.weekStart
                try r.validate(); lesson.scheduleRuleJson = String(decoding: try Wire.encoder.encode(r), as: UTF8.self)
            }; try lesson.validate(); var state = store.state; state.backup("编辑课程前"); if let i = state.lessons.firstIndex(where: { $0.id == lesson.id }) { state.lessons[i] = lesson } else { state.lessons.append(lesson) }; try store.commit(state); dismiss()
        } catch { store.error = error.localizedDescription }
    }
    func strings(_ raw: String) -> [String] { raw.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }
    func integers(_ raw: String) throws -> [Int] { try strings(raw).map { guard let n = Int($0) else { throw ClassingError.invalid("星期和周期日必须为整数。") }; return n } }
}
struct ExamListView: View {
    @EnvironmentObject var store: AppStore
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if store.state.exams.isEmpty { ContentUnavailableView("还没有考试", systemImage: "graduationcap", description: Text("通过右上角添加考试，或请助手提出建议。")) }
            ForEach(store.state.exams.sorted { $0.startAt < $1.startAt }) { e in NavigationLink { ExamEditor(exam: e) } label: { VStack(alignment: .leading, spacing: 6) { HStack { Text(e.title).font(.headline); Spacer(); if e.completed { Image(systemName: "checkmark.circle.fill") } }; Text(e.start.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened, timeZone: TimeZone(identifier: e.timezone) ?? .current))).font(.subheadline); Text("\(e.location) · \(e.timezone)").font(.caption).foregroundStyle(.secondary); if e.end > store.now && !e.completed { Text(e.start <= store.now ? "考试进行中" : "距考试 \(e.start.formatted(.relative(presentation: .numeric)))").font(.caption).foregroundStyle(.indigo) } }.padding(18).frame(maxWidth: .infinity, alignment: .leading).classingGlass() }.buttonStyle(.plain) }
        }
    }
}
struct ExamEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var exam: Exam
    @State private var delete = false
    var body: some View {
        Form {
            TextField("考试名称", text: $exam.title)
            TextField("IANA 时区", text: $exam.timezone).textInputAutocapitalization(.never).autocorrectionDisabled()
            DatePicker("开始", selection: Binding(get: { exam.start }, set: { exam.startAt = Wire.milliseconds($0) })).environment(\.timeZone, TimeZone(identifier: exam.timezone) ?? .current)
            DatePicker("结束", selection: Binding(get: { exam.end }, set: { exam.endAt = Wire.milliseconds($0) })).environment(\.timeZone, TimeZone(identifier: exam.timezone) ?? .current)
            TextField("地点", text: $exam.location); TextField("备注", text: $exam.note, axis: .vertical)
            Stepper("提前 \(exam.reminderMinutes) 分钟提醒", value: $exam.reminderMinutes, in: 0...10080, step: 5)
            Toggle("已完成", isOn: $exam.completed)
            Picker("关联课程", selection: Binding(get: { exam.courseId ?? "" }, set: { exam.courseId = $0.isEmpty ? nil : $0 })) { Text("不关联").tag(""); ForEach(store.state.lessons) { Text($0.title).tag($0.id) } }
            if store.state.exams.contains(where: { $0.id == exam.id }) { Button("删除考试", role: .destructive) { delete = true } }
        }.navigationTitle("考试").toolbar { ToolbarItem(placement: .confirmationAction) { Button("保存") { do { try exam.validate(); var s = store.state; s.backup("编辑考试前"); if let i = s.exams.firstIndex(where: { $0.id == exam.id }) { s.exams[i] = exam } else { s.exams.append(exam) }; try store.commit(s); dismiss() } catch { store.error = error.localizedDescription } } } }
        .confirmationDialog("删除考试？", isPresented: $delete) { Button("删除", role: .destructive) { store.mutate("删除考试前") { $0.exams.removeAll { $0.id == exam.id } }; dismiss() } }
    }
}
struct ChangesView: View {
    @EnvironmentObject var store: AppStore
    var body: some View { List { ForEach(store.state.exceptions.sorted { $0.date > $1.date }) { e in NavigationLink { ChangeEditor(exception: e) } label: { VStack(alignment: .leading) { Text(e.title ?? store.state.lessons.first(where: { $0.id == e.lessonId })?.title ?? "全日放假"); Text("\(e.date) · \(e.type)").font(.caption).foregroundStyle(.secondary) } } } }.navigationTitle("调课与临时安排").toolbar { NavigationLink { ChangeEditor(exception: ScheduleException(type: "MAKE_UP", date: SchoolTime.dateString(store.now, zone: store.state.settings.timezone))) } label: { Image(systemName: "plus") } } }
}
struct ChangeEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var exception: ScheduleException
    @State private var startClock = "08:00", endClock = "09:30"
    var body: some View {
        Form {
            Picker("类型", selection: $exception.type) { Text("取消").tag("CANCEL"); Text("改时间 / 地点").tag("RESCHEDULE"); Text("补课").tag("MAKE_UP"); Text("放假").tag("HOLIDAY") }
            TextField("日期 YYYY-MM-DD", text: $exception.date)
            if exception.type != "MAKE_UP" { Picker("原课程", selection: Binding(get: { exception.lessonId ?? "" }, set: { exception.lessonId = $0.isEmpty ? nil : $0 })) { Text("全日（仅放假）").tag(""); ForEach(store.state.lessons) { Text($0.title).tag($0.id) } } }
            if exception.type == "MAKE_UP" { TextField("名称", text: Binding(get: { exception.title ?? "" }, set: { exception.title = $0 })) }
            if exception.type == "MAKE_UP" || exception.type == "RESCHEDULE" {
                TextField("开始 HH:mm", text: $startClock)
                TextField("结束 HH:mm", text: $endClock)
                TextField("地点", text: Binding(get: { exception.location ?? "" }, set: { exception.location = $0 })); TextField("教师", text: Binding(get: { exception.teacher ?? "" }, set: { exception.teacher = $0 }))
            }
            TextField("备注", text: Binding(get: { exception.note ?? "" }, set: { exception.note = $0 }), axis: .vertical)
            if store.state.exceptions.contains(where: { $0.id == exception.id }) { Button("删除临时安排", role: .destructive) { store.mutate("删除调课前") { $0.exceptions.removeAll { $0.id == exception.id } }; dismiss() } }
        }.navigationTitle("临时安排").toolbar { Button("保存") { do { if exception.type == "MAKE_UP" || exception.type == "RESCHEDULE" { exception.startMinute = try SchoolTime.minute(startClock); exception.endMinute = endClock == "24:00" ? 1440 : try SchoolTime.minute(endClock) }; try exception.validate(); store.save(exception); if store.error == nil { dismiss() } } catch { store.error = error.localizedDescription } } }.onAppear { if exception.startMinute == nil { let l = store.state.lessons.first { $0.id == exception.lessonId }; exception.startMinute = l?.startMinute ?? 480; exception.endMinute = l?.endMinute ?? 570 }; startClock = SchoolTime.clock(exception.startMinute ?? 480); endClock = SchoolTime.clock(exception.endMinute ?? 570) }
    }
}
