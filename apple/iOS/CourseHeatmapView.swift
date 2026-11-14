import SwiftUI
import ClassingCore
struct CourseHeatmapView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.colorScheme) var scheme
    @State private var date = Date()
    @State private var table = false
    private let light = [0x91A3ED, 0x6E85E3, 0x3E5ED7, 0x2345AA], dark = [0x4D5AA7, 0x6C7BC3, 0x8C9AD9, 0xB1BFF3]
    var cells: [HeatmapCell] {
        let selected = (try? SchoolTime.day(SchoolTime.dateString(date, zone: store.state.settings.timezone))) ?? date
        let start = SchoolTime.weekStart(selected, weekday: store.state.settings.weekStart)
        let items = store.occurrences(SchoolTime.dateString(start, zone: "UTC"), SchoolTime.dateString(SchoolTime.addDays(start, 6), zone: "UTC"))
        return HeatmapBuilder.cells(items.map { HeatmapLesson(dayOfWeek: SchoolTime.weekday((try? SchoolTime.day($0.date)) ?? $0.start), startMinute: $0.startMinute, endMinute: $0.endMinute) })
    }
    var body: some View {
        VStack(spacing: 16) {
            HStack { DatePicker("所选周", selection: $date, displayedComponents: .date); Toggle("列表", isOn: $table) }.padding(.horizontal)
            Text("每格为该星期、该时间段的课程数量。色彩越强，同一时段的课程越多；数字与列表提供相同信息。").font(.caption).foregroundStyle(.secondary).padding(.horizontal)
            if cells.isEmpty { ContentUnavailableView("该周没有课程", systemImage: "calendar") }
            else if table { List(cells.filter { $0.count > 0 }) { cell in HStack { Text("周\(cell.dayOfWeek) · \(SchoolTime.clock(cell.startMinute))–\(SchoolTime.clock(cell.endMinute))"); Spacer(); Text("\(cell.count) 节课").monospacedDigit() } } }
            else {
                ScrollView([.horizontal, .vertical]) {
                    let slots = Array(Set(cells.map(\.startMinute))).sorted()
                    Grid(horizontalSpacing: 2, verticalSpacing: 2) {
                        GridRow { Text("时段").font(.caption); ForEach(1...7, id: \.self) { Text("周\($0)").font(.caption).frame(width: 44) } }
                        ForEach(slots, id: \.self) { start in
                            let ends = Array(Set(cells.filter { $0.startMinute == start }.map(\.endMinute))).sorted()
                            ForEach(ends, id: \.self) { end in
                                GridRow {
                                    Text("\(SchoolTime.clock(start))\n\(SchoolTime.clock(end))").font(.caption2.monospacedDigit()).frame(width: 60)
                                    ForEach(1...7, id: \.self) { day in
                                        let count = cells.first { $0.dayOfWeek == day && $0.startMinute == start && $0.endMinute == end }?.count ?? 0
                                        VStack { RoundedRectangle(cornerRadius: 4).fill(color(count)).overlay { if count > 0 { Text("\(count)").font(.caption.bold()).foregroundStyle((scheme == .light && count >= 3) || (scheme == .dark && count == 1) ? Color.white : Color.black) } }.frame(width: 30, height: 30) }.frame(width: 44, height: 44).contentShape(Rectangle())
                                            .accessibilityLabel("星期\(day)，\(SchoolTime.clock(start))到\(SchoolTime.clock(end))，\(count) 节课程").help("\(count) 节课程")
                                    }
                                }
                            }
                        }
                    }.padding(16)
                }
                HStack { Text("数量").font(.caption); ForEach(1...4, id: \.self) { n in HStack(spacing: 4) { RoundedRectangle(cornerRadius: 3).fill(color(n)).frame(width: 12, height: 12); Text(n == 4 ? "≥4" : "\(n)").font(.caption) } } }.padding(.bottom, 12)
            }
        }.navigationTitle("课程密度").onAppear { date = store.now }
    }
    func color(_ count: Int) -> Color { guard count > 0 else { return Color.secondary.opacity(0.12) }; let value = (scheme == .dark ? dark : light)[min(4, count) - 1]; return Color(red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255) }
}
