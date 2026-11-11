package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.data.WeatherRepository
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.home.tempDelta
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengRed
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme
import kotlin.math.abs

/**
 * 昨日天气二级页：昨日高低温、天气转折、风、日出日落与 AQI，并与今日最高温对比。
 * 页面骨架与设置 / 特色功能等二级页统一使用库组件；大温度、AQI 语义色与图标网格等
 * 自定义内容保留在库分组卡片内承载。
 */
@Composable
fun YesterdayDetailScreen(
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
            item {
                MochiListSection(header = "昨日天气") {
                    MochiListRow(title = "暂无昨日天气数据")
                }
            }
            return@MochiPageFrame
        }
        item {
            MochiListSection(header = "高低温与空气质量") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                Fmt.temp(yesterday.high, tempUnit)?.let { "$it°" } ?: "—",
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZhishengOrange,
                            )
                            Text(" / ", fontSize = 24.sp, color = MochiTheme.colorScheme.onSurfaceVariant)
                            Text(
                                Fmt.temp(yesterday.low, tempUnit)?.let { "$it°" } ?: "—",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MochiTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            condition?.label ?: "天气未知",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    WeatherIcon(condition, Modifier.size(56.dp))
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCell("最高温", Fmt.temp(yesterday.high, tempUnit)?.let { "$it°" } ?: "—", ZhishengOrange, Modifier.weight(1f))
                    MetricCell("最低温", Fmt.temp(yesterday.low, tempUnit)?.let { "$it°" } ?: "—", MochiTheme.colorScheme.onSurface, Modifier.weight(1f))
                    MetricCell("AQI", yesterday.aqi?.toString() ?: "—", aqiTone(yesterday.aqi), Modifier.weight(1f))
                }
            }
        }
        tempDelta(today?.high, yesterday.high, tempUnit)?.let { diff ->
            item {
                MochiListSection(header = "较今日最高温") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("较今日最高温", style = MaterialTheme.typography.titleMedium, color = MochiTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                when {
                                    diff > 0 -> "今日最高温比昨天高 ${abs(diff)}°"
                                    diff < 0 -> "今日最高温比昨天低 ${abs(diff)}°"
                                    else -> "今日最高温与昨天持平"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            when {
                                diff > 0 -> "+${diff}°"
                                diff < 0 -> "$diff°"
                                else -> "0°"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (diff > 0) ZhishengOrange else ZhishengMint,
                        )
                    }
                }
            }
        }
        item {
            MochiListSection(header = "天气与风") {
                val facts = listOf(
                    YesterdayFact(R.drawable.ph_cloud, "天气", weatherTransition(yesterday)),
                    YesterdayFact(R.drawable.ph_compass, "风向", windTransition(yesterday)),
                    YesterdayFact(R.drawable.ph_wind, "风速", windSpeedLabel(yesterday, windUnit)),
                    YesterdayFact(R.drawable.ph_sun, "日出", yesterday.sunrise?.takeIf(String::isNotBlank) ?: "—"),
                    YesterdayFact(R.drawable.ph_moon_stars, "日落", yesterday.sunset?.takeIf(String::isNotBlank) ?: "—"),
                )
                facts.chunked(2).forEach { rowFacts ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowFacts.forEach { fact -> FactCell(fact, Modifier.weight(1f)) }
                        if (rowFacts.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
    }
}

private data class YesterdayFact(val icon: Int, val label: String, val value: String)

/** 两列网格单元：图标 + 标签 + 值。 */
@Composable
private fun FactCell(fact: YesterdayFact, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MochiTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            PhosphorIcon(fact.icon, null, Modifier.size(15.dp), MochiTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(fact.label, style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                fact.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MochiTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun aqiTone(value: Int?): Color = when {
    value == null -> MochiTheme.colorScheme.onSurfaceVariant
    value <= 100 -> ZhishengMint
    value <= 200 -> ZhishengOrange
    else -> ZhishengRed
}

private fun weatherTransition(yesterday: YesterdayInfo): String {
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

private fun windTransition(yesterday: YesterdayInfo): String {
    val start = WeatherRepository.windDirection(yesterday.windDirectionStartDeg)
    val end = WeatherRepository.windDirection(yesterday.windDirectionEndDeg)
    return when {
        start != null && end != null && start != end -> "$start 转 $end"
        start != null -> start
        end != null -> end
        else -> "—"
    }
}

private fun windSpeedLabel(yesterday: YesterdayInfo, windUnit: String): String {
    val speed = listOfNotNull(yesterday.windSpeedStart, yesterday.windSpeedEnd).maxOrNull()
    return speed?.let { Fmt.wind(it, windUnit) } ?: "—"
}
