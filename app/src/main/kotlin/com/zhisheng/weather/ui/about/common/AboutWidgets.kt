// 迁移自 mochi-compose demo 的关于页所用通用组件（demoItem / AppIcon / Tag）。
// 仅依赖 mochi 库原生 API，不依赖任何 demo 图片资源。

package com.zhisheng.weather.ui.about.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.Text
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

/**
 * 关于页（懒加载列表）里的一项：一般是一个分组；放几个时竖着排，间距和分组之间一样。
 */
internal fun LazyListScope.demoItem(key: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    item(key) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing), content = content)
    }
}

/** 图标块的配色 */
internal class IconColors(val container: Color, val content: Color)

/** 图标块：连续曲率圆角方块 + 实心图标（形状和实心图标学 iOS，颜色用 MD3 的容器色） */
@Composable
internal fun AppIcon(
    glyph: DemoIcon,
    colors: IconColors,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = size * 0.3f,
    shape: Shape = MochiRoundedRectangle(corner),
) {
    Box(
        modifier.size(size).background(colors.container, shape),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, colors.content, size = (size.value * 0.6f).toInt(), filled = true) }
}

/** 小标签 */
@Composable
internal fun Tag(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.background(bg, MochiTheme.shapes.full).padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, style = MochiTheme.textStyles.labelSmall, color = fg) }
}
