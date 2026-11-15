import XCTest
@testable import ClassingCore

final class ReplyMarkdownTests: XCTestCase {
    func testHeadingsParagraphsAndLineBreaks() {
        XCTAssertEqual(ReplyMarkdown.parse("# 今日课程\n\n**线性代数**\n教室 A-205\n\n第二段"), [.heading(1, "今日课程"), .paragraph("**线性代数**\n教室 A-205"), .paragraph("第二段")])
        XCTAssertEqual(ReplyMarkdown.parse("## 次级标题 ##\n\n标题\n===\n\n小标题\n---"), [.heading(2, "次级标题"), .heading(1, "标题"), .heading(2, "小标题")])
    }
    func testOrderedNestedAndTaskLists() {
        XCTAssertEqual(ReplyMarkdown.parse("3. 先复习\n  - 子项目\n    继续说明\n- [x] 已准备\n- [ ] 待完成"), [.listItem(marker: "3.", text: "先复习", indent: 0, checked: nil), .listItem(marker: "•", text: "子项目\n继续说明", indent: 2, checked: nil), .listItem(marker: "•", text: "已准备", indent: 0, checked: true), .listItem(marker: "•", text: "待完成", indent: 0, checked: false)])
    }
    func testQuotesKeepTheirOwnHeadingsAndLists() {
        XCTAssertEqual(ReplyMarkdown.parse("> ## 提醒\n>\n> - 提前到场\n> - 携带课本"), [.quote([.heading(2, "提醒"), .listItem(marker: "•", text: "提前到场", indent: 0, checked: nil), .listItem(marker: "•", text: "携带课本", indent: 0, checked: nil)])])
    }
    func testFencedCodeIsVerbatimIncludingEmptyLinesAndMarkdownMarkers() {
        XCTAssertEqual(ReplyMarkdown.parse("```swift\nlet title = \"# 标题\"\n\n// **原样显示**\n```\n\n之后的段落"), [.code(language: "swift", text: "let title = \"# 标题\"\n\n// **原样显示**"), .paragraph("之后的段落")])
        XCTAssertEqual(ReplyMarkdown.parse("~~~json\n{\"course\":\"线性代数\"}\n~~~"), [.code(language: "json", text: "{\"course\":\"线性代数\"}")])
    }
    func testIncompleteStreamStillShowsOpenCodeBlockAndPartialHeading() {
        XCTAssertEqual(ReplyMarkdown.parse("##"), [.paragraph("##")])
        XCTAssertEqual(ReplyMarkdown.parse("```json\n{\"course\": \"线性"), [.code(language: "json", text: "{\"course\": \"线性")])
    }
    func testTablesKeepAlignmentEscapedPipesAndInlineCode() {
        XCTAssertEqual(ReplyMarkdown.parse("| 课程 | 时间 | 说明 |\n| :--- | :---: | ---: |\n| 线性代数 | 09:00 | `a|b` |\n| A\\|B | 10:00 |"), [.table(headers: ["课程", "时间", "说明"], rows: [["线性代数", "09:00", "`a|b`"], ["A\\|B", "10:00", ""]], alignments: [.leading, .center, .trailing])])
    }
    func testUnfinishedTableSeparatorAndOrdinaryPipesRemainReadable() {
        XCTAssertEqual(ReplyMarkdown.parse("a | b\n| -- | -- |"), [.paragraph("a | b\n| -- | -- |")])
        XCTAssertEqual(ReplyMarkdown.parse("中文\r\n\r\n***\r\n\r\n[课程链接](https://example.com)"), [.paragraph("中文"), .divider, .paragraph("[课程链接](https://example.com)")])
    }
}
