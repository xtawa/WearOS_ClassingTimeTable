#if os(iOS)
import ActivityKit
import WidgetKit
import SwiftUI

struct CourseLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CourseActivityAttributes.self) { context in
            CourseLiveActivityCard(state: context.state, stale: context.isStale)
                .activityBackgroundTint(Color(white: 0.08))
                .activitySystemActionForegroundColor(.white)
                .foregroundStyle(.white)
                .widgetURL(URL(string: "classing://today"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.state.isExam ? "考试" : "课程", systemImage: context.state.isExam ? "graduationcap" : "calendar").font(.caption).foregroundStyle(.indigo)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if !context.isStale { Text(timerInterval: context.state.reference...context.state.target, countsDown: true).monospacedDigit().frame(width: 90) }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(context.state.title).font(.headline).lineLimit(1)
                        Label(context.isStale ? "打开课表查看最新安排" : context.state.location, systemImage: "mappin.and.ellipse").font(.caption).foregroundStyle(.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
            } compactLeading: {
                Image(systemName: context.state.isExam ? "graduationcap.fill" : "calendar").foregroundStyle(.indigo)
            } compactTrailing: {
                if context.isStale { Image(systemName: "arrow.clockwise") }
                else { Text(timerInterval: context.state.reference...context.state.target, countsDown: true).monospacedDigit().frame(width: 48) }
            } minimal: {
                Image(systemName: context.state.isExam ? "graduationcap.fill" : "calendar")
            }.widgetURL(URL(string: "classing://today")).keylineTint(.indigo)
        }
    }
}
#endif
