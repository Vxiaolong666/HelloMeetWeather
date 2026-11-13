package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.ui.components.PhosphorIcon
import ui.feice.mochi.effect.mask.MochiFadeEdges
import ui.feice.mochi.effect.mask.mochiFadeMask
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/**
 * 说明 / 补充文字卡：库分组卡片承载段落，卡片自己按 demo「渐变遮罩」处理边缘。
 *
 * 卡片高度由文字撑开，不固定高度、卡内不滚动；[mochiFadeMask] 擦掉的是内容本身，
 * 所以放在任何底色上都干净，段落一字不改。
 */
@Composable
internal fun MochiFadeNote(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    iconRes: Int? = null,
    accent: Color? = null,
) {
    MochiFadeNote(listOf(text), modifier, title, iconRes, accent)
}

/** 多条说明段落的版本：每段保持原样，各占一行，卡片高度由段落撑开。 */
@Composable
internal fun MochiFadeNote(
    lines: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
    iconRes: Int? = null,
    accent: Color? = null,
) {
    val cs = MochiTheme.colorScheme
    MochiListSection(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .mochiFadeMask(MochiFadeEdges(top = 10.dp, bottom = 22.dp))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (iconRes != null) {
                        PhosphorIcon(iconRes, null, Modifier.size(14.dp), accent ?: cs.primary)
                    }
                    Text(title, style = MochiTheme.textStyles.navTitle, color = accent ?: cs.onSurface)
                }
            }
            lines.forEach { line ->
                Text(line, style = MochiTheme.textStyles.footnote, color = cs.onSurfaceVariant)
            }
        }
    }
}

/**
 * 用库卡片承载页面自己的图表或自定义内容：只换容器，内容原样放进来。
 * 内容和间距仍由调用方决定，卡片只负责库的背景、圆角、分组小标题与内边距。
 *
 * 直接往 [MochiListSection] 里塞自定义内容是不带内边距的（那是给 [MochiListRow] 用的行容器），
 * 元素会贴着卡片左右边；走这里才和设置页的卡片一致。
 */
@Composable
internal fun MochiPageCard(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    animateHeight: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    MochiListSection(
        modifier = modifier,
        header = header,
        footer = footer,
        animateHeight = animateHeight,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * 只借库卡片的底色与圆角（和 [MochiListCard] 同一套 token），给那种已经自己排好
 * 内外边距、不方便再套一层 Column 的旧卡片用：换成库的容器，内容一行都不用动。
 */
@Composable
internal fun Modifier.mochiCardSurface(): Modifier {
    val shape = MochiRoundedRectangle(MochiListDefaults.SectionCorner)
    return this.clip(shape).background(MochiDefaults.groupedContainer(), shape)
}
