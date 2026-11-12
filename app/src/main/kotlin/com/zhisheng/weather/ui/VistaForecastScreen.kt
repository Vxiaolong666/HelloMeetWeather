@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.*
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiSegmentedControl
import java.util.Locale

@Composable
internal fun VistaForecastScreen(
    city: City?, days: List<DailyWeather>, yesterday: YesterdayInfo?,
    tempUnit: String, windUnit: String, utcOffsetSeconds: Int?, onBack: () -> Unit,
    hourly: List<HourlyWeather> = emptyList(),
    initialDateMillis: Long? = null,
) {
    val chrome = LocalZhishengChrome.current
    val nowMillis = weatherPresentationTime()
    val track = (listOfNotNull(yesterdayForecastDay(yesterday, utcOffsetSeconds, nowMillis)) + days)
        .distinctBy { it.dateMillis }.sortedBy { it.dateMillis }
    var selectedDate by rememberSaveable(city?.locationKey, initialDateMillis) {
        mutableStateOf(initialDateMillis ?: days.firstOrNull()?.dateMillis ?: track.firstOrNull()?.dateMillis)
    }
    var vertical by rememberSaveable { mutableStateOf(false) }
    val selected = track.firstOrNull { it.dateMillis == selectedDate } ?: days.firstOrNull() ?: track.firstOrNull()
    val selectedHours = remember(hourly, selected?.dateMillis, utcOffsetSeconds) {
        selected?.let { forecastHoursForDate(hourly, it.dateMillis, utcOffsetSeconds) } ?: emptyList()
    }
    // 与设置二级页完全同一个外壳：库标题脚手架 + 液态玻璃材质 + 越界手感。
    MochiPageFrame(
        title = if (days.isEmpty()) "多天天气" else "${days.size}日天气预报",
        subtitle = city?.displayName?.takeIf { it.isNotBlank() },
        onBack = onBack,
    ) {
        item {
            MochiSegmentedControl(
                selectedIndex = if (vertical) 1 else 0,
                onSelect = { vertical = it == 1 },
                items = persistentListOf("横向", "纵向"),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        if (!vertical) selected?.let { day ->
            val dayIndex = track.indexOfFirst { it.dateMillis == day.dateMillis }
            val prevDate = track.getOrNull(dayIndex - 1)?.dateMillis
            val nextDate = track.getOrNull(dayIndex + 1)?.dateMillis
            item {
                ForecastDateHeader(
                    dateLabel = "${forecastTemporalLabel(day.dateMillis, utcOffsetSeconds)} · ${forecastDateLabel(day.dateMillis, utcOffsetSeconds)} · ${day.weatherText ?: day.condition?.label ?: "天气预报"}",
                    canPrev = prevDate != null,
                    canNext = nextDate != null,
                    onPrev = { prevDate?.let { selectedDate = it } },
                    onNext = { nextDate?.let { selectedDate = it } },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
            if (vertical) {
                item {
                    ForecastVerticalList(
                        days = track,
                        selectedMillis = selectedDate,
                        tempUnit = tempUnit,
                        windUnit = windUnit,
                        utcOffsetSeconds = utcOffsetSeconds,
                        hourly = hourly,
                        onSelect = { selectedDate = it },
                    )
                }
            } else if (selected == null) {
                item { Text("暂无逐日预报，返回主页刷新后再试", color = ZhishengTextSecondary) }
            } else {
                val dayIndex = track.indexOfFirst { it.dateMillis == selected.dateMillis }
                val prevDate = track.getOrNull(dayIndex - 1)?.dateMillis
                val nextDate = track.getOrNull(dayIndex + 1)?.dateMillis
                item {
                    ForecastDateTrack(track, selected.dateMillis, tempUnit, utcOffsetSeconds) { selectedDate = it }
                }
                if (forecastHasHourlyTemperatures(selectedHours)) item {
                    ForecastHourlyTemperature(selectedHours, tempUnit, utcOffsetSeconds)
                }
                item {
                    val visual = forecastDayNightVisual(selected)
                    Column(
                        Modifier.fillMaxWidth().swipeAdjacentDays(
                            onPrev = { prevDate?.let { selectedDate = it } },
                            onNext = { nextDate?.let { selectedDate = it } },
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(selected.weatherText ?: selected.condition?.label ?: "天气预报",
                                    color = ZhishengText, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            }
                            WeatherIcon(visual.dayCondition, Modifier.size(72.dp))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Fmt.temp(selected.low, tempUnit)?.let { Text("最低 $it°", color = ZhishengText, style = MaterialTheme.typography.titleLarge) }
                            Fmt.temp(selected.high, tempUnit)?.let { Text("最高 $it°", color = ZhishengText, style = MaterialTheme.typography.titleLarge) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            ForecastPhase("白天", visual.dayLabel, visual.dayCondition, Modifier.weight(1f))
                            ForecastPhase("夜间", visual.nightLabel, visual.nightCondition, Modifier.weight(1f))
                        }
                    }
                }
                if (vistaForecastFacts(selected, windUnit).isNotEmpty()) item {
                    Column(
                        Modifier.fillMaxWidth()
                            .swipeAdjacentDays(
                                onPrev = { prevDate?.let { selectedDate = it } },
                                onNext = { nextDate?.let { selectedDate = it } },
                            )
                            .zhishengPanel()
                            .padding(chrome.panelPadding),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text("当日详情", color = ZhishengText, fontWeight = FontWeight.SemiBold)
                        val details = vistaForecastFacts(selected, windUnit)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp), maxItemsInEachRow = 2) {
                            details.forEach { (label, value) ->
                                Column(Modifier.widthIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        PhosphorIcon(
                                            forecastFactIcon(label),
                                            null,
                                            Modifier.size(13.dp),
                                            ZhishengTextTertiary,
                                        )
                                        Text(label, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(value, color = ZhishengText, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                if (days.isNotEmpty()) item {
                    val digest = buildForecastDigest(days, tempUnit, utcOffsetSeconds, city?.locationKey.orEmpty(), nowMillis)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("天气娘简报", color = ZhishengTextSecondary, style = MaterialTheme.typography.labelLarge)
                        Text(digest.headline, color = ZhishengText, style = MaterialTheme.typography.bodyLarge)
                        Text(digest.overview, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
    }
}

internal fun forecastHasHourlyTemperatures(hours: List<HourlyWeather>): Boolean =
    hours.any { it.temperature?.isFinite() == true }

/** 没有可用的小时温度时整块隐藏，连标题行也不占位。 */
@Composable
internal fun ForecastHourlyTemperature(hours: List<HourlyWeather>, unit: String, offset: Int?) {
    if (!forecastHasHourlyTemperatures(hours)) return
    val palette = LocalZhishengPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("当日逐小时温度", color = palette.text, fontWeight = FontWeight.SemiBold)
        Text("仅显示数据源实际提供的小时；缺失处不连线", color = palette.textTertiary,
            style = MaterialTheme.typography.labelSmall)
        val values = hours.mapNotNull { it.temperature?.takeIf(Double::isFinite) }
        val min = values.min() - 2.0
        val max = values.max() + 2.0
        val column = 64.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        // A new date starts at its first available hour instead of inheriting another day's scroll.
        androidx.compose.runtime.key(hours.firstOrNull()?.timeMillis) {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(Modifier.width(column * hours.size).height(110.dp)) {
                    fun point(i: Int, t: Double) = Offset(column.toPx() * (i + .5f),
                        size.height - 8.dp.toPx() - ((t - min) / (max - min)).toFloat() * (size.height - 16.dp.toPx()))
                    var previous: Offset? = null
                    hours.forEachIndexed { i, hour ->
                        val value = hour.temperature?.takeIf(Double::isFinite)
                        if (value == null) previous = null else {
                            val p = point(i, value)
                            if (i > 0 && hour.timeMillis - hours[i - 1].timeMillis > 90 * 60_000L) previous = null
                            previous?.let { drawLine(palette.mint, it, p, 2.dp.toPx()) }
                            drawCircle(palette.mint, 3.dp.toPx(), p)
                            previous = p
                        }
                    }
                }
                Row {
                    hours.forEach { hour ->
                        Column(Modifier.width(column).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(Fmt.hour(hour.timeMillis, offset), color = palette.textSecondary,
                                style = MaterialTheme.typography.labelSmall)
                            Text(Fmt.temp(hour.temperature?.takeIf(Double::isFinite), unit)?.let { "$it°" } ?: "—",
                                color = palette.text, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ForecastPhase(label: String, description: String, condition: WeatherCondition, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeatherIcon(condition, Modifier.size(30.dp))
        Column {
            Text(label, color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
            Text(description, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Every date stays selectable. Sparse/missing temperatures break the curve, never bridge it. */
@Composable
private fun ForecastDateTrack(days: List<DailyWeather>, selected: Long, unit: String, offset: Int?, onSelect: (Long) -> Unit) {
    val palette = LocalZhishengPalette.current
    val column = 68.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val scroll = rememberScrollState()
    val values = days.flatMap { listOfNotNull(it.low, it.high) }.filter(Double::isFinite)
    val min = (values.minOrNull() ?: 0.0) - 2
    val max = (values.maxOrNull() ?: min + 1) + 2
    val chartHeight = 100.dp
    val chartInset = 8.dp
    val density = LocalDensity.current
    fun yDp(value: Double): Dp {
        val ratio = ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
        return chartHeight - chartInset - (chartHeight - chartInset * 2) * ratio
    }
    // 选中日期变化时把该列带回可视区，避免点选/滑动后曲线留在屏幕外。
    LaunchedEffect(selected) {
        val index = days.indexOfFirst { it.dateMillis == selected }
        if (index >= 0) {
            val columnPx = with(density) { column.toPx() }
            scroll.animateScrollTo((columnPx * index - columnPx * 1.2f).toInt().coerceAtLeast(0))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("温度变化", color = ZhishengText, fontWeight = FontWeight.SemiBold)
            Text("最高 / 最低", color = ZhishengTextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Box(Modifier.width(column * days.size).height(chartHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val inset = chartInset.toPx()
                    fun x(index: Int) = column.toPx() * (index + 0.5f)
                    fun y(value: Double) = size.height - inset - ((value - min) / (max - min)).toFloat() * (size.height - inset * 2)
                    val active = days.indexOfFirst { it.dateMillis == selected }
                    if (active >= 0) drawLine(palette.mint.copy(alpha = 0.55f),
                        Offset(x(active), 0f), Offset(x(active), size.height), 2.dp.toPx())
                    listOf(DailyWeather::high to palette.orange, DailyWeather::low to palette.cyan).forEach { (valueOf, color) ->
                        val path = Path()
                        var connected = false
                        days.forEachIndexed { index, day ->
                            val value = valueOf(day)?.takeIf(Double::isFinite)
                            if (value == null) connected = false
                            else {
                                if (connected) path.lineTo(x(index), y(value)) else path.moveTo(x(index), y(value))
                                connected = true
                                drawCircle(color, if (index == active) 4.dp.toPx() else 2.dp.toPx(), Offset(x(index), y(value)))
                            }
                        }
                        drawPath(path, color, style = Stroke(2.dp.toPx()))
                    }
                }
                // 曲线点上的具体温度：高值在点上方，低值在点下方，字号跟随 fontScale。
                Row(Modifier.fillMaxSize()) {
                    days.forEach { day ->
                        Box(Modifier.width(column).fillMaxHeight()) {
                            day.high?.takeIf(Double::isFinite)?.let { raw ->
                                Text(
                                    Fmt.temp(raw, unit)?.let { "$it°" } ?: "—",
                                    modifier = Modifier.align(Alignment.TopCenter)
                                        .offset(y = (yDp(raw) - 17.dp).coerceAtLeast(0.dp)),
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = palette.orange,
                                )
                            }
                            day.low?.takeIf(Double::isFinite)?.let { raw ->
                                Text(
                                    Fmt.temp(raw, unit)?.let { "$it°" } ?: "—",
                                    modifier = Modifier.align(Alignment.TopCenter)
                                        .offset(y = (yDp(raw) + 2.dp).coerceAtMost(chartHeight - 11.dp)),
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = palette.cyan,
                                )
                            }
                        }
                    }
                }
                // 点击温度变化曲线按列切换日期，与下方日期轨道保持一致。
                Row(Modifier.fillMaxSize()) {
                    days.forEach { day ->
                        Box(
                            Modifier.width(column).fillMaxHeight()
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "查看${forecastTemporalLabel(day.dateMillis, offset)}的详情",
                                ) { onSelect(day.dateMillis) },
                        )
                    }
                }
            }
            Row(Modifier.selectableGroup()) {
                days.forEach { day ->
                    val active = day.dateMillis == selected
                    Column(Modifier.width(column).selectable(active, role = Role.Tab, onClick = { onSelect(day.dateMillis) })
                        .padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(forecastTemporalLabel(day.dateMillis, offset), color = if (active) palette.mint else palette.textSecondary,
                            style = MaterialTheme.typography.labelLarge)
                        Text(forecastDateLabel(day.dateMillis, offset), color = palette.textTertiary,
                            style = MaterialTheme.typography.labelSmall)
                        WeatherIcon(forecastDayNightVisual(day).dayCondition, Modifier.size(28.dp))
                        Text(day.weatherText?.takeIf { it.isNotBlank() } ?: day.condition?.label ?: "暂无天气",
                            color = palette.textSecondary, style = MaterialTheme.typography.labelSmall,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${Fmt.temp(day.high, unit) ?: "—"}°", color = palette.orange,
                            style = MaterialTheme.typography.titleSmall)
                        Text("${Fmt.temp(day.low, unit) ?: "—"}°", color = palette.cyan,
                            style = MaterialTheme.typography.titleSmall)
                        Box(Modifier.width(20.dp).height(2.dp).background(if (active) palette.mint else androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
            }
        }
    }
}

internal fun vistaForecastFacts(day: DailyWeather, windUnit: String): List<Pair<String, String>> = buildList {
    val wind = listOfNotNull(com.zhisheng.weather.data.WeatherRepository.windDirection(day.windDirectionDeg),
        day.windSpeed?.takeIf { it.isFinite() && it >= 0 }?.let { Fmt.wind(it, windUnit) })
    if (wind.isNotEmpty()) add("风向风速" to wind.joinToString(" · "))
    day.windGust?.takeIf { it.isFinite() && it >= 0 }?.let { Fmt.wind(it, windUnit) }?.let { add("阵风" to it) }
    day.precipProbability?.takeIf { it in 0..100 }?.let { add("降水概率" to "$it%") }
    day.precipMm?.takeIf { it.isFinite() && it >= 0 }?.let { add("降水量" to String.format(Locale.US, "%.1f mm", it)) }
    day.aqi?.takeIf { it in 0..1000 }?.let { add("空气质量指数" to it.toString()) }
    day.humidity?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { add("湿度" to "${it.toInt()}%") }
    day.cloudCover?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { add("云量" to "${it.toInt()}%") }
    day.uvIndex?.takeIf { it >= 0 }?.let { add("紫外线指数" to it.toString()) }
    day.sunrise?.takeIf(String::isNotBlank)?.let { add("日出" to it) }
    day.sunset?.takeIf(String::isNotBlank)?.let { add("日落" to it) }
    Fmt.moonPhaseZh(day.moonPhase)?.let { add("月相" to it) }
    day.moonrise?.takeIf(String::isNotBlank)?.let { add("月出" to it) }
    day.moonset?.takeIf(String::isNotBlank)?.let { add("月落" to it) }
}

/** 横向工作台 / 纵向日签切换。 */
@Composable
internal fun ForecastViewToggle(vertical: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalZhishengPalette.current
    val chrome = LocalZhishengChrome.current
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = chrome.pagePadding, vertical = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(false to "横向", true to "纵向").forEach { (isVertical, label) ->
            val selected = vertical == isVertical
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.weight(1f).heightIn(min = 46.dp)
                    .clip(shape)
                    .background(if (selected) palette.mint.copy(alpha = 0.16f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) palette.mint.copy(alpha = 0.45f) else palette.cardBorder.copy(alpha = 0.4f),
                        shape,
                    )
                    .selectable(selected = selected, role = Role.Tab) { onChange(isVertical) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) palette.mint else palette.textSecondary,
                )
            }
        }
    }
}

/** 横向模式顶部：选中日期信息 + 前一天/后一天切换，两种主题共用。 */
@Composable
internal fun ForecastDateHeader(
    dateLabel: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.swipeAdjacentDays(onPrev, onNext),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ForecastDaySwitch(canPrev, canNext, onPrev, onNext)
        Text(
            dateLabel,
            style = MaterialTheme.typography.titleSmall,
            color = ZhishengText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 纵向日签：每天一张卡，点开的卡片展开当日要素（两列对齐）。 */
@Composable
internal fun ForecastVerticalList(
    days: List<DailyWeather>,
    selectedMillis: Long?,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    hourly: List<HourlyWeather>,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalZhishengPalette.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        days.forEach { day ->
            val open = day.dateMillis == selectedMillis
            val visual = forecastDayNightVisual(day)
            Column(
                Modifier.fillMaxWidth().zhishengPanel().padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (open) "收起当日详情" else "展开当日详情",
                        ) { onSelect(if (open) null else day.dateMillis) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.width(56.dp)) {
                        Text(
                            Fmt.dailyDayLabel(day.dateMillis, utcOffsetSeconds = utcOffsetSeconds),
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.text,
                            maxLines = 1,
                        )
                        Text(
                            Fmt.dayOfMonth(day.dateMillis, utcOffsetSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary,
                            maxLines = 1,
                        )
                    }
                    WeatherIcon(visual.dayCondition, Modifier.size(28.dp))
                    Text(
                        day.weatherText?.takeIf(String::isNotBlank) ?: day.condition?.label ?: "天气暂缺",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Fmt.temp(day.high, tempUnit)?.let { "$it°" } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.text,
                        )
                        Text(
                            Fmt.temp(day.low, tempUnit)?.let { "$it°" } ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                        )
                    }
                }
                if (open) {
                    val hours = remember(hourly, day.dateMillis, utcOffsetSeconds) {
                        forecastHoursForDate(hourly, day.dateMillis, utcOffsetSeconds)
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.45f), thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))
                    // 当日主信息：天气、温度区间与昼夜两段
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                day.weatherText?.takeIf(String::isNotBlank) ?: day.condition?.label ?: "天气预报",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.text,
                            )
                            Text(
                                "${Fmt.temp(day.low, tempUnit)?.let { "$it°" } ?: "—"} ~ ${Fmt.temp(day.high, tempUnit)?.let { "$it°" } ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                        WeatherIcon(visual.dayCondition, Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ForecastPhase("白天", visual.dayLabel, visual.dayCondition, Modifier.weight(1f))
                        ForecastPhase("夜间", visual.nightLabel, visual.nightCondition, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    if (forecastHasHourlyTemperatures(hours)) {
                        ForecastHourlyTemperature(hours, tempUnit, utcOffsetSeconds)
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.45f), thickness = 1.dp)
                        Spacer(Modifier.height(10.dp))
                    }
                    Text("当日信息", style = MaterialTheme.typography.labelLarge, color = palette.text)
                    Spacer(Modifier.height(4.dp))
                    val facts = vistaForecastFacts(day, windUnit)
                    if (facts.isEmpty()) {
                        Text("暂无当日详情", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
                    } else {
                        facts.chunked(2).forEach { rowFacts ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                rowFacts.forEach { (label, value) ->
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            PhosphorIcon(
                                                forecastFactIcon(label),
                                                null,
                                                Modifier.size(12.dp),
                                                palette.textTertiary,
                                            )
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.textSecondary,
                                                maxLines = 1,
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(value, style = MaterialTheme.typography.titleSmall, color = palette.text)
                                    }
                                }
                                if (rowFacts.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
