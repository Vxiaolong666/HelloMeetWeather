package com.HelloMeet.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.HourlyWeather
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val TrendHourMillis = 3_600_000L

/**
 * 「48 小时天气」独立页：由首页「逐时预报」标题行右侧的「详情」入口进入。
 * 完整迁移 yunsong HourlyForecastDetailActivity 的 48 小时模式 —— 信息摘要卡（气温范围
 * 渐变条 + 当前标记 + 降水/风力/紫外线/空气质量四格）、指标图例 +「回到当前」、
 * 五条指标轨道（温度折线 / 降水柱 / 风力折线 / 紫外线折线 / 空气质量柱）共享横向滚动，
 * 线色与柱色按值做纵向渐变，选中列随滚动连续放大 / 加粗 / 加宽。外壳沿用库
 * [MochiPageFrame] 与库返回按钮，不自行处理返回。
 */
@Composable
internal fun Hourly48Screen(
    city: City?,
    hours: List<HourlyWeather>,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    val now = weatherPresentationTime()
    val points = hours.take(TrendPointCount)
    val isLight = LocalZhishengPalette.current.isLight
    val palette = remember(isLight) { TrendChartPalette.forTheme(isLight) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val atCurrent = scrollState.maxValue <= 0 || scrollState.value == 0

    MochiPageFrame(title = "48小时天气", subtitle = city?.displayName, onBack = onBack) {
        if (points.isEmpty()) {
            item {
                MochiPageCard(header = "信息摘要", animateHeight = false) {
                    Text(
                        "当前城市暂未返回逐时预报，请稍后刷新。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                    )
                }
            }
            return@MochiPageFrame
        }
        item {
            MochiPageCard(header = "信息摘要", animateHeight = false) {
                Hourly48SummaryCard(points, tempUnit, windUnit, now, palette)
            }
        }
        item {
            MochiPageCard(animateHeight = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hourly48LegendRow(palette)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Hourly48ReturnCurrentButton(
                            enabled = !atCurrent,
                            onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                        )
                    }
                    Hourly48CompositeChart(points, now, tempUnit, palette, isLight, scrollState)
                }
            }
        }
    }
}

// ─────────────────────────── 信息摘要卡（紧凑布局） ───────────────────────────

internal data class TrendSummaryMetric(
    val kind: TrendSummaryKind,
    val label: String,
    val value: String,
    val detail: String,
    val advice: String,
    val progress: Float,
    val color: Color,
    val currentValue: String? = null,
    val currentProgress: Float? = null,
)

internal enum class TrendSummaryKind { TEMPERATURE, RAIN, WIND, UV, AQI }

