package com.HelloMeet.weather.ui.notice

import androidx.compose.ui.graphics.Color

/**
 * 极简 HTML 解析辅助：供 [MarkdownParser] 在行首识别到 HTML 块级元素后，整段消费并转成 [MdBlock]。
 * 行内标签（span / font / b / strong / i / em / u / s / strike / del / code / a / br 等）
 * 保留原文，由 [MarkdownView] 的行内解析再处理颜色与样式；未知标签直接剥掉正文继续。
 * 不用第三方库，避免为一段说明文字引入整套解析器与图片加载依赖。
 */
internal object HtmlParser {

    private val blockTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "table")
    private val voidTags = setOf("img", "hr", "br")

    private val blockTagRe = Regex(
        """</?(?:p|div|h[1-6]|ul|ol|li|blockquote|pre|table|thead|tbody|tr|td|th|hr)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

    private class OpenTag(val name: String, val attrs: String, val selfClosing: Boolean, val end: Int)

    /**
     * 从 [startLine]（其行首是一个 HTML 块级标签）消费该元素（可跨多行），
     * 返回块列表与消耗的行数。
     */
    fun consumeElement(lines: List<String>, startLine: Int): Pair<List<MdBlock>, Int> {
        val line0 = lines[startLine]
        val lt = line0.indexOf('<')
        if (lt < 0) return emptyList<MdBlock>() to 1
        val joined = lines.subList(startLine, lines.size).joinToString("\n")
        val open = parseTagAt(joined, lt) ?: return emptyList<MdBlock>() to 1
        val name = open.name
        if (name !in blockTags && name !in voidTags) return emptyList<MdBlock>() to 1

        if (name in voidTags || open.selfClosing) {
            val block = when (name) {
                "img" -> {
                    val src = htmlAttr(open.attrs, "src") ?: htmlAttr(open.attrs, "data-src")
                    src?.let {
                        MdBlock.Picture(
                            alt = htmlAttr(open.attrs, "alt").orEmpty(),
                            url = it,
                            title = null,
                            width = htmlAttr(open.attrs, "width")?.toIntOrNull(),
                        )
                    }
                }
                "hr" -> MdBlock.Rule
                else -> null
            }
            return listOfNotNull(block) to (1 + joined.substring(0, open.end).count { it == '\n' })
        }

        // 深度感知地找匹配闭合标签
        val nameRe = Regex("""</?\s*${name}\b[^>]*>""", RegexOption.IGNORE_CASE)
        var depth = 1
        var cursor = open.end
        var innerEnd = -1
        var closeEnd = -1
        while (true) {
            val m = nameRe.find(joined, cursor) ?: break
            if (m.value.startsWith("</")) depth--
            else if (!m.value.trimEnd().endsWith("/>")) depth++
            if (depth == 0) {
                innerEnd = m.range.first
                closeEnd = m.range.last + 1
                break
            }
            cursor = m.range.last + 1
        }
        val inner = if (innerEnd >= 0) joined.substring(open.end, innerEnd) else joined.substring(open.end)
        val consumed = (if (closeEnd >= 0) closeEnd else joined.length)
        val blocks = elementToBlocks(name, inner)
        return blocks to (1 + joined.substring(0, consumed).count { it == '\n' })
    }

    private fun elementToBlocks(name: String, inner: String): List<MdBlock> = when (name) {
        "p" -> inlineBlocks(stripBlockTags(inner))

        "h1", "h2", "h3", "h4", "h5", "h6" ->
            listOfNotNull(inner.takeIf { it.isNotBlank() }?.let { MdBlock.Heading(name[1] - '0', stripBlockTags(it)) })

        "blockquote" ->
            listOfNotNull(inner.takeIf { it.isNotBlank() }?.let { MdBlock.Quote(stripBlockTags(it)) })

        "pre" -> listOfNotNull(
            decodeEntities(stripHtmlTags(inner)).takeIf { it.isNotBlank() }?.let { MdBlock.Code("", it) },
        )

        "ul" -> listOfNotNull(extractListItems(inner).takeIf { it.isNotEmpty() }?.let { MdBlock.Bullets(it) })
        "ol" -> listOfNotNull(extractListItems(inner).takeIf { it.isNotEmpty() }?.let { MdBlock.Ordered(1, it) })
        "table" -> listOfNotNull(extractTable(inner)?.let { MdBlock.Table(it) })
        "div" -> MarkdownParser.parse(inner)
        else -> emptyList()
    }

    private fun extractListItems(inner: String): List<MdItem> {
        val liOpen = Regex("""<\s*li\b[^>]*>""", RegexOption.IGNORE_CASE)
        val items = mutableListOf<MdItem>()
        var cursor = 0
        for (m in liOpen.findAll(inner)) {
            if (m.range.first < cursor) continue
            val rest = inner.substring(m.range.last + 1)
            val close = Regex("""</\s*li\s*>""", RegexOption.IGNORE_CASE).find(rest)
            val content = close?.let { rest.substring(0, it.range.first) } ?: rest
            items += MdItem(stripBlockTags(content).trim(), null)
            cursor = m.range.last + 1 + (close?.let { it.range.last + 1 } ?: rest.length)
        }
        return items
    }

    private fun extractTable(inner: String): List<List<TableCell>>? {
        val trOpen = Regex("""<\s*tr\b[^>]*>""", RegexOption.IGNORE_CASE)
        val rows = mutableListOf<List<TableCell>>()
        var cursor = 0
        for (m in trOpen.findAll(inner)) {
            if (m.range.first < cursor) continue
            val rest = inner.substring(m.range.last + 1)
            val close = Regex("""</\s*tr\s*>""", RegexOption.IGNORE_CASE).find(rest)
            val content = close?.let { rest.substring(0, it.range.first) } ?: rest
            val cells = extractCells(content)
            if (cells.isNotEmpty()) rows += cells
            cursor = m.range.last + 1 + (close?.let { it.range.last + 1 } ?: rest.length)
        }
        return rows.takeIf { it.isNotEmpty() }
    }

    private fun extractCells(trContent: String): List<TableCell> {
        val cellOpen = Regex("""<\s*(?:td|th)\b[^>]*>""", RegexOption.IGNORE_CASE)
        val cells = mutableListOf<TableCell>()
        var cursor = 0
        for (m in cellOpen.findAll(trContent)) {
            if (m.range.first < cursor) continue
            val rest = trContent.substring(m.range.last + 1)
            val close = Regex("""</\s*(?:td|th)\s*>""", RegexOption.IGNORE_CASE).find(rest)
            val content = close?.let { rest.substring(0, it.range.first) } ?: rest
            cells += TableCell(inlineBlocks(stripBlockTags(content).trim()))
            cursor = m.range.last + 1 + (close?.let { it.range.last + 1 } ?: rest.length)
        }
        return cells
    }

    /** 去掉块级标签（保留行内标签与内容），用于取 p / li / td 等元素的行内文本。 */
    private fun stripBlockTags(text: String): String = blockTagRe.replace(text, "")

    private val tagPattern = Regex("""<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9-]*)([^>]*?)\s*(/?)\s*>""")

    private fun parseTagAt(source: String, index: Int): OpenTag? {
        val m = tagPattern.find(source, index) ?: return null
        if (m.range.first != index) return null
        val name = m.groupValues[2].lowercase()
        if (name.isEmpty()) return null
        val selfClosing = m.groupValues[4].isNotEmpty() || m.value.endsWith("/>")
        return OpenTag(name, m.groupValues[3].trim(), selfClosing, m.range.last + 1)
    }
}

/** 取 HTML 标签属性（支持单双引号与无引号）。 */
internal fun htmlAttr(attrs: String, name: String): String? {
    val re = Regex(
        """(?:^|\s)${name}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
        RegexOption.IGNORE_CASE,
    )
    return re.find(attrs)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
}

