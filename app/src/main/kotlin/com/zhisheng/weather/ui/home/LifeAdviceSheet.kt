package com.zhisheng.weather.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.rememberMochiColorScheme
import com.zhisheng.weather.ui.weatherPresentationTime
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.theme.MochiTheme
import java.time.Instant

/** Conservative forecast-derived advice; no invented supplier indices or medical predictions. */
internal fun forecastLifeAdvice(english: String, day: DailyWeather?): String {
    if (day == null) return "暂无预报"
    val rain = day.condition?.isPrecipitation == true || (day.precipProbability ?: 0) >= 60 || (day.precipMm ?: 0.0) >= 1.0
    return when (english.uppercase()) {
        "DRESS" -> day.high?.let { when { it >= 28 -> "轻薄透气，备好雨具".takeIf { rain } ?: "轻薄透气，避免闷热"
            it >= 22 -> "轻薄衣物，早晚备外套"; it >= 15 -> "长袖配薄外套"; it >= 5 -> "厚外套，注意保暖"; else -> "保暖外套，减少受凉" } } ?: "缺少温度预报"
        "UV", "SPF", "GLASSES" -> day.uvIndex?.let { when { it >= 6 -> "加强遮阳，避开午间曝晒"; it >= 3 -> "户外注意遮阳"; else -> "紫外线较弱" } } ?: "缺少紫外线预报"
        "CAR WASH" -> when { rain -> "预计有降水，建议缓洗"; day.precipProbability != null || day.precipMm != null -> "降水影响较小，可安排洗车"; else -> "缺少降水预报" }
        "SPORTS", "TRAVEL", "FISHING" -> when { rain -> "留意降水，优先安排室内活动"; (day.windSpeed ?: 0.0) >= 30 -> "风力较强，减少空旷处活动"; (day.high ?: 0.0) >= 32 -> "避开高温时段"; day.condition != null -> "结合临近天气安排户外活动"; else -> "缺少天气预报" }
        "DRYING" -> if (rain) "有降水可能，建议室内晾晒" else if (day.precipProbability != null) "关注临近降水，及时收衣" else "缺少降水预报"
        "TRAFFIC" -> if (rain) "雨天路滑，出行预留时间" else "出行前留意当地路况"
        "MORNING EXERCISE", "SHOPPING", "BOATING", "KITE", "NIGHTLIFE", "DATE", "MOOD" ->
            if (rain) "预计有降水，建议改在室内安排" else "天气条件合适，可以安排"
        "UMBRELLA" -> if (rain) "出门记得带伞" else "暂不需要带伞"
        "WIND CHILL" -> (day.low ?: day.high)?.let {
            if (it <= 5) "气温偏低，注意防风保暖" else "风力与体感尚可"
        } ?: "缺少温度预报"
        "HEATSTROKE" -> day.high?.let {
            if (it >= 32) "高温时段注意补水、减少暴晒" else "暂无明显中暑风险"
        } ?: "缺少温度预报"
        "DRYNESS", "HAIR CARE", "BEER" -> "结合当天温湿度自行安排"
        else -> "数据源暂未提供该日建议"
    }
}

/** 生活指数详情：库底部面板 + 库分组卡片，自定义天气图标放在卡片内部。 */
@Composable
internal fun LifeAdviceSheet(name: String, english: String, value: String, data: WeatherData, unit: String = "c", period: String? = null, onDismiss: () -> Unit) {
    val fallbackSheet = rememberMochiSheetState(listOf(MochiSheetDetent.Medium, MochiSheetDetent.Large))
    val sheet = LocalHomeSheetStates.current?.advice ?: fallbackSheet
    LaunchedEffect(Unit) { sheet.show() }
    val zone = Fmt.zoneId(data.utcOffsetSeconds)
    val today = Instant.ofEpochMilli(weatherPresentationTime()).atZone(zone).toLocalDate()
    MochiTheme(colors = rememberMochiColorScheme(), systemBarAppearance = false) {
        val cs = MochiTheme.colorScheme
        MochiSheet(state = sheet, onDismiss = onDismiss) {
            MochiSheetHeader(
                title = "${period ?: "今天"} · $name",
                actions = { MochiBarButton("关闭", onClick = { sheet.hide() }) },
            )
            MochiSheetContent {
                MochiListCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PhosphorIcon(lifeIndexIcon(english), null, Modifier.size(24.dp), cs.primary)
                            Text("当前建议", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                        }
                        Text(value, style = MaterialTheme.typography.headlineLarge, color = cs.onSurface, fontWeight = FontWeight.Medium)
                    }
                }
                MochiListCard {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        (1..3).forEach { offset ->
                            val date = today.plusDays(offset.toLong())
                            val day = data.daily.firstOrNull { Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() == date }
                            val advice = forecastLifeAdvice(english, day)
                            val unavailable = day == null || advice.startsWith("缺少") || advice.startsWith("数据源暂未")
                            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.width(88.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(when(offset) { 1 -> "明天"; 2 -> "后天"; else -> "${date.monthValue}月${date.dayOfMonth}日" },
                                        style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                                    val high = Fmt.temp(day?.high, unit)?.let { "$it°" } ?: "—"
                                    val low = Fmt.temp(day?.low, unit)?.let { "$it°" } ?: "—"
                                    Text(if (day == null) "—" else "$high / $low", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                    if (day?.condition != null && day.condition != WeatherCondition.UNKNOWN) {
                                        WeatherIcon(day.condition, Modifier.fillMaxSize())
                                    } else {
                                        Text("—", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                    }
                                }
                                Text(advice, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                    color = if (unavailable) cs.onSurfaceVariant else cs.onSurface)
                            }
                            if (offset < 3) HorizontalDivider(color = cs.outlineVariant.copy(alpha = .5f))
                        }
                    }
                }
                Text("未来三天建议根据逐日天气预报生成。", Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}
