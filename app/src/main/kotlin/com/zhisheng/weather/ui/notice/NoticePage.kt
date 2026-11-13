package com.zhisheng.weather.ui.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.data.CloudNotice
import com.zhisheng.weather.data.CloudNoticeRepository
import com.zhisheng.weather.ui.MochiFadeNote
import com.zhisheng.weather.ui.MochiPageCard
import com.zhisheng.weather.ui.MochiPageFrame
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.theme.MochiTheme

private sealed interface NoticeUiState {
    data object Loading : NoticeUiState
    data class Ready(val items: List<CloudNotice>) : NoticeUiState
    data class Failed(val message: String) : NoticeUiState
}

/**
 * 独立公告页：从「关于」进入，走设置页同一套库导航与转场。
 * 正文外壳由 [MochiPageFrame] 统一给出左右 16dp，卡片不再自行加边距。
 */
@Composable
internal fun NoticePage(onBack: () -> Unit, onOpen: (CloudNotice) -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<NoticeUiState>(NoticeUiState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        state = NoticeUiState.Loading
        state = runCatching { CloudNoticeRepository.notices(context, force = attempt > 0) }
            .fold(
                onSuccess = { NoticeUiState.Ready(it) },
                onFailure = { NoticeUiState.Failed(it.message?.takeIf(String::isNotBlank) ?: "公告获取失败") },
            )
    }

    MochiPageFrame(title = "公告", onBack = onBack) {
        when (val current = state) {
            NoticeUiState.Loading -> item {
                MochiFadeNote("正在获取公告…")
            }

            is NoticeUiState.Failed -> {
                item {
                    MochiFadeNote(current.message, title = "没能取到公告")
                }
                item {
                    MochiPageCard(contentPadding = PaddingValues(0.dp)) {
                        MochiListRow("重新获取", onClick = { attempt++ }, chevron = true)
                    }
                }
            }

            is NoticeUiState.Ready -> if (current.items.isEmpty()) {
                item {
                    MochiFadeNote("暂时没有公告。")
                }
            } else {
                items(current.items.size) { index ->
                    val notice = current.items[index]
                    NoticeCard(notice, onClick = { onOpen(notice) })
                }
            }
        }
    }
}

/** 公告详情：标题进顶栏，正文按 Markdown 渲染。 */
@Composable
internal fun NoticeDetailPage(notice: CloudNotice?, onBack: () -> Unit) {
    MochiPageFrame(title = "公告详情", onBack = onBack) {
        if (notice == null) {
            item { MochiFadeNote("这条公告已不可用。") }
            return@MochiPageFrame
        }
        item { NoticeHeader(notice) }
        item {
            MochiPageCard(animateHeight = false) {
                MarkdownView(notice.body)
            }
        }
    }
}

@Composable
private fun NoticeHeader(notice: CloudNotice) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    MochiPageCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = notice.title,
                style = ts.titleMedium.copy(color = cs.onSurface),
                modifier = Modifier.weight(1f),
            )
            if (notice.pinned) {
                Text(
                    "置顶",
                    style = ts.footnote,
                    color = cs.onPrimary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(cs.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (notice.publishedAt.isNotBlank()) {
            BasicText(
                text = notice.publishedAt,
                style = ts.footnote.copy(color = cs.onSurfaceVariant),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NoticeCard(notice: CloudNotice, onClick: () -> Unit) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    MochiPageCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = notice.title,
                style = ts.titleMedium.copy(color = cs.onSurface),
                modifier = Modifier.weight(1f),
            )
            if (notice.pinned) {
                Text(
                    "置顶",
                    style = ts.footnote,
                    color = cs.onPrimary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(cs.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (notice.publishedAt.isNotBlank()) {
            BasicText(
                text = notice.publishedAt,
                style = ts.footnote.copy(color = cs.onSurfaceVariant),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val preview = noticePreview(notice.body)
        if (preview.isNotBlank()) {
            BasicText(
                text = preview,
                style = ts.body.copy(fontSize = 14.sp, lineHeight = 21.sp, color = cs.onSurfaceVariant),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val previewTokens = Regex(
    """```[\s\S]*?```|\$\$[\s\S]*?\$\$|!\[[^\]]*]\(\S*?\)|\[([^\]]+)]\(\S*?\)|[*_~`>$#]""",
)

/** 列表预览：去掉语法符号，压成一行，最多三行显示。 */
private fun noticePreview(markdown: String): String {
    val stripped = previewTokens.replace(markdown) { match ->
        match.groupValues[1].ifBlank { " " }
    }
    return stripped.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
