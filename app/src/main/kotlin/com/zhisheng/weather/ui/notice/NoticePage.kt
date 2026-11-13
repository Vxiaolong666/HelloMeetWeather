package com.zhisheng.weather.ui.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 每次进页面先读进程内快照，失败时给一行重试，不阻塞返回。
 */
@Composable
internal fun NoticePage(onBack: () -> Unit) {
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
                MochiFadeNote("正在获取公告…", modifier = Modifier.padding(horizontal = 16.dp))
            }

            is NoticeUiState.Failed -> {
                item {
                    MochiFadeNote(
                        current.message,
                        title = "没能取到公告",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    MochiPageCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        MochiListRow("重新获取", onClick = { attempt++ }, chevron = true)
                    }
                }
            }

            is NoticeUiState.Ready -> if (current.items.isEmpty()) {
                item {
                    MochiFadeNote("暂时没有公告。", modifier = Modifier.padding(horizontal = 16.dp))
                }
            } else {
                items(current.items.size) { index ->
                    NoticeCard(current.items[index], Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: CloudNotice, modifier: Modifier = Modifier) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    MochiPageCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                notice.title,
                style = ts.titleMedium,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (notice.pinned) {
                Spacer(Modifier.padding(start = 8.dp))
                Text(
                    "置顶",
                    style = ts.footnote,
                    color = cs.onPrimary,
                    modifier = Modifier
                        .background(cs.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (notice.publishedAt.isNotBlank()) {
            Text(
                notice.publishedAt,
                style = ts.footnote,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (notice.body.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                notice.body,
                style = ts.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = cs.onSurface,
            )
        }
    }
}
