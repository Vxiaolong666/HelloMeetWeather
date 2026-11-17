package com.HelloMeet.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.ui.components.PhosphorIcon
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonGroup
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiStepSlider
import ui.feice.mochi.basic.MochiStepSliderDefaults
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTabBar
import ui.feice.mochi.basic.MochiTabBarBehavior
import ui.feice.mochi.basic.MochiTabBarDefaults
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.mochiSheetBackdrop
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.effect.glow.mochiPressGlow
import ui.feice.mochi.effect.glow.rememberMochiGlowState
import ui.feice.mochi.effect.hdr.MochiEdgeGlow
import ui.feice.mochi.effect.hdr.MochiHdrPalette
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSliderRow
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.navigation.MochiNavigator
import ui.feice.mochi.picker.MochiColorPickerSheet
import ui.feice.mochi.picker.MochiSeedPalette
import ui.feice.mochi.theme.MochiHdr
import ui.feice.mochi.theme.MochiTheme
import kotlin.math.roundToInt

/*
 * 迁移自 mochi-compose demo 的「HDR 高光（实验性）」整页、按压高光调参与
 * 转场/侧推配置。demo 用 SharedPreferences 且不持久化 MochiGraphics / MochiHdr，
 * 这里改为走项目 DataStore（SettingsRepository），保证设置重启后仍然生效。
 */

private val HdrPalettes = listOf(
    "智能" to MochiHdrPalette.Intelligence,
    "Siri" to MochiHdrPalette.Siri,
    "极光" to MochiHdrPalette.Aurora,
    "日落" to MochiHdrPalette.Sunset,
    "海洋" to MochiHdrPalette.Ocean,
    "白" to MochiHdrPalette.White,
)

