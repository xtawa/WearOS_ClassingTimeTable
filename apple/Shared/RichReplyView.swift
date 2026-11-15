import SwiftUI
import ClassingCore
#if os(iOS)
import UIKit
#endif

struct RichReplyView: View {
    private var blocks: [ReplyMarkdownBlock]
    init(_ markdown: String) { blocks = ReplyMarkdown.parse(markdown) }
    private init(blocks: [ReplyMarkdownBlock]) { self.blocks = blocks }
    private var compact: Bool {
        #if os(watchOS)
        return true
        #else
        return false
        #endif
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in content(block) }
        }.frame(maxWidth: .infinity, alignment: .leading).tint(ClassingPalette.accent)
    }
    @ViewBuilder private func content(_ block: ReplyMarkdownBlock) -> some View {
        switch block {
        case .paragraph(let text): Text(inline(text)).replySelection()
        case .heading(let level, let text):
            Text(inline(text)).font(level == 1 ? (compact ? .title3.bold() : .title2.bold()) : (level == 2 ? .headline : .subheadline.bold()))
                .accessibilityAddTraits(.isHeader).replySelection()
        case .listItem(let marker, let text, let indent, let checked):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                if let checked { Image(systemName: checked ? "checkmark.square.fill" : "square").foregroundStyle(ClassingPalette.accent).accessibilityLabel(checked ? "已完成" : "未完成") }
                else { Text(marker).foregroundStyle(ClassingPalette.accent).fontWeight(.semibold) }
                Text(inline(text)).frame(maxWidth: .infinity, alignment: .leading).replySelection()
            }.padding(.leading, CGFloat(min(indent, 8)) * 4)
        case .quote(let blocks):
            AnyView(RichReplyView(blocks: blocks).foregroundStyle(.secondary).padding(.leading, 14)
                .overlay(alignment: .leading) { RoundedRectangle(cornerRadius: 2).fill(ClassingPalette.accent.opacity(0.55)).frame(width: 3) })
        case .code(let language, let text):
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(language.isEmpty ? "代码" : language).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Spacer()
                    #if os(iOS)
                    Button { UIPasteboard.general.string = text } label: { Label("复制代码", systemImage: "doc.on.doc") }.labelStyle(.iconOnly).buttonStyle(.plain).font(.caption)
                    #endif
                }
                ScrollView(.horizontal) {
                    Text(verbatim: text).font(.system(compact ? .caption : .callout, design: .monospaced)).fixedSize(horizontal: true, vertical: false).replySelection()
                }
            }.padding(12).background(.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12)).accessibilityIdentifier("reply-code")
        case .table(let headers, let rows, let alignments):
            ScrollView(.horizontal) {
                Grid(alignment: .leading, horizontalSpacing: 0, verticalSpacing: 0) {
                    GridRow {
                        ForEach(headers.indices, id: \.self) { column in cell(headers[column], alignment: alignments[column]).fontWeight(.semibold) }
                    }.background(ClassingPalette.accent.opacity(0.1))
                    ForEach(rows.indices, id: \.self) { row in
                        GridRow {
                            ForEach(headers.indices, id: \.self) { column in
                                cell(rows[row][column], alignment: alignments[column]).accessibilityLabel(headers[column] + "：" + rows[row][column])
                            }
                        }.background(row.isMultiple(of: 2) ? Color.secondary.opacity(0.035) : .clear)
                    }
                }.clipShape(RoundedRectangle(cornerRadius: 10)).overlay { RoundedRectangle(cornerRadius: 10).stroke(.secondary.opacity(0.16)) }
            }.accessibilityIdentifier("reply-table").accessibilityHint("可左右滚动查看各列")
        case .divider: Divider().padding(.vertical, 4)
        }
    }
    private func cell(_ text: String, alignment: ReplyTableAlignment) -> some View {
        Text(inline(text)).font(compact ? .caption : .subheadline).replySelection().padding(10)
            .frame(width: compact ? 120 : 160, alignment: alignment == .trailing ? .trailing : alignment == .center ? .center : .leading)
    }
    private func inline(_ text: String) -> AttributedString {
        var value = (try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(text)
        for run in value.runs where run.inlinePresentationIntent?.contains(.code) == true {
            value[run.range].font = .system(compact ? .caption : .body, design: .monospaced)
            value[run.range].backgroundColor = Color.secondary.opacity(0.12)
        }
        return value
    }
}
private extension View {
    @ViewBuilder func replySelection() -> some View {
        #if os(iOS)
        textSelection(.enabled)
        #else
        self
        #endif
    }
}
