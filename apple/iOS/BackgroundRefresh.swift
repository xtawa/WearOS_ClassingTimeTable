import SwiftUI
import BackgroundTasks
import ClassingCore
@MainActor enum BackgroundRefresh {
    static let identifier = "com.xtawa.classingtime.apple.refresh"
    static func register(store: AppStore) {
        guard !store.demo else { return }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            let work = Task { @MainActor in if store.state.settings["cloudSyncEnabled"].bool { await store.sync() }; do { try Task.checkCancellation(); try await LocalNotifications.rebuild(store.state); refresh.setTaskCompleted(success: true) } catch { refresh.setTaskCompleted(success: false) }; schedule() }
            refresh.expirationHandler = { work.cancel() }
        }
    }
    static func schedule() { let request = BGAppRefreshTaskRequest(identifier: identifier); request.earliestBeginDate = Date().addingTimeInterval(1800); try? BGTaskScheduler.shared.submit(request) }
}