/** demo 的 HdrPage 整页迁移：状态、按压高光、开关滑块、边缘流光与全部参数。 */
@Composable
internal fun MochiHdrSettingsPage(prefs: MochiUiPrefs) {
    val scope = rememberCoroutineScope()
    var edge by rememberSaveable { mutableStateOf(false) }
    var palette by rememberSaveable { mutableIntStateOf(0) }
    var width by rememberSaveable { mutableFloatStateOf(24f) }
    var line by rememberSaveable { mutableFloatStateOf(3f) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var intensity by rememberSaveable { mutableFloatStateOf(1f) }
    var previewSwitch by rememberSaveable { mutableStateOf(true) }
    var previewSlider by rememberSaveable { mutableFloatStateOf(0.6f) }
    val betaAck by SettingsRepository.mochiHdrBetaAck.collectAsState(initial = false)
    var betaDismissed by rememberSaveable { mutableStateOf(false) }
    val navigator = MochiNavigator.current
    MochiBetaNoticeToast(SettingsRepository.mochiHdrBetaAck)
    val supported = MochiHdr.supported
    val headroom = "×${(MochiHdr.headroom * 10f).roundToInt() / 10f}"

    Box(Modifier.fillMaxSize()) {
        MochiSettingsScaffold(
            title = "HDR 高光",
            subtitle = "实验性 · 按压高光与边缘流光",
        ) {
            item {
                MochiListSection(
                    header = "状态",
                    footer = if (supported) {
                        "亮度余量 = 屏幕现在能比 SDR 白亮多少倍，系统按环境光和亮度实时调整；屏幕调到最亮时余量会变小。"
                    } else {
                        "这台设备不支持 HDR 界面（需要 Android 14 以上和 HDR 屏幕），下面的演示按 SDR 显示。"
                    },
                ) {
                    MochiListRow(
                        title = "HDR 高光",
                        subtitle = "按压高光、边缘流光用超过 SDR 白的亮度画（全局生效）。屏幕会提高背光，更耗电。",
                        trailing = {
                            MochiSwitch(
                                checked = prefs.hdrEnabled && supported,
                                onCheckedChange = { scope.launch { SettingsRepository.setMochiHdrEnabled(it) } },
                                enabled = supported,
                            )
                        },
                    )
                    MochiListRow(title = "设备支持", value = if (supported) "支持" else "不支持")
                    MochiListRow(title = "亮度余量", value = headroom)
                }
            }
            item {
                MochiListSection(header = "强度", footer = "所有 HDR 光都不超过峰值亮度、按原色相提亮，不会过曝发白。") {
                    MochiListSliderRow(
                        value = prefs.hdrStrength,
                        onValueChange = { scope.launch { SettingsRepository.setMochiHdrStrength(it) } },
                        title = "HDR 亮度",
                        valueText = "${(prefs.hdrStrength * 100).roundToInt()}%",
                    )
                }
            }
            item {
                MochiListSection(
                    header = "按压高光",
                    footer = "按住时手指附近按原色提亮到比白更亮，深色模式下一样亮；松手就消失。",
                ) {
                    // 卡片里放的不是列表行时，自己补上内边距和块间距，不然会贴着卡片边挤成一团。
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MochiButtonGroup {
                            listOf("粗体", "斜体", "下划线").forEach { label ->
                                MochiButton({}, style = MochiButtonStyle.Tonal) { Text(label) }
                            }
                        }
                        MochiButton({}, Modifier.fillMaxWidth()) { Text("按住拖动看光") }
                    }
                }
            }
            item {
                MochiListSection(header = "开关与滑块", footer = "按住圆钮时整颗圆钮比白更亮，松手淡出。") {
                    MochiListRow(
                        title = "开关",
                        trailing = {
                            MochiSwitch(checked = previewSwitch, onCheckedChange = { previewSwitch = it })
                        },
                    )
                    MochiListSliderRow(
                        value = previewSlider,
                        onValueChange = { previewSlider = it },
                        title = "滑块",
                        valueText = "${(previewSlider * 100).roundToInt()}%",
                    )
                }
            }
            item {
                MochiListSection(
                    header = "屏幕边缘流光",
                    footer = "Apple Intelligence 式的彩色光沿着屏幕圆角流动，盖在内容上保持原色；HDR 下贴边亮线最亮。单个 shader 画完，没有离屏模糊。",
                ) {
                    MochiListRow(
                        title = if (edge) "收起边缘流光" else "显示边缘流光",
                        chevron = true,
                        onClick = { edge = !edge },
                    )
                    // 流光类型是一排色块，不是列表行：补上左右内边距，避免贴住卡片边。
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        HdrPalettePicker(palette) { palette = it }
                    }
                }
            }
            item {
                MochiListSection(header = "边缘流光参数") {
                    MochiListSliderRow(
                        value = width,
                        onValueChange = { width = it },
                        valueRange = 8f..56f,
                        title = "宽度",
                        valueText = "${width.roundToInt()} dp",
                    )
                    MochiListSliderRow(
                        value = line,
                        onValueChange = { line = it },
                        valueRange = 1f..8f,
                        title = "亮线",
                        valueText = "%.1f dp".format(line),
                    )
                    MochiListSliderRow(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.2f..3f,
                        title = "速度",
                        valueText = "%.1f×".format(speed),
                    )
                    MochiListSliderRow(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = 0.2f..1f,
                        title = "强度",
                        valueText = "${(intensity * 100).roundToInt()}%",
                    )
                }
            }
        }
        MochiEdgeGlow(
            edge,
            Modifier.fillMaxSize(),
            HdrPalettes[palette].second,
            width.dp,
            line.dp,
            speed,
            intensity,
        )
        MochiBetaNoticeDialog(
            title = "这是一个实验性功能",
            message = "当前处于Beta实验性功能，内容可能随时增加、修改、删除，并可能存在性能下降，耗电增加或显示异常问题，确认继续使用吗？",
            onDismiss = { betaDismissed = true },
            onNeverShowAgain = { scope.launch { SettingsRepository.setMochiHdrBetaAck(true) } },
            onCancel = { navigator?.pop() },
            visible = !betaAck && !betaDismissed,
        )
    }
}

