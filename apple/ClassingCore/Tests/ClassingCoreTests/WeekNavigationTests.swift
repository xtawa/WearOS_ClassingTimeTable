import XCTest
@testable import ClassingCore

final class WeekNavigationTests: XCTestCase {
    func testSchoolTimezoneAndNextPreviousWeek() throws {
        var settings = Settings(); settings["timezone"] = .string("Asia/Shanghai"); settings["semesterWeekStartDate"] = .string("2026-09-07")
        let now = ISO8601DateFormatter().date(from: "2026-11-15T17:00:00Z")!
        let current = try SchoolTime.weekDates(containing: now, settings: settings)
        let next = try SchoolTime.weekDates(containing: now, offset: 1, settings: settings)
        let previous = try SchoolTime.weekDates(containing: now, offset: -1, settings: settings)
        XCTAssertEqual(current.first, "2026-11-16"); XCTAssertEqual(current.last, "2026-11-22")
        XCTAssertEqual(next.first, "2026-11-23"); XCTAssertEqual(previous.first, "2026-11-09")
        XCTAssertEqual(try SchoolTime.weekIndex(next[0], settings: settings), 12)
    }
    func testSundayWeekStartAcrossYearBoundary() throws {
        var settings = Settings(); settings["timezone"] = .string("UTC"); settings["weekStartDay"] = .string("SUNDAY")
        let dates = try SchoolTime.weekDates(containing: SchoolTime.day("2026-12-31"), offset: 1, settings: settings)
        XCTAssertEqual(dates, ["2027-01-03", "2027-01-04", "2027-01-05", "2027-01-06", "2027-01-07", "2027-01-08", "2027-01-09"])
    }
    func testDSTWeekStillContainsSevenDistinctSchoolDates() throws {
        var settings = Settings(); settings["timezone"] = .string("America/New_York")
        let dates = try SchoolTime.weekDates(containing: ISO8601DateFormatter().date(from: "2026-03-08T14:00:00Z")!, settings: settings)
        XCTAssertEqual(dates.first, "2026-03-02"); XCTAssertEqual(dates.last, "2026-03-08"); XCTAssertEqual(Set(dates).count, 7)
    }
    func testMonthGridIncludesLeadingTrailingDaysAndLeapDay() throws {
        let dates = try SchoolTime.monthDates(containing: "2026-11-15", weekStart: 1)
        XCTAssertEqual(dates.count, 42); XCTAssertEqual(dates.first, "2026-10-26"); XCTAssertEqual(dates.last, "2026-12-06")
        XCTAssertTrue(try SchoolTime.monthDates(containing: "2028-02-01", weekStart: 7).contains("2028-02-29"))
    }
}
