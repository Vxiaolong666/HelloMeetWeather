// Copyright 2026, mochi-compose contributors
// SPDX-License-Identifier: Apache-2.0

package ui.feice.mochi.basic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import ui.feice.mochi.effect.content.MochiBlurTransitions
import ui.feice.mochi.effect.content.mochiBlur
import ui.feice.mochi.effect.content.mochiBlurAlpha
import ui.feice.mochi.utils.smoothstep

/**
 * 标签一格的读屏语义：整格是一个「标签页」节点，名字是标签文字。只显示图标、或标签宽度还是 0 时，
 * 文字不可见，读屏和测试都靠这里找到它；整格合成一个节点，选中时不会把图标和文字读两遍
 */
internal fun Modifier.tabSemantics(label: String, selected: Boolean, onClick: () -> Unit): Modifier = clearAndSetSemantics {
    role = Role.Tab
    contentDescription = label
    this.selected = selected
    onClick {
        onClick()
        true
    }
}

/**
 * 横排标签的一格：图标在左、标签在右，整体在 [width] 给的格宽里居中。
 * 标签占的宽度 = [extra] × 选中程度，没选中时图标正好在格子中间；标签跟着选中程度淡入，超出格子的部分裁掉
 */
@Composable
internal fun InlineTabCell(
    width: () -> Float,
    selectedness: () -> Float,
    extra: Float,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    blurLabels: Boolean = true,
    label: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(icon, {
            Box(
                Modifier.graphicsLayer {
                    mochiBlurAlpha(labelAlpha(selectedness()), labelBlur(selectedness(), blurLabels))
                }.mochiBlur { labelBlur(selectedness(), blurLabels) },
            ) { label() }
        }),
        modifier = modifier.fillMaxHeight().clipToBounds(),
    ) { (iconM, labelM), c ->
        val w = width().roundToInt()
        val h = c.maxHeight
        val ip = iconM.first().measure(Constraints())
        val lp = labelM.first().measure(Constraints())
        layout(w, h) {
            val group = ip.width + extra * selectedness()
            val x = ((w - group) / 2f).roundToInt()
            ip.place(x, (h - ip.height) / 2)
            lp.place(x + ip.width + InlineLabelGap.roundToPx(), (h - lp.height) / 2)
        }
    }
}

/**
 * 只在选中时显示的标签跟着选中程度 [t] 出现 / 消失：模糊淡入淡出（和 [MochiBlurTransitions.Appear] 同样的模糊，
 * 和隐去程度成正比、分档复用模糊效果）。位移由调用方照常给，这里只管透明度和模糊
 */
internal fun labelAlpha(t: Float): Float = smoothstep(0.35f, 1f, t)

internal fun labelBlur(t: Float, enabled: Boolean = true): Dp {
    if (!enabled || labelAlpha(t) < 0.02f) return 0.dp
    val hidden = 1f - t.coerceIn(0f, 1f)
    return MochiBlurTransitions.Appear.blur * hidden
}

/**
 * 上下（竖向）排布时所有标签都常驻，模糊只跟着选中块走：只糊选中块最近的那一格，
 * 其余标签全程清晰（同一时刻最多一格满足 [stackedBlurPhase] > 0）。
 *
 * 距离以这一格的图标为中心算：选中块正对着图标时最清晰，越往两侧离开越糊，
 * 到两格交界（选中程度 = 0.5）之前收干净，交接给下一格时也是从 0 起，
 * 不会出现两格一起糊或换格时突然跳一下。
 */
internal fun stackedLabelBlur(selectedness: Float, enabled: Boolean = true): Dp {
    if (!enabled) return 0.dp
    val phase = stackedBlurPhase(selectedness)
    if (phase < 0.02f) return 0.dp
    return StackedBlurMax * phase
}

/**
 * 上下排布拖拽时的最大模糊。常驻的 10sp 小字只需要一点点动态模糊，
 * 比横排「整段隐去」用的 [MochiBlurTransitions.Appear.blur]（10dp）低得多，拖到哪儿都读得清。
 */
internal val StackedBlurMax = 2.5.dp

/**
 * 上下排布里一格的模糊进度（0 = 清晰，1 = 最深）：只有离选中块最近的那一格会大于 0。
 *
 * 选中程度 t = 1 时选中块正对这一格的图标，越偏越糊，t = 0.5（两格交界）前回到 0；
 * 起始那一段用 [smoothstep] 淡入，换格时前一格已经收干净，不会出现两格同时糊。
 */
internal fun stackedBlurPhase(selectedness: Float): Float {
    val t = selectedness.coerceIn(0f, 1f)
    val near = smoothstep(0.5f, 0.62f, t)
    if (near <= 0f) return 0f
    // 1 - t：选中块离这一格图标的距离（0 = 正对，0.5 = 两格正中间），放大成 0..1
    return near * ((1f - t) * 2f).coerceIn(0f, 1f)
}

/**
 * 上下排布的文字常驻、没有淡入。模糊只在最近的一格上出现，这里按同一条进度把透明度轻轻压一点，
 * 免得起糊时像叠了一层重影；幅度很小，文字始终以接近满浓度显示。
 */
internal fun stackedLabelAlpha(selectedness: Float): Float =
    1f - 0.08f * stackedBlurPhase(selectedness)

/** 只在选中时显示标签（竖排）时，没选中的图标往下挪多少才在整格里居中：(2dp 间距 + 12sp 行高) / 2 */
internal val LabelShift = 7.dp

/** 横排标签和图标之间的距离 */
internal val InlineLabelGap = 6.dp

/** 横排在图标右边的标签：比竖排的大一号，和图标同一行 */
internal val InlineLabelStyle = TextStyle(
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

internal val TabLabelStyle = TextStyle(
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
