import XCTest
@testable import ClassingCore

final class UserErrorTests: XCTestCase {
    func testServerInternalsAreNotDisplayedAsUserErrors() {
        let error = ClassingError.network(400, "REQUEST_INVALID_JSON", "SQL/schema/parser details")
        let message = ClassingError.userMessage(error)
        XCTAssertTrue(message.contains("检查")); XCTAssertFalse(message.contains("SQL")); XCTAssertFalse(message.contains("HTTP")); XCTAssertFalse(message.contains("REQUEST_INVALID_JSON"))
    }
    func testResetCardAndConnectivityErrorsHaveSpecificGuidance() {
        XCTAssertTrue(ClassingError.userMessage(ClassingError.network(400, "AI_RESET_CARD_INVALID", "invalid")).contains("重置码"))
        XCTAssertTrue(ClassingError.userMessage(URLError(.notConnectedToInternet)).contains("网络"))
        XCTAssertTrue(ClassingError.userMessage(URLError(.timedOut)).contains("超时"))
    }
}
