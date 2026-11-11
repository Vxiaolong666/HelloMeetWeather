package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.LocalPlaces
import com.zhisheng.weather.data.PlaceFailure
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.WeatherRepository
import com.zhisheng.weather.data.cityMatchesSearchArea
import com.zhisheng.weather.data.mergePlaceSearchResults
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.PlaceRegion
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.model.filterPlaceRegion
import com.zhisheng.weather.ui.home.tempDelta
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiSearchBar
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import kotlin.math.abs

/*
 * 四个详情二级页的 mochi 版：昨日天气 / 短时降水 / 当日详细天气 / 添加地点。
 * 页面骨架、分组、列表行、分段控件、搜索框、返回按钮全部使用库组件；
 * 降水曲线、逐时温度曲线、地点筛选工具是库无对应能力的业务自绘，作为整体复用。
 */

@Composable
fun MochiYesterdayDetailScreen(
    yesterday: YesterdayInfo?,
    today: DailyWeather?,
    cityName: String?,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    val dateLabel = yesterday?.dateMillis?.let { Fmt.date(it, utcOffsetSeconds) }
    val condition = yesterday?.condition?.takeIf { it != WeatherCondition.UNKNOWN }
    MochiPageFrame(
        title = "昨日天气",
        subtitle = listOfNotNull(cityName, dateLabel).joinToString(" · ").ifBlank { "YESTERDAY REVIEW" },
        onBack = onBack,
    ) {
        if (yesterday == null) {
            item { MochiListRow(title = "暂无昨日天气数据") }
            return@MochiPageFrame
        }
        item {
            MochiListSection(header = "高低温与空气质量", footer = condition?.label ?: "天气未知") {
                MochiListRow(title = "最高温", value = Fmt.temp(yesterday.high, tempUnit)?.let { "$it°" } ?: "—")
                MochiListRow(title = "最低温", value = Fmt.temp(yesterday.low, tempUnit)?.let { "$it°" } ?: "—")
                MochiListRow(title = "AQI", value = yesterday.aqi?.toString() ?: "—")
            }
        }
        tempDelta(today?.high, yesterday.high, tempUnit)?.let { diff ->
            item {
                MochiListSection(header = "较今日最高温") {
                    MochiListRow(
                        title = when {
                            diff > 0 -> "今日最高温比昨天高 ${abs(diff)}°"
                            diff < 0 -> "今日最高温比昨天低 ${abs(diff)}°"
                            else -> "今日最高温与昨天持平"
                        },
                        value = when {
                            diff > 0 -> "+${diff}°"
                            diff < 0 -> "$diff°"
                            else -> "0°"
                        },
                    )
                }
            }
        }
        item {
            MochiListSection(header = "天气与风") {
                MochiListRow(title = "天气转折", value = mochiWeatherTransition(yesterday))
                MochiListRow(title = "风向", value = mochiWindTransition(yesterday))
                MochiListRow(title = "风速", value = mochiWindSpeedLabel(yesterday, windUnit))
                MochiListRow(title = "日出", value = yesterday.sunrise?.takeIf(String::isNotBlank) ?: "—")
                MochiListRow(title = "日落", value = yesterday.sunset?.takeIf(String::isNotBlank) ?: "—")
            }
        }
    }
}

private fun mochiWeatherTransition(yesterday: YesterdayInfo): String {
    val start = yesterday.weatherStart?.takeIf { it != WeatherCondition.UNKNOWN }
    val end = yesterday.weatherEnd?.takeIf { it != WeatherCondition.UNKNOWN }
    return when {
        start != null && end != null && start != end -> "${start.label} 转 ${end.label}"
        start != null -> start.label
        end != null -> end.label
        yesterday.condition != null && yesterday.condition != WeatherCondition.UNKNOWN -> yesterday.condition.label
        else -> "—"
    }
}

private fun mochiWindTransition(yesterday: YesterdayInfo): String {
    val start = WeatherRepository.windDirection(yesterday.windDirectionStartDeg)
    val end = WeatherRepository.windDirection(yesterday.windDirectionEndDeg)
    return when {
        start != null && end != null && start != end -> "$start 转 $end"
        start != null -> start
        end != null -> end
        else -> "—"
    }
}

