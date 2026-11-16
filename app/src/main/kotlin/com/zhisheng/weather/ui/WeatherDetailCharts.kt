package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class MetricAxis(val minimum: Double, val maximum: Double) {
    val span: Double get() = (maximum - minimum).coerceAtLeast(1.0)
}

/**
 * 逐小时指标曲线：语义完全移植 yunsong 的 HourlyMetricChartView ——
 * 五条横向网格 + 每 6 小时一条虚线竖向刻度、主线 4dp 纵向色彩渐变、面积用
 * 「颜色渐变 × 向下淡出」双层合成、选中三层数据焦点（8dp 光晕 / 6dp 主题内核 / 3.5dp 数据色）。
 * 拖动时只重绘画布、不重组页面文字。
 */
@Composable
internal fun MetricDetailChart(
    kind: MetricKind,
    times: List<Long>,
    values: List<Double?>,
    secondary: List<Double?>,
    utcOffsetSeconds: Int?,
    prefs: DisplayPrefs,
    tempUnit: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val light = LocalZhishengPalette.current.isLight
    // 与 yunsong 的 Paint 色完全一致：网格/刻度/选中导向/纵轴/时间刻度各用同一组 ARGB。
    val gridColor = if (light) Color(0xFF1A2433).copy(alpha = 34f / 255f) else Color.White.copy(alpha = 42f / 255f)
    val guideColor = if (light) Color(0xFF1A2433).copy(alpha = 76f / 255f) else Color.White.copy(alpha = 70f / 255f)
    val selectionColor = if (light) Color(0xFF1A2433).copy(alpha = 132f / 255f) else Color.White.copy(alpha = 145f / 255f)
    val labelColor = if (light) Color(0xFF2A394A).copy(alpha = 178f / 255f) else Color.White.copy(alpha = 174f / 255f)
    val captionColor = if (light) Color(0xFF2A394A).copy(alpha = 185f / 255f) else Color.White.copy(alpha = 178f / 255f)
    val windSecondary = Color(0xFFBEE6FF)
    val feelsSecondary = if (light) Color(0xFF50606E).copy(alpha = .80f) else Color(0xFFD6EBF6).copy(alpha = .80f)
    val colors = kind.colors
    val description = "${kind.title}逐时曲线，点按或拖动选择时刻"

    Canvas(
        modifier
            .fillMaxWidth()
            .height(244.dp)
            .metricSelect(times.size, onSelect)
            .semantics { contentDescription = description },
    ) {
        if (times.isEmpty() || values.isEmpty()) return@Canvas
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 8.dp.toPx()
        // 窄屏两行交错排时间刻度，与 yunsong wideCaptions 同判据。
        val captionProbe = measurer.measure("00:00", TextStyle(fontSize = 11.sp))
        val captionLine = captionProbe.size.height + 2.dp.toPx()
        val captionRows = if ((captionProbe.size.width + 4.dp.toPx()) * 6f > size.width) 2 else 1
        val bottom = size.height - max(30.dp.toPx(), captionRows * captionLine + 12.dp.toPx())
        if (right <= left || bottom <= top) return@Canvas
        val axis = axisRange(kind, values + secondary)
        val xOf: (Int) -> Float = { index ->
            if (times.size <= 1) (left + right) / 2f
            else left + (right - left) * index / (times.size - 1).toFloat()
        }
        val yOf: (Double) -> Float = { raw ->
            val value = raw.coerceIn(axis.minimum, axis.maximum)
            bottom - ((value - axis.minimum) / axis.span).toFloat() * (bottom - top)
        }

        // 网格与右侧纵轴读数（文字垂直居中在网格线上）
        repeat(5) { step ->
            val fraction = step / 4f
            val y = bottom - (bottom - top) * fraction
            drawLine(gridColor, Offset(left, y), Offset(right, y), 1.dp.toPx())
            val value = axis.minimum + axis.span * fraction
            val layout = measurer.measure(
                kind.axisText(value, prefs, tempUnit),
                TextStyle(fontSize = 10.sp, color = labelColor),
            )
            drawText(
                layout,
                topLeft = Offset(
                    (right - layout.size.width - 3.dp.toPx()).coerceAtLeast(left),
                    (y - layout.size.height / 2f).coerceIn(top, bottom - layout.size.height),
                ),
            )
        }
        // 时间刻度：每 6 小时一条虚线，末点始终标出；窄屏两行交错
        val labelIndices = times.indices.filter { it % 6 == 0 || it == times.lastIndex }
        labelIndices.forEachIndexed { tick, index ->
            val x = xOf(index)
            drawLine(
                guideColor, Offset(x, top), Offset(x, bottom), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
            val label = Fmt.clock(times[index], utcOffsetSeconds)
            val layout = measurer.measure(label, TextStyle(fontSize = 11.sp, color = captionColor))
            val half = (layout.size.width / 2f + 1.dp.toPx()).coerceAtMost(size.width / 2f)
            val safeX = x.coerceIn(half, (size.width - half).coerceAtLeast(half))
            val offset = if (captionRows == 2 && tick % 2 == 0) captionLine else 0f
            drawText(
                layout,
                topLeft = Offset(
                    safeX - layout.size.width / 2f,
                    size.height - 8.dp.toPx() - layout.size.height - offset,
                ),
            )
        }

        val primaryRuns = curveRuns(values, xOf, yOf)
        val secondaryRuns = curveRuns(secondary, xOf, yOf)
        val areaAlpha = if (kind == MetricKind.WIND) {
            if (light) .56f else .44f
        } else {
            .36f
        }
        val lineBrush = Brush.verticalGradient(
            colorStops = metricLineStops(colors),
            startY = bottom,
            endY = top,
        )
        // 面积：颜色沿纵轴分布，同时向下淡出（等价 yunsong 的 ComposeShader(color, 白→透明, DST_IN)）
        val areaBrush = Brush.verticalGradient(
            colorStops = metricAreaStops(colors, areaAlpha),
            startY = bottom,
            endY = top,
        )
        primaryRuns.forEach { run ->
            val area = Path().apply {
                addPath(run.path)
                lineTo(run.lastX, bottom)
                lineTo(run.firstX, bottom)
                close()
            }
            drawPath(area, areaBrush)
        }
        // 对照线：风力为亮蓝，体感为灰蓝；宽度 2.5dp
        if (secondaryRuns.isNotEmpty()) {
            val secondaryColor = when (kind) {
                MetricKind.WIND -> windSecondary
                else -> feelsSecondary
            }
            secondaryRuns.forEach { run ->
                drawPath(
                    run.path,
                    secondaryColor,
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        // 主线 4dp，颜色随纵向渐变
        primaryRuns.forEach { run ->
            if (run.count == 1) {
                drawCircle(colors.last(), 3.5.dp.toPx(), Offset(run.firstX, yOf(values[run.firstIndex]!!)))
            } else {
                drawPath(
                    run.path,
                    lineBrush,
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        // 选中时刻：实线导向线 + 三层数据焦点（主曲线；风力再给阵风一个焦点）
        val index = selected.coerceIn(0, times.lastIndex)
        val sx = xOf(index)
        drawLine(selectionColor, Offset(sx, top), Offset(sx, bottom), 1.dp.toPx())
        values.getOrNull(index)?.takeIf { it.isFinite() }?.let { value ->
            val tone = metricColorAt(kind, value, axis)
            drawChartMarker(Offset(sx, yOf(value)), tone, light)
        }
        if (kind == MetricKind.WIND) {
            secondary.getOrNull(index)?.takeIf { it.isFinite() }?.let { gust ->
                drawChartMarker(Offset(sx, yOf(gust)), windSecondary, light)
            }
        }
    }
}

private data class CurveRun(
    val path: Path,
    val firstX: Float,
    val lastX: Float,
    val firstIndex: Int,
    val count: Int,
)

/** 连续段拆成独立路径：缺测值处断开，不把空段连成假曲线。 */
private fun curveRuns(
    series: List<Double?>,
    xOf: (Int) -> Float,
    yOf: (Double) -> Float,
): List<CurveRun> {
    val runs = ArrayList<CurveRun>()
    var start = -1
    var last = -1
    fun flush() {
        if (start < 0 || last < 0) return
        val path = Path()
        val startPoint = Offset(xOf(start), yOf(series[start]!!))
        path.moveTo(startPoint.x, startPoint.y)
        if (last > start) {
            var previous = startPoint
            for (index in start + 1..last) {
                val current = Offset(xOf(index), yOf(series[index]!!))
                if (index < last) {
                    val midX = (previous.x + current.x) / 2f
                    val midY = (previous.y + current.y) / 2f
                    path.quadraticTo(previous.x, previous.y, midX, midY)
                } else {
                    path.quadraticTo(previous.x, previous.y, current.x, current.y)
                }
                previous = current
            }
        }
        runs += CurveRun(path, xOf(start), xOf(last), start, last - start + 1)
        start = -1
        last = -1
    }
    series.forEachIndexed { index, value ->
        if (value == null || !value.isFinite()) {
            flush()
        } else {
            if (start < 0) start = index
            last = index
        }
    }
    flush()
    return runs
}

/** 常规指标 0 起点；体感与气压贴近数据整数范围，避免曲线被压扁。 */
private fun axisRange(kind: MetricKind, all: List<Double?>): MetricAxis {
    val values = all.filterNotNull().filter { it.isFinite() }
    val rawMax = values.maxOrNull() ?: 1.0
    if (kind != MetricKind.FEELS_LIKE && kind != MetricKind.PRESSURE) {
        val upper = ceil(max(1.0, rawMax * 1.12) / 4.0) * 4.0
        return MetricAxis(0.0, upper)
    }
    val rawMin = values.minOrNull() ?: 0.0
    val span = (rawMax - rawMin).coerceAtLeast(if (kind == MetricKind.PRESSURE) 2.0 else 4.0)
    val padding = max(if (kind == MetricKind.PRESSURE) 1.0 else 2.0, span * 0.18)
    var lower = floor(rawMin - padding)
    var upper = ceil(rawMax + padding)
    if (upper - lower < 2.0) {
        lower = floor(rawMin) - 1.0
        upper = ceil(rawMax) + 1.0
    }
    // 五条网格线把范围切成四段，上限对齐整数避免重复刻度。
    val remainder = ((upper - lower).toInt() % 4 + 4) % 4
    if (remainder != 0) upper += 4 - remainder
    return MetricAxis(lower, upper)
}

/**
 * 主线 / 面积的颜色渐变：Compose 的 stop 从 startY 起算，这里 startY = bottom，
 * 所以 index 0 落在底部（第一色，低值）、最后一个颜色落在顶部（高值）——与 yunsong 一致。
 */
private fun metricLineStops(colors: List<Color>): Array<Pair<Float, Color>> =
    colors.mapIndexed { index, color ->
        val fraction = index / (colors.size - 1f)
        fraction to color
    }.toTypedArray()

/** 面积填充 = 颜色渐变 × 向下淡出：底部透明、顶部保留最大不透明度（等价 yunsong DST_IN 的白→透明）。 */
private fun metricAreaStops(colors: List<Color>, alpha: Float): Array<Pair<Float, Color>> =
    colors.mapIndexed { index, color ->
        val fraction = index / (colors.size - 1f)
        fraction to color.copy(alpha = alpha * fraction)
    }.toTypedArray()

/** 选中点颜色按数值插值：低值取第一色，高值取最后一色。 */
private fun metricColorAt(kind: MetricKind, value: Double, axis: MetricAxis): Color {
    val colors = kind.colors
    val fraction = ((value - axis.minimum) / axis.span).toFloat().coerceIn(0f, 1f)
    val scaled = fraction * (colors.size - 1)
    val start = scaled.toInt().coerceIn(0, colors.lastIndex)
    val end = (start + 1).coerceAtMost(colors.lastIndex)
    return lerp(colors[start], colors[end], scaled - start)
}

/** 与 yunsong ChartSelectionMarker 一致的三层数据焦点。 */
internal fun DrawScope.drawChartMarker(center: Offset, color: Color, dark: Boolean) {
    drawCircle(color.copy(alpha = .36f), 8.dp.toPx(), center)
    drawCircle(if (dark) Color.White else Color(0xFF1A2433), 6.dp.toPx(), center)
    drawCircle(color, 3.5.dp.toPx(), center)
}

private fun Modifier.metricSelect(count: Int, onSelect: (Int) -> Unit): Modifier = pointerInput(count) {
    if (count <= 0) return@pointerInput
    awaitEachGesture {
        fun pick(x: Float) {
            if (count <= 1) {
                onSelect(0)
            } else {
                onSelect(((x / size.width) * (count - 1)).roundToInt().coerceIn(0, count - 1))
            }
        }
        val down = awaitFirstDown(requireUnconsumed = false)
        // 图表自己吃掉横向手势：选点优先于 HorizontalPager 翻页。
        down.consume()
        pick(down.position.x)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                break
            }
            change.consume()
            pick(change.position.x)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 日出日落：全天太阳高度曲线
// ═══════════════════════════════════════════════════════════

@Composable
internal fun SunAltitudeChart(
    timeline: SunTimeline,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val dark = !LocalZhishengPalette.current.isLight
    val points = timeline.points

    Canvas(
        modifier
            .fillMaxWidth()
            .height(192.dp)
            .metricSelect(points.size, onSelect)
            .semantics { contentDescription = "全天太阳高度曲线，点按或拖动选择时刻" },
    ) {
        if (points.size < 2) return@Canvas
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 10.dp.toPx()
        val captionProbe = measurer.measure("00:00", TextStyle(fontSize = 11.sp))
        val captionLine = captionProbe.size.height + 2.dp.toPx()
        val stagger = (captionProbe.size.width + 4.dp.toPx()) * 6f > size.width
        val bottom = size.height - max(26.dp.toPx(), captionLine * (if (stagger) 2 else 1) + 9.dp.toPx())
        if (right <= left || bottom <= top) return@Canvas
        val minValue = min(-20.0, points.minOf { it.second })
        val maxValue = max(50.0, points.maxOf { it.second })
        val span = (maxValue - minValue).coerceAtLeast(1.0)
        val xOf: (Int) -> Float = { index ->
            left + (right - left) * index / (points.size - 1).toFloat()
        }
        val yOf: (Double) -> Float =
            { value -> bottom - (((value - minValue) / span).toFloat() * (bottom - top)) }
        val horizon = yOf(0.0)

        val deepNight = Color(0xFF0A235B)
        val lightNight = Color(0xFF6EB7FF)
        val sunYellow = Color(0xFFFFD347)
        val sunOrange = Color(0xFFFF6943)
        val sky = Color(0xFF48A4FF)

        // 地平线：虚线（4/4）
        val horizonColor = if (dark) Color.White.copy(alpha = .45f) else Color(0xFF263241).copy(alpha = .33f)
        drawLine(
            horizonColor, Offset(left, horizon), Offset(right, horizon), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )

        // 曲线：折线，颜色严格按高度由下至上（夜蓝 → 蓝 → 黄 → 橙）
        val curve = Path()
        points.forEachIndexed { index, (_, elevation) ->
            val point = Offset(xOf(index), yOf(elevation))
            if (index == 0) curve.moveTo(point.x, point.y) else curve.lineTo(point.x, point.y)
        }
        // 只填充地平线以上的白昼区域：橙 → 黄 → 淡天蓝
        val daylight = Path()
        var startedDaylight = false
        var lastDaylightX = left
        points.forEachIndexed { index, (_, elevation) ->
            val px = xOf(index)
            if (elevation >= 0.0) {
                if (!startedDaylight) {
                    daylight.moveTo(px, horizon)
                    startedDaylight = true
                }
                daylight.lineTo(px, yOf(elevation))
                lastDaylightX = px
            } else if (startedDaylight) {
                daylight.lineTo(lastDaylightX, horizon)
                daylight.close()
                startedDaylight = false
            }
        }
        if (startedDaylight) {
            daylight.lineTo(lastDaylightX, horizon)
            daylight.close()
        }
        drawPath(
            daylight,
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to sunOrange.copy(alpha = .44f),
                    .55f to sunYellow.copy(alpha = .25f),
                    1f to sky.copy(alpha = .03f),
                ),
                startY = top,
                endY = horizon,
            ),
        )
        val horizonFraction = ((bottom - horizon) / (bottom - top)).coerceIn(.05f, .88f)
        drawPath(
            curve,
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to deepNight,
                    (horizonFraction * .72f) to lightNight,
                    horizonFraction to lightNight,
                    (horizonFraction + .10f).coerceAtMost(.94f) to sunYellow,
                    1f to sunOrange,
                ),
                startY = bottom,
                endY = top,
            ),
            style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 选中时刻：虚线导向线 + 三层数据焦点（颜色按当前高度取值）
        val index = selected.coerceIn(0, points.lastIndex)
        val sx = xOf(index)
        val guide = if (dark) Color.White.copy(alpha = .67f) else Color(0xFF18222C).copy(alpha = .51f)
        drawLine(
            guide, Offset(sx, top), Offset(sx, bottom), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        drawChartMarker(Offset(sx, yOf(points[index].second)), sunCurveColor(points[index].second), dark)

        // 时间刻度：0 / 1/4 / 1/2 / 3/4 / 末，整点；窄屏两行交错
        val labelColor = if (dark) Color.White.copy(alpha = .75f) else Color(0xFF707780)
        val labelIndices = listOf(0, points.lastIndex / 4, points.lastIndex / 2, points.lastIndex * 3 / 4, points.lastIndex)
            .distinct()
        labelIndices.forEachIndexed { tick, tickIndex ->
            val label = Fmt.clock(points[tickIndex].first, timeline.utcOffsetSeconds)
            val layout = measurer.measure(label, TextStyle(fontSize = 11.sp, color = labelColor))
            val half = (layout.size.width / 2f + 1.dp.toPx()).coerceAtMost(size.width / 2f)
            val offset = if (stagger && tick % 2 == 0) captionLine else 0f
            drawText(
                layout,
                topLeft = Offset(
                    (xOf(tickIndex).coerceIn(half, (size.width - half).coerceAtLeast(half)) - layout.size.width / 2f),
                    size.height - 7.dp.toPx() - layout.size.height - offset,
                ),
            )
        }
    }
}

/** 与 yunsong 的 curveColor 一致：夜间深蓝→浅蓝，白昼黄→橙。 */
private fun sunCurveColor(elevation: Double): Color {
    fun blend(start: Color, end: Color, progress: Float): Color = lerp(start, end, progress)
    return if (elevation < 0.0) {
        blend(Color(0xFF0A235B), Color(0xFF6EB7FF), ((elevation + 20.0) / 20.0).toFloat().coerceIn(0f, 1f))
    } else {
        blend(Color(0xFFFFD347), Color(0xFFFF6943), (elevation / 60.0).toFloat().coerceIn(0f, 1f))
    }
}
