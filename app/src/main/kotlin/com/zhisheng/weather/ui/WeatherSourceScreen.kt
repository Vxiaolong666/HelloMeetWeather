package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.LocationEnginePref
import com.zhisheng.weather.data.ModuleSourcePolicy
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.WeatherModule
import com.zhisheng.weather.data.WeatherRepository
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.zhishengDialogPanel
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.theme.zhishengScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 每个天气来源对应的 Phosphor 图标，供来源列表与模块行统一使用。 */
internal fun weatherSourceIcon(source: SourcePref?): Int = when (source) {
    null -> R.drawable.ph_arrow_clockwise
    SourcePref.AUTO -> R.drawable.ph_sparkle
    SourcePref.ZHISHENG -> R.drawable.ph_sparkle
    SourcePref.NMC -> R.drawable.ph_broadcast
    SourcePref.QWEATHER -> R.drawable.ph_wind
    SourcePref.CAIYUN -> R.drawable.ph_cloud_rain
    SourcePref.AMAP -> R.drawable.ph_map_trifold
    SourcePref.BAIDU -> R.drawable.ph_map_pin
    SourcePref.TENCENT -> R.drawable.ph_cloud
    SourcePref.WEATHERCN -> R.drawable.ph_sun
    SourcePref.SENIVERSE -> R.drawable.ph_leaf
    SourcePref.MOJI -> R.drawable.ph_cloud_rain
    SourcePref.XIAOMI -> R.drawable.ph_cloud
    SourcePref.OPEN_METEO -> R.drawable.ph_waves
}

/** 各天气模块的图标。 */
internal fun weatherModuleIcon(module: WeatherModule): Int = when (module) {
    WeatherModule.CURRENT -> R.drawable.ph_gauge
    WeatherModule.FORECAST -> R.drawable.ph_cloud
    WeatherModule.MINUTELY -> R.drawable.ph_cloud_rain
    WeatherModule.ALERTS -> R.drawable.ph_warning
    WeatherModule.INDICES -> R.drawable.ph_person_simple_walk
    WeatherModule.AIR -> R.drawable.ph_leaf
}

/** 每个定位地理服务引擎对应的 Phosphor 图标，供引擎列表与入口行统一使用。 */
internal fun locationEngineIcon(engine: LocationEnginePref): Int = when (engine) {
    LocationEnginePref.AUTO -> R.drawable.ph_crosshair
    LocationEnginePref.SYSTEM -> R.drawable.ph_compass
    LocationEnginePref.AMAP -> R.drawable.ph_map_pin
    LocationEnginePref.BAIDU -> R.drawable.ph_map_trifold
    LocationEnginePref.SHARED -> R.drawable.ph_database
}

/**
 * 天气来源二级页：主天气源 / 按模块设置两个分栏，来源项与模块项都带图标。
 * 骨架与「服务接入」页一致（zhishengScreen + FeaturePageHeader + zhishengPanel）。
 */
