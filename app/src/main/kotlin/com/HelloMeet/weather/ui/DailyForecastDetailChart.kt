package com.HelloMeet.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HelloMeet.weather.model.HourlyWeather
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 逐日详情页的指标分页标签（水平滚动，与 yunsong DailyForecastMetricPanel 对齐）。 */
internal enum class DailyTrendMetric(val title: String, val colors: List<Color>) {
    TEMPERATURE("温度/体感", TrendTemperatureColors),
    RAIN("降水", TrendRainColors),
    WIND("风力", TrendWindColors),
    UV("紫外线", TrendUvColors),
    HUMIDITY("湿度", TrendHumidityColors),
    PRESSURE("气压", TrendPressureColors),
    VISIBILITY("能见度", TrendVisibilityColors),
    AQI("空气", TrendAqiColors);

    fun value(hour: HourlyWeather): Double? = when (this) {
        TEMPERATURE -> hour.feelsLike
        RAIN -> hour.precipProb?.toDouble()
        WIND -> hour.windSpeed
        UV -> hour.uvIndex?.toDouble()
        HUMIDITY -> hour.humidity
        PRESSURE -> hour.pressure
        VISIBILITY -> hour.visibility
        AQI -> hour.aqi?.toDouble()
    }?.takeIf(Double::isFinite)

    fun secondary(hour: HourlyWeather): Double? = when (this) {
        TEMPERATURE -> hour.temperature
        WIND -> hour.windGust
        else -> null
    }?.takeIf(Double::isFinite)

    fun text(value: Double?, tempUnit: String, windUnit: String): String = when (this) {
        TEMPERATURE -> value?.let { "${Fmt.temp(it, tempUnit) ?: it.roundToInt()}°" } ?: "暂无数据"
        RAIN -> value?.roundToInt()?.let { "$it%" } ?: "暂无数据"
        AQI -> value?.roundToInt()?.let { "$it AQI" } ?: "暂无数据"
        WIND -> value?.let { Fmt.wind(it, windUnit) ?: it.roundToInt().toString() } ?: "暂无数据"
        UV -> value?.roundToInt()?.let { "${it}级" } ?: "暂无数据"
        HUMIDITY -> value?.roundToInt()?.let { "$it%" } ?: "暂无数据"
        PRESSURE -> value?.roundToInt()?.let { "$it hPa" } ?: "暂无数据"
        VISIBILITY -> value?.let { "$it km" } ?: "暂无数据"
    }

    /** 分段选择器短标签：库分段选择器等宽分格，长标题会裁切，用两字缩写。 */
    val segment: String get() = when (this) {
        TEMPERATURE -> "温度"
        RAIN -> "降水"
        WIND -> "风力"
        UV -> "紫外"
        HUMIDITY -> "湿度"
        PRESSURE -> "气压"
        VISIBILITY -> "能见"
        AQI -> "空气"
    }
}

private data class TrendAxisRange(val minimum: Double, val maximum: Double) {
    val span: Double get() = (maximum - minimum).coerceAtLeast(0.01)
}

private fun trendAxisRange(metric: DailyTrendMetric, values: List<Double>, secondary: List<Double>): TrendAxisRange {
    val all = values + secondary
    val rawMax = all.maxOrNull() ?: 1.0
    if (metric != DailyTrendMetric.TEMPERATURE && metric != DailyTrendMetric.PRESSURE) {
        val upper = ceil(max(1.0, rawMax * 1.12) / 4.0) * 4.0
        return TrendAxisRange(0.0, upper)
    }
    val rawMin = all.minOrNull() ?: 0.0
    val span = (rawMax - rawMin).coerceAtLeast(if (metric == DailyTrendMetric.PRESSURE) 2.0 else 4.0)
    val padding = max(if (metric == DailyTrendMetric.PRESSURE) 1.0 else 2.0, span * 0.18)
    var lower = floor(rawMin - padding)
    var upper = ceil(rawMax + padding)
    if (upper - lower < 2.0) {
        lower = floor(rawMin) - 1.0
        upper = ceil(rawMax) + 1.0
    }
    val remainder = ((upper - lower).toInt() % 4 + 4) % 4
    if (remainder != 0) upper += 4 - remainder
    return TrendAxisRange(lower, upper)
}

private fun trendAxisText(metric: DailyTrendMetric, value: Double): String = when (metric) {
    DailyTrendMetric.RAIN -> "${value.roundToInt()}%"
    DailyTrendMetric.AQI -> value.roundToInt().toString()
    else -> value.roundToInt().toString()
}

