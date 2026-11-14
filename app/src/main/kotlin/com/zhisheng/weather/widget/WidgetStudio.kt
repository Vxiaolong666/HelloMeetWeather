package com.zhisheng.weather.widget

import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import com.zhisheng.weather.ui.components.classicConditionIconRes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhisheng.weather.R
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.HomeBackdrop
import com.zhisheng.weather.ui.components.LocalHomeBackdrop
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.ProvideWeatherPresentationClock
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import com.zhisheng.weather.ui.theme.zhishengGlassActionBar
import com.zhisheng.weather.ui.theme.zhishengDialogPanel
import com.zhisheng.weather.data.HomeSurfaceStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import com.zhisheng.weather.ui.rememberMochiColorScheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiBottomToolbar
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

private data class StudioColors(val bg: Color, val panel: Color, val ink: Color, val muted: Color, val accent: Color, val edge: Color, val classic: Boolean) {
    val shape get() = RoundedCornerShape(if (classic) 14.dp else 24.dp)
}
private val LightStudioColors = StudioColors(Color(0xFFEDF4F5), Color.White, Color(0xFF203A43), Color(0xFF647D85), Color(0xFF315F6D), Color(0xFFDCE7E9), false)
private val ClassicStudioColors = StudioColors(Color(0xFF141B1B), Color(0xFF1D2827), Color(0xFFE5EDE7), Color(0xFF9EAFA7), Color(0xFFABD1BD), Color(0xFF344340), true)
private val LocalStudio = staticCompositionLocalOf { LightStudioColors }

/** 三个下级设置页统一取组件库主题色：底色、卡片、文字和强调色都跟随「主题配色」设置。 */
@Composable
private fun studioColors(classic: Boolean): StudioColors {
    val cs = MochiTheme.colorScheme
    return StudioColors(
        bg = MochiDefaults.groupedBackground(),
        panel = cs.surface,
        ink = cs.onSurface,
        muted = cs.onSurfaceVariant,
        accent = cs.primary,
        edge = cs.outlineVariant,
        classic = classic,
    )
}
/** 栏按钮文字显式指定前景色：这三个页面是独立 Activity，不依赖默认内容色（会落回纯黑）。深色模式下即白字。 */
@Composable private fun StudioBarButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val foreground = MochiTheme.colorScheme.onSurface
    MochiBarButton(
        text,
        onClick,
        modifier = modifier,
        enabled = enabled,
        colors = MochiButtonDefaults.barColors().copy(
            content = foreground,
            disabledContent = foreground.copy(alpha = 0.38f),
        ),
    )
}

private val configSaver = Saver<WidgetConfig, String>(save = { Json.encodeToString(WidgetConfig.serializer(), it) }, restore = { Json.decodeFromString<WidgetConfig>(it) })

