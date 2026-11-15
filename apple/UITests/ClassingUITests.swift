import XCTest
import StoreKitTest
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
        app.segmentedControls.buttons["实时活动"].tap()
        XCTAssertTrue(app.staticTexts["锁屏实时活动"].waitForExistence(timeout: 5)); screenshot("iPhone-live-activity")
    }
    func testEmptyScheduleAndExamMode() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--empty"]; app.launch()
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 10)); screenshot("iPhone-empty")
        app.segmentedControls.buttons["考试"].tap()
        XCTAssertTrue(app.staticTexts["还没有考试安排"].waitForExistence(timeout: 5)); screenshot("iPhone-empty-exams")
    }
    func testOnboardingCanFinishWithoutPermissionsOrSubscription() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--onboarding"]; app.launch()
        XCTAssertTrue(app.buttons["onboarding-next"].waitForExistence(timeout: 10)); screenshot("iPhone-oobe-welcome")
        app.buttons["onboarding-next"].tap(); screenshot("iPhone-oobe-semester")
        app.buttons["onboarding-next"].tap(); screenshot("iPhone-oobe-import")
        app.buttons["onboarding-next"].tap(); screenshot("iPhone-oobe-permissions")
        app.buttons["onboarding-next"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 5))
    }
    func testStoreKitLocalPurchaseAndRestore() throws {
        let session = try SKTestSession(configurationFileNamed: "ClassingTest")
        session.disableDialogs = true; session.clearTransactions()
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--storekit-testing"]; app.launch()
        XCTAssertTrue(app.tabBars.buttons["设置"].waitForExistence(timeout: 10)); app.tabBars.buttons["设置"].tap()
        app.staticTexts["Classing Pro"].tap()
        let plan = app.buttons["plan-com.xtawa.classingtime.test.pro.monthly"]
        XCTAssertTrue(plan.waitForExistence(timeout: 20)); screenshot("iPhone-subscription")
        app.swipeUp(); plan.tap(); screenshot("iPhone-subscription-plans")
        app.buttons["subscribe"].tap()
        XCTAssertTrue(app.staticTexts["本地测试订阅已验证"].waitForExistence(timeout: 20))
        app.buttons["恢复购买"].tap()
        XCTAssertTrue(app.staticTexts["本地测试订阅已恢复"].waitForExistence(timeout: 20))
    }
    private func screenshot(_ name: String) { let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment) }
}