/**
 * 逐日详情渐变图：迁移 yunsong HourlyMetricChartView —— 主值彩线（按值纵向渐变、4dp）、
 * 可选灰色/浅蓝次级线、线下渐变面积（上浓下淡）、5 条网格线、右侧轴刻度与底部 HH:mm
 * 时间标签、点选时刻的竖向引导线 + 三层圆点。点选由 [onSelect] 上报，宿主负责震动。
 */
@Composable
internal fun DailyForecastGradientChart(
    hours: List<HourlyWeather>,
    metric: DailyTrendMetric,
    tempUnit: String,
    windUnit: String,
    isLight: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val secondaryColor = if (isLight) Color(0xFF505E70) else Color(0xFFD6EBF6)
    val gridColor = if (isLight) Color(0xFF222433).copy(alpha = 34f / 255f) else Color.White.copy(alpha = 42f / 255f)
    val guideColor = if (isLight) Color(0xFF1A2433).copy(alpha = 76f / 255f) else Color.White.copy(alpha = 70f / 255f)
    val selGuideColor = if (isLight) Color(0xFF1A2433).copy(alpha = 132f / 255f) else Color.White.copy(alpha = 145f / 255f)
    val axisColor = if (isLight) Color(0xFF2A394A).copy(alpha = 178f / 255f) else Color.White.copy(alpha = 174f / 255f)
    val captionColor = if (isLight) Color(0xFF2A394A).copy(alpha = 185f / 255f) else Color.White.copy(alpha = 178f / 255f)

    val values = hours.map { metric.value(it) ?: Double.NaN }
    val second = hours.map { metric.secondary(it) ?: Double.NaN }
    val axis = trendAxisRange(metric, values.filter { !it.isNaN() }, second.filter { !it.isNaN() })
    val latestOnSelect by rememberUpdatedState(onSelect)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(260.dp)
            // 点按 / 拖动选时刻：x 映射到最近的小时列，宿主在 selected 变化时触发震动。
            .pointerInput(hours, metric) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun indexFor(x: Float): Int {
                        if (hours.size <= 1) return 0
                        val left = 2.dp.toPx()
                        val right = (size.width - 2.dp.toPx()).coerceAtLeast(left + 1f)
                        return ((x - left) / (right - left) * (hours.size - 1)).roundToInt()
                            .coerceIn(0, hours.size - 1)
                    }
                    latestOnSelect(indexFor(down.position.x))
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed } ?: break
                        latestOnSelect(indexFor(change.position.x))
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val n = values.size
        if (n < 2) return@Canvas
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 8.dp.toPx() + 3.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        if (bottom - top <= 0f) return@Canvas
        fun xOf(i: Int): Float = left + (right - left) * i / (n - 1)
        fun yOf(v: Double): Float = (bottom - ((v - axis.minimum) / axis.span) * (bottom - top)).toFloat()

        // 5 条网格线 + 轴刻度
        repeat(5) { step ->
            val f = step / 4f
            val y = bottom - (bottom - top) * f
            drawLine(gridColor, Offset(left, y), Offset(right, y), 1.dp.toPx())
            val v = axis.minimum + axis.span * f
            drawBaselineTrendText(measurer, trendAxisText(metric, v), right - 3.dp.toPx(), y + 3.dp.toPx(), 10f, axisColor, false, align = Align.RIGHT)
        }
        // 时间标签 + 竖向引导线（每 6 小时 + 末列）
        val labelIndices = (values.indices.filter { it % 6 == 0 } + (n - 1)).distinct()
        labelIndices.forEach { idx ->
            drawLine(guideColor, Offset(xOf(idx), top), Offset(xOf(idx), bottom), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            val caption = trendTimeLabel(hours[idx].timeMillis)
            drawBaselineTrendText(measurer, caption, xOf(idx), size.height - 8.dp.toPx(), 11f, captionColor, false, align = Align.CENTER)
        }

        // 次级线（气温 / 阵风）
        val secondaryRuns = trendCurveRuns(second, ::xOf, ::yOf)
        if (metric == DailyTrendMetric.WIND) {
            drawTrendRuns(secondaryRuns, Color(0xFFBEE6FF), 2.5f, 1.dp.toPx())
        } else {
            drawTrendRuns(secondaryRuns, secondaryColor, 2.5f, 1.dp.toPx())
        }

        // 主值线 + 渐变面积
        val primaryRuns = trendCurveRuns(values, ::xOf, ::yOf)
        val fillAlpha = if (metric == DailyTrendMetric.WIND) 0.55f else 0.36f
        // 线下面积按值色纵向渐变，且上浓下淡（顶部贴近曲线处最实，越往下越透明）。
        val fillStops = metric.colors.mapIndexed { i, c ->
            val base = if (metric.colors.size > 1) i / (metric.colors.size - 1f) else 0f
            base to c.copy(alpha = fillAlpha * base)
        }
        primaryRuns.forEach { run ->
            val path = Path().apply {
                run.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            if (run.isNotEmpty()) {
                path.lineTo(run.last().x, bottom)
                path.lineTo(run.first().x, bottom)
                path.close()
                drawPath(
                    path,
                    brush = Brush.verticalGradient(*fillStops.toTypedArray(), startY = bottom, endY = top),
                )
            }
        }
        drawTrendRuns(primaryRuns, Color.White, 4f, 1.dp.toPx(), brush = Brush.verticalGradient(metric.colors, startY = bottom, endY = top))

        // 选中引导线 + 标记
        if (selected in values.indices) {
            val sx = xOf(selected)
            drawLine(selGuideColor, Offset(sx, top), Offset(sx, bottom), 1.dp.toPx())
            val v = values[selected]
            if (!v.isNaN()) {
                val markerColor = trendColorAt(metric.colors, yOf(v).toFloat(), top, bottom)
                drawTrendMarker(Offset(sx, yOf(v)), markerColor, isLight, 1.dp.toPx())
            }
            val s = second[selected]
            if (!s.isNaN() && metric == DailyTrendMetric.WIND) {
                drawTrendMarker(Offset(sx, yOf(s)), Color(0xFFBEE6FF), isLight, 1.dp.toPx())
            }
        }
    }
}