/** 解析 HTML 颜色：#rgb / #rrggbb / #rrggbbaa、rgb()/rgba()、常见颜色名。 */
internal fun parseHtmlColor(value: String): Color? {
    val v = value.trim().trimEnd(';').trim()
    if (v.isEmpty()) return null
    val func = Regex(
        """rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([\d.]+))?\s*\)""",
        RegexOption.IGNORE_CASE,
    ).matchEntire(v)
    if (func != null) {
        val r = func.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val g = func.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val b = func.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
        val a = func.groupValues[4].takeIf { it.isNotBlank() }?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        return Color(r / 255f, g / 255f, b / 255f, a)
    }
    return runCatching { Color(android.graphics.Color.parseColor(v)) }.getOrNull()
}

/** 取 span 的 style 属性里的 color（支持 color:red / color:#fff / color:rgb(…, …)）。 */
internal fun parseStyleColor(style: String?): Color? {
    if (style == null) return null
    val m = Regex("""(?:^|;)\s*color\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE).find(style) ?: return null
    return parseHtmlColor(m.groupValues[1])
}

/** 取 span 的 style 属性里的 background / background-color。 */
internal fun parseStyleBackground(style: String?): Color? {
    if (style == null) return null
    val m = Regex("""(?:^|;)\s*background(?:-color)?\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE).find(style) ?: return null
    return parseHtmlColor(m.groupValues[1])
}

internal fun decodeEntities(text: String): String = text
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")

internal fun stripHtmlTags(text: String): String = Regex("<[^>]+>").replace(text, "")
