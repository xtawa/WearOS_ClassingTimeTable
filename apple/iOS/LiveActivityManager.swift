import ActivityKit
import ClassingCore
import UIKit

@MainActor enum LiveActivityManager {
    static func update(_ snapshot: AgendaSnapshot, enabled: Bool) async throws {
        let activities = Activity<CourseActivityAttributes>.activities
        guard enabled, ActivityAuthorizationInfo().areActivitiesEnabled,
              let item = snapshot.primary, snapshot.today.contains(where: { $0.id == item.id }) else {
            for activity in activities { await activity.end(nil, dismissalPolicy: .immediate) }
            return
        }
        let state = CourseActivityAttributes.ContentState(title: item.title, location: item.location, start: item.start, end: item.end, reference: snapshot.date, timezone: item.timezone, isExam: item.isExam)
        let content = ActivityContent(state: state, staleDate: state.target, relevanceScore: 1)
        var existing: Activity<CourseActivityAttributes>?
        for activity in activities {
            if activity.attributes.itemID == item.id { existing = activity }
            else { await activity.end(nil, dismissalPolicy: .immediate) }
        }
        if let existing { await existing.update(content) }
        else if UIApplication.shared.applicationState == .active {
            _ = try Activity.request(attributes: CourseActivityAttributes(itemID: item.id), content: content, pushType: nil)
        }
    }
}
