package com.zhisheng.weather.ui

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import ui.feice.mochi.anim.MochiSprings
import ui.feice.mochi.effect.elastic.MochiGelDefaults
import ui.feice.mochi.effect.glow.MochiGlowDefaults
import ui.feice.mochi.effect.glow.MochiGlowPreference
import ui.feice.mochi.effect.glow.MochiGlowSpec
import ui.feice.mochi.ios.IosGlassStyle
import ui.feice.mochi.ios.iosMaterials
import ui.feice.mochi.navigation.MochiNavDefaults
import ui.feice.mochi.navigation.MochiNavSpec
import ui.feice.mochi.theme.MochiColorScheme
import ui.feice.mochi.theme.MochiGraphics
import ui.feice.mochi.theme.MochiGraphicsLevel
import ui.feice.mochi.theme.MochiGlass
import ui.feice.mochi.theme.MochiHdr
import ui.feice.mochi.theme.MochiMaterials
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.PaletteStyle
import ui.feice.mochi.theme.darkColorScheme
import ui.feice.mochi.theme.lightColorScheme

/**
 * 全站唯一的转场契约。所有页面级动画（MainActivity 的 overlay / 横竖屏 / 首次引导、
 * 设置页的 MochiNavHost、以及子页内部切换）都读这里的可观察状态，避免偏好只被部分页面消费。
 * 由 [ApplyMochiUiPrefs] 每次偏好变化时同步写入。
 */
internal object MochiTransitionConfig {
    var style: PageTransitionStyle by mutableStateOf(PageTransitionStyle.FADE)
    var swipeBack: Boolean by mutableStateOf(true)
    var predictiveBack: Boolean by mutableStateOf(true)
    var motionEnabled: Boolean by mutableStateOf(true)
    var backScale: String by mutableStateOf("subtle")
    /** 打开应用式转场返回时来源卡片是否用主题色高亮，默认关闭。 */
    var sourceHighlight: Boolean by mutableStateOf(false)
}

/**
 * mochi UI 个性化偏好快照。库把 MochiGraphics.level / MochiHdr / MochiGlowDefaults /
 * MochiGelDefaults 设计成全局单例且不负责持久化（库文档明确由 App 自己保存），因此这里统一
 * 从 DataStore 读取，再由 [ApplyMochiUiPrefs] 在应用根部同步到库全局状态。
 */
internal data class MochiUiPrefs(
    val graphicsLevel: String = "high",
    val hdrEnabled: Boolean = false,
    val hdrStrength: Float = 0.6f,
    val pressTinted: Boolean = false,
    val pressStrength: Float = 0.45f,
    val predictiveBack: Boolean = true,
    val pageTransition: String = "fade",
    val swipeBack: Boolean = true,
    val backScale: String = "subtle",
    val colorMode: String = "app",
    val seed: Int = 0xFF2B6A5Au.toInt(),
    val palette: String = "TonalSpot",
    val contrast: Float = 0f,
    val gel: Float = 0.6f,
    val tabLayout: String = "inline",
    val tabLabelBlur: Boolean = true,
    val tabDroplet: Boolean = true,
    val tabCollapse: Boolean = false,
    val sourceHighlight: Boolean = false,
    val glass: Boolean = true,
    val glassBlur: Boolean = true,
    val glassTint: Float = 0.5f,
    val glassBlurOnly: Boolean = false,
    val glassStyle: String = "ios26",
    val glassLight: Float = 0f,
    val glassDark: Float = 0f,
    val glassThemeAccent: Boolean = true,
    val glassTargets: Set<String> = SettingsRepository.MOCHI_GLASS_TARGETS_DEFAULT,
)

internal val MochiUiPrefs.paletteStyle: PaletteStyle
    get() = runCatching { PaletteStyle.valueOf(palette) }.getOrDefault(PaletteStyle.TonalSpot)

