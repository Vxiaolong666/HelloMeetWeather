package com.HelloMeet.weather.ui.notice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.HorizontalDivider
import com.HelloMeet.weather.data.CloudNoticeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import ui.feice.mochi.basic.MochiCheckbox
import ui.feice.mochi.theme.MochiTheme
import java.util.concurrent.TimeUnit

private data class LinkRange(val start: Int, val end: Int, val url: String)

private class InlineResult(val text: AnnotatedString, val links: List<LinkRange>)

private val tokenPattern = Regex(
    """!\[([^\]]*)]\(\s*(\S+?)(?:\s+"([^"]*)")?\s*\)""" + "|" +
        """\[([^\]]+)]\(\s*(\S+?)(?:\s+"[^"]*")?\s*\)""" + "|" +
        """\*\*([^*]+)\*\*""" + "|" +
        """__([^_]+)__""" + "|" +
        """~~([^~]+)~~""" + "|" +
        """`([^`]+)`""" + "|" +
        """\$([^$\n]+)\$""" + "|" +
        """\*([^*\n]+)\*""" + "|" +
        """_([^_\n]+)_""",
)

private val imageClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/**
 * 公告正文渲染：块级结构由 [MarkdownParser] 解析，行内样式在这里转成 AnnotatedString。
 * 公式与 Mermaid 交给 WebView（KaTeX / Mermaid），其余全部是原生 Compose，跟随主题与深色模式。
 */
@Composable
internal fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block -> BlockView(block) }
    }
}

@Composable
private fun BlockView(block: MdBlock) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    when (block) {
        is MdBlock.Heading -> {
            val size = when (block.level) {
                1 -> 21.sp
                2 -> 18.sp
                else -> 16.sp
            }
            RichText(
                text = block.text,
                style = ts.body.copy(
                    fontSize = size,
                    lineHeight = size * 1.35f,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = cs.onSurface,
                modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 0.dp),
            )
        }

        is MdBlock.Paragraph -> RichText(
            text = block.text,
            style = ts.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
            color = cs.onSurface,
        )

        is MdBlock.Quote -> Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(cs.surfaceContainerHigh)
                .drawBehind {
                    drawRect(
                        color = cs.primary.copy(alpha = 0.55f),
                        size = Size(3.dp.toPx(), size.height),
                    )
                }
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            RichText(
                text = block.text,
                style = ts.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = cs.onSurfaceVariant,
            )
        }

        is MdBlock.Code -> if (block.language.equals("mermaid", ignoreCase = true)) {
            MermaidBlock(block.code)
        } else {
            CodeBlock(block.language, block.code)
        }

        is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEach { item -> ListRow(item, index = null) }
        }

        is MdBlock.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item ->
                ListRow(item, index = block.start + index)
            }
        }

        MdBlock.Rule -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            thickness = 1.dp,
            color = cs.outlineVariant,
        )

        is MdBlock.Formula -> WebBlock(
            html = formulaHtml(block.tex, cs.isDark),
            fallback = block.tex,
        )

        is MdBlock.Picture -> RemoteImage(block.url, block.alt, block.width)

        is MdBlock.Table -> TableBlock(block)
    }
}

