package com.HelloMeet.weather.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeParserTest {

    /** 表格单元格辅助：纯文字单元格。 */
    private fun cell(text: String) = TableCell(listOf(MdBlock.Paragraph(text)))

    @Test
    fun `mixed markdown and html blocks`() {
        val source = """
            # 遇见天气

            ## 界面

            <p align="center">**打开就是天气**。</p>

            | 改动 | 说明 |
            |:--|:--|
            | 组件库 | 本地源码模块 |
            | 页面重构 | 分组列表 |

            <img src="assets/banner.png" alt="banner">

            - 列表项一
            - 列表项二

            > 引用文字

        """.trimIndent()

        val blocks = MarkdownParser.parse(source)

        assertEquals(MdBlock.Heading(1, "遇见天气"), blocks[0])
        assertEquals(MdBlock.Heading(2, "界面"), blocks[1])
        assertEquals(MdBlock.Paragraph("**打开就是天气**。"), blocks[2])
        assertEquals(
            MdBlock.Table(listOf(
                listOf(cell("改动"), cell("说明")),
                listOf(cell("组件库"), cell("本地源码模块")),
                listOf(cell("页面重构"), cell("分组列表")),
            )),
            blocks[3],
        )
        assertEquals(MdBlock.Picture("banner", "assets/banner.png", null), blocks[4])
        assertEquals(MdBlock.Bullets(listOf(MdItem("列表项一", null), MdItem("列表项二", null))), blocks[5])
        assertEquals(MdBlock.Quote("引用文字"), blocks[6])
    }

    @Test
    fun `html table`() {
        val source = """
            <table>
            <tr><th>项目</th><th>内容</th></tr>
            <tr><td>天气</td><td>晴</td></tr>
            </table>
        """.trimIndent()

        val blocks = MarkdownParser.parse(source)

        assertEquals(
            listOf(MdBlock.Table(listOf(
                listOf(cell("项目"), cell("内容")),
                listOf(cell("天气"), cell("晴")),
            ))),
            blocks,
        )
    }

    @Test
    fun `html list and paragraph`() {
        val source = """
            <ul>
            <li>甲</li>
            <li>乙</li>
            </ul>
            <p>Hello <b>world</b></p>
        """.trimIndent()

        val blocks = MarkdownParser.parse(source)

        assertEquals(MdBlock.Bullets(listOf(MdItem("甲", null), MdItem("乙", null))), blocks[0])
        assertEquals(MdBlock.Paragraph("Hello <b>world</b>"), blocks[1])
    }

    @Test
    fun `html hr and pre`() {
        val source = """
            <hr>
            <pre><code>int x = 1;
            return x;</code></pre>
        """.trimIndent()

        val blocks = MarkdownParser.parse(source)

        assertTrue(blocks[0] is MdBlock.Rule)
        assertEquals(MdBlock.Code("", "int x = 1;\nreturn x;"), blocks[1])
    }

    @Test
    fun `pure markdown still works`() {
        val source = """
            ## 标题

            段落 **粗体** 与 *斜体* 与 `代码`。

            - 一
            - 二
        """.trimIndent()

        val blocks = MarkdownParser.parse(source)

        assertEquals(MdBlock.Heading(2, "标题"), blocks[0])
        assertEquals(MdBlock.Paragraph("段落 **粗体** 与 *斜体* 与 `代码`。"), blocks[1])
        assertEquals(MdBlock.Bullets(listOf(MdItem("一", null), MdItem("二", null))), blocks[2])
    }

    @Test
    fun `img inside paragraph promotes to picture`() {
        val source = "<p align=\"center\"><img src=\"assets/app-icon.png\" width=\"96\" alt=\"应用图标\"/><br/><b>打开就是天气。</b><br/>一款信息密度较高的天气应用。</p>"

        val blocks = MarkdownParser.parse(source)

        assertEquals(
            listOf(
                MdBlock.Picture("应用图标", "assets/app-icon.png", null, 96),
                MdBlock.Paragraph("<br/><b>打开就是天气。</b><br/>一款信息密度较高的天气应用。"),
            ),
            blocks,
        )
    }

    @Test
    fun `img inside table cell promotes to picture`() {
        val source = """
            <table>
            <tr>
              <td align="center"><a href="assets/screenshot-home.jpg"><img src="assets/screenshot-home.jpg" width="160" alt="主页"/></a></td>
              <td align="center"><a href="assets/screenshot-details.jpg"><img src="assets/screenshot-details.jpg" width="160" alt="遥测"/></a></td>
            </tr>
            <tr>
              <td align="center"><sub>主页</sub></td>
              <td align="center"><sub>遥测</sub></td>
            </tr>
            </table>
        """.trimIndent()

        val blocks = MarkdownParser.parse(source)
        val table = blocks.single() as MdBlock.Table

        assertEquals(
            TableCell(listOf(MdBlock.Picture("主页", "assets/screenshot-home.jpg", null, 160))),
            table.rows[0][0],
        )
        assertEquals(
            TableCell(listOf(MdBlock.Picture("遥测", "assets/screenshot-details.jpg", null, 160))),
            table.rows[0][1],
        )
        assertEquals(cell("<sub>主页</sub>"), table.rows[1][0])
        assertEquals(cell("<sub>遥测</sub>"), table.rows[1][1])
    }

    @Test
    fun `markdown image inline in paragraph promotes to picture`() {
        val source = "介绍见 ![banner](assets/banner.png) 与正文。"

        val blocks = MarkdownParser.parse(source)

        assertEquals(
            listOf(
                MdBlock.Paragraph("介绍见"),
                MdBlock.Picture("banner", "assets/banner.png", null),
                MdBlock.Paragraph("与正文。"),
            ),
            blocks,
        )
    }
}
