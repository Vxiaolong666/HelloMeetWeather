@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.HelloMeet.weather

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
import com.HelloMeet.weather.ui.theme.ZhishengBg
import com.HelloMeet.weather.ui.components.LocalWeatherContinuity
import com.HelloMeet.weather.ui.components.WeatherContinuity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.awaitCancellation
import com.HelloMeet.weather.data.UsageStatistics
import com.HelloMeet.weather.data.ServiceDiagnostics
import com.HelloMeet.weather.data.UpdateNotices
import com.HelloMeet.weather.ui.AppUpdateDialog
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import ui.feice.mochi.basic.rememberMochiToastState
import com.HelloMeet.weather.data.AccentTone
import com.HelloMeet.weather.data.AppIconManager
import com.HelloMeet.weather.data.AppUpdate
import com.HelloMeet.weather.data.AppUpdateCheck
import com.HelloMeet.weather.data.AppUpdateInfo
import com.HelloMeet.weather.data.LandscapeStandbyStyle
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.data.CityRepository
import com.HelloMeet.weather.data.ReleaseFeatures
import com.HelloMeet.weather.ui.isStartupIconEntry
import com.HelloMeet.weather.data.ThemeMode
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.ui.AppScreen
import com.HelloMeet.weather.ui.MochiAppTheme
import com.HelloMeet.weather.ui.rememberMochiColorScheme
import com.HelloMeet.weather.ui.mochiNavSpecFor
import com.HelloMeet.weather.ui.appTransitionsEnabled
import com.HelloMeet.weather.ui.SearchScreen
import com.HelloMeet.weather.ui.WeatherViewModel
import com.HelloMeet.weather.ui.ProvideWeatherPresentationClock
import com.HelloMeet.weather.ui.weatherPresentationTime
import com.HelloMeet.weather.ui.home.HomeScreen
import com.HelloMeet.weather.ui.home.AtlasPage
import com.HelloMeet.weather.ui.home.SceneAtlasPage
import com.HelloMeet.weather.ui.home.CityManagerScreen
import com.HelloMeet.weather.ui.MochiSettingsScreen
import com.HelloMeet.weather.ui.LocalMochiSettingsEntry
import com.HelloMeet.weather.ui.MochiPrecipitationDetailScreen
import com.HelloMeet.weather.ui.MochiSearchScreen
import com.HelloMeet.weather.ui.AtmosphereLabScreen
import com.HelloMeet.weather.ui.LocalMochiToast
import com.HelloMeet.weather.ui.MochiAppToastHost
import com.HelloMeet.weather.ui.PageTransitionStyle
import com.HelloMeet.weather.ui.rememberMochiUiPrefs
import com.HelloMeet.weather.ui.ApplyMochiUiPrefs
import com.HelloMeet.weather.ui.AppTransitions
import com.HelloMeet.weather.ui.LandscapeStandbyScreen
import com.HelloMeet.weather.ui.HistoryScreen
import com.HelloMeet.weather.ui.DailyForecastScreen
import com.HelloMeet.weather.ui.DailyTrendScreen
import com.HelloMeet.weather.ui.Hourly48Screen
import com.HelloMeet.weather.ui.RadarScreen
import com.HelloMeet.weather.ui.TyphoonScreen
import com.HelloMeet.weather.ui.AirQualityScreen
import com.HelloMeet.weather.ui.WeatherDetailScreen
import com.HelloMeet.weather.ui.FeatureHubScreen
import com.HelloMeet.weather.ui.NearbyCityAqi
import com.HelloMeet.weather.ui.UpdateOpeningHandledKey
import com.HelloMeet.weather.ui.UpdateOpeningOverlay
import com.HelloMeet.weather.ui.UpdateOpeningPreferenceFile
import com.HelloMeet.weather.ui.UpdateOpeningSpec
import com.HelloMeet.weather.ui.WhatsNewDialog
import com.HelloMeet.weather.ui.shouldAutoPlayUpdateOpening
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import ui.feice.mochi.anim.MochiSprings
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.theme.MochiDefaults
import androidx.compose.animation.core.tween
import com.HelloMeet.weather.ui.systemAnimationsEnabled
import com.HelloMeet.weather.ui.updateOpeningSpec
import com.HelloMeet.weather.ui.WhatsNewPreferenceFile
import com.HelloMeet.weather.ui.WhatsNewSeenKey
import com.HelloMeet.weather.ui.WhatsNewVersion
import com.HelloMeet.weather.ui.InitialLocationSetupHost
import com.HelloMeet.weather.ui.theme.ZhishengWeatherTheme
import com.HelloMeet.weather.data.InterfaceStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private data class ShortcutCommand(val action: String? = null, val sequence: Long = 0L)

