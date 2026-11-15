import Foundation

public enum ReplyMarkdownBlock: Equatable, Sendable {
    case paragraph(String)
    case heading(Int, String)
    case listItem(marker: String, text: String, indent: Int, checked: Bool?)
    indirect case quote([ReplyMarkdownBlock])
    case code(language: String, text: String)
    case table(headers: [String], rows: [[String]], alignments: [ReplyTableAlignment])
    case divider
}
public enum ReplyTableAlignment: Equatable, Sendable { case leading, center, trailing }

/// Block layout for assistant replies; inline emphasis and links are interpreted
/// by Foundation's Markdown renderer on each Apple platform.
public enum ReplyMarkdown {
    public static func parse(_ source: String) -> [ReplyMarkdownBlock] {
        let lines = source.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").components(separatedBy: "\n")
        var output: [ReplyMarkdownBlock] = [], paragraph: [String] = [], index = 0
        func flush() { if !paragraph.isEmpty { output.append(.paragraph(paragraph.joined(separator: "\n"))); paragraph = [] } }
        while index < lines.count {
            let line = lines[index], trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty { flush(); index += 1; continue }
            if let fence = fence(trimmed) {
                flush(); index += 1; var code: [String] = []
                while index < lines.count {
                    let closing = lines[index].trimmingCharacters(in: .whitespaces)
                    if closing.prefix(while: { $0 == fence.marker }).count >= fence.count && closing.allSatisfy({ $0 == fence.marker || $0.isWhitespace }) { index += 1; break }
                    code.append(lines[index]); index += 1
                }
                output.append(.code(language: fence.language, text: code.joined(separator: "\n"))); continue
            }
            if index + 1 < lines.count, line.contains("|"), let alignment = tableSeparator(lines[index + 1]) {
                let headers = tableCells(line)
                if headers.count == alignment.count {
                    flush(); index += 2; var rows: [[String]] = []
                    while index < lines.count && !lines[index].trimmingCharacters(in: .whitespaces).isEmpty && lines[index].contains("|") {
                        var cells = Array(tableCells(lines[index]).prefix(headers.count))
                        cells += Array(repeating: "", count: headers.count - cells.count); rows.append(cells); index += 1
                    }
                    output.append(.table(headers: headers, rows: rows, alignments: alignment)); continue
                }
            }
            let hashes = trimmed.prefix(while: { $0 == "#" }).count
            if (1...6).contains(hashes), trimmed.dropFirst(hashes).first?.isWhitespace == true {
                flush(); var text = String(trimmed.dropFirst(hashes)).trimmingCharacters(in: .whitespaces)
                if let closing = text.range(of: #"\s+#+\s*$"#, options: .regularExpression) { text.removeSubrange(closing) }
                output.append(.heading(hashes, text)); index += 1; continue
            }
            if index + 1 < lines.count, let level = setextLevel(lines[index + 1]), listItem(line) == nil, !trimmed.hasPrefix(">") {
                paragraph.append(line); output.append(.heading(level, paragraph.joined(separator: "\n"))); paragraph = []; index += 2; continue
            }
            if isDivider(trimmed) { flush(); output.append(.divider); index += 1; continue }
            if trimmed.hasPrefix(">") {
                flush(); var quote: [String] = []
                while index < lines.count {
                    let current = lines[index].trimmingCharacters(in: .whitespaces)
                    guard current.hasPrefix(">") else { break }
                    var content = String(current.dropFirst()); if content.hasPrefix(" ") { content.removeFirst() }
                    quote.append(content); index += 1
                }
                output.append(.quote(parse(quote.joined(separator: "\n")))); continue
            }
            if let item = listItem(line) {
                flush(); var text = item.text; index += 1
                while index < lines.count {
                    let next = lines[index], indentation = next.prefix(while: { $0 == " " || $0 == "\t" }).count
                    guard !next.trimmingCharacters(in: .whitespaces).isEmpty, indentation > item.indent, listItem(next) == nil, fence(next.trimmingCharacters(in: .whitespaces)) == nil else { break }
                    text += "\n" + next.trimmingCharacters(in: .whitespaces); index += 1
                }
                output.append(.listItem(marker: item.marker, text: text, indent: item.indent, checked: item.checked)); continue
            }
            paragraph.append(line); index += 1
        }
        flush(); return output
    }
    private static func fence(_ text: String) -> (marker: Character, count: Int, language: String)? {
        guard let marker = text.first, marker == "`" || marker == "~" else { return nil }
        let count = text.prefix(while: { $0 == marker }).count; guard count >= 3 else { return nil }
        return (marker, count, String(text.dropFirst(count)).trimmingCharacters(in: .whitespaces))
    }
    private static func setextLevel(_ line: String) -> Int? {
        let text = line.trimmingCharacters(in: .whitespaces)
        if !text.isEmpty && text.allSatisfy({ $0 == "=" }) { return 1 }
        if !text.isEmpty && text.allSatisfy({ $0 == "-" }) { return 2 }
        return nil
    }
    private static func isDivider(_ text: String) -> Bool {
        let characters = text.filter { !$0.isWhitespace }
        return characters.count >= 3 && ["-", "*", "_"].contains(String(characters.first!)) && characters.allSatisfy { $0 == characters.first }
    }
    private static func listItem(_ line: String) -> (marker: String, text: String, indent: Int, checked: Bool?)? {
        let indent = line.prefix(while: { $0 == " " || $0 == "\t" }).count, trimmed = String(line.dropFirst(indent))
        guard let match = trimmed.range(of: #"^(?:[-*+]|[0-9]+[.)])\s+"#, options: .regularExpression) else { return nil }
        let raw = String(trimmed[match]).trimmingCharacters(in: .whitespaces), marker = ["-", "*", "+"].contains(raw) ? "•" : raw
        var text = String(trimmed[match.upperBound...]), checked: Bool?
        if text.hasPrefix("[ ] ") || text.hasPrefix("[x] ") || text.hasPrefix("[X] ") { checked = !text.hasPrefix("[ ]"); text = String(text.dropFirst(4)) }
        return (marker, text, indent, checked)
    }
    private static func tableSeparator(_ line: String) -> [ReplyTableAlignment]? {
        let cells = tableCells(line); guard !cells.isEmpty else { return nil }
        var result: [ReplyTableAlignment] = []
        for cell in cells {
            guard cell.range(of: #"^:?-{3,}:?$"#, options: .regularExpression) != nil else { return nil }
            result.append(cell.hasSuffix(":") ? (cell.hasPrefix(":") ? .center : .trailing) : .leading)
        }
        return result
    }
    private static func tableCells(_ line: String) -> [String] {
        let characters = Array(line.trimmingCharacters(in: .whitespaces)); var cells: [String] = [], cell = "", index = 0, codeFence = 0
        while index < characters.count {
            let character = characters[index]
            if character == "\\", index + 1 < characters.count { cell.append(character); cell.append(characters[index + 1]); index += 2; continue }
            if character == "`" {
                var end = index; while end < characters.count && characters[end] == "`" { end += 1 }
                let count = end - index; if codeFence == 0 { codeFence = count } else if count == codeFence { codeFence = 0 }
                cell += String(repeating: "`", count: count); index = end; continue
            }
            if character == "|" && codeFence == 0 { cells.append(cell.trimmingCharacters(in: .whitespaces)); cell = "" } else { cell.append(character) }
            index += 1
        }
        cells.append(cell.trimmingCharacters(in: .whitespaces))
        if characters.first == "|" && cells.first == "" { cells.removeFirst() }
        if characters.last == "|" && cells.last == "" { cells.removeLast() }
        return cells
    }
}
