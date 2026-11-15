import XCTest
@testable import ClassingCore

final class CompanionSyncTests: XCTestCase {
    private func phone() throws -> AppState {
        var state = AppState(); state.settings["semesterWeekStartDate"] = .string("2026-09-07"); state.settings["timezone"] = .string("Asia/Shanghai"); state.settings["academicMode"] = .string("EXAM")
        state.settings["syncScopes"] = .string(""); state.lessons = [Lesson(title: "来自手机的课程")]
        try CompanionSync.capture(&state, role: .phone, now: 100); return state
    }
    func testLocalTransferWorksWithAllCloudScopesDisabled() throws {
        let phone = try phone(); var watch = AppState(); watch.settings["syncScopes"] = .string("")
        try CompanionSync.capture(&watch, role: .watch, now: 200)
        try CompanionSync.apply(CompanionSync.outbound(phone, role: .phone), to: &watch, role: .watch)
        XCTAssertEqual(watch.lessons, phone.lessons); XCTAssertEqual(watch.settings.semesterStart, "2026-09-07"); XCTAssertEqual(watch.settings.timezone, "Asia/Shanghai")
        XCTAssertEqual(watch.settings["syncScopes"].string, ""); XCTAssertFalse(watch.settings["cloudSyncEnabled"].bool); XCTAssertTrue(watch.settings[CompanionSync.readyKey].bool)
    }
    func testCompanionPayloadDoesNotIncludeCloudConfigurationOrBootstrapFlag() throws {
        let state = try phone(), document = CompanionSync.outbound(state, role: .phone)
        XCTAssertNil(document.records["cloud.config"])
        XCTAssertFalse(document.records.values.flatMap { $0 }.contains { $0.id == CompanionSync.readyKey })
        XCTAssertTrue(CloudSync.outbound(state).records.isEmpty)
    }
    func testFreshWatchCannotOverwritePhoneWithNewerDefaultSettings() throws {
        var phone = try phone(), watch = AppState(); watch.counter = 10_000
        try CloudSync.capture(&watch, now: 200) // A legacy watch with newer local defaults.
        try CompanionSync.apply(CompanionSync.outbound(watch, role: .watch), to: &phone, role: .phone)
        XCTAssertEqual(phone.settings.semesterStart, "2026-09-07"); XCTAssertEqual(phone.settings["academicMode"].string, "EXAM"); XCTAssertEqual(phone.lessons.count, 1)
    }
    func testPhoneSchoolSettingsReplaceLegacyWatchDefaultsDespiteTheirCounter() throws {
        let phone = try phone(); var watch = AppState(); watch.counter = 10_000; try CloudSync.capture(&watch, now: 200)
        try CompanionSync.apply(CompanionSync.outbound(phone, role: .phone), to: &watch, role: .watch)
        XCTAssertEqual(watch.settings.semesterStart, phone.settings.semesterStart); XCTAssertEqual(watch.settings.timezone, phone.settings.timezone); XCTAssertEqual(watch.settings["academicMode"], phone.settings["academicMode"])
    }
    func testWatchCourseEditsAndTombstonesReturnToPhoneWithCloudDisabled() throws {
        var phone = try phone(), watch = AppState(); watch.settings["syncScopes"] = .string("")
        try CompanionSync.apply(CompanionSync.outbound(phone, role: .phone), to: &watch, role: .watch)
        watch.lessons[0].title = "手表修改"; try CompanionSync.capture(&watch, role: .watch, now: 200)
        try CompanionSync.apply(CompanionSync.outbound(watch, role: .watch), to: &phone, role: .phone)
        XCTAssertEqual(phone.lessons.first?.title, "手表修改")
        watch.lessons = []; try CompanionSync.capture(&watch, role: .watch, now: 300)
        try CompanionSync.apply(CompanionSync.outbound(watch, role: .watch), to: &phone, role: .phone)
        XCTAssertTrue(phone.lessons.isEmpty)
    }
    func testInvalidIncomingDocumentLeavesExistingStateUntouched() throws {
        let phone = try phone(); var watch = AppState(); try CloudSync.capture(&watch)
        let previous = watch; var invalid = CompanionSync.outbound(phone, role: .phone); invalid.format = "invalid"
        XCTAssertThrowsError(try CompanionSync.apply(invalid, to: &watch, role: .watch))
        XCTAssertEqual(watch.cloud, previous.cloud); XCTAssertEqual(watch.settings, previous.settings); XCTAssertEqual(watch.lessons, previous.lessons)
    }
}
