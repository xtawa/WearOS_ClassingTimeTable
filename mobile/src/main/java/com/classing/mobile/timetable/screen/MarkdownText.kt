package com.xtawa.classingtime.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

internal enum class MarkdownBlockKind { PARAGRAPH, HEADING, LIST_ITEM, QUOTE, CODE }

internal data class MarkdownBlock(
    val kind: MarkdownBlockKind,
    val text: String,
    val level: Int = 0,
    val ordered: Boolean = false,
    val number: Int = 0,
)

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCode = false
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            result += MarkdownBlock(MarkdownBlockKind.PARAGRAPH, paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }
    markdown.replace("\r", "").lineSequence().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                result += MarkdownBlock(MarkdownBlockKind.CODE, code.joinToString("\n"))
                code.clear()
            } else {
                flushParagraph()
            }
            inCode = !inCode
            return@forEach
        }
        if (inCode) {
            code += line
            return@forEach
        }
        val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
        val unordered = Regex("^\\s*[-*+]\\s+(.+)$").matchEntire(line)
        val ordered = Regex("^\\s*(\\d+)[.)]\\s+(.+)$").matchEntire(line)
        when {
            heading != null -> {
                flushParagraph()
                result += MarkdownBlock(MarkdownBlockKind.HEADING, heading.groupValues[2], heading.groupValues[1].length)
            }
            unordered != null -> {
                flushParagraph()
                result += MarkdownBlock(MarkdownBlockKind.LIST_ITEM, unordered.groupValues[1])
            }
            ordered != null -> {
                flushParagraph()
                result += MarkdownBlock(MarkdownBlockKind.LIST_ITEM, ordered.groupValues[2], ordered = true, number = ordered.groupValues[1].toIntOrNull() ?: 1)
            }
            line.startsWith(">") -> {
                flushParagraph()
                result += MarkdownBlock(MarkdownBlockKind.QUOTE, line.removePrefix(">").trimStart())
            }
            line.isBlank() -> flushParagraph()
            else -> paragraph += line
        }
    }
    if (inCode && code.isNotEmpty()) result += MarkdownBlock(MarkdownBlockKind.CODE, code.joinToString("\n"))
    flushParagraph()
    return result
}

internal fun markdownInlineText(
    source: String,
    codeBackground: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < source.length) {
        val marker = when {
            source.startsWith("**", index) -> "**"
            source.startsWith("__", index) -> "__"
            source.startsWith("~~", index) -> "~~"
            source.startsWith("`", index) -> "`"
            source.startsWith("*", index) -> "*"
            source.startsWith("_", index) -> "_"
            else -> ""
        }
        if (source[index] == '[') {
            val labelEnd = source.indexOf(']', index + 1)
            val targetStart = if (labelEnd >= 0 && labelEnd + 1 < source.length && source[labelEnd + 1] == '(') labelEnd + 2 else -1
            val targetEnd = if (targetStart >= 0) source.indexOf(')', targetStart) else -1
            if (targetEnd > targetStart) {
                val target = source.substring(targetStart, targetEnd)
                if (target.startsWith("https://") || target.startsWith("http://")) {
                    val start = length
                    append(source.substring(index + 1, labelEnd))
                    addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline), start, length)
                    index = targetEnd + 1
                    continue
                }
            }
        }
        if (marker.isNotEmpty()) {
            val end = source.indexOf(marker, index + marker.length)
            if (end > index + marker.length) {
                val start = length
                append(source.substring(index + marker.length, end))
                val style = when (marker) {
                    "**", "__" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "`" -> SpanStyle(background = codeBackground, fontFamily = FontFamily.Monospace)
                    else -> SpanStyle(fontStyle = FontStyle.Italic)
                }
                addStyle(style, start, length)
                index = end + marker.length
                continue
            }
        }
        append(source[index])
        index += 1
    }
}

@Composable
internal fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val blocks = parseMarkdownBlocks(markdown)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            val annotated = markdownInlineText(block.text, colors.surfaceContainerHighest, colors.primary)
            when (block.kind) {
                MarkdownBlockKind.HEADING -> Text(
                    annotated,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                )
                MarkdownBlockKind.LIST_ITEM -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (block.ordered) "${block.number}." else "•", fontWeight = FontWeight.Bold, color = colors.primary)
                    Text(annotated, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                MarkdownBlockKind.QUOTE -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(3.dp).background(colors.primary, RoundedCornerShape(999.dp)).padding(vertical = 12.dp))
                    Text(annotated, modifier = Modifier.weight(1f), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                MarkdownBlockKind.CODE -> Text(
                    block.text,
                    modifier = Modifier.fillMaxWidth().background(colors.inverseSurface, RoundedCornerShape(12.dp)).padding(12.dp),
                    color = colors.inverseOnSurface,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                MarkdownBlockKind.PARAGRAPH -> Text(annotated, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
