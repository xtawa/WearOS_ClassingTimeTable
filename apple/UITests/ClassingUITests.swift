import XCTest
final class ClassingUITests: XCTestCase {
    func testMainScreensAndScreenshots() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 10))
        screenshot("iPhone-today")
        app.tabBars.buttons["课表"].tap(); XCTAssertTrue(app.navigationBars["课表"].waitForExistence(timeout: 5)); screenshot("iPhone-timetable")
        app.tabBars.buttons["助手"].tap(); screenshot("iPhone-assistant")
        app.tabBars.buttons["设置"].tap(); screenshot("iPhone-settings")
        app.staticTexts["导入课表"].tap(); XCTAssertTrue(app.textViews["import-text"].waitForExistence(timeout: 5)); screenshot("iPhone-import")
    }
    func testDarkModeAndLargeTextRemainNavigable() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]; app.launch()
        XCTAssertTrue(app.tabBars.buttons["设置"].waitForExistence(timeout: 10)); app.tabBars.buttons["设置"].tap(); screenshot("iPhone-accessibility")
    }
    private func screenshot(_ name: String) { let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment) }
}
