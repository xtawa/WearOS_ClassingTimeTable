import SwiftUI
import ClassingCore

enum ClassingPalette {
    static let accent = Color(red: 0.24, green: 0.37, blue: 0.84)
    static func course(_ title: String, dark: Bool = false) -> Color {
        let index = title.unicodeScalars.reduce(0) { ($0 + Int($1.value)) % 6 }
        let light: [Color] = [Color(red: 0.31, green: 0.44, blue: 0.77), Color(red: 0.46, green: 0.36, blue: 0.68), Color(red: 0.24, green: 0.49, blue: 0.38), Color(red: 0.61, green: 0.40, blue: 0.15), Color(red: 0.67, green: 0.34, blue: 0.39), Color(red: 0.36, green: 0.44, blue: 0.48)]
        let night: [Color] = [Color(red: 0.68, green: 0.75, blue: 1), Color(red: 0.80, green: 0.73, blue: 1), Color(red: 0.57, green: 0.84, blue: 0.70), Color(red: 0.95, green: 0.77, blue: 0.48), Color(red: 1, green: 0.70, blue: 0.73), Color(red: 0.72, green: 0.79, blue: 0.82)]
        return dark ? night[index] : light[index]
    }
}

struct AgendaStatusPill: View {
    var text: String
    var color: Color = ClassingPalette.accent
    var body: some View {
        HStack(spacing: 5) { Circle().fill(color).frame(width: 5, height: 5); Text(text).font(.caption.weight(.semibold)) }
            .foregroundStyle(color).padding(.horizontal, 9).padding(.vertical, 5).background(color.opacity(0.10), in: Capsule())
    }
}

struct AgendaHeroCard: View {
    @Environment(\.colorScheme) private var scheme
    var snapshot: AgendaSnapshot
    var compact = false
    private var item: AgendaItem? { snapshot.primary }
    private var tint: Color { ClassingPalette.course(item?.title ?? "Classing", dark: scheme == .dark) }
    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 10 : 20) {
            HStack {
                AgendaStatusPill(text: snapshot.status, color: tint)
                Spacer(minLength: 0)
                if item?.changed == true { Image(systemName: "arrow.triangle.swap").foregroundStyle(.orange).accessibilityLabel("已调课") }
            }
            if let item, snapshot.phase == .upcoming || snapshot.phase == .inProgress || snapshot.phase == .breakTime {
                VStack(alignment: .leading, spacing: 8) {
                    Text(item.title).font(.system(compact ? .title3 : .largeTitle, design: .rounded, weight: .bold)).lineLimit(2).minimumScaleFactor(0.8)
                    Label(item.location.isEmpty ? "未设置地点" : item.location, systemImage: "mappin.and.ellipse")
                        .font(compact ? .caption : .subheadline).foregroundStyle(.secondary).lineLimit(2)
                }
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text("\(snapshot.minutesRemaining)").font(.system(size: compact ? 36 : 58, weight: .light, design: .rounded)).monospacedDigit().contentTransition(.numericText())
                    Text("分钟").font(.caption).foregroundStyle(.secondary)
                    Spacer(minLength: 2)
                    VStack(alignment: .trailing, spacing: 4) {
                        Text(snapshot.countdownLabel).font(.caption).foregroundStyle(.secondary)
                        Text(item.start, style: .time).font(compact ? .caption : .headline).monospacedDigit()
                    }
                }
                if snapshot.phase == .inProgress {
                    ProgressView(value: snapshot.progress).tint(tint).accessibilityLabel("课程进度")
                } else if !compact {
                    HStack { Text(item.start, format: .dateTime.month().day().weekday(.abbreviated)); Spacer(); Text(item.end, style: .time) }
                        .font(.caption).foregroundStyle(.secondary)
                }
            } else {
                Image(systemName: snapshot.phase == .empty ? "calendar.badge.plus" : "leaf").font(.system(size: compact ? 24 : 36)).foregroundStyle(tint).padding(.top, 5)
                Text(snapshot.phase == .empty ? "让安排，一目了然。" : snapshot.phase == .finished ? "今天的课，上完了。" : "留一点时间，给自己。")
                    .font(compact ? .headline : .title2.bold())
                Text(snapshot.phase == .empty ? (snapshot.isExam ? "添加考试后，时间与地点会显示在这里。" : "导入或添加课程，手机与手表都能随时查看。") : "下一段安排之前，好好休息一下。")
                    .font(.caption).foregroundStyle(.secondary)
                if let item {
                    Divider()
                    HStack { Text("接下来 · \(item.title)").lineLimit(1); Spacer(); Text(item.start, format: .dateTime.month().day().hour().minute()) }.font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .padding(compact ? 14 : 24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(scheme == .dark ? Color(white: 0.10) : .white.opacity(0.92), in: RoundedRectangle(cornerRadius: compact ? 24 : 32))
        .background(tint.opacity(scheme == .dark ? 0.08 : 0.12), in: RoundedRectangle(cornerRadius: compact ? 24 : 32))
        .overlay(RoundedRectangle(cornerRadius: compact ? 24 : 32).stroke(tint.opacity(0.12), lineWidth: 1))
        .environment(\.timeZone, TimeZone(identifier: item?.timezone ?? snapshot.timezone) ?? .current)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("next-course")
    }
}

struct ClassingBackdrop: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        ZStack {
            (scheme == .dark ? Color(red: 0.055, green: 0.067, blue: 0.08) : Color(red: 0.96, green: 0.97, blue: 0.965))
            RadialGradient(colors: [ClassingPalette.accent.opacity(scheme == .dark ? 0.16 : 0.10), .clear], center: .topTrailing, startRadius: 0, endRadius: 420)
            RadialGradient(colors: [Color(red: 0.94, green: 0.77, blue: 0.71).opacity(scheme == .dark ? 0.06 : 0.13), .clear], center: .bottomLeading, startRadius: 0, endRadius: 360)
        }.ignoresSafeArea()
    }
}