private fun mochiWindSpeedLabel(yesterday: YesterdayInfo, windUnit: String): String =
    listOfNotNull(yesterday.windSpeedStart, yesterday.windSpeedEnd).maxOrNull()
        ?.let { Fmt.wind(it, windUnit) } ?: "—"

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
internal fun MochiDailyDayDetailScreen(
    city: City?,
    days: List<DailyWeather>,
    yesterday: YesterdayInfo?,
    dateMillis: Long,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    hourly: List<HourlyWeather>,
    onBack: () -> Unit,
) {
    val nowMillis = weatherPresentationTime()
    val track = (listOfNotNull(yesterdayForecastDay(yesterday, utcOffsetSeconds, nowMillis)) + days)
        .distinctBy { it.dateMillis }
        .sortedBy { it.dateMillis }
    var selectedDate by rememberSaveable(city?.locationKey, dateMillis) { mutableStateOf(dateMillis) }
    val day = track.firstOrNull { it.dateMillis == selectedDate }
        ?: track.minByOrNull { abs(it.dateMillis - selectedDate) }
    val index = day?.let { target -> track.indexOfFirst { it.dateMillis == target.dateMillis } } ?: -1
    val prevDate = track.getOrNull(index - 1)?.dateMillis
    val nextDate = track.getOrNull(index + 1)?.dateMillis
    // LazyListScope 不是 Composable 上下文，remember 等必须在这里提前算好。
    val visual = day?.let { forecastDayNightVisual(it) }
    val hours = remember(hourly, day?.dateMillis, utcOffsetSeconds) {
        day?.let { forecastHoursForDate(hourly, it.dateMillis, utcOffsetSeconds) }.orEmpty()
    }
    val facts = day?.let { vistaForecastFacts(it, windUnit) }.orEmpty()

    MochiPageFrame(title = "当日详细天气", subtitle = city?.displayName, onBack = onBack) {
        if (day == null || visual == null) {
            item { MochiListRow(title = "暂无这一天的预报数据") }
            return@MochiPageFrame
        }
        item {
            MochiListSection(header = "日期切换") {
                MochiListRow(
                    title = "前一天",
                    enabled = prevDate != null,
                    onClick = { prevDate?.let { selectedDate = it } },
                )
                MochiListRow(
                    title = "${forecastTemporalLabel(day.dateMillis, utcOffsetSeconds)} · ${forecastDateLabel(day.dateMillis, utcOffsetSeconds)}",
                    value = day.weatherText ?: day.condition?.label ?: "天气预报",
                )
                MochiListRow(
                    title = "后一天",
                    enabled = nextDate != null,
                    onClick = { nextDate?.let { selectedDate = it } },
                )
            }
        }
        item {
            MochiListSection(header = "天气概览") {
                MochiListRow(title = "最低温", value = Fmt.temp(day.low, tempUnit)?.let { "$it°" } ?: "—")
                MochiListRow(title = "最高温", value = Fmt.temp(day.high, tempUnit)?.let { "$it°" } ?: "—")
                MochiListRow(title = "白天", value = visual.dayLabel)
                MochiListRow(title = "夜间", value = visual.nightLabel)
            }
        }
        if (forecastHasHourlyTemperatures(hours)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ForecastHourlyTemperature(hours, tempUnit, utcOffsetSeconds)
                }
            }
        }
        if (facts.isNotEmpty()) {
            item {
                MochiListSection(header = "当日详情") {
                    facts.forEach { (label, value) ->
                        MochiListRow(title = label, value = value)
                    }
                }
            }
        }
        item { MochiListRow(title = "点「前一天 / 后一天」可切换日期") }
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
    val requestGeneration = generation
    val requestArea = listOf(province, municipality).filter(String::isNotBlank).distinct()
    LaunchedEffect(requestQuery, requestPrecise, requestSubmitted, retry, requestGeneration, requestRegion) {
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
        if (requestSubmitted != term) return@LaunchedEffect
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

    MochiPageFrame(title = "添加地点", subtitle = "搜索城市、区县或街道", onBack = onBack) {
        item {
            MochiSearchBar(
                query = query,
                onQueryChange = ::onQueryChanged,
                placeholder = if (precise) "城市、街道或地点" else "城市、区县或拼音",
                onSearch = { onSearch() },
                onCancel = { onQueryChanged("") },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        item {
            MochiSegmentedControl(
                selectedIndex = if (region == PlaceRegion.DOMESTIC) 0 else 1,
                onSelect = { onRegionChanged(if (it == 0) PlaceRegion.DOMESTIC else PlaceRegion.OVERSEAS) },
                items = persistentListOf("国内", "海外"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item {
            MochiSegmentedControl(
                selectedIndex = if (precise) 1 else 0,
                onSelect = { onScopeChanged(it == 1) },
                items = persistentListOf("城市与区县", "街道与地点"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item { SearchPlaceTools(uiState, ::onAreaChanged, onCityPicked) }
        item {
            MochiListSection(header = "${region.label} · ${state.results.size} 个结果") {
                MochiListRow(
                    title = if (state.searching) "搜索中…" else "联网搜索",
                    enabled = query.isNotBlank() && !state.searching,
                    onClick = { onSearch() },
                )
                if (amapEnhanced && region == PlaceRegion.DOMESTIC) {
                    MochiListRow(title = "已接入你的高德 Key，街道和地点优先用高德查找")
                }
            }
        }
        if (query.isBlank()) {
            item {
                MochiListSection(
                    header = if (precise) "搜索街道或地点" else "查找天气地点",
                    footer = if (region == PlaceRegion.DOMESTIC) "国内范围含港澳台" else "海外结果会标注国家或地区",
                ) {
                    MochiListRow(title = "输入城市名、区县或拼音，本地结果即时出现")
                    MochiListRow(title = "没有合适结果时，点「联网搜索」补充")
                    MochiListRow(title = "点任意结果即可添加到首页")
                }
            }
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
        if (state.results.isEmpty() && query.isNotBlank() && !state.searching) {
            item { MochiListRow(title = "没有找到匹配的地点") }
        }
        if (state.results.isNotEmpty()) {
            item {
                MochiListSection(header = "搜索结果") {
                    state.results.forEach { city ->
                        MochiListRow(
                            title = city.displayName,
                            subtitle = listOfNotNull(city.affiliation, city.street?.takeIf { it.isNotBlank() })
                                .joinToString(" · ").ifBlank { null },
                            chevron = true,
                            onClick = { onCityPicked(city) },
                        )
                    }
                }
            }
        }
    }
}
