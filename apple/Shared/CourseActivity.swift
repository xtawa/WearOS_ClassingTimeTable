#if os(iOS)
import ActivityKit
import SwiftUI

struct CourseActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var title: String
        var location: String
        var start: Date
        var end: Date
        var reference: Date
        var timezone: String
        var isExam: Bool
        var target: Date { reference < start ? start : end }
        var status: String { reference < start ? (isExam ? "下一场考试" : "下一节课") : (isExam ? "考试进行中" : "正在上课") }
    }
    var itemID: String
}

struct CourseLiveActivityCard: View {
    var state: CourseActivityAttributes.ContentState
    var stale = false
    var liveTimer = true
    var body: some View {
        HStack(spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Label(stale ? "打开课表查看最新安排" : state.status, systemImage: state.isExam ? "graduationcap" : "calendar").font(.caption).foregroundStyle(.secondary)
                Text(state.title).font(.title3.bold()).lineLimit(1)
                Label(state.location.isEmpty ? "未设置地点" : state.location, systemImage: "mappin.and.ellipse").font(.caption).lineLimit(1)
            }
            Spacer(minLength: 0)
            if !stale {
                VStack(alignment: .trailing, spacing: 4) {
                    Group {
                        if liveTimer { Text(timerInterval: state.reference...state.target, countsDown: true) }
                        else { Text("\(max(0, Int(ceil(state.target.timeIntervalSince(state.reference) / 60)))) 分钟") }
                    }.font(.title2.monospacedDigit()).frame(maxWidth: 100)
                    Text(state.reference < state.start ? "距开始" : "距结束").font(.caption2).foregroundStyle(.secondary)
                }
            }
        }.padding(18).environment(\.timeZone, TimeZone(identifier: state.timezone) ?? .current)
    }
}
#endif
