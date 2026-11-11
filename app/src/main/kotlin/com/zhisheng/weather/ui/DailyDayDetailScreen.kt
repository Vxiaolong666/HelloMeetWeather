@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherIcon
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

/**
 * 单日详细天气页：由首页五天卡片的详情块进入，[dateMillis] 决定打开哪一天。
 * 页面外壳与设置 / 特色功能等二级页统一使用库组件；自定义天气图形（概览、逐时曲线、详情网格）
 * 不适合用列表行表达，保留在库分组卡片内承载。
 */
@Composable
internal fun DailyDayDetailScreen(
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

    // 入口日期即首屏选中日期；数据刷新导致该日缺失时退化到最接近的一天。
    var selectedDate by rememberSaveable(city?.locationKey, dateMillis) { mutableStateOf(dateMillis) }
    val day = track.firstOrNull { it.dateMillis == selectedDate }
        ?: track.minByOrNull { kotlin.math.abs(it.dateMillis - selectedDate) }
    val index = day?.let { target -> track.indexOfFirst { it.dateMillis == target.dateMillis } } ?: -1
    val prevDate = track.getOrNull(index - 1)?.dateMillis
    val nextDate = track.getOrNull(index + 1)?.dateMillis

    // 以下计算需在 Composable 上下文完成：MochiPageFrame 的 content 是 LazyListScope，不能直接用 remember。
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
            MochiListSection(header = "切换日期") {
                Column(
                    Modifier.fillMaxWidth().swipeAdjacentDays(
                        onPrev = { prevDate?.let { selectedDate = it } },
                        onNext = { nextDate?.let { selectedDate = it } },
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ForecastDaySwitch(
                        canPrev = prevDate != null,
                        canNext = nextDate != null,
                        onPrev = { prevDate?.let { selectedDate = it } },
                        onNext = { nextDate?.let { selectedDate = it } },
                    )
                    city?.displayName?.let {
                        Text(it, color = MochiTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            MochiListSection(header = "天气概览") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${forecastTemporalLabel(day.dateMillis, utcOffsetSeconds)} · ${forecastDateLabel(day.dateMillis, utcOffsetSeconds)}",
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                day.weatherText ?: day.condition?.label ?: "天气预报",
                                color = MochiTheme.colorScheme.onSurface,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        WeatherIcon(visual.dayCondition, Modifier.size(64.dp))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Fmt.temp(day.low, tempUnit)?.let {
                            Text("最低 $it°", color = MochiTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                        }
                        Fmt.temp(day.high, tempUnit)?.let {
                            Text("最高 $it°", color = MochiTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        ForecastPhase("白天", visual.dayLabel, visual.dayCondition, Modifier.weight(1f))
                        ForecastPhase("夜间", visual.nightLabel, visual.nightCondition, Modifier.weight(1f))
                    }
                }
            }
        }
        if (forecastHasHourlyTemperatures(hours)) {
            item {
                MochiListSection(header = "逐时气温") {
                    ForecastHourlyTemperature(hours, tempUnit, utcOffsetSeconds)
                }
            }
        }
        if (facts.isNotEmpty()) {
            item {
                MochiListSection(header = "当日详情") {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        facts.forEach { (label, value) ->
                            Column(
                                Modifier.widthIn(min = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    PhosphorIcon(
                                        forecastFactIcon(label),
                                        null,
                                        Modifier.size(13.dp),
                                        MochiTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(label, color = MochiTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(value, color = MochiTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "左右滑动卡片或点「前一天 / 后一天」可切换日期",
                color = MochiTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
