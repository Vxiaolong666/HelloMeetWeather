package com.zhisheng.weather.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 云松天气「48 小时天气」与「未来 15 日天气详情」共用的渐变 / 调色板 / 几何规则。
 * 全部常量与算法从 yunsong HourlyForecastDetailActivity / HourlyForecastDetailPolicy /
 * ForecastChartGradient / ChartSelectionMarker / ForecastSummaryAdvice 原样移植。
 */

// —— ForecastChartGradient 的线色（自上而下、按值升序） ——
internal val TrendTemperatureColors = listOf(
    Color(0xFF4294FF), Color(0xFF54D368), Color(0xFFFFBF33), Color(0xFFFF504E),
)
internal val TrendWindColors = listOf(
    Color(0xFFAAE7FF), Color(0xFF5FBDFF), Color(0xFF1C70DA),
)
internal val TrendUvColors = listOf(
    Color(0xFF4CD669), Color(0xFFE5DF31), Color(0xFFFFAC23), Color(0xFFFF5457), Color(0xFFE73C91),
)
internal val TrendHumidityColors = listOf(
    Color(0xFF57D380), Color(0xFF32B4BF), Color(0xFF3E83FF),
)
internal val TrendRainColors = listOf(
    Color(0xFF41A4EC), Color(0xFF1769D5),
)
internal val TrendPressureColors = listOf(
    Color(0xFF84CDFF), Color(0xFFB99DFF), Color(0xFFEFADFF),
)
internal val TrendVisibilityColors = listOf(
    Color(0xFF4ACFA9), Color(0xFF53BCEB), Color(0xFF3987FF),
)
internal val TrendAqiStops = listOf(0.0, 50.0, 100.0, 150.0, 200.0, 300.0, 500.0)
internal val TrendAqiColors = listOf(
    Color(0xFF3ED362), Color(0xFF9CD639), Color(0xFFFFCD2E), Color(0xFFFF972F),
    Color(0xFFE25357), Color(0xFF9C4EAE), Color(0xFF7C2B4C),
)

internal fun trendAqiColor(aqi: Double?): Color {
    val v = aqi?.takeIf(Double::isFinite)?.coerceIn(0.0, 500.0) ?: return TrendAqiColors[0]
    for (i in 0 until TrendAqiStops.lastIndex) {
        val lo = TrendAqiStops[i]
        val hi = TrendAqiStops[i + 1]
        if (v <= hi) {
            val f = if (hi > lo) ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) else 0f
            return lerpColor(TrendAqiColors[i], TrendAqiColors[i + 1], f)
        }
    }
    return TrendAqiColors.last()
}

/** 信息卡温度渐变条的五段色（左低右高）。 */
internal val TrendTempRangeColors = listOf(
    Color(0xFF85CFEF), Color(0xFF66CFB1), Color(0xFFE1D35B), Color(0xFFF29D46), Color(0xFFE24F4C),
)

internal fun trendTempRangeColor(progress: Float): Color {
    val p = progress.coerceIn(0f, 1f)
    val pos = p * TrendTempRangeColors.lastIndex
    val lower = pos.toInt().coerceIn(0, TrendTempRangeColors.lastIndex)
    val upper = (lower + 1).coerceAtMost(TrendTempRangeColors.lastIndex)
    return lerpColor(TrendTempRangeColors[lower], TrendTempRangeColors[upper], pos - lower)
}

private fun lerpColor(a: Color, b: Color, f: Float): Color {
    val t = f.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}

/** 指标图调色板（深浅主题）。 */
internal data class TrendChartPalette(
    val temperature: Color,
    val rain: Color,
    val wind: Color,
    val ultraviolet: Color,
    val aqi: Color,
    val grid: Color,
    val barTrack: Color,
) {
    companion object {
        fun forTheme(isLight: Boolean): TrendChartPalette {
            val secondary = if (isLight) Color(0xFF59677C) else Color(0xFFB8C4D3)
            return TrendChartPalette(
                temperature = if (isLight) Color(0xFF293340) else Color(0xFFF4F7FB),
                rain = if (isLight) Color(0xFF1C81D3) else Color(0xFF71CBFF),
                wind = if (isLight) Color(0xFF008479) else Color(0xFF4FD2BA),
                ultraviolet = if (isLight) Color(0xFF8E58CC) else Color(0xFFBA91F4),
                aqi = if (isLight) Color(0xFF30964C) else Color(0xFF72CA62),
                grid = secondary.copy(alpha = if (isLight) 44f / 255f else 66f / 255f),
                barTrack = secondary.copy(alpha = if (isLight) 46f / 255f else 64f / 255f),
            )
        }
    }
}

// —— HourlyForecastDetailPolicy 几何规则 ——
internal const val TrendPointCount = 48
internal const val TrendHourStepDp = 26f
internal const val TrendEdgeInsetDp = 12f
internal const val TrendBarWidthDp = 11f
internal const val TrendBarSelectedWidthDp = 24f
internal const val TrendTimeAxisIntervalHours = 6
internal const val TrendHighlightedMetricScale = 1.72f

