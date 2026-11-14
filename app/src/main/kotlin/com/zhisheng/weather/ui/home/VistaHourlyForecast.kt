package com.zhisheng.weather.ui.home

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.phaseAwareCondition
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.LocalHomeSurfaceStyle
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Temperature readings follow their contour; weather details retain their shared baseline. */
@Composable
internal fun VistaHourlyForecast(
    items: List<HourlyDisplayItem>,
    data: WeatherData,
    unit: String,
    utcOffsetSeconds: Int?,
) {
    if (items.isEmpty()) return
    val palette = LocalZhishengPalette.current
    val haptic = LocalHapticFeedback.current
    val inspectionMode = LocalInspectionMode.current
    val edgeOptics = remember(inspectionMode) {
        if (!inspectionMode && Build.VERSION.SDK_INT >= 33) HourlyEdgeOptics() else null
    }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val trend = remember(items) { hourlyTemperatureTrend(items) }
    val curves = remember(trend) { hourlyTemperatureCurves(trend) }
    val hasTrend = trend.any { it != null }
    val temperatureLabelHeight = 26.dp * fontScale
    val temperatureTravel = if (hasTrend) HourlyTemperatureProfileTravel else 0.dp
    val temperatureProfileHeight = temperatureLabelHeight + temperatureTravel +
        if (hasTrend) HourlyTemperatureLineGap + HourlyTemperatureMistDepth else 0.dp
    val iconToTemperatureGap = if (hasTrend) 6.dp else 8.dp
    val showRain = vistaHourlyShowsPrecip(items.map { it.weather })
    // 逐时列表用与经典页同款的 LazyRow（state 交给库横向滚动），曲线画在列表背后、
    // 随滚动平移，列内容与曲线始终对齐。cityChartGestures 的嵌套滚动连接把横向滚动
    // 认领下来，城市分页/侧推返回不会抢；自定义 detectHorizontalDragGestures 在这里会
    // 和 HorizontalPager 抢事件导致无法滑动，已退回 LazyRow + cityChartGestures()。
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val visibleColumns = floor(maxWidth.value / (56f * fontScale)).toInt()
            .coerceIn(if (fontScale <= 1.5f) 3 else 2, 8)
        val cellWidth = maxWidth / visibleColumns
        val cellPx = with(LocalDensity.current) { cellWidth.toPx() }
        val surfaceStyle = LocalHomeSurfaceStyle.current
        val refract = surfaceStyle == HomeSurfaceStyle.FRAGRANCE_GLASS && edgeOptics != null
        // 全部逐时列等宽，滚动偏移 = 首个可见项索引 × 格宽 + 项内偏移。
        fun scrollPx(): Float = listState.firstVisibleItemIndex * cellPx + listState.firstVisibleItemScrollOffset
        val maxScrollPx = (items.size * cellPx - with(LocalDensity.current) { maxWidth.toPx() }).coerceAtLeast(0f)

        LaunchedEffect(listState, cellPx, haptic) {
            var lastHour = -1
            snapshotFlow {
                if (listState.isScrollInProgress) listState.firstVisibleItemIndex else -1
            }.distinctUntilChanged().collect { hour ->
                if (hour >= 0 && lastHour >= 0 && hour != lastHour) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastHour = hour
            }
        }
        LaunchedEffect(listState, cellPx, surfaceStyle, items.size) {
            snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged()
                .collect { inProgress ->
                    if (!inProgress && maxScrollPx > 0) {
                        val target = ((scrollPx() / cellPx).roundToInt() * cellPx).roundToInt()
                            .coerceIn(0, maxScrollPx.roundToInt())
                        if (abs(target - scrollPx()) > 1) {
                            val index = (target / cellPx).roundToInt().coerceIn(0, items.lastIndex)
                            val itemOffset = (target - index * cellPx).roundToInt().coerceIn(0, cellPx.roundToInt())
                            listState.animateScrollToItem(index, itemOffset)
                        }
                    }
                }
        }
        // Forecast text stays flat and readable; only the last pixels bend under the glass lip.
        Box(Modifier.fillMaxWidth().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }.drawWithContent {
            drawContent()
            val edge = ((if (refract) 14.dp else 12.dp).toPx() / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.16f)
            drawRect(Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = if (scrollPx() > 0) 0f else 1f),
                edge to Color.Black,
                (1f - edge) to Color.Black,
                1f to Color.Black.copy(alpha = if (scrollPx() < maxScrollPx) 0f else 1f),
            ), blendMode = BlendMode.DstIn)
        }) {
        Box(Modifier.fillMaxWidth().graphicsLayer {
            if (refract && Build.VERSION.SDK_INT >= 33) {
                renderEffect = edgeOptics?.effect(
                    width = size.width,
                    height = size.height,
                    edgeWidth = 20.dp.toPx(),
                    hasLeft = scrollPx() > 0,
                    hasRight = scrollPx() < maxScrollPx,
                )
            }
        }) {
        // 逐时列内容用与经典页同款的 LazyRow 横向滚动；cityChartGestures 的嵌套滚动连接把
        // 横向滚动认领下来，城市分页/侧推返回不会抢。温度曲线画在列表背后、随滚动平移，
        // 列内容与曲线始终对齐（此前 custom detectHorizontalDragGestures / Row+horizontalScroll
        // 都会和 HorizontalPager 抢事件导致无法滑动，已退回 LazyRow + cityChartGestures()）。
        Box(Modifier.fillMaxWidth().clipToBounds().drawWithCache {
            // Match the actual rounded Column/Spacer measurements, including large type.
            val columnWidth = cellWidth.roundToPx().toFloat()
            val top = (22.dp * fontScale).roundToPx() + 8.dp.roundToPx() +
                28.dp.roundToPx() + iconToTemperatureGap.roundToPx()
            val lineTop = top + temperatureLabelHeight.roundToPx() + HourlyTemperatureLineGap.roundToPx()
            val points = trend.mapIndexed { index, point ->
                point?.let { Offset((index + 0.5f) * columnWidth,
                    lineTop + temperatureTravel.toPx() * it.fraction) }
            }
            // Shared pure geometry keeps adjacent segments tangent-continuous. The edge
            // continuation follows the real trend instead of adding a short horizontal shelf.
            val floorY = lineTop + temperatureTravel.toPx() + HourlyTemperatureMistDepth.toPx()
            val travel = temperatureTravel.toPx()
            val contours = curves.map { curve ->
                val first = curve.segments.first().start
                val last = curve.segments.last().end
                val trace = Path().apply {
                    moveTo(first.x * columnWidth, lineTop + first.fraction * travel)
                    curve.segments.forEach { segment ->
                        cubicTo(segment.firstControl.x * columnWidth, lineTop + segment.firstControl.fraction * travel,
                            segment.secondControl.x * columnWidth, lineTop + segment.secondControl.fraction * travel,
                            segment.end.x * columnWidth, lineTop + segment.end.fraction * travel)
                    }
                }
                val wash = Path().apply {
                    addPath(trace)
                    lineTo(last.x * columnWidth, floorY)
                    lineTo(first.x * columnWidth, floorY)
                    close()
                }
                trace to wash
            }
            val warm = lerp(palette.orange, palette.text, if (palette.isLight) 0.16f else 0.35f)
            val cool = lerp(palette.cyan, palette.mint, 0.25f)
            val colorTop = lineTop.toFloat()
            val dyes = trend.map { lerp(warm, cool, it?.fraction ?: 0.5f).copy(alpha = 0.88f) }
            // 曲线画在列表背后，坐标以整段内容（items.size × 格宽）为基准，绘制时按滚动偏移平移。
            val contentWidth = (items.size * columnWidth).coerceAtLeast(1f)
            val edge = (14.dp.toPx() / contentWidth).coerceIn(0f, 0.15f)
            val colorStops = buildList<Pair<Float, Color>> {
                add(0f to dyes.first().copy(alpha = 0f))
                add(edge to dyes.first())
                dyes.forEachIndexed { index, color -> add((index + 0.5f) * columnWidth / contentWidth to color) }
                add((1f - edge) to dyes.last())
                add(1f to dyes.last().copy(alpha = 0f))
            }
            val silk = Brush.horizontalGradient(*colorStops.toTypedArray())
            val mist = Brush.verticalGradient(listOf(warm.copy(alpha = 0.12f),
                cool.copy(alpha = 0.045f), cool.copy(alpha = 0f)), colorTop, floorY.coerceAtLeast(colorTop + 1f))
            val traceStroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round)
            val softStroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val softOffset = 1.dp.toPx()
            val nowRadius = 2.dp.toPx()
            onDrawBehind {
                withTransform({ translate(left = -scrollPx()) }) {
                    contours.forEach { (trace, wash) ->
                        drawPath(wash, mist)
                        withTransform({ translate(top = softOffset) }) {
                            drawPath(trace, silk, alpha = 0.075f, style = softStroke)
                        }
                        drawPath(trace, silk, style = traceStroke)
                    }
                    points.forEachIndexed { index, point ->
                        if (point != null && items[index].isNow) {
                            val tint = lerp(warm, cool, trend[index]!!.fraction)
                            drawCircle(tint.copy(alpha = 0.12f), 5.dp.toPx(), point)
                            drawCircle(tint, nowRadius, point)
                            drawCircle(lerp(tint, palette.text, 0.6f), 0.8.dp.toPx(), point)
                        }
                    }
                }
            }
        }) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().cityChartGestures(),
            ) {
                itemsIndexed(items, key = { i, item ->
                    if (item.isNow) "now-${item.weather.timeMillis}" else "hour-$i-${item.weather.timeMillis}"
                }) { index, item ->
                val h = item.weather
                val condition = phaseAwareCondition(h.condition, data, h.timeMillis)
                val temperature = Fmt.temp(h.temperature?.takeIf(Double::isFinite), unit)?.let { "$it°" } ?: "—"
                val numberSize = minOf(20f, cellWidth.value / (temperature.length * 0.58f * fontScale))
                Column(Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Every reading occupies the full cell width and a shared row height.
                    // Different icon silhouettes and text lengths must not shift a column's axis.
                    Box(Modifier.fillMaxWidth().height(22.dp * fontScale), contentAlignment = Alignment.Center) {
                        Text(if (item.isNow) "现在" else Fmt.hour(h.timeMillis, utcOffsetSeconds),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            color = if (item.isNow) palette.mint else palette.textSecondary,
                            fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                        WeatherIcon(condition, Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(iconToTemperatureGap))
                    // The number and its curve point form one object, with a fixed clear gap.
                    Box(Modifier.fillMaxWidth().height(temperatureProfileHeight)) {
                        Box(Modifier.fillMaxWidth().height(temperatureLabelHeight)
                            .offset(y = temperatureTravel * (trend[index]?.fraction ?: 0.5f)),
                            contentAlignment = Alignment.Center) {
                            Text(temperature, color = if (item.isNow) palette.mint else palette.text,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                fontSize = numberSize.sp, lineHeight = 25.sp,
                                fontWeight = FontWeight.Light, letterSpacing = (-0.4).sp, maxLines = 1)
                        }
                    }
                    Box(Modifier.height(18.dp * fontScale).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(condition?.takeUnless { it == WeatherCondition.UNKNOWN }?.label ?: "暂无",
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            color = palette.textSecondary, fontSize = 11.sp,
                            lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (showRain) Row(Modifier.fillMaxWidth().height(20.dp * fontScale),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        val label = vistaHourlyPrecipLabel(h.precipProb)
                        if (label.isNotEmpty()) {
                            val tint = if ((h.precipProb ?: 0) > 0) palette.cyan else palette.textTertiary
                            PhosphorIcon(R.drawable.ph_drop, null, Modifier.size(10.dp), tint.copy(alpha = 0.7f))
                            Spacer(Modifier.width(3.dp))
                            Text(label, color = tint, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(20.dp * fontScale).padding(top = 2.dp), contentAlignment = Alignment.Center) {
                        Text(hourlyWindLabel(h) ?: "—", color = palette.textSecondary,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                }
            }
        }
        }
        }
        }
    }
}

private val HourlyTemperatureProfileTravel = 36.dp
private val HourlyTemperatureLineGap = 6.dp
private val HourlyTemperatureMistDepth = 14.dp
