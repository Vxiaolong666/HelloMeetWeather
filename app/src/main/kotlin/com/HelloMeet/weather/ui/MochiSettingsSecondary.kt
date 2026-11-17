package com.HelloMeet.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import com.HelloMeet.weather.model.WeatherCondition
import com.HelloMeet.weather.ui.components.LocalSolarSkyPreview
import com.HelloMeet.weather.ui.components.SolarSkyState
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSegmentedDefaults
import ui.feice.mochi.navigation.MochiTitleBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.AmbienceLevel
import com.HelloMeet.weather.data.CityRepository
import com.HelloMeet.weather.data.LocationEnginePref
import com.HelloMeet.weather.data.LocationServiceLog
import com.HelloMeet.weather.data.LocationSource
import com.HelloMeet.weather.data.ModuleSourcePolicy
import com.HelloMeet.weather.data.SecretStore
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.data.SourcePref
import com.HelloMeet.weather.data.WeatherModule
import com.HelloMeet.weather.data.WeatherRepository
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.WeatherData
import com.HelloMeet.weather.model.WeatherIntensity
import com.HelloMeet.weather.ui.components.LocalWeatherContinuity
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.home.SimulatedWeatherSurface
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonColors
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDisclosure
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiSelectableRow
import ui.feice.mochi.list.rememberMochiListEditState
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/*
 * 设置链路三个跨页二级页的 mochi 版：天气来源 / 服务接入 / 氛围实验室。
 * 页面骨架、分组、列表行、开关、分段控件、返回按钮、弹窗全部使用库组件；
 * 服务接入引导改走 MochiProviderWizard 新版页面（库步骤进度 + 库卡片/输入/按钮），
 * 天气来源比较与天气模拟表面仍是自绘实现，交互与写入路径与原页一致。
 */