internal fun trendSelectionEmphasis(distanceColumns: Float): Float {
    val raw = (1f - abs(distanceColumns)).coerceIn(0f, 1f)
    return raw * raw * (3f - 2f * raw)
}

internal fun trendBarWidthDp(emphasis: Float): Dp =
    (TrendBarWidthDp + (TrendBarSelectedWidthDp - TrendBarWidthDp) * emphasis.coerceIn(0f, 1f)).dp

internal fun trendMetricLabelScale(emphasis: Float): Float =
    1f + (TrendHighlightedMetricScale - 1f) * emphasis.coerceIn(0f, 1f)

internal fun trendShouldShowTimeLabel(index: Int): Boolean =
    index >= 0 && index % TrendTimeAxisIntervalHours == 0

internal fun trendMetricY(value: Double, low: Double, high: Double, graphTop: Float, graphBottom: Float): Float {
    val range = (high - low).coerceAtLeast(0.01)
    val normalized = ((value - low) / range).coerceIn(0.0, 1.0)
    return (graphBottom - normalized * (graphBottom - graphTop)).toFloat()
}

internal fun trendMovingSelectionViewportX(
    scrollX: Float,
    maxScroll: Float,
    initialX: Float,
    viewportWidth: Float,
    edgeInset: Float,
): Float {
    val right = (viewportWidth - edgeInset).coerceAtLeast(edgeInset)
    if (maxScroll <= 0f) return initialX.coerceIn(edgeInset, right)
    val progress = (scrollX / maxScroll).coerceIn(0f, 1f)
    return initialX + (right - initialX) * progress
}

// —— ChartSelectionMarker：三层同心圆（8dp 光晕 / 6dp 芯 / 3.5dp 数据色） ——
internal fun DrawScope.drawTrendMarker(center: Offset, color: Color, isLight: Boolean, dp: Float) {
    val halo = color.copy(alpha = 92f / 255f)
    drawCircle(halo, 8f * dp, center)
    val core = if (isLight) Color(0xFF1A2433) else Color.White
    drawCircle(core, 6f * dp, center)
    drawCircle(color, 3.5f * dp, center)
}

/** 渐变条（横向）用于信息卡温度轨道。 */
internal fun DrawScope.drawHorizontalGradientBar(
    left: Float,
    right: Float,
    y: Float,
    thickness: Float,
    colors: List<Color>,
    alpha: Float = 1f,
) {
    if (right - left <= 0f) return
    drawLine(
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = thickness,
        brush = Brush.horizontalGradient(colors.map { it.copy(alpha = it.alpha * alpha) }),
        cap = StrokeCap.Round,
    )
}

/** 历史段虚线。 */
internal fun trendHistoryDash(dp: Float): PathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * dp, 4f * dp), 0f)
internal fun trendGuideDash(dp: Float): PathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * dp, 5f * dp), 0f)

internal fun DrawScope.trendStroke(widthDp: Float, dash: PathEffect? = null): Stroke =
    Stroke(width = widthDp, cap = StrokeCap.Round, pathEffect = dash)

// —— ForecastSummaryAdvice 文案 ——
internal fun trendRainAdvice(value: Int): String = when {
    value <= 0 -> "今日无需备伞"
    value <= 10 -> "降水机会小，轻装出行"
    value < 30 -> "或有零星降水，备折叠伞"
    value < 60 -> "有雨可能，出门带伞"
    value < 80 -> "降水可能较大，备好雨具"
    else -> "降水概率高，注意避雨"
}

internal fun trendWindAdvice(maximum: Double): String = when {
    maximum < 6 -> "微风轻拂，适宜户外"
    maximum < 12 -> "风力轻柔，适合出行"
    maximum < 25 -> "风力适中，骑行防风"
    maximum < 40 -> "风力较强，避开高处"
    else -> "大风天气，减少外出"
}

internal fun trendUvAdvice(maximum: Double): String = when {
    maximum <= 1 -> "今日无需额外防晒"
    maximum <= 2 -> "紫外线较弱，适宜户外"
    maximum <= 5 -> "紫外线适中，建议防晒"
    maximum <= 7 -> "紫外线较强，做好防晒"
    maximum <= 10 -> "紫外线很强，避开午间"
    else -> "紫外线极强，减少暴晒"
}

internal fun trendAirAdvice(maximum: Double): String = when {
    maximum <= 50 -> "空气优，适宜户外"
    maximum <= 100 -> "空气良好，适宜出行"
    maximum <= 150 -> "敏感人群减少户外"
    maximum <= 200 -> "污染较重，减少户外"
    maximum <= 300 -> "空气污染重，尽量少外出"
    else -> "严重污染，尽量留在室内"
}

internal fun trendPercent(value: Double?): Int? =
    value?.takeIf(Double::isFinite)?.let { (if (it <= 1.0) it * 100.0 else it).roundToInt().coerceIn(0, 100) }