@Composable
fun WeatherSourceScreen(
    weather: WeatherData?,
    onManageProviders: () -> Unit,
    onBack: () -> Unit,
    initialModules: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalZhishengPalette.current
    val primary by SettingsRepository.sourcePref.collectAsState(initial = SourcePref.AUTO)
    val policy by SettingsRepository.moduleSources.collectAsState(initial = ModuleSourcePolicy())
    val developer by SettingsRepository.developerMode.collectAsState(initial = false)
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
    val comparisonCity by CityRepository.selectedCity.collectAsState(initial = null)
    var showComparison by remember { mutableStateOf(false) }
    var modules by rememberSaveable { mutableStateOf(initialModules) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val editing = WeatherModule.entries.firstOrNull { it.key == editingKey }
    val visible = SourcePref.visible(developer).toSet()

    fun select(module: WeatherModule?, source: SourcePref?) {
        if (saving) return
        scope.launch {
            saving = true
            error = null
            try {
                if (module != null) {
                    SettingsRepository.setModuleSource(module, source)
                    editingKey = null
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

    Box(Modifier.fillMaxSize().zhishengScreen()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            FeaturePageHeader("天气来源", subtitle = "WEATHER SOURCE", onBack = onBack)

            // 当前主源概览
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).zhishengPanel()) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceBadge(primary, active = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "当前主源 · ${primary.cn}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ZhishengText,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            primary.desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextSecondary,
                        )
                    }
                }
            }

            // 分栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(false to "主天气源", true to "按模块设置").forEach { (isModules, label) ->
                    val selected = modules == isModules
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) palette.mint.copy(alpha = 0.14f)
                                else palette.surface.copy(alpha = 0.36f),
                            )
                            .selectable(selected, enabled = !saving, role = Role.Tab) {
                                modules = isModules
                                editingKey = null
                                error = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) palette.mint else ZhishengTextSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    error.orEmpty(),
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.red,
                )
            }

            when {
                !modules -> {
                    weatherSourceChoices(null, visible, available, primary).forEach { choice ->
                        SourceOption(
                            source = choice.source,
                            hint = if (!choice.enabled) {
                                "当前不可用，可在服务接入中检查"
                            } else {
                                choice.source?.desc ?: "使用当前主源 ${primary.cn}"
                            },
                            selected = choice.source == primary,
                            enabled = choice.enabled && !saving,
                            onSelect = { select(null, choice.source) },
                        )
                    }
                }
                else -> {
                    WeatherModuleSettingsContent(
                        policy = policy,
                        available = available,
                        weather = weather,
                        onCompare = { if (comparisonCity != null) showComparison = true else error = "请先返回主页选择一个城市" },
                        onChoose = { editingKey = it.key },
                        showHeader = true,
                    )
                    SourcePrioritySettings(primary, available, onSaving = { saving = it })
                }
            }

            // 自有接入入口
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).zhishengPanel()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !saving, role = Role.Button) { onManageProviders() }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhosphorIcon(R.drawable.ph_database, null, Modifier.size(18.dp), palette.mint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "管理自有服务接入",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ZhishengText,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "自定义数据来源设置",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextSecondary,
                        )
                    }
                    PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(16.dp), ZhishengTextTertiary)
                }
            }

            if (comparisonCity == null) {
                Text(
                    "返回主页选择城市后可比较天气来源",
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { module ->
        ModuleSourcePickerDialog(
            module = module,
            primary = primary,
            current = policy[module],
            visible = visible,
            available = available,
            saving = saving,
            error = error,
            onSelect = { source -> select(module, source) },
            onDismiss = { if (!saving) editingKey = null },
        )
    }

    if (showComparison) {
        comparisonCity?.let { city ->
            WeatherSourceComparison(city = city, onDismiss = { showComparison = false })
        }
    }
}

/**
 * 模块来源选择弹窗：点模块行后不再整页切换，列表上下文保持不动。
 * 选项复用 [SourceOption]，不可用项保持最淡灰。
 */
@Composable
internal fun ModuleSourcePickerDialog(
    module: WeatherModule,
    primary: SourcePref,
    current: SourcePref?,
    visible: Set<SourcePref>,
    available: Set<SourcePref>,
    saving: Boolean,
    onSelect: (SourcePref?) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    val palette = LocalZhishengPalette.current
    WeatherOverlayDialog(onDismiss = onDismiss, maxWidth = 480.dp) { modifier ->
        Column(
            modifier.zhishengDialogPanel().padding(vertical = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${module.cn} · 选择来源",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ZhishengText,
                    )
                    Text(
                        "跟随主源：${primary.cn}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextSecondary,
                    )
                }
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(if (saving) "保存中…" else "取消", color = palette.mint)
                }
            }
            if (error != null) {
                Text(
                    error,
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.red,
                )
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                weatherSourceChoices(module, visible, available, current).forEach { choice ->
                    SourceOption(
                        source = choice.source,
                        hint = if (!choice.enabled) {
                            "当前不可用或不支持该模块"
                        } else {
                            choice.source?.desc ?: "跟随主源 ${primary.cn}"
                        },
                        selected = choice.source == current,
                        enabled = choice.enabled && !saving,
                        onSelect = { onSelect(choice.source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(source: SourcePref?, active: Boolean, muted: Boolean = false) {
    val palette = LocalZhishengPalette.current
    val tone = when {
        muted -> ZhishengTextTertiary.copy(alpha = DisabledInkTint)
        active -> palette.mint
        else -> ZhishengTextSecondary
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        PhosphorIcon(weatherSourceIcon(source), null, Modifier.size(18.dp), tone)
    }
}

/** 不可用/不支持该模块的置灰：比正文三级文字更淡，避免读起来仍像可选。 */
internal const val DisabledInkTint = 0.45f

@Composable
internal fun SourceOption(
    source: SourcePref?,
    hint: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalZhishengPalette.current
    val disabledInk = ZhishengTextTertiary.copy(alpha = DisabledInkTint)
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).zhishengPanel()) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected, enabled = enabled, role = Role.RadioButton) { onSelect() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceBadge(source, active = selected, muted = !enabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source?.cn ?: "跟随主源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (enabled) ZhishengText else disabledInk,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) ZhishengTextSecondary else disabledInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = palette.mint,
                    unselectedColor = ZhishengTextTertiary.copy(alpha = DisabledInkTint),
                ),
            )
        }
    }
}