// The carousel must keep the controls below it in the same place on every page.
// Only an explicit tap on enlarge changes the height.
internal fun previewStageHeight(expanded: Boolean): Int = if (expanded) 340 else 280

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun WidgetStudio(style: WidgetStyle, editingId: Int?, initialKind: WidgetKind, initialConfig: WidgetConfig,
                          cities: List<City>, selected: City?, locatedKey: String?, notification: NotificationSettings,
                          onNotification: (NotificationSettings) -> Unit, onClose: () -> Unit,
                          onApply: (WidgetKind, WidgetConfig) -> Unit, previewData: WidgetSnapshot? = null,
                          pinState: WidgetPinState = WidgetPinState.IDLE, onAddHelp: (WidgetKind) -> Unit = {}) {
    val classic = style == WidgetStyle.CLASSIC
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val configuration = LocalConfiguration.current
    val ownerView = LocalView.current
    var config by rememberSaveable(stateSaver = configSaver) { mutableStateOf(initialConfig) }
    var backdrop by rememberSaveable { mutableIntStateOf(0) }
    var largePreview by rememberSaveable { mutableStateOf(false) }
    var previewWall by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = initialKind.ordinal, pageCount = { WidgetKind.entries.size })
    val scope = rememberCoroutineScope()
    val desktopSize = remember(editingId, configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
        if (editingId == null || inspection) null else runCatching {
            widgetEditorSize(AppWidgetManager.getInstance(context).getAppWidgetOptions(editingId), initialKind,
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        }.getOrNull()
    }
    var data by remember { mutableStateOf(previewData) }
    LaunchedEffect(config.locationMode, config.cityKey, selected, cities, locatedKey) {
        if (!inspection && previewData == null) data = withContext(Dispatchers.IO) { WidgetRuntime.snapshot(context, config) }
    }
    val sample = remember { widgetPreviewSample() }
    val preview = data?.takeIf { it.weather.current != null } ?: sample
    val kind = WidgetKind.entries[pager.currentPage]
    val sourceLayer = rememberGraphicsLayer()
    var sourceOrigin by remember { mutableStateOf(Offset.Zero) }

    ProvideWeatherPresentationClock {
        MochiTheme(colors = rememberMochiColorScheme(followSystem = true), systemBarAppearance = true) {
            val colors = studioColors(classic)
            CompositionLocalProvider(LocalStudio provides colors) {
                val dark = MochiTheme.colorScheme.isDark
                MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = colors.accent, surface = colors.panel, background = colors.bg, onSurface = colors.ink, onPrimary = MochiTheme.colorScheme.onPrimary)
                    else lightColorScheme(primary = colors.accent, surface = colors.panel, background = colors.bg, onSurface = colors.ink, onPrimary = MochiTheme.colorScheme.onPrimary)) {
                    // 供「款式一览 / 放大预览」弹窗采样页面做玻璃材质。
                    Box(Modifier.fillMaxSize().background(colors.bg)
                        .onGloballyPositioned { sourceOrigin = it.positionInRoot() }
                        .drawWithContent {
                            sourceLayer.record { drawRect(colors.bg); this@drawWithContent.drawContent() }
                            drawLayer(sourceLayer)
                        }) {
                        MochiLazyCompactTitleScaffold(
                            title = "${style.title}小组件",
                            subtitle = "${kind.name(style)} · ${kind.columns} × ${kind.rows}",
                            navigationIcon = { MochiBackButton(onClick = onClose) },
                            actions = { StudioBarButton("试壁纸", onClick = { backdrop = (backdrop + 1) % 3 }) },
                            content = {
                                item {
                                    MochiListSection(header = "组件预览") {
                                        SectionBody {
                                            Box(Modifier.fillMaxWidth().height(previewStageHeight(false).dp).clip(colors.shape)
                                                .background(Brush.linearGradient(studioBackdropColors(style, backdrop)))) {
                                                HorizontalPager(state = pager, userScrollEnabled = editingId == null,
                                                    modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                                                    val pageKind = WidgetKind.entries[page]
                                                    NativeWidgetPreview(style, pageKind, config, preview,
                                                        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp,
                                                            top = if (pageKind == WidgetKind.SCENE) 12.dp else 24.dp, bottom = 28.dp),
                                                        previewBackground = studioPreviewBackground(style, backdrop).toArgb(),
                                                        hostSize = desktopSize.takeIf { pageKind == initialKind })
                                                }
                                                Text(if (desktopSize != null) "按桌面提供的尺寸缩放预览" else "参考预览，实际尺寸以桌面为准",
                                                    color = if (backdrop == 1 || classic) Color(0xFFF8FCFF) else Color(0xFF203A43),
                                                    fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp))
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(kind.name(style), color = colors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                if (editingId == null) WidgetKind.entries.forEachIndexed { index, _ ->
                                                    Box(Modifier.size(width = if (pager.currentPage == index) 16.dp else 5.dp, height = 5.dp)
                                                        .clip(CircleShape).background(colors.accent.copy(alpha = if (pager.currentPage == index) 1f else .28f)))
                                                }
                                            }
                                            Text((if (data?.weather?.current == null) "示例天气" else data?.city?.name.orEmpty()) + " · 左右滑动换款式",
                                                color = colors.muted, fontSize = 12.sp)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                StudioBarButton("款式一览", onClick = { previewWall = true })
                                                StudioBarButton(if (largePreview) "缩小预览" else "放大预览", onClick = { largePreview = !largePreview })
                                            }
                                            if (editingId == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                                StudioBarButton("上一个", onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + WidgetKind.entries.size - 1) % WidgetKind.entries.size) } })
                                                StudioBarButton("下一个", onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1) % WidgetKind.entries.size) } })
                                            }
                                        }
                                    }
                                }
                                item {
                                    MochiListSection(header = "外观") {
                                        SectionBody {
                                            LabelValue("可读预设", "")
                                            Text(if (editingId == null) "先在预览中试用，添加时用于新组件。" else "先在预览中试用，保存修改后用于当前组件。", color = colors.muted, fontSize = 11.sp)
                                            Row {
                                                StudioBarButton("清晰阅读", onClick = { config = widgetReadablePreset(config) })
                                                StudioBarButton("透明护字", onClick = { config = widgetTransparentPreset(config) })
                                            }
                                            LabelValue("背景不透明度", "${config.opacity}%")
                                            StudioSlider(config.opacity.toFloat(), { config = config.copy(opacity = it.roundToInt()) }, 0f..100f)
                                            Choice(listOf("透明" to "0", "轻透" to "45", "柔光" to "80", "实色" to "100"), config.opacity.toString()) { config = config.copy(opacity = it.toInt()) }
                                            LabelValue("底色", "")
                                            Choice(listOf("浅色" to "light", "深色" to "dark", "系统" to "system"), config.theme) { config = config.copy(theme = it) }
                                            Text("0% 完全无底色；100% 遮住壁纸。桌面组件只能绘制自己的底色，无法实时模糊桌面壁纸。", fontSize = 11.sp, color = colors.muted)
                                            LabelValue("天气图标", "无界为默认")
                                            IconSetPicker(config.iconSet) { config = config.copy(iconSet = it) }
                                            LabelValue("文字颜色", "")
                                            Choice(listOf("自动" to "auto", "浅色字" to "light", "深色字" to "dark"), config.text) { config = config.copy(text = it) }
                                            Text("自动只参考壁纸整体明暗；复杂壁纸可选浅色字配深色壁纸，或深色字配浅色壁纸。图标彩色表示天气种类；降水图显示毫米/小时，百分比是降水概率。", fontSize = 11.sp, color = colors.muted)
                                            Toggle("透明时增强文字轮廓", config.protectText) { config = config.copy(protectText = it) }
                                            LabelValue("数字大小", "${(config.textScale * 100).roundToInt()}%")
                                            StudioSlider(config.textScale, { config = config.copy(textScale = it) }, WidgetMinimumNumberScale..WidgetMaximumNumberScale)
                                            Text("调节温度、时间和天气读数；小读数会保留清晰易读的大小。", fontSize = 11.sp, color = colors.muted)
                                            Text("最多 200%，实际大小受组件空间限制；隐藏时钟或预报可腾出空间。", fontSize = 11.sp, color = colors.muted)
                                            LabelValue("说明文字大小", "${(config.detailScale * 100).roundToInt()}%")
                                            StudioSlider(config.detailScale, { config = config.copy(detailScale = it) }, .85f..1.5f)
                                            Toggle("说明文字加粗", config.boldText) { config = config.copy(boldText = it) }
                                        }
                                    }
                                }
                                item {
                                    MochiListSection(header = "地点") {
                                        SectionBody {
                                            CitySearch(config, { config = it }, boxed = false)
                                            PlaceRow("跟随当前城市", selected?.displayName ?: "在应用中选择地点", config.locationMode == "selected") { config = config.copy(locationMode = "selected", cityKey = "", fixedCity = null) }
                                            PlaceRow("跟随定位", cities.firstOrNull { it.locationKey == locatedKey }?.displayName ?: "先在应用中开启定位", config.locationMode == "located") { config = config.copy(locationMode = "located", cityKey = "", fixedCity = null) }
                                            if (cities.isNotEmpty()) {
                                                LabelValue("固定地点", "收藏优先")
                                                cities.sortedByDescending { it.isFavorite }.forEach { city ->
                                                    PlaceRow(city.displayName, if (city.isFavorite) "收藏 · ${city.contextLabel}" else city.contextLabel, config.locationMode == "fixed" && config.cityKey == city.locationKey) {
                                                        WidgetStore.rememberCity(context, city)
                                                        config = config.copy(locationMode = "fixed", cityKey = city.locationKey, fixedCity = city)
                                                    }
                                                }
                                            }
                                            Text("固定地点的小组件会独立显示该地天气，切换应用城市不会改变它。", fontSize = 12.sp, color = colors.muted)
                                        }
                                    }
                                }
                                item {
                                    MochiListSection(header = "内容") {
                                        SectionBody {
                                            widgetEditorSizeNotice(kind, desktopSize, config)?.let { Text(it, color = colors.muted, fontSize = 12.sp) }
                                            if (kind == WidgetKind.PULSE || kind == WidgetKind.SCENE || (kind == WidgetKind.WEEK && !classic)) {
                                                Toggle("显示时钟和日期", config.showClock) { config = config.copy(showClock = it) }
                                                Toggle("24 小时制", config.clock24) { config = config.copy(clock24 = it) }
                                            }
                                            if (kind in listOf(WidgetKind.SCENE, WidgetKind.WEEK, WidgetKind.BAND, WidgetKind.COMBO)) Toggle("显示天气预报", config.showForecast) { config = config.copy(showForecast = it) }
                                            if (kind == WidgetKind.SCENE) Toggle("短时降水柱图", config.showPrecipitation) { config = config.copy(showPrecipitation = it) }
                                            if (kind == WidgetKind.SCENE || (kind == WidgetKind.WEEK && !classic)) Toggle("农历", config.showLunar) { config = config.copy(showLunar = it) }
                                            if (kind == WidgetKind.SCENE) Toggle("天气娘提示", config.showWeatherGirl) { config = config.copy(showWeatherGirl = it) }
                                            if (kind == WidgetKind.NOW) Toggle("空气质量", config.showAir) { config = config.copy(showAir = it) }
                                            Choice(listOf("摄氏 °C" to "c", "华氏 °F" to "f"), config.tempUnit) { config = config.copy(tempUnit = it) }
                                            if (kind == WidgetKind.SCENE) {
                                                LabelValue("天气读数", "长按拖动排序 · 最多六项")
                                                MetricOrder(config.metrics) { config = config.copy(metrics = it) }
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    metricLabels.filterKeys { it !in config.metrics }.forEach { (key, label) ->
                                                        TextButton(onClick = { config = config.copy(metrics = (config.metrics + key).take(6)) }, enabled = config.metrics.size < 6) { Text("+ $label", fontSize = 12.sp, color = MochiTheme.colorScheme.onSurface) }
                                                    }
                                                }
                                            }
                                            LabelValue("通知栏天气", "")
                                            Toggle("常驻天气速览", notification.enabled && notification.style == style.key) {
                                                onNotification(if (it) notification.copy(enabled = true, style = style.key, config = config) else notification.copy(enabled = false))
                                            }
                                            Text("开启时采用当前地点与单位设置，之后独立于桌面组件。", color = colors.muted, fontSize = 11.sp)
                                            if (notification.enabled && notification.style == style.key) StudioBarButton("将当前设置用于通知栏", onClick = { onNotification(notification.copy(config = config)) })
                                        }
                                    }
                                }
                                item {
                                    MochiListSection(header = "点击与刷新") {
                                        SectionBody {
                                            LabelValue("点击时间", "")
                                            Choice(listOf("系统时钟" to "clock", "天气" to "app", "编辑组件" to "settings", "无操作" to "none"), config.clickTime) { config = config.copy(clickTime = it) }
                                            LabelValue("点击日期", "")
                                            Choice(listOf("系统日历" to "calendar", "天气" to "app", "编辑组件" to "settings", "无操作" to "none"), config.clickDate) { config = config.copy(clickDate = it) }
                                            LabelValue("点击天气与空白区域", "")
                                            Choice(listOf("天气主页" to "app", "编辑组件" to "settings", "无操作" to "none"), config.clickWeather) { config = config.copy(clickWeather = it) }
                                            LabelValue("天气刷新间隔", "")
                                            Choice(listOf("30 分钟" to "30", "1 小时" to "60", "2 小时" to "120"), config.intervalMinutes.toString()) { config = config.copy(intervalMinutes = it.toInt()) }
                                            Text("系统省电模式可能延后天气刷新；时钟由桌面自动走时。", color = colors.muted, fontSize = 11.sp)
                                        }
                                    }
                                }
                                item {
                                    if (editingId == null) {
                                        val feedback = when (pinState) {
                                            WidgetPinState.PREPARING -> "正在准备小组件…"
                                            WidgetPinState.WAITING -> "请在桌面弹窗中确认添加，返回后会检查结果"
                                            WidgetPinState.ADDED -> "已添加到桌面"
                                            WidgetPinState.UNCONFIRMED -> "尚未收到添加确认，可查看桌面或打开添加帮助"
                                            WidgetPinState.IDLE -> null
                                            else -> "暂时无法直接添加，请查看添加帮助"
                                        }
                                        if (feedback != null) Text(feedback, fontSize = 12.sp, color = colors.muted)
                                    }
                                    Spacer(Modifier.height(80.dp))
                                }
                            },
                        )
                        MochiBottomToolbar(Modifier.align(Alignment.BottomCenter)) {
                            MochiButton(
                                onClick = { onApply(kind, config) },
                                enabled = pinState !in setOf(WidgetPinState.PREPARING, WidgetPinState.WAITING),
                                modifier = Modifier.weight(1f),
                            ) {
                                PhosphorIcon(if (editingId == null) R.drawable.ph_plus else R.drawable.ph_check, null,
                                    Modifier.size(18.dp), Color.White)
                                Text(when {
                                    pinState == WidgetPinState.PREPARING -> "正在准备…"
                                    pinState == WidgetPinState.WAITING -> "等待桌面确认"
                                    editingId != null -> "保存修改"
                                    pinState == WidgetPinState.ADDED -> "再添加一个"
                                    pinState == WidgetPinState.IDLE -> "添加到桌面"
                                    else -> "重试添加"
                                }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                            if (editingId == null) StudioBarButton("添加帮助", onClick = { onAddHelp(kind) })
                        }
                        if (previewWall || largePreview) {
                            val allKinds = previewWall
                            val dismiss = { if (allKinds) previewWall = false else largePreview = false }
                            ZhishengWeatherTheme(isLight = !classic, homeSurfaceStyle = HomeSurfaceStyle.CURRENT, softGlow = false) {
                                CompositionLocalProvider(LocalHomeBackdrop provides HomeBackdrop(sourceLayer, sourceOrigin, ownerView)) {
                                    WeatherOverlayDialog(onDismiss = dismiss) { panelModifier ->
                                        WidgetPreviewDialogContent(style, kind, config, preview, backdrop, allKinds,
                                            desktopSize, onTryWallpaper = { backdrop = (backdrop + 1) % 3 },
                                            onSelect = { candidate ->
                                                previewWall = false
                                                scope.launch { pager.animateScrollToPage(candidate.ordinal) }
                                            }, onDone = dismiss,
                                            previewLabel = if (data?.weather?.current == null) "示例天气" else preview.city.name,
                                            modifier = panelModifier)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun studioBackdropColors(style: WidgetStyle, backdrop: Int): List<Color> = when (backdrop) {
    1 -> listOf(Color(0xFF3B5263), Color(0xFF202B38), Color(0xFF617782))
    2 -> listOf(Color(0xFFCCC2AB), Color(0xFF758777), Color(0xFFB3C9C6))
    else -> if (style == WidgetStyle.CLASSIC) listOf(Color(0xFF394845), Color(0xFF62716A), Color(0xFF364744))
        else listOf(Color(0xFFA7C9D9), Color(0xFFD9E8E8), Color(0xFF89B6C5))
}

private fun studioPreviewBackground(style: WidgetStyle, backdrop: Int): Color = when (backdrop) {
    1 -> Color(0xFF202B38)
    2 -> Color(0xFFB3C9C6)
    else -> if (style == WidgetStyle.CLASSIC) Color(0xFF394845) else Color(0xFFD9E8E8)
}

/** Explain the same host-size reductions used by the formal RemoteViews binder. */
internal fun widgetEditorSizeNotice(kind: WidgetKind, hostSize: SizeF?, config: WidgetConfig): String? {
    val size = hostSize?.takeIf { it.width.isFinite() && it.height.isFinite() && it.width > 0f && it.height > 0f }
        ?: return null
    if (kind != WidgetKind.SCENE) return null
    val heightHidden = buildList {
        if (size.height < 280f) add(if (config.showPrecipitation) "短时降水柱图" else "天气提示")
        if (size.height < 250f && config.metrics.isNotEmpty()) add("天气读数")
        if (size.height < 180f && config.showForecast) add("天气预报")
        if (size.height < 440f && config.showForecast) add("逐时预报")
    }
    val narrow = size.width < 240f
    val hidden = heightHidden + if (narrow) listOf("今日温差") else emptyList()
    if (hidden.isEmpty()) return null
    val actions = buildList {
        if (narrow) add("拉宽组件查看今日温差")
        if (heightHidden.isNotEmpty()) add("拉高组件查看其余内容")
    }
    return "桌面当前可用尺寸 ${size.width.toInt()} × ${size.height.toInt()}dp，${hidden.joinToString("、")}暂时收起，以保留主要天气。可在桌面${actions.joinToString("，或")}，设置会保留。"
}

/** Actual preview-window content, shared by the short-screen preview and style overview. */
@Composable
internal fun WidgetPreviewDialogContent(style: WidgetStyle, kind: WidgetKind, config: WidgetConfig,
    preview: WidgetSnapshot, backdrop: Int, allKinds: Boolean, hostSize: SizeF?,
    onTryWallpaper: () -> Unit, onSelect: (WidgetKind) -> Unit, onDone: () -> Unit,
    previewLabel: String = preview.city.name, modifier: Modifier = Modifier) {
    val colors = if (style == WidgetStyle.CLASSIC) ClassicStudioColors else LightStudioColors
    Column(modifier.zhishengDialogPanel(glass = true)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (allKinds) "${style.title} · 五款桌面组件" else kind.name(style),
                color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("完成", color = colors.accent, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (allKinds) "$previewLabel · 点击选择，实际尺寸以桌面为准"
                else if (hostSize != null) "$previewLabel · 按桌面提供的尺寸预览" else "$previewLabel · 参考尺寸，以桌面为准",
                color = colors.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onTryWallpaper, modifier = Modifier.heightIn(min = 48.dp)) { Text("试壁纸", color = colors.accent, fontSize = 12.sp) }
        }
        HorizontalDivider(color = colors.edge.copy(alpha = .55f))
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            widgetEditorSizeNotice(kind, hostSize, config)?.let { Text(it, color = colors.muted, fontSize = 12.sp) }
            (if (allKinds) WidgetKind.entries else listOf(kind)).forEach { candidate ->
                Column(Modifier.fillMaxWidth()
                    .then(if (allKinds) Modifier.clickable { onSelect(candidate) } else Modifier)) {
                    if (allKinds) Text("${candidate.name(style)} · ${candidate.columns} × ${candidate.rows}", color = colors.ink,
                        fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    Box(Modifier.fillMaxWidth().height(when (candidate) {
                        WidgetKind.PULSE -> 100.dp; WidgetKind.NOW -> 190.dp; WidgetKind.SCENE -> 300.dp; WidgetKind.COMBO -> 260.dp; else -> 200.dp
                    }).clip(colors.shape).background(Brush.linearGradient(studioBackdropColors(style, backdrop)))
                        .semantics { contentDescription = "${candidate.name(style)}预览，试览壁纸${backdrop + 1}" }) {
                        NativeWidgetPreview(style, candidate, config, preview, Modifier.fillMaxSize().padding(12.dp),
                            previewBackground = studioPreviewBackground(style, backdrop).toArgb(),
                            hostSize = hostSize.takeIf { !allKinds && candidate == kind })
                    }
                }
            }
        }
    }
}

/** Notification settings are reachable without creating a desktop widget. */
@Composable
internal fun NotificationStudio(value: NotificationSettings, cities: List<City>, selected: City?, locatedKey: String?,
                                onChange: (NotificationSettings) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    var snapshot by remember { mutableStateOf<WidgetSnapshot?>(null) }
    LaunchedEffect(value.config, cities, selected, locatedKey) {
        if (!inspection) snapshot = withContext(Dispatchers.IO) { WidgetRuntime.snapshot(context, value.config) }
    }
    val sample = remember { widgetPreviewSample() }
    fun change(config: WidgetConfig) = onChange(value.copy(config = config))

    ProvideWeatherPresentationClock {
        MochiTheme(colors = rememberMochiColorScheme(followSystem = true), systemBarAppearance = true) {
            val c = studioColors(false)
            CompositionLocalProvider(LocalStudio provides c) {
                val dark = MochiTheme.colorScheme.isDark
                MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = c.accent, surface = c.panel, onSurface = c.ink)
                    else lightColorScheme(primary = c.accent, surface = c.panel, onSurface = c.ink)) {
                    Box(Modifier.fillMaxSize().background(c.bg)) {
                        MochiLazyCompactTitleScaffold(
                            title = "通知栏天气",
                            navigationIcon = { MochiBackButton(onClick = onClose) },
                            actions = { StudioBarButton("保存", onClick = onClose) },
                            content = {
                                item {
                                    MochiListSection(header = "通知与权限") {
                                        SectionBody {
                                            Toggle("常驻天气速览", value.enabled) { onChange(value.copy(enabled = it)) }
                                            Text("当前天气、体感、风力、湿度、空气质量、降水概率与未来三日预报。", fontSize = 13.sp, color = c.muted)
                                            Toggle("天气预警提醒", value.alertsEnabled) { onChange(value.copy(alertsEnabled = it)) }
                                            Text("提醒下方所选地点的新预警及更新。约每 30 分钟检查，受网络和系统省电影响；不保证即时送达。", fontSize = 13.sp, color = c.muted)
                                            var permissionVersion by remember { mutableIntStateOf(0) }
                                            val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                                            DisposableEffect(owner) {
                                                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permissionVersion++
                                                }
                                                owner.lifecycle.addObserver(observer)
                                                onDispose { owner.lifecycle.removeObserver(observer) }
                                            }
                                            val permissionState = remember(permissionVersion, value.enabled, value.alertsEnabled) {
                                                runCatching {
                                                    val manager = context.getSystemService(android.app.NotificationManager::class.java)
                                                    when {
                                                        !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() -> "系统通知权限未开启"
                                                        value.enabled && manager?.getNotificationChannel(WidgetNotification.CHANNEL)?.importance == android.app.NotificationManager.IMPORTANCE_NONE -> "常驻天气渠道已关闭"
                                                        value.alertsEnabled && manager?.getNotificationChannel(WeatherAlertNotification.CHANNEL)?.importance == android.app.NotificationManager.IMPORTANCE_NONE -> "天气预警渠道已关闭"
                                                        else -> "系统通知权限已开启"
                                                    }
                                                }.getOrNull()
                                            }
                                            permissionState?.let { Text(it, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                                            StudioBarButton("系统通知权限与渠道", onClick = {
                                                val action = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                                context.startActivity(android.content.Intent(action)
                                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, WidgetNotification.CHANNEL))
                                            })
                                        }
                                    }
                                }
                                item {
                                    MochiListSection(header = "展开预览") {
                                        SectionBody {
                                            LabelValue("通知栏展开效果", if (snapshot?.weather?.current == null) "示例天气" else "")
                                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                                .background(c.bg).border(1.dp, c.edge, RoundedCornerShape(22.dp)).padding(8.dp)) {
                                                AndroidView(modifier = Modifier.fillMaxWidth().height(244.dp).clip(RoundedCornerShape(17.dp)),
                                                    factory = { FrameLayout(it).apply { clipToOutline = true } }, update = { host ->
                                                        host.removeAllViews()
                                                        val night = host.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                                                        val surface = if (night) android.graphics.Color.rgb(38, 40, 43) else android.graphics.Color.rgb(249, 250, 252)
                                                        host.background = GradientDrawable().apply {
                                                            setColor(surface)
                                                            cornerRadius = 17f * host.resources.displayMetrics.density
                                                        }
                                                        val views = WidgetNotification.views(host.context, value, snapshot ?: sample, true, System.currentTimeMillis())
                                                        host.addView(views.apply(host.context, host), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                                                    })
                                            }
                                            Text("通知背景和文字颜色跟随系统；修改即时保存。", fontSize = 11.sp, color = c.muted)
                                        }
                                    }
                                }
                                item {
                                    MochiListSection(header = "天气地点") {
                                        SectionBody {
                                            CitySearch(value.config, ::change, boxed = false)
                                            PlaceRow("跟随当前城市", selected?.displayName ?: "在应用中选择", value.config.locationMode == "selected") { change(value.config.copy(locationMode = "selected", cityKey = "", fixedCity = null)) }
                                            PlaceRow("跟随定位", cities.firstOrNull { it.locationKey == locatedKey }?.displayName ?: "先在应用中开启定位", value.config.locationMode == "located") { change(value.config.copy(locationMode = "located", cityKey = "", fixedCity = null)) }
                                            cities.sortedByDescending { it.isFavorite }.forEach { city ->
                                                PlaceRow(city.displayName, if (city.isFavorite) "收藏" else city.contextLabel, value.config.locationMode == "fixed" && value.config.cityKey == city.locationKey) {
                                                    change(value.config.copy(locationMode = "fixed", cityKey = city.locationKey, fixedCity = city))
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    MochiListSection(header = "图标与单位") {
                                        SectionBody {
                                            LabelValue("天气图标", "")
                                            IconSetPicker(value.config.iconSet) { change(value.config.copy(iconSet = it)) }
                                            Choice(listOf("摄氏 °C" to "c", "华氏 °F" to "f"), value.config.tempUnit) { change(value.config.copy(tempUnit = it)) }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(24.dp)) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconSetPicker(selected: String, onSelect: (String) -> Unit) {
    val colors = LocalStudio.current
    val density = LocalDensity.current.density
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WidgetIconSet.entries.forEach { set ->
            val active = WidgetIconSet.from(selected) == set
            Column(Modifier.weight(1f).clip(colors.shape)
                .background(if (active) colors.accent.copy(alpha = .14f) else colors.bg)
                .border(if (active) 2.dp else 1.dp, if (active) colors.accent else colors.edge, colors.shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(set.key) }
                .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(WeatherCondition.CLEAR, WeatherCondition.OVERCAST, WeatherCondition.RAIN).forEach { condition ->
                        if (set == WidgetIconSet.WUJIE) {
                            val bitmap = remember(condition, colors.classic, density) { WidgetIcons.bitmap(condition, !colors.classic, (30f * density).roundToInt()).asImageBitmap() }
                            Image(bitmap, condition.label, Modifier.size(30.dp))
                        } else Image(painterResource(classicConditionIconRes(condition) ?: R.drawable.ph_cloud), condition.label, Modifier.size(30.dp))
                    }
                }
                Text(set.title, color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
internal fun NativeWidgetPreview(style: WidgetStyle, kind: WidgetKind, config: WidgetConfig, data: WidgetSnapshot?, modifier: Modifier = Modifier,
                                 previewBackground: Int? = null, hostSize: SizeF? = null,
                                 livingSkyOverride: Boolean? = null,
                                 ambienceOverride: com.zhisheng.weather.data.AmbienceLevel? = null, nowOverride: Long? = null) {
    val inspection = LocalInspectionMode.current
    val sky = livingSkyOverride ?: if (inspection) false else
        com.zhisheng.weather.data.SettingsRepository.livingSky.collectAsState(initial = false).value
    val level = ambienceOverride ?: if (inspection) com.zhisheng.weather.data.AmbienceLevel.SUBTLE else
        com.zhisheng.weather.data.SettingsRepository.ambience.collectAsState(initial = com.zhisheng.weather.data.AmbienceLevel.SUBTLE).value
    val now = nowOverride ?: com.zhisheng.weather.ui.weatherPresentationTime()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val usableSize = hostSize?.takeIf { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
        val width = usableSize?.width ?: kind.width.toFloat()
        val height = usableSize?.height ?: kind.height.toFloat()
        val scale = minOf(maxWidth.value / width, maxHeight.value / height).coerceIn(.01f, 1f)
        AndroidView(factory = { context ->
            object : FrameLayout(context) {
                override fun onInterceptTouchEvent(event: MotionEvent?) = true
                override fun onTouchEvent(event: MotionEvent?) = false
            }
        }, modifier = Modifier.requiredSize(width.dp, height.dp).graphicsLayer { scaleX = scale; scaleY = scale }, update = { host ->
            // The editor background is a controlled surface, so auto text must follow
            // that surface rather than the phone's unrelated launcher wallpaper.
            val views = WidgetBinder.bind(host.context, -1, WidgetInstance(-1, style, kind), config, data,
                width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1), now = now, interactive = false,
                previewBackground = previewBackground, livingSkyEnabled = sky, ambienceLevel = level)
            if (inspection) {
                listOf("setFormat12Hour", "setFormat24Hour").forEach { method ->
                    views.setCharSequence(R.id.widget_time, method, "'09:41'")
                    views.setCharSequence(R.id.widget_date, method, "'9月20日 周日'")
                }
            }
            val previewKey = views.layoutId to config.iconSet
            if (host.tag == previewKey && host.childCount == 1) views.reapply(host.context, host.getChildAt(0))
            else {
                host.removeAllViews()
                host.addView(views.apply(host.context, host), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                host.tag = previewKey
            }
            host.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        })
    }
}

@Composable private fun CitySearch(config: WidgetConfig, change: (WidgetConfig) -> Unit, boxed: Boolean = true) {
    val context = LocalContext.current
    val c = LocalStudio.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<City>()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    // 组合期同步读会与 Application 启动迁移竞跑（首载+两次 commit 落主线程），首读放 IO。
    var recent by remember { mutableStateOf(emptyList<City>()) }
    LaunchedEffect(Unit) {
        recent = withContext(Dispatchers.IO) { WidgetStore.recentCities(context) }
    }
    LaunchedEffect(query) {
        error = false
        if (query.trim().isEmpty()) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(450)
        try { results = withContext(Dispatchers.IO) { CityRepository.search(query.trim()) } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = true; results = emptyList() }
        finally { searching = false }
    }
    val body: @Composable ColumnScope.() -> Unit = {
        OutlinedTextField(query, { query = it }, label = { Text("搜索地点", fontSize = 12.sp) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        if (searching) Text("正在查找…", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        else if (query.isNotBlank() && results.isEmpty()) Text(if (error) "暂时无法搜索，请稍后再试" else "未找到匹配地点", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        if (query.isBlank() && recent.isNotEmpty()) { Spacer(Modifier.height(14.dp)); LabelValue("最近选择", "") }
        (if (query.isBlank()) recent else results.take(8)).forEach { city ->
            PlaceRow(city.displayName, city.contextLabel, config.locationMode == "fixed" && city.locationKey == config.cityKey) {
                WidgetStore.rememberCity(context, city); recent = WidgetStore.recentCities(context)
                change(config.copy(locationMode = "fixed", cityKey = city.locationKey, fixedCity = city))
                query = ""
            }
        }
    }
    if (boxed) Panel(body) else Column(Modifier.fillMaxWidth(), content = body)
}

@Composable private fun SectionBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    // 统一用组件库分组卡片承载，自定义内容（天气预览、图标选择、排序等）原样放在卡片内部。
    MochiListCard(showDividers = false) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}
@Composable private fun StudioSlider(value: Float, change: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    MochiSlider(value, change, valueRange = range, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
}
@Composable private fun LabelValue(label: String, value: String) {
    val c = LocalStudio.current
    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = c.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = c.muted)
    }
}
@Composable private fun Choice(options: List<Pair<String, String>>, selected: String, onChange: (String) -> Unit) {
    MochiSegmentedControl(
        selectedIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0),
        onSelect = { index -> options.getOrNull(index)?.let { onChange(it.second) } },
        items = options.map { it.first }.toImmutableList(),
        modifier = Modifier.fillMaxWidth(),
    )
}
@Composable private fun Toggle(label: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 47.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = LocalStudio.current.ink, modifier = Modifier.weight(1f))
        MochiSwitch(checked = enabled, onCheckedChange = change)
    }
}
@Composable private fun PlaceRow(title: String, description: String, selected: Boolean, action: () -> Unit) {
    val c = LocalStudio.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = action).padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PhosphorIcon(R.drawable.ph_map_pin, null, Modifier.size(18.dp), if (selected) c.accent else c.muted)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, color = c.ink)
            if (description.isNotBlank()) Text(description, fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
        }
        if (selected) PhosphorIcon(R.drawable.ph_check, "已选择", Modifier.size(17.dp), c.accent)
    }
}
@Composable private fun MetricOrder(metrics: List<String>, change: (List<String>) -> Unit) {
    val c = LocalStudio.current
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(metrics)
    Column {
        metrics.forEach { key ->
            key(key) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).graphicsLayer { if (dragging == key) translationY = dragY }
                    .background(if (dragging == key) c.accent.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .pointerInput(key) {
                        detectDragGesturesAfterLongPress(onDragStart = { dragging = key; dragY = 0f }, onDragEnd = { dragging = null; dragY = 0f }, onDragCancel = { dragging = null; dragY = 0f }) { event, amount ->
                            event.consume(); dragY += amount.y
                            val step = with(density) { 44.dp.toPx() }
                            val old = latest.indexOf(key)
                            val target = (old + if (dragY > step / 2) 1 else if (dragY < -step / 2) -1 else 0).coerceIn(0, latest.lastIndex)
                            if (target != old) { change(latest.toMutableList().apply { add(target, removeAt(old)) }); dragY = 0f }
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Text("≡", color = c.muted, fontSize = 21.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(metricLabels[key].orEmpty(), color = c.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { val i = metrics.indexOf(key); if (i > 0) change(metrics.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = metrics.indexOf(key) > 0, contentPadding = PaddingValues(4.dp)) { Text("上移", fontSize = 10.sp, color = MochiTheme.colorScheme.onSurface) }
                    IconButton(onClick = { change(metrics - key) }, modifier = Modifier.size(48.dp)) { PhosphorIcon(R.drawable.ph_x, "移除${metricLabels[key]}", Modifier.size(14.dp), c.muted) }
                }
            }
        }
    }
}

internal fun widgetPreviewSample(now: Long = System.currentTimeMillis()): WidgetSnapshot {
    val day = java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return WidgetSnapshot(City("杭州", "示例天气", 30.27, 120.15, "widget-preview"), WeatherData(
        current = CurrentWeather(26.0, 27.0, WeatherCondition.PARTLY_CLOUDY, "多云", 62.0, 8.0, pressure = 1012.0, uvIndex = 3),
        hourly = (0..5).map { HourlyWeather(now + it * 3_600_000L, listOf(26.0,27.0,27.0,26.0,25.0,24.0)[it], if (it == 3) WeatherCondition.RAIN else WeatherCondition.PARTLY_CLOUDY, precipProb = listOf(10,10,20,60,20,10)[it]) },
        daily = (0..4).map { DailyWeather(day + it * 86_400_000L, listOf(28.0,29.0,26.0,27.0,28.0)[it], listOf(21.0,22.0,20.0,21.0,22.0)[it], if (it == 2) WeatherCondition.RAIN else if (it == 3) WeatherCondition.CLEAR else WeatherCondition.PARTLY_CLOUDY, precipProbability = if (it == 2) 70 else 10) },
        aqi = AqiInfo(42, "优"), forecastSummary = "午后有短时阵雨，出门记得带伞。", fetchedAt = now, updateTime = now))
}
