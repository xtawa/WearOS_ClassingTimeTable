package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class MobileImportTextDecoderTest {
    @Test fun decodesUtf8Bom() {
        val body = "课程".toByteArray(StandardCharsets.UTF_8)
        assertEquals("课程", decodeImportBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body))
    }

    @Test fun fallsBackToGb18030() {
        val text = "课程表"
        assertEquals(text, decodeImportBytes(text.toByteArray(charset("GB18030"))))
    }
}
