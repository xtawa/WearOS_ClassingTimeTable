import XCTest
@testable import ClassingCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
final class IntegrationContractTests: XCTestCase {
    func testCaptureIsStableWithoutLocalChanges() throws { var s = AppState(); try CloudSync.capture(&s, now: 10); let baseline = s.cloud; try CloudSync.capture(&s, now: 20); XCTAssertEqual(s.cloud, baseline) }
    func testNaturalWeekMatchesAndroidFourDayRule() throws { var settings = Settings(); settings["weekNumberMode"] = .string("NATURAL"); settings["weekStartDay"] = .string("MONDAY"); XCTAssertEqual(try SchoolTime.weekIndex("2026-01-01", settings: settings), 1); XCTAssertEqual(try SchoolTime.weekIndex("2027-01-01", settings: settings), 53) }
    func testAndroidMakeupWireDecodes() throws { let raw = Data("{\"id\":\"makeup\",\"type\":\"MAKE_UP\",\"date\":\"2026-11-16\",\"title\":\"补课\",\"startMinute\":600,\"endMinute\":660}".utf8); let e = try Wire.decoder.decode(ScheduleException.self, from: raw); try e.validate(); XCTAssertEqual(e.type, "MAKE_UP") }
    func testMovedDestinationOutsideBaseWeekday() throws {
        var s = AppState(); s.settings["timezone"] = .string("Asia/Shanghai"); s.settings["semesterWeekStartDate"] = .string("2026-09-07")
        var l = Lesson(title: "数学"); l.id = "math"; s.lessons = [l]
        var destination = ScheduleException(type: "RESCHEDULE", date: "2026-09-08", lessonId: l.id); destination.startMinute = 600; destination.endMinute = 660
        s.exceptions = [ScheduleException(type: "CANCEL", date: "2026-09-07", lessonId: l.id), destination]
        let items = try ScheduleProjector.project(s, from: "2026-09-07", through: "2026-09-08"); XCTAssertEqual(items.count, 1); XCTAssertEqual(items[0].date, "2026-09-08")
    }
    func testHolidaySerializesAsPortableCancellations() throws {
        var s = AppState(); var l = Lesson(title: "数学"); l.id = "math"; s.lessons = [l]; s.exceptions = [ScheduleException(type: "HOLIDAY", date: s.settings.semesterStart)]
        try CloudSync.capture(&s); let raw = s.cloud.records["timetable.exceptions"]![0].payload!; let e = try Wire.decoder.decode(ScheduleException.self, from: Data(raw.utf8)); XCTAssertEqual(e.type, "CANCEL"); XCTAssertEqual(e.lessonId, "math")
    }
    func testCredentialCannotEnterBackupSettings() {
        var s = AppState(); s.settings["apiKey"] = .string("not-a-real-key"); XCTAssertThrowsError(try s.validate())
        s.settings.values.removeValue(forKey: "apiKey"); s.settings["byokConfig"] = .object(["endpoint": .string("https://example.invalid/v1"), "key": .string("not-a-real-key")]); XCTAssertThrowsError(try s.validate())
    }
    func testRefreshSingleFlightUnderConcurrency() async throws {
        let vault = MemoryTokenVault(), transport = RefreshTransport()
        let old = try AuthSession(.object(["accessToken": .string("old"), "refreshToken": .string("refresh-old"), "accessExpiresAt": .int(1)])); await vault.write(old)
        let api = ClassingAPI(baseURL: URL(string: "https://example.invalid")!, vault: vault, platform: "IOS", bundleID: "example", transport: transport)
        try await withThrowingTaskGroup(of: Void.self) { group in for _ in 0..<20 { group.addTask { _ = try await api.request("GET", "/api/v1/account/me") } }; try await group.waitForAll() }
        let count = await transport.refreshes; XCTAssertEqual(count, 1); let session = await vault.read(); XCTAssertEqual(session?.refreshToken, "refresh-new")
    }
    func testLogoutDuringRefreshCannotRestoreSession() async throws {
        let vault = MemoryTokenVault(), transport = RefreshTransport()
        await vault.write(try AuthSession(.object(["accessToken": .string("old"), "refreshToken": .string("refresh-old"), "accessExpiresAt": .int(1)])))
        let api = ClassingAPI(baseURL: URL(string: "https://example.invalid")!, vault: vault, platform: "WATCHOS", bundleID: "example", transport: transport)
        let task = Task { _ = try await api.request("GET", "/api/v1/account/me") }
        try await Task.sleep(nanoseconds: 5_000_000); try await api.setSession(nil); _ = await task.result
        let session = await vault.read(); XCTAssertNil(session)
    }
}
private actor RefreshTransport: HTTPTransport {
    var refreshes = 0
    func send(_ request: URLRequest, limit: Int) async throws -> HTTPResponse {
        if request.url?.path == "/api/v1/auth/refresh" {
            refreshes += 1; try await Task.sleep(nanoseconds: 30_000_000)
            let body = JSONValue.object(["session": .object(["accessToken": .string("new"), "refreshToken": .string("refresh-new"), "accessExpiresAt": .int(Wire.milliseconds() + 3_600_000), "refreshExpiresAt": .int(Wire.milliseconds() + 86_400_000)])])
            return HTTPResponse(status: 200, data: try body.data())
        }
        return HTTPResponse(status: 200, data: Data("{}".utf8))
    }
    func stream(_ request: URLRequest, onEvent: @escaping @Sendable (SSEEvent) async throws -> Void) async throws {}
}

