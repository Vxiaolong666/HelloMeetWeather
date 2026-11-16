package com.HelloMeet.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.WeatherRepository
import com.HelloMeet.weather.model.DailyWeather
import com.HelloMeet.weather.model.WeatherCondition
import com.HelloMeet.weather.model.YesterdayInfo
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.components.WeatherIcon
import com.HelloMeet.weather.ui.home.tempDelta
import com.HelloMeet.weather.ui.theme.ZhishengMint
import com.HelloMeet.weather.ui.theme.ZhishengOrange
import com.HelloMeet.weather.ui.theme.ZhishengRed
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme
import kotlin.math.abs

/**
 * 昨日天气二级页：昨日高低温、天气转折、风、日出日落与 AQI，并与今日最高温对比。
 * 页面骨架与设置 / 特色功能等二级页统一使用库组件；大温度、AQI 语义色这类自定义内容
 * 用库的内容卡片 [MochiPageCard] 承载（自带卡内边距），逐条信息直接排成库列表行，
 * 行高、分隔线与字阶都由组件库给。
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
            MochiPageCard(header = "高低温与空气质量") {
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
                            style = MochiTheme.textStyles.body,
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
                MochiPageCard(header = "较今日最高温") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                diff > 0 -> "今日最高温比昨天高 ${abs(diff)}°"
                                diff < 0 -> "今日最高温比昨天低 ${abs(diff)}°"
                                else -> "今日最高温与昨天持平"
                            },
                            Modifier.weight(1f),
                            style = MochiTheme.textStyles.subhead,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            when {
                                diff > 0 -> "+${diff}°"
                                diff < 0 -> "$diff°"
                                else -> "0°"
                            },
                            style = MochiTheme.textStyles.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (diff > 0) ZhishengOrange else ZhishengMint,
                        )
                    }
                }
            }
        }
        item {
            val facts = listOf(
                YesterdayFact(R.drawable.ph_cloud, "天气", weatherTransition(yesterday)),
                YesterdayFact(R.drawable.ph_compass, "风向", windTransition(yesterday)),
                YesterdayFact(R.drawable.ph_wind, "风速", windSpeedLabel(yesterday, windUnit)),
                YesterdayFact(R.drawable.ph_sun, "日出", yesterday.sunrise?.takeIf(String::isNotBlank) ?: "—"),
                YesterdayFact(R.drawable.ph_moon_stars, "日落", yesterday.sunset?.takeIf(String::isNotBlank) ?: "—"),
            )
            MochiListSection(header = "天气与风") {
                facts.forEach { fact ->
                    MochiListRow(
                        title = fact.label,
                        value = fact.value,
                        leading = {
                            PhosphorIcon(
                                fact.icon,
                                null,
                                Modifier.size(18.dp),
                                MochiTheme.colorScheme.primary,
                            )
                        },
                        leadingStyle = MochiListLeadingStyle.Symbol,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MochiTheme.textStyles.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MochiTheme.textStyles.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
    }
}

private data class YesterdayFact(val icon: Int, val label: String, val value: String)

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
