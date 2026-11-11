package com.zhisheng.weather.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zhisheng.weather.data.AutomationSettings
import com.zhisheng.weather.data.AutomationSettingsRepository
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.ReminderPanelStatus
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.WeatherReminderNotification
import com.zhisheng.weather.data.WeatherReminderSchedule
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.widget.WidgetRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection

/**
 * 「刷新与天气提醒」的 mochi 版独立二级页：整页用库组件重写，行为与旧 AutomationSettingsPanel 一致
 * （即点即存、通知权限申请、提醒状态读取、立即检查 / 测试通知 / 系统设置跳转）。
 */
@Composable
internal fun MochiAutomationSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AutomationSettingsRepository.settings.collectAsState()
    val city by CityRepository.selectedCity.collectAsState(initial = null)
    val source by SettingsRepository.sourcePref.collectAsState(initial = SourcePref.AUTO)
    val revision by WeatherReminderNotification.revision.collectAsState()

    fun access(): ReminderNotificationAccess {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return ReminderNotificationAccess.APP_BLOCKED
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        return if (manager?.getNotificationChannel(WeatherReminderNotification.CHANNEL)?.importance ==
            NotificationManager.IMPORTANCE_NONE
        ) ReminderNotificationAccess.CHANNEL_BLOCKED else ReminderNotificationAccess.ALLOWED
    }

    var notificationAccess by rememberSaveable { mutableStateOf(access()) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<ReminderPanelStatus?>(null) }
    var checking by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTime by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingForeground by rememberSaveable { mutableStateOf(false) }
    var pickingBackground by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings, city, source, revision, notificationAccess) {
        try {
            status = WeatherReminderNotification.readStatus(context.applicationContext)
            statusError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = null
            statusError = "暂时无法读取提醒状态，请点击立即检查重试"
        }
    }

    fun checkAccess() {
        val next = access()
        if (ReminderPermissionPolicy.refreshOnRestore(
                notificationAccess == ReminderNotificationAccess.ALLOWED,
                next == ReminderNotificationAccess.ALLOWED,
                AutomationSettingsRepository.read(context).remindersEnabled,
            )
        ) {
            WidgetRefresh.request(context.applicationContext)
            WeatherReminderSchedule.request(context.applicationContext)
            WeatherReminderSchedule.schedule(context.applicationContext)
        }
        notificationAccess = next
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checkAccess() }
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { checkAccess() }

    fun onChange(next: AutomationSettings) {
        val previous = AutomationSettingsRepository.read(context)
        AutomationSettingsRepository.save(context, next)
        if (Build.VERSION.SDK_INT >= 33 && ReminderPermissionPolicy.requestOnChange(
                previous, next, NotificationManagerCompat.from(context).areNotificationsEnabled()
            )
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openNotificationSettings() {
        actionError = null
        val intent = reminderNotificationSettingsIntent(access(), context.packageName)
        if (runCatching { context.startActivity(intent) }.isFailure && runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }.isFailure
        ) actionError = "暂时无法打开系统设置，请在系统设置中允许天气通知"
    }

    fun openBackgroundSettings() {
        if (runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }.isFailure
        ) actionError = "请在系统应用设置中允许枳生天气后台运行"
    }

    MochiSettingsScaffold(
        title = "刷新与天气提醒",
        subtitle = "前台/后台刷新与早、晚、降水提醒",
    ) {
        item {
            MochiListSection(header = "天气刷新", footer = "使用应用时，每隔一段时间自动更新当前地点。") {
                MochiListRow(
                    title = "天气刷新间隔",
                    value = "${settings.foregroundMinutes} 分钟",
                    chevron = true,
                    onClick = { pickingForeground = true },
                )
                MochiListRow(
                    title = "后台刷新",
                    subtitle = "离开应用后也更新已保存的当前地点",
                    trailing = {
                        MochiSwitch(
                            checked = settings.backgroundEnabled,
                            onCheckedChange = { onChange(settings.copy(backgroundEnabled = it)) },
                        )
                    },
                )
                if (settings.needsBackground) {
                    MochiListRow(
                        title = if (settings.backgroundEnabled) "后台刷新间隔" else "提醒检查间隔",
                        value = "${settings.backgroundMinutes} 分钟",
                        chevron = true,
                        onClick = { pickingBackground = true },
                    )
                    MochiListRow(
                        title = if (settings.remindersEnabled && settings.checkMinutes < settings.backgroundMinutes) {
                            "提醒开启后，最长每 ${settings.checkMinutes} 分钟检查"
                        } else {
                            "实际执行时间受系统省电影响"
                        },
                    )
                }
            }
        }
        item {
            MochiListSection(
                header = "天气提醒",
                footer = status?.cityName?.let { "提醒地点：$it · 当地时间" } ?: "时间以当前地点的当地时间为准",
            ) {
                MochiListRow(
                    title = "早间今日天气",
                    subtitle = "早上收到今天的天气简报",
                    trailing = {
                        MochiSwitch(
                            checked = settings.morningEnabled,
                            onCheckedChange = { onChange(settings.copy(morningEnabled = it)) },
                        )
                    },
                )
                if (settings.morningEnabled) {
                    MochiListRow(
                        title = "早间提醒时间",
                        value = minuteLabel(settings.morningMinute),
                        chevron = true,
                        onClick = { editingTime = "morning" },
                    )
                }
                MochiListRow(
                    title = "晚间明日天气",
                    subtitle = "提前了解明天的天气",
                    trailing = {
                        MochiSwitch(
                            checked = settings.eveningEnabled,
                            onCheckedChange = { onChange(settings.copy(eveningEnabled = it)) },
                        )
                    },
                )
                if (settings.eveningEnabled) {
                    MochiListRow(
                        title = "晚间提醒时间",
                        value = minuteLabel(settings.eveningMinute),
                        chevron = true,
                        onClick = { editingTime = "evening" },
                    )
                }
                MochiListRow(
                    title = "短时降水提醒",
                    subtitle = "未来 30 分钟有降水时提醒，需来源提供短时预报",
                    trailing = {
                        MochiSwitch(
                            checked = settings.rainEnabled,
                            onCheckedChange = { onChange(settings.copy(rainEnabled = it)) },
                        )
                    },
                )
            }
        }
        if (settings.remindersEnabled) {
            item {
                MochiListSection(header = "提醒状态", footer = testFeedback ?: "通知测试不占用天气提醒次数。") {
                    MochiListRow(
                        title = if (checking) "正在检查…" else "立即检查",
                        chevron = true,
                        enabled = !checking && !testing,
                        onClick = {
                            scope.launch {
                                checking = true
                                actionError = null
                                try {
                                    WeatherReminderNotification.checkNow(context.applicationContext, forceRefresh = true)
                                    WeatherReminderSchedule.schedule(context.applicationContext)
                                    status = WeatherReminderNotification.readStatus(context.applicationContext)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    actionError = "这次检查未完成，请重试"
                                } finally {
                                    checking = false
                                }
                            }
                        },
                    )
                    MochiListRow(
                        title = if (testing) "正在测试…" else "测试通知",
                        chevron = true,
                        enabled = !testing,
                        onClick = {
                            scope.launch {
                                testing = true
                                testFeedback = null
                                try {
                                    testFeedback = withContext(Dispatchers.IO) {
                                        WeatherReminderNotification.testNotification(context.applicationContext)
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    testFeedback = "测试通知未发出，请重试或检查系统通知设置"
                                } finally {
                                    testing = false
                                    checkAccess()
                                }
                            }
                        },
                    )
                    MochiListRow(title = "通知显示设置", chevron = true,
                        onClick = { openNotificationSettings() })
                    if (status?.backgroundRestricted == true) {
                        MochiListRow(title = "允许后台运行", subtitle = "系统限制了后台运行", chevron = true,
                            onClick = { openBackgroundSettings() })
                    }
                }
            }
        }
        if (settings.remindersEnabled && notificationAccess != ReminderNotificationAccess.ALLOWED) {
            item {
                MochiListSection(header = "通知权限") {
                    MochiListRow(
                        title = "提醒已保存，通知尚未开启",
                        subtitle = if (notificationAccess == ReminderNotificationAccess.CHANNEL_BLOCKED) {
                            "系统关闭了天气提醒通知，请允许这个通知类别"
                        } else {
                            "允许天气通知后，才能收到已开启的提醒"
                        },
                        chevron = true,
                        onClick = { openNotificationSettings() },
                    )
                }
            }
        }
        actionError?.let { error ->
            item { MochiListSection { MochiListRow(title = error) } }
        }
        statusError?.let { error ->
            item { MochiListSection { MochiListRow(title = error) } }
        }
        item {
            MochiListSection(header = "说明") {
                MochiListRow(
                    title = if (details) "收起刷新与提醒说明" else "了解刷新与提醒",
                    chevron = true,
                    onClick = { details = !details },
                )
                if (details) {
                    MochiListRow(
                        title = "后台只使用已保存的地点，不申请后台定位。早晚提醒会按地点时区安排到时检查，"
                            + "90 分钟内使用有效预报；系统省电、后台限制或断网可能延后检查。",
                    )
                }
            }
        }
    }

    MochiChoiceDialog(
        visible = pickingForeground,
        title = "天气刷新间隔",
        options = listOf(5, 10, 15, 30).map { "${it} 分钟" to it.toString() },
        selected = settings.foregroundMinutes.toString(),
        onSelect = { onChange(settings.copy(foregroundMinutes = it.toInt())) },
        onDismiss = { pickingForeground = false },
    )
    MochiChoiceDialog(
        visible = pickingBackground,
        title = if (settings.backgroundEnabled) "后台刷新间隔" else "提醒检查间隔",
        options = listOf(15, 30, 60, 120).map { "${it} 分钟" to it.toString() },
        selected = settings.backgroundMinutes.toString(),
        onSelect = { onChange(settings.copy(backgroundMinutes = it.toInt())) },
        onDismiss = { pickingBackground = false },
    )
    editingTime?.let { kind ->
        val minute = if (kind == "morning") settings.morningMinute else settings.eveningMinute
        WeatherOverlayDialog(onDismiss = { editingTime = null }, maxWidth = 420.dp) { modifier ->
            ReminderTimePanel(
                if (kind == "morning") "早间提醒时间" else "晚间提醒时间",
                minute,
                onDismiss = { editingTime = null },
                onSelect = {
                    onChange(
                        if (kind == "morning") settings.copy(morningMinute = it)
                        else settings.copy(eveningMinute = it)
                    )
                    editingTime = null
                },
                modifier = modifier,
            )
        }
    }
}