/** 液态玻璃：总开关、通透度、玻璃样式、使用范围与 HDR 高光。材质在 [MochiAppTheme] 根部接一次。 */
@Composable
internal fun MochiGlassSettingsPage(prefs: MochiUiPrefs) {
    val scope = rememberCoroutineScope()
    val supported = MochiHdr.supported
    val ios27 = prefs.glassStyle == "ios27"
    val glassOnly = !prefs.glass
    // 液态玻璃与 HDR 高光同时开启时的确认弹窗：值为待开启的项（bar / control / hdr）
    var confirmTarget by remember { mutableStateOf<String?>(null) }
    val betaAck by SettingsRepository.mochiGlassBetaAck.collectAsState(initial = false)
    var betaDismissed by rememberSaveable { mutableStateOf(false) }
    val navigator = MochiNavigator.current
    MochiBetaNoticeToast(SettingsRepository.mochiGlassBetaAck)

    Box(Modifier.fillMaxSize()) {
        MochiSettingsScaffold(
            title = "液态玻璃",
            subtitle = "Beta · 栏、菜单、面板与按钮的材质",
        ) {
            item {
                MochiListSection(
                    header = "材质",
                    footer = "液态玻璃每帧都要重新渲染背后的内容，比纯色更耗电；关掉后各处退回纯色。" +
                        "Android 13 以下会降级成两块纯色形状，Android 12 以下只有淡入淡出。",
                ) {
                    MochiListRow(
                        title = "液态玻璃",
                        subtitle = "浮在内容上的栏、菜单、面板和玻璃按钮用折射玻璃绘制",
                        trailing = {
                            MochiSwitch(
                                checked = prefs.glass,
                                onCheckedChange = { scope.launch { SettingsRepository.setMochiGlass(it) } },
                            )
                        },
                    )
                    MochiListRow(
                        title = "滚动边缘模糊",
                        subtitle = "内容滚到栏下面时渐进模糊，和玻璃是两项独立开关",
                        trailing = {
                            MochiSwitch(
                                checked = prefs.glassBlur,
                                onCheckedChange = { scope.launch { SettingsRepository.setMochiGlassBlur(it) } },
                                enabled = !glassOnly,
                            )
                        },
                    )
                    MochiListRow(
                        title = "iOS 27 玻璃",
                        subtitle = "关闭时用 iOS 26.5 的系统原值；打开后可以另外调亮度",
                        trailing = {
                            MochiSwitch(
                                checked = ios27,
                                onCheckedChange = {
                                    scope.launch {
                                        SettingsRepository.setMochiGlassStyle(if (it) "ios27" else "ios26")
                                    }
                                },
                                enabled = !glassOnly,
                            )
                        },
                    )
                    if (ios27) {
                        MochiListSliderRow(
                            value = prefs.glassLight,
                            onValueChange = { scope.launch { SettingsRepository.setMochiGlassLight(it) } },
                            valueRange = -1f..1f,
                            title = "日间亮度",
                            valueText = "${(prefs.glassLight * 100).roundToInt()}%",
                        )
                        MochiListSliderRow(
                            value = prefs.glassDark,
                            onValueChange = { scope.launch { SettingsRepository.setMochiGlassDark(it) } },
                            valueRange = -1f..1f,
                            title = "夜间亮度",
                            valueText = "${(prefs.glassDark * 100).roundToInt()}%",
                        )
                    }
                    MochiListSliderRow(
                        value = prefs.glassTint,
                        onValueChange = { scope.launch { SettingsRepository.setMochiGlassTint(it) } },
                        title = "通透度",
                        valueText = "${(prefs.glassTint * 100).roundToInt()}%",
                    )
                    MochiListRow(
                        title = "仅模糊",
                        subtitle = "去掉折射和边光，只保留模糊与淡入淡出，更省电",
                        trailing = {
                            MochiSwitch(
                                checked = prefs.glassBlurOnly,
                                onCheckedChange = { scope.launch { SettingsRepository.setMochiGlassBlurOnly(it) } },
                                enabled = !glassOnly,
                            )
                        },
                    )
                    MochiListRow(
                        title = "跟随主题强调色",
                        subtitle = "玻璃保持中性，选中项用主题的强调色",
                        trailing = {
                            MochiSwitch(
                                checked = prefs.glassThemeAccent,
                                onCheckedChange = { scope.launch { SettingsRepository.setMochiGlassThemeAccent(it) } },
                                enabled = !glassOnly,
                            )
                        },
                    )
                }
            }
            item {
                MochiListSection(
                    header = "使用范围",
                    footer = "标签栏、栏、栏内按钮各管一处：标签栏只管底部标签栏（和栏互不影响），" +
                        "栏是顶栏与大标题栏，栏内按钮是顶栏上的圆形按钮。菜单、警告框、面板、玻璃按钮" +
                        "没有单独开启时按组件库的回退顺序取上一档玻璃（警告框取菜单，其余取栏）；" +
                        "栏开着玻璃时，栏内按钮即使关掉也会跟随栏。",
                ) {
                    GlassTargetRow("栏", "bar", prefs, scope) { on ->
                        if (on && prefs.hdrEnabled) confirmTarget = "bar"
                        else scope.launch { SettingsRepository.setMochiGlassTarget("bar", on) }
                    }
                    GlassTargetRow("标签栏", "tabbar", prefs, scope) { on ->
                        if (on && prefs.hdrEnabled) confirmTarget = "tabbar"
                        else scope.launch { SettingsRepository.setMochiGlassTarget("tabbar", on) }
                    }
                    GlassTargetRow("栏内按钮", "control", prefs, scope) { on ->
                        if (on && prefs.hdrEnabled) confirmTarget = "control"
                        else scope.launch { SettingsRepository.setMochiGlassTarget("control", on) }
                    }
                    GlassTargetRow("菜单", "menu", prefs, scope)
                    GlassTargetRow("警告框", "alert", prefs, scope)
                    GlassTargetRow("面板", "sheet", prefs, scope)
                    GlassTargetRow("玻璃按钮", "clear", prefs, scope)
                }
            }
            item {
                MochiListSection(
                    header = "HDR 高光",
                    footer = if (supported) {
                        "边缘流光与按压高光用超过 SDR 白的亮度画；玻璃边光同样按亮度余量提亮，不会过曝。"
                    } else {
                        "这台设备不支持 HDR 界面（需要 Android 14 以上和 HDR 屏幕），玻璃边光按 SDR 显示。"
                    },
                ) {
                    MochiListRow(
                        title = "HDR 高光",
                        subtitle = "警告：HDR与液态玻璃不要同时开启！",
                        trailing = {
                            MochiSwitch(
                                checked = prefs.hdrEnabled && supported,
                                onCheckedChange = { on ->
                                    // 反向联动：栏 / 栏内按钮已经开着玻璃时，打开 HDR 也要先确认。
                                    if (on && ("bar" in prefs.glassTargets || "tabbar" in prefs.glassTargets ||
                                            "control" in prefs.glassTargets)
                                    ) {
                                        confirmTarget = "hdr"
                                    } else {
                                        scope.launch { SettingsRepository.setMochiHdrEnabled(on) }
                                    }
                                },
                                enabled = supported,
                            )
                        },
                    )
                    MochiListRow(
                        title = "设备支持",
                        value = if (supported) "支持" else "不支持",
                    )
                }
            }
        }
        // 液态玻璃与 HDR 高光同时开启：两者都会提亮边缘，先让用户确认；确认按钮用红色。
        val target = confirmTarget
        MochiConfirmDialog(
            title = "液态玻璃与 HDR 高光同时开启",
            message = when (target) {
                "bar" -> "当前栏液态玻璃效果与 HDR 高光同时开启可能存在兼容性问题，" +
                    "部分设备上栏的边缘可能过亮或闪烁。仍然开启吗？"
                "tabbar" -> "当前标签栏液态玻璃效果与 HDR 高光同时开启可能存在兼容性问题，" +
                    "部分设备上标签栏边缘可能过亮或闪烁。仍然开启吗？"
                "control" -> "当前栏内按钮液态玻璃效果与 HDR 高光同时开启可能存在兼容性问题，" +
                    "部分设备上按钮边缘可能过亮或闪烁。仍然开启吗？"
                else -> "当前栏、标签栏或栏内按钮的液态玻璃效果与 HDR 高光同时开启可能存在兼容性问题，" +
                    "部分设备上边缘可能过亮或闪烁。仍然开启吗？"
            },
            confirmLabel = "仍然开启",
            confirmFilledError = true,
            onConfirm = {
                val pending = target
                confirmTarget = null
                when (pending) {
                    "bar" -> scope.launch { SettingsRepository.setMochiGlassTarget("bar", true) }
                    "tabbar" -> scope.launch { SettingsRepository.setMochiGlassTarget("tabbar", true) }
                    "control" -> scope.launch { SettingsRepository.setMochiGlassTarget("control", true) }
                    "hdr" -> scope.launch { SettingsRepository.setMochiHdrEnabled(true) }
                }
            },
            onDismiss = { confirmTarget = null },
            destructive = true,
            visible = target != null,
        )
        MochiBetaNoticeDialog(
            title = "这是一个实验性功能",
            message = "当前处于并将长期处于Beta实验性功能，内容可能随时增加、修改、删除，并可能存在性能下降，耗电增加或显示异常问题，确认继续使用吗？",
            onDismiss = { betaDismissed = true },
            onNeverShowAgain = { scope.launch { SettingsRepository.setMochiGlassBetaAck(true) } },
            onCancel = { navigator?.pop() },
            visible = !betaAck && !betaDismissed,
        )
    }
}

