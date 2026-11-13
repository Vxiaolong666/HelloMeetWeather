@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.zhisheng.weather

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.components.LocalWeatherContinuity
import com.zhisheng.weather.ui.components.WeatherContinuity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import com.zhisheng.weather.data.UsageStatistics
import com.zhisheng.weather.data.ServiceDiagnostics
import com.zhisheng.weather.data.UpdateNotices
import com.zhisheng.weather.ui.AppUpdateDialog
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import ui.feice.mochi.basic.rememberMochiToastState
import com.zhisheng.weather.data.AccentTone
import com.zhisheng.weather.data.AppIconManager
import com.zhisheng.weather.data.AppIconStyle
import com.zhisheng.weather.data.AppUpdate
import com.zhisheng.weather.data.AppUpdateCheck
import com.zhisheng.weather.data.AppUpdateInfo
import com.zhisheng.weather.data.LandscapeStandbyStyle
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.ReleaseFeatures
import com.zhisheng.weather.ui.isStartupIconEntry
import com.zhisheng.weather.data.ThemeMode
import com.zhisheng.weather.model.City
import com.zhisheng.weather.ui.AppScreen
import com.zhisheng.weather.ui.MochiAppTheme
import com.zhisheng.weather.ui.rememberMochiColorScheme
import com.zhisheng.weather.ui.mochiNavSpecFor
import com.zhisheng.weather.ui.SearchScreen
import com.zhisheng.weather.ui.WeatherViewModel
import com.zhisheng.weather.ui.ProvideWeatherPresentationClock
import com.zhisheng.weather.ui.weatherPresentationTime
import com.zhisheng.weather.ui.home.HomeScreen
import com.zhisheng.weather.ui.MochiSettingsScreen
import com.zhisheng.weather.ui.LocalMochiSettingsEntry
import com.zhisheng.weather.ui.MochiPrecipitationDetailScreen
import com.zhisheng.weather.ui.MochiSearchScreen
import com.zhisheng.weather.ui.AtmosphereLabScreen
import com.zhisheng.weather.ui.LocalMochiToast
import com.zhisheng.weather.ui.MochiAppToastHost
import com.zhisheng.weather.ui.PageTransitionStyle
import com.zhisheng.weather.ui.rememberMochiUiPrefs
import com.zhisheng.weather.ui.ApplyMochiUiPrefs
import com.zhisheng.weather.ui.screenTransition
import com.zhisheng.weather.ui.LandscapeStandbyScreen
import com.zhisheng.weather.ui.HistoryScreen
import com.zhisheng.weather.ui.DailyForecastScreen
import com.zhisheng.weather.ui.RadarScreen
import com.zhisheng.weather.ui.TyphoonScreen
import com.zhisheng.weather.ui.AirQualityScreen
import com.zhisheng.weather.ui.FeatureHubScreen
import com.zhisheng.weather.ui.NearbyCityAqi
import com.zhisheng.weather.ui.UpdateOpeningHandledKey
import com.zhisheng.weather.ui.UpdateOpeningOverlay
import com.zhisheng.weather.ui.UpdateOpeningPreferenceFile
import com.zhisheng.weather.ui.UpdateOpeningSpec
import com.zhisheng.weather.ui.WhatsNewDialog
import com.zhisheng.weather.ui.shouldAutoPlayUpdateOpening
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.rememberMochiNavigator
import com.zhisheng.weather.ui.systemAnimationsEnabled
import com.zhisheng.weather.ui.updateOpeningSpec
import com.zhisheng.weather.ui.WhatsNewPreferenceFile
import com.zhisheng.weather.ui.WhatsNewSeenKey
import com.zhisheng.weather.ui.WhatsNewVersion
import com.zhisheng.weather.ui.InitialLocationSetupHost
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import com.zhisheng.weather.data.InterfaceStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private data class ShortcutCommand(val action: String? = null, val sequence: Long = 0L)

