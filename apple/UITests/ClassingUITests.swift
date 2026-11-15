import XCTest
final class ClassingUITests: XCTestCase {
    func testMainScreensAndScreenshots() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 10))
        screenshot("iPhone-light")
        app.tabBars.buttons["课表"].tap(); XCTAssertTrue(app.navigationBars["课表"].waitForExistence(timeout: 5)); screenshot("iPhone-timetable")
        app.tabBars.buttons["助手"].tap(); screenshot("iPhone-assistant")
        app.tabBars.buttons["设置"].tap(); screenshot("iPhone-settings")
        app.staticTexts["导入课表"].tap(); XCTAssertTrue(app.textViews["import-text"].waitForExistence(timeout: 5)); screenshot("iPhone-import")
    }
    func testDarkModeAndLargeTextRemainNavigable() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--dark", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]; app.launch()
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 10)); screenshot("iPhone-dark")
        XCTAssertTrue(app.tabBars.buttons["设置"].waitForExistence(timeout: 10)); app.tabBars.buttons["设置"].tap(); screenshot("iPhone-accessibility")
    }
    func testWidgetGalleryAndLockScreenLayouts() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        XCTAssertTrue(app.tabBars.buttons["设置"].waitForExistence(timeout: 10)); app.tabBars.buttons["设置"].tap()
        app.staticTexts["小组件"].tap()
        XCTAssertTrue(app.navigationBars["小组件"].waitForExistence(timeout: 5)); screenshot("iPhone-widgets")
        app.swipeUp(); screenshot("iPhone-widget-agenda")
        app.navigationBars["小组件"].buttons.element(boundBy: 0).tap(); app.staticTexts["小组件"].tap()
        app.segmentedControls.buttons["锁屏与手表"].tap(); screenshot("iPhone-widget-accessories")
    }
    func testEmptyScheduleAndExamMode() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--empty"]; app.launch()
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 10)); screenshot("iPhone-empty")
        app.segmentedControls.buttons["考试"].tap()
        XCTAssertTrue(app.staticTexts["还没有考试安排"].waitForExistence(timeout: 5)); screenshot("iPhone-empty-exams")
    }
    private func screenshot(_ name: String) { let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment) }
}