@Composable
private fun ListRow(item: MdItem, index: Int?) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        when {
            item.checked != null -> MochiCheckbox(
                checked = item.checked,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 8.dp, top = 1.dp),
            )

            index != null -> BasicText(
                text = "$index.",
                style = ts.body.copy(fontSize = 15.sp, lineHeight = 23.sp, color = cs.onSurfaceVariant),
                modifier = Modifier.width(24.dp).padding(top = 1.dp),
            )

            else -> Box(
                Modifier
                    .padding(start = 5.dp, end = 13.dp, top = 9.dp)
                    .width(5.dp)
                    .height(5.dp)
                    .background(cs.onSurfaceVariant, CircleShape),
            )
        }
        RichText(
            text = item.text,
            style = ts.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TableBlock(table: MdBlock.Table) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    if (table.rows.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cs.surfaceContainerHigh),
    ) {
        table.rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 10.dp), color = cs.outlineVariant)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { cell ->
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        cell.blocks.forEach { block ->
                            when (block) {
                                is MdBlock.Paragraph -> BasicText(
                                    text = block.text,
                                    style = ts.body.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (rowIndex == 0) cs.onSurface else cs.onSurfaceVariant,
                                    ),
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                is MdBlock.Picture -> RemoteImage(block.url, block.alt, block.width)

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainerHigh),
    ) {
        if (language.isNotBlank() && !language.equals("text", ignoreCase = true)) {
            BasicText(
                text = language,
                style = ts.footnote.copy(color = cs.onSurfaceVariant),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
            )
        }
        BasicText(
            text = code,
            style = ts.body.copy(
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurface,
            ),
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

@Composable
private fun RichText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val rendered = remember(text, color, cs.primary) {
        buildInline(text, color, cs.primary, cs.onSurface, cs.surfaceContainerHighest)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = rendered.text,
        modifier = modifier.pointerInput(rendered) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(position)
                rendered.links.firstOrNull { offset >= it.start && offset < it.end }
                    ?.let { runCatching { uriHandler.openUri(it.url) } }
            }
        },
        style = style.copy(color = color),
        onTextLayout = { layout = it },
    )
}

/** 无任何样式修饰的空 [SpanStyle]，用于比较「是否真的带了样式」。 */
private val emptySpan = SpanStyle()

private fun buildInline(text: String, color: Color, linkColor: Color, codeColor: Color, codeBg: Color): InlineResult {
    val links = mutableListOf<LinkRange>()
    val annotated = buildAnnotatedString {
        appendNoticeInline(text, SpanStyle(color = color), linkColor, codeColor, codeBg, links)
    }
    return InlineResult(annotated, links)
}

/** Markdown 行内解析：强调、链接、代码、公式。文本段不含 HTML 标签（由 [appendNoticeInline] 切分）。 */
private fun AnnotatedString.Builder.appendMarkdownInline(
    text: String,
    base: SpanStyle,
    linkColor: Color,
    codeColor: Color,
    codeBg: Color,
    links: MutableList<LinkRange>,
) {
    var cursor = 0
    for (match in tokenPattern.findAll(text)) {
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        cursor = match.range.last + 1
        val groups = match.groups
        when {
            // 行内图片退化成一行说明文字：图片本身按块级处理
            groups[1] != null -> append("[图片] ${groups[1]?.value.orEmpty()}")

            groups[4] != null -> {
                val start = length
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    appendMarkdownInline(groups[4]?.value.orEmpty(), base, linkColor, codeColor, codeBg, links)
                }
                links += LinkRange(start, length, groups[5]?.value.orEmpty())
            }

            groups[6] != null || groups[7] != null -> withStyle(
                SpanStyle(fontWeight = FontWeight.SemiBold),
            ) {
                appendMarkdownInline(
                    groups[6]?.value ?: groups[7]?.value.orEmpty(),
                    base, linkColor, codeColor, codeBg, links,
                )
            }

            groups[8] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendMarkdownInline(groups[8]?.value.orEmpty(), base, linkColor, codeColor, codeBg, links)
            }

            groups[9] != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor),
            ) {
                append(groups[9]?.value.orEmpty())
            }

            groups[10] != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic),
            ) {
                append(groups[10]?.value.orEmpty())
            }

            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendMarkdownInline(
                    groups[11]?.value ?: groups[12]?.value.orEmpty(),
                    base, linkColor, codeColor, codeBg, links,
                )
            }
        }
    }
    if (cursor < text.length) append(text.substring(cursor))
}

/**
 * 统一行内解析：先按 HTML 标签切分，标签段走 [appendStyledInline]（可递归），
 * 非标签段走 [appendMarkdownInline]。Markdown 与 HTML 混合时两者都不丢。
 */
