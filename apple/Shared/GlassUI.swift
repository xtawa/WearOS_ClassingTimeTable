import SwiftUI
import ClassingCore

struct GlassSurface: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @ViewBuilder func body(content: Content) -> some View {
        #if os(iOS)
        if #available(iOS 26.0, *), !reduceTransparency { content.glassEffect(.regular, in: .rect(cornerRadius: 24)) }
        else { content.background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24)) }
        #else
        content.background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
        #endif
    }
}
extension View { func classingGlass() -> some View { modifier(GlassSurface()) } }
struct ClassingBackdrop: View {
    var body: some View { LinearGradient(colors: [Color.accentColor.opacity(0.16), Color.purple.opacity(0.06), Color.clear], startPoint: .topLeading, endPoint: .bottomTrailing).ignoresSafeArea() }
}
struct CourseRow: View {
    @EnvironmentObject var store: AppStore
    var occurrence: Occurrence
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 3).fill(occurrence.isChanged ? Color.orange : Color.accentColor).frame(width: 4)
            VStack(alignment: .leading, spacing: 5) {
                Text(occurrence.title).font(.headline)
                Text("\(SchoolTime.clock(occurrence.startMinute))–\(SchoolTime.clock(occurrence.endMinute))").font(.subheadline.monospacedDigit())
                if let place = occurrence.location, !place.isEmpty { Label(place, systemImage: "mappin.and.ellipse").font(.caption).foregroundStyle(.secondary) }
                if let teacher = occurrence.teacher, !teacher.isEmpty {
                    #if os(watchOS)
                    if store.state.settings["watchShowTeacher"].bool { Text(teacher).font(.caption).foregroundStyle(.secondary) }
                    #else
                    Text(teacher).font(.caption).foregroundStyle(.secondary)
                    #endif
                }
                if occurrence.isChanged { Text(occurrence.source == "MAKE_UP" ? "补课" : "已调课").font(.caption).foregroundStyle(.orange) }
            }
            Spacer(minLength: 0)
        }.padding(14).classingGlass().accessibilityElement(children: .combine)
    }
}
struct NextCourseCard: View {
    var occurrence: Occurrence?, now: Date
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(occurrence.map { $0.start <= now ? "正在上课" : "下一节课" } ?? "空闲时间", systemImage: "sparkle").font(.caption).foregroundStyle(.secondary)
            if let o = occurrence {
                Text(o.title).font(.system(.title2, design: .rounded, weight: .bold))
                Label(o.location ?? "未设置地点", systemImage: "location").font(.subheadline)
                HStack { Text(SchoolTime.clock(o.startMinute)).font(.title3.monospacedDigit()); Spacer(); Text(o.start > now ? o.start : o.end, style: .relative).font(.caption.monospacedDigit()) }
            } else { Text("今天的节奏，由你决定。").font(.headline); Text("添加课程或导入课表，下一节课就会出现在这里。").font(.caption).foregroundStyle(.secondary) }
        }.padding(22).frame(maxWidth: .infinity, alignment: .leading).classingGlass().accessibilityIdentifier("next-course")
    }
}