@Composable
private fun Hourly48SummaryCard(
    points: List<HourlyWeather>,
    tempUnit: String,
    windUnit: String,
    now: Long,
    palette: TrendChartPalette,
) {
    val metrics = remember(points, tempUnit, windUnit, now) {
        buildHourly48SummaryMetrics(points, tempUnit, windUnit, now, palette)
    }
    val temperature = metrics.first { it.kind == TrendSummaryKind.TEMPERATURE }
    val others = metrics.filter { it.kind != TrendSummaryKind.TEMPERATURE }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 气温范围 + 当前标记
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(0.56f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("气温范围", style = MaterialTheme.typography.labelMedium, color = ZhishengTextTertiary)
                Text(
                    temperature.value,
                    style = MaterialTheme.typography.titleLarge,
                    color = temperature.color,
                    fontWeight = FontWeight.Bold,
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
            }
            Column(Modifier.weight(0.44f), horizontalAlignment = Alignment.End) {
                temperature.currentValue?.let {
                    Text(
                        "当前 $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Hourly48TempTrack(
                    progress = temperature.currentProgress,
                    isLight = LocalZhishengPalette.current.isLight,
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    val lows = points.mapNotNull { it.temperature?.takeIf(Double::isFinite) }
                    val highs = lows
                    Text(
                        "低 ${degreeText(lows.minOrNull(), tempUnit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "高 ${degreeText(highs.maxOrNull(), tempUnit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextSecondary,
                    )
                }
            }
        }
        // 四格
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            others.take(2).forEach { metric ->
                Hourly48SummaryQuadCell(metric, Modifier.weight(1f))
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            others.drop(2).forEach { metric ->
                Hourly48SummaryQuadCell(metric, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun Hourly48SummaryQuadCell(metric: TrendSummaryMetric, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        TrendMetricRing(metric.kind, metric.color, metric.progress)
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            Text(
                metric.value,
                style = MaterialTheme.typography.titleMedium,
                color = metric.color,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${metric.detail} · ${metric.advice}",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextSecondary,
            )
        }
    }
}

@Composable
private fun Hourly48TempTrack(progress: Float?, isLight: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val y = size.height / 2f
        drawLine(
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 4.dp.toPx(),
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(TrendTempRangeColors),
            cap = StrokeCap.Round,
        )
        if (progress != null) {
            val x = size.width * progress.coerceIn(0f, 1f)
            val color = trendTempRangeColor(progress)
            drawTrendMarker(Offset(x, y), color, isLight, 1.dp.toPx())
        }
    }
}

/** 指标环形图标：进度弧 + 中心符号（雨滴 / 风 / 太阳 / 三点）。 */
@Composable
internal fun TrendMetricRing(kind: TrendSummaryKind, color: Color, progress: Float) {
    Canvas(Modifier.size(30.dp)) {
        val r = 13.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color.copy(alpha = 48f / 255f), r, c, style = Stroke(2.5.dp.toPx()))
        if (progress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        when (kind) {
            TrendSummaryKind.RAIN -> drawDrop(c, r, color)
            TrendSummaryKind.WIND -> drawWind(c, r, color)
            TrendSummaryKind.UV -> drawSun(c, r, color)
            TrendSummaryKind.AQI -> drawAqiDots(c, r, color)
            else -> {}
        }
    }
}

private fun DrawScope.drawDrop(c: Offset, r: Float, color: Color) {
    val s = r * 0.36f
    val p = Path().apply {
        moveTo(c.x, c.y - s)
        cubicTo(c.x + s * 0.9f, c.y, c.x + s * 0.6f, c.y + s, c.x, c.y + s)
        cubicTo(c.x - s * 0.6f, c.y + s, c.x - s * 0.9f, c.y, c.x, c.y - s)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawWind(c: Offset, r: Float, color: Color) {
    val w = r * 0.52f
    val stroke = 1.7.dp.toPx()
    for (i in 0..2) {
        val y = c.y - r * 0.26f + i * r * 0.26f
        drawLine(color, Offset(c.x - w, y), Offset(c.x + w * 0.5f, y), stroke, StrokeCap.Round)
        drawLine(color, Offset(c.x + w * 0.5f, y), Offset(c.x + w, y - r * 0.13f), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.drawSun(c: Offset, r: Float, color: Color) {
    drawCircle(color, r * 0.34f, c)
    // 太阳射线收在圆环内（此前外伸到 r*2.05 超出 30dp 画布、叠到右侧标题上）：
    // 射线从中心圆外沿伸到环内沿附近，最大半径 r*0.78 < 环半径 r，不再溢出。
    val inner = r * 0.45f
    val outer = r * 0.78f
    for (i in 0 until 8) {
        val angle = Math.toRadians(i * 45.0)
        val dx = kotlin.math.cos(angle).toFloat()
        val dy = kotlin.math.sin(angle).toFloat()
        drawLine(color, Offset(c.x + dx * inner, c.y + dy * inner), Offset(c.x + dx * outer, c.y + dy * outer), 1.4.dp.toPx(), StrokeCap.Round)
    }
}

private fun DrawScope.drawAqiDots(c: Offset, r: Float, color: Color) {
    val dotR = r * 0.34f
    for (i in -1..1) drawCircle(color, dotR, Offset(c.x + i * r * 0.44f, c.y))
}

// ─────────────────────────── 图例 + 回到当前 ───────────────────────────

@Composable
private fun Hourly48LegendRow(palette: TrendChartPalette) {
    val items = listOf(
        "温度" to TrendTemperatureColors,
        "降水" to TrendRainColors,
        "风力" to TrendWindColors,
        "紫外线" to TrendUvColors,
        "空气质量" to TrendAqiColors,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, colors) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 10.dp, height = 7.dp)) {
                    drawRoundRect(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(colors),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                }
                Spacer(Modifier.width(3.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
            }
        }
    }
}

@Composable
private fun Hourly48ReturnCurrentButton(enabled: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .background(
                color = if (enabled) primary.copy(alpha = 28f / 255f) else secondary.copy(alpha = 18f / 255f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .then(
                if (enabled) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                else Modifier
            ),
    ) {
        Text(
            "回到当前",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) primary else secondary,
            fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ─────────────────────────── 五条指标轨道合成图 ───────────────────────────

@Composable
internal fun Hourly48CompositeChart(
    points: List<HourlyWeather>,
    now: Long,
    tempUnit: String,
    palette: TrendChartPalette,
    isLight: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val hourStepPx = with(density) { (TrendHourStepDp.dp * fontScale).toPx() }
    val edgeInsetPx = with(density) { (TrendEdgeInsetDp.dp * fontScale).toPx() }
    val initialIndex = points.indexOfFirst { it.timeMillis / TrendHourMillis >= now / TrendHourMillis }
        .takeIf { it >= 0 } ?: 0
    val initialSelectionX = edgeInsetPx + hourStepPx * initialIndex
    val totalWidthPx = (edgeInsetPx * 2f + hourStepPx * (points.size - 1).coerceAtLeast(0)).coerceAtLeast(1f)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val totalWidthDp = with(density) { totalWidthPx.toDp() }
        val maxScroll = (totalWidthPx - viewportWidthPx).coerceAtLeast(0f)
        val haptic = LocalHapticFeedback.current
        // 选中列随滚动连续移动，每跨一列给一次图表互动震动。
        LaunchedEffect(haptic, points, viewportWidthPx) {
            snapshotFlow {
                val scrollX = scrollState.value.toFloat()
                val selViewportX = trendMovingSelectionViewportX(
                    scrollX, maxScroll, initialSelectionX, viewportWidthPx, edgeInsetPx,
                )
                val content = (scrollX + selViewportX)
                    .coerceIn(edgeInsetPx, edgeInsetPx + hourStepPx * (points.size - 1))
                ((content - edgeInsetPx) / hourStepPx).roundToInt().coerceIn(0, points.size - 1)
            }.drop(1).collect {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
            Canvas(
                Modifier
                    .width(totalWidthDp)
                    .height(330.dp),
            ) {
                if (points.size < 2) return@Canvas
                val scrollX = scrollState.value.toFloat()
                val selViewportX = trendMovingSelectionViewportX(
                    scrollX, maxScroll, initialSelectionX, viewportWidthPx, edgeInsetPx,
                )
                val selectionContentX = (scrollX + selViewportX).coerceIn(edgeInsetPx, edgeInsetPx + hourStepPx * (points.size - 1))
                val selectedIndex = ((selectionContentX - edgeInsetPx) / hourStepPx).roundToInt()
                    .coerceIn(0, points.size - 1)

                val headerHeight = minOf(36.dp.toPx(), size.height * 0.15f)
                val laneGap = minOf(8.dp.toPx(), maxOf(4.dp.toPx(), size.height * 0.025f))
                val laneCount = 5
                val airLane = laneCount - 1
                val laneHeight = ((size.height - headerHeight - laneGap * (laneCount - 1)) / laneCount)
                    .coerceAtLeast(42.dp.toPx())
                val chartBottom = headerHeight + laneHeight * laneCount + laneGap * (laneCount - 1)
                fun laneTop(i: Int) = headerHeight + i * (laneHeight + laneGap)
                fun laneBottom(i: Int) = laneTop(i) + laneHeight
                val labelOffset = 12.dp.toPx()
                val labelSpace = labelOffset + 10.dp.toPx() + 4.dp.toPx()
                fun graphTop(i: Int) = laneTop(i) + maxOf(labelSpace, minOf(23.dp.toPx(), laneHeight * 0.42f))
                fun graphBottom(i: Int) = laneBottom(i) - minOf(5.dp.toPx(), laneHeight * 0.12f)

                fun xAt(i: Int) = edgeInsetPx + hourStepPx * i.coerceIn(0, points.size - 1)

                // 横向网格 + 六小时虚线引导
                repeat(laneCount) { lane ->
                    drawLine(palette.grid, Offset(0f, laneBottom(lane)), Offset(size.width, laneBottom(lane)), 1.dp.toPx())
                }
                val guideIndices = (points.indices.filter { trendShouldShowTimeLabel(it) } + points.size - 1).distinct()
                guideIndices.forEach { idx ->
                    drawLine(
                        palette.grid,
                        Offset(xAt(idx), headerHeight),
                        Offset(xAt(idx), chartBottom),
                        1.dp.toPx(),
                        pathEffect = trendGuideDash(1.dp.toPx()),
                    )
                }

                // 各指标纵向位置
                val temperatures = points.map { it.temperature }
                val rains = points.map { it.precipProb?.toDouble() }
                val winds = points.map { it.windSpeed }
                val uvs = points.map { it.uvIndex?.toDouble() }
                val airs = points.map { it.aqi?.toDouble() }
                fun positions(values: List<Double?>, lowerFloor: Double?, top: Float, bottom: Float): List<Offset?> {
                    val finite = values.mapNotNull { it?.takeIf(Double::isFinite) }
                    var low = finite.minOrNull() ?: 0.0
                    var high = finite.maxOrNull() ?: 1.0
                    if (lowerFloor != null) low = minOf(low, lowerFloor)
                    val spread = (high - low).takeIf { it >= 0.8 } ?: 1.0
                    low -= spread * 0.08
                    high += spread * 0.08
                    if (lowerFloor != null) low = minOf(low, lowerFloor)
                    return values.mapIndexed { i, v ->
                        v?.takeIf(Double::isFinite)?.let { Offset(xAt(i), trendMetricY(it, low, high, top, bottom)) }
                    }
                }
                val tempPos = positions(temperatures, null, graphTop(0), graphBottom(0))
                val windPos = positions(winds, 0.0, graphTop(2), graphBottom(2))
                val uvPos = positions(uvs, 0.0, graphTop(3), graphBottom(3))

                fun drawSeries(pos: List<Offset?>, colors: List<Color>, top: Float, bottom: Float, strokeDp: Float) {
                    // 历史段（虚线 + 48% 透明度）
                    val pastEnd = points.indexOfFirst { it.timeMillis / TrendHourMillis >= now / TrendHourMillis }
                        .let { if (it < 0) points.size else it }
                    for (i in 0 until pos.lastIndex) {
                        val from = pos[i] ?: continue
                        val to = pos[i + 1] ?: continue
                        val past = i < pastEnd
                        val path = Path().apply {
                            moveTo(from.x, from.y)
                            cubicTo((from.x + to.x) / 2f, from.y, (from.x + to.x) / 2f, to.y, to.x, to.y)
                        }
                        drawPath(
                            path,
                            style = trendStroke(strokeDp, if (past) trendHistoryDash(1.dp.toPx()) else null),
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors.map { if (past) it.copy(alpha = it.alpha * 122f / 255f) else it },
                                startY = bottom,
                                endY = top,
                            ),
                        )
                    }
                }

                fun drawBars(values: List<Double?>, top: Float, bottom: Float, baseColor: Color, normalize: (Double) -> Float, colors: List<Color>) {
                    values.forEachIndexed { idx, raw ->
                        val emphasis = trendSelectionEmphasis((xAt(idx) - selectionContentX) / hourStepPx)
                        val barWidth = with(density) { trendBarWidthDp(emphasis).toPx() }
                        val left = xAt(idx) - barWidth / 2f
                        val right = xAt(idx) + barWidth / 2f
                        val radius = minOf(barWidth / 2f, 5.dp.toPx())
                        drawRoundRect(
                            color = palette.barTrack.copy(alpha = if (idx < pastEndOf(points, now)) palette.barTrack.alpha * 122f / 255f else palette.barTrack.alpha),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                        )
                        val value = raw?.takeIf(Double::isFinite) ?: return@forEachIndexed
                        val ratio = normalize(value)
                        if (ratio <= 0f) return@forEachIndexed
                        val filledTop = (bottom - (bottom - top) * ratio).coerceAtMost(bottom - 2.dp.toPx())
                        val isPast = idx < pastEndOf(points, now)
                        if (baseColor == palette.aqi) {
                            drawRoundRect(
                                color = trendAqiColor(value).copy(alpha = if (isPast) 122f / 255f else 1f),
                                topLeft = Offset(left, filledTop),
                                size = Size(right - left, bottom - filledTop),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                            )
                        } else {
                            drawRoundRect(
                                topLeft = Offset(left, filledTop),
                                size = Size(right - left, bottom - filledTop),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors.map { if (isPast) it.copy(alpha = it.alpha * 122f / 255f) else it },
                                    startY = bottom,
                                    endY = top,
                                ),
                            )
                        }
                    }
                }

                fun drawSeriesLabels(
                    values: List<Double?>,
                    pos: List<Offset?>,
                    colors: List<Color>,
                    lane: Int,
                    text: (Double?) -> String,
                    colorAt: (Double?, Int) -> Color,
                ) {
                    for (idx in 0 until points.size) {
                        val emphasis = trendSelectionEmphasis((xAt(idx) - selectionContentX) / hourStepPx)
                        val accent = colorAt(values[idx], idx)
                        val focus = emphasis.coerceIn(0f, 1f)
                        val fontSize = 8.5f * trendMetricLabelScale(focus)
                        drawCenteredTrendText(
                            measurer, text(values[idx]), xAt(idx), laneTop(lane) + labelOffset,
                            fontSize, if (focus >= 0.48f) accent else paletteSecondary(isLight), focus >= 0.48f,
                        )
                    }
                }

                drawSeries(tempPos, TrendTemperatureColors, graphTop(0), graphBottom(0), 2.5f)
                drawBars(rains, graphTop(1), graphBottom(1), palette.rain, { (it / 100.0).toFloat().coerceIn(0f, 1f) }, TrendRainColors)
                drawSeries(windPos, TrendWindColors, graphTop(2), graphBottom(2), 2.3f)
                drawSeries(uvPos, TrendUvColors, graphTop(3), graphBottom(3), 2.2f)
                val aqiCeiling = (airs.mapNotNull { it?.takeIf(Double::isFinite) }.maxOrNull()?.coerceAtLeast(50.0) ?: 50.0)
                drawBars(airs, graphTop(airLane), graphBottom(airLane), palette.aqi, { (it / aqiCeiling).toFloat().coerceIn(0f, 1f) }, TrendAqiColors)

                // 标签
                drawSeriesLabels(temperatures, tempPos, TrendTemperatureColors, 0, { degreeText(it, tempUnit) }) { v, idx ->
                    tempPos[idx]?.let { trendColorAt(TrendTemperatureColors, it.y, graphTop(0), graphBottom(0)) } ?: palette.temperature
                }
                drawSeriesLabels(rains, emptyList(), TrendRainColors, 1, { trendPercent(it)?.let { "$it%" } ?: "—" }) { _, _ -> palette.rain }
                drawSeriesLabels(winds, windPos, TrendWindColors, 2, { it?.roundToInt()?.toString() ?: "—" }) { v, idx ->
                    windPos[idx]?.let { trendColorAt(TrendWindColors, it.y, graphTop(2), graphBottom(2)) } ?: palette.wind
                }
                drawSeriesLabels(uvs, uvPos, TrendUvColors, 3, { it?.roundToInt()?.let { "${it}级" } ?: "—" }) { v, idx ->
                    uvPos[idx]?.let { trendColorAt(TrendUvColors, it.y, graphTop(3), graphBottom(3)) } ?: palette.ultraviolet
                }
                drawSeriesLabels(airs, emptyList(), TrendAqiColors, airLane, { it?.roundToInt()?.toString() ?: "—" }) { v, _ -> trendAqiColor(v) }

                // 选中点标记（温度 / 风力 / 紫外线）
                fun marker(pos: List<Offset?>, colors: List<Color>, lane: Int) {
                    val raw = ((selectionContentX - edgeInsetPx) / hourStepPx).coerceIn(0f, (points.size - 1).toFloat())
                    val li = floor(raw).toInt()
                    val ri = (li + 1).coerceAtMost(points.size - 1)
                    val f = raw - li
                    val from = pos[li] ?: return
                    val to = pos[ri] ?: return
                    val y = from.y + (to.y - from.y) * f
                    val color = trendColorAt(colors, y, graphTop(lane), graphBottom(lane))
                    val past = points[selectedIndex].timeMillis / TrendHourMillis < now / TrendHourMillis
                    drawTrendMarker(
                        Offset(selectionContentX, y),
                        if (past) color.copy(alpha = color.alpha * 0.48f) else color,
                        isLight,
                        1.dp.toPx(),
                    )
                }
                marker(tempPos, TrendTemperatureColors, 0)
                marker(windPos, TrendWindColors, 2)
                marker(uvPos, TrendUvColors, 3)

                // 时间轴
                for (idx in 0 until points.size) {
                    val emphasis = trendSelectionEmphasis((xAt(idx) - selectionContentX) / hourStepPx)
                    val highlighted = idx == selectedIndex
                    val regularTick = trendShouldShowTimeLabel(idx)
                    if (!regularTick && !highlighted) continue
                    if (regularTick && !highlighted && kotlin.math.abs(idx - selectedIndex) <= 1) continue
                    val focus = if (highlighted) emphasis.coerceAtLeast(0.5f) else 0f
                    val label = trendTimeAxisLabel(points[idx].timeMillis, now)
                    val fontSize = 7.5f * (1f + 0.52f * focus)
                    val baseline = minOf(maxOf(22.dp.toPx(), 11.4f * 1.05f * with(density) { 1.dp.toPx() }), headerHeight * 0.7f)
                    drawTrendTimeLabel(measurer, label, xAt(idx), idx == 0, idx == points.size - 1, size.width, baseline, fontSize, paletteSecondary(isLight), focus)
                }
            }
        }
    }
}

private fun pastEndOf(points: List<HourlyWeather>, now: Long): Int =
    points.indexOfFirst { it.timeMillis / TrendHourMillis >= now / TrendHourMillis }
        .let { if (it < 0) points.size else it }

private fun paletteSecondary(isLight: Boolean): Color =
    if (isLight) Color(0xFF59677C) else Color(0xFFB8C4D3)

internal fun trendColorAt(colors: List<Color>, y: Float, top: Float, bottom: Float): Color {
    val fraction = ((bottom - y) / (bottom - top).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val position = fraction * colors.lastIndex
    val lower = position.toInt().coerceIn(0, colors.lastIndex)
    val upper = (lower + 1).coerceAtMost(colors.lastIndex)
    return lerpTrendColor(colors[lower], colors[upper], position - lower)
}

private fun lerpTrendColor(a: Color, b: Color, f: Float): Color {
    val t = f.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t,
    )
}

private fun DrawScope.drawCenteredTrendText(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    baseline: Float,
    fontSize: Float,
    color: Color,
    bold: Boolean,
) {
    val layout = measurer.measure(
        text,
        TextStyle(fontSize = fontSize.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
    )
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, baseline - layout.getLineBaseline(0)))
}

private fun DrawScope.drawTrendTimeLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    isFirst: Boolean,
    isLast: Boolean,
    width: Float,
    baseline: Float,
    fontSize: Float,
    color: Color,
    focus: Float,
) {
    val bold = focus >= 0.48f
    val layout = measurer.measure(
        text,
        TextStyle(fontSize = fontSize.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
    )
    val drawX = when {
        isFirst -> layout.size.width / 2f
        isLast -> width - layout.size.width / 2f
        else -> x
    }
    drawText(layout, topLeft = Offset(drawX - layout.size.width / 2f, baseline - layout.getLineBaseline(0)))
}

// ─────────────────────────── 摘要数据构建 ───────────────────────────

private fun buildHourly48SummaryMetrics(
    points: List<HourlyWeather>,
    tempUnit: String,
    windUnit: String,
    now: Long,
    palette: TrendChartPalette,
): List<TrendSummaryMetric> {
    val temps = points.mapNotNull { it.temperature?.takeIf(Double::isFinite) }
    val tempLow = temps.minOrNull()
    val tempHigh = temps.maxOrNull()
    val temperatureValue = if (tempLow != null && tempHigh != null) {
        "${degreeText(tempLow, tempUnit)}–${degreeText(tempHigh, tempUnit)}"
    } else {
        "—"
    }
    val current = temps.firstOrNull() ?: tempLow
    val currentProgress = if (tempLow != null && tempHigh != null && tempHigh - tempLow > 0.5 && current != null) {
        ((current - tempLow) / (tempHigh - tempLow)).toFloat().coerceIn(0f, 1f)
    } else if (current != null) {
        0.5f
    } else {
        null
    }
    val tempSpread = ((tempHigh ?: 0.0) - (tempLow ?: 0.0)).coerceAtLeast(0.0)
    val tempProgress = (tempSpread / 14.0).toFloat().coerceIn(0f, 1f)

    val rainPeak = points.maxByOrNull { it.precipProb ?: 0 }?.precipProb
    val rainValue = when {
        rainPeak == null -> "—"
        rainPeak == 0 -> "无雨"
        else -> "$rainPeak%"
    }
    val rainDetail = rainPeak?.let {
        trendCompactTime(points.maxByOrNull { h -> h.precipProb ?: 0 }?.timeMillis ?: now, now)
    } ?: "概率待补充"
    val rainAdvice = if (rainPeak == null) "出门前留意实时天气" else trendRainAdvice(rainPeak)

    val winds = points.mapNotNull { it.windSpeed?.takeIf(Double::isFinite) }
    val windLow = winds.minOrNull() ?: 0.0
    val windHigh = winds.maxOrNull() ?: 0.0
    val windValue = "${windLow.roundToInt()}–${windHigh.roundToInt()}${windUnitLabel(windUnit)}"

    val uvPeak = points.maxByOrNull { it.uvIndex ?: 0 }?.uvIndex
    val uvValue = uvPeak?.let { "${it}级" } ?: "—"
    val uvDetail = uvPeak?.let { "${trendUvSemantic(it)} · ${trendCompactTime(points.maxByOrNull { h -> h.uvIndex ?: 0 }?.timeMillis ?: now, now)}" } ?: "暂无"

    val aqiVals = points.mapNotNull { it.aqi?.toDouble()?.takeIf(Double::isFinite) }
    val aqiBest = aqiVals.minOrNull()
    val aqiWorst = aqiVals.maxOrNull()
    val aqiValue = if (aqiBest != null && aqiWorst != null) "AQI ${aqiBest.roundToInt()}–${aqiWorst.roundToInt()}" else "—"
    val aqiDetail = if (aqiBest != null && aqiWorst != null) trendAirRangeSummary(aqiBest, aqiWorst) else "空气质量暂缺"

    val metrics = listOf(
        TrendSummaryMetric(TrendSummaryKind.TEMPERATURE, "气温范围", temperatureValue, trendTwoDayOutlook(points, tempUnit, now), "气温变化平稳，保持当前着装", tempProgress, palette.temperature,
            currentValue = current?.let { degreeText(it, tempUnit) }, currentProgress = currentProgress),
        TrendSummaryMetric(TrendSummaryKind.RAIN, "降水概率", rainValue, rainDetail, rainAdvice, (rainPeak ?: 0) / 100f, palette.rain),
        TrendSummaryMetric(TrendSummaryKind.WIND, "风力范围", windValue, "未来48小时", trendWindAdvice(windHigh), (windHigh / 45.0).toFloat().coerceIn(0f, 1f), palette.wind),
        TrendSummaryMetric(TrendSummaryKind.UV, "紫外线峰值", uvValue, uvDetail, (uvPeak ?: 0).let { trendUvAdvice(it.toDouble()) }, ((uvPeak ?: 0) / 11f).coerceIn(0f, 1f), palette.ultraviolet),
        TrendSummaryMetric(TrendSummaryKind.AQI, "空气质量", aqiValue, aqiDetail, aqiWorst?.let { trendAirAdvice(it) } ?: "以已提供的数据为准", ((aqiWorst ?: 0.0) / 200.0).toFloat().coerceIn(0f, 1f), palette.aqi),
    )
    return metrics.map { if (it.value == "—") it.copy(detail = "当日数据暂缺", advice = "以已提供的数据为准") else it }
}

internal fun degreeText(value: Double?, tempUnit: String): String =
    value?.takeIf(Double::isFinite)?.let { "${Fmt.temp(it, tempUnit) ?: it.roundToInt()}°" } ?: "--"

internal fun windUnitLabel(windUnit: String): String = when (windUnit) {
    "ms" -> "m/s"
    "bft" -> "级"
    else -> "km/h"
}

internal fun trendUvSemantic(value: Int): String = when {
    value <= 2 -> "很弱"
    value <= 5 -> "中等"
    value <= 7 -> "较强"
    value <= 10 -> "很强"
    else -> "极强"
}

internal fun trendAirRangeSummary(best: Double, worst: Double): String {
    val bestL = trendAirLevel(best)
    val worstL = trendAirLevel(worst)
    return if (bestL == worstL) "整体$bestL" else "整体$bestL，短时$worstL"
}

internal fun trendAirLevel(aqi: Double): String = when {
    aqi <= 50 -> "优"
    aqi <= 100 -> "良好"
    aqi <= 150 -> "轻度污染"
    aqi <= 200 -> "中度污染"
    aqi <= 300 -> "重度污染"
    else -> "严重污染"
}

private fun trendTwoDayOutlook(points: List<HourlyWeather>, tempUnit: String, now: Long): String {
    val dayStarts = points.groupBy { it.timeMillis / (24 * 60 * 60 * 1000L) }
    val futureDays = dayStarts.keys.sorted().filter { it >= now / (24 * 60 * 60 * 1000L) }.take(3)
    if (futureDays.size < 2) return "明日${rangeTextForDay(points, futureDays.getOrNull(1), tempUnit)}"
    val day1 = futureDays[0]
    val day2 = futureDays[1]
    val day2Range = rangeTextForDay(points, day2, tempUnit)
    val day1Range = rangeTextForDay(points, day1, tempUnit)
    val dayOffset1 = day1 - now / (24 * 60 * 60 * 1000L)
    val label1 = when (dayOffset1) { 0L -> "今日"; 1L -> "明日"; 2L -> "后日"; else -> "" }
    return "${label1}$day1Range / 后日$day2Range"
}

private fun rangeTextForDay(points: List<HourlyWeather>, dayBucket: Long?, tempUnit: String): String {
    if (dayBucket == null) return ""
    val temps = points.filter { it.timeMillis / (24 * 60 * 60 * 1000L) == dayBucket }
        .mapNotNull { it.temperature?.takeIf(Double::isFinite) }
    if (temps.isEmpty()) return ""
    return "${degreeText(temps.min(), tempUnit)}–${degreeText(temps.max(), tempUnit)}"
}

private val trendTimeFmt = DateTimeFormatter.ofPattern("HH时", Locale.CHINA)

private fun trendTimeAxisLabel(timeMillis: Long, now: Long): String {
    val prefix = trendDayPrefix(timeMillis, now)
    val hh = Instant.ofEpochMilli(timeMillis).atZone(java.time.ZoneId.systemDefault()).format(trendTimeFmt)
    return "$prefix $hh"
}

internal fun trendCompactTime(timeMillis: Long, now: Long): String {
    val prefix = trendDayPrefix(timeMillis, now)
    val h = Instant.ofEpochMilli(timeMillis).atZone(java.time.ZoneId.systemDefault()).hour
    return "${prefix}${h}点"
}

private fun trendDayPrefix(timeMillis: Long, now: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val offset = java.time.temporal.ChronoUnit.DAYS.between(today, target)
    return when (offset) {
        0L -> "今天"
        1L -> "明天"
        2L -> "后天"
        else -> "${target.monthValue}月${target.dayOfMonth}日"
    }
}