@Composable
private fun GlassTargetRow(
    title: String,
    key: String,
    prefs: MochiUiPrefs,
    scope: kotlinx.coroutines.CoroutineScope,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    MochiListRow(
        title = title,
        trailing = {
            MochiSwitch(
                checked = key in prefs.glassTargets,
                onCheckedChange = { on ->
                    if (onToggle != null) onToggle(on)
                    else scope.launch { SettingsRepository.setMochiGlassTarget(key, on) }
                },
                enabled = prefs.glass,
            )
        },
    )
}

/** demo 的六个颜色预设，选中态用主题色边框。 */
@Composable
private fun HdrPalettePicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        HdrPalettes.forEachIndexed { index, (name, palette) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val colors = palette.colors + palette.colors.first()
                Box(
                    Modifier
                        .size(40.dp)
                        .border(
                            if (selected == index) 2.dp else 1.dp,
                            if (selected == index) MochiTheme.colorScheme.primary
                            else MochiTheme.colorScheme.outlineVariant,
                            CircleShape,
                        )
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(colors))
                        .border(1.dp, MochiTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onSelect(index) },
                )
                Text(name, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 按压高光设置：光的颜色、亮度与可实际按住拖动的预览。 */
@Composable
internal fun MochiPressGlowSettingsPage(prefs: MochiUiPrefs) {
    val scope = rememberCoroutineScope()
    var showColorPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val glow = rememberMochiGlowState(mochiGlowSpecFor(prefs))

    MochiSettingsScaffold(
        title = "按压高光",
        subtitle = "按哪亮哪",
    ) {
        item {
            MochiListSection(
                header = "调参",
                footer = "按住任意一块并拖动，光从手指处亮起、带阻尼地跟着走。大块的光更大更柔，圆按钮的光收得更紧。",
            ) {
                MochiListRow(
                    title = "光的颜色",
                    value = if (prefs.pressTinted) "主色" else "白光",
                    chevron = true,
                    onClick = { showColorPicker = true },
                )
                MochiListSliderRow(
                    value = prefs.pressStrength.coerceIn(0.05f, 0.8f),
                    onValueChange = { scope.launch { SettingsRepository.setMochiPressStrength(it) } },
                    valueRange = 0.05f..0.8f,
                    title = "亮度",
                    valueText = "%.2f".format(prefs.pressStrength),
                )
            }
        }
        item {
            MochiListSection(
                header = "预览",
                footer = "按住任意一块并拖动，光从手指处亮起、带阻尼地跟着走；设置会应用到全部 mochi 页面的库组件与预览块。",
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .mochiPressDeform()
                        .background(MochiTheme.colorScheme.primaryContainer, shape)
                        .mochiPressGlow(glow, shape)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text("按住拖动看光", color = MochiTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
    MochiChoiceDialog(
        visible = showColorPicker,
        title = "光的颜色",
        options = listOf("白光" to "white", "主色" to "tinted"),
        selected = if (prefs.pressTinted) "tinted" else "white",
        onSelect = { key ->
            scope.launch { SettingsRepository.setMochiPressTinted(key == "tinted") }
        },
        onDismiss = { showColorPicker = false },
    )
}

/** 应用转场与侧推：直接改变 MainActivity 的页面切换与 mochi 导航规格。 */
@Composable
internal fun MochiTransitionSettingsPage(prefs: MochiUiPrefs) {
    val scope = rememberCoroutineScope()
    var showStylePicker by remember { mutableStateOf(false) }
    var showScalePicker by remember { mutableStateOf(false) }
    val style = PageTransitionStyle.from(prefs.pageTransition)
    val scaleLabel = when (prefs.backScale) {
        "none" -> "不缩放"
        "deep" -> "明显"
        else -> "克制"
    }

    MochiSettingsScaffold(
        title = "应用转场与侧推",
        subtitle = "页面切换与返回手势",
    ) {
        item {
            MochiListSection(
                header = "转场",
            ) {
                MochiListRow(
                    title = "应用转场",
                    value = style.cn,
                    chevron = true,
                    onClick = { showStylePicker = true },
                )
                MochiListRow(
                    title = "预测性返回",
                    subtitle = "系统返回手势跟手预览，关闭后仍可正常返回",
                    trailing = {
                        MochiSwitch(
                            checked = prefs.predictiveBack,
                            onCheckedChange = { scope.launch { SettingsRepository.setMochiPredictiveBack(it) } },
                        )
                    },
                )
                MochiListRow(
                    title = "侧推返回",
                    subtitle = "返回时页面跟随手势向右推出；关闭后返回使用淡变",
                    trailing = {
                        MochiSwitch(
                            checked = prefs.swipeBack,
                            onCheckedChange = { scope.launch { SettingsRepository.setMochiSwipeBack(it) } },
                        )
                    },
                )
                MochiListRow(
                    title = "下层页缩放",
                    value = scaleLabel,
                    chevron = true,
                    onClick = { showScalePicker = true },
                )
                MochiListRow(
                    title = "返回卡片主题色高亮",
                    subtitle = "关闭：回到原点击卡片时用卡片真实底色，不出现主题色闪光",
                    trailing = {
                        MochiSwitch(
                            checked = prefs.sourceHighlight,
                            onCheckedChange = { scope.launch { SettingsRepository.setMochiSourceHighlight(it) } },
                        )
                    },
                )
            }
        }
        item {
            // 说明用普通文本展示，不拆成一行一行的列表项（列表行会截断长文案）。
            MochiListCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "说明",
                        style = MochiTheme.textStyles.footnote,
                        color = MochiTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Text(
                        "以上设置会应用到首页与全部二级页的切换动画；设置页内部二级页的推入/返回同步使用当前配置。" +
                            "预测性返回需要在系统设置里使用手势导航：从屏幕边缘往里滑可跟手缩小页面，松手返回，滑回去取消。",
                        style = MochiTheme.textStyles.footnote,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    MochiChoiceDialog(
        visible = showStylePicker,
        title = "应用转场",
        options = PageTransitionStyle.entries.map { it.cn to it.key },
        selected = style.key,
        onSelect = { key ->
            scope.launch { SettingsRepository.setMochiPageTransition(key) }
        },
        onDismiss = { showStylePicker = false },
    )
    MochiChoiceDialog(
        visible = showScalePicker,
        title = "下层页缩放",
        options = listOf("不缩放" to "none", "克制" to "subtle", "明显" to "deep"),
        selected = prefs.backScale,
        onSelect = { key ->
            scope.launch { SettingsRepository.setMochiBackScale(key) }
        },
        onDismiss = { showScalePicker = false },
    )
}

/** 黑暗模式：也是全站深浅的唯一来源，界面设置里不再重复提供主题模式。 */
private val MochiColorModes = listOf(
    "浅色" to "light",
    "深色" to "dark",
    "跟随系统" to "system",
)

private val MochiPaletteStyles = listOf(
    "Tonal" to "TonalSpot",
    "Expressive" to "Expressive",
    "Vibrant" to "Vibrant",
    "Content" to "Content",
    "Fidelity" to "Fidelity",
    "Neutral" to "Neutral",
)

private val MochiTabLayouts = listOf("左右排布" to "inline", "上下排布" to "stacked", "仅图标" to "icon")

/** 设置页根标签栏和调参页预览共用同一套 behavior，保证所见即所得。 */
internal fun mochiTabBehaviorFor(prefs: MochiUiPrefs): MochiTabBarBehavior {
    val fuse = if (prefs.tabDroplet) 18.dp else 0.dp
    return when (prefs.tabLayout) {
        "stacked" -> MochiTabBarDefaults.behavior(
            gel = prefs.gel,
            fuseRadius = if (prefs.tabDroplet) 22.dp else 0.dp,
            showLabels = true,
            labelBlur = prefs.tabLabelBlur,
        )
        "icon" -> MochiTabBarDefaults.iconOnlyBehavior(
            gel = prefs.gel,
            fuseRadius = fuse,
            labelBlur = prefs.tabLabelBlur,
        )
        else -> MochiTabBarDefaults.iconOnlyBehavior(
            gel = prefs.gel,
            fuseRadius = fuse,
            selectedLabelOnly = true,
            labelBlur = prefs.tabLabelBlur,
        )
    }
}

/** 主题配色：黑暗模式、主题色（含自定义取色）、取色算法与对比度。 */
@Composable
internal fun MochiThemeSettingsPage(prefs: MochiUiPrefs) {
    val scope = rememberCoroutineScope()
    var showModePicker by remember { mutableStateOf(false) }
    var showPalettePicker by remember { mutableStateOf(false) }
    val style = prefs.paletteStyle
    val modeLabel = MochiColorModes.firstOrNull { it.second == prefs.colorMode }?.first ?: "浅色"
    val paletteLabel = MochiPaletteStyles.firstOrNull { it.second == prefs.palette }?.first ?: "Tonal"

    // 参考 demo「颜色选择」：库 MochiColorPickerSheet 自己管 Medium/Large 两档，
    // 弹出时保持半屏，往上拉到全屏；mochiSheetBackdrop 让后面的页面按 iOS 卡片堆叠缩小退后。
    val sheet = rememberMochiSheetState(listOf(MochiSheetDetent.Medium, MochiSheetDetent.Large))
    var draft by remember { mutableStateOf(Color(prefs.seed)) }

    Box(Modifier.fillMaxSize().mochiSheetBackdrop(sheet)) {
        MochiSettingsScaffold(
            title = "主题配色",
            subtitle = "自定义外观设置",
        ) {
            item {
                MochiListSection(
                    header = "配色",
                ) {
                    MochiListRow(title = "黑暗模式", value = modeLabel, chevron = true, onClick = { showModePicker = true })
                    MochiListRow(title = "取色算法", value = paletteLabel, chevron = true, onClick = { showPalettePicker = true })
                    MochiListSliderRow(
                        value = prefs.contrast,
                        onValueChange = { scope.launch { SettingsRepository.setMochiContrast(it) } },
                        valueRange = -1f..1f,
                        title = "对比度",
                        valueText = "%+.2f".format(prefs.contrast),
                    )
                }
            }
            item {
                MochiListSection(
                    header = "主题色",
                ) {
                    MochiSeedPalette(
                        selected = Color(prefs.seed),
                        onSelect = { scope.launch { SettingsRepository.setMochiSeed(it.toArgb()) } },
                        style = style,
                        custom = false,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    MochiListRow(
                        title = "自定义主题色",
                        value = "#%06X".format(prefs.seed and 0xFFFFFF),
                        trailing = {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .border(1.dp, MochiTheme.colorScheme.outlineVariant, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color(prefs.seed)),
                            )
                        },
                        onClick = {
                            draft = Color(prefs.seed)
                            sheet.show()
                        },
                    )
                    MochiListRow(
                        title = "恢复默认配色",
                        destructive = true,
                        onClick = {
                            scope.launch {
                                SettingsRepository.setMochiColorMode("light")
                                SettingsRepository.setMochiSeed(0xFF2B6A5Au.toInt())
                                SettingsRepository.setMochiPalette("TonalSpot")
                                SettingsRepository.setMochiContrast(0f)
                            }
                        },
                    )
                }
            }
        }
    }

    // 库的选色面板：网格 / 色谱 / 滑块三种方式 + 不透明度，内容和 demo 完全一致。
    MochiColorPickerSheet(
        state = sheet,
        color = draft,
        onColorChange = { picked ->
            draft = picked
            scope.launch { SettingsRepository.setMochiSeed(picked.toArgb()) }
        },
        title = "自定义主题色",
        supportsAlpha = false,
        onDismiss = { },
    )

    MochiChoiceDialog(
        visible = showModePicker,
        title = "黑暗模式",
        options = MochiColorModes,
        selected = prefs.colorMode,
        onSelect = { scope.launch { SettingsRepository.setMochiColorMode(it) } },
        onDismiss = { showModePicker = false },
    )
    MochiChoiceDialog(
        visible = showPalettePicker,
        title = "取色算法",
        options = MochiPaletteStyles,
        selected = prefs.palette,
        subtitle = "对应 Material 的 dynamic scheme variant",
        onSelect = { scope.launch { SettingsRepository.setMochiPalette(it) } },
        onDismiss = { showPalettePicker = false },
    )
}

/** 参考 demo 的 TabBarPage：底部标签栏的布局、文字模糊、融球残影、收起与果冻感。 */
@Composable
internal fun MochiTabBarSettingsPage(prefs: MochiUiPrefs) {
    val scope = rememberCoroutineScope()
    var previewIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember {
        persistentListOf(
            MochiTabItem("数据与位置") { tint ->
                PhosphorIcon(R.drawable.ph_database, null, Modifier.size(22.dp), tint)
            },
            MochiTabItem("首页内容") { tint ->
                PhosphorIcon(R.drawable.ph_sun, null, Modifier.size(22.dp), tint)
            },
            MochiTabItem("外观与设备") { tint ->
                PhosphorIcon(R.drawable.ph_sparkle, null, Modifier.size(22.dp), tint)
            },
        )
    }
    val behavior = mochiTabBehaviorFor(prefs)
    val layoutIndex = MochiTabLayouts.indexOfFirst { it.second == prefs.tabLayout }.coerceAtLeast(0)

    MochiSettingsScaffold(
        title = "底部标签栏",
        subtitle = "排布 · 文字模糊 · 融球 · 果冻感",
    ) {
        item {
            MochiListSection(
                header = "预览",
                footer = "点标签或按住左右滑动预览标签栏效果",
            ) {
                MochiTabBar(
                    selectedIndex = previewIndex,
                    onSelect = { previewIndex = it },
                    items = tabs,
                    behavior = behavior,
                    windowInsets = WindowInsets(0),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }
        item {
            MochiListSection(header = "调参") {
                MochiListRow(title = "标签排布")
                MochiSegmentedControl(
                    selectedIndex = layoutIndex,
                    onSelect = { i ->
                        scope.launch { SettingsRepository.setMochiTabLayout(MochiTabLayouts[i].second) }
                    },
                    items = MochiTabLayouts.map { it.first }.toImmutableList(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
                MochiListRow(
                    title = "文字模糊",
                    subtitle = "关闭后标签只做透明度淡入，不再带模糊",
                    trailing = {
                        MochiSwitch(
                            checked = prefs.tabLabelBlur,
                            onCheckedChange = { scope.launch { SettingsRepository.setMochiTabLabelBlur(it) } },
                        )
                    },
                )
                MochiListRow(
                    title = "融球残影",
                    subtitle = "选中块拖动时后面跟一滴可融合的残影",
                    trailing = {
                        MochiSwitch(
                            checked = prefs.tabDroplet,
                            onCheckedChange = { scope.launch { SettingsRepository.setMochiTabDroplet(it) } },
                        )
                    },
                )
                MochiListRow(
                    title = "滚动时收起",
                    subtitle = "向下阅读时标签栏收成一颗小圆",
                    trailing = {
                        MochiSwitch(
                            checked = prefs.tabCollapse,
                            onCheckedChange = { scope.launch { SettingsRepository.setMochiTabCollapse(it) } },
                        )
                    },
                )
                MochiListSliderRow(
                    value = prefs.gel,
                    onValueChange = { scope.launch { SettingsRepository.setMochiGel(it) } },
                    valueRange = 0f..1f,
                    title = "果冻感",
                    valueText = "%.2f".format(prefs.gel),
                )
            }
        }
    }
}

internal val MochiGraphicsLevels = listOf("中" to "mid", "高" to "high", "超高" to "extra")

/**
 * 三档各自的填充渐变，保留项目原有的「蓝 → 紫流动渐变 + 星点」设计：
 * 中档补齐柔和的磷光青 → 蓝，高档是库 CodexBlue → 紫，超高把这套蓝紫推到更浓。
 * 每档三个色标，切换档位时用 animateColorAsState 让渐变平滑过渡。
 */
internal val GraphicsGradients = listOf(
    listOf(Color(0xFF2E9FB8), Color(0xFF3189C9), Color(0xFF3D7FD1)),
    listOf(Color(0xFF339CFF), Color(0xFF5C82F2), Color(0xFF8B55EF)),
    listOf(Color(0xFF2E86FF), Color(0xFF7A4BF0), Color(0xFFB45CF7)),
)

/**
 * 画面效果独立页：参考 demo 引导页的动效设置，三档切换 + 可持续播放的动效预览。
 * 档位写入 MochiGraphics.level，预览用库自己的模糊文字 / 转动指示器 / 果冻按压。
 */
@Composable
internal fun MochiGraphicsSettingsPage(prefs: MochiUiPrefs) {
    val scope = rememberCoroutineScope()
    var animate by rememberSaveable { mutableStateOf(true) }
    var frame by rememberSaveable { mutableIntStateOf(0) }
    val levelIndex = MochiGraphicsLevels.indexOfFirst { it.second == prefs.graphicsLevel }.coerceAtLeast(0)
    val captions = persistentListOf("液态玻璃", "模糊淡入", "弹性按压")

    // 分段滑条沿用库的标准交互，填充段换成项目自己的档位渐变（含星点）。
    val target = GraphicsGradients[levelIndex]
    val c0 by animateColorAsState(target[0], animationSpec = tween(320), label = "graphics-0")
    val c1 by animateColorAsState(target[1], animationSpec = tween(320), label = "graphics-1")
    val c2 by animateColorAsState(target[2], animationSpec = tween(320), label = "graphics-2")
    val sparkle by animateColorAsState(
        Color.White.copy(alpha = 0.26f + 0.22f * levelIndex),
        animationSpec = tween(320),
        label = "graphics-sparkle",
    )
    val fillBrush = remember(c0, c1, c2) { Brush.horizontalGradient(listOf(c0, c1, c2)) }
    val sliderColors = MochiStepSliderDefaults.colors(fillBrush = fillBrush, sparkleColor = sparkle)

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(1100)
            frame = (frame + 1) % captions.size
        }
    }

    MochiSettingsScaffold(
        title = "画面效果",
        subtitle = "档位与实时动效预览",
    ) {
        item {
            MochiListSection(
                header = "档位",
                footer = "点哪去哪、拖动跟手，每过一档轻震并带回弹吸附。三档各有自己的填充渐变：" +
                    "中是磷光青蓝，高是蓝紫（原设计），超高是更浓的蓝紫。中：关闭 AGSL 着色器；" +
                    "高（默认）：全部开启；超高：模糊半径 ×1.5 并选择最高刷新率，不打开 HDR。",
            ) {
                MochiListRow(
                    title = "当前档位",
                    value = MochiGraphicsLevels[levelIndex].first,
                )
                MochiStepSlider(
                    value = levelIndex,
                    onValueChange = { i ->
                        if (i != levelIndex) {
                            scope.launch { SettingsRepository.setMochiGraphicsLevel(MochiGraphicsLevels[i].second) }
                        }
                    },
                    count = MochiGraphicsLevels.size,
                    colors = sliderColors,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
                MochiListRow(
                    title = "预览动效",
                    subtitle = "打开后下方预览持续播放文字与图标动效",
                    trailing = {
                        MochiSwitch(checked = animate, onCheckedChange = { animate = it })
                    },
                )
            }
        }
        item {
            MochiListSection(
                header = "预览",
             ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .padding(16.dp)
                        .background(MochiTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MochiBlurText(
                            captions[frame],
                            color = MochiTheme.colorScheme.onPrimaryContainer,
                        )
                        MochiActivityIndicator(
                            size = 26.dp,
                            color = MochiTheme.colorScheme.onPrimaryContainer,
                        )
                        MochiButton({}, Modifier.mochiPressDeform()) {
                            Text("按住看果冻感", color = MochiTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
