package com.HelloMeet.weather.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.HelloMeet.weather.data.CityRepository
import com.HelloMeet.weather.data.LocalPlaces
import com.HelloMeet.weather.data.PlaceFailure
import com.HelloMeet.weather.data.SecretStore
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.data.WeatherRepository
import com.HelloMeet.weather.data.cityMatchesSearchArea
import com.HelloMeet.weather.data.mergePlaceSearchResults
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.DailyWeather
import com.HelloMeet.weather.model.HourlyWeather
import com.HelloMeet.weather.model.PlaceRegion
import com.HelloMeet.weather.model.WeatherCondition
import com.HelloMeet.weather.model.WeatherData
import com.HelloMeet.weather.model.YesterdayInfo
import com.HelloMeet.weather.model.filterPlaceRegion
import com.HelloMeet.weather.ui.home.tempDelta
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiConfirmationDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiSearchBar
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.theme.MochiTheme
import kotlin.math.abs

/*
 * 两个详情二级页的 mochi 版：短时降水 / 添加地点。
 * 页面骨架、分组、列表行、分段控件、搜索框、返回按钮全部使用库组件；
 * 降水曲线、逐时温度曲线、地点筛选工具是库无对应能力的业务自绘，作为整体复用。
 */

@Composable
fun MochiPrecipitationDetailScreen(data: WeatherData, cityName: String?, onBack: () -> Unit) {
    val presentation = rememberPrecipitationPresentation(data)
    MochiPageFrame(title = "短时降水", subtitle = cityName?.takeIf { it.isNotBlank() }, onBack = onBack) {
        item {
            MochiListSection(
                header = presentation.summary,
                footer = precipitationCoverageLabel(presentation, data.utcOffsetSeconds),
            ) {
                MochiListRow(title = "mm/h · 每小时降水量")
            }
        }
        if (presentation.points.isNotEmpty() || presentation.history.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PrecipitationTimeline(presentation, data.utcOffsetSeconds, interactive = true)
                }
            }
        } else {
            item { MochiListRow(title = "暂无可查看的降水曲线") }
        }
        item {
            MochiListSection(header = "数据来源") {
                MochiListRow(title = precipitationSourceLabel(data, presentation))
                MochiListRow(title = precipitationHistoryLabel(data, presentation))
            }
        }
    }
}