@Composable
internal fun rememberMochiUiPrefs(): MochiUiPrefs {
    val graphicsLevel by SettingsRepository.mochiGraphicsLevel.collectAsState(initial = "high")
    val hdrEnabled by SettingsRepository.mochiHdrEnabled.collectAsState(initial = false)
    val hdrStrength by SettingsRepository.mochiHdrStrength.collectAsState(initial = 0.6f)
    val pressTinted by SettingsRepository.mochiPressTinted.collectAsState(initial = false)
    val pressStrength by SettingsRepository.mochiPressStrength.collectAsState(initial = 0.45f)
    val predictiveBack by SettingsRepository.mochiPredictiveBack.collectAsState(initial = true)
    val pageTransition by SettingsRepository.mochiPageTransition.collectAsState(initial = "fade")
    val swipeBack by SettingsRepository.mochiSwipeBack.collectAsState(initial = true)
    val backScale by SettingsRepository.mochiBackScale.collectAsState(initial = "subtle")
    val colorMode by SettingsRepository.mochiColorMode.collectAsState(initial = "app")
    val seed by SettingsRepository.mochiSeed.collectAsState(initial = 0xFF2B6A5Au.toInt())
    val palette by SettingsRepository.mochiPalette.collectAsState(initial = "TonalSpot")
    val contrast by SettingsRepository.mochiContrast.collectAsState(initial = 0f)
    val gel by SettingsRepository.mochiGel.collectAsState(initial = 0.6f)
    val tabLayout by SettingsRepository.mochiTabLayout.collectAsState(initial = "inline")
    val tabLabelBlur by SettingsRepository.mochiTabLabelBlur.collectAsState(initial = true)
    val tabDroplet by SettingsRepository.mochiTabDroplet.collectAsState(initial = true)
    val tabCollapse by SettingsRepository.mochiTabCollapse.collectAsState(initial = false)
    val sourceHighlight by SettingsRepository.mochiSourceHighlight.collectAsState(initial = false)
    val glass by SettingsRepository.mochiGlass.collectAsState(initial = true)
    val glassBlur by SettingsRepository.mochiGlassBlur.collectAsState(initial = true)
    val glassTint by SettingsRepository.mochiGlassTint.collectAsState(initial = 0.5f)
    val glassBlurOnly by SettingsRepository.mochiGlassBlurOnly.collectAsState(initial = false)
    val glassStyle by SettingsRepository.mochiGlassStyle.collectAsState(initial = "ios26")
    val glassLight by SettingsRepository.mochiGlassLight.collectAsState(initial = 0f)
    val glassDark by SettingsRepository.mochiGlassDark.collectAsState(initial = 0f)
    val glassThemeAccent by SettingsRepository.mochiGlassThemeAccent.collectAsState(initial = true)
    val glassTargets by SettingsRepository.mochiGlassTargets.collectAsState(
        initial = SettingsRepository.MOCHI_GLASS_TARGETS_DEFAULT,
    )
    return MochiUiPrefs(
        graphicsLevel = graphicsLevel,
        hdrEnabled = hdrEnabled,
        hdrStrength = hdrStrength,
        pressTinted = pressTinted,
        pressStrength = pressStrength,
        predictiveBack = predictiveBack,
        pageTransition = pageTransition,
        swipeBack = swipeBack,
        backScale = backScale,
        colorMode = colorMode,
        seed = seed,
        palette = palette,
        contrast = contrast,
        gel = gel,
        tabLayout = tabLayout,
        tabLabelBlur = tabLabelBlur,
        tabDroplet = tabDroplet,
        tabCollapse = tabCollapse,
        sourceHighlight = sourceHighlight,
        glass = glass,
        glassBlur = glassBlur,
        glassTint = glassTint,
        glassBlurOnly = glassBlurOnly,
        glassStyle = glassStyle,
        glassLight = glassLight,
        glassDark = glassDark,
        glassThemeAccent = glassThemeAccent,
        glassTargets = glassTargets,
    )
}

/**
 * mochi 页面统一的外观入口：配色来自主题，液态玻璃材质由「液态玻璃」页的开关、通透度与使用范围生成。
 * 主题只在根部接一次，页面里不写按玻璃开关的分支。
 */
