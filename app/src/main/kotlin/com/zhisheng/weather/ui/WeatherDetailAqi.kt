package com.zhisheng.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.MeeAirQualityRankRepository
import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import ui.feice.mochi.effect.mask.mochiScrollEdgeBlur
import ui.feice.mochi.navigation.MochiScrollEdgeDefaults
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════
// 国标六级色阶（与独立空气质量页同一套阈值，颜色各自维护）
// ═══════════════════════════════════════════════════════════

private data class AqiTone(
    val label: String,
    val short: String,
    val range: String,
    val lightColor: Color,
    val darkColor: Color,
    val advice: String,
) {
    fun color(light: Boolean): Color = if (light) lightColor else darkColor
}

private val AqiTones = listOf(
    AqiTone("优", "优", "0–50", Color(0xFF21A356), Color(0xFF35D95A),
        "空气质量令人满意，适合各类户外活动"),
    AqiTone("良", "良", "51–100", Color(0xFFEFB800), Color(0xFFFFE14D),
        "空气质量可接受，极少数异常敏感人群应减少户外活动"),
    AqiTone("轻度污染", "轻度", "101–150", Color(0xFFFF7E00), Color(0xFFFF9E2C),
        "儿童、老年人及心脏病、呼吸系统疾病患者应减少长时间户外锻炼"),
    AqiTone("中度污染", "中度", "151–200", Color(0xFFE8382B), Color(0xFFFF5A4F),
        "敏感人群避免户外活动，一般人群适当减少户外运动"),
    AqiTone("重度污染", "重度", "201–300", Color(0xFF8F3F97), Color(0xFFC77BE0),
        "尽量留在室内，外出佩戴口罩并减少开窗"),
    AqiTone("严重污染", "严重", ">300", Color(0xFF7E0023), Color(0xFFE07A6E),
        "避免户外活动，做好防护并关注官方预警"),
)

private val AqiUnknownTone = Color(0xFF8A949E)

private fun aqiToneIndex(value: Int?): Int? = when {
    value == null -> null
    value <= 50 -> 0
    value <= 100 -> 1
    value <= 150 -> 2
    value <= 200 -> 3
    value <= 300 -> 4
    else -> 5
}

private fun aqiToneColor(value: Int?, light: Boolean): Color =
    aqiToneIndex(value)?.let { AqiTones[it].color(light) } ?: AqiUnknownTone

/**
 * 与 yunsong AirQualityScale.color 一致的连续色阶：0–500 七档线性插值，
 * 仪表弧、柱状图与选中焦点都用这一份，避免离散六档在连续值上出现跳变。
 */
internal fun smoothAqiColor(aqi: Double?): Color {
    val value = (aqi ?: 0.0).coerceIn(0.0, 500.0)
    val stops = doubleArrayOf(0.0, 50.0, 100.0, 150.0, 200.0, 300.0, 500.0)
    val colors = listOf(
        Color(0xFF3ED362), Color(0xFF9CD639), Color(0xFFFFCD2E),
        Color(0xFFFF972F), Color(0xFFE25357), Color(0xFF9C4EAE),
        Color(0xFF7C2B4C),
    )
    for (index in 1 until stops.size) {
        if (value <= stops[index]) {
            val fraction = ((value - stops[index - 1]) / (stops[index] - stops[index - 1])).toFloat()
            return lerp(colors[index - 1], colors[index], fraction)
        }
    }
    return colors.last()
}

/** 与国标阈值一致的等级名称，图表刻度和读数共用。 */
internal fun aqiDetailLevelLabel(value: Int?): String =
    aqiToneIndex(value)?.let { AqiTones[it].label } ?: "暂无"

private fun aqiDetailAdvice(value: Int?): String {
    val v = value ?: return "暂无可用的空气质量数据。"
    return when {
        v <= 50 -> "空气很好，可正常活动，快去呼吸新鲜空气吧。"
        v <= 100 -> "空气不错，适合户外活动，建议适当开窗通风。"
        v <= 150 -> "空气质量一般，敏感人群请减少户外活动。"
        v <= 200 -> "空气较差，建议减少户外活动，外出佩戴口罩。"
        v <= 300 -> "空气很差，尽量留在室内，关闭门窗。"
        else -> "空气极差，避免外出，做好健康防护。"
    }
}

