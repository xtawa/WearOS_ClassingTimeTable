import XCTest
@testable import ClassingCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

final class AssistantContractTests: XCTestCase {
    func testSwiftTaskCancellationIsNotAnOperationalError() { XCTAssertTrue(ClassingError.isCancellation(CancellationError())) }
    func testURLSessionCancellationIsNotAnOperationalError() { XCTAssertTrue(ClassingError.isCancellation(URLError(.cancelled))); XCTAssertTrue(ClassingError.isCancellation(NSError(domain: NSURLErrorDomain, code: NSURLErrorCancelled))) }
    func testTimeoutAndAuthorizationErrorsRemainOperationalErrors() { XCTAssertFalse(ClassingError.isCancellation(URLError(.timedOut))); XCTAssertFalse(ClassingError.isCancellation(ClassingError.network(401, "UNAUTHORIZED", "请登录"))) }
    private func client(_ transport: any HTTPTransport) async throws -> ClassingAPI {
        let vault = MemoryTokenVault()
        await vault.write(try AuthSession(.object(["accessToken": .string("test-access"), "refreshToken": .string("test-refresh"), "accessExpiresAt": .int(Wire.milliseconds() + 3_600_000)])))
        return ClassingAPI(baseURL: URL(string: "https://example.invalid")!, vault: vault, platform: "IOS", bundleID: "test", transport: transport)
    }
    private func event(_ type: String, _ data: String) -> SSEEvent { SSEEvent(event: type, data: data, id: nil) }
    private func chat(_ api: ClassingAPI) async throws -> JSONValue {
        try await api.chat(message: "今天有什么课", conversationId: nil, snapshot: .object(["lessons": .array([])]), model: "test-model", attachments: []) { _ in }
    }
    func testAllBooleanPreferencesUseSingleBooleanPatch() async throws {
        let transport = AssistantTransport(), api = try await client(transport)
        let keys = ["autoRefreshPrompts", "showPromptSuggestions", "voiceAutoSend", "showReasoning", "imagePreviews", "showTimestamps"]
        for key in keys { _ = try await api.setAssistantPreference(key, to: false) }
        let bodies = await transport.bodies
        XCTAssertEqual(bodies.count, keys.count)
        for (key, body) in zip(keys, bodies) { XCTAssertEqual(body, .object([key: .bool(false)])) }
    }
    func testFavoritesUseAddRemoveInsteadOfReplacingOtherDevicesStars() async throws {
        let transport = AssistantTransport(), api = try await client(transport)
        _ = try await api.setFavoriteModel("model-a", favorite: true)
        _ = try await api.setFavoriteModel("model-a", favorite: false)
        let bodies = await transport.bodies
        XCTAssertEqual(bodies, [.object(["favoriteAdd": .string("model-a")]), .object(["favoriteRemove": .string("model-a")])])
    }
    func testUnsupportedPreferenceDoesNotSendRequest() async throws {
        let transport = AssistantTransport(), api = try await client(transport)
        do { _ = try await api.setAssistantPreference("favoriteModels", to: true); XCTFail("Unsupported preference accepted") } catch is ClassingError {}
        let bodies = await transport.bodies; XCTAssertTrue(bodies.isEmpty)
    }
    func testBackendChatEventsAndCurrentSnapshotContract() async throws {
        let transport = AssistantTransport(events: [event("conversation", "{\"conversationId\":\"conversation-a\"}"), event("delta", "{\"text\":\"回答\"}"), event("usage", "{\"used\":1,\"limit\":100}"), event("done", "{\"reply\":\"完成\",\"courseProposal\":null,\"examProposal\":null}")])
        let api = try await client(transport), log = EventLog()
        let snapshot = JSONValue.object(["lessons": .array([])])
        let result = try await api.chat(message: "问题", conversationId: "conversation-a", snapshot: snapshot, model: "test-model", attachments: []) { await log.append($0.event) }
        XCTAssertEqual(result["reply"].string, "完成")
        let names = await log.names; XCTAssertEqual(names, ["conversation", "delta", "usage", "done"])
        let body = await transport.bodies.first!
        XCTAssertEqual(body["currentTimetableSnapshot"], snapshot); XCTAssertEqual(body["conversationId"].string, "conversation-a"); XCTAssertEqual(body["timetableSnapshot"], .null)
    }
    func testOptionalUsageAndUnknownEventsCannotInvalidateAnswer() async throws {
        let transport = AssistantTransport(events: [event("delta", "{\"text\":\"已回答\"}"), event("heartbeat", "keepalive"), event("usage", "unrecognized quota metadata"), event("done", "{\"reply\":\"已回答\"}")])
        let api = try await client(transport), result = try await chat(api)
        XCTAssertEqual(result["reply"].string, "已回答")
    }
    func testValidDoneSurvivesMalformedTrailerAndSocketCloseError() async throws {
        let transport = AssistantTransport(events: [event("done", "{\"reply\":\"完整回答\"}"), event("done", "[DONE]")], tailError: .invalid("socket closed after completion"))
        let api = try await client(transport), result = try await chat(api)
        XCTAssertEqual(result["reply"].string, "完整回答")
    }
    func testServerErrorBeforeCompletionStillFails() async throws {
        let transport = AssistantTransport(events: [event("delta", "{\"text\":\"部分回答\"}"), event("error", "{\"code\":\"AI_UPSTREAM_ERROR\",\"message\":\"provider failed\"}")])
        let api = try await client(transport)
        do { _ = try await chat(api); XCTFail("Incomplete answer accepted") } catch let error as ClassingError { XCTAssertEqual(error, .network(502, "AI_UPSTREAM_ERROR", "provider failed")) }
    }
    func testMalformedDoneHasActionableErrorAndIsNotAcceptedAsComplete() async throws {
        for payload in ["not-json", "{}", "[]"] {
            let transport = AssistantTransport(events: [event("done", payload)]), api = try await client(transport)
            do { _ = try await chat(api); XCTFail("Malformed completion accepted") } catch let error as ClassingError { XCTAssertTrue(error.localizedDescription.contains("done")); XCTAssertTrue(error.localizedDescription.contains("草稿已保留")) }
        }
    }
    func testReplayCompletionWithoutReplyIsAccepted() async throws {
        let transport = AssistantTransport(events: [event("delta", "{\"text\":\"之前的回答\"}"), event("done", "{\"messageId\":\"message-a\",\"replayed\":true}")])
        let api = try await client(transport), result = try await chat(api)
        XCTAssertTrue(result["replayed"].bool)
    }
    func testActualURLSessionTransportPreservesBackendEventBoundaries() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AssistantSSEProtocol.self]
        let api = try await client(URLTransport(configuration: configuration)), log = EventLog()
        let result = try await api.chat(message: "课表", conversationId: nil, snapshot: .object([:]), model: "test", attachments: []) { await log.append($0.event) }
        let names = await log.names
        XCTAssertEqual(names, ["conversation", "delta", "delta", "usage", "done"])
        XCTAssertEqual(result["reply"].string, "## 今日课程\n**线性代数** ☀️")
    }
}
private final class AssistantSSEProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "example.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "text/event-stream; charset=utf-8"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        let body = "event: conversation\ndata: {\"conversationId\":\"test\"}\n\nevent: delta\ndata: {\"text\":\"## 今日课程\\n\"}\n\nevent: delta\ndata: {\"text\":\"**线性代数** ☀️\"}\n\nevent: usage\ndata: {\"used\":1}\n\nevent: done\ndata: {\"reply\":\"## 今日课程\\n**线性代数** ☀️\",\"courseProposal\":null,\"examProposal\":null}\n\n"
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
private actor AssistantTransport: HTTPTransport {
    var bodies: [JSONValue] = []
    let events: [SSEEvent]
    let tailError: ClassingError?
    init(events: [SSEEvent] = [], tailError: ClassingError? = nil) { self.events = events; self.tailError = tailError }
    func send(_ request: URLRequest, limit: Int) async throws -> HTTPResponse {
        if let data = request.httpBody { bodies.append(try JSONValue.decode(data)) }
        return HTTPResponse(status: 200, data: Data("{\"preferences\":{\"favoriteModels\":[\"remote-star\"],\"showTimestamps\":false}}".utf8))
    }
    func stream(_ request: URLRequest, onEvent: @escaping @Sendable (SSEEvent) async throws -> Void) async throws {
        if let data = request.httpBody { bodies.append(try JSONValue.decode(data)) }
        for item in events { try await onEvent(item) }
        if let tailError { throw tailError }
    }
}
private actor EventLog { var names: [String] = []; func append(_ name: String) { names.append(name) } }
