package com.HelloMeet.weather.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.AccentTone
import com.HelloMeet.weather.ui.about.AboutPage
import com.HelloMeet.weather.ui.notice.NoticeDetailPage
import com.HelloMeet.weather.ui.notice.NoticePage
import com.HelloMeet.weather.data.AppIconManager
import com.HelloMeet.weather.data.AppIconStyle
import com.HelloMeet.weather.data.AtlasContent
import com.HelloMeet.weather.data.AmbienceLevel
import com.HelloMeet.weather.data.CityManagerStyle
import com.HelloMeet.weather.data.HomeBriefingStyle
import com.HelloMeet.weather.data.HomeModule
import com.HelloMeet.weather.data.InterfaceStyle
import com.HelloMeet.weather.data.LandscapeStandbyStyle
import com.HelloMeet.weather.data.LifeIndexMetric
import com.HelloMeet.weather.data.LocationSource
import com.HelloMeet.weather.data.LocationServiceLog
import com.HelloMeet.weather.data.QWeatherApi
import com.HelloMeet.weather.data.CaiyunApi
import com.HelloMeet.weather.data.CloudNotice
import com.HelloMeet.weather.data.ReleaseFeatures
import com.HelloMeet.weather.data.SecretStore
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.data.SoftGlowLevel
import com.HelloMeet.weather.data.SourcePref
import com.HelloMeet.weather.data.TelemetryMetric
import com.HelloMeet.weather.model.WeatherData
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.home.lifeIndexIcon
import com.HelloMeet.weather.ui.theme.ZhishengCyan
import com.HelloMeet.weather.ui.theme.ZhishengMint
import com.HelloMeet.weather.ui.theme.ZhishengOrange
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.isPhosphorVista
import com.HelloMeet.weather.widget.WidgetConfigActivity
import com.HelloMeet.weather.widget.WidgetStyle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import ui.feice.mochi.basic.MochiBadge
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiBottomToolbar
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.MochiBlurVisibility
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListPickerRow
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionFooter
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.list.MochiListSliderRow
import ui.feice.mochi.list.MochiSelectableRow
import ui.feice.mochi.list.MochiSwipeAction
import ui.feice.mochi.list.MochiSwipeRow
import ui.feice.mochi.list.mochiReorderable
import ui.feice.mochi.list.rememberMochiListEditState
import ui.feice.mochi.list.rememberMochiReorderState
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.MochiTabScaffold
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.navigation.rememberMochiTabScaffoldState
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.LocalMochiMaterials
import ui.feice.mochi.theme.MochiTheme

/** 首页顶栏「新设置」入口的回调，避免改动 HomeScreen / CitySharedChromeHost 的整条参数链。null = 不展示入口。 */
internal val LocalMochiSettingsEntry = staticCompositionLocalOf<(() -> Unit)?> { null }

/*
 * 用 mochi-compose 组件库重做的设置页。
 * 与旧 SettingsScreen 完全独立、功能一一对应（同一批 SettingsRepository 读写、同样的开发者模式
 * 与 ReleaseFeatures 条件、同样的定位权限流程、同样的应用图标/小组件/自动化设置入口）。
 * 二级设置页走库的 MochiNavHost 侧推 + MochiBackButton，返回键由库导航内部处理。
 */

