package com.zhisheng.weather.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.AccentTone
import com.zhisheng.weather.ui.about.AboutPage
import com.zhisheng.weather.data.AppIconManager
import com.zhisheng.weather.data.AppIconStyle
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.HomeBriefingStyle
import com.zhisheng.weather.data.HomeModule
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.LandscapeStandbyStyle
import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.data.LocationSource
import com.zhisheng.weather.data.QWeatherApi
import com.zhisheng.weather.data.CaiyunApi
import com.zhisheng.weather.data.ReleaseFeatures
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.SoftGlowLevel
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.TelemetryMetric
import com.zhisheng.weather.data.ThemeMode
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.widget.WidgetConfigActivity
import com.zhisheng.weather.widget.WidgetStyle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiBadge
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSliderRow
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
    val showTyphoon by SettingsRepository.showTyphoon.collectAsState(initial = true)
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
    val showAqi by SettingsRepository.showAqi.collectAsState(initial = true)
    val showIndices by SettingsRepository.showIndices.collectAsState(initial = true)
    val showYesterday by SettingsRepository.showYesterday.collectAsState(initial = true)
    val showPrecip by SettingsRepository.showPrecip.collectAsState(initial = true)
    val showTelemetry by SettingsRepository.showTelemetry.collectAsState(initial = true)
    val showSpacetime by SettingsRepository.showSpacetime.collectAsState(initial = true)
    val showSkyPhotography by SettingsRepository.showSkyPhotography.collectAsState(initial = true)
    val showCoastWeather by SettingsRepository.showCoastWeather.collectAsState(initial = true)
    val showFeatures by SettingsRepository.showFeatures.collectAsState(initial = true)
    val showOutfit by SettingsRepository.showOutfit.collectAsState(initial = true)
    val keepScreenOn by SettingsRepository.keepScreenOn.collectAsState(initial = false)
    val standbyBurnInProtection by SettingsRepository.standbyBurnInProtection.collectAsState(initial = true)
    val landscapeStandby by SettingsRepository.landscapeStandby.collectAsState(initial = true)
    val landscapeStandbyStyle by SettingsRepository.landscapeStandbyStyle.collectAsState(
        initial = LandscapeStandbyStyle.WEATHER_CORE,
    )
    val telemetryMetrics by SettingsRepository.telemetryMetrics.collectAsState(initial = TelemetryMetric.defaultSelection)
    val lifeIndexMetrics by SettingsRepository.lifeIndexMetrics.collectAsState(initial = LifeIndexMetric.defaultSelection)
    val themeMode by SettingsRepository.themeMode.collectAsState(initial = ThemeMode.LIGHT)
    val interfaceStyle by SettingsRepository.interfaceStyle.collectAsState(initial = InterfaceStyle.PHOSPHOR_VISTA)
    val accentTone by SettingsRepository.accentTone.collectAsState(initial = AccentTone.STANDARD)
    val appIconStyle by SettingsRepository.appIconStyle.collectAsState(initial = AppIconStyle.CHARACTER)
    val homeBriefingStyle by SettingsRepository.homeBriefingStyle.collectAsState(initial = HomeBriefingStyle.TIPS)
    val homeSurfaceStyle by SettingsRepository.homeSurfaceStyle.collectAsState(initial = HomeSurfaceStyle.FRAGRANCE_GLASS)
    val moduleOrder by SettingsRepository.moduleOrder.collectAsState(initial = HomeModule.defaultOrder)

    var permDenied by remember { mutableStateOf(false) }
    var showIconDialog by rememberSaveable { mutableStateOf(false) }
    val toast = LocalMochiToast.current
    // 三段设置页改用库的底部悬浮标签栏；状态自带跨重建保存。
    val tabState = rememberMochiTabScaffoldState(initialSection.coerceIn(0, 2))
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
    val uiPrefs = rememberMochiUiPrefs()
    // 「打开应用式」：push 时把来源行登记的 key 交给库，页面从那一行长成整屏，返回缩回去。
    // 其余样式走库的侧推。偏好变化会让这个 lambda 重新组合，下一次 push 立即采用新配置。
    val openSettingsPage: (String) -> Unit = { route ->
        val from = if (PageTransitionStyle.from(uiPrefs.pageTransition) == PageTransitionStyle.APP) {
            mochiSettingsSourceKey(route)
        } else {
            null
        }
        nav.push(route, from = from)
    }
    val tabBehavior = mochiTabBehaviorFor(uiPrefs)
    // 外观在根部接一次：iOS 外观开关只影响这里的材质与配色，页面内不写分支。
    MochiAppTheme(colors = mochiColors) {
        ProvideMochiOverscroll {
            // 转场与侧推配置来自「外观与设备 → 应用转场与侧推」，同步作用于库导航。
            MochiNavHost(
                nav,
                spec = mochiNavSpecFor(uiPrefs),
                predictiveBack = uiPrefs.predictiveBack && uiPrefs.swipeBack,
                swipeBackEnabled = uiPrefs.swipeBack && MochiTransitionConfig.motionEnabled,
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
                                    MochiLazyCompactTitleScaffold(
                            title = "设置",
                            subtitle = when (index) {
                                0 -> "数据与位置"
                                1 -> "首页内容"
                                else -> "外观与设备"
                            },
                            navigationIcon = { MochiBackButton(onClick = onBack) },
                            actions = {
                                MochiBarIconButton(onClick = { openSettingsPage("settings.about") }) {
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
                                onProviders = { openSettingsPage("settings.providers") },
                            )
                            1 -> mochiHomeSection(
                                homeBriefingStyle = homeBriefingStyle, tempUnit = tempUnit,
                                windUnit = windUnit, pressureUnit = pressureUnit,
                                showSkyPhotography = showSkyPhotography,
                                showCoastWeather = showCoastWeather, showSpacetime = showSpacetime,
                                showPrecip = showPrecip, showTelemetry = showTelemetry,
                                showAqi = showAqi, showIndices = showIndices,
                                showYesterday = showYesterday, showTyphoon = showTyphoon,
                                showFeatures = showFeatures, showOutfit = showOutfit,
                                developerMode = developerMode, telemetryMetrics = telemetryMetrics,
                                lifeIndexMetrics = lifeIndexMetrics,
                                onBriefing = { scope.launch { SettingsRepository.setHomeBriefingStyle(HomeBriefingStyle.from(it)) } },
                                onTempUnit = { scope.launch { SettingsRepository.setTempUnit(it) } },
                                onWindUnit = { scope.launch { SettingsRepository.setWindUnit(it) } },
                                onPressureUnit = { scope.launch { SettingsRepository.setPressureUnit(it) } },
                                onModuleToggle = { key, value ->
                                    scope.launch {
                                        when (key) {
                                            "sky" -> SettingsRepository.setShowSkyPhotography(value)
                                            "coast" -> SettingsRepository.setShowCoastWeather(value)
                                            "spacetime" -> SettingsRepository.setShowSpacetime(value)
                                            "precip" -> SettingsRepository.setShowPrecip(value)
                                            "telemetry" -> SettingsRepository.setShowTelemetry(value)
                                            "aqi" -> SettingsRepository.setShowAqi(value)
                                            "indices" -> SettingsRepository.setShowIndices(value)
                                            "yesterday" -> SettingsRepository.setShowYesterday(value)
                                            "typhoon" -> SettingsRepository.setShowTyphoon(value)
                                            "features" -> SettingsRepository.setShowFeatures(value)
                                            "outfit" -> SettingsRepository.setShowOutfit(value)
                                        }
                                    }
                                },
                                onPush = openSettingsPage,
                            )
                            else -> mochiAppearanceSection(
                                homeSurfaceStyle = homeSurfaceStyle, interfaceStyle = interfaceStyle,
                                themeMode = themeMode,
                                accentTone = accentTone, softGlow = softGlow,
                                softGlowLevel = softGlowLevel, ambience = ambience,
                                livingSky = livingSky, scanlines = scanlines,
                                landscapeStandby = landscapeStandby,
                                standbyBurnInProtection = standbyBurnInProtection,
                                landscapeStandbyStyle = landscapeStandbyStyle,
                                appIconStyle = appIconStyle, keepScreenOn = keepScreenOn,
                                developerMode = developerMode,
                                graphicsLevel = uiPrefs.graphicsLevel,
                                onSurfaceStyle = { scope.launch { SettingsRepository.setHomeSurfaceStyle(HomeSurfaceStyle.from(it)) } },
                                onInterfaceStyle = { scope.launch { SettingsRepository.setInterfaceStyle(InterfaceStyle.from(it)) } },
                                onThemeMode = { scope.launch { SettingsRepository.setThemeMode(ThemeMode.from(it)) } },
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
                    "settings.about" -> AboutPage(onBack = { nav.pop() })
                    // 天气源、服务商、氛围实验室也从设置进，走同一套库导航转场与侧滑返回
                    "settings.weatherSource" -> MochiWeatherSourceScreen(
                        weather = activeWeather,
                        onManageProviders = { openSettingsPage("settings.providers") },
                        onBack = { nav.pop() },
                    )
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
                    "settings.modules" -> MochiSecondaryPage("模块布局", "长按并拖动，调整首页模块顺序") {
                        item {
                            MochiListSection(
                                header = "首页模块",
                                footer = "顺序变更会立即应用到首页。",
                            ) {
                                ModuleOrderEditor(moduleOrder) { next ->
                                    scope.launch { SettingsRepository.setModuleOrder(next) }
                                }
                                MochiListRow(
                                    title = "恢复默认顺序",
                                    enabled = moduleOrder != HomeModule.defaultOrder,
                                    onClick = {
                                        scope.launch { SettingsRepository.setModuleOrder(HomeModule.defaultOrder) }
                                    },
                                    destructive = true,
                                )
                            }
                        }
                    }
                    "settings.telemetry" -> MochiSecondaryPage("遥测项目", "${telemetryMetrics.size} / ${TelemetryMetric.entries.size} 项显示在首页") {
                        item {
                            MochiListSection(header = "逐项开关") {
                                TelemetryMetric.entries.forEach { metric ->
                                    MochiListRow(
                                        title = metric.cn,
                                        subtitle = metric.en,
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
                                MochiListRow(title = "澄空小组件", chevron = true, onClick = {
                                    context.startActivity(Intent(context, WidgetConfigActivity::class.java)
                                        .putExtra("widget_style", WidgetStyle.VISTA.key))
                                })
                                MochiListRow(title = "经典小组件", chevron = true, onClick = {
                                    context.startActivity(Intent(context, WidgetConfigActivity::class.java)
                                        .putExtra("widget_style", WidgetStyle.CLASSIC.key))
                                })
                                MochiListRow(title = "通知栏天气", chevron = true, onClick = {
                                    context.startActivity(Intent(context, WidgetConfigActivity::class.java)
                                        .putExtra("notification_settings", true))
                                })
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
            footer = "深色与浅色随主题联动；天气娘和简约图标独立保持",
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
    }
}

/** 库的 onCheckedChange 是必需的第二个位置参数，trailing lambda 会误落到最后的 hapticProfile。 */
@Composable
private fun MochiSettingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    MochiSwitch(checked = checked, onCheckedChange = onCheckedChange)
}

/** 每个设置入口一个稳定的来源 key：push(route, from = key) 时库才知道从哪张卡片展开。 */
internal fun mochiSettingsSourceKey(route: String): String = "settings.source.$route"

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
        modifier = Modifier.mochiNavSource(
            mochiSettingsSourceKey(route),
            MochiListDefaults.SectionCorner,
            sourceColor,
        ),
        onClick = { onPush(route) },
    )
}

/** HDR 高光设置入口右侧的「Beta」小徽章。 */
@Composable
private fun BetaBadge() {
    val cs = MochiTheme.colorScheme
    Box(
        Modifier
            .background(cs.secondaryContainer, MochiTheme.shapes.full)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text("Beta", style = MochiTheme.textStyles.labelSmall, color = cs.onSecondaryContainer) }
}

/** 二级设置页统一外壳：库顶栏 + 侧推返回按钮 + 懒加载列表。 */
@Composable
private fun MochiSecondaryPage(
    title: String,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit) = { MochiBackButton() },
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    MochiSettingsScaffold(title = title, subtitle = subtitle, content = content)
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
    onWeatherSource: () -> Unit, onProviders: () -> Unit,
) {
    item {
        MochiListSection(
            header = "天气数据",
            footer = if (isPhosphorVista) "天气来源、定位方式与数据接入。"
                else mochiSourceHint(source, activeSource, activeSupplementSources, activeCityName, sourceLoading),
        ) {
            MochiListRow(
                title = "天气来源",
                value = if (source == SourcePref.AUTO) "自动选择" else source.cn,
                chevron = true, onClick = onWeatherSource,
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
                MochiListRow(
                    title = "街道级精确定位",
                    subtitle = if (preciseLocationEnabled) "开启·系统定位与街道识别无需 Key；精度不足保留坐标"
                    else "关闭·仅使用城市级大致位置",
                    trailing = { MochiSettingSwitch(preciseLocationEnabled, togglePrecise) },
                )
                MochiListRow(
                    title = if (locating) "定位中 ..." else "⌖ 立即重新定位",
                    enabled = !locating, chevron = true,
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
                    MochiListRow(
                        title = activeCityName ?: "当前位置",
                        subtitle = if (activeCityLatitude != null && activeCityLongitude != null)
                            mochiCoordinateLabel(activeCityLatitude, activeCityLongitude) else null,
                    )
                }
                if (locateMessage != null) MochiListRow(title = "> $locateMessage")
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
                MochiListRow(title = "管理自有服务接入", chevron = true,
                    enabled = developerMode, onClick = onProviders)
            }
        }
    }
}

/** 页 1：首页内容。 */
private fun androidx.compose.foundation.lazy.LazyListScope.mochiHomeSection(
    homeBriefingStyle: HomeBriefingStyle, tempUnit: String, windUnit: String, pressureUnit: String,
    showSkyPhotography: Boolean, showCoastWeather: Boolean, showSpacetime: Boolean,
    showPrecip: Boolean, showTelemetry: Boolean, showAqi: Boolean, showIndices: Boolean,
    showYesterday: Boolean, showTyphoon: Boolean, showFeatures: Boolean, showOutfit: Boolean,
    developerMode: Boolean, telemetryMetrics: Set<TelemetryMetric>, lifeIndexMetrics: Set<LifeIndexMetric>,
    onBriefing: (String) -> Unit, onTempUnit: (String) -> Unit, onWindUnit: (String) -> Unit,
    onPressureUnit: (String) -> Unit, onModuleToggle: (String, Boolean) -> Unit,
    onPush: (String) -> Unit,
) {
    item {
        MochiListSection(header = "播报与单位") {
            MochiChoiceRow(
                "播报样式",
                listOf("天气娘" to "weather_girl", (if (isPhosphorVista) "文字提示" else "简洁 Tips") to "tips", "关闭" to "off"),
                homeBriefingStyle.key,
                "天气娘与文字提示内容一致；关闭后主界面会完整收起播报区域",
                onBriefing,
            )
            MochiChoiceRow("温度", listOf("摄氏 °C" to "c", "华氏 °F" to "f"), tempUnit, null, onTempUnit)
            MochiChoiceRow("风速", listOf("km/h" to "kmh", "m/s" to "ms", "级" to "bft"), windUnit, null, onWindUnit)
            MochiChoiceRow("气压", listOf("hPa" to "hpa", "mmHg" to "mmhg", "inHg" to "inhg"),
                pressureUnit, null, onPressureUnit)
        }
    }
    item {
        MochiListSection(header = "模块", footer = "开关会立即影响首页内容。") {
            MochiListRow(
                title = if (ReleaseFeatures.starPhotography) "气象视界 · 天空" else "霞光时刻",
                subtitle = if (ReleaseFeatures.starPhotography) "查看霞光和星空拍摄时段"
                    else "查看晨昏霞光参考时段；使用 Open-Meteo 当地预报",
                trailing = { MochiSettingSwitch(showSkyPhotography) { onModuleToggle("sky", it) } },
            )
            if (ReleaseFeatures.coastalWeather) MochiListRow(
                title = "气象视界 · 海岸",
                subtitle = "潮位、海浪与海温预览；开启后把地点坐标发送给 Open-Meteo",
                trailing = { MochiSettingSwitch(showCoastWeather) { onModuleToggle("coast", it) } },
            )
            MochiListRow(
                title = if (ReleaseFeatures.radar) "气象视界 · 回看与雷达" else "天气回看",
                subtitle = if (ReleaseFeatures.radar) "查看过去天气和降雨变化" else "查看历史天气与往年同期",
                trailing = { MochiSettingSwitch(showSpacetime) { onModuleToggle("spacetime", it) } },
            )
            MochiListRow(title = "短时降水", subtitle = "未来两小时开始、停止与强度趋势",
                trailing = { MochiSettingSwitch(showPrecip) { onModuleToggle("precip", it) } })
            MochiListRow(title = "遥测数据", subtitle = "湿度/风/气压/能见度等",
                trailing = { MochiSettingSwitch(showTelemetry) { onModuleToggle("telemetry", it) } })
            MochiListRow(title = "空气质量", subtitle = "AQI 与六项污染物",
                trailing = { MochiSettingSwitch(showAqi) { onModuleToggle("aqi", it) } })
            MochiListRow(title = "生活指数", subtitle = "当天指数与未来三天建议，项目随数据源而定",
                trailing = { MochiSettingSwitch(showIndices) { onModuleToggle("indices", it) } })
            MochiListRow(title = "昨日复盘", subtitle = "昨日高低温与温差",
                trailing = { MochiSettingSwitch(showYesterday) { onModuleToggle("yesterday", it) } })
            if (ReleaseFeatures.typhoon) MochiListRow(title = "台风路径", subtitle = "实况路径、强度变化与多机构预报",
                trailing = { MochiSettingSwitch(showTyphoon) { onModuleToggle("typhoon", it) } })
            MochiListRow(title = "特色功能", subtitle = "卫星辐射、洪水风险、预报扩展字段与海拔",
                trailing = { MochiSettingSwitch(showFeatures) { onModuleToggle("features", it) } })
            MochiListRow(title = "穿搭", subtitle = "按体感温度推荐上身/外搭/下装与鞋履",
                trailing = { MochiSettingSwitch(showOutfit) { onModuleToggle("outfit", it) } })
        }
    }
    if (developerMode) {
        item {
            MochiListSection(header = "开发者选项") {
                MochiSettingsNavRow(
                    route = "settings.telemetry",
                    title = "选择遥测项目",
                    value = "${telemetryMetrics.size}/${TelemetryMetric.entries.size}",
                    enabled = showTelemetry,
                    onPush = onPush,
                )
                MochiSettingsNavRow(
                    route = "settings.lifeIndex",
                    title = "选择生活指数项目",
                    value = "${lifeIndexMetrics.size}/${LifeIndexMetric.entries.size}",
                    enabled = showIndices,
                    onPush = onPush,
                )
            }
        }
    }
    item {
        MochiListSection(header = "布局") {
            MochiSettingsNavRow(route = "settings.modules", title = "调整主页模块顺序", onPush = onPush)
        }
    }
}

/** 页 2：外观与体验。 */
@Suppress("LongParameterList")
private fun androidx.compose.foundation.lazy.LazyListScope.mochiAppearanceSection(
    homeSurfaceStyle: HomeSurfaceStyle, interfaceStyle: InterfaceStyle,
    themeMode: ThemeMode, accentTone: AccentTone, softGlow: Boolean, softGlowLevel: SoftGlowLevel,
    ambience: AmbienceLevel, livingSky: Boolean, scanlines: Boolean,
    landscapeStandby: Boolean, standbyBurnInProtection: Boolean,
    landscapeStandbyStyle: LandscapeStandbyStyle, appIconStyle: AppIconStyle, keepScreenOn: Boolean,
    developerMode: Boolean, graphicsLevel: String, gel: Float,
    onSurfaceStyle: (String) -> Unit, onInterfaceStyle: (String) -> Unit,
    onThemeMode: (String) -> Unit, onAccentTone: (String) -> Unit, onSoftGlow: (Boolean) -> Unit,
    onSoftGlowLevel: (String) -> Unit, onAmbience: (String) -> Unit, onLivingSky: (Boolean) -> Unit,
    onScanlines: (Boolean) -> Unit, onLandscapeStandby: (Boolean) -> Unit, onBurnIn: (Boolean) -> Unit,
    onLandscapeStyle: (String) -> Unit, onKeepScreenOn: (Boolean) -> Unit,
    onAtmosphereLab: () -> Unit, onGraphicsLevel: (String) -> Unit,
    onGel: (Float) -> Unit, onAppIcon: () -> Unit, onPush: (String) -> Unit,
    glassOn: Boolean,
) {
    item {
        MochiListSection(header = "界面") {
            if (isPhosphorVista) MochiChoiceRow("模块外观", HomeSurfaceStyle.entries.map { it.cn to it.key },
                homeSurfaceStyle.key, "切换首页与设置页的玻璃质感；天气文字和图标保持清晰", onSurfaceStyle)
            if (ReleaseFeatures.classicTheme) MochiChoiceRow("界面主题",
                listOf("澄空终端" to "phosphor_vista", "经典终端" to "classic_terminal"),
                interfaceStyle.key,
                if (interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA) "无框天气概览，逐时、五日与多日预报依次展开"
                else "经典终端美术风格，与澄空共用天气逻辑和模块排序", onInterfaceStyle)
            MochiChoiceRow("主题模式", listOf("深色" to "dark", "浅色" to "light", "跟随系统" to "system"),
                themeMode.key, "浅色清透，深色柔和，也可跟随系统", onThemeMode)
            MochiChoiceRow("强调色亮度", listOf("标准" to "standard", "柔和" to "soft"),
                accentTone.key, "调整界面强调色的亮度", onAccentTone)
            MochiListRow(
                title = "液态玻璃",
                subtitle = "栏、菜单、面板与按钮的材质，可单独选择用在哪里",
                value = if (glassOn) "开" else "关",
                titleTrailing = { MochiBadge("Beta") },
                chevron = true,
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
                MochiListRow(title = "天气效果预览", subtitle = "天气场景与一天的天空",
                    chevron = true, onClick = onAtmosphereLab)
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
                    "extra" -> "极致"
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
                supportingText = "0 = 关闭；影响开关、滑块、分段控件、标签栏与下拉菜单的弹性形变",
            )
            MochiSettingsNavRow(
                route = "settings.theme",
                title = "主题配色",
                subtitle = "配色模式、主题色、取色算法与对比度",
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
                trailing = { BetaBadge() },
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
                subtitle = "深色与浅色随主题联动；天气娘和简约图标独立保持",
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
    "ZHISHENG" -> "枳生天气源"
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

private fun mochiCoordinateLabel(latitude: Double, longitude: Double): String {
    val lat = String.format(java.util.Locale.US, "%.4f", kotlin.math.abs(latitude))
    val lon = String.format(java.util.Locale.US, "%.4f", kotlin.math.abs(longitude))
    val latDir = if (latitude >= 0) "北纬" else "南纬"
    val lonDir = if (longitude >= 0) "东经" else "西经"
    return "$latDir $lat° · $lonDir $lon°"
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
