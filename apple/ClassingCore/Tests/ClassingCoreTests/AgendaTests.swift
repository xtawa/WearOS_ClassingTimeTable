import XCTest
@testable import ClassingCore

final class AgendaTests: XCTestCase {
    func state() -> AppState {
        var s = AppState()
        s.settings["timezone"] = .string("Asia/Shanghai")
        s.settings["semesterWeekStartDate"] = .string("2026-09-07")
        var first = Lesson(title: "交互设计"); first.id = "design"; first.startMinute = 600; first.endMinute = 660
        var second = Lesson(title: "高等数学"); second.id = "math"; second.startMinute = 840; second.endMinute = 900
        s.lessons = [first, second]
        return s
    }
    func date(_ minute: Int) throws -> Date { try SchoolTime.instant(date: "2026-11-16", minute: minute, zone: "Asia/Shanghai") }
    func testUpcomingActiveBreakAndFinishedBoundaries() throws {
        let s = state()
        let before = try AgendaSnapshot.make(s, at: date(580))
        XCTAssertEqual(before.phase, .upcoming); XCTAssertEqual(before.minutesRemaining, 20)
        let active = try AgendaSnapshot.make(s, at: date(600))
        XCTAssertEqual(active.phase, .inProgress); XCTAssertEqual(active.minutesRemaining, 60)
        let half = try AgendaSnapshot.make(s, at: date(630))
        XCTAssertEqual(half.progress, 0.5, accuracy: 0.001)
        let rest = try AgendaSnapshot.make(s, at: date(660))
        XCTAssertEqual(rest.phase, .breakTime); XCTAssertEqual(rest.primary?.id, "math:2026-11-16"); XCTAssertEqual(rest.completedCount, 1)
        let done = try AgendaSnapshot.make(s, at: date(900))
        XCTAssertEqual(done.phase, .finished); XCTAssertEqual(done.remainingCount, 0)
    }
    func testEmptyExamModeDoesNotDisplayCourses() throws {
        var s = state(); s.settings["academicMode"] = .string("EXAM")
        let value = try AgendaSnapshot.make(s, at: date(580))
        XCTAssertEqual(value.phase, .empty); XCTAssertTrue(value.isExam); XCTAssertNil(value.primary)
    }
    func testExamTimezoneAndCompletedExams() throws {
        var s = state(); s.settings["academicMode"] = .string("EXAM")
        var exam = Exam(title: "期末考试", start: try date(600), end: try date(660), timezone: "America/New_York")
        s.exams = [exam]
        let value = try AgendaSnapshot.make(s, at: date(630))
        XCTAssertEqual(value.phase, .inProgress); XCTAssertEqual(value.primary?.timezone, "America/New_York")
        exam.completed = true; s.exams = [exam]
        XCTAssertNil(try AgendaSnapshot.make(s, at: date(630)).primary)
    }
    func testTimelineIncludesLocalMidnightAndCourseBoundaries() throws {
        let value = try AgendaSnapshot.make(state(), at: date(580))
        XCTAssertTrue(value.transitionDates.contains(try date(600)))
        XCTAssertTrue(value.transitionDates.contains(try date(660)))
        XCTAssertTrue(value.transitionDates.contains(try SchoolTime.instant(date: "2026-11-17", minute: 0, zone: "Asia/Shanghai")))
        XCTAssertEqual(value.transitionDates, Array(Set(value.transitionDates)).sorted())
    }
    func testCancelledCourseIsAbsentAndChangedLocationIsRetained() throws {
        var s = state()
        s.exceptions = [ScheduleException(type: "CANCEL", date: "2026-11-16", lessonId: "design")]
        var move = ScheduleException(type: "RESCHEDULE", date: "2026-11-16", lessonId: "math")
        move.startMinute = 850; move.endMinute = 910; move.location = "B305"; s.exceptions.append(move)
        let value = try AgendaSnapshot.make(s, at: date(580))
        XCTAssertEqual(value.today.count, 1); XCTAssertEqual(value.primary?.location, "B305"); XCTAssertEqual(value.primary?.changed, true)
    }
}