private fun AnnotatedString.Builder.appendNoticeInline(
    text: String,
    base: SpanStyle,
    linkColor: Color,
    codeColor: Color,
    codeBg: Color,
    links: MutableList<LinkRange>,
) {
    val tagRe = Regex("""<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9-]*)([^>]*?)\s*/?\s*>""", RegexOption.IGNORE_CASE)
    var index = 0
    val n = text.length
    while (index < n) {
        val match = tagRe.find(text, index) ?: break
        if (match.range.first > index) {
            appendMarkdownInline(text.substring(index, match.range.first), base, linkColor, codeColor, codeBg, links)
        }
        index = match.range.last + 1
        val closing = match.groupValues[1].isNotEmpty()
        val name = match.groupValues[2].lowercase()
        val attrs = match.groupValues[3]
        if (closing) continue
        when (name) {
            "br" -> {
                append("\n")
                continue
            }
            "img" -> {
                append("[图片] ${htmlAttr(attrs, "alt").orEmpty()}")
                continue
            }
            "hr" -> continue
        }
        val closeRe = Regex("""</\s*${name}\s*>""", RegexOption.IGNORE_CASE)
        val rest = text.substring(index)
        val close = closeRe.find(rest)
        val inner = close?.let { rest.substring(0, it.range.first) } ?: rest
        appendStyledInline(name, attrs, inner, base, linkColor, codeColor, codeBg, links)
        index += (close?.let { it.range.last + 1 } ?: rest.length)
    }
    if (index < n) {
        appendMarkdownInline(text.substring(index), base, linkColor, codeColor, codeBg, links)
    }
}

private fun AnnotatedString.Builder.appendStyledInline(
    name: String,
    attrs: String,
    inner: String,
    base: SpanStyle,
    linkColor: Color,
    codeColor: Color,
    codeBg: Color,
    links: MutableList<LinkRange>,
) {
    if (name == "a") {
        val url = htmlAttr(attrs, "href")
        if (url.isNullOrBlank()) {
            appendNoticeInline(inner, base, linkColor, codeColor, codeBg, links)
            return
        }
        val start = length
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            appendNoticeInline(inner, base, linkColor, codeColor, codeBg, links)
        }
        links += LinkRange(start, length, url)
        return
    }
    val style = inlineHtmlStyle(name, attrs, codeColor, codeBg)
    if (style != emptySpan) {
        withStyle(style) { appendNoticeInline(inner, base, linkColor, codeColor, codeBg, links) }
    } else {
        appendNoticeInline(inner, base, linkColor, codeColor, codeBg, links)
    }
}

private fun inlineHtmlStyle(name: String, attrs: String, codeColor: Color, codeBg: Color): SpanStyle =
    when (name) {
        "span" -> {
            val styleAttr = htmlAttr(attrs, "style")
            SpanStyle(
                color = parseStyleColor(styleAttr) ?: Color.Unspecified,
                background = parseStyleBackground(styleAttr) ?: Color.Unspecified,
            )
        }
        "font" -> SpanStyle(color = htmlAttr(attrs, "color")?.let { parseHtmlColor(it) } ?: Color.Unspecified)
        "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
        "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
        "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
        "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        "code" -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor)
        else -> emptySpan
    }

