package com.HelloMeet.weather.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.DailyWeather
import com.HelloMeet.weather.model.cityZone
import com.HelloMeet.weather.model.conditionIconRes
import com.HelloMeet.weather.ui.theme.ZhishengText
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.theme.MochiDefaults
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 「多日天气预报」页：由首页「五天天气」标题行右侧的「更多」入口进入，迁移 yunsong
 * 的 DailyTrendView 多日折线图 —— 顶部星期 / 日期、天气描述 / 图标 / 降水概率，
 * 中间两条温度折线（最高温在上半区、最低温在下半区，Fritsch-Carlson 单调保形
 * 三次贝塞尔）、历史段虚线、圆点与温度标签。
 *
 * 图表横向可滑动（每屏 5 天），点选某一天列触发 [HapticFeedbackType.TextHandleMove]
 * 震动并同步更新下方读数；外壳沿用库 [MochiPageFrame] + 库返回按钮，不自行处理返回。
 */
@Composable
internal fun DailyTrendScreen(
    city: City?,
    days: List<DailyWeather>,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit = {},
) {
    val now = weatherPresentationTime()
    val visibleDays = days.take(15)
    var selectedIndex by remember(visibleDays) {
        mutableIntStateOf(todayIndex(visibleDays, utcOffsetSeconds))
    }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(selectedIndex, haptic) {
        snapshotFlow { selectedIndex }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val pageTitle = if (visibleDays.size >= 15) "多日天气预报" else "${visibleDays.size}日天气预报"
    MochiPageFrame(
        title = pageTitle,
        subtitle = city?.displayName?.takeIf { it.isNotBlank() },
        onBack = onBack,
    ) {
        if (visibleDays.isEmpty()) {
            item {
                MochiPageCard(header = "信息摘要", animateHeight = false) {
                    Text(
                        "当前城市暂未返回逐日预报，请稍后刷新。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                    )
                }
            }
            return@MochiPageFrame
        }
        val safeIndex = selectedIndex.coerceIn(0, visibleDays.lastIndex)
        val selectedDay = visibleDays[safeIndex]
        item {
            MochiPageCard(header = "信息摘要", animateHeight = false) {
                Text(
                    buildDailyTrendSummary(visibleDays, tempUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
            }
        }
        item {
            MochiPageCard(animateHeight = false) {
                DailyTrendChart(
                    days = visibleDays,
                    tempUnit = tempUnit,
                    utcOffsetSeconds = utcOffsetSeconds,
                    selected = safeIndex,
                    onSelect = { selectedIndex = it },
                )
            }
        }
        item {
            MochiPageCard(
                modifier = Modifier.mochiNavSource(
                    // 「打开应用式」转场：把这张「查看该日完整详情」卡片登记为容器变形的源，
                    // 富逐日详情页从它长成整页、返回缩回它（key 与 AppTransitions.dailyTrendDaySourceKey
                    // 一致，含城市后缀防跨城串位）；圆角与底色必须和真实卡片一致。
                    AppTransitions.dailyTrendDaySourceKey(selectedDay.dateMillis, city?.locationKey),
                    MochiListDefaults.SectionCorner,
                    MochiDefaults.groupedContainer(),
                ),
                animateHeight = false,
            ) {
                val day = selectedDay
                val high = day.high?.takeIf(Double::isFinite)?.let { degree(it, tempUnit) } ?: "--"
                val low = day.low?.takeIf(Double::isFinite)?.let { degree(it, tempUnit) } ?: "--"
                Text(
                    "${Fmt.dailyDayLabel(day.dateMillis, now, utcOffsetSeconds)} · ${trendDateText(day.dateMillis, utcOffsetSeconds)} · ${day.weatherText?.takeIf { it.isNotBlank() } ?: day.condition?.label ?: "天气暂缺"}",
                    style = MaterialTheme.typography.titleLarge,
                    color = ZhishengText,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "最高 $high · 最低 $low",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
                val facts = buildList {
                    Fmt.probability(day.precipProbability)?.let { add("降水 $it") }
                    day.humidity?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { add("湿度 ${it.roundToInt()}%") }
                    day.windSpeed?.takeIf { it.isFinite() && it >= 0 }?.let { add("风 ${Fmt.wind(it, windUnit) ?: ""}") }
                    day.sunrise?.takeIf(String::isNotBlank)?.let { add("日出 $it") }
                    day.sunset?.takeIf(String::isNotBlank)?.let { add("日落 $it") }
                    day.aqi?.takeIf { it in 0..1000 }?.let { add("AQI $it") }
                }
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        facts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhishengTextTertiary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 进入该日完整详情（迁移 yunsong ForecastDetailActivity 的逐日详情）。
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "查看该日完整详情") {
                            onOpenDay(day.dateMillis)
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "查看该日完整详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun degree(value: Double, tempUnit: String): String =
    "${Fmt.temp(value, tempUnit) ?: value.roundToInt().toString()}°"

private fun todayIndex(days: List<DailyWeather>, utcOffsetSeconds: Int?): Int =
    days.indexOfFirst { Fmt.dailyDayLabel(it.dateMillis, utcOffsetSeconds = utcOffsetSeconds) == "今天" }
        .takeIf { it >= 0 } ?: 0

/** 多日趋势摘要：温度区间 + 降水天数 + 昼夜温差提醒。 */
internal fun buildDailyTrendSummary(days: List<DailyWeather>, tempUnit: String): String {
    val temps = days.flatMap { listOfNotNull(it.high, it.low) }.filter(Double::isFinite)
    if (temps.isEmpty()) return "多日温度暂未提供，请稍后刷新。"
    val min = temps.min()
    val max = temps.max()
    val range = if (max - min < 0.5) degree(min, tempUnit) else "${degree(min, tempUnit)}–${degree(max, tempUnit)}"
    val rainDays = days.count { it.condition?.isPrecipitation == true || (it.precipProbability ?: 0) >= 50 }
    val rainText = when {
        rainDays == 0 -> "无明显降水"
        rainDays <= 2 -> "有短时降水"
        else -> "降水天数较多"
    }
    val span = max - min
    val spanAdvice = when {
        span >= 10 -> "昼夜温差大，注意增减衣物"
        span >= 6 -> "气温起伏较明显"
        else -> "气温相对平稳"
    }
    return "未来${days.size}日温度 $range，$rainText，$spanAdvice。"
}

private val TrendDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)

private fun trendDateText(dateMillis: Long, utcOffsetSeconds: Int?): String =
    Instant.ofEpochMilli(dateMillis).atZone(cityZone(utcOffsetSeconds)).format(TrendDateFormatter)

private val TrendColumnPerScreen = 5
private val TrendEdgeInset = 32.dp
private val TrendChartHeight = 230.dp
private val TrendIconSize = 26.dp

/**
 * 多日温度折线图：整图按每屏 5 天排布，超过 5 天时横向滑动；曲线与标注严格按
 * yunsong DailyTrendView 移植。点选某一列由 [onSelect] 上报，宿主负责震动。
 */
@Composable
internal fun DailyTrendChart(
    days: List<DailyWeather>,
    tempUnit: String,
    utcOffsetSeconds: Int?,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = days.size
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 主题化配色：深色主题近白、浅色主题深灰蓝，与 48 小时图同一套。
    val lineColor = if (com.HelloMeet.weather.ui.theme.LocalZhishengPalette.current.isLight) {
        Color(0xFF293340)
    } else {
        Color(0xFFF4F7FB)
    }
    val probColor = if (com.HelloMeet.weather.ui.theme.LocalZhishengPalette.current.isLight) {
        Color(0xFF1C81D3)
    } else {
        Color(0xFF71CBFF)
    }

    val iconSizePx = with(density) { TrendIconSize.toPx() }.roundToInt().coerceAtLeast(1)
    val iconBitmaps = remember(days, iconSizePx) {
        days.map { day ->
            conditionIconRes(day.condition)?.let { res ->
                runCatching {
                    val drawable = ContextCompat.getDrawable(context, res) ?: return@runCatching null
                    val bitmap = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, iconSizePx, iconSizePx)
                    drawable.draw(canvas)
                    bitmap.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    val description = "多日最高最低温折线，点按选择某一天"
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val colWDp = maxWidth / TrendColumnPerScreen
        val totalWidthDp = colWDp * n.coerceAtLeast(1)
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .semantics { contentDescription = description },
        ) {
            Canvas(
                Modifier
                    .width(totalWidthDp)
                    .height(TrendChartHeight)
                    .pointerInput(n) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var moved = false
                            val startX = down.position.x
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (abs(change.position.x - startX) > 8.dp.toPx()) moved = true
                            }
                            if (!moved) {
                                columnAt(down.position.x, size.width.toFloat(), TrendEdgeInset.toPx(), n)
                                    ?.let(onSelect)
                            }
                        }
                    },
            ) {
                if (n < 2) return@Canvas
                val left = TrendEdgeInset.toPx()
                val right = size.width - left
                val chartW = right - left
                if (chartW <= 0) return@Canvas
                val step = chartW / (n - 1).toFloat()
                fun xAt(i: Int): Float = left + step * i

                val highs = days.map { it.high?.takeIf(Double::isFinite)?.roundToInt() }
                val lows = days.map { it.low?.takeIf(Double::isFinite)?.roundToInt() }
                val highValues = highs.filterNotNull()
                val lowValues = lows.filterNotNull()

                // 顶部：星期 / 日期 / 天气文字 / 图标 / 降水概率
                val dayY = 16.dp.toPx()
                val dateY = 34.dp.toPx()
                val descY = 54.dp.toPx()
                val iconCY = 82.dp.toPx()
                val probY = 102.dp.toPx()
                val pastCount = todayIndex(days, utcOffsetSeconds).coerceIn(0, n)

                val dayLabelStyle = TextStyle(fontSize = 14.sp, color = lineColor, fontWeight = FontWeight.Bold)
                val dateLabelStyle = TextStyle(fontSize = 12.sp, color = lineColor.copy(alpha = .78f))
                val descLabelStyle = TextStyle(fontSize = 12.sp, color = lineColor.copy(alpha = .9f))
                val probLabelStyle = TextStyle(fontSize = 11.sp, color = probColor, fontWeight = FontWeight.Bold)

                for (i in 0 until n) {
                    val x = xAt(i)
                    val past = i < pastCount
                    drawBaselineText(
                        measurer, Fmt.dailyDayLabel(days[i].dateMillis, utcOffsetSeconds = utcOffsetSeconds), x, dayY,
                        dayLabelStyle.color.copy(alpha = if (past) .43f else 1f), 14.sp, bold = true,
                    )
                    drawBaselineText(
                        measurer, trendDateText(days[i].dateMillis, utcOffsetSeconds), x, dateY,
                        dateLabelStyle.color.copy(alpha = if (past) .39f else .78f), 12.sp, bold = false,
                    )
                    val desc = days[i].condition?.label.orEmpty()
                    if (desc.isNotBlank()) {
                        drawBaselineText(
                            measurer, desc, x, descY,
                            descLabelStyle.color.copy(alpha = if (past) .43f else .9f), 12.sp, bold = false,
                        )
                    }
                    iconBitmaps.getOrNull(i)?.let { bmp ->
                        drawImage(
                            bmp,
                            topLeft = Offset(x - iconSizePx / 2f, iconCY - iconSizePx / 2f),
                            alpha = if (past) .43f else 1f,
                        )
                    }
                    val prob = days[i].precipProbability ?: 0
                    if (prob >= 1 && days[i].condition?.isPrecipitation == true) {
                        drawBaselineText(
                            measurer, "$prob%", x, probY,
                            probLabelStyle.color.copy(alpha = if (past) .39f else .82f), 11.sp, bold = true,
                        )
                    }
                }

                if (highValues.isEmpty() || lowValues.isEmpty()) return@Canvas

                // 两条温度线：最高温在上半区、最低温在下半区
                val chartTop = 118.dp.toPx()
                val chartMid = 168.dp.toPx()
                val chartBottom = 212.dp.toPx()
                val hMin = highValues.min()
                val hMax = highValues.max()
                val lMin = lowValues.min()
                val lMax = lowValues.max()
                val hRange = if (hMax - hMin < 1) 1 else hMax - hMin
                val lRange = if (lMax - lMin < 1) 1 else lMax - lMin
                fun highY(v: Int): Float =
                    chartTop + 8.dp.toPx() + (hMax - v).toFloat() / hRange * (chartMid - chartTop - 16.dp.toPx())
                fun lowY(v: Int): Float =
                    chartMid + 8.dp.toPx() + (lMax - v).toFloat() / lRange * (chartBottom - chartMid - 16.dp.toPx())

                val px = FloatArray(n) { xAt(it) }
                val highPy = FloatArray(n) { Float.NaN }
                val lowPy = FloatArray(n) { Float.NaN }
                for (i in 0 until n) {
                    highs.getOrNull(i)?.let { highPy[i] = highY(it) }
                    lows.getOrNull(i)?.let { lowPy[i] = lowY(it) }
                }

                fun drawTemperatureSeries(
                    py: FloatArray,
                    yForLabel: (Int) -> Float,
                    labelAbove: Boolean,
                    values: List<Int?>,
                ) {
                    val m = computeTrendTangents(py, step)
                    val pastPath = Path()
                    val futurePath = Path()
                    appendTrendBezier(pastPath, px, py, m, 0, pastCount)
                    appendTrendBezier(futurePath, px, py, m, pastCount, n - 1)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    if (pastCount > 0) {
                        drawPath(pastPath, lineColor.copy(alpha = .47f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash))
                    }
                    drawPath(futurePath, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val sel = selected.coerceIn(0, n - 1)
                    for (i in 0 until n) {
                        val value = values.getOrNull(i) ?: continue
                        val x = px[i]
                        val y = py[i]
                        val label = "$value°"
                        if (i == sel) {
                            drawCircle(lineColor.copy(alpha = .25f), 8.dp.toPx(), Offset(x, y))
                            drawCircle(lineColor, 4.5.dp.toPx(), Offset(x, y))
                        } else {
                            drawCircle(lineColor.copy(alpha = if (i < pastCount) .47f else 1f), 3.dp.toPx(), Offset(x, y))
                        }
                        val labelBaseline = if (labelAbove) y - 7.dp.toPx() else y + 14.dp.toPx()
                        drawBaselineText(
                            measurer, label, x, labelBaseline,
                            lineColor.copy(alpha = if (i < pastCount) .47f else 1f), if (labelAbove) 13.sp else 12.sp, bold = true,
                        )
                    }
                }
                drawTemperatureSeries(highPy, ::highY, labelAbove = true, values = highs)
                drawTemperatureSeries(lowPy, ::lowY, labelAbove = false, values = lows)
            }
        }
    }
}

internal fun columnAt(x: Float, width: Float, inset: Float, count: Int): Int? {
    if (count <= 0 || width <= 0 || x < 0 || x > width) return null
    if (count == 1) return 0
    val span = (width - 2 * inset).coerceAtLeast(1f)
    return ((x - inset) / span * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/** Fritsch-Carlson 单调保形切线：与 yunsong DailyTrendView.computeTangents 一致。 */
private fun computeTrendTangents(py: FloatArray, step: Float): FloatArray {
    val n = py.size
    val dy = FloatArray(n)
    for (i in 0 until n - 1) {
        if (!py[i].isNaN() && !py[i + 1].isNaN()) dy[i] = (py[i + 1] - py[i]) / step
    }
    val m = FloatArray(n)
    for (i in 0 until n) {
        if (py[i].isNaN()) continue
        val dL = if (i > 0) dy[i - 1] else Float.NaN
        val dR = if (i < n - 1) dy[i] else Float.NaN
        m[i] = when {
            dL.isNaN() && dR.isNaN() -> 0f
            dL.isNaN() -> dR
            dR.isNaN() -> dL
            dL * dR > 0f -> 2f * dL * dR / (dL + dR)
            else -> 0f
        }
    }
    return m
}

/** 把 [from, to) 段内相邻数据点用三次贝塞尔连接（跳过缺数据段）。 */
private fun appendTrendBezier(
    path: Path,
    px: FloatArray,
    py: FloatArray,
    m: FloatArray,
    from: Int,
    to: Int,
) {
    var started = false
    for (i in from until to) {
        if (py[i].isNaN() || py[i + 1].isNaN()) {
            started = false
            continue
        }
        val x0 = px[i]
        val y0 = py[i]
        val x1 = px[i + 1]
        val y1 = py[i + 1]
        val dx = x1 - x0
        val c1y = y0 + m[i] * dx / 3f
        val c2y = y1 - m[i + 1] * dx / 3f
        if (!started) {
            path.moveTo(x0, y0)
            started = true
        }
        path.cubicTo(x0 + dx / 3f, c1y, x1 - dx / 3f, c2y, x1, y1)
    }
}

/** 以基线定位绘制单行文字。 */
private fun DrawScope.drawBaselineText(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    baseline: Float,
    color: Color,
    fontSize: TextUnit,
    bold: Boolean,
) {
    val layout = measurer.measure(
        text,
        TextStyle(fontSize = fontSize, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
    )
    drawText(layout, topLeft = Offset(x - layout.size.width / 2f, baseline - layout.getLineBaseline(0)))
}
