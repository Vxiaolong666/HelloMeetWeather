package com.zhisheng.weather.ui.notice

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
    data class Picture(val alt: String, val url: String, val title: String?) : MdBlock
}

internal data class MdItem(val text: String, val checked: Boolean?)

/**
 * 极简 Markdown 解析：只覆盖公告实际会用到的语法。
 * 不用第三方库，避免为一段说明文字引入整套解析器与图片加载依赖。
 */
internal object MarkdownParser {

    private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")
    private val orderedPattern = Regex("^(\\d{1,9})[.)]\\s+(.*)$")
    private val bulletPattern = Regex("^[-*+]\\s+(.*)$")
    private val taskPattern = Regex("^\\[([ xX])]\\s+(.*)$")
    private val rulePattern = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val picturePattern = Regex("^!\\[([^]]*)]\\(\\s*(\\S+?)(?:\\s+\"([^\"]*)\")?\\s*\\)$")

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        var bullets: MutableList<MdItem>? = null
        var orderedItems: MutableList<MdItem>? = null
        var orderedStart = 1

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
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
}