@Composable
fun MochiWeatherSourceScreen(
    weather: WeatherData?,
    onManageProviders: () -> Unit,
    onBack: () -> Unit,
    initialModules: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val primary by SettingsRepository.sourcePref.collectAsState(initial = SourcePref.AUTO)
    val policy by SettingsRepository.moduleSources.collectAsState(initial = ModuleSourcePolicy())
    val priority by SettingsRepository.sourcePriority.collectAsState(initial = emptyList())
    val developer by SettingsRepository.developerMode.collectAsState(initial = false)
    val comparisonCity by CityRepository.selectedCity.collectAsState(initial = null)
    val qw by SecretStore.qwRuntimeFlow.collectAsState(initial = SecretStore.qwRuntime)
    val cy by SecretStore.caiyunRuntimeFlow.collectAsState(initial = SecretStore.caiyunRuntime)
    val amap by SecretStore.amapRuntimeFlow.collectAsState(initial = SecretStore.amapRuntime)
    val baidu by SecretStore.baiduRuntimeFlow.collectAsState(initial = SecretStore.baiduRuntime)
    val seniverse by SecretStore.seniverseRuntimeFlow.collectAsState(initial = SecretStore.seniverseRuntime)
    val moji by SecretStore.mojiRuntimeFlow.collectAsState(initial = SecretStore.mojiRuntime)

    var available by remember { mutableStateOf(SourcePref.visible(false).toSet()) }
    LaunchedEffect(developer, qw, cy, amap, baidu, seniverse, moji) {
        available = WeatherRepository.configuredSources()
    }
    var modules by rememberSaveable { mutableStateOf(initialModules) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showComparison by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val visible = SourcePref.visible(developer).toSet()
    val editingModule = WeatherModule.entries.firstOrNull { it.key == editing }

    fun select(module: WeatherModule?, source: SourcePref?) {
        if (saving) return
        scope.launch {
            saving = true
            error = null
            try {
                if (module != null) {
                    SettingsRepository.setModuleSource(module, source)
                    editing = null
                } else {
                    SettingsRepository.setSourcePref(requireNotNull(source))
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = "没有保存成功，请重试"
            } finally {
                saving = false
            }
        }
    }

    fun savePriority(next: List<SourcePref>) {
        if (saving) return
        scope.launch {
            saving = true
            error = null
            try {
                SettingsRepository.setSourcePriority(next)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = "未能保存，请重试"
            } finally {
                saving = false
            }
        }
    }

    MochiPageFrame(title = "天气来源", onBack = onBack, subtitle = "WEATHER SOURCE", showSave = true) {
        item {
            MochiListSection(header = "当前主源", footer = primary.desc) {
                MochiListRow(
                    title = primary.cn,
                    subtitle = "主天气数据当前从此来源获取",
                    leading = { PhosphorIcon(weatherSourceIcon(primary), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                )
            }
        }
        item {
            MochiSegmentedControl(
                selectedIndex = if (modules) 1 else 0,
                onSelect = { modules = it == 1; editing = null; error = null },
                items = persistentListOf("主天气源", "按模块设置"),
                enabled = !saving,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (error != null) {
            item {
                MochiListSection(header = "提示") {
                    MochiListRow(title = "> $error", destructive = true)
                }
            }
        }
        if (!modules) {
            item {
                MochiListSection(header = "主天气源", footer = "不可用的来源可在「服务接入」中配置凭据。") {
                    weatherSourceChoices(null, visible, available, primary).forEach { choice ->
                        val source = choice.source
                        MochiListRow(
                            title = source?.cn ?: "跟随主源 ${primary.cn}",
                            subtitle = when {
                                !choice.enabled -> "当前不可用，可在服务接入中检查"
                                source == null -> "使用当前主源 ${primary.cn}"
                                else -> source.desc
                            },
                            leading = { PhosphorIcon(weatherSourceIcon(source), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                            checked = source == primary,
                            enabled = choice.enabled && !saving,
                            onClick = { source?.let { select(null, it) } },
                        )
                    }
                }
            }
        } else {
            item {
                MochiListSection(header = "按模块选择来源", footer = "点选模块可单独指定来源，或跟随主源。") {
                    WeatherModule.entries.forEach { module ->
                        MochiListRow(
                            title = module.cn,
                            value = policy[module]?.cn ?: "跟随主源",
                            subtitle = weather?.sourceDiagnostics?.get(module.key),
                            leading = { PhosphorIcon(weatherModuleIcon(module), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                            chevron = true,
                            enabled = !saving,
                            onClick = { editing = module.key },
                        )
                    }
                }
            }
            item {
                MochiSourcePriorityCard(
                    priority = priority,
                    candidates = available.filter {
                        it != primary && it !in priority && it != SourcePref.AUTO && it != SourcePref.ZHISHENG
                    },
                    saving = saving,
                    onSave = { savePriority(it) },
                )
            }
        }
        item {
            MochiListSection(header = "服务接入与比较") {
                MochiListRow(
                    title = "管理自有服务接入",
                    subtitle = "自定义服务来源配置",
                    leading = { PhosphorIcon(R.drawable.ph_database, null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                    chevron = true,
                    enabled = !saving,
                    modifier = Modifier.mochiNavSource(
                        mochiSettingsSourceKey("settings.providers"),
                        MochiListDefaults.SectionCorner,
                        if (MochiTransitionConfig.sourceHighlight) {
                            MochiTheme.colorScheme.primaryContainer
                        } else {
                            MochiDefaults.groupedContainer()
                        },
                    ),
                    onClick = onManageProviders,
                )
                MochiListRow(
                    title = "比较天气来源",
                    leading = { PhosphorIcon(R.drawable.ph_eye, null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                    chevron = true,
                    enabled = comparisonCity != null && !saving,
                    onClick = {
                        if (comparisonCity != null) showComparison = true
                        else error = "请先返回主页选择一个城市"
                    },
                )
            }
        }
        if (comparisonCity == null) {
            item { MochiListRow(title = "返回主页选择城市后可比较天气来源") }
        }
    }

    if (editingModule != null) {
        val choices = weatherSourceChoices(editingModule, visible, available, policy[editingModule])
        MochiChoiceDialog(
            title = "${editingModule.cn} · 选择来源",
            subtitle = "跟随主源：${primary.cn}",
            options = choices.map { (it.source?.cn ?: "跟随主源") to (it.source?.key ?: "") },
            selected = policy[editingModule]?.key ?: "",
            enabled = { key -> choices.firstOrNull { (it.source?.key ?: "") == key }?.enabled == true && !saving },
            iconFor = { key -> weatherSourceIcon(key.takeIf { it.isNotEmpty() }?.let(SourcePref::from)) },
            onSelect = { key ->
                select(editingModule, key.takeIf { it.isNotEmpty() }?.let(SourcePref::from))
            },
            onDismiss = { if (!saving) editing = null },
        )
    }
    if (showComparison) {
        comparisonCity?.let { WeatherSourceComparison(city = it, onDismiss = { showComparison = false }) }
    }
}

/**
 * 定位地理服务引擎二级页：参考 [MochiWeatherSourceScreen] 的结构，
 * 列出系统定位 / 高德 / 百度 / 遇见位置服务，点选即写入 [SettingsRepository.setLocationEngine]，
 * 当前引擎与凭据可用性实时反映在行内。
 */
@Composable
fun MochiLocationEngineScreen(
    onBack: () -> Unit,
    onManageProviders: () -> Unit = {},
    onOpenLog: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val current by SettingsRepository.locationEngine.collectAsState(initial = LocationEnginePref.AUTO)
    val amap by SecretStore.amapRuntimeFlow.collectAsState(initial = SecretStore.amapRuntime)
    val baidu by SecretStore.baiduRuntimeFlow.collectAsState(initial = SecretStore.baiduRuntime)
    val shared by SecretStore.sharedPlaceFlow.collectAsState(initial = SecretStore.sharedPlaceRuntime)
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun available(engine: LocationEnginePref): Boolean = when (engine) {
        LocationEnginePref.AUTO, LocationEnginePref.SYSTEM -> true
        LocationEnginePref.AMAP -> amap.ready
        LocationEnginePref.BAIDU -> baidu.ready
        LocationEnginePref.SHARED -> shared.enabled
    }

    fun select(engine: LocationEnginePref) {
        if (saving) return
        scope.launch {
            saving = true
            error = null
            try {
                SettingsRepository.setLocationEngine(engine)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = "没有保存成功，请重试"
            } finally {
                saving = false
            }
        }
    }

    MochiPageFrame(title = "位置服务引擎", onBack = onBack, subtitle = "LOCATION ENGINE", showSave = true) {
        item {
            MochiListSection(header = "当前引擎", footer = current.desc) {
                MochiListRow(
                    title = current.cn,
                    subtitle = "定位地理数据当前优先从此服务获取",
                    leading = { PhosphorIcon(locationEngineIcon(current), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                )
                val resolution = LocationServiceLog.lastResolution
                MochiListRow(
                    title = "当前服务商",
                    value = resolution?.let { locationSourceCn(it.source) } ?: "尚未定位",
                    subtitle = resolution?.let {
                        locationStreetStatusCn(it.streetStatus) +
                            " · ${LocationServiceLog.formatTime(it.timeMillis)}" +
                            (it.street?.let { street -> " · $street" } ?: "")
                    } ?: "完成一次定位后在此显示实际返回地址的服务商",
                    leading = { PhosphorIcon(R.drawable.ph_map_pin, null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                )
            }
        }
        if (error != null) {
            item {
                MochiListSection(header = "提示") {
                    MochiListRow(title = "> $error", destructive = true)
                }
            }
        }
        item {
            MochiListSection(header = "定位服务引擎", footer = "不可用的服务可在「服务接入」中配置凭据。") {
                LocationEnginePref.entries.forEach { engine ->
                    val ok = available(engine)
                    MochiListRow(
                        title = engine.cn,
                        subtitle = when {
                            !ok -> "当前不可用，可在服务接入中检查"
                            else -> engine.desc
                        },
                        leading = { PhosphorIcon(locationEngineIcon(engine), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                        checked = engine == current,
                        enabled = ok && !saving,
                        onClick = { select(engine) },
                    )
                }
            }
        }
        item {
            MochiListSection(header = "凭据与接入") {
                MochiListRow(
                    title = "管理自有服务接入",
                    subtitle = "高德 / 百度 / 遇见位置服务",
                    leading = { PhosphorIcon(R.drawable.ph_database, null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                    chevron = true,
                    enabled = !saving,
                    modifier = Modifier.mochiNavSource(
                        mochiSettingsSourceKey("settings.providers"),
                        MochiListDefaults.SectionCorner,
                        if (MochiTransitionConfig.sourceHighlight) {
                            MochiTheme.colorScheme.primaryContainer
                        } else {
                            MochiDefaults.groupedContainer()
                        },
                    ),
                    onClick = onManageProviders,
                )
            }
        }
        item {
            MochiListSection(header = "诊断") {
                MochiListRow(
                    title = "定位服务日志",
                    subtitle = "查看定位与逆地理的尝试、失败与降级记录",
                    leading = { PhosphorIcon(R.drawable.ph_list, null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                    chevron = true,
                    modifier = Modifier.mochiNavSource(
                        mochiSettingsSourceKey("settings.locationLog"),
                        MochiListDefaults.SectionCorner,
                        if (MochiTransitionConfig.sourceHighlight) {
                            MochiTheme.colorScheme.primaryContainer
                        } else {
                            MochiDefaults.groupedContainer()
                        },
                    ),
                    onClick = onOpenLog,
                )
            }
        }
    }
}

/**
 * 定位服务日志页：展示最近一次生效的服务商与内存中的定位/逆地理运行日志，
 * 每 2 秒自动刷新，便于在「立即重新定位」后直接观察新增条目。
 */
@Composable
fun MochiLocationLogScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }
    val entries = remember(tick) { LocationServiceLog.snapshot() }
    val resolution = remember(tick) { LocationServiceLog.lastResolution }
    val clipboard = LocalClipboardManager.current
    fun reportText(): String = buildString {
        append("遇见天气 定位诊断 ${LocationServiceLog.formatTime(System.currentTimeMillis())}\n")
        append("当前服务商：")
        append(resolution?.let { res ->
            locationSourceCn(res.source) + "（" + locationStreetStatusCn(res.streetStatus) +
                (res.street?.let { street -> " · $street" } ?: "") + "）"
        } ?: "尚未定位")
        append('\n')
        LocationServiceLog.snapshot().forEach { entry ->
            append("[${LocationServiceLog.formatTime(entry.timeMillis)} ${entry.level} ${entry.tag}] ${entry.message}\n")
        }
    }
    MochiPageFrame(title = "定位服务日志", onBack = onBack, subtitle = "LOCATION LOG") {
        item {
            MochiListSection(header = "当前服务商") {
                if (resolution == null) {
                    MochiListRow(title = "尚未定位", subtitle = "完成一次定位后在此显示实际返回地址的服务商")
                } else {
                    MochiListRow(
                        title = locationSourceCn(resolution.source),
                        subtitle = locationStreetStatusCn(resolution.streetStatus) +
                            " · ${LocationServiceLog.formatTime(resolution.timeMillis)}" +
                            (resolution.street?.let { " · $it" } ?: ""),
                    )
                }
            }
        }
        item {
            MochiListSection(
                header = "运行日志（${entries.size} 条）",
                footer = "仅保存在本次运行内存中，不含 Key 与坐标；重启应用后清空。",
            ) {
                MochiListRow(
                    title = "复制诊断信息",
                    subtitle = "复制当前服务商与全部日志文本，便于反馈问题",
                    onClick = { clipboard.setText(AnnotatedString(reportText())) },
                )
                MochiListRow(
                    title = "清空日志",
                    destructive = true,
                    onClick = {
                        LocationServiceLog.clear()
                        tick++
                    },
                )
            }
        }
        if (entries.isEmpty()) {
            item {
                MochiListSection {
                    MochiListRow(title = "暂无日志：可在「数据与位置 → 立即重新定位」后返回本页查看")
                }
            }
        } else {
            // 每条日志都是卡片里的一行：按 8 条一张卡片分组，避免 200 条日志各画一张卡。
            items(entries.asReversed().chunked(8)) { chunk ->
                MochiListCard {
                    chunk.forEach { entry ->
                        MochiListRow(
                            title = "${LocationServiceLog.formatTime(entry.timeMillis)} · ${entry.tag}",
                            subtitle = entry.message,
                            destructive = entry.level == LocationServiceLog.Level.ERROR,
                        )
                    }
                }
            }
        }
    }
}

private fun locationSourceCn(source: String): String = when (source) {
    "amap" -> "高德定位"
    "baidu" -> "百度定位"
    "shared" -> "遇见位置服务"
    else -> "系统定位"
}

private fun locationStreetStatusCn(status: LocationSource.StreetStatus?): String = when (status) {
    null -> "尚未定位"
    LocationSource.StreetStatus.NOT_REQUESTED -> "未开启街道级"
    LocationSource.StreetStatus.RESOLVED -> "街道级已生效"
    LocationSource.StreetStatus.APPROXIMATE_PERMISSION -> "仅大致位置权限"
    LocationSource.StreetStatus.INSUFFICIENT_ACCURACY -> "定位精度不足"
    LocationSource.StreetStatus.UNAVAILABLE -> "街道服务不可用"
}

/**
 * 缺失数据补全顺序：照 demo「列表编辑与索引」重写。
 * [MochiListDisclosure] 收起时只显示已启用数量，展开后是同一张卡片里的编辑区；
 * [rememberMochiListEditState] + [MochiSelectableRow] 让已启用来源与可选来源都能多选，
 * 点「完成」一次性移除勾选的已启用项、添加勾选的可选项。
 */
@Composable
private fun MochiSourcePriorityCard(
    priority: List<SourcePref>,
    candidates: List<SourcePref>,
    saving: Boolean,
    onSave: (List<SourcePref>) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val edit = rememberMochiListEditState()
    val onKeys = priority.map { "on:${it.key}" }
    val addKeys = candidates.map { "add:${it.key}" }
    val allKeys = onKeys + addKeys

    fun finishEditing() {
        if (edit.selected.isNotEmpty()) {
            val selected = edit.selected.toSet()
            val next = priority.filterNot { "on:${it.key}" in selected } +
                candidates.filter { "add:${it.key}" in selected }
            onSave(next)
        }
        edit.clear()
        edit.editing = false
    }

    MochiListSection(
        footer = "展开后点已启用来源可上移一位；进入编辑可勾选多个来源，完成时批量移除已启用、添加未启用。",
    ) {
        MochiListDisclosure(
            title = "缺失数据补全顺序",
            expanded = expanded,
            onExpandedChange = { expanded = it },
            subtitle = "主源缺少某个模块时按顺序向其他来源补全",
            value = if (priority.isEmpty()) "未启用" else "${priority.size} 个来源",
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MochiBlurText(
                    if (edit.editing) "勾选来源后点完成即可批量应用" else "已启用 ${priority.size} 个来源",
                    style = MochiTheme.textStyles.subhead,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                MochiButton(
                    onClick = { if (edit.editing) finishEditing() else edit.editing = true },
                    enabled = !saving,
                    style = MochiButtonStyle.Text,
                ) {
                    MochiBlurText(if (edit.editing) "完成" else "编辑")
                }
            }
            if (priority.isEmpty()) {
                MochiListRow(title = "未启用补全", subtitle = "从下方带 + 的来源中添加")
            }
            priority.forEachIndexed { index, source ->
                MochiSelectableRow(
                    state = edit,
                    key = "on:${source.key}",
                    title = source.cn,
                    subtitle = source.desc,
                    value = "第 ${index + 1} 顺位",
                    leading = {
                        PhosphorIcon(weatherSourceIcon(source), null, Modifier.size(22.dp), mochiLeadingIconTint())
                    },
                    onClick = {
                        if (!saving && index > 0) {
                            onSave(priority.toMutableList().also { java.util.Collections.swap(it, index, index - 1) })
                        }
                    },
                )
            }
            candidates.forEach { source ->
                MochiSelectableRow(
                    state = edit,
                    key = "add:${source.key}",
                    title = "+ ${source.cn}",
                    subtitle = source.desc,
                    leading = {
                        PhosphorIcon(weatherSourceIcon(source), null, Modifier.size(22.dp), mochiLeadingIconTint())
                    },
                    onClick = { if (!saving) onSave(priority + source) },
                )
            }
            if (edit.editing) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MochiBarButton(
                        if (edit.selected.size == allKeys.size) "全不选" else "全选",
                        onClick = {
                            if (edit.selected.size == allKeys.size) edit.clear() else edit.selectAll(allKeys)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    MochiBlurText(
                        "已选 ${edit.selected.size} 项",
                        style = MochiTheme.textStyles.subhead,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class MochiProviderItem(
    val title: String,
    val description: String,
    val icon: Int,
    val ready: Boolean,
    val kind: ProviderWizardKind,
    val clear: suspend () -> Unit,
)

private data class MochiProviderGroup(
    val title: String,
    val caption: String,
    val items: List<MochiProviderItem>,
)

@Composable
fun MochiProviderAccessScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val qwRt by SecretStore.qwRuntimeFlow.collectAsState(initial = SecretStore.qwRuntime)
    val caiyunRt by SecretStore.caiyunRuntimeFlow.collectAsState(initial = SecretStore.caiyunRuntime)
    val amapRt by SecretStore.amapRuntimeFlow.collectAsState(initial = SecretStore.amapRuntime)
    val baiduRt by SecretStore.baiduRuntimeFlow.collectAsState(initial = SecretStore.baiduRuntime)
    val seniverseRt by SecretStore.seniverseRuntimeFlow.collectAsState(initial = SecretStore.seniverseRuntime)
    val mojiRt by SecretStore.mojiRuntimeFlow.collectAsState(initial = SecretStore.mojiRuntime)
    var wizard by rememberSaveable { mutableStateOf<ProviderWizardKind?>(null) }
    var pendingClear by remember { mutableStateOf<MochiProviderItem?>(null) }

    // 「清除凭据」是破坏性操作：按钮用错误容器色，未接入时禁用。
    val cs = MochiTheme.colorScheme
    val clearColors = remember(cs) {
        MochiButtonColors(
            container = cs.errorContainer,
            content = cs.onErrorContainer,
            outline = Color.Transparent,
            pressedContainer = Color.Transparent,
            glow = Color.White.copy(alpha = if (cs.isDark) 0.16f else 0.45f),
            disabledContainer = cs.onSurface.copy(alpha = 0.12f),
            disabledContent = cs.onSurface.copy(alpha = 0.38f),
        )
    }

    val groups = remember(
        qwRt.ready, caiyunRt.ready, amapRt.ready, baiduRt.ready, seniverseRt.ready, mojiRt.ready,
    ) {
        listOf(
            MochiProviderGroup("气象数据", "接入后可在天气来源中单独指定", listOf(
                MochiProviderItem("和风天气", "预报、逐时与预警 · 个人开发版", R.drawable.ph_wind,
                    qwRt.ready, ProviderWizardKind.QWEATHER) { SecretStore.clearQw() },
                MochiProviderItem("彩云天气", "分钟级降水与雷达回波", R.drawable.ph_cloud_rain,
                    caiyunRt.ready, ProviderWizardKind.CAIYUN) { SecretStore.clearCaiyun() },
                MochiProviderItem("心知天气", "天气实况与生活指数 · 个人免费版", R.drawable.ph_leaf,
                    seniverseRt.ready, ProviderWizardKind.SENIVERSE) { SecretStore.clearSeniverse() },
                MochiProviderItem("墨迹天气", "天气与预警 · 个人订阅", R.drawable.ph_cloud,
                    mojiRt.ready, ProviderWizardKind.MOJI) { SecretStore.clearMoji() },
            )),
            MochiProviderGroup("地理服务", "地名搜索、坐标转换与街道识别", listOf(
                MochiProviderItem("高德服务", "地理编码与逆地址解析", R.drawable.ph_map_trifold,
                    amapRt.ready, ProviderWizardKind.AMAP) { SecretStore.clearAmap() },
                MochiProviderItem("百度服务", "地理编码与坐标定位", R.drawable.ph_map_pin,
                    baiduRt.ready, ProviderWizardKind.BAIDU) { SecretStore.clearBaidu() },
            )),
        )
    }
    val total = groups.sumOf { it.items.size }
    val connected = groups.sumOf { group -> group.items.count { it.ready } }

    MochiPageFrame(title = "服务接入", onBack = onBack, subtitle = "PROVIDER ACCESS", showSave = true) {
        item {
            MochiListSection(header = "自有服务凭据", footer = "凭据仅保存在本机") {
                MochiListRow(title = "已接入 $connected / $total")
            }
        }
        item {
            MochiMapServiceSection()
        }
        groups.forEach { group ->
            item {
                MochiListSection(header = group.title, footer = group.caption) {
                    group.items.forEach { item ->
                        MochiListRow(
                            title = item.title,
                            subtitle = item.description,
                            value = if (item.ready) "已接入" else "未接入",
                            leading = { PhosphorIcon(item.icon, null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                            checked = item.ready,
                            chevron = true,
                            onClick = { wizard = item.kind },
                        )
                        // 卡片里放的不是列表行时，自己补上内边距和块间距。
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MochiButton(
                                    onClick = { wizard = item.kind },
                                    modifier = Modifier.weight(1f),
                                    style = MochiButtonStyle.Tonal,
                                ) {
                                    Text(if (item.ready) "重新配置" else "接入")
                                }
                                MochiButton(
                                    onClick = { pendingClear = item },
                                    modifier = Modifier.weight(1f),
                                    enabled = item.ready,
                                    style = MochiButtonStyle.Tonal,
                                    colors = clearColors,
                                ) {
                                    Text("清除凭据")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingClear?.let { item ->
        MochiConfirmDialog(
            title = "清除 ${item.title} 凭据？",
            message = "清除后该服务将回到未接入状态，需要重新配置才能继续使用。",
            confirmLabel = "清除",
            onConfirm = {
                pendingClear = null
                scope.launch { item.clear() }
            },
            onDismiss = { pendingClear = null },
        )
    }
    // 服务接入引导是叠在「服务接入」页上的整页（非库导航），进入 / 退出统一走 AppTransitions
    // 的 contentTransform：打开应用式从小容器展开、缩回；无动画时立即切换，不再突然出现 / 消失。
    val uiPrefs = rememberMochiUiPrefs()
    val wizardStyle = PageTransitionStyle.from(uiPrefs.pageTransition)
    val wizardMotion = MochiTransitionConfig.motionEnabled && systemAnimationsEnabled()
    AnimatedContent(
        targetState = wizard,
        transitionSpec = { AppTransitions.contentTransform(wizardMotion, wizardStyle, uiPrefs.swipeBack) },
        label = "provider-wizard",
    ) { wizardKind ->
        if (wizardKind != null) MochiProviderWizard(kind = wizardKind, onClose = { wizard = null })
    }
}


/**
 * 地图底图凭据：填了天地图 Key 就用官方天地图，留空时台风页回落 OpenFreeMap 公共底图。
 * 与「构建默认凭据」的读取顺序一致：应用内设置覆盖 BuildConfig.TDT_TOKEN。
 */
@Composable
private fun MochiMapServiceSection() {
    val scope = rememberCoroutineScope()
    val saved by SettingsRepository.tiandituToken.collectAsState(initial = null)
    // draft 为 null 表示还没开始编辑：回填已保存的 Key；之前只靠键盘 Done 提交，
    // 不打 Done 直接返回就丢改动，现在输入即保存。
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(saved) { if (draft == null) draft = saved.orEmpty() }
    val value = draft ?: saved.orEmpty()
    MochiListSection(
        header = "地图底图",
        footer = "填写天地图 Key 后，台风路径页改用官方天地图；留空回落 OpenFreeMap 公共底图。",
    ) {
        MochiListTextFieldRow(
            value = value,
            onValueChange = {
                draft = it
                scope.launch { SettingsRepository.setTiandituToken(it) }
            },
            label = "天地图 Key",
            placeholder = "留空使用 OpenFreeMap",
        )
    }
}

/** 「一天的天空」的六个时刻：合并进天气效果预览页，不再单独开一个弹窗。 */
private data class SkyPreviewMoment(val label: String, val altitude: Float, val minutes: Int, val rising: Boolean = false)

private val skyPreviewMoments = listOf(
    SkyPreviewMoment("晨曦", -2f, 6 * 60 + 20, true), SkyPreviewMoment("日间", 35f, 14 * 60),
    SkyPreviewMoment("暖金", 5f, 19 * 60 + 15), SkyPreviewMoment("霞光", -1.5f, 19 * 60 + 42),
    SkyPreviewMoment("蓝调", -7f, 20 * 60 + 10), SkyPreviewMoment("夜色", -24f, 22 * 60),
)

private val skyPreviewConditions = listOf(
    "晴" to WeatherCondition.CLEAR, "多云" to WeatherCondition.PARTLY_CLOUDY,
    "下雨" to WeatherCondition.RAIN, "雾" to WeatherCondition.FOG,
)

@Composable
fun MochiAtmosphereLabScreen(initialLevel: AmbienceLevel, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var scenarioIndex by rememberSaveable { mutableIntStateOf(0) }
    var intensityIndex by rememberSaveable { mutableIntStateOf(1) }
    var levelKey by rememberSaveable { mutableStateOf(initialLevel.key) }
    var momentIndex by rememberSaveable { mutableIntStateOf(3) }
    var conditionIndex by rememberSaveable { mutableIntStateOf(1) }
    var showScenarioPicker by remember { mutableStateOf(false) }
    val skyMode = mode == 1
    val moment = skyPreviewMoments[momentIndex]
    val condition = skyPreviewConditions[conditionIndex]
    val scenario = if (skyMode) AtmosphereScenario(
        "SKY_PREVIEW", condition.first, condition.second,
        night = moment.altitude < -4f, minuteOfDay = moment.minutes,
    ) else atmosphereScenarios[scenarioIndex]
    val intensity = WeatherIntensity.entries[intensityIndex]
    val level = AmbienceLevel.from(levelKey)
    val data = remember(scenarioIndex, intensityIndex, skyMode, momentIndex, conditionIndex) {
        simulatedWeather(scenario, if (skyMode) WeatherIntensity.MODERATE else intensity)
    }
    val prefs = remember(level, scenario.condition, skyMode) {
        if (skyMode) DisplayPrefs(
            ambience = AmbienceLevel.VIVID, scanlines = false,
            showIndices = false, showYesterday = false, showTelemetry = false,
            showSpacetime = false, showSkyPhotography = false,
            showCoastWeather = false, showPrecip = false, bootAnim = false,
        ) else DisplayPrefs(
            showAqi = true,
            showIndices = false,
            showYesterday = false,
            showPrecip = scenario.condition.isPrecipitation,
            showTelemetry = true,
            showSpacetime = false,
            scanlines = false,
            ambience = level,
            bootAnim = false,
        )
    }
    val simCity = remember {
        City("模拟站", "ATMOSPHERE LAB", 38.52, 102.21, "simulation:atmosphere-lab")
    }
    val solar = SolarSkyState(moment.altitude, if (moment.rising) 95f else 265f, moment.rising)
    val colors = rememberMochiColorScheme()

    CompositionLocalProvider(
        LocalWeatherContinuity provides null,
        LocalSolarSkyPreview provides if (skyMode) solar else null,
    ) {
        MochiAppTheme(colors = colors) {
            val cs = MochiTheme.colorScheme
            // 主题色模块：分段控件和顶栏底色都取自「主题配色」页生成的配色，改主题色立即生效。
            val segmentedColors = MochiSegmentedDefaults.colors(
                track = cs.surfaceContainerHigh,
                thumb = cs.primary,
                selectedContent = cs.onPrimary,
                content = cs.onSurfaceVariant,
                shadow = cs.shadow,
            )
            SimulatedWeatherSurface(
                data = data,
                city = simCity,
                prefs = prefs,
                night = scenario.night,
                referenceTimeMillis = data.updateTime,
                livingSkyOverride = if (skyMode) true else null,
                header = {
                    // 库标题栏自带状态栏留白，顶栏不会贴到屏幕最上沿。
                    MochiTitleBar(
                        title = "天气效果预览",
                        navigationIcon = { MochiBackButton(onClick = onBack) },
                        actions = { MochiBarButton("完成", onClick = onBack) },
                        background = cs.surface.copy(alpha = 0.82f),
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MochiSegmentedControl(
                            selectedIndex = mode,
                            onSelect = { mode = it },
                            items = persistentListOf("天气场景", "一天的天空"),
                            colors = segmentedColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (skyMode) {
                            MochiListSection(
                                header = "一天的天空",
                                footer = "示例预览 · 仅展示效果，不会改变当前城市的天气。",
                            ) {
                                MochiListRow(title = "当前时刻", value = moment.label)
                                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    MochiSegmentedControl(
                                        selectedIndex = momentIndex,
                                        onSelect = { momentIndex = it },
                                        items = skyPreviewMoments.map { it.label }.toImmutableList(),
                                        colors = segmentedColors,
                                        behavior = MochiSegmentedDefaults.behavior(segmentPadding = 6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    MochiSegmentedControl(
                                        selectedIndex = conditionIndex,
                                        onSelect = { conditionIndex = it },
                                        items = skyPreviewConditions.map { it.first }.toImmutableList(),
                                        colors = segmentedColors,
                                        behavior = MochiSegmentedDefaults.behavior(segmentPadding = 6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        } else {
                            MochiSegmentedControl(
                                selectedIndex = AmbienceLevel.entries.indexOf(level).coerceAtLeast(0),
                                onSelect = { levelKey = AmbienceLevel.entries[it].key },
                                items = AmbienceLevel.entries.map { it.cn }.toImmutableList(),
                                colors = segmentedColors,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MochiSegmentedControl(
                                selectedIndex = intensityIndex,
                                onSelect = { intensityIndex = it },
                                items = persistentListOf("轻", "中", "强", "极"),
                                colors = segmentedColors,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MochiListSection(header = "天气场景") {
                                MochiListRow(
                                    title = "当前场景",
                                    value = scenario.name,
                                    chevron = true,
                                    onClick = { showScenarioPicker = true },
                                )
                                MochiListRow(title = "预览效果不会改变当前城市的天气。")
                            }
                        }
                    }
                },
            )
        }
    }

    if (showScenarioPicker) {
        MochiChoiceDialog(
            title = "选择天气场景",
            subtitle = "共 ${atmosphereScenarios.size} 组模拟天气",
            options = atmosphereScenarios.mapIndexed { index, s -> s.name to index.toString() },
            selected = scenarioIndex.toString(),
            onSelect = { scenarioIndex = it.toIntOrNull() ?: 0; showScenarioPicker = false },
            onDismiss = { showScenarioPicker = false },
        )
    }
}
