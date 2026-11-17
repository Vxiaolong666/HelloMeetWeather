package com.HelloMeet.weather.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.HelloMeet.weather.data.AccentTone
import com.HelloMeet.weather.data.LocationSource
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.data.SourcePref
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.ui.about.WeatherMark
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiBadge
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiChipGroup
import ui.feice.mochi.basic.MochiPageControl
import ui.feice.mochi.basic.MochiSelectFrame
import ui.feice.mochi.basic.MochiSelectionCheck
import ui.feice.mochi.basic.MochiStepSlider
import ui.feice.mochi.basic.MochiStepSliderDefaults
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.effect.content.mochiBlur
import ui.feice.mochi.effect.content.mochiBlurAlpha
import ui.feice.mochi.effect.content.mochiBlurReveal
import ui.feice.mochi.effect.mask.mochiScrollFadeMask
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.navigation.MochiTitleBarDefaults
import ui.feice.mochi.picker.MochiSeedPalette
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.emphasized
import ui.feice.mochi.utils.rememberMochiHaptics

private const val PageCount = 6
private const val StaggerMillis = 1300f
private const val WelcomeLeadMillis = 600L
private val PageCorner = 32.dp

/**
 * 首次启动的完整引导（学 Mochi demo 的设置助理）：整页铺满、逐页推进，
 * 欢迎页先一笔一笔写出「你好」，后面依次处理定位、外观、数据与转场。
 * 引导层盖在 App 上，自己决定何时结束，不跟随定位结果提前消失。
 */
