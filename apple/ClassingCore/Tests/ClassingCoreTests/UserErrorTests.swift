import XCTest
@testable import ClassingCore

final class UserErrorTests: XCTestCase {
    func testAppleAndEmailFailuresGiveRecoveryInstructions() {
        XCTAssertTrue(ClassingError.userMessage(ClassingError.network(401, "AUTH_APPLE_TOKEN_INVALID", "jwt nonce invalid")).contains("重新"))
        XCTAssertTrue(ClassingError.userMessage(ClassingError.network(409, "ACCOUNT_EMAIL_BINDING_REQUIRED", "internal")).contains("验证邮箱"))
        XCTAssertTrue(ClassingError.userMessage(ClassingError.network(403, "ACCOUNT_PASSWORD_CURRENT_INVALID", "internal")).contains("当前密码"))
    }
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
