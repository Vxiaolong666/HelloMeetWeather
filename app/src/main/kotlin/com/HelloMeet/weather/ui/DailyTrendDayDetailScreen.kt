package com.HelloMeet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HelloMeet.weather.data.OpenMeteoApi
import com.HelloMeet.weather.data.WeatherRepository
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.DailyWeather
import com.HelloMeet.weather.model.HourlyWeather
import com.HelloMeet.weather.model.YesterdayInfo
import com.HelloMeet.weather.model.cityZone
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListSectionHeader

private val dailyTrendDateFmt = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
private val dailyTrendWeekFmt = DateTimeFormatter.ofPattern("EEE", Locale.CHINA)

/**
 * 未来 15 日逐日详情页（迁移 yunsong ForecastDetailActivity 的逐日详情）：由「多日天气预报」
 * （首页「五天天气」标题行右侧「更多」→ DAILY_TREND）点选某天进入。顶部日期分页条
 * （每屏 7 天、选中高亮）+「信息摘要」+「返回今日」+ 信息卡（气温范围渐变条 + 当前标记 +
 * 降水/风力/紫外线/空气质量四格）+ 指标分页胶囊（温度/体感、降水、风力、紫外线、湿度、
 * 气压、能见度、空气）+ 渐变面积图（彩线 = 主值、灰/浅蓝线 = 气温/阵风）。点选图表某
 * 时刻触发 [HapticFeedbackType.TextHandleMove] 震动。外壳沿用库 [MochiPageFrame]。
 */
@Composable
internal fun DailyTrendDayDetailScreen(
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
    // 信息摘要「返回今日」的目标日期：track 里今天那天的 dateMillis（不在列表时按钮禁用）。
    val todayMillis = track.firstOrNull { isDailyToday(it.dateMillis) }?.dateMillis
    // 富逐日详情页专用 open-meteo 逐时：主源逐时仅约 24h，未来日缺逐时会「无数据」。
    // 此页单独拉 16 天逐时补缺（omToHourly 过滤历史段），失败回退主源逐时；
    // 只作用于本页，不得影响其它页面的主源逐时。
    var omHourly by remember(city?.locationKey) { mutableStateOf<List<HourlyWeather>?>(null) }
    androidx.compose.runtime.LaunchedEffect(city?.locationKey, city?.latitude, city?.longitude, city?.isPreciseLocation) {
        val c = city ?: return@LaunchedEffect
        omHourly = OpenMeteoApi.fetchHourlyFull(c.latitude, c.longitude, c.isPreciseLocation)
            ?.let { WeatherRepository.omToHourly(it) }
    }
    // 合并主源与 open-meteo：主源逐时保留（含空气质量等主源独有字段，open-meteo 不提供 AQI），
    // open-meteo 只补主源缺的时刻（约 24h 之后），保证今天的数据不被覆盖、未来日有逐时。
    val effectiveHourly = remember(hourly, omHourly) {
        val om = omHourly
        if (om.isNullOrEmpty()) hourly
        else {
            val primaryByTime = hourly.associateBy { it.timeMillis }
            val merged = primaryByTime.values.toMutableList()
            om.forEach { omHour -> if (omHour.timeMillis !in primaryByTime) merged += omHour }
            merged.sortedBy { it.timeMillis }
        }
    }
    val hours = remember(effectiveHourly, day?.dateMillis, utcOffsetSeconds) {
        day?.let { forecastHoursForDate(effectiveHourly, it.dateMillis, utcOffsetSeconds) }.orEmpty()
    }
    val isLight = LocalZhishengPalette.current.isLight
    val palette = remember(isLight) { TrendChartPalette.forTheme(isLight) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    MochiPageFrame(
        title = "未来15日天气详情",
        subtitle = city?.displayName,
        onBack = onBack,
    ) {
        if (day == null) {
            item {
                MochiPageCard(animateHeight = false) {
                    Text(
                        "暂无这一天的预报数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                    )
                }
            }
            return@MochiPageFrame
        }

        item {
            DailyTrendDateChips(track, selectedDate, utcOffsetSeconds) { selectedDate = it }
        }
        item {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        MochiListSectionHeader("信息摘要")
                    }
                    MochiButton(
                        onClick = { todayMillis?.let { selectedDate = it } },
                        enabled = todayMillis != null && selectedDate != todayMillis,
                        style = MochiButtonStyle.Text,
                        minHeight = 30.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = MochiListDefaults.TextRowPadding)
                            .offset(y = -(MochiListDefaults.HeaderGap / 2f)),
                    ) { Text("返回今日") }
                }
                MochiPageCard(animateHeight = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DailyTrendSummaryCard(day, hours, tempUnit, windUnit, nowMillis, palette, isLight)
                    }
                }
            }
        }
        item {
            DailyTrendChartPanel(day, hours, tempUnit, windUnit, isLight, palette, haptic)
        }
    }
}

