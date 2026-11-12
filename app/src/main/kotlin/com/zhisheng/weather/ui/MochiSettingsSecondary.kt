package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.ui.components.LocalSolarSkyPreview
import com.zhisheng.weather.ui.components.SolarSkyState
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSegmentedDefaults
import ui.feice.mochi.navigation.MochiTitleBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.ModuleSourcePolicy
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.WeatherModule
import com.zhisheng.weather.data.WeatherRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.ui.components.LocalWeatherContinuity
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.home.SimulatedWeatherSurface
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
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
    var addingPriority by remember { mutableStateOf(false) }
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
                MochiListSection(header = "缺失数据补全顺序", footer = "主源缺少某个模块时按顺序向其他来源补全。") {
                    if (priority.isEmpty()) MochiListRow(title = "未启用补全")
                    priority.forEachIndexed { index, source ->
                        MochiListRow(
                            title = "${index + 1}. ${source.cn}",
                            leading = { PhosphorIcon(weatherSourceIcon(source), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                        )
                        MochiListRow(
                            title = "↑ 上移",
                            enabled = !saving && index > 0,
                            onClick = {
                                savePriority(priority.toMutableList().also { java.util.Collections.swap(it, index, index - 1) })
                            },
                        )
                        MochiListRow(
                            title = "移除",
                            enabled = !saving,
                            destructive = true,
                            onClick = { savePriority(priority - source) },
                        )
                    }
                    MochiListRow(
                        title = if (addingPriority) "收起可选来源" else "添加补全天气源",
                        chevron = true,
                        enabled = !saving,
                        onClick = { addingPriority = !addingPriority },
                    )
                    if (addingPriority) {
                        available.filter {
                            it != primary && it !in priority && it != SourcePref.AUTO && it != SourcePref.ZHISHENG
                        }.forEach { source ->
                            MochiListRow(
                                title = "+ ${source.cn}",
                                subtitle = source.desc,
                                leading = { PhosphorIcon(weatherSourceIcon(source), null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                                enabled = !saving,
                                onClick = { savePriority(priority + source); addingPriority = false },
                            )
                        }
                    }
                }
            }
        }
        item {
            MochiListSection(header = "服务接入与比较") {
                MochiListRow(
                    title = "管理自有服务接入",
                    subtitle = "和风 / 彩云 / 心知 / 墨迹 / 高德 / 百度",
                    leading = { PhosphorIcon(R.drawable.ph_database, null, Modifier.size(22.dp), mochiLeadingIconTint()) },
                    chevron = true,
                    enabled = !saving,
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
                        MochiListRow(
                            title = if (item.ready) "重新配置" else "接入",
                            chevron = true,
                            onClick = { wizard = item.kind },
                        )
                        MochiListRow(
                            title = "清除凭据",
                            enabled = item.ready,
                            destructive = true,
                            onClick = { pendingClear = item },
                        )
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
    wizard?.let { kind ->
        MochiProviderWizard(kind = kind, onClose = { wizard = null })
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
