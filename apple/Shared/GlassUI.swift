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
struct CourseRow: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.colorScheme) private var scheme
    var occurrence: Occurrence
    private var tint: Color { ClassingPalette.course(occurrence.title, dark: scheme == .dark) }
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 3).fill(occurrence.isChanged ? Color.orange : tint).frame(width: 3)
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
        }.padding(16)
            .background(tint.opacity(scheme == .dark ? 0.10 : 0.065), in: RoundedRectangle(cornerRadius: 22))
            .accessibilityElement(children: .combine)
    }
}
