package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.PhosphorIcon
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiConfirmationDialog
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.MochiToastState
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.navigation.MochiNavigator
import ui.feice.mochi.theme.MochiTheme

/**
 * 全 app 共用一个 Toast 状态与 Host（demo 的 MochiAppStarter 就是这么接的）。
 * 放在根部，nav.pop() / 页面返回后提示仍然能显示完。
 */
internal val LocalMochiToast = staticCompositionLocalOf<MochiToastState?> { null }

/** demo「共享顶栏按钮」：所有二级设置页复用同一个语义 navId，换页时按钮留在原位、内容原地替换。 */
internal const val MochiSettingsSaveNavId = "app.settings.save"

@Composable
internal fun MochiAppToastHost(state: MochiToastState) {
    val colors = rememberMochiColorScheme()
    MochiTheme(colors = colors, systemBarAppearance = false) {
        MochiToastHost(state)
    }
}

/** 供 MochiBackButton(history=…) 用的返回历史标题。 */
internal fun mochiSettingsRouteTitle(route: String): String = when (route) {
    "settings.hdr" -> "HDR 高光"
    "settings.pressGlow" -> "按压高光"
    "settings.transition" -> "应用转场与侧推"
    "settings.theme" -> "主题配色"
    "settings.tabbar" -> "底部标签栏"
    "settings.graphics" -> "画面效果"
    "settings.automation" -> "刷新与天气提醒"
    "settings.modules" -> "模块布局"
    "settings.telemetry" -> "遥测项目"
    "settings.lifeIndex" -> "生活指数项目"
    "settings.widgets" -> "小组件与通知栏"
    "settings.icons" -> "应用图标"
    else -> "设置"
}

/**
 * 所有二级设置页的统一外壳：库紧凑标题骨架 + 库共享顶栏「保存」按钮 + 库返回按钮。
 * 设置本来就是即点即存，保存按钮沿用 demo「完成」的语义：给一条「设置已保存」提示并回到上一页。
 */
@Composable
internal fun MochiSettingsScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showSave: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val toast = LocalMochiToast.current
    val navigator = MochiNavigator.current
    MochiLazyCompactTitleScaffold(
        title = title,
        subtitle = subtitle,
        navigationIcon = { MochiBackButton(onClick = onBack, history = ::mochiSettingsRouteTitle) },
        actions = actions ?: if (showSave) {
            {
                MochiBarButton(
                    "保存",
                    onClick = {
                        toast?.show("保存成功", icon = { tint ->
                            PhosphorIcon(R.drawable.ph_check, null, Modifier.size(20.dp), tint)
                        })
                        if (onBack != null) onBack() else navigator?.pop()
                    },
                    navId = if (navigator != null) MochiSettingsSaveNavId else null,
                )
            }
        } else null,
        content = content,
    )
}

/**
 * 所有 mochi 版二级页的统一外壳：库主题 + 越界手感 + 统一顶栏。
 * [showSave] 只有设置链路的跨页二级页打开，天气详情类页面保持原来的纯返回顶栏。
 */
@Composable
internal fun MochiPageFrame(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    showSave: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val colors = rememberMochiColorScheme()
    MochiTheme(colors = colors, systemBarAppearance = false) {
        ProvideMochiOverscroll {
            MochiSettingsScaffold(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                showSave = showSave,
                actions = actions,
                content = content,
            )
            overlay()
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

@Composable
internal fun MochiToastCheckIcon(tint: Color) {
    PhosphorIcon(R.drawable.ph_check, null, Modifier.size(20.dp), tint)
}