// 问题4：城市管理页选城时的扩散请求——被点卡片的屏幕矩形 + 目标城市 key。
private data class CityExpandRequest(val rect: Rect, val cityKey: String)

/**
 * 停用全屏「应用内右滑返回」的页面：它们的正文有整片的横向拖动内容，与右滑返回手势
 * 互相抢事件（和台风页的原生地图同一个冲突）。48 小时天气、多日天气预报、富逐日详情
 * 的图表卡片都是横向滚动的 Canvas，右滑返回会把图表的横向拖动抢走、导致图表无法左右
 * 滑动；停用后顶栏返回按钮与系统返回键照常退出（见 AGENTS.md 统一返回语义）。
 */
private val noSwipeBackScreens = setOf(
    AppScreen.TYPHOON,
    AppScreen.HOURLY_48,
    AppScreen.DAILY_TREND,
    AppScreen.DAILY_TREND_DAY_DETAIL,
)

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
        startupPromptsSuppressed = com.HelloMeet.weather.ui.suppressStartupPrompts(iconEntry, intent?.action, shouldShowWhatsNew())
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
            val softGlowLevel by SettingsRepository.softGlowLevel.collectAsState(initial = com.HelloMeet.weather.data.SoftGlowLevel.SUBTLE)
            val interfaceStyle by SettingsRepository.interfaceStyle.collectAsState(
                initial = InterfaceStyle.PHOSPHOR_VISTA,
            )
            val homeSurfaceStyle by SettingsRepository.homeSurfaceStyle.collectAsState(
                initial = com.HelloMeet.weather.data.HomeSurfaceStyle.CURRENT,
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
                var locationProviderWizard by rememberSaveable { mutableStateOf<com.HelloMeet.weather.ui.ProviderWizardKind?>(null) }
                var locationSetupDismissedForSession by rememberSaveable { mutableStateOf(false) }
                var choosingInitialCity by rememberSaveable { mutableStateOf(false) }
                // 返回首页的唯一入口：左上角返回按钮、系统返回键 / 手势、应用内右滑、
                // 搜索页选城 / 返回、快捷方式，以及库导航自己退回根，都写回同一个 screen。
                // 关闭动画不在各入口单独处理，统一由 MochiNavHost 按当前「应用转场」设置执行。
                fun returnHome() {
                    if (screen == AppScreen.SEARCH) choosingInitialCity = false
                    screen = AppScreen.HOME
                }
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
                            returnHome()
                            // onNewIntent can reuse an already resumed Activity, so
                            // a launcher re-entry must not rely solely on ON_RESUME.
                            vm.autoLocateIfEnabled()
                        }
                        ACTION_WIDGET_WEATHER -> {
                            returnHome()
                            vm.preserveWidgetCityOnEntry()
                        }
                        ACTION_SEARCH -> screen = AppScreen.SEARCH
                        ACTION_SETTINGS -> {
                            screen = AppScreen.MOCHI_SETTINGS
                        }
                        ACTION_TYPHOON -> if (ReleaseFeatures.typhoon) screen = AppScreen.TYPHOON else returnHome()
                        ACTION_REFRESH -> {
                            returnHome()
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
                BackHandler(enabled = screen != AppScreen.HOME) { returnHome() }

                val automation by com.HelloMeet.weather.data.AutomationSettingsRepository.settings.collectAsState()
                LaunchedEffect(automation.remindersEnabled, automation.morningMinute, automation.eveningMinute) {
                    if (automation.remindersEnabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (true) {
                            com.HelloMeet.weather.data.WeatherReminderNotification.refresh(applicationContext)
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
                val appStyle = PageTransitionStyle.from(uiPrefs.pageTransition)
                // LaunchedEffect(Unit) 的收集协程只创建一次，直接引用组合期 val 会把样式
                // 永远冻结在首帧（首帧偏好还没从磁盘读回来，兜底值会覆盖用户真实设置）；
                // 用 rememberUpdatedState 让每次 push 实时读当前样式。
                val currentAppStyle by rememberUpdatedState(appStyle)
                // 「无动画」或系统关掉动画时，页面弹簧 / 压暗与所有返回手势一起关掉。
                // 偏好一变就重组，下一次打开或返回立即采用新设置。
                val transitionsEnabled = appTransitionsEnabled(appStyle, motionEnabled)
                // 应用级导航走库的 MochiNavHost（与设置页内部同一条转场）：
                // 弹簧、下层页缩放、压暗、侧推与预测性返回都由库给，MainActivity 不再自拼覆盖层动画。
                val nav = rememberMochiNavigator(AppScreen.HOME.route)
                // 城市管理页「点城市卡 → 卡片从页面中部扩散长成整屏首页」的请求（问题4）。
                var cityExpand by remember { mutableStateOf<CityExpandRequest?>(null) }
                // 富逐日详情由「多日天气预报」页的某张「查看该日完整详情」卡片进入：
                // 「打开应用式」转场用按天登记的来源 key（与 DailyTrendScreen 的 mochiNavSource
                // 一致），返回时缩回那张卡片；读 state 所以每次调用取当前选中日。
                // 此处位于协程内不能调 @Composable 的 weatherPresentationTime()，
                // 用真实时钟兜底（forecastDate 进入时必已设置，兜底仅在异常时命中）。
                fun trendDayDetailFrom(style: PageTransitionStyle): String? {
                    if (style != PageTransitionStyle.APP) return null
                    val now = System.currentTimeMillis()
                    val date = forecastDate
                        ?: uiState.weather?.todayDaily(now)?.dateMillis
                        ?: now
                    return AppTransitions.dailyTrendDaySourceKey(date, uiState.selectedCity?.locationKey)
                }
                // screen 仍是应用导航的唯一事实（意图、小组件、页面回调都写它）；
                // 这里把它单向同步进库导航，库导航自己退回根（返回键 / 侧推手势）时再写回 screen。
                LaunchedEffect(Unit) {
                    snapshotFlow { screen }.collect { dest ->
                        val target = dest.route
                        if (nav.current != target) {
                            if (target == AppScreen.HOME.route) nav.popTo(0)
                            else {
                                // 富逐日详情是唯一嵌套页：从「多日天气预报」进入时保留父页，
                                // 库栈为 [HOME, DAILY_TREND, 富页]，返回（系统返回 / 返回按钮 /
                                // 应用内右滑）退到「多日天气预报」而非首页；其余页面仍是
                                // 「单层覆盖」语义：换覆盖页先回根再推，旧页滑走、新页推进来
                                // 由库同时进行，不瞬切。
                                val nested = target == AppScreen.DAILY_TREND_DAY_DETAIL.route &&
                                    nav.current == AppScreen.DAILY_TREND.route
                                if (!nested && nav.backStack.size > 1) nav.popTo(0)
                                // 转场样式为「打开应用式」时，把来源元素（卡片 / 齿轮 / 按天详情卡）
                                // 的 key 交给库：页面从那张卡长成整页、返回缩回卡片；没有来源卡片的
                                // 页面兜底成角落哨兵 key，走「从角落小卡展开 / 缩回小卡」；其余样式
                                // 走库的侧推。来源 key 与各页 mochiNavSource 的登记一一对应（含城市后缀）。
                                val from = if (dest == AppScreen.DAILY_TREND_DAY_DETAIL) {
                                    trendDayDetailFrom(currentAppStyle)
                                } else {
                                    AppTransitions.pushFrom(
                                        currentAppStyle, dest, uiState.selectedCity?.locationKey,
                                    )
                                }
                                nav.push(target, from = from)
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    snapshotFlow { nav.current }.collect { route ->
                        // 库导航自己退回根（系统返回手势 / 应用内右滑 / 返回按钮触发的 pop）
                        // 也写回统一状态，保证 screen 与导航栈始终一致。
                        if (route == AppScreen.HOME.route && screen != AppScreen.HOME) returnHome()
                        // 富逐日详情由「多日天气预报」嵌套进入：库返回退到多日页（而非首页），
                        // 把 screen 同步回去，与 UI 返回按钮走同一条库 pop 语义。
                        else if (route == AppScreen.DAILY_TREND.route && screen == AppScreen.DAILY_TREND_DAY_DETAIL) {
                            screen = AppScreen.DAILY_TREND
                        }
                    }
                }
                // 「应用转场」设置一变，已打开页面的关闭形态立刻跟着变：之后无论从哪个
                // 入口返回首页（返回按钮、系统返回手势、应用内右滑、快捷方式），关闭动画
                // 都按当前设置执行，不会沿用进入页面时的旧样式。
                LaunchedEffect(appStyle) {
                    if (screen != AppScreen.HOME) {
                        nav.setTopSource(
                            if (screen == AppScreen.DAILY_TREND_DAY_DETAIL) {
                                trendDayDetailFrom(appStyle)
                            } else {
                                AppTransitions.pushFrom(appStyle, screen, uiState.selectedCity?.locationKey)
                            },
                        )
                    }
                }
                // 首页的氛围动效、共享元素与光晕在库导航转场期间冻结：
                // 否则返回首页的那一刻首页整体重排，会把退场动画挤成看不见的一帧。
                val homeSettled by remember { derivedStateOf { !nav.isTransitioning } }
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = standbyVisible,
                        transitionSpec = {
                            if (transitionsEnabled) {
                                AppTransitions.contentTransform(motionEnabled, appStyle, uiPrefs.swipeBack)
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
                    val liveGlow = com.HelloMeet.weather.ui.theme.LocalVistaGlowPhase.current
                    val frozenGlow = remember { mutableStateOf(0.0) }
                    MochiAppTheme(colors = rememberMochiColorScheme(), systemBarAppearance = false) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        com.HelloMeet.weather.ui.theme.LocalVistaGlowPhase provides
                            if (homeSettled) liveGlow else frozenGlow,
                        LocalMochiSettingsEntry provides {
                            screen = AppScreen.MOCHI_SETTINGS
                        },
                    ) {
                    MochiNavHost(
                        nav,
                        spec = mochiNavSpecFor(uiPrefs, motionEnabled),
                        // 「无动画」时系统预测性返回与应用内右滑一起关掉：
                        // 否则手指仍会跟手拖出一段返回动画。
                        predictiveBack = transitionsEnabled && uiPrefs.predictiveBack && uiPrefs.swipeBack,
                        // 台风页整页是原生地图：地图横向拖动与应用内右滑返回互相抢事件，
                        // 台风页停用应用内右滑返回；顶栏返回按钮与系统返回键照常退出。
                        // 48 小时天气 / 多日天气预报 / 富逐日详情的图表卡片也是横向滑动：
                        // 全屏右滑返回会与图表的横向拖动抢事件，导致图表无法左右滑动，
                        // 同样停用应用内右滑返回（见 noSwipeBackScreens）。
                        swipeBackEnabled = transitionsEnabled && uiPrefs.swipeBack &&
                            AppScreen.fromRoute(nav.current) !in noSwipeBackScreens,
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
                                    onDailyMoreClick = {
                                        forecastDate = null
                                        screen = AppScreen.DAILY_TREND
                                    },
                                    onOpenDailyDay = { millis ->
                                        forecastDate = millis
                                        screen = AppScreen.DAILY_DAY_DETAIL
                                    },
                                    onPrecipitationClick = { screen = AppScreen.PRECIPITATION },
                                    onHourlyClick = { screen = AppScreen.HOURLY_DETAIL },
                                    onHourly48Click = { screen = AppScreen.HOURLY_48 },
                                    onTyphoonClick = { if (ReleaseFeatures.typhoon) screen = AppScreen.TYPHOON },
                                    onAirQualityClick = { screen = AppScreen.AIR_QUALITY },
                                    onWeatherDetailClick = { screen = AppScreen.WEATHER_DETAIL },
                                    onYesterdayClick = { screen = AppScreen.YESTERDAY_DETAIL },
                                    onFeatureHubClick = { screen = AppScreen.FEATURES },
                                    onCityManagerClick = { screen = AppScreen.CITY_MANAGER },
                    // 三个场景页（霞光时刻 / 星空拍摄 / 海岸气象）从首页瓦片走统一路由，不再用页内自开窗口覆盖层。
                    onScenePage = { page ->
                        screen = when (page) {
                            AtlasPage.GLOW -> AppScreen.SCENE_GLOW
                            AtlasPage.STARS -> AppScreen.SCENE_STARS
                            AtlasPage.COAST -> AppScreen.SCENE_COAST
                            AtlasPage.DARK -> screen
                        }
                    },
                                )
                            }
                                    AppScreen.MOCHI_SETTINGS -> Box(Modifier.fillMaxSize()) {
                                    MochiSettingsScreen(
                                        onBack = { returnHome() },
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
                                        onBack = { returnHome() },
                                        hourly = uiState.weather?.hourly.orEmpty(),
                                        initialDateMillis = forecastDate,
                                    )
                                    AppScreen.DAILY_TREND -> DailyTrendScreen(
                                        city = uiState.selectedCity,
                                        days = uiState.weather?.currentAndFutureDaily(weatherPresentationTime()).orEmpty(),
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { returnHome() },
                                        onOpenDay = { millis ->
                                            forecastDate = millis
                                            screen = AppScreen.DAILY_TREND_DAY_DETAIL
                                        },
                                    )
                                    AppScreen.DAILY_TREND_DAY_DETAIL -> com.HelloMeet.weather.ui.DailyTrendDayDetailScreen(
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
                                        // 富逐日详情由「多日天气预报」嵌套进入：返回走库 pop，退到
                                        // 多日天气预报页（系统返回 / 返回按钮 / 应用内右滑同一条语义）；
                                        // nav.current 观察者把 screen 同步回 DAILY_TREND。
                                        onBack = { nav.pop() },
                                    )
                                    AppScreen.DAILY_DAY_DETAIL -> com.HelloMeet.weather.ui.DailyDayDetailScreen(
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
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.PRECIPITATION -> MochiPrecipitationDetailScreen(
                                        data = uiState.weather ?: com.HelloMeet.weather.model.WeatherData(),
                                        cityName = uiState.selectedCity?.displayName,
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.HOURLY_DETAIL -> com.HelloMeet.weather.ui.HourlyDetailScreen(
                                        data = uiState.weather ?: com.HelloMeet.weather.model.WeatherData(),
                                        cityName = uiState.selectedCity?.displayName,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.HOURLY_48 -> Hourly48Screen(
                                        city = uiState.selectedCity,
                                        hours = com.HelloMeet.weather.ui.detailHourly48(uiState.weather, weatherPresentationTime()),
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.HISTORY -> HistoryScreen(
                                        city = uiState.selectedCity,
                                        weather = uiState.weather,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.RADAR -> if (ReleaseFeatures.radar) RadarScreen(
                                        weather = uiState.weather,
                                        city = uiState.selectedCity,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.TYPHOON -> if (ReleaseFeatures.typhoon) TyphoonScreen(
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.AIR_QUALITY -> AirQualityScreen(
                                        weather = uiState.weather,
                                        cityName = uiState.selectedCity?.displayName,
                                        // 全国排名按「城市」发布，街道级 displayName 匹配不到，单独传城市名。
                                        rankingCity = uiState.selectedCity?.name,
                                        rankingAffiliation = uiState.selectedCity?.affiliation,
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
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.WEATHER_DETAIL -> WeatherDetailScreen(
                                        weather = uiState.weather,
                                        city = uiState.selectedCity,
                                        prefs = uiState.prefs,
                                        tempUnit = uiState.tempUnit,
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
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.FEATURES -> FeatureHubScreen(
                                        cityName = uiState.selectedCity?.displayName,
                                        latitude = uiState.selectedCity?.latitude ?: 0.0,
                                        longitude = uiState.selectedCity?.longitude ?: 0.0,
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.SCENE_GLOW, AppScreen.SCENE_STARS, AppScreen.SCENE_COAST ->
                                        SceneAtlasPage(
                                            page = when (dest) {
                                                AppScreen.SCENE_STARS -> AtlasPage.STARS
                                                AppScreen.SCENE_COAST -> AtlasPage.COAST
                                                else -> AtlasPage.GLOW
                                            },
                                            scene = uiState.sceneWeather,
                                            city = uiState.selectedCity,
                                            unit = uiState.tempUnit,
                                            onBack = { returnHome() },
                                        )
                                    AppScreen.CITY_MANAGER -> CityManagerScreen(
                                        uiState = uiState,
                                        onBack = { returnHome() },
                                        onSelect = { key ->
                                            vm.selectCity(key)
                                            returnHome()
                                        },
                                        // 「打开应用式」下选城：先记下卡片矩形，由应用根部遮罩播扩散变换，
                                        // 遮罩铺满全屏后再在底下切城回首页；其余样式保持原来的直接切换。
                                        onSelectAt = { key, rect ->
                                            if (currentAppStyle == PageTransitionStyle.APP && motionEnabled) {
                                                cityExpand = CityExpandRequest(rect, key)
                                            } else {
                                                vm.selectCity(key)
                                                returnHome()
                                            }
                                        },
                                        onToggleFavorite = { key -> vm.toggleCityFavorite(key) },
                                        onRemove = { key -> vm.removeCity(key) },
                                        onLocate = { vm.locateCurrentCity() },
                                        onClearLocateMessage = { vm.clearLocateMessage() },
                                        onAddCity = {
                                            if (initialLocationSetupRequired == true) {
                                                choosingInitialCity = true
                                                locationSetupDismissedForSession = true
                                                markInitialLocationHandled()
                                            }
                                            screen = AppScreen.SEARCH
                                        },
                                    )
                                    AppScreen.YESTERDAY_DETAIL -> com.HelloMeet.weather.ui.YesterdayDetailScreen(
                                        yesterday = uiState.weather?.yesterday,
                                        today = uiState.weather?.todayDaily(weatherPresentationTime()),
                                        cityName = uiState.selectedCity?.displayName,
                                        tempUnit = uiState.tempUnit,
                                        windUnit = uiState.prefs.windUnit,
                                        utcOffsetSeconds = uiState.weather?.utcOffsetSeconds,
                                        onBack = { returnHome() },
                                    )
                                    AppScreen.SEARCH -> MochiSearchScreen(
                                        onCityPicked = { city: City ->
                                            vm.addCityAndSelect(city)
                                            cityPickedRequest++
                                            returnHome()
                                        },
                                        onBack = { returnHome() },
                                    )
                        }
                        }
                    }
                    }
                }
                // 问题4：城市管理页「点城市卡 → 卡片从页面中部扩散长成整屏首页」。
                // 库的 pop 只支持「顶层页缩回下层来源卡」，不支持从顶层页内的来源长成整屏，
                // 所以「打开应用式」下在应用根部自绘反向容器变换：卡片矩形按与库同一条弹簧
                // （MochiSprings.smooth(300)）插值铺满全屏，遮罩在底下切城回首页，
                // 等库的返回动画收尾后遮罩淡出露出首页，两段动画不叠影。
                cityExpand?.let { request ->
                    val expand = remember(request) { Animatable(0f) }
                    val reveal = remember(request) { Animatable(1f) }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val screenW = with(density) { maxWidth.toPx() }
                        val screenH = with(density) { maxHeight.toPx() }
                        LaunchedEffect(request) {
                            expand.animateTo(1f, MochiSprings.smooth(300, visibilityThreshold = 0.0005f))
                            vm.selectCity(request.cityKey)
                            returnHome()
                            // returnHome 要等导航协程下一帧才真正 pop，先等动画开始再等它结束；
                            // 加超时兜底，万一没走到动画也不能把遮罩永远盖在首页上。
                            withTimeoutOrNull(1200L) {
                                snapshotFlow { nav.isTransitioning }.first { it }
                                snapshotFlow { homeSettled }.first { settled -> settled }
                            }
                            reveal.animateTo(0f, tween(220))
                            cityExpand = null
                        }
                        val p = expand.value
                        val left = request.rect.left * (1f - p)
                        val top = request.rect.top * (1f - p)
                        val width = request.rect.width + (screenW - request.rect.width) * p
                        val height = request.rect.height + (screenH - request.rect.height) * p
                        Box(
                            Modifier
                                .offset { IntOffset(left.toInt().coerceAtLeast(0), top.toInt().coerceAtLeast(0)) }
                                .size(width = (width / density.density).dp, height = (height / density.density).dp)
                                .pointerInput(request) { detectTapGestures { } }
                                .clip(RoundedCornerShape(26.dp * (1f - p)))
                                .background(MochiDefaults.groupedContainer().copy(alpha = reveal.value)),
                        )
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
                            com.HelloMeet.weather.data.SecretStore.setSharedPlaceConsent(false)
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
                    com.HelloMeet.weather.ui.MochiProviderWizard(kind) {
                        locationProviderWizard = null
                        val ready = if (kind == com.HelloMeet.weather.ui.ProviderWizardKind.AMAP)
                            com.HelloMeet.weather.data.SecretStore.amapRuntime.ready else com.HelloMeet.weather.data.SecretStore.baiduRuntime.ready
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
                com.HelloMeet.weather.ui.AnnouncementHost(
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
        startupPromptsSuppressed = com.HelloMeet.weather.ui.suppressStartupPrompts(iconEntry, intent.action, shouldShowWhatsNew())
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
        const val ACTION_WIDGET_WEATHER = "com.HelloMeet.weather.action.WIDGET_WEATHER"
        const val ACTION_REFRESH = "com.HelloMeet.weather.action.REFRESH"
        const val ACTION_SEARCH = "com.HelloMeet.weather.action.SEARCH"
        const val ACTION_SETTINGS = "com.HelloMeet.weather.action.SETTINGS"
        const val ACTION_TYPHOON = "com.HelloMeet.weather.action.TYPHOON"
        const val PREFS_SPLASH = "zhisheng_splash"
        const val KEY_SPLASH_LIGHT = "light"
    }
}