@Composable
fun MochiSearchScreen(onCityPicked: (City) -> Unit, onBack: () -> Unit) {
    val amap by SecretStore.amapRuntimeFlow.collectAsState(initial = SecretStore.amapRuntime)
    val developerMode by SettingsRepository.developerMode.collectAsState(initial = false)
    val amapEnhanced = developerMode && amap.ready
    val context = LocalContext.current
    val ioDispatcher = Dispatchers.IO

    var region by rememberSaveable { mutableStateOf(PlaceRegion.DOMESTIC) }
    var query by rememberSaveable { mutableStateOf("") }
    var precise by rememberSaveable { mutableStateOf(false) }
    var onlineEnabled by rememberSaveable { mutableStateOf(true) }
    var pendingCity by remember { mutableStateOf<City?>(null) }
    var submitted by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var generation by remember { mutableStateOf(0L) }
    var state by remember { mutableStateOf(SearchUiState()) }
    var province by rememberSaveable { mutableStateOf("") }
    var municipality by rememberSaveable { mutableStateOf("") }
    var provinces by remember { mutableStateOf(emptyList<String>()) }
    var municipalities by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(province) {
        provinces = LocalPlaces.areas(context)
        municipalities = if (province.isBlank()) emptyList() else LocalPlaces.areas(context, province)
    }
    val requestRegion = region
    val requestQuery = query
    val requestPrecise = precise
    val requestSubmitted = submitted
    val requestOnline = onlineEnabled
    val requestGeneration = generation
    val requestArea = listOf(province, municipality).filter(String::isNotBlank).distinct()
    LaunchedEffect(requestQuery, requestPrecise, requestSubmitted, requestOnline, retry, requestGeneration, requestRegion) {
        if (generation != requestGeneration) return@LaunchedEffect
        fun publish(value: SearchUiState) {
            if (generation == requestGeneration) state = value
        }
        val term = requestQuery.trim()
        publish(SearchUiState(query = requestQuery, preciseAddress = requestPrecise, region = requestRegion))
        if (term.isEmpty() && requestArea.isEmpty()) return@LaunchedEffect
        val scopedTerm = (requestArea + term).filter(String::isNotBlank).joinToString(" ")
        fun scoped(values: List<City>) = values.filter { cityMatchesSearchArea(it, requestArea) }
        delay(100)
        val local = withContext(ioDispatcher) {
            scoped(filterPlaceRegion(CityRepository.searchLocal(scopedTerm), requestRegion))
        }
        if (generation != requestGeneration) return@LaunchedEffect
        publish(state.copy(results = local, indexUnavailable = LocalPlaces.unavailable))
        if (requestSubmitted != term || !requestOnline) return@LaunchedEffect
        publish(state.copy(searching = true, onlineAttempted = true))
        try {
            val onlineTerm = if (requestPrecise) scopedTerm else term.ifBlank { requestArea.lastOrNull().orEmpty() }
            val online = withContext(ioDispatcher) {
                CityRepository.searchOnline(onlineTerm, requestPrecise, requestRegion, requestArea)
            }
            publish(
                state.copy(
                    results = scoped(filterPlaceRegion(mergePlaceSearchResults(local, online.cities), requestRegion)),
                    failure = online.failure,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publish(state.copy(failure = PlaceFailure.UNAVAILABLE))
        } finally {
            publish(state.copy(searching = false))
        }
    }

    fun onQueryChanged(next: String) {
        query = next.take(120)
        submitted = null
        generation++
        state = SearchUiState(query = query, preciseAddress = precise, region = region)
    }

    fun onSearch() {
        submitted = query.trim()
        retry++
        generation++
    }

    fun onRegionChanged(next: PlaceRegion) {
        region = next
        province = ""
        municipality = ""
        submitted = null
        generation++
        state = SearchUiState(query = query, preciseAddress = precise, region = region)
    }

    fun onScopeChanged(next: Boolean) {
        precise = next
        submitted = null
        generation++
        state = SearchUiState(query = query, preciseAddress = precise, region = region)
    }

    fun onAreaChanged(first: String, second: String) {
        province = first
        municipality = second
        submitted = null
        generation++
    }

    val uiState = state.copy(
        query = query,
        preciseAddress = precise,
        region = region,
        province = province,
        municipality = municipality,
        provinces = provinces,
        municipalities = municipalities,
    )

    val placeTools = rememberSearchPlaceToolsState()

    MochiPageFrame(
        title = "添加地点",
        subtitle = "搜索城市、区县或街道",
        onBack = onBack,
        actions = {
            MochiBarIconButton(onClick = placeTools::openManualDialog) {
                MochiIonicon(name = "add", contentDescription = "自定义经纬度", size = 22.dp)
            }
        },
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MochiSearchBar(
                    query = query,
                    onQueryChange = ::onQueryChanged,
                    placeholder = if (precise) "城市、街道或地点" else "城市、区县或拼音",
                    onSearch = { onSearch() },
                    onCancel = { onQueryChanged("") },
                    modifier = Modifier.weight(1f),
                )
                // 省市联级只对国内有意义，海外没有省可选，筛选按钮直接收起来。
                if (region == PlaceRegion.DOMESTIC) {
                    MochiBarIconButton(onClick = placeTools::openAreaSheet) {
                        MochiIonicon(name = "funnel", contentDescription = "地区筛选", size = 22.dp)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MochiSegmentedControl(
                    selectedIndex = if (region == PlaceRegion.DOMESTIC) 0 else 1,
                    onSelect = { onRegionChanged(if (it == 0) PlaceRegion.DOMESTIC else PlaceRegion.OVERSEAS) },
                    items = persistentListOf("国内", "海外"),
                    modifier = Modifier.weight(1f),
                )
                MochiChip(
                    label = "联网搜索",
                    selected = onlineEnabled,
                    onClick = { onlineEnabled = !onlineEnabled },
                    // 开关两个方向都要有图标动画：固定给一个 16dp 图标位，
                    // 对勾与「离线」图标交叉淡变，避免关闭时图标直接跳出来、对勾却在擦除。
                    leadingIcon = { OnlineSearchIcon(online = onlineEnabled) },
                )
            }
        }
        item {
            MochiSegmentedControl(
                selectedIndex = if (precise) 1 else 0,
                onSelect = { onScopeChanged(it == 1) },
                items = persistentListOf("城市与区县", "街道与地点"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (amapEnhanced && region == PlaceRegion.DOMESTIC) {
            item { MochiListRow(title = "已接入你的高德 Key，街道和地点优先用高德查找") }
        }
        if (uiState.indexUnavailable) {
            item { MochiListRow(title = "本地地名数据暂不可用，可选收藏或联网搜索") }
        }
        if (state.failure != null) {
            item {
                MochiListRow(
                    title = if (state.failure == PlaceFailure.CONFIG_CHANGED) "接入设置已变化，请重新搜索"
                    else "搜索暂时不可用，请稍后重试",
                    destructive = true,
                )
            }
        }
        if (state.searching) {
            item { MochiListRow(title = "正在补充在线结果…") }
        }
        // 搜索结果在说明卡片上面：先看到结果，再看怎么用。
        if (state.results.isNotEmpty()) {
            item {
                MochiListSection(header = "${region.label} · ${state.results.size} 个结果") {
                    state.results.forEach { city ->
                        MochiListRow(
                            title = city.displayName,
                            subtitle = listOfNotNull(city.affiliation, city.street?.takeIf { it.isNotBlank() })
                                .joinToString(" · ").ifBlank { null },
                            leading = {
                                MochiIonicon(name = "location", size = 20.dp)
                            },
                            leadingStyle = MochiListLeadingStyle.Symbol,
                            chevron = true,
                            onClick = { pendingCity = city },
                        )
                    }
                }
            }
        } else if (query.isNotBlank() && !state.searching && state.failure == null && !uiState.indexUnavailable) {
            item { MochiListRow(title = "没有找到匹配的地点，换个关键词或开启联网搜索") }
        }
        if (query.isBlank()) {
            item {
                MochiListSection(
                    header = if (precise) "搜索街道或地点" else "查找天气地点",
                    footer = if (region == PlaceRegion.DOMESTIC) "国内范围含港澳台" else "海外结果会标注国家或地区",
                ) {
                    // 说明整句展示，不塞进会截断的列表行。
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SearchTip("输入城市、区县或拼音，本地结果会即时出现。")
                        SearchTip("联网搜索开启时，按键盘上的搜索键会补充在线结果。")
                        SearchTip("点任意结果，确认后添加到首页。")
                    }
                }
            }
        }
    }
    AreaWheelSheet(uiState, placeTools, ::onAreaChanged)
    ManualPlaceDialog(placeTools, onCityPicked)
    pendingCity?.let { city ->
        MochiConfirmationDialog(
            visible = true,
            onDismissRequest = { pendingCity = null },
            actions = persistentListOf(
                MochiDialogAction("添加到首页", MochiDialogActionRole.Primary) {
                    pendingCity = null
                    onCityPicked(city)
                },
                MochiDialogAction("取消", MochiDialogActionRole.Cancel) { pendingCity = null },
            ),
            title = "添加到首页",
            message = "将「${city.displayName}」添加到首页，并用它查看天气？",
        )
    }
}

@Composable
private fun SearchTip(text: String) {
    Text(text, style = MochiTheme.textStyles.body, color = MochiTheme.colorScheme.onSurface)
}

/** 联网搜索开关的图标位：对勾与离线图标交叉淡变，两个方向都有动画，宽度保持不变。 */
@Composable
private fun OnlineSearchIcon(online: Boolean) {
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Crossfade(targetState = online, animationSpec = tween(180), label = "onlineSearchIcon") { enabled ->
            MochiIonicon(
                name = if (enabled) "checkmark" else "cloud-offline",
                size = 16.dp,
            )
        }
    }
}
