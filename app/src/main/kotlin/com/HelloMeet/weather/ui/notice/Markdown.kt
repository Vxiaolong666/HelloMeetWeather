package com.HelloMeet.weather.ui.notice

/** 公告正文解析后的块级结构。 */
internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Bullets(val items: List<MdItem>) : MdBlock
    data class Ordered(val start: Int, val items: List<MdItem>) : MdBlock
    data object Rule : MdBlock
    data class Formula(val tex: String) : MdBlock
    data class Picture(val alt: String, val url: String, val title: String?, val width: Int? = null) : MdBlock
    /** 表格：首行为表头，其余为数据行；单元格内可有文字与图片。 */
    data class Table(val rows: List<List<TableCell>>) : MdBlock
}

internal data class MdItem(val text: String, val checked: Boolean?)

/** 表格单元格：由若干块组成（一般为一段文字，或一张图片）。 */
internal data class TableCell(val blocks: List<MdBlock>)

/** 行内图片：Markdown `![alt](url)` 或 HTML `<img>`。用于把藏在段落/单元格里的图片提升为块级图片。 */
private val inlineImagePattern = Regex(
    """!\[([^\]]*)]\(\s*([^)\s]+)(?:\s+"([^"]*)")?\s*\)|<img\b[^>]*>""",
    RegexOption.IGNORE_CASE,
)

/**
 * 把一段「行内文本」切成块：行内图片（Markdown 或 HTML）提升为 [MdBlock.Picture]，
 * 其余文字按图片切分成若干 [MdBlock.Paragraph]。这样图片不会降级成 `[图片]` 文字，
 * 且 `<img>` 藏在 `<p>` / `<td>` / `<blockquote>` 里也能正常显示。
 */
