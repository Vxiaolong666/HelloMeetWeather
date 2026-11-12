package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiCheckbox
import ui.feice.mochi.basic.MochiConfirmationDialog
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiDialogDefaults
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.MochiToastState
import ui.feice.mochi.utils.rememberMochiHaptics
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
    MochiAppTheme(colors = colors) {
        MochiToastHost(state)
    }
}

/** 供 MochiBackButton(history=…) 用的返回历史标题。 */
internal fun mochiSettingsRouteTitle(route: String): String = when (route) {
    "settings.about" -> "关于"
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
    MochiAppTheme(colors = colors) {
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
    iconFor: ((String) -> Int?)? = null,
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
                        leading = iconFor?.invoke(key)?.let { icon ->
                            { PhosphorIcon(icon, null, Modifier.size(22.dp), mochiLeadingIconTint()) }
                        },
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
    confirmFilledError: Boolean = false,
    visible: Boolean = true,
) {
    MochiConfirmationDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        // 填充红色：确认按钮用错误色底 + 反白字，破坏性操作在警告框里最醒目
        colors = if (confirmFilledError) {
            MochiDialogDefaults.colors(
                primary = MochiTheme.colorScheme.error,
                onPrimary = MochiTheme.colorScheme.onError,
            )
        } else {
            MochiDialogDefaults.colors()
        },
        actions = persistentListOf(
            MochiDialogAction(
                confirmLabel,
                if (destructive && !confirmFilledError) MochiDialogActionRole.Destructive else MochiDialogActionRole.Primary,
            ) {
                onConfirm()
            },
            MochiDialogAction("取消", MochiDialogActionRole.Cancel),
        ),
    )
}

/**
 * Beta 功能提示：照 demo 的 DemoNotice，主按钮走 [MochiDialogAction.holdToConfirm]
 * 长按确认（内部是 MochiHoldButton），「不再提示」用库圆形勾选圈 [MochiCheckbox]。
 * 两个 action 交给库的 MochiButtonGroup 并排同一行（Cancel 在左、Primary 在右）；
 * 显式给次按钮高对比底色与文字色，避免玻璃按钮太淡看不见。
 * 取消调 [onCancel] 返回上一页；勾选后由 [onNeverShowAgain] 持久化。
 */
@Composable
internal fun MochiBetaNoticeDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onNeverShowAgain: () -> Unit,
    onCancel: () -> Unit,
    visible: Boolean = true,
) {
    var dontShow by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberMochiHaptics()
    MochiDialog(
        visible = visible,
        onDismissRequest = {
            if (dontShow) onNeverShowAgain()
            onDismiss()
        },
        title = title,
        message = message,
        actions = persistentListOf(
            MochiDialogAction("继续使用", MochiDialogActionRole.Primary, holdToConfirm = true),
            MochiDialogAction("取消", MochiDialogActionRole.Cancel) { onCancel() },
        ),
        dismissOnBack = false,
        dismissOnOutsideTap = false,
        colors = MochiDialogDefaults.colors(
            primary = MochiTheme.colorScheme.error,
            onPrimary = MochiTheme.colorScheme.onError,
            button = MochiTheme.colorScheme.surfaceContainerHighest,
            buttonContent = MochiTheme.colorScheme.onSurface,
        ),
    ) {
        // 整行都能点：勾选圈只负责显示
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .toggleable(
                    value = dontShow,
                    role = Role.Checkbox,
                    interactionSource = null,
                    indication = null,
                ) {
                    haptics.confirm()
                    dontShow = it
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MochiCheckbox(dontShow, onCheckedChange = null)
            Text("不再提示", style = MochiTheme.textStyles.body, color = MochiTheme.colorScheme.onSurface)
        }
    }
}

/**
 * 勾选「不再提示」后，再进页面不再弹对话框，只给一条带图标的轻提示。
 * 只取进入页面时持久化的首个值：本次页面里勾选造成的变化不会立刻又弹一条。
 */
@Composable
internal fun MochiBetaNoticeToast(ackFlow: Flow<Boolean>) {
    val toast = LocalMochiToast.current
    val ackOnEnter by produceState<Boolean?>(null, ackFlow) {
        value = ackFlow.first()
    }
    LaunchedEffect(ackOnEnter) {
        if (ackOnEnter == true) {
            toast?.show("Beta实验性功能", icon = { tint ->
                PhosphorIcon(R.drawable.ph_warning, null, Modifier.size(20.dp), tint)
            })
        }
    }
}

/** 弹窗列表里的小标题行，避免直接用 Material Text 造成风格断层。 */
@Composable
internal fun MochiSectionNote(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/**
 * 列表行首图标的颜色：黑暗模式白色，浅色页面主题色。
 *
 * Phosphor 图标资源本身是白色 path（fillColor=#FFFFFFFF），[PhosphorIcon] 默认 tint
 * 为 Unspecified 时不会着色，浅色卡片上就是白图标，必须显式传 tint。
 *
 * 「主题配色」页的配色模式与应用明暗模式是两套独立设置，只用 colorScheme.isDark
 * 判断会出现「页面是浅色、配色却是深色」的错配；因此只要任一信号表明是浅色页面，
 * 就按浅色处理。深色版配色的 primary 是亮色，在浅底上同样看不清，这里退到浅色板
 * 的图标信号色兜底。
 */
@Composable
internal fun mochiLeadingIconTint(): Color {
    val cs = MochiTheme.colorScheme
    val palette = LocalZhishengPalette.current
    val lightPage = palette.isLight || !cs.isDark
    if (!lightPage) return Color.White
    val primary = cs.primary
    return when {
        primary.brightness() <= 0.55f -> primary
        // 错配时（浅色页面却拿到深色版配色的亮 primary）退到浅色板的图标信号色
        palette.isLight -> palette.cyan
        else -> lerp(primary, Color.Black, 0.65f)
    }
}

private fun Color.brightness(): Float = red * 0.299f + green * 0.587f + blue * 0.114f

@Composable
internal fun MochiToastCheckIcon(tint: Color) {
    PhosphorIcon(R.drawable.ph_check, null, Modifier.size(20.dp), tint)
}