@Composable
internal fun MochiAppTheme(
    colors: MochiColorScheme,
    systemBarAppearance: Boolean = false,
    content: @Composable () -> Unit,
) {
    val prefs = rememberMochiUiPrefs()
    val materials = rememberMochiMaterials(prefs, colors.isDark)
    MochiTheme(
        colors = colors,
        systemBarAppearance = systemBarAppearance,
        materials = materials,
        content = content,
    )
}

/** 材质随玻璃设置变化时重算；供 [MochiAppTheme] 与标签栏单独覆写共用。 */
@Composable
internal fun rememberMochiMaterials(prefs: MochiUiPrefs, isDark: Boolean): MochiMaterials =
    remember(
        prefs.glass, prefs.glassBlur, prefs.glassTint, prefs.glassBlurOnly, prefs.glassStyle,
        prefs.glassLight, prefs.glassDark, prefs.glassThemeAccent, prefs.glassTargets, isDark,
    ) {
        buildMochiMaterials(prefs, isDark)
    }

/**
 * 按「液态玻璃」页的设置拼材质。关掉某项时该处退回纯色；菜单、警告框、面板、玻璃按钮按组件库的
 * 回退顺序（menu → bar、alert → menu、sheet → bar、clear → control）取上一档。
 * 「标签栏」不在这里关：它和栏共用一个材质，由设置页在 MochiTabScaffold 外层单独覆写。
 */
private fun buildMochiMaterials(prefs: MochiUiPrefs, isDark: Boolean): MochiMaterials {
    if (!prefs.glass) return MochiMaterials()
    val base = iosMaterials(
        isDark = isDark,
        glassTint = prefs.glassTint,
        glassStyle = if (prefs.glassStyle == "ios27") IosGlassStyle.Ios27 else IosGlassStyle.Ios26,
        glassLightBrightness = prefs.glassLight,
        glassDarkBrightness = prefs.glassDark,
        glass = true,
        blur = prefs.glassBlur,
        // 玻璃保持中性，选中项用主题强调色
        themeAccent = prefs.glassThemeAccent,
        blurOnly = prefs.glassBlurOnly,
    )
    val targets = prefs.glassTargets
    fun pick(on: Boolean, glass: MochiGlass?) = if (on) glass else null
    return MochiMaterials(
        bar = pick("bar" in targets, base.bar),
        scrollEdge = base.scrollEdge,
        control = pick("control" in targets, base.control ?: base.bar),
        menu = pick("menu" in targets, base.menu ?: base.bar),
        alert = pick("alert" in targets, base.alert ?: base.menu ?: base.bar),
        sheet = pick("sheet" in targets, base.sheet ?: base.bar),
        clear = pick("clear" in targets, base.clear ?: base.control ?: base.bar),
    )
}

/** 标签栏单独一份材质：关掉「标签栏」时只把 bar 置空，页面内的栏仍保留自己的玻璃。 */
internal fun MochiMaterials.withTabBar(on: Boolean): MochiMaterials =
    if (on || bar == null) this else copy(bar = null)

/** 所有 mochi 页面共用的一套配色：由「主题配色」页的 4 项设置生成，改完立即生效。 */
@Composable
internal fun rememberMochiColorScheme(followSystem: Boolean = false): MochiColorScheme {
    val prefs = rememberMochiUiPrefs()
    val appLight = LocalZhishengPalette.current.isLight
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    return remember(prefs.colorMode, prefs.seed, prefs.palette, prefs.contrast, appLight, systemDark, followSystem) {
        val dark = when {
            // 独立 Activity 的小组件/通知栏设置页没有 ZhishengWeatherTheme，
            // 默认分支会退化成 appLight 常量；这里强制只看系统深色开关。
            followSystem -> systemDark
            prefs.colorMode == "light" -> false
            prefs.colorMode == "dark" -> true
            prefs.colorMode == "system" || prefs.colorMode == "monet" -> systemDark
            else -> !appLight
        }
        val seed = if (prefs.colorMode == "monet" && Build.VERSION.SDK_INT >= 31) {
            Color(context.getColor(android.R.color.system_accent1_500))
        } else {
            Color(prefs.seed)
        }
        val contrast = prefs.contrast.toDouble()
        if (dark) darkColorScheme(seed, prefs.paletteStyle, contrast)
        else lightColorScheme(seed, prefs.paletteStyle, contrast)
    }
}