extension IntegrationContractTests {
    func testUploadPreservesDisabledRemoteDomains() throws {
        var state = AppState(); state.settings["syncScopes"] = .string("MOBILE_SETTINGS"); try CloudSync.capture(&state)
        var remote = CloudDocument(); let row = VersionedRecord(id: "remote-course", payload: "remote-only", version: LogicalVersion(counter: 99, deviceId: "remote", changedAt: 1)); remote.records["timetable.lessons"] = [row]
        let upload = try CloudSync.upload(state, preserving: remote); XCTAssertEqual(upload.records["timetable.lessons"], [row]); XCTAssertNil(CloudSync.outbound(state).records["timetable.lessons"])
    }
    func testSettingsOnlyApplyDoesNotImportOfficialTimetable() throws {
        var state = AppState(), remote = CloudDocument(); var lesson = Lesson(title: "官方云课程"); lesson.id = "server"
        remote.records["timetable.lessons"] = [VersionedRecord(id: lesson.id, payload: String(decoding: try Wire.encoder.encode(lesson), as: UTF8.self), version: LogicalVersion(counter: 100, deviceId: "server", changedAt: 1))]
        try CloudSync.apply(remote, to: &state, domains: ["mobile.settings", "wear.settings", "cloud.config"]); XCTAssertTrue(state.lessons.isEmpty)
    }
    func testWatchSettingsAreSeparateAndAndroidFieldsSurvive() throws {
        var state = AppState(); let android = VersionedRecord(id: "androidOnly", payload: "{\"value\":true}", version: LogicalVersion(counter: 1, deviceId: "android", changedAt: 1)); state.cloud.records["wear.settings"] = [android]
        state.settings["watchShowTeacher"] = .bool(true); try CloudSync.capture(&state)
        XCTAssertTrue(state.cloud.records["wear.settings"]!.contains { $0.id == "watchShowTeacher" && $0.deletedAt == nil }); XCTAssertEqual(state.cloud.records["wear.settings"]!.first { $0.id == "androidOnly" }, android)
    }
    func testHeatmapKeepsExactSlotOrderingAndEmptyCells() {
        let cells = HeatmapBuilder.cells([HeatmapLesson(dayOfWeek: 1, startMinute: 600, endMinute: 660), HeatmapLesson(dayOfWeek: 1, startMinute: 600, endMinute: 660), HeatmapLesson(dayOfWeek: 2, startMinute: 480, endMinute: 570)])
        XCTAssertEqual(cells.count, 14); XCTAssertEqual(cells[0].startMinute, 480); XCTAssertEqual(cells.first { $0.dayOfWeek == 1 && $0.startMinute == 600 }?.count, 2); XCTAssertEqual(cells.first { $0.dayOfWeek == 7 }?.count, 0)
    }
    func testUpcomingFindsFarFutureDateRule() throws {
        var state = AppState(); state.settings["timezone"] = .string("UTC"); let today = "2026-01-01", later = "2026-05-01"
        var lesson = Lesson(title: "四个月后的课程"), rule = ScheduleRule(anchorDate: today, endDate: later); rule.kind = "DATES"; rule.dates = [later]; lesson.scheduleRuleJson = String(decoding: try Wire.encoder.encode(rule), as: UTF8.self); state.lessons = [lesson]
        let result = try ScheduleProjector.upcoming(state, now: SchoolTime.instant(date: today, minute: 0, zone: "UTC")); XCTAssertEqual(result.first?.date, later)
    }
}
extension IntegrationContractTests {
    func testWearScopeWorksWhenMobileScopeIsDisabled() throws {
        var state = AppState(), remote = CloudDocument(); state.settings["syncScopes"] = .string("WEAR_SETTINGS")
        remote.records["wear.settings"] = [VersionedRecord(id: "watchShowTeacher", payload: "{\"value\":true}", version: LogicalVersion(counter: 100, deviceId: "watch", changedAt: 1))]
        try CloudSync.apply(remote, to: &state); XCTAssertTrue(state.settings["watchShowTeacher"].bool)
    }
}
extension IntegrationContractTests {
    func testIdenticalUploadIsNoopForRealtimeSync() throws { var state = AppState(); try CloudSync.capture(&state); let remote = state.cloud; XCTAssertEqual(try CloudSync.upload(state, preserving: remote), remote) }
}
extension IntegrationContractTests {
    func testAndroidNullKeysAndFieldOrderAreNotEdits() throws {
        var state = AppState(); var lesson = Lesson(title: "数学"); lesson.id = "math"; state.lessons = [lesson]
        let raw = "{\"id\":\"math\",\"title\":\"数学\",\"teacher\":null,\"location\":null,\"note\":null,\"dayOfWeek\":1,\"startMinute\":480,\"endMinute\":570,\"startWeek\":1,\"endWeek\":20,\"weekParity\":\"ALL\",\"scheduleRuleJson\":null}"
        let stamp = LogicalVersion(counter: 7, deviceId: "android", changedAt: 1)
        state.cloud.records["timetable.lessons"] = [VersionedRecord(id: "math", payload: raw, version: stamp)]
        try CloudSync.capture(&state); XCTAssertEqual(state.cloud.records["timetable.lessons"]?.first?.version, stamp); XCTAssertEqual(state.cloud.records["timetable.lessons"]?.first?.payload, raw)
        XCTAssertFalse(PayloadEquivalence.equal("{\"title\":\"旧名称\"}", "{\"title\":\"新名称\"}"))
    }
}
