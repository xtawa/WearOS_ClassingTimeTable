package com.xtawa.classingtime.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    @Test
    fun parsesHeadingsListsQuotesAndCodeBlocks() {
        val blocks = parseMarkdownBlocks(
            """
            ## 明日课程
            - **数学** 08:00
            1. 准备作业
            > 记得带书
            ```text
            room = A101
            ```
            """.trimIndent(),
        )

        assertEquals(MarkdownBlockKind.HEADING, blocks[0].kind)
        assertEquals(2, blocks[0].level)
        assertEquals(MarkdownBlockKind.LIST_ITEM, blocks[1].kind)
        assertTrue(blocks[2].ordered)
        assertEquals(MarkdownBlockKind.QUOTE, blocks[3].kind)
        assertEquals(MarkdownBlockKind.CODE, blocks[4].kind)
        assertEquals("room = A101", blocks[4].text)
    }
}