/** 只在偏好变化时写库全局状态，避免覆盖 HDR 设置页里临时打开的 HDR 预览。 */
@Composable
internal fun ApplyMochiUiPrefs(prefs: MochiUiPrefs, motionEnabled: Boolean = true) {
    LaunchedEffect(prefs.pageTransition, prefs.swipeBack, prefs.predictiveBack, prefs.backScale,
        prefs.sourceHighlight, motionEnabled) {
        MochiTransitionConfig.style = PageTransitionStyle.from(prefs.pageTransition)
        MochiTransitionConfig.swipeBack = prefs.swipeBack
        MochiTransitionConfig.predictiveBack = prefs.predictiveBack
        MochiTransitionConfig.motionEnabled = motionEnabled
        MochiTransitionConfig.backScale = prefs.backScale
        MochiTransitionConfig.sourceHighlight = prefs.sourceHighlight
    }
    LaunchedEffect(prefs.graphicsLevel) {
        MochiGraphics.level = when (prefs.graphicsLevel) {
            "mid" -> MochiGraphicsLevel.Mid
            "extra" -> MochiGraphicsLevel.Extra
            else -> MochiGraphicsLevel.High
        }
    }
    LaunchedEffect(prefs.hdrEnabled, prefs.hdrStrength) {
        MochiHdr.enabled = prefs.hdrEnabled
        MochiHdr.strength = prefs.hdrStrength
    }
    LaunchedEffect(prefs.pressTinted, prefs.pressStrength) {
        MochiGlowDefaults.preference =
            MochiGlowPreference(tinted = prefs.pressTinted, strength = prefs.pressStrength)
    }
    // 果冻感：库的 gel 默认参数全部读这个全局状态，一次赋值作用于所有组件。
    LaunchedEffect(prefs.gel) {
        MochiGelDefaults.gel = prefs.gel.coerceIn(0f, 1f)
    }
}

/** 应用转场样式同时驱动库导航的弹簧与下层页缩放；偏好变化会重建 spec。 */
@Composable
internal fun mochiNavSpecFor(prefs: MochiUiPrefs): MochiNavSpec {
    val style = PageTransitionStyle.from(prefs.pageTransition)
    val instant = tween<Float>(0)
    val spring: AnimationSpec<Float> = when {
        !MochiTransitionConfig.motionEnabled || style == PageTransitionStyle.NONE -> instant
        style == PageTransitionStyle.SLIDE -> MochiSprings.crisp(320, visibilityThreshold = 0.0005f)
        style == PageTransitionStyle.DEPTH -> MochiSprings.smooth(420, visibilityThreshold = 0.0005f)
        style == PageTransitionStyle.APP -> MochiSprings.smooth(380, extraBounce = 0.08, visibilityThreshold = 0.0005f)
        else -> MochiSprings.smooth(300, visibilityThreshold = 0.0005f)
    }
    return MochiNavDefaults.spec(
        open = spring,
        close = spring,
        cancel = spring,
        push = spring,
        openMove = spring,
        closeMove = spring,
        // 「下层页缩放」作用在来源转场的背景页（underScale），而不是预测性返回的手势缩放。
        underScale = when (prefs.backScale) {
            "none" -> 1f
            "deep" -> 0.88f
            else -> 0.95f
        },
        backScale = 0.86f,
    )
}

/** 按压高光：白光 / 主色，以及 0.05..0.8 的亮度。 */
@Composable
internal fun mochiGlowSpecFor(prefs: MochiUiPrefs): MochiGlowSpec {
    val base = if (prefs.pressTinted) MochiTheme.colorScheme.primary else Color.White
    return MochiGlowSpec(color = base.copy(alpha = prefs.pressStrength.coerceIn(0.05f, 0.8f)))
}
