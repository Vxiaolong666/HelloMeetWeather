package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiConfirmationDialog
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.theme.MochiTheme

/**
 * 所有 mochi 版二级页的统一外壳：库主题 + 越界手感 + 库紧凑标题骨架 + 库返回按钮。
 * 页面内容统一用 [MochiListSection] / [MochiListRow] 组织，业务自绘图表作为 item 嵌入。
 */
@Composable
internal fun MochiPageFrame(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = rememberMochiColorScheme()
    MochiTheme(colors = colors, systemBarAppearance = false) {
        ProvideMochiOverscroll {
            MochiLazyCompactTitleScaffold(
                title = title,
                subtitle = subtitle,
                navigationIcon = { MochiBackButton(onClick = onBack) },
                content = content,
            )
        }
    }
}

/**
 * 选项弹窗：直接用库 [MochiDialog]，内容区放库列表行；按钮用 [MochiDialogAction]。
 * 库在点击任意 action 后自动回调 onDismissRequest，调用方不需要再手动清一次状态。
 * 之前这里把库组件塞进 androidx.compose.ui.window.Dialog，属于错误用法：库对话框自己用
 * InWindowOverlay 管理窗口，外面再套一层反而丢了主题、遮罩与预测性返回。
 */
@Composable
internal fun MochiChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    enabled: (String) -> Boolean = { true },
    footer: String? = null,
    visible: Boolean = true,
) {
    val message = listOfNotNull(subtitle, footer).joinToString("\n").takeIf { it.isNotBlank() }
    MochiDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        dismissOnOutsideTap = true,
        actions = persistentListOf(
            MochiDialogAction("取消", MochiDialogActionRole.Cancel),
        ),
    ) {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            MochiListSection {
                options.forEach { (label, key) ->
                    MochiListRow(
                        title = label,
                        checked = key == selected,
                        enabled = enabled(key),
                        onClick = {
                            onSelect(key)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/** 确认弹窗：库 [MochiConfirmationDialog]，取消 / 警示两个标准 action。 */
@Composable
internal fun MochiConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    visible: Boolean = true,
) {
    MochiConfirmationDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        actions = persistentListOf(
            MochiDialogAction(confirmLabel, if (destructive) MochiDialogActionRole.Destructive else MochiDialogActionRole.Primary) {
                onConfirm()
            },
            MochiDialogAction("取消", MochiDialogActionRole.Cancel),
        ),
    )
}

/** 弹窗列表里的小标题行，避免直接用 Material Text 造成风格断层。 */
@Composable
internal fun MochiSectionNote(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