internal fun inlineBlocks(text: String): List<MdBlock> {
    if (text.isBlank()) return emptyList()
    val out = mutableListOf<MdBlock>()
    var cursor = 0
    for (m in inlineImagePattern.findAll(text)) {
        if (m.range.first > cursor) {
            val seg = text.substring(cursor, m.range.first)
            if (stripHtmlTags(seg).isNotBlank()) out += MdBlock.Paragraph(seg.trim())
        }
        if (m.value.startsWith("<img", ignoreCase = true)) {
            val attrs = m.value.removePrefix("<img").removeSuffix(">").trim().trimEnd('/')
            val src = htmlAttr(attrs, "src") ?: htmlAttr(attrs, "data-src")
            if (src != null) {
                out += MdBlock.Picture(
                    alt = htmlAttr(attrs, "alt").orEmpty(),
                    url = src,
                    title = null,
                    width = htmlAttr(attrs, "width")?.toIntOrNull(),
                )
            }
        } else {
            out += MdBlock.Picture(m.groupValues[1], m.groupValues[2], m.groupValues[3].takeIf { it.isNotBlank() })
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) {
        val seg = text.substring(cursor)
        if (stripHtmlTags(seg).isNotBlank()) out += MdBlock.Paragraph(seg.trim())
    }
    return out
}

/**
 * 极简公告正文解析：同时识别 Markdown 与基础 HTML 的块级结构（公告常见为两者混合）。
 * 块级覆盖标题、引用、代码、列表、任务、分割线、公式、图片、表格，以及
 * p / div / h1-h6 / ul / ol / li / blockquote / pre / hr / img / table 等 HTML 元素；
 * 行内语法（Markdown 强调 / 链接 / 代码 / 公式 与 HTML 颜色 / 粗体等）留在渲染层处理。
 * 不用第三方库，避免为一段说明文字引入整套解析器与图片加载依赖。
 */
internal object MarkdownParser {

    private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")
    private val orderedPattern = Regex("^(\\d{1,9})[.)]\\s+(.*)$")
    private val bulletPattern = Regex("^[-*+]\\s+(.*)$")
    private val taskPattern = Regex("^\\[([ xX])]\\s+(.*)$")
    private val rulePattern = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val picturePattern = Regex("^!\\[([^]]*)]\\(\\s*(\\S+?)(?:\\s+\"([^\"]*)\")?\\s*\\)$")
    private val tableSeparator = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    /** HTML 块级标签：一行以它开头时整段交给 [HtmlParser.consumeElement]。 */
    private val htmlBlockOpen = Regex(
        """^<(p|div|h[1-6]|ul|ol|li|blockquote|pre|table|img|hr)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        var bullets: MutableList<MdItem>? = null
        var orderedItems: MutableList<MdItem>? = null
        var orderedStart = 1

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += inlineBlocks(paragraph.joinToString("\n"))
                paragraph.clear()
            }
        }

        fun flushLists() {
            bullets?.let { blocks += MdBlock.Bullets(it) }
            orderedItems?.let { blocks += MdBlock.Ordered(orderedStart, it) }
            bullets = null
            orderedItems = null
        }

        fun flushAll() {
            flushParagraph()
            flushLists()
        }

        var index = 0
        while (index < lines.size) {
            val text = lines[index].trim()

            if (text.isEmpty()) {
                flushAll()
                index++
                continue
            }

            // HTML 块级元素：一行以块标签开头，整段（可跨行）交给 HTML 解析
            if (htmlBlockOpen.containsMatchIn(text)) {
                flushAll()
                val (htmlBlocks, consumed) = HtmlParser.consumeElement(lines, index)
                blocks += htmlBlocks
                index += consumed
                continue
            }

            // 代码块：```lang 起，``` 止；未闭合时吃到文末
            if (text.startsWith("```")) {
                flushAll()
                val language = text.removePrefix("```").trim()
                val body = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    body += lines[index]
                    index++
                }
                if (index < lines.size) index++
                blocks += MdBlock.Code(language, body.joinToString("\n"))
                continue
            }

            // 块级公式：$$ 起止，可跨行
            if (text.startsWith("$$")) {
                flushAll()
                val body = mutableListOf<String>()
                val single = text.removePrefix("$$").let { if (it.endsWith("$$")) it.dropLast(2) else null }
                if (single != null) {
                    blocks += MdBlock.Formula(single.trim())
                    index++
                    continue
                }
                val head = text.removePrefix("$$").trim()
                if (head.isNotEmpty()) body += head
                index++
                while (index < lines.size && !lines[index].trim().endsWith("$$")) {
                    body += lines[index]
                    index++
                }
                if (index < lines.size) {
                    val tail = lines[index].trim().removeSuffix("$$")
                    if (tail.isNotBlank()) body += tail
                    index++
                }
                blocks += MdBlock.Formula(body.joinToString("\n"))
                continue
            }

            val heading = headingPattern.matchEntire(text)
            if (heading != null) {
                flushAll()
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                index++
                continue
            }

            if (rulePattern.matches(text)) {
                flushAll()
                blocks += MdBlock.Rule
                index++
                continue
            }

            if (text.startsWith(">")) {
                flushAll()
                val quoted = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quoted += lines[index].trim().removePrefix(">").trimStart()
                    index++
                }
                blocks += MdBlock.Quote(quoted.joinToString("\n"))
                continue
            }

            val picture = picturePattern.matchEntire(text)
            if (picture != null) {
                flushAll()
                blocks += MdBlock.Picture(
                    alt = picture.groupValues[1],
                    url = picture.groupValues[2],
                    title = picture.groupValues[3].takeIf { it.isNotBlank() },
                )
                index++
                continue
            }

            // Markdown 表格：当前行带 |，且下一行是分隔线
            if (text.contains('|') && index + 1 < lines.size && tableSeparator.matches(lines[index + 1].trim())) {
                flushAll()
                val (table, consumed) = parseMarkdownTable(lines, index)
                blocks += MdBlock.Table(table)
                index += consumed
                continue
            }

            val bullet = bulletPattern.matchEntire(text)
            if (bullet != null) {
                flushParagraph()
                orderedItems?.let { blocks += MdBlock.Ordered(orderedStart, it) }
                orderedItems = null
                val list = bullets ?: mutableListOf<MdItem>().also { bullets = it }
                val content = bullet.groupValues[1].trim()
                val task = taskPattern.matchEntire(content)
                list += if (task != null) {
                    MdItem(task.groupValues[2], task.groupValues[1] != " ")
                } else {
                    MdItem(content, null)
                }
                index++
                continue
            }

            val ordered = orderedPattern.matchEntire(text)
            if (ordered != null) {
                flushParagraph()
                bullets?.let { blocks += MdBlock.Bullets(it) }
                bullets = null
                val list = orderedItems ?: mutableListOf<MdItem>().also {
                    orderedItems = it
                    orderedStart = ordered.groupValues[1].toIntOrNull() ?: 1
                }
                list += MdItem(ordered.groupValues[2].trim(), null)
                index++
                continue
            }

            paragraph += text
            index++
        }
        flushAll()
        return blocks
    }

    private fun parseMarkdownTable(lines: List<String>, start: Int): Pair<List<List<TableCell>>, Int> {
        val rows = mutableListOf<List<TableCell>>()
        var i = start
        rows += splitTableRow(lines[i])
        i++ // 分隔线行
        i++
        while (i < lines.size && lines[i].trim().contains('|')) {
            rows += splitTableRow(lines[i])
            i++
        }
        return rows to (i - start)
    }

    private fun splitTableRow(line: String): List<TableCell> {
        val trimmed = line.trim()
        val body = trimmed.removePrefix("|").removeSuffix("|")
        return body.split("|").map { TableCell(inlineBlocks(it.trim())) }
    }
}