/** 与 yunsong AirQualityScale.pollutantProgress 相同的相对进度。 */
private fun pollutantProgress(name: String, value: Double?): Float {
    val max = when (name) {
        "PM2.5" -> 150.0
        "PM10" -> 250.0
        "NO₂" -> 280.0
        "O₃" -> 400.0
        "CO" -> 60.0
        "SO₂" -> 800.0
        else -> 100.0
    }
    return ((value ?: 0.0) / max).toFloat().coerceIn(0f, 1f)
}

/** 与 yunsong AirQualityScale.formatPollutant 相同：CO 保留一位，其余取整。 */
private fun formatPollutant(name: String, value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    return if (name == "CO") String.format(java.util.Locale.US, "%.1f", value)
    else value.roundToInt().toString()
}

// ═══════════════════════════════════════════════════════════
// 序列
// ═══════════════════════════════════════════════════════════

/** 一根柱子：时刻、AQI、横轴短标签。 */
internal data class AqiDetailPoint(
    val timeMillis: Long,
    val value: Int,
    val axisLabel: String,
)

/** 未来 24 小时逐时 AQI（与逐时指标页同一时间窗）。 */
internal fun aqiDetailHourly(
    weather: WeatherData?,
    now: Long = System.currentTimeMillis(),
): List<AqiDetailPoint> = detailHourly(weather, now).mapNotNull { hour ->
    hour.aqi?.let { value ->
        AqiDetailPoint(
            timeMillis = hour.timeMillis,
            value = value,
            axisLabel = Fmt.clock(hour.timeMillis, weather?.utcOffsetSeconds),
        )
    }
}

/** 逐日 AQI：今天起 7 天，缺失日直接跳过。 */
internal fun aqiDetailDaily(
    weather: WeatherData?,
    now: Long = System.currentTimeMillis(),
): List<AqiDetailPoint> {
    val zone = weather?.utcOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return weather?.currentAndFutureDaily(now).orEmpty().take(7).mapNotNull { day ->
        day.aqi?.let { value ->
            AqiDetailPoint(
                timeMillis = day.dateMillis,
                value = value,
                axisLabel = dayLabel(Instant.ofEpochMilli(day.dateMillis).atZone(zone).toLocalDate(), today),
            )
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今天"
    today.plusDays(1) -> "明天"
    else -> "${date.monthValue}/${date.dayOfMonth}"
}

// ═══════════════════════════════════════════════════════════
// 页面
// ═══════════════════════════════════════════════════════════

/**
 * 天气详情页的「空气质量」一页：仪表 + 信息摘要 + 污染物 + 逐时/逐日预报 + 全国排名。
 *
 * 与首页进入的独立空气质量页是两份实现（那份还要承担图例弹窗、附近城市地图和扩展指标），
 * 这里只保留参考实现 yunsong 详情页的核心内容，避免两页互相拖累。
 */
@Composable
internal fun AqiDetailPage(
    weather: WeatherData?,
    city: City?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    val aqi = weather?.aqi
    val utcOffset = weather?.utcOffsetSeconds
    val listState = rememberLazyListState()
    val edge = MochiScrollEdgeDefaults.progress(listState)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .mochiScrollEdgeBlur(top = topInset, alpha = edge),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MochiPageCard(animateHeight = false) {
                AqiGauge(aqi?.value)
                Spacer(Modifier.height(14.dp))
                AqiToneRuler(aqi?.value)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        aqi?.primary?.takeIf { it.isNotBlank() }?.let { "首要污染物 $it" } ?: "首要污染物暂未标注",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (aqi?.estimated == true) Fmt.AQI_ESTIMATION_LABEL else Fmt.aqiBasisLabel(aqi?.standard),
                        style = MaterialTheme.typography.labelMedium,
                        color = ZhishengTextTertiary,
                    )
                }
            }
        }
        item {
            MochiPageCard(header = "信息摘要", animateHeight = false) {
                Text(
                    buildAqiSummary(aqi?.value, aqiDetailHourly(weather).map { it.value }, weather?.yesterday?.aqi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
                if (!aqi?.suggest.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        aqi?.suggest.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhishengTextTertiary,
                    )
                }
            }
        }
        item {
            MochiPageCard(header = "污染物浓度", animateHeight = false) {
                AqiPollutantGrid(aqi)
                Spacer(Modifier.height(12.dp))
                Text(
                    "浓度为天气源提供的瞬时读数；进度条按各污染物常用上限归一，颜色按连续 AQI 色阶，防护建议以官方发布为准。",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                )
            }
        }
        item {
            MochiPageCard(animateHeight = false) {
                AqiForecast(weather = weather, utcOffsetSeconds = utcOffset)
            }
        }
        item {
            MochiPageCard(header = "全国排名", animateHeight = false) {
                AqiRankBody(city)
            }
        }
        item {
            MochiFadeNote(
                "排名读取生态环境部城市 AQI 实时报：同一发布时间内按 AQI 由低到高计算，区县会按上级城市统计。",
            )
        }
    }
}