class MainActivity : ComponentActivity() {
    private var hostResumed by mutableStateOf(false)
    private var startupPromptsSuppressed by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        hostResumed = true
    }

    override fun onPause() {
        hostResumed = false
        super.onPause()
    }

    private var lastInteraction = SystemClock.elapsedRealtime()
    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteraction = SystemClock.elapsedRealtime()
    }
    private val shortcutCommand = MutableStateFlow(ShortcutCommand())
    private var shortcutSequence = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preserve the real entry before dispatchShortcut normalizes its action.
        val iconEntry = isStartupIconEntry(intent?.action,
            intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true, isTaskRoot,
            savedInstanceState != null,
            (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0)
        // An installer may restore the old task, whose saved UI said notes were closed.
        startupPromptsSuppressed = com.zhisheng.weather.ui.suppressStartupPrompts(iconEntry, intent?.action, shouldShowWhatsNew())
        if (savedInstanceState == null) dispatchShortcut(intent)
        applySplashBackground()
        enableEdgeToEdge()
        applyEdgeToEdgeSystemBars()
        setContent {
            ProvideWeatherPresentationClock {
            // 主题模式（v0.0.5）：深色 / 浅色 / 跟随系统三档，切换立即生效
            val themeMode by SettingsRepository.themeMode.collectAsState(initial = initialThemeMode())
            val accentTone by SettingsRepository.accentTone.collectAsState(initial = AccentTone.STANDARD)
            val softGlow by SettingsRepository.softGlow.collectAsState(initial = false)
            val softGlowLevel by SettingsRepository.softGlowLevel.collectAsState(initial = com.zhisheng.weather.data.SoftGlowLevel.SUBTLE)
            val interfaceStyle by SettingsRepository.interfaceStyle.collectAsState(
                initial = InterfaceStyle.PHOSPHOR_VISTA,
            )
            val homeSurfaceStyle by SettingsRepository.homeSurfaceStyle.collectAsState(
                initial = com.zhisheng.weather.data.HomeSurfaceStyle.CURRENT,
            )
            val appConfiguration = LocalConfiguration.current
            val systemDark = isSystemInDarkTheme()
            val isLight = when (themeMode) {
                ThemeMode.LIGHT -> true
                ThemeMode.DARK -> false
                // 跟随系统：系统深色→深色板（此前直接取 systemDark，方向反了，跟随系统会显示相反主题）
                ThemeMode.SYSTEM -> !systemDark
            }
            ZhishengWeatherTheme(
                isLight = isLight,
                accentTone = accentTone,
                interfaceStyle = interfaceStyle,
                homeSurfaceStyle = homeSurfaceStyle,
                softGlow = softGlow,
                softGlowLevel = softGlowLevel,
            ) {
                val vm: WeatherViewModel = viewModel()
                val scope = rememberCoroutineScope()
                // 全 app 共用一个 Toast：挂在主题根部而不是某个页面分支里，横竖屏切换、页面返回都不影响它存活。
                val toastState = rememberMochiToastState()
                CompositionLocalProvider(LocalMochiToast provides toastState) {
                    MochiAppToastHost(toastState)
                }
                // 方向变化已由 configChanges 原地处理，不需要跨进程保存临时页面。
                // 三星 / realme 会比小米更积极恢复任务状态；若保存 SEARCH，横向冷启动会误回城市选择页。
                // 冷启动统一回主页（横放时由 standbyActive 展示气象时钟）；快捷方式仍由 command 明确跳转。
                var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
                // 从首页五日卡片进入时携带的日期；null 表示按默认（今天）选中。
                var forecastDate by rememberSaveable { mutableStateOf<Long?>(null) }
                var cityPickedRequest by rememberSaveable { mutableIntStateOf(0) }
                val initialLocationSetupRequired: Boolean? by remember {
                    CityRepository.initialLocationSetupRequired.catch { error ->
                        // A storage error is not proof of a new installation.
                        android.util.Log.w("ZhishengWeather", "暂时无法读取首次地点选择状态", error)
                        emit(false)
                    }
                }.collectAsState(initial = null)
                var showLocationSetupAgain by rememberSaveable { mutableStateOf(false) }
                var locateImmediately by rememberSaveable { mutableStateOf(false) }
                var locationProviderWizard by rememberSaveable { mutableStateOf<com.zhisheng.weather.ui.ProviderWizardKind?>(null) }
                var locationSetupDismissedForSession by rememberSaveable { mutableStateOf(false) }
                var choosingInitialCity by rememberSaveable { mutableStateOf(false) }
                fun markInitialLocationHandled() {
                    scope.launch {
                        try {
                            CityRepository.completeInitialLocationSetup()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.util.Log.w("ZhishengWeather", "首次地点选择状态暂时未能保存", error)
                        }
                    }
                }
                val locationSetupHoldsStartup =
                    (initialLocationSetupRequired != false && screen == AppScreen.HOME) ||
                        showLocationSetupAgain || choosingInitialCity
                LaunchedEffect(initialLocationSetupRequired, startupPromptsSuppressed, screen,
                    locationSetupDismissedForSession) {
                    if (initialLocationSetupRequired == true && !startupPromptsSuppressed &&
                        screen == AppScreen.HOME && !locationSetupDismissedForSession) {
                        // 默认使用系统定位：不弹「先选一个天气地点」引导，直接静默申请权限并定位。
                        locateImmediately = true
                    }
                }
                val initialWhatsNew = remember { shouldShowWhatsNew() }
                var showWhatsNew by rememberSaveable(key = "notes_$WhatsNewVersion") { mutableStateOf(initialWhatsNew) }
                val previousNotesVersion = remember {
                    getSharedPreferences(WhatsNewPreferenceFile, MODE_PRIVATE).getString(WhatsNewSeenKey, null)
                }
                // The opening precedes the release notes: once automatically per version, and on every manual request.
                var autoOpeningPending by rememberSaveable(key = "opening_${BuildConfig.VERSION_CODE}") {
                    mutableStateOf(shouldAutoPlayUpdateOpening(BuildConfig.VERSION_CODE,
                        getSharedPreferences(UpdateOpeningPreferenceFile, MODE_PRIVATE).getInt(UpdateOpeningHandledKey, 0),
                        initialWhatsNew, systemAnimationsEnabled()))
                }
                var openingSpec by remember { mutableStateOf<UpdateOpeningSpec?>(null) }
                var openingHandedOff by remember { mutableStateOf(false) }
                var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
                var automaticUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
                val notices = remember { UpdateNotices(this@MainActivity) }
                val statistics = remember { UsageStatistics(this@MainActivity) }
                val diagnosticsEnabled by SettingsRepository.serviceDiagnostics.collectAsState(initial = false)
                LaunchedEffect(diagnosticsEnabled) {
                    if (diagnosticsEnabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        delay(15_000)
                        ServiceDiagnostics.report(this@MainActivity)
                        awaitCancellation()
                    }
                }
                val usageEnabled by SettingsRepository.usageStatistics.collectAsState(initial = false)
                LaunchedEffect(usageEnabled, showWhatsNew) {
                    if (usageEnabled && !showWhatsNew) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            delay(15_000)
                            statistics.register(true)
                            awaitCancellation()
                        }
                    }
                }
                val uiState by vm.uiState.collectAsState()
                LaunchedEffect(initialLocationSetupRequired, uiState.selectedCity?.locationKey, uiState.locating) {
                    if (initialLocationSetupRequired == false && uiState.selectedCity != null && !uiState.locating) {
                        // An explicit city from search or a widget also completes
                        // the guide, including when it superseded a GPS request.
                        showLocationSetupAgain = false
                        locationSetupDismissedForSession = true
                    }
                }
                val command by shortcutCommand.collectAsState()
                val landscapeStandby by SettingsRepository.landscapeStandby.collectAsState(initial = false)
                val landscapeStandbyStyle by SettingsRepository.landscapeStandbyStyle.collectAsState(
                    initial = LandscapeStandbyStyle.WEATHER_CORE,
                )
                val configuration = LocalConfiguration.current
                // Android's fullUser policy owns rotation, including the user's rotation lock.
                // This preference only chooses the content shown after the system rotates.
                val standbyActive = landscapeStandby &&
                    configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                // 横屏只在主页展示待机时钟；从时钟进入设置后仍保持横向，
                // 但恢复系统栏并展示完整设置，返回主页再回到待机界面。
                val standbyVisible = standbyActive && screen == AppScreen.HOME
                LaunchedEffect(availableUpdate, screen, standbyVisible, showWhatsNew, startupPromptsSuppressed, locationSetupHoldsStartup) {
                    val info = availableUpdate
                    if (info != null && screen == AppScreen.HOME && !standbyVisible && !showWhatsNew && !startupPromptsSuppressed && !locationSetupHoldsStartup) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            delay(3_000)
                            while (SystemClock.elapsedRealtime() - lastInteraction < 3_000) delay(500)
                            if (withContext(Dispatchers.IO) { notices.offer(info) }) automaticUpdate = info
                            awaitCancellation()
                        }
                    }
                }

                // 状态栏/导航栏图标颜色随主题切换（浅色主题 → 深色图标）
                val view = LocalView.current
                SideEffect {
                    applyEdgeToEdgeSystemBars()
                    androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = isLight
                        isAppearanceLightNavigationBars = isLight
                    }
                }
                LaunchedEffect(isLight, themeMode) {
                    getSharedPreferences(PREFS_SPLASH, MODE_PRIVATE).edit().putString("theme_mode", themeMode.key).apply()
                    persistSplashBackground(isLight)
                    // 天气娘是用户明确选择的独立图标，不参与主题联动。
                    // 深/浅图标则在 App 主题变化（含跟随系统）时同步切换。
                    val selectedIcon = SettingsRepository.appIconStyle.first()
                    if (selectedIcon != AppIconStyle.CHARACTER && selectedIcon != AppIconStyle.MINIMAL) {
                        val themedIcon = if (isLight) AppIconStyle.LIGHT else AppIconStyle.DARK
                        if (AppIconManager.apply(this@MainActivity, themedIcon) && selectedIcon != themedIcon) {
                            SettingsRepository.setAppIconStyle(themedIcon)
                        }
                    }
                }

                DisposableEffect(standbyVisible) {
                    val bars = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    // 仅横屏待机需要锁屏上显示/点亮屏幕；普通竖屏天气页不能长期持有
                    // SHOW_WHEN_LOCKED，否则部分三星、realme 在任务恢复时会越过锁屏露出页面。
                    @Suppress("DEPRECATION")
                    if (standbyVisible) {
                        window.addFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        )
                    } else {
                        window.clearFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        setShowWhenLocked(standbyVisible)
                        setTurnScreenOn(standbyVisible)
                    }
                    if (standbyVisible) {
                        bars.systemBarsBehavior =
                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    } else {
                        bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                    onDispose {
                        bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        @Suppress("DEPRECATION")
                        window.clearFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(false)
                            setTurnScreenOn(false)
                        }
                    }
                }

                LaunchedEffect(command.sequence) {
                    when (command.action) {
                        Intent.ACTION_MAIN -> {
                            screen = AppScreen.HOME
                            // onNewIntent can reuse an already resumed Activity, so
                            // a launcher re-entry must not rely solely on ON_RESUME.
                            vm.autoLocateIfEnabled()
                        }
                        ACTION_WIDGET_WEATHER -> {
                            screen = AppScreen.HOME
                            vm.preserveWidgetCityOnEntry()
                        }
                        ACTION_SEARCH -> screen = AppScreen.SEARCH
                        ACTION_SETTINGS -> {
                            screen = AppScreen.MOCHI_SETTINGS
                        }
                        ACTION_TYPHOON -> screen = if (ReleaseFeatures.typhoon) AppScreen.TYPHOON else AppScreen.HOME
                        ACTION_REFRESH -> {
                            screen = AppScreen.HOME
                            vm.refreshFromUser()
                        }
                    }
                }

                // 静默检测更新；提示由上方主页空闲和版本提醒策略决定。
                // 检测不自动下载。
                LaunchedEffect(Unit) {
                    for (attempt in 0..2) {
                        val result = if (attempt == 0) AppUpdate.check() else AppUpdate.check(refresh = true)
                        when (result) {
                            is AppUpdateCheck.Available -> {
                                availableUpdate = result.info
                                break
                            }
                            AppUpdateCheck.UpToDate -> {
                                availableUpdate = null
                                break
                            }
                            is AppUpdateCheck.Failed -> {
                                if (attempt < 2) delay(10_000)
                            }
                        }
                    }
                }

                // 常亮屏幕（设置项）
                val keepOn by SettingsRepository.keepScreenOn.collectAsState(initial = false)
                DisposableEffect(keepOn, standbyVisible) {
                    view.keepScreenOn = keepOn || standbyVisible
                    onDispose { view.keepScreenOn = false }
                }

                // 系统返回键、侧推手势、左上角返回按钮三处共用同一套返回语义。
                fun navigateBack() {
                    if (screen == AppScreen.SEARCH) choosingInitialCity = false
                    screen = AppScreen.HOME
                }
                BackHandler(enabled = screen != AppScreen.HOME) { navigateBack() }

                val automation by com.zhisheng.weather.data.AutomationSettingsRepository.settings.collectAsState()
                LaunchedEffect(automation.remindersEnabled, automation.morningMinute, automation.eveningMinute) {
                    if (automation.remindersEnabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            com.zhisheng.weather.data.WeatherReminderNotification.refresh(applicationContext)
                            delay(60_000L)
                        }
                    }
                }
                // The resumed scope is cancelled on background; no hidden foreground polling.
                LaunchedEffect(automation.foregroundMinutes, command.action) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            delay(automation.foregroundMinutes * 60_000L)
                            vm.refresh(force = false, intervalElapsed = true)
                            if (command.action != ACTION_WIDGET_WEATHER) vm.autoLocateIfEnabled()
                        }
                    }
                }
                // 每次打开 / 回到前台按所选间隔刷新；失败仅短暂退避。
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    vm.refresh(force = false)
                    // A fixed-city widget tap is an explicit city choice. Ordinary
                    // app entry still follows the saved location when enabled.
                    if (command.action != ACTION_WIDGET_WEATHER) vm.autoLocateIfEnabled()
                }

                val orientationState = rememberSaveableStateHolder()
                val motionEnabled = systemAnimationsEnabled()
                // UI 个性化偏好：同步到库全局效果状态，并驱动全站页面转场。
                val uiPrefs = rememberMochiUiPrefs()
                ApplyMochiUiPrefs(uiPrefs, motionEnabled)
                // 应用级导航走库的 MochiNavHost（与设置页内部同一条转场）：
                // 弹簧、下层页缩放、压暗、侧推与预测性返回都由库给，MainActivity 不再自拼覆盖层动画。
                val nav = rememberMochiNavigator(AppScreen.HOME.route)
                // screen 仍是应用导航的唯一事实（意图、小组件、页面回调都写它）；
                // 这里把它单向同步进库导航，库导航自己退回根（返回键 / 侧推手势）时再写回 screen。
                LaunchedEffect(Unit) {
                    snapshotFlow { screen }.collect { dest ->
                        val target = dest.route
                        if (nav.current != target) {
                            if (target == AppScreen.HOME.route) nav.popTo(0)
                            else {
                                // 应用是「单层覆盖」语义：换覆盖页先回根再推，
                                // 旧页滑走、新页推进来由库同时进行，不瞬切。
                                if (nav.backStack.size > 1) nav.popTo(0)
                                // 转场样式为「打开应用式」时，把首页来源元素（卡片 / 齿轮）的 key
                                // 交给库：页面从那张卡长成整页、返回缩回卡片；其余样式走库的侧推。
                                // 来源 key 与首页 mochiNavSource 的登记一一对应（含城市后缀），
                                // 没登记的元素库会退回原地缩放淡入，不会出错。
                                val from = if (
                                    PageTransitionStyle.from(uiPrefs.pageTransition) == PageTransitionStyle.APP
                                ) {
                                    dest.navSourceKey(uiState.selectedCity?.locationKey)
                                } else {
                                    null
                                }
                                nav.push(target, from = from)
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    snapshotFlow { nav.current }.collect { route ->
                        if (route == AppScreen.HOME.route && screen != AppScreen.HOME) {
                            if (screen == AppScreen.SEARCH) choosingInitialCity = false
                            screen = AppScreen.HOME
                        }
                    }
                }
                // 首页的氛围动效、共享元素与光晕在库导航转场期间冻结：
                // 否则返回首页的那一刻首页整体重排，会把退场动画挤成看不见的一帧。
                val homeSettled by remember { derivedStateOf { !nav.isTransitioning } }
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = standbyVisible,
                        transitionSpec = {
                            if (motionEnabled) {
                                screenTransition(
                                    motionEnabled,
                                    PageTransitionStyle.from(uiPrefs.pageTransition),
                                    uiPrefs.swipeBack,
                                )
                            } else {
                                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            }
                        },
                        label = "weather-orientation",
                    ) { landscape ->
                        CompositionLocalProvider(LocalWeatherContinuity provides
                            if (homeSettled) WeatherContinuity(this@SharedTransitionLayout, this) else null) {
                            orientationState.SaveableStateProvider(if (landscape) "landscape" else "portrait") {
                if (landscape) {
                    LandscapeStandbyScreen(
                        uiState = uiState,
                        style = landscapeStandbyStyle,
                        onRefresh = { vm.refreshFromUser() },
                        onSettings = {
                            screen = AppScreen.MOCHI_SETTINGS
                        },
                    )
                } else {
                    // 主屏与二级页全部是库导航的页面：返回首页 / 打开二级页的转场
                    // （弹簧、下层页缩放、压暗、侧推与预测性返回）由 MochiNavHost 统一处理，
                    // 与设置页内部二级页是同一套组件。主屏始终留在下层，避免拆掉
                    // WeatherContent 后温度、图标再播一遍交错入场。
                    val liveGlow = com.zhisheng.weather.ui.theme.LocalVistaGlowPhase.current
                    val frozenGlow = remember { mutableStateOf(0.0) }
                    MochiAppTheme(colors = rememberMochiColorScheme(), systemBarAppearance = false) {
                    CompositionLocalProvider(
                        com.zhisheng.weather.ui.theme.LocalVistaGlowPhase provides
                            if (homeSettled) liveGlow else frozenGlow,
                        LocalMochiSettingsEntry provides {
                            screen = AppScreen.MOCHI_SETTINGS
                        },
                    ) {
                    MochiNavHost(
                        nav,
                        spec = mochiNavSpecFor(uiPrefs),
                        predictiveBack = uiPrefs.predictiveBack && uiPrefs.swipeBack,
                        swipeBackEnabled = uiPrefs.swipeBack && motionEnabled,
                        // 首页天空是不间断动画、雷达页是原生地图视图：整页录进离屏图层给浮层玻璃
                        // 取样会每帧重录（原生视图甚至可能取样崩溃）。浮层玻璃退回各页自己的
                        // 正文来源，与迁移前的行为一致。
                        windowGlass = false,
                    ) { route ->
                        val dest = AppScreen.fromRoute(route)
                        // 提示状态包住每个页面（迁移前同样包在页面外层），页面里才能拿到
                        // LocalMochiToast，保存成功之类的提示不被吞掉。
                        CompositionLocalProvider(LocalMochiToast provides toastState) {
                        when (dest) {
                            AppScreen.HOME -> Box(Modifier.fillMaxSize().background(ZhishengBg)) {
                                HomeScreen(
                                    viewModel = vm,
                                    ambienceActive = homeSettled,
                                    dismissCitiesRequest = cityPickedRequest,
                                    onSearchClick = {
                                        if (initialLocationSetupRequired == true) {
                                            choosingInitialCity = true
                                            locationSetupDismissedForSession = true
                                            markInitialLocationHandled()
                                        }
                                        screen = AppScreen.SEARCH
                                    },
                                    onLocationSetupClick = {
                                        locateImmediately = true
                                        showLocationSetupAgain = true
                                        startupPromptsSuppressed = false
                                    },
                                    onSettingsClick = {
                                        screen = AppScreen.MOCHI_SETTINGS
                                    },
                                    onHistoryClick = { screen = AppScreen.HISTORY },
                                    onRadarClick = { if (ReleaseFeatures.radar) screen = AppScreen.RADAR },
                                    onDailyForecastClick = {
                                        forecastDate = null
                                        screen = AppScreen.DAILY_FORECAST
                                    },
                                    onOpenDailyDay = { millis ->
                                        forecastDate = millis
                                        screen = AppScreen.DAILY_DAY_DETAIL
                                    },
                                    onPrecipitationClick = { screen = AppScreen.PRECIPITATION },
                                    onHourlyClick = { screen = AppScreen.HOURLY_DETAIL },
                                    onTyphoonClick = { if (ReleaseFeatures.typhoon) screen = AppScreen.TYPHOON },
                                    onAirQualityClick = { screen = AppScreen.AIR_QUALITY },
                                    onYesterdayClick = { screen = AppScreen.YESTERDAY_DETAIL },
                                    onFeatureHubClick = { screen = AppScreen.FEATURES },
                                )
                            }
                                    AppScreen.MOCHI_SETTINGS -> Box(Modifier.fillMaxSize()) {
                                    MochiSettingsScreen(
                                        onBack = { navigateBack() },
                                        onLocate = { vm.locateCurrentCity() },
                                        locating = uiState.locating,
                                        locateMessage = uiState.locateMessage,
                                        onClearLocateMessage = { vm.clearLocateMessage() },
                                        activeSource = uiState.weather?.dataSource,
                                        activeWeather = uiState.weather,
                                        activeSupplementSources = uiState.weather?.let { weather ->
                                            weather.blockSources.values
                                                .filter { it.isNotBlank() && it != weather.dataSource }
                                                .distinct()
                                        }.orEmpty(),
                                        activeCityName = uiState.selectedCity?.name,
                                        activeCityLatitude = uiState.selectedCity?.latitude,
                                        activeCityLongitude = uiState.selectedCity?.longitude,
                                        sourceLoading = uiState.loading,
                                    )
                                    }
                                    AppScreen.DAILY_FORECAST -> DailyForecastScreen(
                                        city = uiState.selectedCity,
                                        days = uiState.weather?.currentAndFutureDaily(weatherPresentationTime()).orEmpty(),
                                        yesterday = uiState.weather?.yesterday,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { navigateBack() },
                                        hourly = uiState.weather?.hourly.orEmpty(),
                                        initialDateMillis = forecastDate,
                                    )
                                    AppScreen.DAILY_DAY_DETAIL -> com.zhisheng.weather.ui.DailyDayDetailScreen(
                                        city = uiState.selectedCity,
                                        days = uiState.weather?.currentAndFutureDaily(weatherPresentationTime()).orEmpty(),
                                        yesterday = uiState.weather?.yesterday,
                                        dateMillis = forecastDate
                                            ?: uiState.weather?.todayDaily(weatherPresentationTime())?.dateMillis
                                            ?: weatherPresentationTime(),
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        hourly = uiState.weather?.hourly.orEmpty(),
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.PRECIPITATION -> MochiPrecipitationDetailScreen(
                                        data = uiState.weather ?: com.zhisheng.weather.model.WeatherData(),
                                        cityName = uiState.selectedCity?.displayName,
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.HOURLY_DETAIL -> com.zhisheng.weather.ui.HourlyDetailScreen(
                                        data = uiState.weather ?: com.zhisheng.weather.model.WeatherData(),
                                        cityName = uiState.selectedCity?.displayName,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.HISTORY -> HistoryScreen(
                                        city = uiState.selectedCity,
                                        weather = uiState.weather,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.RADAR -> if (ReleaseFeatures.radar) RadarScreen(
                                        weather = uiState.weather,
                                        city = uiState.selectedCity,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.TYPHOON -> if (ReleaseFeatures.typhoon) TyphoonScreen(
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.AIR_QUALITY -> AirQualityScreen(
                                        weather = uiState.weather,
                                        cityName = uiState.selectedCity?.displayName,
                                        nearby = buildList {
                                            uiState.selectedCity?.let {
                                                add(NearbyCityAqi(it.displayName, uiState.weather?.aqi?.value, it.latitude, it.longitude))
                                            }
                                            uiState.cities.forEach { c ->
                                                if (c.locationKey != uiState.selectedCity?.locationKey) {
                                                    add(NearbyCityAqi(c.displayName, uiState.citySnapshots[c.locationKey]?.data?.aqi?.value, c.latitude, c.longitude))
                                                }
                                            }
                                        }.take(5),
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.FEATURES -> FeatureHubScreen(
                                        cityName = uiState.selectedCity?.displayName,
                                        latitude = uiState.selectedCity?.latitude ?: 0.0,
                                        longitude = uiState.selectedCity?.longitude ?: 0.0,
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.YESTERDAY_DETAIL -> com.zhisheng.weather.ui.YesterdayDetailScreen(
                                        yesterday = uiState.weather?.yesterday,
                                        today = uiState.weather?.todayDaily(weatherPresentationTime()),
                                        cityName = uiState.selectedCity?.displayName,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { navigateBack() },
                                    )
                                    AppScreen.SEARCH -> MochiSearchScreen(
                                        onCityPicked = { city: City ->
                                            vm.addCityAndSelect(city)
                                            cityPickedRequest++
                                            choosingInitialCity = false
                                            screen = AppScreen.HOME
                                        },
                                        onBack = {
                                            choosingInitialCity = false
                                            screen = AppScreen.HOME
                                        },
                                    )
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
                InitialLocationSetupHost(
                    viewModel = vm,
                    activity = this@MainActivity,
                    visible = ((initialLocationSetupRequired == true && !locationSetupDismissedForSession) ||
                        showLocationSetupAgain) && screen == AppScreen.HOME &&
                        (!startupPromptsSuppressed || showLocationSetupAgain) && !standbyVisible && openingSpec == null &&
                        !showWhatsNew && !autoOpeningPending && locationProviderWizard == null,
                    startImmediately = locateImmediately,
                    firstRun = initialLocationSetupRequired == true,
                    silent = !showLocationSetupAgain,
                    onConfigureProvider = { kind -> locationProviderWizard = kind },
                    onLocated = {
                        showLocationSetupAgain = false
                        locateImmediately = false
                        locationSetupDismissedForSession = true
                    },
                    onChooseCity = {
                        scope.launch {
                            SettingsRepository.setLocationEnabled(false)
                            com.zhisheng.weather.data.SecretStore.setSharedPlaceConsent(false)
                        }
                        locateImmediately = false
                        showLocationSetupAgain = false
                        locationSetupDismissedForSession = true
                        choosingInitialCity = true
                        screen = AppScreen.SEARCH
                        markInitialLocationHandled()
                    },
                    onDismiss = {
                        locateImmediately = false
                        showLocationSetupAgain = false
                        locationSetupDismissedForSession = true
                        markInitialLocationHandled()
                    },
                )
                locationProviderWizard?.let { kind ->
                    com.zhisheng.weather.ui.MochiProviderWizard(kind) {
                        locationProviderWizard = null
                        val ready = if (kind == com.zhisheng.weather.ui.ProviderWizardKind.AMAP)
                            com.zhisheng.weather.data.SecretStore.amapRuntime.ready else com.zhisheng.weather.data.SecretStore.baiduRuntime.ready
                        if (ready) scope.launch {
                            SettingsRepository.setDeveloperMode(true)
                            SettingsRepository.setLocationEnabled(true)
                            locateImmediately = true
                        }
                    }
                }
                LaunchedEffect(autoOpeningPending, showWhatsNew, hostResumed, startupPromptsSuppressed, locationSetupHoldsStartup) {
                    if (autoOpeningPending && showWhatsNew && hostResumed && !startupPromptsSuppressed) {
                        withContext(Dispatchers.IO) {
                            getSharedPreferences(UpdateOpeningPreferenceFile, MODE_PRIVATE).edit()
                                .putInt(UpdateOpeningHandledKey, BuildConfig.VERSION_CODE).commit()
                        }
                        openingHandedOff = false
                        openingSpec = updateOpeningSpec(BuildConfig.VERSION_NAME, previousNotesVersion,
                            upgrade = previousNotesVersion != null)
                        autoOpeningPending = false
                    }
                }
                val openingHoldsNotes = autoOpeningPending || (openingSpec != null && !openingHandedOff)
                openingSpec?.let { spec ->
                    androidx.compose.runtime.key(spec) {
                        UpdateOpeningOverlay(
                            spec = spec,
                            onHandoff = { openingHandedOff = true },
                            onFinished = { openingSpec = null },
                        )
                    }
                }
                automaticUpdate?.takeIf { hostResumed && !startupPromptsSuppressed && openingSpec == null && !locationSetupHoldsStartup }?.let { info ->
                    AppUpdateDialog(initialInfo = info,
                        onDeclineVersion = {
                            automaticUpdate = null
                            scope.launch(Dispatchers.IO) { notices.dismiss(info) }
                        },
                        onClose = { automaticUpdate = null })
                }
                if (showWhatsNew && hostResumed && !startupPromptsSuppressed && !openingHoldsNotes) {
                    WhatsNewDialog(
                        onClose = {
                            markWhatsNewSeen()
                            showWhatsNew = false
                        },
                    )
                }
                com.zhisheng.weather.ui.AnnouncementHost(
                    lifecycle = lifecycle,
                    canPresent = hostResumed && screen == AppScreen.HOME && !standbyVisible &&
                        !startupPromptsSuppressed && openingSpec == null && !autoOpeningPending &&
                        !showWhatsNew && !locationSetupHoldsStartup && automaticUpdate == null,
                )
            }
        }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val iconEntry = isStartupIconEntry(intent.action,
            intent.hasCategory(Intent.CATEGORY_LAUNCHER), isTaskRoot, false,
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0)
        startupPromptsSuppressed = com.zhisheng.weather.ui.suppressStartupPrompts(iconEntry, intent.action, shouldShowWhatsNew())
        setIntent(intent)
        dispatchShortcut(intent)
    }

    private fun dispatchShortcut(intent: Intent?) {
        // 最近任务恢复不能重放曾经点击过的“搜索城市”快捷命令。
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val action = intent.action
        if (action == Intent.ACTION_MAIN || action == ACTION_SEARCH || action == ACTION_SETTINGS || action == ACTION_REFRESH || action == ACTION_TYPHOON || action == ACTION_WIDGET_WEATHER) {
            shortcutCommand.value = ShortcutCommand(action, ++shortcutSequence)
            setIntent(Intent(intent).setAction(Intent.ACTION_MAIN))
        }
    }

    private fun initialThemeMode(): ThemeMode {
        val prefs = getSharedPreferences(PREFS_SPLASH, MODE_PRIVATE)
        return prefs.getString("theme_mode", null)?.let(ThemeMode::from)
            ?: if (prefs.getBoolean(KEY_SPLASH_LIGHT, true)) ThemeMode.LIGHT else ThemeMode.DARK
    }
    private fun applySplashBackground() {
        val light = when (initialThemeMode()) {
            ThemeMode.LIGHT -> true
            ThemeMode.DARK -> false
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawableResource(if (light) R.color.splash_light else R.color.splash_dark)
    }

    private fun persistSplashBackground(light: Boolean) {
        getSharedPreferences(PREFS_SPLASH, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SPLASH_LIGHT, light)
            .apply()
        val color = if (light) R.color.splash_light else R.color.splash_dark
        window.setBackgroundDrawableResource(color)
    }

    @Suppress("DEPRECATION")
    private fun applyEdgeToEdgeSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 禁止系统为手势区强加不透明对比底色，让天气背景与氛围层连续延伸到底部。
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun shouldShowWhatsNew(): Boolean =
        getSharedPreferences(WhatsNewPreferenceFile, MODE_PRIVATE)
            .getString(WhatsNewSeenKey, null) != WhatsNewVersion

    private fun markWhatsNewSeen() {
        getSharedPreferences(WhatsNewPreferenceFile, MODE_PRIVATE)
            .edit()
            .putString(WhatsNewSeenKey, WhatsNewVersion)
            .apply()
    }

    private companion object {
        const val ACTION_WIDGET_WEATHER = "com.zhisheng.weather.action.WIDGET_WEATHER"
        const val ACTION_REFRESH = "com.zhisheng.weather.action.REFRESH"
        const val ACTION_SEARCH = "com.zhisheng.weather.action.SEARCH"
        const val ACTION_SETTINGS = "com.zhisheng.weather.action.SETTINGS"
        const val ACTION_TYPHOON = "com.zhisheng.weather.action.TYPHOON"
        const val PREFS_SPLASH = "zhisheng_splash"
        const val KEY_SPLASH_LIGHT = "light"
    }
}
