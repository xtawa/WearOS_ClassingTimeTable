import XCTest
import StoreKitTest
final class ClassingUITests: XCTestCase {
    func testMainScreensAndScreenshots() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["问问 Classing"].exists)
        screenshot("iPhone-light")
        app.tabBars.buttons["课表"].tap(); XCTAssertTrue(app.navigationBars["课表"].waitForExistence(timeout: 5)); screenshot("iPhone-timetable")
        app.tabBars.buttons["助手"].tap(); screenshot("iPhone-assistant")
        app.tabBars.buttons["设置"].tap(); screenshot("iPhone-settings")
        reveal(app.staticTexts["数据与备份"], in: app); app.staticTexts["数据与备份"].tap()
        app.staticTexts["导入课表"].tap(); XCTAssertTrue(app.textViews["import-text"].waitForExistence(timeout: 5)); screenshot("iPhone-import")
    }
    func testTimetableWeekNavigationAndMonthCalendarDisplayCourses() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        XCTAssertTrue(app.tabBars.buttons["课表"].waitForExistence(timeout: 10)); app.tabBars.buttons["课表"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["timetable-week-course"].firstMatch.waitForExistence(timeout: 5))
        let original = app.staticTexts["timetable-period-label"].label
        app.buttons["timetable-next"].tap(); XCTAssertNotEqual(app.staticTexts["timetable-period-label"].label, original)
        app.buttons["timetable-previous"].tap()
        app.segmentedControls.buttons["日历"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["month-calendar"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["timetable-day-course"].firstMatch.waitForExistence(timeout: 5))
        screenshot("iPhone-calendar")
        app.segmentedControls.buttons["课程"].tap(); screenshot("iPhone-course-list")
    }
    func testDarkModeAndLargeTextRemainNavigable() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--dark", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXL"]; app.launch()
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 10)); screenshot("iPhone-dark")
        XCTAssertTrue(app.tabBars.buttons["设置"].waitForExistence(timeout: 10)); app.tabBars.buttons["设置"].tap(); screenshot("iPhone-accessibility")
    }
    func testWidgetGalleryAndLockScreenLayouts() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        XCTAssertTrue(app.tabBars.buttons["设置"].waitForExistence(timeout: 10)); app.tabBars.buttons["设置"].tap()
        app.staticTexts["外观与组件"].tap(); app.staticTexts["小组件"].tap()
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
    func testSettingsUsesCategoriesAndPreservesDetailedControls() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        XCTAssertTrue(app.tabBars.buttons["设置"].waitForExistence(timeout: 10)); app.tabBars.buttons["设置"].tap()
        XCTAssertEqual(app.switches.count, 0)
        for (entry, title) in [("学期与课程", "学期与课程"), ("提醒与简报", "通知与简报"), ("外观与组件", "外观与组件"), ("同步与设备", "同步与设备"), ("Ask Classing", "Ask Classing"), ("数据与备份", "数据与备份"), ("关于 Classing", "关于 Classing")] {
            reveal(app.staticTexts[entry], in: app); app.staticTexts[entry].tap()
            XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout: 5))
            if entry == "外观与组件" { screenshot("iPhone-settings-appearance") }
            if entry == "同步与设备" { screenshot("iPhone-settings-devices") }
            app.navigationBars[title].buttons.element(boundBy: 0).tap()
        }
    }
    func testOnboardingCanFinishWithoutPermissionsOrSubscription() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--onboarding"]; app.launch()
        XCTAssertTrue(app.buttons["onboarding-next"].waitForExistence(timeout: 10)); screenshot("iPhone-oobe-welcome")
        for (title, name) in [("从你的学期开始。", "iPhone-oobe-semester"), ("你的课表，轻松带来。", "iPhone-oobe-import"), ("只在需要时，提醒你。", "iPhone-oobe-permissions")] {
            app.buttons["onboarding-next"].tap()
            XCTAssertTrue(app.staticTexts[title].waitForExistence(timeout: 5))
            screenshot(name)
        }
        XCTAssertTrue(app.buttons["开启课前通知"].exists)
        app.buttons["onboarding-next"].tap()
        XCTAssertTrue(app.buttons["onboarding-next"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.descendants(matching: .any)["next-course"].waitForExistence(timeout: 5))
    }
    func testAssistantRendersRichReplyAndScrollableTableAndCode() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing", "--assistant-preview"]; app.launch()
        XCTAssertTrue(app.tabBars.buttons["助手"].waitForExistence(timeout: 10)); app.tabBars.buttons["助手"].tap()
        XCTAssertTrue(app.staticTexts["今日课程"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["# 今日课程"].exists); screenshot("iPhone-assistant-rich")
        let table = app.descendants(matching: .any)["reply-table"].firstMatch
        reveal(table, in: app); screenshot("iPhone-assistant-table")
        let code = app.descendants(matching: .any)["reply-code"].firstMatch
        reveal(code, in: app); XCTAssertTrue(app.buttons["复制代码"].exists); screenshot("iPhone-assistant-code")
    }
    func testStoreKitLocalPurchaseAndRestore() throws {
        let session = try SKTestSession(configurationFileNamed: "ClassingTest")
        session.resetToDefaultState()
        session.disableDialogs = true; session.clearTransactions()
        defer { session.clearTransactions() }
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
    private func screenshot(_ name: String) {
        // The onboarding state changes before its 250ms transition finishes.
        // Let the compositor settle after the explicit page assertion above.
        if name.hasPrefix("iPhone-oobe-") { Thread.sleep(forTimeInterval: 0.5) }
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<6 { if element.exists && element.isHittable { return }; app.swipeUp() }
        XCTAssertTrue(element.exists && element.isHittable)
    }
}