@Composable
internal fun FirstRunOnboarding(
    viewModel: WeatherViewModel,
    visible: Boolean,
    onComplete: (located: Boolean) -> Unit,
    onChooseCity: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val motion = !rememberReducedMotion()
    // 这些状态在引导暂时隐藏（去选城、弹窗）时也要留着，
    // 否则 rememberSaveable 会随子树一起被丢弃，回来又从第一页开始。
    var step by rememberSaveable { mutableStateOf(0) }
    var located by rememberSaveable { mutableStateOf(false) }
    var locating by rememberSaveable { mutableStateOf(false) }
    var locationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var leaving by remember { mutableStateOf(false) }
    // 液态玻璃在引导里默认关闭：第一次到达外观步时写一次，之后用户自己打开就不再回写。
    var glassDefaultApplied by rememberSaveable { mutableStateOf(false) }

    val pager = remember { Animatable(0f) }
    val staggers = remember { List(PageCount) { Animatable(0f) } }
    val open = remember { Animatable(0f) }
    var firstWelcome by remember { mutableStateOf(true) }

    LaunchedEffect(visible) {
        if (!visible) {
            open.snapTo(0f)
            return@LaunchedEffect
        }
        if (motion) open.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 220f)) else open.snapTo(1f)
    }
    LaunchedEffect(step, motion, visible) {
        if (!visible) return@LaunchedEffect
        if (motion) {
            pager.animateTo(step.toFloat(), spring(dampingRatio = 0.92f, stiffness = 210f))
        } else {
            pager.snapTo(step.toFloat())
        }
    }
    LaunchedEffect(step, motion, visible) {
        if (!visible) return@LaunchedEffect
        if (!motion) {
            staggers.forEach { it.snapTo(1f) }
            return@LaunchedEffect
        }
        staggers[step].snapTo(0f)
        if (step == 0 && firstWelcome) delay(WelcomeLeadMillis)
        if (step > 0) firstWelcome = false
        staggers[step].animateTo(1f, tween(StaggerMillis.toInt(), easing = LinearEasing))
    }

    // 系统返回只退回到上一步，不直接退出引导。
    BackHandler(enabled = visible && step > 0 && !leaving) { step -= 1 }

    fun startLocate() {
        if (operation?.isActive == true) return
        locating = true
        locationMessage = null
        operation = scope.launch {
            try {
                // 权限弹窗可能在 Activity 回到前台之前就回调，单次定位会在这个间隙失败。
                lifecycle.withResumed { Unit }
                val ok = viewModel.locateInitialCity()
                located = ok
                if (!ok) {
                    locationMessage = viewModel.uiState.value.locateMessage
                        ?: "暂时没能定位，可以先手动选择城市"
                }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (_: Exception) {
                locationMessage = "定位暂时失败，可以先手动选择城市"
            } finally {
                locating = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[LocationSource.PERMISSION] == true || LocationSource.hasPermission(context)) {
            startLocate()
        } else {
            locationMessage = "未授予定位权限，可以先手动选择城市，稍后在设置中开启"
        }
    }

    fun requestLocation() {
        if (LocationSource.hasPermission(context)) startLocate()
        else permission.launch(LocationSource.requestedPermissions(precise = true))
    }

    fun finish() {
        if (leaving) return
        leaving = true
        if (!motion) {
            onComplete(located)
            return
        }
        scope.launch {
            open.animateTo(0f, spring(dampingRatio = 1f, stiffness = 240f))
            onComplete(located)
        }
    }

    if (!visible) return
    val uiState by viewModel.uiState.collectAsState()
    // 从搜索页手动选城回来时，城市已经就位，定位步骤直接算完成。
    LaunchedEffect(step, uiState.selectedCity?.locationKey) {
        if (step >= 1 && uiState.selectedCity != null) located = true
    }

    val colorMode by SettingsRepository.mochiColorMode.collectAsState(initial = "system")
    val graphicsLevel by SettingsRepository.mochiGraphicsLevel.collectAsState(initial = "high")
    val hdrEnabled by SettingsRepository.mochiHdrEnabled.collectAsState(initial = false)
    val glassEnabled by SettingsRepository.mochiGlass.collectAsState(initial = false)
    val preciseLocation by SettingsRepository.preciseLocationEnabled.collectAsState(initial = true)
    val sourcePref by SettingsRepository.sourcePref.collectAsState(initial = SourcePref.AUTO)
    val transition by SettingsRepository.mochiPageTransition.collectAsState(initial = "app")
    val swipeBack by SettingsRepository.mochiSwipeBack.collectAsState(initial = true)

    // 外观步之前不动用户的设置；第一次进入外观步时把液态玻璃关掉。
    LaunchedEffect(visible, step) {
        if (!visible || step < 2 || glassDefaultApplied) return@LaunchedEffect
        glassDefaultApplied = true
        SettingsRepository.setMochiGlass(false)
    }

    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = open.value
                val s = 0.94f + 0.06f * p
                scaleX = s
                scaleY = s
                mochiBlurAlpha(p.coerceIn(0f, 1f), 18.dp * (1f - p), continuous = true)
            }
            .mochiBlur(continuous = true) { 18.dp * (1f - open.value) }
            .background(MochiDefaults.groupedBackground())
            // 引导盖着的时候，底下的 App 收不到触摸
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(MochiTitleBarDefaults.Height)
                    .padding(horizontal = MochiButtonDefaults.BarHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                TopAction(visible = step < PageCount - 1) {
                    Text(
                        "跳过",
                        style = ts.bodyLarge,
                        color = cs.primary,
                        modifier = Modifier
                            .clickable(onClick = { finish() })
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val width = constraints.maxWidth.toFloat()
                for (i in 0 until PageCount) {
                    key(i) {
                        // 前后两页提前组合好，翻页开始的那一帧不用现组合。
                        val present by remember {
                            derivedStateOf { abs(i - pager.value) < 0.999f || abs(i - step) == 1 }
                        }
                        if (present) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // 新页从右边推进来；旧页往左挪一小段、缩小、糊掉。
                                        val d = i - pager.value
                                        if (d >= 0f) {
                                            translationX = d * width
                                        } else {
                                            translationX = d * width * 0.28f
                                            val s = 1f + d * 0.06f
                                            scaleX = s
                                            scaleY = s
                                            mochiBlurAlpha((1f + d).coerceIn(0f, 1f), 10.dp * -d, continuous = true)
                                        }
                                    }
                                    .mochiBlur(continuous = true) {
                                        val d = i - pager.value
                                        if (d >= 0f) 12.dp * d else 10.dp * -d
                                    }
                                    .graphicsLayer {
                                        val d = i - pager.value
                                        val moving = abs(d) > 0.001f
                                        clip = moving
                                        if (moving) {
                                            shape = MochiRoundedRectangle(PageCorner * (abs(d) * 6f).coerceAtMost(1f))
                                        }
                                    },
                            ) {
                                val t = { staggers[i].value }
                                when (i) {
                                    0 -> WelcomePage(
                                        t = t,
                                        lead = if (firstWelcome) WelcomeLeadMillis else 0L,
                                        motion = motion,
                                        active = step == 0,
                                    )
                                    1 -> LocationPage(
                                        t = t,
                                        locating = locating,
                                        located = located,
                                        preciseLocation = preciseLocation,
                                        message = locationMessage,
                                        city = uiState.selectedCity,
                                        onRequest = { requestLocation() },
                                        onChooseCity = onChooseCity,
                                    )
                                    2 -> AppearancePage(
                                        t = t,
                                        colorMode = colorMode,
                                        hdrEnabled = hdrEnabled,
                                        // 默认关闭的写入在 LaunchedEffect 里，这一帧按关闭显示，避免开关闪一下。
                                        glassEnabled = glassDefaultApplied && glassEnabled,
                                    )
                                    3 -> EffectsPage(
                                        t = t,
                                        graphicsLevel = graphicsLevel,
                                    )
                                    4 -> PreferencesPage(
                                        t = t,
                                        sourcePref = sourcePref,
                                        transition = transition,
                                        swipeBack = swipeBack,
                                    )
                                    else -> DonePage(
                                        t = t,
                                        active = step == i,
                                        summary = summaryOf(colorMode, graphicsLevel, sourcePref, transition),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MochiPageControl(
                    pageCount = PageCount,
                    position = { pager.value },
                    onPageSelect = { if (!leaving) step = it },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    MochiButton(
                        onClick = { if (!leaving) step -= 1 },
                        enabled = !leaving,
                        style = MochiButtonStyle.Tonal,
                        modifier = Modifier.width(104.dp),
                        minHeight = 54.dp,
                    ) {
                        Text(
                            "上一步",
                            style = ts.bodyLarge.emphasized(),
                            // MochiButton 提供的是库内 LocalContentColor，Material Text 不继承它，这里显式取按钮内容色。
                            color = MochiButtonDefaults.colors(MochiButtonStyle.Tonal).content,
                        )
                    }
                }
                MochiButton(
                    onClick = { if (step < PageCount - 1) step += 1 else finish() },
                    enabled = !locating && !leaving,
                    modifier = Modifier.weight(1f),
                    minHeight = 54.dp,
                ) {
                    Text(
                        if (step < PageCount - 1) "继续" else "开始使用",
                        style = ts.bodyLarge.emphasized(),
                        color = cs.onPrimary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun summaryOf(
    colorMode: String,
    graphicsLevel: String,
    sourcePref: SourcePref,
    transition: String,
): String = listOf(
    when (colorMode) {
        "light" -> "浅色"
        "dark" -> "深色"
        else -> "跟随系统"
    },
    "${MochiGraphicsLevels.firstOrNull { it.second == graphicsLevel }?.first ?: "高"}画面",
    sourcePref.cn,
    PageTransitionStyle.from(transition).cn,
).joinToString(" · ")

/** 顶栏按钮出现 / 消失：模糊淡入淡出 + 轻微缩放；看不见时不接收点击。 */
@Composable
private fun TopAction(visible: Boolean, content: @Composable () -> Unit) {
    val a by animateFloatAsState(
        if (visible) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "onboarding-top",
    )
    Box(
        Modifier
            .graphicsLayer {
                val s = 0.8f + 0.2f * a
                scaleX = s
                scaleY = s
            }
            .mochiBlurReveal({ a }, blur = 6.dp),
    ) {
        content()
        if (!visible) {
            Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        }
    }
}

/** 页内元素按顺序依次升起：第 [i] 个比前一个晚 70ms，各自 480ms 从下面糊着浮上来。 */
private fun Modifier.rise(t: () -> Float, i: Int): Modifier = mochiBlurReveal({
    val v = ((t() * StaggerMillis - i * 70f) / 480f).coerceIn(0f, 1f)
    1f - (1f - v) * (1f - v) * (1f - v)
}, blur = 10.dp, rise = 22.dp)

/** 卡片类元素弹出来：按顺序浮上来，同时从 0.82 带回弹放大到 1。 */
private fun Modifier.pop(t: () -> Float, i: Int): Modifier = graphicsLayer {
    val ms = t() * StaggerMillis - i * 70f
    val k = if (ms <= 0f) 0f else 1f - exp(-ms / 120f) * cos(ms / 75f)
    val sc = 0.82f + 0.18f * k
    scaleX = sc
    scaleY = sc
}.mochiBlurReveal({
    val v = ((t() * StaggerMillis - i * 70f) / 360f).coerceIn(0f, 1f)
    1f - (1f - v) * (1f - v) * (1f - v)
}, blur = 10.dp, rise = 18.dp)

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .mochiScrollFadeMask(scroll)
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** 内容居中的页（欢迎、完成）：内容不长也能上下拖动。 */
@Composable
private fun CenteredPage(content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String, t: () -> Float) {
    val ts = MochiTheme.textStyles
    val cs = MochiTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = ts.largeTitle.copy(textAlign = TextAlign.Center),
            color = cs.onSurface,
            modifier = Modifier.rise(t, 0),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = ts.bodyLarge.copy(textAlign = TextAlign.Center),
            color = cs.onSurfaceVariant,
            modifier = Modifier.rise(t, 1),
        )
    }
}

// ---------- 第 1 页：欢迎 ----------

@Composable
private fun WelcomePage(t: () -> Float, lead: Long, motion: Boolean, active: Boolean) {
    val ts = MochiTheme.textStyles
    val cs = MochiTheme.colorScheme
    CenteredPage {
        WelcomeMark(Modifier.pop(t, 1))
        Spacer(Modifier.height(12.dp))
        HandwrittenHello(
            modifier = Modifier.fillMaxWidth().height(108.dp),
            startDelay = lead,
            motion = motion,
            active = active,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "遇见天气",
            style = ts.titleLarge.emphasized(),
            color = cs.onSurface,
            modifier = Modifier.rise(t, 2),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "看得见天气，也看得见填空。\n花半分钟，把常用的外观和数据偏好选好。",
            style = ts.bodyLarge.copy(textAlign = TextAlign.Center),
            color = cs.onSurfaceVariant,
            modifier = Modifier.rise(t, 3),
        )
    }
}

// ---------- 第 2 页：定位 ----------

/**
 * 欢迎页顶部的品牌图标，画法与关于页一致（[WeatherMark]）。
 * 交互也保留：点一下云被按扁，长按整枚图标鼓起并轻震，松手弹回。
 */
@Composable
private fun WelcomeMark(modifier: Modifier = Modifier) {
    val poke = remember { Animatable(0f) }
    val charge = remember { Animatable(0f) }
    val haptics = rememberMochiHaptics()
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .size(96.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        coroutineScope {
                            val ticker = launch {
                                var step = 0
                                snapshotFlow { (charge.value * 4).toInt() }.collect {
                                    if (it > step) {
                                        step = it
                                        haptics.tick()
                                    }
                                }
                            }
                            charge.animateTo(1f, tween(800, easing = LinearEasing))
                            ticker.cancel()
                        }
                        haptics.confirm()
                        scope.launch { charge.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 240f)) }
                    }
                    val up = waitForUpOrCancellation()
                    if (job.isActive) {
                        job.cancel()
                        val tap = up != null && charge.value < 0.3f
                        scope.launch { charge.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
                        if (tap) {
                            haptics.tick()
                            scope.launch {
                                poke.animateTo(1f, tween(70))
                                poke.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 480f))
                            }
                        }
                    }
                }
            },
    ) {
        WeatherMark(
            Modifier.fillMaxSize(),
            poke = { poke.value },
            swell = { charge.value },
            tremble = { charge.value.let { c -> 2.5f * c * sin(c * 70f) } },
        )
    }
}

@Composable
private fun LocationPage(
    t: () -> Float,
    locating: Boolean,
    located: Boolean,
    preciseLocation: Boolean,
    message: String?,
    city: City?,
    onRequest: () -> Unit,
    onChooseCity: () -> Unit,
) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    val scope = rememberCoroutineScope()
    PageColumn {
        PageHeading(
            "先确定你在哪里",
            "允许定位后立刻取一次当前位置，作为首页的默认城市。不授权也可以手动选城。",
            t,
        )
        MochiListSection(Modifier.pop(t, 2)) {
            MochiListRow(
                title = when {
                    located -> "位置已就绪"
                    locating -> "正在定位"
                    else -> "尚未定位"
                },
                subtitle = when {
                    located -> "首页会用这个位置作为默认城市"
                    locating -> "正在向系统请求位置信息"
                    else -> "也可以直接选择城市"
                },
                loading = locating,
            )
            MochiListRow(
                "高精度定位",
                subtitle = "获得更精确的天气信息",
                trailing = {
                    MochiSwitch(
                        checked = preciseLocation,
                        onCheckedChange = { on ->
                            scope.launch { SettingsRepository.setPreciseLocationEnabled(on) }
                        },
                    )
                },
            )
        }
        // 位置就绪后把拿到的地点摊开给用户看一眼，确认没错再继续。
        val current = city?.takeIf { located }
        if (current != null) {
            Spacer(Modifier.height(16.dp))
            MochiListSection(
                header = "当前位置",
                footer = if (current.precisePermissionGranted) {
                    "之后也可以添加别的城市哦"
                } else {
                    "只拿到城市级坐标；打开高精度定位可以精确到街道。"
                },
                modifier = Modifier.pop(t, 3),
            ) {
                MochiListRow(
                    title = current.displayName,
                    subtitle = current.contextLabel.takeIf { it.isNotBlank() },
                )
                MochiListRow(
                    title = "坐标",
                    value = "%.4f, %.4f".format(current.latitude, current.longitude),
                )
                current.locationAccuracyMeters
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.let { accuracy ->
                        MochiListRow(
                            title = "精度",
                            value = if (accuracy >= 1000f) {
                                "约 %.1f km".format(accuracy / 1000f)
                            } else {
                                "±%.0f m".format(accuracy)
                            },
                        )
                    }
                MochiListRow(title = "来源", value = current.coordinateLabel)
            }
        }
        Spacer(Modifier.height(16.dp))
        MochiButton(
            onClick = onRequest,
            enabled = !locating,
            style = MochiButtonStyle.Tonal,
            modifier = Modifier.fillMaxWidth().rise(t, 3),
            minHeight = 50.dp,
        ) {
            Text(
                if (located) "重新定位" else "允许定位并获取当前位置",
                style = ts.bodyLarge.emphasized(),
                color = MochiButtonDefaults.colors(MochiButtonStyle.Tonal).content,
            )
        }
        message?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                it,
                style = ts.footnote.copy(textAlign = TextAlign.Center),
                color = cs.error,
                modifier = Modifier.fillMaxWidth().rise(t, 4),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "手动选择城市",
            style = ts.bodyLarge,
            color = cs.primary,
            modifier = Modifier
                .clickable(onClick = onChooseCity)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .rise(t, 4),
        )
    }
}