/**
 * 公式 / Mermaid 的公共外框：WebView 加载 CDN 脚本，渲染完成后由 JS 回传高度，
 * 离线或脚本被拦时退回等宽源码，绝不留白屏。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebBlock(html: String, fallback: String) {
    var heightDp by remember(html) { mutableIntStateOf(0) }
    var failed by remember(html) { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(html) {
        delay(9_000)
        if (heightDp == 0) failed = true
    }

    if (failed) {
        CodeBlock("", fallback)
        return
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.loadsImagesAutomatically = true
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun height(value: Int) {
                            main.post { if (value > 0) heightDp = value }
                        }

                        @JavascriptInterface
                        fun fail() {
                            main.post { failed = true }
                        }
                    },
                    "Bridge",
                )
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(if (heightDp > 0) (heightDp + 2).dp else 120.dp),
        onRelease = { it.destroy() },
    )
}

private fun formulaHtml(tex: String, dark: Boolean): String {
    val color = if (dark) "#EDEDF0" else "#1B1B1F"
    val source = JSONObject.quote(tex)
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
        <style>html,body{margin:0;padding:0;background:transparent}
        #c{padding:8px 2px;text-align:center;color:$color;overflow-x:auto;font-family:serif}</style>
        </head><body><div id="c"></div>
        <script>
        (function(){
          var tex = $source;
          try {
            katex.render(tex, document.getElementById('c'), {displayMode:true, throwOnError:false});
          } catch (e) {
            document.getElementById('c').textContent = tex;
          }
          setTimeout(function(){
            Bridge.height(Math.ceil(document.getElementById('c').getBoundingClientRect().height));
          }, 80);
        })();
        </script></body></html>
    """.trimIndent()
}

private fun mermaidHtml(code: String, dark: Boolean): String {
    val theme = if (dark) "dark" else "default"
    val source = JSONObject.quote(code)
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
        <style>html,body{margin:0;padding:0;background:transparent}
        #c{padding:6px 0;text-align:center}
        #c svg{max-width:100%;height:auto}</style>
        </head><body><div id="c"></div>
        <script>
        (function(){
          function report(){
            setTimeout(function(){
              Bridge.height(Math.ceil(document.getElementById('c').getBoundingClientRect().height));
            }, 60);
          }
          try {
            mermaid.initialize({ startOnLoad:false, theme:'$theme', securityLevel:'loose' });
            mermaid.render('g1', $source).then(function(r){
              document.getElementById('c').innerHTML = r.svg;
              report();
            }).catch(function(){ Bridge.fail(); });
          } catch (e) { Bridge.fail(); }
        })();
        </script></body></html>
    """.trimIndent()
}

@Composable
private fun MermaidBlock(code: String) {
    WebBlock(html = mermaidHtml(code, MochiTheme.colorScheme.isDark), fallback = code)
}

@Composable
private fun RemoteImage(url: String, alt: String, widthPx: Int? = null) {
    val cs = MochiTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val badge = parseShieldsBadge(url)
    if (badge != null) {
        ShieldsBadge(badge)
        return
    }
    val context = LocalContext.current.applicationContext
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var preview by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        val loaded = withContext(Dispatchers.IO) { loadNoticeImage(context, url) }
        bitmap = loaded
        failed = loaded == null
    }

    val image = bitmap
    when {
        image != null -> Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = image,
                contentDescription = alt.takeIf { it.isNotBlank() },
                modifier = Modifier
                    .then(if (widthPx != null) Modifier.width(widthPx.dp) else Modifier.fillMaxWidth())
                    .clip(shape)
                    .clickable { preview = true },
                contentScale = ContentScale.FillWidth,
            )
        }

        else -> Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(shape)
                .background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = if (failed) (alt.ifBlank { url }).let { "$it（图片加载失败）" } else "正在加载图片…",
                style = MochiTheme.textStyles.footnote.copy(color = cs.onSurfaceVariant),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }

    if (preview && image != null) {
        ImagePreviewDialog(image, alt, onClose = { preview = false })
    }
}

/**
 * 加载公告图片：支持 data: URI、相对 / 协议相对路径、常见位图格式；
 * 相对路径先尝试随 APK 打包的公告图片资源（公告正文的相对 assets 没有可达的图片服务器，
 * 仓库里的配图随包内置），命中失败再按公告服务源解析并走网络。
 */
private fun loadNoticeImage(context: Context, url: String): ImageBitmap? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("data:")) return decodeDataUri(trimmed)
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("//")) {
        loadNoticeAsset(context, trimmed)?.let { return it }
    }
    val resolved = resolveNoticeUrl(trimmed) ?: return null
    return runCatching {
        val request = Request.Builder()
            .url(resolved)
            .header("User-Agent", "Mozilla/5.0 (Android) ZhishengWeather")
            .build()
        imageClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            decodeNoticeBitmap(bytes)
        }
    }.getOrNull()
}

