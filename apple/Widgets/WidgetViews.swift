import SwiftUI
import ClassingCore

enum AgendaWidgetSize { case small, medium, large, inline, circular, rectangular, corner }

/// Also used by the in-app gallery to preview the actual widget layouts.
struct AgendaWidgetCard: View {
    @Environment(\.colorScheme) private var scheme
    var snapshot: AgendaSnapshot
    var size: AgendaWidgetSize
    var agenda = false
    var liveTimer = true
    private var item: AgendaItem? { snapshot.primary }
    private var tint: Color { ClassingPalette.course(item?.title ?? "Classing", dark: scheme == .dark) }
    private var title: String { item?.title ?? (snapshot.isExam ? "暂无考试" : "享受自由时间") }
    private var status: String {
        guard let item else { return snapshot.status }
        return item.start <= snapshot.date ? (snapshot.isExam ? "考试进行中" : "正在上课") : (snapshot.isExam ? "下一场考试" : "下一节课")
    }
    var body: some View {
        Group {
            switch size {
            case .inline:
                HStack(spacing: 3) { Image(systemName: snapshot.isExam ? "graduationcap" : "calendar"); if let item { Text(item.start, style: .time) }; Text(title) }
            case .circular:
                Gauge(value: snapshot.progress) { Image(systemName: snapshot.isExam ? "graduationcap" : "calendar") } currentValueLabel: {
                    if item != nil { Text(snapshot.minutesRemaining >= 1440 ? "\(snapshot.minutesRemaining / 1440)天" : "\(snapshot.minutesRemaining)").font(.system(.title3, design: .rounded, weight: .semibold)).minimumScaleFactor(0.6) }
                    else { Image(systemName: "leaf") }
                }.gaugeStyle(.accessoryCircular).tint(tint).accessibilityLabel("\(title)，\(snapshot.countdownLabel)\(snapshot.minutesRemaining)分钟")
            case .rectangular:
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 2).fill(tint).frame(width: 3)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.headline).lineLimit(1)
                        Text(item?.location.isEmpty == false ? item!.location : snapshot.status).font(.caption).lineLimit(1)
                        if let item { HStack(spacing: 4) { Text(snapshot.countdownLabel); countdownText(item) }.font(.caption2.monospacedDigit()) }
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
            case .corner: Text(title).font(.caption).lineLimit(1)
            case .small: small
            case .medium: medium
            case .large: large
            }
        }.environment(\.timeZone, TimeZone(identifier: item?.timezone ?? snapshot.timezone) ?? .current)
    }
    private var heading: some View {
        HStack {
            Label(agenda ? "今日安排" : status, systemImage: snapshot.isExam ? "graduationcap" : "calendar").font(.caption.weight(.medium)).foregroundStyle(tint).lineLimit(1)
            Spacer(minLength: 2)
            if agenda { Text("\(snapshot.remainingCount) 待办").font(.caption2).foregroundStyle(.secondary) }
            else { Image(systemName: "sparkle").font(.caption).foregroundStyle(tint) }
        }
    }
    private var small: some View {
        VStack(alignment: .leading, spacing: 8) {
            heading
            Text(title).font(.system(.title3, design: .rounded, weight: .bold)).lineLimit(2).minimumScaleFactor(0.8)
            Text(item?.location.isEmpty == false ? item!.location : snapshot.status).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            Spacer(minLength: 0)
            if let item {
                HStack(alignment: .firstTextBaseline) {
                    Text(item.start, style: .time).font(.system(.title2, design: .rounded, weight: .medium)).monospacedDigit()
                    Spacer(minLength: 2)
                    if snapshot.minutesRemaining < 1440 { Text("\(snapshot.minutesRemaining) 分钟").font(.caption2).foregroundStyle(tint) }
                    else { Text(item.start, format: .dateTime.month().day()).font(.caption2).foregroundStyle(.secondary) }
                }
                if snapshot.phase == .inProgress { ProgressView(value: snapshot.progress).tint(tint) }
            } else { Label("打开 Classing", systemImage: "arrow.up.right").font(.caption2).foregroundStyle(tint) }
        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
    private var medium: some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                heading
                Text(title).font(.system(.title3, design: .rounded, weight: .bold)).lineLimit(2).minimumScaleFactor(0.8)
                Text(item?.location.isEmpty == false ? item!.location : snapshot.status).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Spacer(minLength: 0)
                if let item { HStack(spacing: 5) { Text(snapshot.countdownLabel); countdownText(item) }.font(.caption.monospacedDigit()).foregroundStyle(tint) }
            }.frame(maxWidth: .infinity, alignment: .leading)
            Rectangle().fill(tint.opacity(0.15)).frame(width: 1)
            VStack(alignment: .leading, spacing: 10) {
                Text("接下来").font(.caption2).foregroundStyle(.secondary)
                if snapshot.upcoming.dropFirst().isEmpty { Image(systemName: "leaf").font(.title3).foregroundStyle(tint); Text("留白也是安排").font(.caption).foregroundStyle(.secondary) }
                ForEach(Array(snapshot.upcoming.dropFirst().prefix(2))) { row($0, compact: true) }
            }.frame(maxWidth: .infinity, alignment: .leading)
        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
    private var large: some View {
        VStack(alignment: .leading, spacing: 12) {
            heading
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(title).font(.system(.title2, design: .rounded, weight: .bold)).lineLimit(2)
                    Text(item?.location.isEmpty == false ? item!.location : snapshot.status).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                if let item { VStack(alignment: .trailing, spacing: 3) { Text(item.start, style: .time).font(.title3.monospacedDigit()); Text(status).font(.caption2).foregroundStyle(tint) } }
            }
            if snapshot.phase == .inProgress { ProgressView(value: snapshot.progress).tint(tint) }
            Divider()
            let items = agenda ? Array(snapshot.today.filter { $0.end > snapshot.date }.prefix(4)) : Array(snapshot.upcoming.dropFirst().prefix(3))
            if items.isEmpty { Label("剩下的时间，留给自己。", systemImage: "leaf").font(.subheadline).foregroundStyle(.secondary).padding(.vertical, 12) }
            ForEach(items) { row($0, compact: false) }
            Spacer(minLength: 0)
            HStack { Text(snapshot.date, format: .dateTime.month().day().weekday(.wide)); Spacer(); Text("Classing").fontWeight(.semibold) }.font(.caption2).foregroundStyle(.secondary)
        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
    private func row(_ item: AgendaItem, compact: Bool) -> some View {
        HStack(alignment: .top, spacing: 8) {
            RoundedRectangle(cornerRadius: 2).fill(ClassingPalette.course(item.title, dark: scheme == .dark)).frame(width: 3)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title).font(compact ? .caption.weight(.semibold) : .subheadline.weight(.semibold)).lineLimit(1)
                HStack(spacing: 4) { Text(item.start, style: .time); if !compact { Text(item.location) } }.font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
        }.fixedSize(horizontal: false, vertical: true).environment(\.timeZone, TimeZone(identifier: item.timezone) ?? .current)
    }
    @ViewBuilder private func countdownText(_ item: AgendaItem) -> some View {
        if !liveTimer { Text("\(snapshot.minutesRemaining) 分钟") }
        else if item.start <= snapshot.date { Text(timerInterval: snapshot.date...item.end, countsDown: true) }
        else if snapshot.minutesRemaining < 1440 { Text(timerInterval: snapshot.date...item.start, countsDown: true) }
        else { Text(item.start, format: .dateTime.month().day()) }
    }
}