// ---------- 第 3 页：外观 ----------

private enum class ShadeOption(val key: String, val label: String) {
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    AUTO("system", "跟随系统"),
}

@Composable
private fun AppearancePage(
    t: () -> Float,
    colorMode: String,
    hdrEnabled: Boolean,
    glassEnabled: Boolean,
) {
    val scope = rememberCoroutineScope()
    val cs = MochiTheme.colorScheme
    val accentTone by SettingsRepository.accentTone.collectAsState(initial = AccentTone.STANDARD)
    val hdrAck by SettingsRepository.mochiHdrBetaAck.collectAsState(initial = false)
    val glassAck by SettingsRepository.mochiGlassBetaAck.collectAsState(initial = false)
    var pendingBeta by remember { mutableStateOf<BetaTarget?>(null) }
    PageColumn {
        PageHeading("外观", "先选一个顺眼的明暗和颜色，之后随时能在设置里改。", t)
        Row(
            Modifier.fillMaxWidth().pop(t, 2),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ShadeOption.entries.forEach { option ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val selected = option.key == colorMode
                    MochiSelectFrame(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                // 主题模式与 Mochi 主题共用同一个偏好，选中后整页立即换色。
                                scope.launch { SettingsRepository.setMochiColorMode(option.key) }
                            }
                        },
                        modifier = Modifier.width(84.dp).aspectRatio(0.56f),
                        cornerRadius = 20.dp,
                    ) {
                        ShadeThumb(option, cs.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(option.label, style = MochiTheme.textStyles.bodyMedium, color = cs.onSurface)
                    Spacer(Modifier.height(6.dp))
                    MochiSelectionCheck(selected)
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        val uiPrefs = rememberMochiUiPrefs()
        MochiListSection(
            header = "主题色",
            modifier = Modifier.pop(t, 3),
        ) {
            MochiSeedPalette(
                selected = Color(uiPrefs.seed),
                onSelect = { picked -> scope.launch { SettingsRepository.setMochiSeed(picked.toArgb()) } },
                style = uiPrefs.paletteStyle,
                custom = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        OnboardingChipSection("强调色", t, 4) {
            AccentTone.entries.forEach { tone ->
                MochiChip(
                    label = tone.cn,
                    selected = tone == accentTone,
                    onClick = { scope.launch { SettingsRepository.setAccentTone(tone) } },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        MochiListSection(Modifier.pop(t, 5)) {
            MochiListRow(
                "HDR 高光",
                subtitle = "边缘流光与按压高光用超过 SDR 白的亮度",
                titleTrailing = { MochiBadge(text = "Beta测试功能") },
                trailing = {
                    MochiSwitch(
                        checked = hdrEnabled,
                        onCheckedChange = { on ->
                            if (on && !hdrAck) pendingBeta = BetaTarget.HDR
                            else scope.launch { SettingsRepository.setMochiHdrEnabled(on) }
                        },
                    )
                },
            )
            MochiListRow(
                "液态玻璃",
                subtitle = "顶栏、标签栏与面板使用玻璃材质",
                titleTrailing = { MochiBadge(text = "Beta测试功能") },
                trailing = {
                    MochiSwitch(
                        checked = glassEnabled,
                        onCheckedChange = { on ->
                            if (on && !glassAck) pendingBeta = BetaTarget.GLASS
                            else scope.launch { SettingsRepository.setMochiGlass(on) }
                        },
                    )
                },
            )
        }
    }
    pendingBeta?.let { target ->
        MochiBetaNoticeDialog(
            title = "这是一个实验性功能",
            message = "当前处于并将长期处于Beta实验性功能，内容可能随时增加、修改、删除，并可能存在性能下降，耗电增加或显示异常问题，确认继续使用吗？",
            onDismiss = {
                scope.launch {
                    when (target) {
                        BetaTarget.HDR -> SettingsRepository.setMochiHdrEnabled(true)
                        BetaTarget.GLASS -> SettingsRepository.setMochiGlass(true)
                    }
                }
                pendingBeta = null
            },
            onNeverShowAgain = {
                scope.launch {
                    when (target) {
                        BetaTarget.HDR -> SettingsRepository.setMochiHdrBetaAck(true)
                        BetaTarget.GLASS -> SettingsRepository.setMochiGlassBetaAck(true)
                    }
                }
            },
            onCancel = { pendingBeta = null },
            visible = true,
        )
    }
}

private enum class BetaTarget { HDR, GLASS }

/** 明暗缩略图：一块小屏，上面两张卡片和几条字条；自动 = 斜着一半浅一半深。 */
@Composable
private fun ShadeThumb(option: ShadeOption, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        fun screen(dark: Boolean) {
            val bg = if (dark) Color(0xFF111111) else Color(0xFFF2F2F7)
            val card = if (dark) Color(0xFF2A2A2C) else Color.White
            val bar = if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.12f)
            drawRect(bg)
            val pad = size.width * 0.12f
            val w = size.width - pad * 2f
            val r = CornerRadius(size.width * 0.08f)
            drawRoundRect(bar, Offset(pad, size.height * 0.1f), Size(w * 0.55f, size.height * 0.035f), r)
            drawRoundRect(card, Offset(pad, size.height * 0.2f), Size(w, size.height * 0.28f), r)
            for (k in 0 until 3) {
                val y = size.height * (0.24f + k * 0.08f)
                drawCircle(
                    if (k == 0) accent else bar,
                    size.width * 0.045f,
                    Offset(pad + w * 0.14f, y + size.height * 0.015f),
                )
                drawRoundRect(bar, Offset(pad + w * 0.28f, y), Size(w * 0.5f, size.height * 0.03f), r)
            }
            drawRoundRect(card, Offset(pad, size.height * 0.53f), Size(w, size.height * 0.16f), r)
            drawRoundRect(accent, Offset(pad, size.height * 0.82f), Size(w, size.height * 0.08f), CornerRadius(size.height))
        }
        when (option) {
            ShadeOption.LIGHT -> screen(false)
            ShadeOption.DARK -> screen(true)
            ShadeOption.AUTO -> {
                screen(false)
                val cut = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                clipPath(cut) { screen(true) }
            }
        }
    }
}

// ---------- 第 4 页：画面效果 ----------

/** 三档画面效果的说明文案；档位名称统一取 [MochiGraphicsLevels]，不和设置页叫法分叉。 */
private class EffectsLevel(
    val summary: String,
    val blur: String,
    val shader: String,
    val reveal: String,
    val refresh: String,
)

private val EffectsLevels = listOf(
    EffectsLevel(
        summary = "关掉着色器、模糊减半，界面最轻快。旧设备或省电时选它。",
        blur = "基础",
        shader = "关",
        reveal = "硬边",
        refresh = "跟随系统",
    ),
    EffectsLevel(
        summary = "模糊与着色器全部开启，效果和耗电的平衡点，也是默认档。",
        blur = "标准",
        shader = "开",
        reveal = "羽化",
        refresh = "跟随系统",
    ),
    EffectsLevel(
        summary = "在「高」的基础上模糊再加强一半，并请求屏幕最高刷新率。更耗电。",
        blur = "加强",
        shader = "开",
        reveal = "羽化",
        refresh = "最高",
    ),
)

@Composable
private fun EffectsPage(t: () -> Float, graphicsLevel: String) {
    val scope = rememberCoroutineScope()
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    val index = MochiGraphicsLevels.indexOfFirst { it.second == graphicsLevel }.coerceAtLeast(0)
    val level = EffectsLevels[index]

    // 档位渐变沿用设置页：切档时颜色平滑过去，滑条填充段和星点跟着变。
    val target = GraphicsGradients[index]
    val c0 by animateColorAsState(target[0], tween(320), label = "onboarding-graphics-0")
    val c1 by animateColorAsState(target[1], tween(320), label = "onboarding-graphics-1")
    val c2 by animateColorAsState(target[2], tween(320), label = "onboarding-graphics-2")
    val sparkle by animateColorAsState(
        Color.White.copy(alpha = 0.26f + 0.22f * index),
        tween(320),
        label = "onboarding-graphics-sparkle",
    )
    val fillBrush = remember(c0, c1, c2) { Brush.horizontalGradient(listOf(c0, c1, c2)) }
    val sliderColors = MochiStepSliderDefaults.colors(fillBrush = fillBrush, sparkleColor = sparkle)

    PageColumn {
        PageHeading("画面效果", "越往右越好看，也越耗电。随时可以改。", t)
        Text(
            "${MochiGraphicsLevels[index].first}画面效果",
            style = ts.titleLarge.emphasized().copy(textAlign = TextAlign.Center),
            color = cs.primary,
            modifier = Modifier.fillMaxWidth().pop(t, 2),
        )
        MochiStepSlider(
            value = index,
            onValueChange = { i ->
                if (i != index) {
                    scope.launch { SettingsRepository.setMochiGraphicsLevel(MochiGraphicsLevels[i].second) }
                }
            },
            count = MochiGraphicsLevels.size,
            colors = sliderColors,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 12.dp)
                .pop(t, 3),
        )
        Text(
            level.summary,
            style = ts.bodyMedium.copy(textAlign = TextAlign.Center),
            color = cs.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().rise(t, 4),
        )
        Spacer(Modifier.height(20.dp))
        MochiListSection(Modifier.pop(t, 5)) {
            MochiListRow(title = "模糊", value = level.blur)
            MochiListRow(title = "着色器", value = level.shader)
            MochiListRow(title = "换明暗揭开", value = level.reveal)
            MochiListRow(title = "刷新率", value = level.refresh)
        }
    }
}

// ---------- 第 5 页：数据与转场 ----------

@Composable
private fun PreferencesPage(
    t: () -> Float,
    sourcePref: SourcePref,
    transition: String,
    swipeBack: Boolean,
) {
    val scope = rememberCoroutineScope()
    PageColumn {
        PageHeading("数据与转场", "这些选项决定天气从哪来、页面怎么切换。", t)
        MochiListSection(Modifier.pop(t, 2)) {
            MochiListRow(
                "侧推返回",
                subtitle = "从屏幕边缘右滑返回上一级",
                trailing = {
                    MochiSwitch(
                        checked = swipeBack,
                        onCheckedChange = { on -> scope.launch { SettingsRepository.setMochiSwipeBack(on) } },
                    )
                },
            )
        }
        Spacer(Modifier.height(22.dp))
        OnboardingChipSection("天气来源", t, 3) {
            listOf(
                SourcePref.AUTO,
                SourcePref.ZHISHENG,
                SourcePref.XIAOMI,
                SourcePref.NMC,
                SourcePref.OPEN_METEO,
            ).forEach { pref ->
                MochiChip(
                    label = pref.cn,
                    selected = pref == sourcePref,
                    onClick = { scope.launch { SettingsRepository.setSourcePref(pref) } },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        OnboardingChipSection("应用转场", t, 4) {
            PageTransitionStyle.entries.forEach { style ->
                MochiChip(
                    label = style.cn,
                    selected = style.key == transition,
                    onClick = { scope.launch { SettingsRepository.setMochiPageTransition(style.key) } },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "更细的设置可以稍后进行",
            style = MochiTheme.textStyles.footnote.copy(textAlign = TextAlign.Center),
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingChipSection(
    title: String,
    t: () -> Float,
    index: Int,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().pop(t, index)) {
        MochiListSectionHeader(title)
        MochiChipGroup(wrap = true, contentPadding = PaddingValues(horizontal = 0.dp)) {
            content()
        }
    }
}

// ---------- 第 6 页：准备就绪 ----------

@Composable
private fun DonePage(t: () -> Float, active: Boolean, summary: String) {
    val haptics = rememberMochiHaptics()
    val ring = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    val pop = remember { Animatable(0.7f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        ring.snapTo(0f)
        tick.snapTo(0f)
        pop.snapTo(0.7f)
        delay(260)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
        ring.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        haptics.confirm()
        tick.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    CenteredPage {
        val path = remember { Path() }
        val part = remember { Path() }
        val measure = remember { PathMeasure() }
        Canvas(
            Modifier.size(112.dp).graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = (ring.value * 3f).coerceIn(0f, 1f)
            },
        ) {
            val stroke = 5.dp.toPx()
            val r = size.minDimension / 2f - stroke / 2f
            drawCircle(cs.primary.copy(alpha = tick.value), r)
            drawArc(
                cs.primary,
                -90f,
                360f * ring.value,
                false,
                Offset(stroke / 2f, stroke / 2f),
                Size(r * 2f, r * 2f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (tick.value > 0f) {
                path.reset()
                path.moveTo(size.width * 0.3f, size.height * 0.52f)
                path.lineTo(size.width * 0.44f, size.height * 0.66f)
                path.lineTo(size.width * 0.71f, size.height * 0.37f)
                measure.setPath(path, false)
                part.reset()
                measure.getSegment(0f, measure.length * tick.value, part, true)
                drawPath(
                    part,
                    cs.onPrimary,
                    style = Stroke(stroke * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "准备就绪",
            style = ts.largeTitle,
            color = cs.onSurface,
            modifier = Modifier.rise(t, 1),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            summary,
            style = ts.bodyLarge.emphasized().copy(textAlign = TextAlign.Center),
            color = cs.primary,
            modifier = Modifier.rise(t, 2),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "这些设置随时可以在「设置」里修改。",
            style = ts.bodyMedium.copy(textAlign = TextAlign.Center),
            color = cs.onSurfaceVariant,
            modifier = Modifier.rise(t, 3),
        )
    }
}

// ---------- 手写的「你好 / hello」 ----------

/**
 * 一个手写的词：几笔（一笔 = 一条中心线），按书写顺序。[box] 是笔画所在的范围（设计稿单位）。
 * [lift] = 每笔之间提笔（「你好」按笔顺一笔一笔写）；false = 连笔（「hello」按字母分段）。
 */
private class InkWord(paths: List<String>, val box: androidx.compose.ui.geometry.Rect, val lift: Boolean) {
    val strokes: List<Path> = paths.map { PathParser().parsePathString(it).toPath() }
    val lengths: FloatArray = strokes.map { PathMeasure().apply { setPath(it, false) }.length }.toFloatArray()
    val durations: FloatArray = FloatArray(strokes.size) { i ->
        if (lift) 90f + lengths[i] * 1.35f else 120f + lengths[i] * 1.55f
    }
    val gap: Float = if (lift) 40f else 0f
    val starts: FloatArray = FloatArray(strokes.size).also { s ->
        var acc = 0f
        for (i in strokes.indices) {
            s[i] = acc
            acc += durations[i] + gap
        }
    }
    val end: Float = starts.last() + durations.last()
    val easing: Easing = if (lift) StrokeEasing else LetterEasing

    /** 第 [i] 笔在 [clock]（ms）时写出来的长度 */
    fun written(i: Int, clock: Float): Float {
        val t = ((clock - starts[i]) / durations[i]).coerceIn(0f, 1f)
        return lengths[i] * easing.transform(t)
    }
}

// 提笔字：起笔、收笔只稍稍放缓，整体顺下来。
private val StrokeEasing = CubicBezierEasing(0.3f, 0.15f, 0.35f, 1f)

// 连笔字：字母之间只放慢一点，速度不归零。
private val LetterEasing = CubicBezierEasing(0.32f, 0.12f, 0.5f, 0.9f)

private const val InkWidth = 7f

private val Nihao = InkWord(
    listOf(
        // 你
        "M30,8 C26,22 18,34 8,44",
        "M21,30 L21,94",
        "M52,8 C49,18 44,26 38,32",
        "M44,22 L86,22 C84,30 80,36 76,40",
        "M64,34 L64,88 C64,94 60,95 54,90",
        "M48,52 C45,62 40,70 34,76",
        "M78,52 C84,60 88,68 90,74",
        // 好
        "M130,8 C126,30 120,48 116,58 C128,66 140,78 148,90",
        "M142,30 C140,56 130,78 114,92",
        "M110,46 L152,42",
        "M160,18 L192,16 C186,26 180,32 174,38",
        "M176,38 L176,88 C176,94 172,96 166,92",
        "M156,58 L200,54",
    ),
    androidx.compose.ui.geometry.Rect(0f, 0f, 208f, 102f),
    lift = true,
)

private val Hello = InkWord(
    listOf(
        // h
        "M14,104 C34,92 56,62 58,38 C60,18 50,12 44,16 C36,22 34,60 33,104 C38,84 48,72 58,72 C68,72 70,82 70,92 C70,100 72,104 80,103",
        // e
        "M80,103 C92,101 106,92 108,82 C110,72 100,68 94,72 C86,78 86,98 100,103 C112,107 124,98 132,86",
        // l
        "M132,86 C142,70 152,48 152,32 C152,18 144,14 140,18 C134,26 132,64 134,90 C135,100 140,104 148,102",
        // l
        "M148,102 C160,98 170,72 174,50 C177,32 172,16 166,18 C160,22 158,62 160,88 C161,100 168,104 176,100",
        // o
        "M176,100 C184,96 186,84 196,76 C206,70 218,74 218,88 C218,100 206,106 198,102 C190,98 190,82 202,76 C212,72 222,76 232,70",
    ),
    androidx.compose.ui.geometry.Rect(6f, 6f, 240f, 112f),
    lift = false,
)

private val Words = listOf(Nihao, Hello)

/** 写完一笔后笔画粗细的回弹。 */
private fun strokeBounce(t: Float, amount: Float): Float =
    if (t < 0f) 0f else amount * exp(-t / 150f) * sin(t / 46f)

/** 整个词写完后的回弹：轻轻放大约 4% 再落定。 */
private fun wordBounce(t: Float): Float = if (t < 0f) 0f else 0.045f * exp(-t / 210f) * sin(t / 62f)

/**
 * 苹果「hello」那样一笔一笔写出来的问候：「你好」→「hello」轮流写，写完停一会儿，模糊淡出再写下一个。
 * 笔画用彩虹渐变；只有欢迎页在前台时才书写和震动，翻到其它步骤立即停。
 */
@Composable
private fun HandwrittenHello(
    modifier: Modifier = Modifier,
    startDelay: Long = 0L,
    motion: Boolean = true,
    active: Boolean = true,
) {
    val haptics = rememberMochiHaptics()
    var index by remember { mutableStateOf(0) }
    val clock = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(motion, active) {
        if (!motion) {
            index = 0
            clock.snapTo(Words[0].end)
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        if (!active) {
            fade.snapTo(0f)
            return@LaunchedEffect
        }
        delay(650 + startDelay)
        while (true) {
            for (i in Words.indices) {
                val word = Words[i]
                index = i
                fade.snapTo(1f)
                clock.snapTo(0f)
                for (s in word.strokes.indices) {
                    val end = word.starts[s] + word.durations[s]
                    clock.animateTo(end, tween(word.durations[s].toInt(), easing = LinearEasing))
                    if (word.lift || s == word.strokes.lastIndex) haptics.tick()
                    if (word.lift && s < word.strokes.lastIndex) {
                        clock.animateTo(end + word.gap, tween(word.gap.toInt(), easing = LinearEasing))
                    }
                }
                // 停一会儿（回弹在这段时间里收住）
                clock.animateTo(word.end + 1500f, tween(1500, easing = LinearEasing))
                fade.animateTo(0f, tween(460, easing = FastOutSlowInEasing))
                delay(180)
            }
        }
    }
    val word = Words[index]
    val measure = remember { PathMeasure() }
    val part = remember { Path() }
    Canvas(
        modifier
            .graphicsLayer {
                mochiBlurAlpha(fade.value, 10.dp * (1f - fade.value), continuous = true)
                val b = 1f + wordBounce(clock.value - word.end)
                scaleX = b
                scaleY = b
            }
            // 手写字每帧都在长，模糊没法缓存；降采样让每帧的模糊便宜很多
            .mochiBlur(continuous = true) { 10.dp * (1f - fade.value) },
    ) {
        val c = clock.value
        val rainbow = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF4D6D),
                Color(0xFFFF9F1C),
                Color(0xFFFFE45E),
                Color(0xFF4ADE80),
                Color(0xFF38BDF8),
                Color(0xFF8B5CF6),
                Color(0xFFF472B6),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val k = minOf(size.width / word.box.width, size.height / word.box.height)
        val dx = (size.width - word.box.width * k) / 2f - word.box.left * k
        val dy = (size.height - word.box.height * k) / 2f - word.box.top * k
        translate(dx, dy) {
            withTransform({ scale(k, k, Offset.Zero) }) {
                for (i in word.strokes.indices) {
                    val len = word.written(i, c)
                    if (len <= 0f) break
                    measure.setPath(word.strokes[i], false)
                    part.reset()
                    measure.getSegment(0f, len, part, true)
                    val doneAt = word.starts[i] + word.durations[i]
                    val width = InkWidth * (1f + strokeBounce(c - doneAt, if (word.lift) 0.1f else 0.3f))
                    drawPath(part, rainbow, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