@Composable
fun MochiSettingsScreen(
    onBack: () -> Unit,
    onLocate: () -> Unit,
    locating: Boolean,
    locateMessage: String?,
    onClearLocateMessage: () -> Unit,
    activeSource: String?,
    activeSupplementSources: List<String>,
    activeCityName: String?,
    sourceLoading: Boolean,
    initialSection: Int = 0,
    activeWeather: WeatherData? = null,
    activeCityLatitude: Double? = null,
    activeCityLongitude: Double? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mochiColors = rememberMochiColorScheme()

    // 与旧页完全相同的 38 个偏好流与缺省值。
    val tempUnit by SettingsRepository.tempUnit.collectAsState(initial = "c")
    val windUnit by SettingsRepository.windUnit.collectAsState(initial = "kmh")
    val pressureUnit by SettingsRepository.pressureUnit.collectAsState(initial = "hpa")
    val source by SettingsRepository.sourcePref.collectAsState(initial = SourcePref.AUTO)
    val developerMode by SettingsRepository.developerMode.collectAsState(initial = false)
    val ambience by SettingsRepository.ambience.collectAsState(initial = AmbienceLevel.INTENSE)
    val livingSky by SettingsRepository.livingSky.collectAsState(initial = true)
    val scanlines by SettingsRepository.scanlines.collectAsState(initial = true)
    val softGlow by SettingsRepository.softGlow.collectAsState(initial = false)
    val softGlowLevel by SettingsRepository.softGlowLevel.collectAsState(initial = SoftGlowLevel.FLOWING)
    val locationEnabled by SettingsRepository.locationEnabled.collectAsState(initial = false)
    val preciseLocationEnabled by SettingsRepository.preciseLocationEnabled.collectAsState(initial = false)
    val preciseLocationPermissionAsked by SettingsRepository.preciseLocationPermissionAsked.collectAsState(initial = false)
    val showIndices by SettingsRepository.showIndices.collectAsState(initial = true)
    val showTelemetry by SettingsRepository.showTelemetry.collectAsState(initial = true)
    val keepScreenOn by SettingsRepository.keepScreenOn.collectAsState(initial = false)
    val standbyBurnInProtection by SettingsRepository.standbyBurnInProtection.collectAsState(initial = true)
    val landscapeStandby by SettingsRepository.landscapeStandby.collectAsState(initial = true)
    val landscapeStandbyStyle by SettingsRepository.landscapeStandbyStyle.collectAsState(
        initial = LandscapeStandbyStyle.WEATHER_CORE,
    )
    val cityManagerStyle by SettingsRepository.cityManagerStyle.collectAsState(
        initial = CityManagerStyle.SIDEBAR,
    )
    val telemetryMetrics by SettingsRepository.telemetryMetrics.collectAsState(initial = TelemetryMetric.defaultSelection)
    val lifeIndexMetrics by SettingsRepository.lifeIndexMetrics.collectAsState(initial = LifeIndexMetric.defaultSelection)
    val interfaceStyle by SettingsRepository.interfaceStyle.collectAsState(initial = InterfaceStyle.PHOSPHOR_VISTA)
    val accentTone by SettingsRepository.accentTone.collectAsState(initial = AccentTone.STANDARD)
    val appIconStyle by SettingsRepository.appIconStyle.collectAsState(initial = AppIconStyle.DEFAULT)
    val homeBriefingStyle by SettingsRepository.homeBriefingStyle.collectAsState(initial = HomeBriefingStyle.TIPS)

    var permDenied by remember { mutableStateOf(false) }
    var showIconDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdateCheck by rememberSaveable { mutableStateOf(false) }
    var showCurrentNotes by rememberSaveable { mutableStateOf(false) }
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    val toast = LocalMochiToast.current
    // 四段设置页改用库的底部悬浮标签栏；状态自带跨重建保存。
    // 「关于」从独立二级页改为第 4 个 TAB（原先顶栏 info 按钮也改为切到该标签）。
    val tabState = rememberMochiTabScaffoldState(initialSection.coerceIn(0, 3))
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
            MochiTabItem("关于") { tint ->
                PhosphorIcon(R.drawable.ph_info, null, Modifier.size(22.dp), tint)
            },
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch {
            if (preciseLocationEnabled) SettingsRepository.setPreciseLocationPermissionAsked()
            if (LocationSource.hasPermission(context)) {
                permDenied = false
                onLocate()
            } else permDenied = true
        }
    }

    val nav = rememberMochiNavigator("settings.main")
    // 公告列表 → 详情复用同一个导航器，选中的公告跟着路由一起传。
    var openedNotice by remember { mutableStateOf<CloudNotice?>(null) }
    val uiPrefs = rememberMochiUiPrefs()
    val style = PageTransitionStyle.from(uiPrefs.pageTransition)
    val transitionsEnabled = appTransitionsEnabled(style)
    // 「打开应用式」：push 时把来源行登记的 key 交给库，页面从那一行长成整屏，返回缩回去。
    // 没有登记来源行的页面（关于 / 公告 / 详情等）由 AppTransitions 兜底成 CORNER_KEY 走角落
    // 容器变换，不再从左右两侧进入 / 退出；其余样式走库的侧推。
    // 偏好变化会让这个 lambda 重新组合，下一次 push 立即采用新配置。
    val openSettingsPage: (String) -> Unit = { route ->
        nav.push(route, from = AppTransitions.settingsPushFrom(style, route))
    }
    // 在「应用转场」页改完设置后，已打开的二级页返回形态也立刻跟随：
    // 返回按钮、系统返回手势、右滑返回都按新设置执行，不会沿用进入时的旧样式。
    LaunchedEffect(style) {
        if (nav.canPop) {
            nav.setTopSource(AppTransitions.settingsPushFrom(style, nav.current))
        }
    }
    val tabBehavior = mochiTabBehaviorFor(uiPrefs)
    // 外观在根部接一次：iOS 外观开关只影响这里的材质与配色，页面内不写分支。
    MochiAppTheme(colors = mochiColors) {
        ProvideMochiOverscroll {
            // 转场与侧推配置来自「外观与设备 → 应用转场与侧推」，同步作用于库导航。
            MochiNavHost(
                nav,
                spec = mochiNavSpecFor(uiPrefs),
                // 「无动画」时预测性返回与右滑一起关掉，避免手指跟手拖出动画。
                predictiveBack = transitionsEnabled && uiPrefs.predictiveBack && uiPrefs.swipeBack,
                swipeBackEnabled = transitionsEnabled && uiPrefs.swipeBack,
            ) { route ->
                when (route) {
                    "settings.main" -> {
                        // 标签栏和栏共用 bar 材质：外层只给标签栏用它的那份，正文里还原成页面的材质。
                        val materials = rememberMochiMaterials(uiPrefs, mochiColors.isDark)
                        CompositionLocalProvider(
                            LocalMochiMaterials provides materials.tabBar,
                        ) {
                            MochiTabScaffold(
                                items = tabs,
                                state = tabState,
                                behavior = tabBehavior,
                                collapseOnScroll = uiPrefs.tabCollapse,
                            ) { index ->
                                CompositionLocalProvider(LocalMochiMaterials provides materials.page) {
                                    // 「关于」是第 4 个标签：AboutPage 自带库顶栏与 MochiBackButton，
                                    // 直接作为这一页的内容，不再套外层「设置」标题栏（避免两层栏）。
                                    if (index == 3) {
                                        AboutPage(
                                            onBack = onBack,
                                            onOpenNotices = { openSettingsPage("settings.notice") },
                                            onCheckUpdate = { showUpdateCheck = true },
                                            onOpenCurrentNotes = { showCurrentNotes = true },
                                            onOpenChangelog = { showChangelog = true },
                                        )
                                    } else MochiLazyCompactTitleScaffold(
                            title = "设置",
                            subtitle = when (index) {
                                0 -> "数据与位置"
                                1 -> "首页内容"
                                else -> "外观与设备"
                            },
                            navigationIcon = { MochiBackButton(onClick = onBack) },
                            actions = {
                                MochiBarIconButton(onClick = { tabState.select(3) }) {
                                    PhosphorIcon(R.drawable.ph_info, null, Modifier.size(22.dp), MochiTheme.colorScheme.onSurface)
                                }
                            },
                        ) {
                            when (index) {
                            0 -> mochiDataSection(
                                context = context,
                                source = source, activeSource = activeSource,
                                activeSupplementSources = activeSupplementSources,
                                activeCityName = activeCityName, sourceLoading = sourceLoading,
                                locationEnabled = locationEnabled,
                                preciseLocationEnabled = preciseLocationEnabled,
                                preciseLocationPermissionAsked = preciseLocationPermissionAsked,
                                locating = locating, locateMessage = locateMessage,
                                onClearLocateMessage = onClearLocateMessage,
                                activeCityLatitude = activeCityLatitude,
                                activeCityLongitude = activeCityLongitude,
                                developerMode = developerMode, permDenied = permDenied,
                                onLocate = onLocate,
                                launchPermissions = { precise ->
                                    permDenied = false
                                    permLauncher.launch(LocationSource.requestedPermissions(precise))
                                },
                                openAppSettings = { mochiOpenAppSettings(context) },
                                toggleLocation = { enabled ->
                                    scope.launch {
                                        SettingsRepository.setLocationEnabled(enabled)
                                        if (!enabled) onClearLocateMessage()
                                    }
                                },
                                togglePrecise = { enabled ->
                                    onClearLocateMessage(); permDenied = false
                                    scope.launch { SettingsRepository.setPreciseLocationEnabled(enabled) }
                                },
                                toggleDeveloper = { enabled ->
                                    scope.launch { SettingsRepository.setDeveloperMode(enabled) }
                                },
                                onWeatherSource = { openSettingsPage("settings.weatherSource") },
                                onLocationEngine = { openSettingsPage("settings.locationEngine") },
                                onProviders = { openSettingsPage("settings.providers") },
                            )
                            1 -> mochiHomeSection(
                                homeBriefingStyle = homeBriefingStyle, tempUnit = tempUnit,
                                windUnit = windUnit, pressureUnit = pressureUnit,
                                showTelemetry = showTelemetry, showIndices = showIndices,
                                developerMode = developerMode, telemetryMetrics = telemetryMetrics,
                                lifeIndexMetrics = lifeIndexMetrics,
                                cityManagerStyle = cityManagerStyle,
                                onBriefing = { scope.launch { SettingsRepository.setHomeBriefingStyle(HomeBriefingStyle.from(it)) } },
                                onTempUnit = { scope.launch { SettingsRepository.setTempUnit(it) } },
                                onWindUnit = { scope.launch { SettingsRepository.setWindUnit(it) } },
                                onPressureUnit = { scope.launch { SettingsRepository.setPressureUnit(it) } },
                                onCityManagerStyle = { scope.launch { SettingsRepository.setCityManagerStyle(CityManagerStyle.from(it)) } },
                                onPush = openSettingsPage,
                            )
                            else -> mochiAppearanceSection(
                                interfaceStyle = interfaceStyle,
                                accentTone = accentTone, softGlow = softGlow,
                                softGlowLevel = softGlowLevel, ambience = ambience,
                                livingSky = livingSky, scanlines = scanlines,
                                landscapeStandby = landscapeStandby,
                                standbyBurnInProtection = standbyBurnInProtection,
                                landscapeStandbyStyle = landscapeStandbyStyle,
                                appIconStyle = appIconStyle, keepScreenOn = keepScreenOn,
                                developerMode = developerMode,
                                graphicsLevel = uiPrefs.graphicsLevel,
                                onInterfaceStyle = { scope.launch { SettingsRepository.setInterfaceStyle(InterfaceStyle.from(it)) } },
                                onAccentTone = { scope.launch { SettingsRepository.setAccentTone(AccentTone.from(it)) } },
                                onSoftGlow = { scope.launch { SettingsRepository.setSoftGlow(it) } },
                                onSoftGlowLevel = { scope.launch { SettingsRepository.setSoftGlowLevel(SoftGlowLevel.from(it)) } },
                                onAmbience = { scope.launch { SettingsRepository.setAmbience(AmbienceLevel.from(it)) } },
                                onLivingSky = { scope.launch { SettingsRepository.setLivingSky(it) } },
                                onScanlines = { scope.launch { SettingsRepository.setScanlines(it) } },
                                onLandscapeStandby = { scope.launch { SettingsRepository.setLandscapeStandby(it) } },
                                onBurnIn = { scope.launch { SettingsRepository.setStandbyBurnInProtection(it) } },
                                onLandscapeStyle = { scope.launch { SettingsRepository.setLandscapeStandbyStyle(LandscapeStandbyStyle.from(it)) } },
                                onKeepScreenOn = { scope.launch { SettingsRepository.setKeepScreenOn(it) } },
                                onAtmosphereLab = { openSettingsPage("settings.atmosphere") },
                                onGraphicsLevel = { scope.launch { SettingsRepository.setMochiGraphicsLevel(it) } },
                                onGel = { scope.launch { SettingsRepository.setMochiGel(it) } },
                                gel = uiPrefs.gel,
                                onAppIcon = { showIconDialog = true },
                                onPush = openSettingsPage,
                                glassOn = uiPrefs.glass,
                            )
                            }
                        }
                    }
                    }
                        }
                    }
                    "settings.notice" -> NoticePage(
                        onBack = { nav.pop() },
                        onOpen = { notice ->
                            openedNotice = notice
                            openSettingsPage("settings.noticeDetail")
                        },
                    )
                    "settings.noticeDetail" -> NoticeDetailPage(
                        notice = openedNotice,
                        onBack = { nav.pop() },
                    )
                    // 天气源、服务商、氛围实验室也从设置进，走同一套库导航转场与侧滑返回
                    "settings.weatherSource" -> MochiWeatherSourceScreen(
                        weather = activeWeather,
                        onManageProviders = { openSettingsPage("settings.providers") },
                        onBack = { nav.pop() },
                    )
                    "settings.locationEngine" -> MochiLocationEngineScreen(
                        onManageProviders = { openSettingsPage("settings.providers") },
                        onOpenLog = { openSettingsPage("settings.locationLog") },
                        onBack = { nav.pop() },
                    )
                    "settings.locationLog" -> MochiLocationLogScreen(onBack = { nav.pop() })
                    "settings.providers" -> MochiProviderAccessScreen(onBack = { nav.pop() })
                    "settings.atmosphere" -> MochiAtmosphereLabScreen(
                        initialLevel = ambience,
                        onBack = { nav.pop() },
                    )
                    "settings.hdr" -> MochiHdrSettingsPage(uiPrefs)
                    "settings.glass" -> MochiGlassSettingsPage(uiPrefs)
                    "settings.pressGlow" -> MochiPressGlowSettingsPage(uiPrefs)
                    "settings.transition" -> MochiTransitionSettingsPage(uiPrefs)
                    "settings.theme" -> MochiThemeSettingsPage(uiPrefs)
                    "settings.tabbar" -> MochiTabBarSettingsPage(uiPrefs)
                    "settings.graphics" -> MochiGraphicsSettingsPage(uiPrefs)
                    "settings.automation" -> MochiAutomationSettingsPage()
                    "settings.modules" -> MochiModuleLayoutPage()
                    "settings.telemetry" -> MochiSecondaryPage("遥测项目", "${telemetryMetrics.size} / ${TelemetryMetric.entries.size} 项显示在首页") {
                        item {
                            MochiListSection(header = "逐项开关") {
                                TelemetryMetric.entries.forEach { metric ->
                                    MochiListRow(
                                        title = metric.cn,
                                        subtitle = metric.en,
                                        leading = { PhosphorIcon(telemetryMetricIcon(metric), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                                        trailing = {
                                            MochiSettingSwitch(metric in telemetryMetrics) { checked ->
                                                val next = telemetryMetrics.toMutableSet().apply {
                                                    if (checked) add(metric) else remove(metric)
                                                }
                                                scope.launch { SettingsRepository.setTelemetryMetrics(next) }
                                            }
                                        },
                                    )
                                }
                                MochiListRow(
                                    title = "全选",
                                    enabled = telemetryMetrics.size != TelemetryMetric.entries.size,
                                    onClick = {
                                        scope.launch { SettingsRepository.setTelemetryMetrics(TelemetryMetric.defaultSelection) }
                                    },
                                )
                                MochiListRow(
                                    title = "清空",
                                    enabled = telemetryMetrics.isNotEmpty(),
                                    onClick = {
                                        scope.launch { SettingsRepository.setTelemetryMetrics(emptySet()) }
                                    },
                                    destructive = true,
                                )
                            }
                        }
                    }
                    "settings.lifeIndex" -> MochiSecondaryPage("生活指数项目", "${lifeIndexMetrics.size} / ${LifeIndexMetric.entries.size} 项显示在首页") {
                        item {
                            MochiListSection(header = "逐项开关") {
                                LifeIndexMetric.entries.forEach { metric ->
                                    MochiListRow(
                                        title = metric.cn,
                                        subtitle = metric.en,
                                        leading = { PhosphorIcon(lifeIndexIcon(metric.en), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                                        trailing = {
                                            MochiSettingSwitch(metric in lifeIndexMetrics) { checked ->
                                                val next = lifeIndexMetrics.toMutableSet().apply {
                                                    if (checked) add(metric) else remove(metric)
                                                }
                                                scope.launch { SettingsRepository.setLifeIndexMetrics(next) }
                                            }
                                        },
                                    )
                                }
                                MochiListRow(
                                    title = "全选",
                                    enabled = lifeIndexMetrics.size != LifeIndexMetric.entries.size,
                                    onClick = {
                                        scope.launch { SettingsRepository.setLifeIndexMetrics(LifeIndexMetric.defaultSelection) }
                                    },
                                )
                                MochiListRow(
                                    title = "清空",
                                    enabled = lifeIndexMetrics.isNotEmpty(),
                                    onClick = {
                                        scope.launch { SettingsRepository.setLifeIndexMetrics(emptySet()) }
                                    },
                                    destructive = true,
                                )
                            }
                        }
                    }
                    "settings.widgets" -> MochiSecondaryPage("小组件与通知栏", "选择要预览和配置的样式") {
                        item {
                            MochiListSection(header = "样式", footer = "配置会写入桌面小组件。") {
                                // 三个入口是独立 Activity，从被点行展开 / 缩回，遵循「应用转场」设置：
                                // 打开应用式走 clipReveal，无动画关掉系统左右侧滑，其余样式保留系统转场。
                                MochiExternalLaunchRow("澄空小组件", style, transitionsEnabled) {
                                    Intent(context, WidgetConfigActivity::class.java)
                                        .putExtra("widget_style", WidgetStyle.VISTA.key)
                                }
                                MochiExternalLaunchRow("经典小组件", style, transitionsEnabled) {
                                    Intent(context, WidgetConfigActivity::class.java)
                                        .putExtra("widget_style", WidgetStyle.CLASSIC.key)
                                }
                                MochiExternalLaunchRow("通知栏天气", style, transitionsEnabled) {
                                    Intent(context, WidgetConfigActivity::class.java)
                                        .putExtra("notification_settings", true)
                                }
                            }
                        }
                    }
                    // 应用图标改为对话框选择：从主设置行直接弹库 MochiDialog，不再单独推一页。
                    "settings.icons" -> MochiSecondaryPage("应用图标", "切换后桌面图标可能需要重启启动器") {
                        item {
                            MochiListSection {
                                MochiListRow(
                                    title = "选择应用图标",
                                    value = appIconStyle.cn,
                                    chevron = true,
                                    onClick = { showIconDialog = true },
                                )
                            }
                        }
                    }
                    else -> MochiSecondaryPage("设置") {
                        item { MochiListRow(title = "返回", onClick = { nav.pop() }) }
                    }
                }
            }
        }
    }
    MochiAppTheme(colors = mochiColors) {
        MochiChoiceDialog(
            visible = showIconDialog,
            title = "应用图标",
            options = AppIconStyle.entries.map { it.cn to it.key },
            selected = appIconStyle.key,
            footer = "切换后重启启动器生效",
            onSelect = { value ->
                val selected = AppIconStyle.from(value)
                // 先弹提示再切别名：setComponentEnabledSetting 可能触发系统重建 Activity，
                // 提示会被连带销毁；先给 1.5s 让「请重启生效」完整显示，再真正切换桌面图标。
                toast?.show(
                    "应用图标已切换",
                    subtitle = "请重启生效",
                    icon = { tint ->
                        PhosphorIcon(R.drawable.ph_arrow_clockwise, null, Modifier.size(20.dp), tint)
                    },
                    durationMillis = 3500,
                )
                scope.launch {
                    delay(1500)
                    if (AppIconManager.apply(context, selected)) {
                        SettingsRepository.setAppIconStyle(selected)
                    }
                }
            },
            onDismiss = { showIconDialog = false },
        )
        // 关于页的「检查更新」「当前版本更新」「历史更新日志」：三个独立弹层，互不复用。
        if (showUpdateCheck) {
            AppUpdateDialog(onClose = { showUpdateCheck = false })
        }
        if (showCurrentNotes) {
            WhatsNewDialog(onClose = { showCurrentNotes = false })
        }
        if (showChangelog) {
            ChangelogDialog(onClose = { showChangelog = false })
        }
    }
}

/** 库的 onCheckedChange 是必需的第二个位置参数，trailing lambda 会误落到最后的 hapticProfile。 */
@Composable
private fun MochiSettingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    MochiSwitch(checked = checked, onCheckedChange = onCheckedChange)
}

/** 每个设置入口一个稳定的来源 key：push(route, from = key) 时库才知道从哪张卡片展开。 */
internal fun mochiSettingsSourceKey(route: String): String = "settings.source.$route"

/**
 * 打开独立 Activity 的入口行（小组件 / 通知栏设置）。库导航的来源 key 只作用于 MochiNavHost 内部，
 * 跨 Activity 用不了，因此这里记录点击行的屏幕矩形，交给 [AppTransitions.activityEnterOptions]
 * 做「从卡片展开」的 clipReveal；无动画时关掉系统默认的左右侧滑。
 */
@Composable
private fun MochiExternalLaunchRow(
    title: String,
    style: PageTransitionStyle,
    animated: Boolean,
    intent: () -> Intent,
) {
    val context = LocalContext.current
    val source = LocalView.current
    var bounds by remember { mutableStateOf<IntArray?>(null) }
    MochiListRow(
        title = title,
        chevron = true,
        modifier = Modifier.onGloballyPositioned { coords ->
            val p = coords.positionInRoot()
            bounds = intArrayOf(
                p.x.roundToInt(), p.y.roundToInt(),
                (p.x + coords.size.width).roundToInt(),
                (p.y + coords.size.height).roundToInt(),
            )
        },
        onClick = { AppTransitions.launchActivity(context, intent(), source, bounds, style, animated) },
    )
}

@Composable
private fun MochiSettingsNavRow(
    route: String,
    title: String,
    onPush: (String) -> Unit,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    chevron: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    titleTrailing: @Composable (() -> Unit)? = null,
) {
    // 默认用卡片真实底色，返回落地时不会突然被主题色铺一下；开关打开后才用主题色高亮。
    val sourceColor = if (MochiTransitionConfig.sourceHighlight) {
        MochiTheme.colorScheme.primaryContainer
    } else {
        MochiDefaults.groupedContainer()
    }
    MochiListRow(
        title = title,
        subtitle = subtitle,
        value = value,
        enabled = enabled,
        chevron = chevron,
        trailing = trailing,
        titleTrailing = titleTrailing,
        modifier = Modifier.mochiNavSource(
            mochiSettingsSourceKey(route),
            MochiListDefaults.SectionCorner,
            sourceColor,
        ),
        onClick = { onPush(route) },
    )
}

/** 二级设置页统一外壳：库顶栏 + 侧推返回按钮 + 懒加载列表。 */
@Composable
private fun MochiSecondaryPage(
    title: String,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit) = { MochiBackButton() },
    showSave: Boolean = true,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    MochiSettingsScaffold(
        title = title,
        subtitle = subtitle,
        showSave = showSave,
        actions = actions,
        overlay = overlay,
        content = content,
    )
}

/**
 * 模块布局：把原先分散在「首页内容 → 模块展示」的显示开关与「调整主页模块顺序」合并到一页。
 *
 * 排序照 demo「列表拖拽优化」用库 [rememberMochiReorderState] + [mochiReorderable]：
 * 长按浮起、邻行让位、松手落进空位，拖动期间关掉卡片分隔线；
 * 批量显示/隐藏照 demo「列表编辑与索引」用 [rememberMochiListEditState] + [MochiSelectableRow]。
 * 逐时预报与五天天气没有开关，固定显示；台风未开放时不出现在列表里，但保留在偏好中。
 */
@Composable
private fun MochiModuleLayoutPage() {
    val scope = rememberCoroutineScope()
    // 顺序先改本地（和 demo 一样同步换布局）再异步落盘：DataStore 回流要晚一两帧，
    // 晚到的话落位动画会先缩回旧位置、再被新布局带走，看起来就是「闪一下」。
    val persistedOrder by SettingsRepository.moduleOrder.collectAsState(initial = HomeModule.defaultOrder)
    var order by remember { mutableStateOf(persistedOrder) }
    LaunchedEffect(persistedOrder) {
        if (persistedOrder != order) order = persistedOrder
    }
    val showSpacetime by SettingsRepository.showSpacetime.collectAsState(initial = true)
    val showPrecip by SettingsRepository.showPrecip.collectAsState(initial = true)
    val showTelemetry by SettingsRepository.showTelemetry.collectAsState(initial = true)
    val showAqi by SettingsRepository.showAqi.collectAsState(initial = true)
    val showIndices by SettingsRepository.showIndices.collectAsState(initial = true)
    val showYesterday by SettingsRepository.showYesterday.collectAsState(initial = true)
    val showTyphoon by SettingsRepository.showTyphoon.collectAsState(initial = true)
    val showFeatures by SettingsRepository.showFeatures.collectAsState(initial = true)
    val showOutfit by SettingsRepository.showOutfit.collectAsState(initial = true)
    val showSkyPhotography by SettingsRepository.showSkyPhotography.collectAsState(initial = true)
    val showCoastWeather by SettingsRepository.showCoastWeather.collectAsState(initial = true)
    val edit = rememberMochiListEditState()

    // 气象视界内容（天空与星空 / 海岸）的顺序：同样先改本地再落盘，落位动画才不闪。
    val persistedAtlasOrder by SettingsRepository.atlasContentOrder.collectAsState(initial = AtlasContent.defaultOrder)
    var atlasOrder by remember { mutableStateOf(persistedAtlasOrder) }
    LaunchedEffect(persistedAtlasOrder) {
        if (persistedAtlasOrder != atlasOrder) atlasOrder = persistedAtlasOrder
    }
    val atlasRows = remember(atlasOrder) {
        atlasOrder.filter { it != AtlasContent.COAST || ReleaseFeatures.coastalWeather }
    }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    val rows = remember(order) { order.filter { ReleaseFeatures.typhoon || it != HomeModule.TYPHOON } }
    val toggleKeys = remember(rows) {
        rows.filter { it != HomeModule.HOURLY && it != HomeModule.DAILY }.map { it.key }
    }

    fun visible(module: HomeModule): Boolean = when (module) {
        HomeModule.HOURLY, HomeModule.DAILY -> true
        HomeModule.PRECIP -> showPrecip
        HomeModule.SPACETIME -> showSpacetime
        HomeModule.TELEMETRY -> showTelemetry
        HomeModule.AQI -> showAqi
        HomeModule.OUTFIT -> showOutfit
        HomeModule.INDICES -> showIndices
        HomeModule.YESTERDAY -> showYesterday
        HomeModule.TYPHOON -> showTyphoon
        HomeModule.FEATURES -> showFeatures
    }

    suspend fun apply(module: HomeModule, value: Boolean) {
        when (module) {
            HomeModule.PRECIP -> SettingsRepository.setShowPrecip(value)
            HomeModule.SPACETIME -> SettingsRepository.setShowSpacetime(value)
            HomeModule.TELEMETRY -> SettingsRepository.setShowTelemetry(value)
            HomeModule.AQI -> SettingsRepository.setShowAqi(value)
            HomeModule.OUTFIT -> SettingsRepository.setShowOutfit(value)
            HomeModule.INDICES -> SettingsRepository.setShowIndices(value)
            HomeModule.YESTERDAY -> SettingsRepository.setShowYesterday(value)
            HomeModule.TYPHOON -> SettingsRepository.setShowTyphoon(value)
            HomeModule.FEATURES -> SettingsRepository.setShowFeatures(value)
            HomeModule.HOURLY, HomeModule.DAILY -> Unit
        }
    }

    fun applySelected(value: Boolean) {
        val chosen = edit.selected.mapNotNull { key -> HomeModule.entries.firstOrNull { it.key == key } }
        edit.clear()
        edit.editing = false
        if (chosen.isEmpty()) return
        scope.launch { chosen.forEach { apply(it, value) } }
    }

    // 浮起的行保持卡片原宽（liftScale = 1）：放大 1.03 会横向溢出卡片，看着就像「没回到卡片大小」。
    val reorder = rememberMochiReorderState(liftScale = 1f) { from, to ->
        val fromModule = rows.getOrNull(from)
        val toModule = rows.getOrNull(to)
        if (fromModule != null && toModule != null && from != to) {
            val fromIndex = order.indexOf(fromModule)
            val toIndex = order.indexOf(toModule)
            if (fromIndex >= 0 && toIndex >= 0) {
                val next = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                order = next
                scope.launch { SettingsRepository.setModuleOrder(next) }
            }
        }
    }

    fun moveToTop(module: HomeModule) {
        val next = order.toMutableList()
        if (next.remove(module)) {
            next.add(0, module)
            order = next
            scope.launch { SettingsRepository.setModuleOrder(next) }
        }
    }

    // 气象视界内容也支持长按拖动排序（不需要进批量模式）。
    val atlasReorder = rememberMochiReorderState(liftScale = 1f) { from, to ->
        val fromItem = atlasRows.getOrNull(from)
        val toItem = atlasRows.getOrNull(to)
        if (fromItem != null && toItem != null && from != to) {
            val fromIndex = atlasOrder.indexOf(fromItem)
            val toIndex = atlasOrder.indexOf(toItem)
            if (fromIndex >= 0 && toIndex >= 0) {
                val next = atlasOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                atlasOrder = next
                scope.launch { SettingsRepository.setAtlasContentOrder(next) }
            }
        }
    }

    fun resetOrders() {
        order = HomeModule.defaultOrder
        atlasOrder = AtlasContent.defaultOrder
        scope.launch {
            SettingsRepository.setModuleOrder(HomeModule.defaultOrder)
            SettingsRepository.setAtlasContentOrder(AtlasContent.defaultOrder)
        }
    }

    val listColors = MochiListDefaults.colors()
    // 关闭态的灰：只压文字与图标色，不铺不透明底色（会盖掉浮起行的圆角，也会透出滑动操作胶囊）。
    val hiddenRowColors = MochiListDefaults.colors(
        container = listColors.container,
        content = MochiTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        secondary = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        accent = MochiTheme.colorScheme.primary.copy(alpha = 0.38f),
    )
    val dimIconTint = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    MochiSecondaryPage(
        title = "模块布局",
        subtitle = "顺序与显示开关",
        // 顶栏右侧跟着编辑态在「编辑」/「保存」之间切换，和其他二级设置页共用同一个共享顶栏位置。
        showSave = false,
        actions = {
            MochiBarButton(
                if (edit.editing) "保存" else "编辑",
                onClick = {
                    if (edit.editing) edit.clear()
                    edit.editing = !edit.editing
                },
                navId = MochiSettingsSaveNavId,
            )
        },
        // 编辑态底部栏照 demo「列表编辑与索引」：渐隐玻璃条 + 全选 + 已选计数 + 两个操作按钮。
        overlay = {
            Box(Modifier.fillMaxSize()) {
                MochiBlurVisibility(
                    visible = edit.editing,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    MochiBottomToolbar {
                        MochiBarButton(
                            if (edit.selected.size == toggleKeys.size) "全不选" else "全选",
                            onClick = {
                                if (edit.selected.size == toggleKeys.size) edit.clear() else edit.selectAll(toggleKeys)
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        MochiBlurText(
                            "已选 ${edit.selected.size} 项",
                            style = MochiTheme.textStyles.subhead,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        MochiBarIconButton(
                            onClick = { applySelected(false) },
                            enabled = edit.selected.isNotEmpty(),
                        ) {
                            PhosphorIcon(
                                R.drawable.ph_x, "全部关闭", Modifier.size(20.dp),
                                MochiTheme.colorScheme.onSurface,
                            )
                        }
                        MochiBarIconButton(
                            onClick = { applySelected(true) },
                            enabled = edit.selected.isNotEmpty(),
                        ) {
                            PhosphorIcon(
                                R.drawable.ph_check, "全部开启", Modifier.size(20.dp),
                                MochiTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                // 恢复默认排序先二次确认，避免误触把排好的顺序清掉。
                MochiDialog(
                    visible = showResetConfirm,
                    onDismissRequest = { showResetConfirm = false },
                    title = "恢复默认排序？",
                    message = "首页模块与气象视界内容都会回到默认顺序，显示开关不受影响。",
                    actions = persistentListOf(
                        MochiDialogAction("取消", MochiDialogActionRole.Cancel),
                        MochiDialogAction("恢复默认", MochiDialogActionRole.Destructive) { resetOrders() },
                    ),
                )
            }
        },
    ) {
        item {
            MochiBlurText(
                if (edit.editing) "勾选模块后批量显示或隐藏" else "长按拖动排序，左滑显示/隐藏，右滑置顶",
                style = MochiTheme.textStyles.subhead,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            // 库的 destructive 只染主标题，这一行把副标题与图标也统一成警示色。
            val resetColors = MochiListDefaults.colors(
                container = listColors.container,
                content = listColors.content,
                secondary = listColors.destructive,
                divider = listColors.divider,
                highlight = listColors.highlight,
                accent = listColors.destructive,
                destructive = listColors.destructive,
                chevron = listColors.chevron,
            )
            MochiListSection(colors = resetColors) {
                MochiListRow(
                    title = "恢复默认排序",
                    subtitle = "首页模块与气象视界内容回到默认顺序",
                    leading = {
                        PhosphorIcon(
                            R.drawable.ph_arrow_clockwise, null, Modifier.size(22.dp),
                            resetColors.destructive,
                        )
                    },
                    colors = resetColors,
                    enabled = order != HomeModule.defaultOrder || atlasOrder != AtlasContent.defaultOrder,
                    destructive = true,
                    onClick = { showResetConfirm = true },
                )
            }
        }
        item {
            MochiListSectionHeader("首页模块")
            // 拖动期间关掉分隔线，免得横在浮起的行上。
            MochiListCard(colors = listColors, showDividers = !reorder.isDragging) {
                rows.forEachIndexed { index, module ->
                    val toggleable = module != HomeModule.HOURLY && module != HomeModule.DAILY
                    val shown = visible(module)
                    val leading: @Composable () -> Unit = {
                        PhosphorIcon(
                            homeModuleIcon(module), null, Modifier.size(22.dp),
                            if (shown) mochiLeadingIconTint() else dimIconTint,
                        )
                    }
                    when {
                        edit.editing && toggleable -> MochiSelectableRow(
                            state = edit,
                            key = module.key,
                            title = module.cn,
                            subtitle = homeModuleSubtitle(module),
                            value = if (shown) "显示" else "已隐藏",
                            leading = leading,
                            // 编辑态里已关闭的模块同样压灰，和正常态一致。
                            colors = if (shown) listColors else hiddenRowColors,
                        )
                        edit.editing -> MochiListRow(
                            title = module.cn,
                            subtitle = homeModuleSubtitle(module),
                            leading = leading,
                        )
                        else -> key(module.key) {
                            MochiSwipeRow(
                                // 左滑露出「隐藏/显示」，右滑露出「置顶」。
                                actions = if (toggleable) listOf(
                                    MochiSwipeAction(
                                        label = if (shown) "隐藏" else "显示",
                                        color = if (shown) MochiTheme.colorScheme.tertiary else MochiTheme.colorScheme.primary,
                                        contentColor = if (shown) MochiTheme.colorScheme.onTertiary else MochiTheme.colorScheme.onPrimary,
                                        onClick = { scope.launch { apply(module, !shown) } },
                                    ),
                                ) else emptyList(),
                                leadingActions = listOf(
                                    MochiSwipeAction(
                                        label = "置顶",
                                        color = MochiTheme.colorScheme.secondary,
                                        contentColor = MochiTheme.colorScheme.onSecondary,
                                        onClick = { moveToTop(module) },
                                    ),
                                ),
                                modifier = Modifier.mochiReorderable(reorder, module.key, index, listColors.container),
                            ) {
                                MochiListRow(
                                    title = module.cn,
                                    subtitle = homeModuleSubtitle(module),
                                    leading = leading,
                                    // 隐藏的模块整行压灰：只换文字/图标色，不铺不透明底色，
                                    // 否则拖动浮起时会盖掉库里画的 12dp 圆角，行变成方角白块。
                                    colors = if (shown) listColors else hiddenRowColors,
                                    trailing = if (toggleable) {
                                        {
                                            Box(Modifier.alpha(if (shown) 1f else 0.5f)) {
                                                MochiSettingSwitch(shown) { checked ->
                                                    scope.launch { apply(module, checked) }
                                                }
                                            }
                                        }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
            MochiListSectionFooter("顺序与显示状态立即保存；逐时预报与五天天气固定显示。")
        }
        // 气象视界整块关掉时，它内部的小节开关也没有意义，一并不展示。
        if (showSpacetime) {
            item {
                MochiListSectionHeader("气象视界内容")
                // 这两行常驻可拖动（不需要进批量模式），拖动期间同样关掉分隔线。
                MochiListCard(colors = listColors, showDividers = !atlasReorder.isDragging) {
                    atlasRows.forEachIndexed { index, item ->
                        val on = if (item == AtlasContent.SKY) showSkyPhotography else showCoastWeather
                        key(item.key) {
                            MochiListRow(
                                title = atlasContentTitle(item),
                                subtitle = atlasContentSubtitle(item),
                                leading = {
                                    PhosphorIcon(
                                        atlasContentIcon(item), null, Modifier.size(22.dp),
                                        if (on) mochiLeadingIconTint() else dimIconTint,
                                    )
                                },
                                colors = if (on) listColors else hiddenRowColors,
                                modifier = Modifier.mochiReorderable(atlasReorder, item.key, index, listColors.container),
                                trailing = {
                                    Box(Modifier.alpha(if (on) 1f else 0.5f)) {
                                        MochiSettingSwitch(on) { enabled ->
                                            scope.launch {
                                                if (item == AtlasContent.SKY) {
                                                    SettingsRepository.setShowSkyPhotography(enabled)
                                                } else {
                                                    SettingsRepository.setShowCoastWeather(enabled)
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                MochiListSectionFooter("长按可拖动排序，顺序与开关立即保存。")
            }
        }
        if (edit.editing) {
            // 给浮在底部的操作栏留出空间，最后一行不被挡住；只放在列表末尾，免得中间多出一段空白。
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

/** 模块布局页每行的一句说明，和首页实际内容保持一致。 */
private fun homeModuleSubtitle(module: HomeModule): String = when (module) {
    HomeModule.HOURLY -> "未来两小时的逐时天气"
    HomeModule.PRECIP -> "未来两小时开始、停止与强度趋势"
    HomeModule.SPACETIME -> if (ReleaseFeatures.radar) "天气回看、雷达与摄影参考时段" else "天气回看与摄影参考时段"
    HomeModule.DAILY -> "未来五天的高低温与天气"
    HomeModule.TELEMETRY -> "湿度/风/气压/能见度等"
    HomeModule.AQI -> "AQI 与六项污染物"
    HomeModule.OUTFIT -> "按体感温度推荐穿搭"
    HomeModule.INDICES -> "当天指数与未来三天建议"
    HomeModule.YESTERDAY -> "昨日高低温与温差"
    HomeModule.TYPHOON -> "实况路径、强度变化与多机构预报"
    HomeModule.FEATURES -> "卫星辐射、洪水风险、预报扩展"
}

/** 「气象视界内容」每一行的标题，和首页气象视界里的两张卡保持一致。 */
private fun atlasContentTitle(item: AtlasContent): String = when (item) {
    AtlasContent.SKY -> if (ReleaseFeatures.starPhotography) "天空与星空" else "霞光时刻"
    AtlasContent.COAST -> "海岸"
}

private fun atlasContentSubtitle(item: AtlasContent): String = when (item) {
    AtlasContent.SKY -> if (ReleaseFeatures.starPhotography) "查看霞光和星空拍摄时段"
    else "查看晨昏霞光参考时段；使用 Open-Meteo 当地预报"
    AtlasContent.COAST -> "潮位、海浪与海温预览"
}

private fun atlasContentIcon(item: AtlasContent): Int = when (item) {
    AtlasContent.SKY -> if (ReleaseFeatures.starPhotography) R.drawable.ph_moon_stars else R.drawable.ph_sparkle
    AtlasContent.COAST -> R.drawable.ph_waves
}

/** Mochi 分段选择行：标题 + 2~5 个短选项，写回旧页同一批 Repository 方法。 */
@Composable
private fun MochiChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    hint: String? = null,
    onPick: (String) -> Unit,
) {
    val labels = remember(options) { options.map { it.first }.toImmutableList() }
    val index = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    MochiListRow(label, subtitle = hint)
    MochiSegmentedControl(
        selectedIndex = index,
        onSelect = { i -> options.getOrNull(i)?.second?.let(onPick) },
        items = labels,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    )
}


@Composable
private fun MochiPickerRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
) {
    val labels = remember(options) { options.map { it.first }.toImmutableList() }
    val index = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    MochiListPickerRow(
        title = label,
        options = labels,
        selectedIndex = index,
        onSelect = { i -> options.getOrNull(i)?.second?.let(onPick) },
    )
}

/** 页 0：数据与位置。 */
private fun androidx.compose.foundation.lazy.LazyListScope.mochiDataSection(
    context: Context,
    source: SourcePref, activeSource: String?, activeSupplementSources: List<String>,
    activeCityName: String?, sourceLoading: Boolean,
    locationEnabled: Boolean, preciseLocationEnabled: Boolean, preciseLocationPermissionAsked: Boolean,
    locating: Boolean, locateMessage: String?, onClearLocateMessage: () -> Unit,
    activeCityLatitude: Double?, activeCityLongitude: Double?,
    developerMode: Boolean, permDenied: Boolean, onLocate: () -> Unit,
    launchPermissions: (Boolean) -> Unit, openAppSettings: () -> Unit,
    toggleLocation: (Boolean) -> Unit, togglePrecise: (Boolean) -> Unit,
    toggleDeveloper: (Boolean) -> Unit,
    onWeatherSource: () -> Unit, onLocationEngine: () -> Unit, onProviders: () -> Unit,
) {
    item {
        MochiListSection(
            header = "天气数据",
            footer = if (isPhosphorVista) ""
                else mochiSourceHint(source, activeSource, activeSupplementSources, activeCityName, sourceLoading),
        ) {
            MochiSettingsNavRow(
                route = "settings.weatherSource",
                title = "天气来源",
                value = if (source == SourcePref.AUTO) "自动选择" else source.cn,
                onPush = { onWeatherSource() },
            )
            MochiListRow(
                title = "当前使用",
                value = if (sourceLoading) "天气更新中" else
                    mochiSourceName(activeSource) ?: "等待天气数据",
                subtitle = "可按模块设置",
            )
        }
    }
    item {
        MochiListSection(
            header = "位置服务",
            footer = if (locationEnabled) "已开启 · 仅在打开时复核" else "已关闭 · 不读取位置权限",
        ) {
            MochiListRow(
                title = "自动跟随所在城市",
                subtitle = if (locationEnabled) "开启·打开 App 时自动更新" else "关闭·不申请任何位置权限",
                trailing = { MochiSettingSwitch(locationEnabled, toggleLocation) },
            )
            if (locationEnabled) {
                MochiSettingsNavRow(
                    route = "settings.locationEngine",
                    title = "位置服务引擎",
                    subtitle = "系统定位 / 高德 / 百度 / 遇见位置服务",
                    onPush = { onLocationEngine() },
                )
                MochiListRow(
                    title = "街道级精确定位",
                    subtitle = if (preciseLocationEnabled) "开启·系统定位与街道识别无需 Key；精度不足保留坐标"
                    else "关闭·仅使用城市级大致位置",
                    trailing = { MochiSettingSwitch(preciseLocationEnabled, togglePrecise) },
                )
                MochiListRow(
                    title = if (locating) "定位中 ..." else "立即重新定位",
                    enabled = !locating, chevron = true,
                    leading = {
                        PhosphorIcon(R.drawable.ph_crosshair, null, Modifier.size(22.dp), mochiLeadingIconTint())
                    },
                    onClick = {
                        onClearLocateMessage()
                        val permissionReady = if (preciseLocationEnabled) {
                            LocationSource.hasPrecisePermission(context) ||
                                (LocationSource.hasPermission(context) && preciseLocationPermissionAsked)
                        } else LocationSource.hasPermission(context)
                        if (permissionReady) onLocate() else launchPermissions(preciseLocationEnabled)
                    },
                )
                if (activeCityName != null || (activeCityLatitude != null && activeCityLongitude != null)) {
                    // 已获取到的定位：详细地址（最多两行）+ 经纬度（单行超长省略）+ 精度 / 新鲜度小字。
                    // 定位文案里的精度与秒数已经用图标单独展示，这里不再重复整句。
                    val addressParts = remember(locateMessage, activeCityName) {
                        mochiLocateAddressParts(locateMessage, activeCityName)
                    }
                    val region = addressParts?.first ?: activeCityName
                    val detail = addressParts?.second
                    val notice = remember(locateMessage) { mochiLocateNotice(locateMessage) }
                    val fix = remember(locating, locateMessage) { LocationServiceLog.lastResolution }
                    val coordinates = if (activeCityLatitude != null && activeCityLongitude != null)
                        mochiCoordinateLabel(activeCityLatitude, activeCityLongitude) else null
                    val accuracy = fix?.accuracyMeters?.takeIf { it.isFinite() && it > 0f }?.let {
                        if (it >= 1000f) "约 %.1f km".format(it / 1000f) else "约 ${it.toInt().coerceAtLeast(1)} 米"
                    }
                    // 记录时的定位年龄 + 之后过去的时间，展示「此刻离这次定位多久」。
                    // 参考 demo 的数字计时器（MochiCounterText + LaunchedEffect delay(1000)）：
                    // 基值是这次定位出现时的年龄，之后每秒 tick++，秒数持续递增并滚动刷新。
                    val fixKey = fix?.timeMillis
                    val baseAge = remember(fixKey) {
                        fix?.let {
                            it.fixAgeSeconds + ((System.currentTimeMillis() - it.timeMillis) / 1000L).coerceAtLeast(0L)
                        }
                    }
                    var tick by remember(fixKey) { mutableLongStateOf(0L) }
                    LaunchedEffect(fixKey) {
                        while (fixKey != null) {
                            delay(1_000)
                            tick++
                        }
                    }
                    val fixAge = baseAge?.let { it + tick }
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PhosphorIcon(R.drawable.ph_map_pin, null, Modifier.size(22.dp), mochiLeadingIconTint())
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                // 第一行省市独占一行；具体位置允许换行；坐标一行省略。
                                Text(
                                    region ?: "当前位置",
                                    style = MochiTheme.textStyles.body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (detail != null) Text(
                                    detail,
                                    style = MochiTheme.textStyles.subhead,
                                    color = MochiTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (coordinates != null) Text(
                                    coordinates,
                                    style = MochiTheme.textStyles.footnote,
                                    color = MochiTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (accuracy != null || fixAge != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 34.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (accuracy != null) MochiLocationFact(R.drawable.ph_crosshair, accuracy)
                                if (fixAge != null) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        PhosphorIcon(R.drawable.ph_clock, null, Modifier.size(14.dp), MochiTheme.colorScheme.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            MochiCounterText(
                                                fixAge,
                                                style = MochiTheme.textStyles.footnote,
                                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                " 秒前的位置",
                                                style = MochiTheme.textStyles.footnote,
                                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (notice != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 34.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PhosphorIcon(
                                    R.drawable.ph_warning, null, Modifier.size(14.dp),
                                    MochiTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    notice,
                                    style = MochiTheme.textStyles.footnote,
                                    color = MochiTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                        // 识别中 / 失败等非地址文案仍然展示，但收进同样的缩进小字里。
                        if (addressParts == null && locateMessage != null) Text(
                            locateMessage,
                            style = MochiTheme.textStyles.footnote,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 34.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (permDenied) {
                    MochiListRow(
                        title = "> 已拒绝位置权限，定位不可用（手动搜索城市不受影响）",
                        subtitle = "[ 去系统设置授权 ]",
                        onClick = openAppSettings,
                    )
                }
            }
        }
    }
    item {
        MochiListSection(header = "数据接入", footer = "自有服务凭据保存在本机") {
            MochiListRow(
                title = "开发者模式",
                subtitle = if (developerMode) "已开启 · 管理个人天气与地理服务" else "配置自有天气与地理服务凭据",
                trailing = { MochiSettingSwitch(developerMode, toggleDeveloper) },
            )
            if (!isPhosphorVista || developerMode) {
                MochiSettingsNavRow(
                    route = "settings.providers",
                    title = "管理自有服务接入",
                    enabled = developerMode,
                    onPush = { onProviders() },
                )
            }
        }
    }
}

/** 页 1：首页内容。 */
private fun androidx.compose.foundation.lazy.LazyListScope.mochiHomeSection(
    homeBriefingStyle: HomeBriefingStyle, tempUnit: String, windUnit: String, pressureUnit: String,
    showTelemetry: Boolean, showIndices: Boolean,
    developerMode: Boolean, telemetryMetrics: Set<TelemetryMetric>, lifeIndexMetrics: Set<LifeIndexMetric>,
    cityManagerStyle: CityManagerStyle,
    onBriefing: (String) -> Unit, onTempUnit: (String) -> Unit, onWindUnit: (String) -> Unit,
    onPressureUnit: (String) -> Unit,
    onCityManagerStyle: (String) -> Unit,
    onPush: (String) -> Unit,
) {
    item {
        MochiListSection(header = "播报与单位") {
            MochiListRow(
                title = "天气娘播报",
                subtitle = "关闭后主界面会完整收起播报区域",
                trailing = {
                    MochiSettingSwitch(homeBriefingStyle != HomeBriefingStyle.OFF) { enabled ->
                        onBriefing((if (enabled) HomeBriefingStyle.WEATHER_GIRL else HomeBriefingStyle.OFF).key)
                    }
                },
            )
            MochiPickerRow("温度", listOf("摄氏 °C" to "c", "华氏 °F" to "f"), tempUnit, onTempUnit)
            MochiPickerRow("风速", listOf("km/h" to "kmh", "m/s" to "ms", "级" to "bft"), windUnit, onWindUnit)
            MochiPickerRow("气压", listOf("hPa" to "hpa", "mmHg" to "mmhg", "inHg" to "inhg"),
                pressureUnit, onPressureUnit)
        }
    }
    item {
        MochiListSection(header = "城市管理页面") {
            MochiChoiceRow(
                "页面样式",
                listOf("侧边栏" to "sidebar", "独立页面" to "page"),
                cityManagerStyle.key,
                if (cityManagerStyle == CityManagerStyle.SIDEBAR) "从首页边缘滑出城市列表，不离开当前天气页"
                else "整屏打开城市列表，选中城市后回到天气页",
                onCityManagerStyle,
            )
        }
    }
    if (developerMode) {
        item {
            MochiListSection(header = "自定义数据") {
                MochiSettingsNavRow(
                    route = "settings.telemetry",
                    title = "遥测数据",
                    value = "${telemetryMetrics.size}/${TelemetryMetric.entries.size}",
                    enabled = showTelemetry,
                    onPush = onPush,
                )
                MochiSettingsNavRow(
                    route = "settings.lifeIndex",
                    title = "生活指数数据",
                    value = "${lifeIndexMetrics.size}/${LifeIndexMetric.entries.size}",
                    enabled = showIndices,
                    onPush = onPush,
                )
            }
        }
    }
    item {
        MochiListSection(header = "布局") {
            MochiSettingsNavRow(
                route = "settings.modules",
                title = "模块布局",
                subtitle = "长按拖动排序，开关控制是否显示在首页",
                onPush = onPush,
            )
        }
    }
}

/** 页 2：外观与体验。 */
@Suppress("LongParameterList")
private fun androidx.compose.foundation.lazy.LazyListScope.mochiAppearanceSection(
    interfaceStyle: InterfaceStyle,
    accentTone: AccentTone, softGlow: Boolean, softGlowLevel: SoftGlowLevel,
    ambience: AmbienceLevel, livingSky: Boolean, scanlines: Boolean,
    landscapeStandby: Boolean, standbyBurnInProtection: Boolean,
    landscapeStandbyStyle: LandscapeStandbyStyle,
    appIconStyle: AppIconStyle, keepScreenOn: Boolean,
    developerMode: Boolean, graphicsLevel: String, gel: Float,
    onInterfaceStyle: (String) -> Unit,
    onAccentTone: (String) -> Unit, onSoftGlow: (Boolean) -> Unit,
    onSoftGlowLevel: (String) -> Unit, onAmbience: (String) -> Unit, onLivingSky: (Boolean) -> Unit,
    onScanlines: (Boolean) -> Unit, onLandscapeStandby: (Boolean) -> Unit, onBurnIn: (Boolean) -> Unit,
    onLandscapeStyle: (String) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onAtmosphereLab: () -> Unit, onGraphicsLevel: (String) -> Unit,
    onGel: (Float) -> Unit, onAppIcon: () -> Unit, onPush: (String) -> Unit,
    glassOn: Boolean,
) {
    item {
        MochiListSection(header = "界面") {
            // 「模块外观」已移除：全站固定使用无界香氛；深浅主题统一到「主题配色」页的黑暗模式。
            if (ReleaseFeatures.classicTheme) MochiChoiceRow("界面主题",
                listOf("澄空终端" to "phosphor_vista", "经典终端" to "classic_terminal"),
                interfaceStyle.key,
                if (interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA) "无框天气概览，逐时、五日与多日预报依次展开"
                else "经典终端美术风格，与澄空共用天气逻辑和模块排序", onInterfaceStyle)
            MochiPickerRow("强调色亮度", listOf("标准" to "standard", "柔和" to "soft"),
                accentTone.key, onAccentTone)
            MochiListRow(
                title = "液态玻璃",
                subtitle = "栏、菜单、面板与按钮的材质，可单独选择用在哪里",
                value = if (glassOn) "开" else "关",
                titleTrailing = { MochiBadge("Beta") },
                chevron = true,
                // 和 MochiSettingsNavRow 一样登记来源卡片，返回时缩回这一行而不是掉到角落变换。
                modifier = Modifier.mochiNavSource(
                    mochiSettingsSourceKey("settings.glass"),
                    MochiListDefaults.SectionCorner,
                    if (MochiTransitionConfig.sourceHighlight) MochiTheme.colorScheme.primaryContainer
                    else MochiDefaults.groupedContainer(),
                ),
                onClick = { onPush("settings.glass") },
            )
        }
    }
    item {
        MochiListSection(header = "天光与动效") {
            if (isPhosphorVista) MochiListRow(title = "柔雾辉光", subtitle = "柔和色泽缓慢流动，贯穿所有页面",
                trailing = { MochiSettingSwitch(softGlow, onSoftGlow) })
            if (isPhosphorVista && softGlow) MochiChoiceRow("辉光变化",
                SoftGlowLevel.entries.map { it.cn to it.key }, softGlowLevel.key,
                "柔和档轻缓流动；流光档色彩聚散和移动更明显", onSoftGlowLevel)
            if (ReleaseFeatures.weatherAtmosphere) {
                MochiChoiceRow(
                    if (isPhosphorVista) "自然天光" else "天气氛围层",
                    listOf("关闭" to "off", "克制" to "subtle", "明显" to "vivid", "强烈" to "intense"),
                    ambience.key,
                    if (isPhosphorVista) "天光、云雾与雨雪随天气变化，向下阅读时渐隐；省电模式下静止显示"
                    else "强烈档增加粒子密度、移动速度与磷光亮度",
                    onAmbience,
                )
                if (isPhosphorVista) {
                    MochiListRow(title = "随时间变化的天空",
                        subtitle = if (ambience == AmbienceLevel.OFF) "打开自然天光后生效；关闭此开关恢复原来的天空"
                        else "主页与澄空小组件跟随城市日照变化；关闭恢复原来的天空",
                        trailing = { MochiSettingSwitch(livingSky, onLivingSky) })
                }
                // 标准范例：登记来源行，push/from 用同一个 key，页面从这一行长成整屏、返回缩回。
                MochiSettingsNavRow(route = "settings.atmosphere", title = "天气效果预览",
                    subtitle = "天气场景与一天的天空", onPush = onPush)
                if (!isPhosphorVista) MochiListRow(title = "CRT 扫描线", subtitle = "整屏细横纹，终端质感",
                    trailing = { MochiSettingSwitch(scanlines, onScanlines) })
            }
        }
    }
    item {
        MochiListSection(
            header = "UI 个性化",
            footer = "画面效果档位来自组件库的图形能力；HDR 高光需要 Android 14 以上和支持 HDR 的屏幕。",
        ) {
            MochiSettingsNavRow(
                route = "settings.graphics",
                title = "画面效果",
                value = when (graphicsLevel) {
                    "mid" -> "中"
                    "extra" -> "超高"
                    else -> "高"
                },
                subtitle = "独立页内切换档位并可实时预览动效",
                onPush = onPush,
            )
            MochiListSliderRow(
                value = gel,
                onValueChange = onGel,
                valueRange = 0f..1f,
                title = "果冻感",
                valueText = "%.2f".format(gel),
                supportingText = "影响开关、滑块、分段控件、标签栏与下拉菜单的弹性形变",
            )
            MochiSettingsNavRow(
                route = "settings.theme",
                title = "主题配色",
                subtitle = "黑暗模式、主题色、取色算法与对比度",
                onPush = onPush,
            )
            MochiSettingsNavRow(
                route = "settings.tabbar",
                title = "底部标签栏",
                subtitle = "图标文字排布、文字模糊、融球残影与滚动收起",
                onPush = onPush,
            )
            MochiSettingsNavRow(
                route = "settings.hdr",
                title = "HDR 高光设置",
                subtitle = "按压高光、边缘流光、亮度余量与全部参数",
                onPush = onPush,
                titleTrailing = { MochiBadge("Beta") },
            )
            MochiSettingsNavRow(
                route = "settings.pressGlow",
                title = "按压高光设置",
                subtitle = "光的颜色与亮度，带可按住拖动的预览",
                onPush = onPush,
            )
            MochiSettingsNavRow(
                route = "settings.transition",
                title = "应用转场与侧推",
                subtitle = "页面切换动画、打开应用式转场、预测性返回与下层页缩放",
                onPush = onPush,
            )
        }
    }
    item {
        MochiListSection(header = "横屏待机") {
            MochiListRow(title = "横屏待机界面", subtitle = "横屏时显示天气时钟；屏幕旋转跟随系统设置",
                trailing = { MochiSettingSwitch(landscapeStandby, onLandscapeStandby) })
            MochiListRow(title = "防烧屏保护",
                subtitle = "横屏内容每 15 秒轻移 1 个像素，减少固定位置长期显示；只能降低烧屏风险",
                trailing = { MochiSettingSwitch(standbyBurnInProtection, onBurnIn) })
            if (ReleaseFeatures.classicTheme) MochiChoiceRow("横屏样式",
                listOf("经典终端" to "classic", "气象中枢" to "weather_core"),
                landscapeStandbyStyle.key,
                if (!landscapeStandby) "开启横屏待机界面后生效"
                else if (isPhosphorVista) "经典时钟，或同时显示天气趋势的天气时钟"
                else "经典保留现版；气象中枢强化日照轨迹、天气趋势与沉浸光感", onLandscapeStyle)
        }
    }
    item {
        MochiListSection(header = "刷新、小组件与图标") {
            MochiSettingsNavRow(route = "settings.automation", title = "刷新与天气提醒", onPush = onPush)
            MochiSettingsNavRow(route = "settings.widgets", title = "小组件与通知栏", onPush = onPush)
            MochiListRow(
                title = "应用图标",
                value = appIconStyle.cn,
                subtitle = "自定义桌面图标",
                chevron = true,
                onClick = onAppIcon,
            )
            MochiListRow(title = "常亮屏幕", subtitle = "看天气时不自动息屏",
                trailing = { MochiSettingSwitch(keepScreenOn, onKeepScreenOn) })
        }
    }
}

private fun mochiSourceName(activeSource: String?): String? = when (activeSource) {
    "QWEATHER" -> "和风天气"
    "CAIYUN" -> "彩云天气"
    "XIAOMI" -> "小米公开接口"
    "OPEN-METEO" -> "Open-Meteo"
    "NMC" -> "中央气象台"
    "ZHISHENG" -> "遇见天气源"
    "AMAP" -> "高德天气"
    "BAIDU" -> "百度天气"
    "TENCENT" -> "腾讯天气"
    "WEATHERCN" -> "中国天气网"
    "SENIVERSE" -> "心知天气"
    "MOJI" -> "墨迹天气"
    else -> activeSource
}

private fun mochiSourceHint(
    selected: SourcePref, activeSource: String?, supplementSources: List<String>,
    cityName: String?, loading: Boolean,
): String {
    val city = cityName ?: "当前城市"
    if (loading) return "正在为 $city 连接 ${selected.cn}，完成后这里会显示实际返回数据的来源。"
    val active = mochiSourceName(activeSource)
        ?: return "$city 还没有成功返回天气数据；选择数据源后可直接看到连接结果。"
    val supplements = supplementSources.mapNotNull(::mochiSourceName).filter { it != active }.distinct()
    val summary = if (supplements.isEmpty()) active else "$active + ${supplements.joinToString("/")}（分项）"
    return if (selected == SourcePref.AUTO) {
        "$city 当前实际使用：$summary。自动优选会按功能选源，并在首选源不可用时降级。"
    } else "$city 当前实际使用：$summary；设置已锁定为 ${selected.cn}。"
}

/** 定位信息里的一枚小字说明：14dp 图标 + 13sp 文字。 */
@Composable
private fun MochiLocationFact(icon: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhosphorIcon(icon, null, Modifier.size(14.dp), MochiTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MochiTheme.textStyles.footnote,
            color = MochiTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun mochiCoordinateLabel(latitude: Double, longitude: Double): String {
    // 异常坐标（非有限值或超出范围）不铺开整串数字，统一截断成省略号。
    if (!latitude.isFinite() || !longitude.isFinite() ||
        latitude !in -90.0..90.0 || longitude !in -180.0..180.0
    ) return "经纬度异常 …"
    val lat = String.format(java.util.Locale.US, "%.4f", kotlin.math.abs(latitude))
    val lon = String.format(java.util.Locale.US, "%.4f", kotlin.math.abs(longitude))
    val latDir = if (latitude >= 0) "北纬" else "南纬"
    val lonDir = if (longitude >= 0) "东经" else "西经"
    return "$latDir $lat° · $lonDir $lon°"
}

/**
 * 从定位成功文案里取出街道级地址，例如
 * 「已定位：茂名市 · 茂南区 · 公馆镇银莲路… · 约30米 · 0秒前的位置」→「茂名市 · 茂南区 · 公馆镇银莲路…」。
 * 精度与秒数已经单独用小字展示，这里只留地址，避免整句重复。
 */
private fun mochiLocateAddress(message: String?): String? {
    if (message.isNullOrBlank()) return null
    val prefixes = listOf("已定位：", "已自动更新定位：", "最近大致位置：")
    val prefix = prefixes.firstOrNull { message.startsWith(it) } ?: return null
    val parts = message.removePrefix(prefix).split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    // 结尾的「约30米」「0秒前的位置」「精度未知」是单独展示的，去掉后剩下的整段才是地址。
    val tail = Regex("""^(约\s*\d+(?:\.\d+)?\s*(?:米|km)|精度未知|\d+秒前的位置)$""")
    return parts.dropLastWhile { tail.matches(it) }
        .joinToString(" · ")
        .substringBefore('（')
        .trim()
        .takeIf { it.isNotBlank() }
}

/** 定位文案括号里的提示（大致位置 / 精度不足 / 未识别到街道）。 */
private fun mochiLocateNotice(message: String?): String? {
    if (message.isNullOrBlank()) return null
    return message.substringAfter('（', "").substringBefore('）').takeIf { it.isNotBlank() }
}

/**
 * 把地址拆成「省市级」与「具体位置」两段，分别独占一行：
 * 「茂名市 · 茂南区 · 公馆镇银莲路…」→「茂名市 · 茂南区」+「公馆镇银莲路…」。
 */
private fun mochiLocateAddressParts(message: String?, cityName: String?): Pair<String, String?>? {
    val address = mochiLocateAddress(message) ?: return null
    val parts = address.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    return when {
        parts.size >= 3 -> {
            val regionParts = parts.take(2)
            regionParts.joinToString(" · ") to mochiDedupeRegion(parts.drop(2).joinToString(" · "), regionParts)
        }
        parts.size == 2 -> parts[0] to mochiDedupeRegion(parts[1], listOf(parts[0]))
        parts.isNotEmpty() -> {
            val region = cityName?.takeIf { it.isNotBlank() && it != address } ?: address
            region to if (region == address) null else mochiDedupeRegion(address, listOf(region))
        }
        else -> null
    }
}

/**
 * 具体位置开头若重复出现第一行的行政区域名，自动去掉：
 * 第一行「茂名市 · 茂南区」+ 详情「茂南区公馆镇银莲路…」→「公馆镇银莲路…」。
 */
private fun mochiDedupeRegion(detail: String, regionParts: List<String>): String? {
    var text = detail.trim()
    // 只用完整的行政区域名（带「市 / 区 / 县」后缀）做前缀匹配，
    // 避免把「茂名大道」这类以城市简称开头的路名误删。
    val tokens = regionParts.map { it.trim() }.filter { it.length >= 2 }.distinct().sortedByDescending { it.length }
    var trimmed = true
    while (trimmed) {
        trimmed = false
        for (token in tokens) {
            if (text.startsWith(token) && text.length > token.length) {
                text = text.removePrefix(token).trimStart(' ', '·', '—', '-')
                trimmed = true
            }
        }
    }
    return text.takeIf { it.isNotBlank() }
}

private fun mochiOpenAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
