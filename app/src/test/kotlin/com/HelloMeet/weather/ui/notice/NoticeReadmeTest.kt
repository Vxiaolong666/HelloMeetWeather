package com.HelloMeet.weather.ui.notice

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NoticeReadmeTest {

    /** 以仓库根 README.md 为准：解析后应产出多类块，且不抛异常、不泄漏原始标签。 */
    @Test
    fun `repo readme parses to mixed blocks`() {
        val file = File("../README.md")
        assumeTrue("README.md 不在预期路径", file.exists())
        val source = file.readText()

        val blocks = MarkdownParser.parse(source)

        assertTrue("至少应有 40 个块", blocks.size >= 40)
        assertTrue("应有标题", blocks.any { it is MdBlock.Heading })
        assertTrue("应有图片", blocks.any { it is MdBlock.Picture })
        assertTrue("应有表格", blocks.any { it is MdBlock.Table })
        assertTrue("应有列表", blocks.any { it is MdBlock.Bullets || it is MdBlock.Ordered })
        assertTrue("应有代码块", blocks.any { it is MdBlock.Code })
        // 不应有整段 HTML 源码串行（正文文本不应再含未处理的 <table> 等块标签）
        val rawTags = blocks.filterIsInstance<MdBlock.Paragraph>()
            .any { Regex("""<(table|tr|td|th|p|div|h[1-6]|ul|ol|li)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        assertTrue("段落不应残留 HTML 块标签", !rawTags)
        // 行内图片（<img> / ![alt](url)）应被提升为 Picture 块，而不是降级成段落文字
        val imgLeaks = blocks.filterIsInstance<MdBlock.Paragraph>()
            .any { Regex("""<img\b|!\[[^\]]*]\([^)]*\)""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        assertTrue("段落不应残留图片语法", !imgLeaks)
        // 表格单元格里的图片也应提升为 Picture
        val tableImg = blocks.filterIsInstance<MdBlock.Table>()
            .flatMap { it.rows.flatten() }
            .any { cell -> cell.blocks.any { it is MdBlock.Picture } }
        assertTrue("表格里应有图片", tableImg)
    }
}