// ─────────────────────────── 日期分页条 ───────────────────────────

@Composable
private fun DailyTrendDateChips(
    track: List<DailyWeather>,
    selectedDate: Long,
    utcOffsetSeconds: Int?,
    onSelect: (Long) -> Unit,
) {
    val isLight = LocalZhishengPalette.current.isLight
    val selectedBg = if (isLight) Color(0xFFF8FAFC) else Color.White.copy(alpha = 0x48 / 255f)
    val scroll = rememberScrollState()
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            track.forEach { d ->
                val selected = d.dateMillis == selectedDate
                val label = Instant.ofEpochMilli(d.dateMillis).atZone(cityZone(utcOffsetSeconds))
                Box(
                    Modifier
                        .background(
                            color = if (selected) selectedBg else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(d.dateMillis) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${label.format(dailyTrendDateFmt)}\n${label.format(dailyTrendWeekFmt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else ZhishengTextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

// ─────────────────────────── 信息摘要卡（非紧凑） ───────────────────────────

@Composable
private fun DailyTrendSummaryCard(
    day: DailyWeather,
    hours: List<HourlyWeather>,
    tempUnit: String,
    windUnit: String,
    nowMillis: Long,
    palette: TrendChartPalette,
    isLight: Boolean,
) {
    val metrics = buildDailyTrendSummaryMetrics(day, hours, tempUnit, windUnit, nowMillis, palette)
    val temperature = metrics.first { it.kind == TrendSummaryKind.TEMPERATURE }
    val others = metrics.filter { it.kind != TrendSummaryKind.TEMPERATURE }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("气温范围", style = MaterialTheme.typography.labelMedium, color = ZhishengTextTertiary)
        Text(
            temperature.value,
            style = MaterialTheme.typography.headlineSmall,
            color = temperature.color,
            fontWeight = FontWeight.Bold,
        )
        DailyTrendTempTrack(
            progress = temperature.currentProgress,
            showCurrent = temperature.currentValue != null,
            currentLabel = temperature.currentValue,
            isLight = isLight,
        )
        Text(
            temperature.detail,
            style = MaterialTheme.typography.bodySmall,
            color = ZhishengTextSecondary,
        )
        Text(
            temperature.advice,
            style = MaterialTheme.typography.bodySmall,
            color = ZhishengTextSecondary,
        )
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            others.take(2).forEach { m -> Hourly48SummaryQuadCell(m, Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            others.drop(2).forEach { m -> Hourly48SummaryQuadCell(m, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DailyTrendTempTrack(
    progress: Float?,
    showCurrent: Boolean,
    currentLabel: String?,
    isLight: Boolean,
) {
    Column {
        if (showCurrent) {
            Text(
                "当前 $currentLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val y = size.height / 2f
            drawLine(
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 4.dp.toPx(),
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(TrendTempRangeColors),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            if (progress != null) {
                val x = size.width * progress.coerceIn(0f, 1f)
                drawTrendMarker(Offset(x, y), trendTempRangeColor(progress), isLight, 1.dp.toPx())
            }
        }
    }
}

internal fun buildDailyTrendSummaryMetrics(
    day: DailyWeather,
    hours: List<HourlyWeather>,
    tempUnit: String,
    windUnit: String,
    nowMillis: Long,
    palette: TrendChartPalette,
): List<TrendSummaryMetric> {
    val low = day.low?.takeIf(Double::isFinite)
    val high = day.high?.takeIf(Double::isFinite)
    val temperatureValue = if (low != null && high != null) "${degreeText(low, tempUnit)}–${degreeText(high, tempUnit)}" else "—"
    val tempSpread = ((high ?: 0.0) - (low ?: 0.0)).coerceAtLeast(0.0)
    val tempProgress = (tempSpread / 14.0).toFloat().coerceIn(0f, 1f)
    val isToday = isDailyToday(day.dateMillis)
    val currentTemp = hours.firstOrNull { it.timeMillis >= nowMillis }?.temperature ?: hours.firstOrNull()?.temperature
    val currentProgress = if (low != null && high != null && high - low > 0.5) {
        ((currentTemp ?: low) - low) / (high - low)
    } else 0.5
    val detail = "${day.condition?.label ?: "天气"} · 当日温差${tempSpread.roundToInt()}°".trim(' ', '·')
    val advice = when {
        (high ?: 0.0) >= 33 -> "天气炎热，轻薄着装并及时补水"
        (low ?: 0.0) <= 5 -> "早晚寒冷，外出注意添衣保暖"
        tempSpread >= 8 -> "昼夜温差较大，建议分层穿衣"
        (high ?: 0.0) < 18 -> "天气偏凉，建议备一件外套"
        else -> "温度较舒适，按需增减衣物"
    }

    val rainPercent = day.precipProbability
    val rainValue = when {
        rainPercent == null -> "—"
        rainPercent == 0 -> "无雨"
        else -> "$rainPercent%"
    }
    val rainAdvice = when {
        rainPercent == null -> "以已提供的数据为准"
        rainPercent <= 0 -> "今日无需备伞"
        else -> trendRainAdvice(rainPercent)
    }

    val winds = hours.mapNotNull { it.windSpeed?.takeIf(Double::isFinite) }
    val windLow = winds.minOrNull() ?: 0.0
    val windHigh = winds.maxOrNull() ?: 0.0
    val windValue = "${windLow.roundToInt()}–${windHigh.roundToInt()}${windUnitLabel(windUnit)}"

    val uvPeak = hours.maxByOrNull { it.uvIndex ?: 0 }?.uvIndex
    val uvValue = uvPeak?.let { "${it}级" } ?: "—"
    val uvDetail = uvPeak?.let { "${trendUvSemantic(it)} · ${trendCompactTime(hours.maxByOrNull { h -> h.uvIndex ?: 0 }?.timeMillis ?: day.dateMillis, nowMillis)}" } ?: "当日数据暂缺"

    val aqiVals = hours.mapNotNull { it.aqi?.toDouble()?.takeIf(Double::isFinite) }
    val aqiBest = aqiVals.minOrNull()
    val aqiWorst = aqiVals.maxOrNull()
    val aqiValue = if (aqiBest != null && aqiWorst != null) "AQI ${aqiBest.roundToInt()}–${aqiWorst.roundToInt()}" else "—"
    val aqiDetail = if (aqiBest != null && aqiWorst != null) trendAirRangeSummary(aqiBest, aqiWorst) else "当日数据暂缺"

    return listOf(
        TrendSummaryMetric(TrendSummaryKind.TEMPERATURE, "气温范围", temperatureValue, detail, advice, tempProgress, palette.temperature,
            currentValue = if (isToday) currentTemp?.let { degreeText(it, tempUnit) } else null,
            currentProgress = if (isToday) currentProgress.toFloat() else null),
        TrendSummaryMetric(TrendSummaryKind.RAIN, "降水概率", rainValue, "当日", rainAdvice, (rainPercent ?: 0) / 100f, palette.rain),
        TrendSummaryMetric(TrendSummaryKind.WIND, "风力范围", windValue, "当日", trendWindAdvice(windHigh), (windHigh / 45.0).toFloat().coerceIn(0f, 1f), palette.wind),
        TrendSummaryMetric(TrendSummaryKind.UV, "紫外线峰值", uvValue, uvDetail, (uvPeak ?: 0).let { trendUvAdvice(it.toDouble()) }, ((uvPeak ?: 0) / 11f).coerceIn(0f, 1f), palette.ultraviolet),
        TrendSummaryMetric(TrendSummaryKind.AQI, "空气质量", aqiValue, aqiDetail, aqiWorst?.let { trendAirAdvice(it) } ?: "以已提供的数据为准", ((aqiWorst ?: 0.0) / 200.0).toFloat().coerceIn(0f, 1f), palette.aqi),
    ).map { if (it.value == "—") it.copy(detail = "当日数据暂缺", advice = "以已提供的数据为准") else it }
}

// ─────────────────────────── 图表面板（胶囊 + 渐变图 + 读数） ───────────────────────────

@Composable
private fun DailyTrendChartPanel(
    day: DailyWeather,
    hours: List<HourlyWeather>,
    tempUnit: String,
    windUnit: String,
    isLight: Boolean,
    palette: TrendChartPalette,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    var metric by rememberSaveable(day.dateMillis) { mutableStateOf(DailyTrendMetric.TEMPERATURE) }
    val nowMillis = weatherPresentationTime()
    var selected by remember(hours, day.dateMillis) {
        mutableIntStateOf(hours.indices.minByOrNull { abs(hours[it].timeMillis - nowMillis) }?.coerceAtLeast(0) ?: 0)
    }
    androidx.compose.runtime.LaunchedEffect(day.dateMillis, haptic) {
        androidx.compose.runtime.snapshotFlow { selected }.drop(1).collect {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }

    MochiPageCard(animateHeight = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val segmentLabels = remember { DailyTrendMetric.entries.map { it.segment }.toImmutableList() }
            MochiSegmentedControl(
                selectedIndex = DailyTrendMetric.entries.indexOf(metric).coerceAtLeast(0),
                onSelect = { i -> DailyTrendMetric.entries.getOrNull(i)?.let { metric = it } },
                items = segmentLabels,
                modifier = Modifier.fillMaxWidth(),
            )
            val safeIndex = selected.coerceIn(0, (hours.size - 1).coerceAtLeast(0))
            val hour = hours.getOrNull(safeIndex)
            val value = hour?.let { metric.value(it) }
            val second = hour?.let { metric.secondary(it) }
            val reading = if (hour != null) dailyTrendReading(metric, hour, tempUnit, windUnit) else "暂无记录"
            Text(
                reading,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${trendTimeLabel(hour?.timeMillis ?: day.dateMillis)} · ${dailyTrendMeta(metric, value, second, tempUnit, windUnit)}",
                style = MaterialTheme.typography.bodySmall,
                color = ZhishengTextSecondary,
            )
            DailyForecastGradientChart(
                hours = hours,
                metric = metric,
                tempUnit = tempUnit,
                windUnit = windUnit,
                isLight = isLight,
                selected = safeIndex,
                onSelect = { selected = it },
            )
            val count = hours.count { metric.value(it) != null || metric.secondary(it) != null }
            Text(
                if (count == 0) "当前天气源未提供当日${metric.title}数据"
                else if (isDailyToday(day.dateMillis)) "历史时段为本机保存的同源预报 · 已有$count/${hours.size}小时"
                else "当日已有$count/${hours.size}小时 · 空缺时段不作推算",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
            )
        }
    }
}

private fun dailyTrendMeta(
    metric: DailyTrendMetric,
    value: Double?,
    second: Double?,
    tempUnit: String,
    windUnit: String,
): String = when (metric) {
    DailyTrendMetric.TEMPERATURE -> {
        val cmp = when {
            value == null -> "体感数据暂无，暂不能计算温差"
            second == null -> "气温数据暂无，暂不能计算温差"
            else -> {
                val delta = ((value - second) * if (tempUnit == "f") 1.8 else 1.0).roundToInt()
                when {
                    delta == 0 -> "与实际温度相同（温差 0${if (tempUnit == "f") "°F" else "°C"}）"
                    delta > 0 -> "较实际温度高 $delta${if (tempUnit == "f") "°F" else "°C"}"
                    else -> "较实际温度低 ${-delta}${if (tempUnit == "f") "°F" else "°C"}"
                }
            }
        }
        "$cmp · ${trendFeelsLevel(value)}"
    }
    DailyTrendMetric.RAIN -> value?.roundToInt()?.let(::trendRainAdvice) ?: "该时段暂无记录"
    // 其余指标复用「天气详情」页逐时语义建议（yunsong 口径）：按当前值给出可执行提示，
    // 有数据不再显示「该时段暂无记录」。风 / 紫外线 / 湿度 / 气压 / 能见度共用 MetricKind.advice。
    DailyTrendMetric.WIND -> MetricKind.WIND.advice(value)
    DailyTrendMetric.UV -> MetricKind.UV.advice(value)
    DailyTrendMetric.HUMIDITY -> MetricKind.HUMIDITY.advice(value)
    DailyTrendMetric.PRESSURE -> MetricKind.PRESSURE.advice(value)
    DailyTrendMetric.VISIBILITY -> MetricKind.VISIBILITY.advice(value)
    DailyTrendMetric.AQI -> value?.let(::trendAirAdvice) ?: "该时段暂无记录"
}

private fun trendFeelsLevel(value: Double?): String = when {
    value == null -> "暂无"
    value >= 33 -> "炎热"
    value >= 28 -> "较热"
    value >= 24 -> "舒适"
    value >= 18 -> "凉爽"
    value >= 10 -> "较冷"
    else -> "寒冷"
}

private fun isDailyToday(dateMillis: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    return Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate() == today
}
