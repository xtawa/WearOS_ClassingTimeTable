import XCTest
@testable import ClassingCore

final class SSEParserTests: XCTestCase {
    private func parse(_ text: String) -> [SSEEvent] {
        var parser = SSEParser(), result: [SSEEvent] = []
        for byte in text.utf8 { if let event = parser.byte(byte) { result.append(event) } }
        if let event = parser.finish() { result.append(event) }
        return result
    }
    func testBlankLinesDispatchEachEventBeforeTheFinalDone() {
        let events = parse("event: delta\ndata: {\"text\":\"一\"}\n\nevent: delta\ndata: {\"text\":\"二\"}\n\nevent: done\ndata: {\"reply\":\"一二\"}\n\n")
        XCTAssertEqual(events.map(\.event), ["delta", "delta", "done"])
        XCTAssertEqual(events.map(\.data), ["{\"text\":\"一\"}", "{\"text\":\"二\"}", "{\"reply\":\"一二\"}"])
    }
    func testLFCRLFAndCRKeepEmptyEventSeparators() {
        for ending in ["\n", "\r\n", "\r"] {
            let text = ["event: delta", "data: 中文 ☀️", "", "event: done", "data: {}", "", ""].joined(separator: ending)
            let events = parse(text)
            XCTAssertEqual(events.count, 2); XCTAssertEqual(events.first?.data, "中文 ☀️"); XCTAssertEqual(events.last?.event, "done")
        }
    }
    func testCommentsBOMAndMultilineData() {
        let events = parse("\u{FEFF}: ping\r\nid: cursor\r\nevent: delta\r\ndata: first\r\ndata: second\r\n\r\n: heartbeat\r\n\r\n")
        XCTAssertEqual(events, [SSEEvent(event: "delta", data: "first\nsecond", id: "cursor")])
    }
    func testFinalUnterminatedDataLineIsNotLost() {
        XCTAssertEqual(parse("event: done\ndata: {\"reply\":\"完成\"}"), [SSEEvent(event: "done", data: "{\"reply\":\"完成\"}", id: nil)])
    }
}