/** 公告正文里的相对图片随 APK 内置在 assets/notice/ 下，按文件名匹配加载。 */
private fun loadNoticeAsset(context: Context, url: String): ImageBitmap? {
    val name = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
    if (name.isBlank()) return null
    return runCatching {
        context.assets.open("notice/$name").use { stream ->
            decodeNoticeBitmap(stream.readBytes())
        }
    }.getOrNull()
}

/** 公告图片 URL 解析：绝对 URL 原样，`//` 补 https，相对路径按公告服务源解析。 */
private fun resolveNoticeUrl(url: String): String? {
    val u = url.trim()
    if (u.isEmpty()) return null
    if (u.startsWith("//")) return "https:$u"
    if (u.startsWith("http://") || u.startsWith("https://")) return u
    return CloudNoticeRepository.noticeOrigin + "/" + u.trimStart('/')
}

/** shields.io 徽章（img.shields.io/badge/<主题>-<状态>-<颜色>）：SVG 无法用位图解码，改为原生绘制。 */
private data class ShieldsBadge(val subject: String, val status: String, val color: Color, val labelColor: Color)

private fun parseShieldsBadge(url: String): ShieldsBadge? {
    if (!url.contains("img.shields.io")) return null
    val path = Regex("""/badge/([^?]+)""").find(url) ?: return null
    val parts = path.groupValues[1].split("-").map { Uri.decode(it).trim() }
    if (parts.size < 2) return null
    val status = parts[parts.size - 2]
    val subject = parts.take(parts.size - 2).joinToString("-")
    val color = parseHexColor(parts.last()) ?: return null
    val labelColor = Regex("""labelColor=([0-9a-fA-F]{6})""").find(url)?.groupValues?.get(1)
        ?.let { parseHexColor(it) } ?: Color(0xFF555555)
    return ShieldsBadge(subject, status, color, labelColor)
}

private fun parseHexColor(hex: String): Color? {
    val h = hex.removePrefix("#")
    return when (h.length) {
        6 -> h.toLongOrNull(16)?.let { Color(0xFF000000L or it) }
        3 -> h.toLongOrNull(16)?.let { v ->
            val r = (v shr 8) and 0xF
            val g = (v shr 4) and 0xF
            val b = v and 0xF
            Color(0xFF000000L or (r * 17L shl 16) or (g * 17L shl 8) or (b * 17L))
        }
        else -> null
    }
}

@Composable
private fun ShieldsBadge(badge: ShieldsBadge) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .height(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (badge.subject.isNotBlank()) {
                Box(
                    Modifier
                        .background(badge.labelColor)
                        .padding(horizontal = 7.dp)
                        .height(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = badge.subject,
                        style = MochiTheme.textStyles.footnote.copy(fontSize = 10.sp, color = Color.White),
                    )
                }
            }
            Box(
                Modifier
                    .background(badge.color)
                    .padding(horizontal = 7.dp)
                    .height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = badge.status,
                    style = MochiTheme.textStyles.footnote.copy(fontSize = 10.sp, color = Color.White),
                )
            }
        }
    }
}

private fun decodeDataUri(uri: String): ImageBitmap? {
    val comma = uri.indexOf(',')
    if (comma < 0) return null
    val meta = uri.substring(5, comma)
    val payload = uri.substring(comma + 1)
    val bytes = if (meta.contains(";base64", ignoreCase = true)) {
        runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return null
    } else {
        payload.toByteArray()
    }
    return decodeNoticeBitmap(bytes)
}

private fun decodeNoticeBitmap(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

/** 全屏黑底大图预览：点按关闭，双指缩放、单指平移。 */
@Composable
private fun ImagePreviewDialog(image: ImageBitmap, alt: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        ) {
            Image(
                bitmap = image,
                contentDescription = alt.takeIf { it.isNotBlank() },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