// ── 仪表 ──────────────────────────────────────────────────

@Composable
private fun AqiGauge(value: Int?) {
    val light = LocalZhishengPalette.current.isLight
    val track = if (light) Color(0xFFE4E4E4) else Color.White.copy(alpha = .27f)
    val numberColor = if (light) Color(0xFF141818) else Color.White
    // 与 yunsong 一致：等级字用连续色阶，再对卡片底色做对比度收敛，保证任一主题下可读。
    val levelColor = readableAqiLevelColor(value, !light, MochiDefaults.groupedContainer())
    val measurer = rememberTextMeasurer()

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // 与 yunsong 布局一致：仪表固定 216×144 并水平居中；数值与等级直接画在画布上，
        // 基线锚定弧心（cy − radius×0.03 + 7dp / cy + radius×0.23 + 6dp），不再用居中 Column 叠字。
        Canvas(Modifier.width(216.dp).height(144.dp)) {
            val w = size.width
            val h = size.height
            val diameter = min(w * .88f, h * 1.10f)
            val stroke = diameter * .078f
            val radius = diameter * .42f
            if (radius <= stroke) return@Canvas
            val center = Offset(w / 2f, h * .57f)
            val arcSize = Size(radius * 2f, radius * 2f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // 0–500 切成 108 段，逐段取连续色阶的对应颜色：弧线平滑渐变。
            val progress = ((value ?: 0) / 500f).coerceIn(0f, 1f)
            val segments = (progress * 108f).toInt()
            if (segments > 0) {
                val unit = 270f / 108f
                repeat(segments) { index ->
                    val level = (index + 1) * 500.0 / 108.0
                    drawArc(
                        color = smoothAqiColor(level),
                        startAngle = 135f + index * unit,
                        sweepAngle = unit + 0.8f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                }
            }
            // 数值 = 直径×0.225，等级 = 直径×0.080（yunsong 的 textSize 口径）。
            val numberLayout = measurer.measure(
                value?.toString() ?: "--",
                TextStyle(fontSize = (diameter * .225f / density).sp, fontWeight = FontWeight.Bold, color = numberColor),
            )
            val numberBaseline = center.y - radius * .03f + 7.dp.toPx()
            drawText(
                numberLayout,
                topLeft = Offset(
                    center.x - numberLayout.size.width / 2f,
                    numberBaseline - numberLayout.getLineBaseline(0),
                ),
            )
            val levelLayout = measurer.measure(
                aqiDetailLevelLabel(value),
                TextStyle(fontSize = (diameter * .08f / density).sp, color = levelColor),
            )
            val levelBaseline = center.y + radius * .23f + 6.dp.toPx()
            drawText(
                levelLayout,
                topLeft = Offset(
                    center.x - levelLayout.size.width / 2f,
                    levelBaseline - levelLayout.getLineBaseline(0),
                ),
            )
        }
    }
}

@Composable
private fun AqiToneRuler(value: Int?) {
    val light = LocalZhishengPalette.current.isLight
    val index = aqiToneIndex(value)
    val active = aqiToneColor(value, light)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val gap = 3.dp.toPx()
            val segment = (size.width - gap * (AqiTones.size - 1)) / AqiTones.size
            val radius = CornerRadius(size.height / 2f)
            AqiTones.forEachIndexed { position, level ->
                val left = position * (segment + gap)
                drawRoundRect(
                    color = level.color(light).copy(alpha = if (index == null || index == position) .95f else .30f),
                    topLeft = Offset(left, 0f),
                    size = Size(segment, size.height),
                    cornerRadius = radius,
                )
                if (index == position) {
                    val cx = left + segment / 2f
                    drawCircle(Color.White, size.height * .34f, Offset(cx, size.height / 2f))
                    drawCircle(level.color(light), size.height * .18f, Offset(cx, size.height / 2f))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            AqiTones.forEachIndexed { position, level ->
                Text(
                    level.short,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (position == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (position == index) active else ZhishengTextTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── 污染物 ────────────────────────────────────────────────

@Composable
private fun AqiPollutantGrid(aqi: AqiInfo?) {
    val light = LocalZhishengPalette.current.isLight
    val configuration = LocalConfiguration.current
    // 与 yunsong 同判据：大字号或窄屏折成两列，否则三列。
    val columns = if (configuration.fontScale > 1.3f || configuration.screenWidthDp < 340) 2 else 3
    val items = listOf(
        Pair("PM2.5", aqi?.pm25?.toDoubleOrNull()),
        Pair("NO₂", aqi?.no2?.toDoubleOrNull()),
        Pair("O₃", aqi?.o3?.toDoubleOrNull()),
        Pair("PM10", aqi?.pm10?.toDoubleOrNull()),
        Pair("CO", aqi?.co?.toDoubleOrNull()),
        Pair("SO₂", aqi?.so2?.toDoubleOrNull()),
    )
    val track = if (light) Color(0xFFE5E5E5) else Color.White.copy(alpha = .25f)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    val progress = pollutantProgress(label, value)
                    val rawFill = progress?.let { smoothAqiColor((it * 300.0).coerceAtLeast(16.0)) }
                    val fill = rawFill?.let { displayFillColor(it, light) }
                    Column(
                        // 与 yunsong 的 tile 一致：左右各 7dp，相邻污染物的进度条自然留出 14dp 间隔。
                        Modifier.weight(1f).padding(horizontal = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhishengTextSecondary,
                            maxLines = 1,
                        )
                        Text(
                            formatPollutant(label, value),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ZhishengText,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        PollutantBar(
                            progress = progress,
                            trackColor = track,
                            fillColor = fill,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 与 yunsong AirPollutantGridView 的 PollutantBar 一致：轨道圆角铺满，填充圆头。 */
@Composable
private fun PollutantBar(
    progress: Float?,
    trackColor: Color,
    fillColor: Color?,
    modifier: Modifier = Modifier,
) {
    val radius = 4.dp
    Canvas(modifier) {
        val trackHeight = size.height
        val corner = trackHeight / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(corner, corner),
        )
        val fraction = progress ?: return@Canvas
        val fillColor = fillColor ?: return@Canvas
        val fillWidth = (size.width * fraction).coerceIn(
            min(size.width, maxOf(corner, size.width * .065f)),
            size.width,
        )
        val fillRadius = min(corner, fillWidth / 2f)
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(0f, 0f),
            size = Size(fillWidth, trackHeight),
            cornerRadius = CornerRadius(fillRadius, fillRadius),
        )
    }
}

/** 与 yunsong 一致：统一提升饱和度与明度，避免任一主题下低浓度融入灰色轨道。 */
private fun displayFillColor(color: Color, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(if (dark) .78f else .72f)
    hsv[2] = hsv[2].coerceAtLeast(if (dark) .96f else .82f)
    return Color(android.graphics.Color.HSVToColor(255, hsv))
}

/** 与 yunsong AirQualityGaugeView 的可读色一致：连续色阶对卡片底色收敛到对比度 ≥ 4.5。 */
private fun readableAqiLevelColor(value: Int?, dark: Boolean, surface: Color): Color {
    val foreground = if (dark) Color.White else Color(0xFF141818)
    var readable = smoothAqiColor(value?.toDouble())
    repeat(8) {
        if (contrastRatio(readable, surface) < 4.5f) {
            readable = lerp(readable, foreground, .18f)
        }
    }
    return if (contrastRatio(readable, surface) >= 4.5f) readable else foreground
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

// ── 预报图表 ──────────────────────────────────────────────

@Composable
private fun AqiForecast(weather: WeatherData?, utcOffsetSeconds: Int?) {
    val hourly = remember(weather) { aqiDetailHourly(weather) }
    val daily = remember(weather) { aqiDetailDaily(weather) }
    var mode by remember { mutableIntStateOf(0) }
    var hourlySelected by remember(hourly) { mutableIntStateOf(0) }
    var dailySelected by remember(daily) { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("空气质量预报", style = MaterialTheme.typography.titleMedium, color = ZhishengText)
            Text(
                "点按或横向拖动柱子查看具体数值",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
            )
        }
        AqiModeToggle(mode) { target ->
            if (target != mode) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                mode = target
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 14 }) togetherWith
                (fadeOut(tween(120)) + slideOutHorizontally(tween(220)) { -it / 14 })
        },
        label = "aqi-forecast-mode",
    ) { target ->
        val points = if (target == 0) hourly else daily
        if (points.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (target == 0) "暂无逐时 AQI 数据" else "暂无逐日 AQI 数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengTextSecondary,
                )
            }
        } else {
            val rawSelected = if (target == 0) hourlySelected else dailySelected
            val index = rawSelected.coerceIn(0, points.lastIndex)
            val point = points[index]
            val light = LocalZhishengPalette.current.isLight
            val tone = aqiToneColor(point.value, light)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AQI ${point.value}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tone,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        aqiDetailLevelLabel(point.value),
                        style = MaterialTheme.typography.titleSmall,
                        color = tone,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (target == 0) {
                            detailTimeLabel(point.timeMillis, weatherPresentationTime(), utcOffsetSeconds)
                        } else {
                            point.axisLabel
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = ZhishengTextSecondary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    aqiDetailAdvice(point.value),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengTextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                AqiBarChart(
                    points = points,
                    selected = index,
                    onSelect = { next ->
                        if (next != index) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (target == 0) hourlySelected = next else dailySelected = next
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AqiModeToggle(mode: Int, onMode: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(MochiTheme.colorScheme.surfaceContainerHighest.copy(alpha = .7f))
            .padding(3.dp),
    ) {
        listOf("逐时", "逐日").forEachIndexed { index, label ->
            val selected = mode == index
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (selected) MochiTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onMode(index) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MochiTheme.colorScheme.onPrimary else ZhishengTextSecondary,
                )
            }
        }
    }
}

/**
 * AQI 柱状图：语义移植 yunsong 的 AqiHourlyBarChartView ——
 * 0–50 安全区铺底 + 50/100/150 横向参考线（50 为绿色强调）、柱宽 slot×0.70 圆角 3.5dp、
 * 柱色按连续 AQI 色阶（不渐变、不透明），选中柱加虚线导向线与三层数据焦点。
 * 逐时 24 根只标时间，逐日 7 根全部标出；横向拖动时图表自己吃掉手势，不与翻页抢。
 */
@Composable
private fun AqiBarChart(
    points: List<AqiDetailPoint>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val light = LocalZhishengPalette.current.isLight
    val measurer = rememberTextMeasurer()
    val gridColor = if (light) Color(0xFF141E28).copy(alpha = .16f) else Color.White.copy(alpha = .20f)
    val safeLine = Color(0xFF3ED362)
    val safeFill = Color(0xFF3ED362).copy(alpha = if (light) .19f else .25f)
    val labelColor = if (light) Color(0xFF7C8288) else Color.White.copy(alpha = .72f)
    val guideColor = if (light) Color(0xFF2D3740).copy(alpha = 110f / 255f) else Color.White.copy(alpha = 150f / 255f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .aqiChartSelect(points.size, onSelect)
            .semantics { contentDescription = "空气质量柱状图，点按或拖动查看具体数值" },
    ) {
        val count = points.size
        if (count == 0) return@Canvas
        // 时间标签：窄屏两行交错；底部预留两行空间。
        val captionProbe = measurer.measure("00:00", TextStyle(fontSize = 9.sp))
        val captionLine = captionProbe.size.height + 2.dp.toPx()
        val stagger = (captionProbe.size.width + 4.dp.toPx()) * 6f > size.width
        val top = 4.dp.toPx()
        val bottom = size.height - maxOf(30.dp.toPx(), captionLine * (if (stagger) 2 else 1) + 10.dp.toPx())
        if (bottom <= top) return@Canvas
        val ceiling = maxOf(150f, ceil(points.maxOf { it.value } / 50f) * 50f)
        val yOf: (Int) -> Float = { value ->
            bottom - (value.coerceIn(0, ceiling.toInt()) / ceiling) * (bottom - top)
        }

        // 安全区（AQI ≤ 50）：铺浅绿，外加强调线
        drawRect(safeFill, Offset(0f, yOf(50)), Size(size.width, bottom - yOf(50)))
        listOf(0, 50, 100, 150).filter { it <= ceiling }.forEach { value ->
            drawLine(
                color = if (value == 50) safeLine else gridColor,
                start = Offset(0f, yOf(value)),
                end = Offset(size.width, yOf(value)),
                strokeWidth = if (value == 50) 1.5.dp.toPx() else 1.dp.toPx(),
            )
        }

        val slot = size.width / count
        val barWidth = (slot * .70f).coerceAtLeast(3.dp.toPx())
        val selectedIndex = selected.coerceIn(0, count - 1)
        points.forEachIndexed { index, point ->
            val cx = slot * index + slot / 2f
            val barTop = yOf(point.value)
            drawRoundRect(
                color = smoothAqiColor(point.value.toDouble()),
                topLeft = Offset(cx - barWidth / 2f, barTop),
                size = Size(barWidth, (bottom - barTop).coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(3.5.dp.toPx()),
            )
            // 时间标签：首尾贴边，其余居中；窄屏两行交错
            if (count <= 8 || index % 6 == 0 || index == count - 1) {
                val layout = measurer.measure(point.axisLabel, TextStyle(fontSize = 9.sp, color = labelColor))
                val safeCx = when (index) {
                    0 -> layout.size.width / 2f
                    count - 1 -> size.width - layout.size.width / 2f
                    else -> cx
                }
                val half = (layout.size.width / 2f + 1.dp.toPx()).coerceAtMost(size.width / 2f)
                val clamped = safeCx.coerceIn(half, (size.width - half).coerceAtLeast(half))
                val offset = if (stagger && index % 2 == 0) captionLine else 0f
                drawText(
                    layout,
                    topLeft = Offset(
                        clamped - layout.size.width / 2f,
                        size.height - 10.dp.toPx() - layout.size.height - offset,
                    ),
                )
            }
        }

        // 选中柱：虚线导向线 + 三层数据焦点
        val point = points[selectedIndex]
        val cx = (slot * selectedIndex + slot / 2f).coerceIn(0f, size.width)
        drawLine(
            color = guideColor,
            start = Offset(cx, top),
            end = Offset(cx, bottom),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        drawChartMarker(
            center = Offset(cx, yOf(point.value)),
            color = smoothAqiColor(point.value.toDouble()),
            dark = !light,
        )
    }
}

private fun Modifier.aqiChartSelect(count: Int, onSelect: (Int) -> Unit): Modifier = pointerInput(count) {
    if (count <= 0) return@pointerInput
    fun pick(x: Float): Int = if (count <= 1) 0 else (x / size.width * count).toInt().coerceIn(0, count - 1)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onSelect(pick(down.position.x))
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                break
            }
            change.consume()
            onSelect(pick(change.position.x))
        }
    }
}

// ── 全国排名 ──────────────────────────────────────────────

@Composable
private fun AqiRankBody(city: City?) {
    val candidates = remember(city) {
        MeeAirQualityRankRepository.rankCandidateChain(city?.name, city?.affiliation)
    }
    var result by remember(candidates) { mutableStateOf<MeeAirQualityRankRepository.Result?>(null) }
    LaunchedEffect(candidates) {
        result = if (candidates.isEmpty()) {
            MeeAirQualityRankRepository.Result.CityNotCovered
        } else {
            MeeAirQualityRankRepository.fetch(candidates)
        }
    }

    val available = result as? MeeAirQualityRankRepository.Result.Available
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (available != null) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("第", style = MaterialTheme.typography.titleMedium, color = ZhishengTextSecondary)
                    Text(
                        " ${available.rank} ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = ZhishengText,
                    )
                    Text(
                        "/ ${available.total} 座城市",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (available.matchedCity != available.requestedCity) {
                        "按上级城市 ${available.matchedCity} 统计"
                    } else {
                        "按同一批次发布时间排序"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = ZhishengTextTertiary,
                )
            }
            Text(
                "前 ${available.topPercent}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = rankPercentTone(available.topPercent),
            )
        } else {
            Text(
                when {
                    result == null -> "正在获取生态环境部实时报…"
                    result == MeeAirQualityRankRepository.Result.CityNotCovered ->
                        "生态环境部实时报暂未收录该地点，切换到所在城市可以查看排名"
                    else -> "全国排名暂不可用"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ZhishengTextSecondary,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = MochiTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    Spacer(Modifier.height(10.dp))
    Text(
        buildString {
            append("数据来自生态环境部城市 AQI 实时报，按同一发布时间内 AQI 由低到高计算。")
            available?.reportedAtMillis?.takeIf { it > 0L }?.let {
                append(" 最新批次 ${Fmt.clock(it, 8 * 3600)}。")
            }
        },
        style = MaterialTheme.typography.labelMedium,
        color = ZhishengTextTertiary,
    )
}

/** 排名百分位配色：越靠前越绿，越靠后越暖。 */
private fun rankPercentTone(percent: Int): Color = when {
    percent <= 30 -> Color(0xFF57D380)
    percent <= 70 -> Color(0xFFFFD247)
    else -> Color(0xFFFFA04C)
}
