package com.xtawa.classingtime.screen

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal fun decodeImportBytes(bytes: ByteArray): String {
    if (bytes.startsWithBytes(0xEF, 0xBB, 0xBF)) return bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
    if (bytes.startsWithBytes(0xFF, 0xFE)) return bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16LE)
    if (bytes.startsWithBytes(0xFE, 0xFF)) return bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16BE)
    val utf8 = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()
    return utf8 ?: bytes.toString(charset("GB18030"))
}

private fun ByteArray.startsWithBytes(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index -> this[index].toInt() and 0xFF == expected[index] }