private enum class Align { LEFT, CENTER, RIGHT }

private fun DrawScope.drawBaselineTrendText(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    baseline: Float,
    fontSize: Float,
    color: Color,
    bold: Boolean,
    align: Align,
) {
    val layout = measurer.measure(
        text,
        TextStyle(fontSize = fontSize.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
    )
    val drawX = when (align) {
        Align.LEFT -> x
        Align.CENTER -> x - layout.size.width / 2f
        Align.RIGHT -> x - layout.size.width
    }
    drawText(layout, topLeft = Offset(drawX, baseline - layout.getLineBaseline(0)))
}

/** 二次贝塞尔中线折线段（NaN 断开）。 */
private fun trendCurveRuns(values: List<Double>, xOf: (Int) -> Float, yOf: (Double) -> Float): List<List<Offset>> {
    val runs = mutableListOf<List<Offset>>()
    var current = mutableListOf<Offset>()
    values.forEachIndexed { i, v ->
        if (v.isNaN()) {
            if (current.size >= 2) runs.add(current)
            current = mutableListOf()
        } else {
            current.add(Offset(xOf(i), yOf(v)))
        }
    }
    if (current.size >= 2) runs.add(current)
    return runs
}

private fun DrawScope.drawTrendRuns(
    runs: List<List<Offset>>,
    color: Color,
    width: Float,
    dp: Float,
    brush: Brush? = null,
) {
    runs.forEach { run ->
        val path = Path()
        run.forEachIndexed { i, p ->
            if (i == 0) {
                path.moveTo(p.x, p.y)
            } else {
                val prev = run[i - 1]
                val midX = (prev.x + p.x) / 2f
                val midY = (prev.y + p.y) / 2f
                if (i == run.lastIndex) path.quadraticTo(prev.x, prev.y, p.x, p.y)
                else path.quadraticTo(prev.x, prev.y, midX, midY)
            }
        }
        if (brush != null) drawPath(path, brush = brush, style = Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        else drawPath(path, color = color, style = Stroke(width = width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

private val trendDayTimeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

internal fun trendTimeLabel(timeMillis: Long): String =
    Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).format(trendDayTimeFmt)

/** 读数行：当前选中时刻的指标读数与说明。 */
internal fun dailyTrendReading(metric: DailyTrendMetric, hour: HourlyWeather, tempUnit: String, windUnit: String): String = when (metric) {
    DailyTrendMetric.TEMPERATURE -> {
        val feels = metric.value(hour)
        val actual = metric.secondary(hour)
        "体感 ${metric.text(feels, tempUnit, windUnit)}  气温 ${metric.text(actual, tempUnit, windUnit)}"
    }
    else -> "${metric.title} ${metric.text(metric.value(hour), tempUnit, windUnit)}"
}
