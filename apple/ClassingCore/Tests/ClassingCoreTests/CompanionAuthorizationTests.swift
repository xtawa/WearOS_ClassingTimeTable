import XCTest
@testable import ClassingCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

final class CompanionAuthorizationTests: XCTestCase {
    func testCompanionUsesDeviceAuthorizationWithoutCopyingPhoneCredentials() async throws {
        let transport = DeviceAuthorizationTransport(), vault = MemoryTokenVault(), approved = ApprovedDevice()
        let api = ClassingAPI(baseURL: URL(string: "https://example.invalid")!, vault: vault, platform: "WATCHOS", bundleID: "test.watch", transport: transport)
        let session = try await api.authorizeCompanion { await approved.record($0) }
        let ids = await approved.ids, requests = await transport.requests
        XCTAssertEqual(ids, ["watch-authorization"]); XCTAssertEqual(session.account["userId"].string, "phone-user")
        XCTAssertEqual(requests.map { $0.url!.path }, ["/api/v1/auth/device/qr/start", "/api/v1/auth/device/qr/poll"])
        XCTAssertTrue(requests.allSatisfy { $0.value(forHTTPHeaderField: "Authorization") == nil })
        XCTAssertEqual(try JSONValue.decode(requests[1].httpBody!), .object(["authorizationId": .string("watch-authorization"), "pollSecret": .string("watch-secret")]))
        let saved = await vault.read(); XCTAssertNil(saved) // Caller verifies the expected account before installing the session.
    }
    func testCancelledApprovalDoesNotPollOrInstallASession() async throws {
        let transport = DeviceAuthorizationTransport(), vault = MemoryTokenVault()
        let api = ClassingAPI(baseURL: URL(string: "https://example.invalid")!, vault: vault, platform: "WATCHOS", bundleID: "test", transport: transport)
        do { _ = try await api.authorizeCompanion { _ in throw CancellationError() }; XCTFail("Cancelled approval accepted") } catch is CancellationError {}
        let requests = await transport.requests, saved = await vault.read(); XCTAssertEqual(requests.count, 1); XCTAssertNil(saved)
    }
    func testExpiredAuthorizationDoesNotRequestPhoneApproval() async throws {
        let transport = DeviceAuthorizationTransport(expired: true), approved = ApprovedDevice()
        let api = ClassingAPI(baseURL: URL(string: "https://example.invalid")!, vault: MemoryTokenVault(), platform: "WATCHOS", bundleID: "test", transport: transport)
        do { _ = try await api.authorizeCompanion { await approved.record($0) }; XCTFail("Expired authorization accepted") } catch is ClassingError {}
        let ids = await approved.ids, requests = await transport.requests; XCTAssertTrue(ids.isEmpty); XCTAssertEqual(requests.count, 1)
    }
    func testWatchChatDoesNotAdvertiseCourseEditing() async throws {
        let transport = DeviceAuthorizationTransport(), vault = MemoryTokenVault()
        await vault.write(try AuthSession(.object(["accessToken": .string("watch-access"), "refreshToken": .string("watch-refresh"), "accessExpiresAt": .int(Wire.milliseconds() + 3_600_000)])))
        let api = ClassingAPI(baseURL: URL(string: "https://example.invalid")!, vault: vault, platform: "WATCHOS", bundleID: "test", transport: transport)
        _ = try await api.chat(message: "今天的课", conversationId: nil, snapshot: .object([:]), model: "test", attachments: []) { _ in }
        let requests = await transport.requests, body = try JSONValue.decode(requests[0].httpBody!)
        XCTAssertEqual(body["supportsTimetableActions"], .bool(false))
    }
}
private actor ApprovedDevice { var ids: [String] = []; func record(_ id: String) { ids.append(id) } }
private actor DeviceAuthorizationTransport: HTTPTransport {
    var requests: [URLRequest] = []; var expired: Bool
    init(expired: Bool = false) { self.expired = expired }
    func send(_ request: URLRequest, limit: Int) async throws -> HTTPResponse {
        requests.append(request)
        let result: JSONValue
        if request.url!.path.hasSuffix("/start") {
            result = .object(["authorizationId": .string("watch-authorization"), "pollSecret": .string("watch-secret"), "expiresAt": .int(expired ? 0 : Wire.milliseconds() + 300_000), "intervalSeconds": .int(5)])
        } else {
            result = .object(["session": .object(["accessToken": .string("independent-watch-access"), "refreshToken": .string("independent-watch-refresh"), "accessExpiresAt": .int(Wire.milliseconds() + 3_600_000)]), "account": .object(["userId": .string("phone-user")])])
        }
        return HTTPResponse(status: 200, data: try result.data())
    }
    func stream(_ request: URLRequest, onEvent: @escaping @Sendable (SSEEvent) async throws -> Void) async throws {
        requests.append(request); try await onEvent(SSEEvent(event: "done", data: "{\"reply\":\"回答\"}", id: nil))
    }
}
