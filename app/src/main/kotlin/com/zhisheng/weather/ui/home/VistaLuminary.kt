package com.zhisheng.weather.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.MoonCalc
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.clockMinutes
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.weatherPresentationTime
import kotlin.math.PI
import kotlin.math.sqrt

internal const val VISTA_DAY_MINUTES = 24 * 60

internal data class VistaAstroWindow(
    val rise: Int,
    val set: Int,
    val windowStart: Int,
    val windowLength: Int,
)

internal fun vistaNowMinutes(nowMillis: Long, utcOffsetSeconds: Int?): Int {
    val local = java.time.Instant.ofEpochMilli(nowMillis).atZone(Fmt.zoneId(utcOffsetSeconds))
    return local.hour * 60 + local.minute
}

internal fun vistaLuminaryProgress(sunrise: String?, sunset: String?, nowMinutes: Int): Float {
    val rise = clockMinutes(sunrise) ?: return 0.5f
    val set = clockMinutes(sunset) ?: return 0.5f
    if (set <= rise) return 0.5f
    return ((nowMinutes - rise).toFloat() / (set - rise).toFloat()).coerceIn(0f, 1f)
}

internal fun vistaDaylightActive(sunrise: String?, sunset: String?, nowMinutes: Int): Boolean {
    val rise = clockMinutes(sunrise) ?: return false
    val set = clockMinutes(sunset) ?: return false
    if (set <= rise) return false
    return nowMinutes in rise..set
}

internal fun vistaAstroUpDuration(rise: Int, set: Int): Int? {
    val span = Math.floorMod(set - rise, VISTA_DAY_MINUTES)
    return span.takeIf { it > 0 }
}

internal fun vistaAstroVisible(now: Int, rise: Int, set: Int): Boolean {
    val up = vistaAstroUpDuration(rise, set) ?: return false
    return Math.floorMod(now - rise, VISTA_DAY_MINUTES) < up
}

/** +1 中天，0 升/落，−1 反中天。月出跨日也能算。 */
internal fun vistaAstroAltitude(now: Int, rise: Int, set: Int): Float {
    val up = vistaAstroUpDuration(rise, set)?.toFloat() ?: return 0f
    val down = (VISTA_DAY_MINUTES - up).coerceAtLeast(1f)
    val elapsed = Math.floorMod(now - rise, VISTA_DAY_MINUTES).toFloat()
    val slope = minOf(PI.toFloat() / up, PI.toFloat() / down)
    val above = elapsed <= up
    val duration = if (above) up else down
    val phase = if (above) elapsed else elapsed - up
    val t = (if (phase <= duration / 2f) phase else duration - phase) / (duration / 2f)
    val altitude = (-2f * t * t * t + 3f * t * t) +
        (t * t * t - 2f * t * t + t) * slope * duration / 2f
    return if (above) altitude else -altitude
}

internal fun vistaAstroWindow(rise: Int, set: Int, padMinutes: Int = 180): VistaAstroWindow? {
    val up = vistaAstroUpDuration(rise, set) ?: return null
    val down = VISTA_DAY_MINUTES - up
    val pad = minOf(padMinutes, down / 2).coerceAtLeast(0)
    return VistaAstroWindow(rise, set, rise - pad, up + 2 * pad)
}

internal fun vistaAstroProgressInWindow(now: Int, window: VistaAstroWindow): Float {
    val start = Math.floorMod(window.windowStart, VISTA_DAY_MINUTES)
    val elapsed = Math.floorMod(now - start, VISTA_DAY_MINUTES)
    return (elapsed.toFloat() / window.windowLength.toFloat()).coerceIn(0f, 1f)
}

/** 当天 0 点到 24 点，横轴按钟点对齐。 */
internal fun vistaAstroClockFraction(clockMinutes: Int): Float =
    Math.floorMod(clockMinutes, VISTA_DAY_MINUTES).toFloat() / VISTA_DAY_MINUTES.toFloat()

/** 时长文案：10小时29分 / 11小时 / 29分。 */
internal fun vistaAstroDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分"
    }
}

/** 下一次日出/日落倒计时；日落后算到次日日出。 */
internal fun vistaAstroCountdown(nowMinutes: Int, sunrise: String?, sunset: String?): String? {
    val rise = clockMinutes(sunrise) ?: return null
    val set = clockMinutes(sunset) ?: return null
    val (label, delta) = when {
        nowMinutes < rise -> "日出" to (rise - nowMinutes)
        nowMinutes < set -> "日落" to (set - nowMinutes)
        else -> "日出" to (VISTA_DAY_MINUTES - nowMinutes + rise)
    }
    return "${label}还有 ${vistaAstroDuration(delta)}"
}

@Composable
internal fun VistaLuminary(today: DailyWeather, utcOffsetSeconds: Int?) {
    val nowMinutes = vistaNowMinutes(weatherPresentationTime(), utcOffsetSeconds)
    val phase = Fmt.moonPhaseZh(today.moonPhase)
    val illumination = MoonCalc.illuminationFraction(weatherPresentationTime()).coerceIn(0.0, 1.0)
    val orange = ZhishengOrange
    val cyan = ZhishengCyan
    val palette = LocalZhishengPalette.current
    val rail = palette.cardBorder.copy(alpha = 0.55f)
    // 弧线轨迹（参考设计，默认）与原来的时钟波形，右上角切换。
    var arcMode by rememberSaveable { mutableStateOf(true) }
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = buildString {
                append("太阳和月亮，")
                append("日出 ${today.sunrise ?: "暂缺"}，日落 ${today.sunset ?: "暂缺"}，")
                append("月升 ${today.moonrise ?: "暂缺"}，月落 ${today.moonset ?: "暂缺"}")
                phase?.let { append("，$it") }
                append(if (arcMode) "，弧线轨迹视图" else "，波形视图")
            }
        },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("太阳和月亮", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                color = ZhishengText, fontWeight = FontWeight.Medium)
            if (phase != null) {
                VistaMoonDisc(illumination, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(phase, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
            }
            Spacer(Modifier.width(10.dp))
            LuminaryModeSwitch(arcMode) { arcMode = it }
        }
        vistaAstroCountdown(nowMinutes, today.sunrise, today.sunset)?.let { countdown ->
            Text(
                countdown,
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.titleSmall,
                color = ZhishengText,
                fontWeight = FontWeight.Medium,
            )
        }
        Crossfade(
            targetState = arcMode,
            modifier = Modifier.animateContentSize(tween(260)),
            animationSpec = tween(260),
            label = "luminary-view",
        ) { arc ->
            if (arc) {
                VistaLuminaryArc(today, illumination, rail, orange, cyan)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VistaAstroWave(
                        riseText = today.sunrise,
                        setText = today.sunset,
                        riseLabel = "日出",
                        setLabel = "日落",
                        nowMinutes = nowMinutes,
                        color = orange,
                        rail = rail,
                    )
                    VistaAstroWave(
                        riseText = today.moonrise,
                        setText = today.moonset,
                        riseLabel = "月升",
                        setLabel = "月落",
                        nowMinutes = nowMinutes,
                        color = cyan,
                        rail = rail,
                    )
                }
            }
        }
    }
}

/** 右上角两态切换：波形 ∿ / 弧线 ⌒；选中项用主色实心强调。 */
@Composable
private fun LuminaryModeSwitch(arc: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalZhishengPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .width(58.dp)
            .height(28.dp)
            .clip(shape)
            .background(palette.surface.copy(alpha = 0.72f))
            .border(1.dp, palette.cardBorder.copy(alpha = 0.45f), shape)
            .selectableGroup()
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(false, true).forEach { isArc ->
            val selected = arc == isArc
            val fill by animateColorAsState(
                targetValue = if (selected) palette.mint else Color.Transparent,
                animationSpec = tween(220),
                label = "luminary-switch-fill",
            )
            val iconTint by animateColorAsState(
                targetValue = if (selected) palette.surface else palette.textTertiary,
                animationSpec = tween(220),
                label = "luminary-switch-tint",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(fill)
                    .selectable(selected = selected, role = Role.Tab) { onChange(isArc) }
                    .semantics {
                        contentDescription = if (isArc) "弧线轨迹视图" else "波形视图"
                    },
                contentAlignment = Alignment.Center,
            ) {
                LuminaryModeIcon(isArc, iconTint, Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun LuminaryModeIcon(arc: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (arc) {
                moveTo(w * 0.12f, h * 0.72f)
                cubicTo(w * 0.22f, h * 0.16f, w * 0.78f, h * 0.16f, w * 0.88f, h * 0.72f)
            } else {
                moveTo(w * 0.08f, h * 0.50f)
                cubicTo(w * 0.24f, h * 0.14f, w * 0.34f, h * 0.86f, w * 0.50f, h * 0.50f)
                cubicTo(w * 0.66f, h * 0.14f, w * 0.76f, h * 0.86f, w * 0.92f, h * 0.50f)
            }
        }
        drawPath(path, tint, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * 弧线轨迹视图：外弧为太阳（日出→日落），内弧为月亮（月升→月落），
 * 基线四个点对应底部四个时刻；照亮与白昼时长放在最后一行。
 */
@Composable
private fun VistaLuminaryArc(
    today: DailyWeather,
    illumination: Double,
    rail: Color,
    orange: Color,
    cyan: Color,
) {
    val marks = listOf(
        Triple("日出", today.sunrise, orange),
        Triple("月升", today.moonrise, cyan),
        Triple("月落", today.moonset, cyan),
        Triple("日落", today.sunset, orange),
    )
    val hasMoon = today.moonrise != null && today.moonset != null
    val rise = clockMinutes(today.sunrise)
    val set = clockMinutes(today.sunset)
    val daylight = if (rise != null && set != null && set > rise) vistaAstroDuration(set - rise) else "—"
    // 必须包成单个根布局：外层 Crossfade/AnimatedContent 会把多个子节点叠放，
    // 否则弧线、四个时刻与照亮/白昼文字会互相压在同一行。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val left = 6.dp.toPx()
            val right = size.width - 6.dp.toPx()
            val width = (right - left).coerceAtLeast(1f)
            val base = size.height - 14.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            val arcStroke = Stroke(1.5.dp.toPx(), pathEffect = dash)
            fun drawArc(startFraction: Float, endFraction: Float, height: Float) {
                val x1 = left + width * startFraction
                val x2 = left + width * endFraction
                val mid = (x1 + x2) / 2f
                val half = (x2 - x1) / 2f
                val path = Path().apply {
                    moveTo(x1, base)
                    cubicTo(x1 + half * 0.25f, base - height * 0.86f,
                        mid - half * 0.55f, base - height, mid, base - height)
                    cubicTo(mid + half * 0.55f, base - height,
                        x2 - half * 0.25f, base - height * 0.86f, x2, base)
                }
                drawPath(path, rail.copy(alpha = 0.9f), style = arcStroke)
            }
            drawLine(rail.copy(alpha = 0.55f), Offset(left, base), Offset(right, base), 1.dp.toPx())
            val outerHeight = (base - 12.dp.toPx()).coerceAtLeast(24f)
            drawArc(0.06f, 0.94f, outerHeight)
            if (hasMoon) drawArc(0.22f, 0.78f, outerHeight * 0.62f)
            listOf(0.06f, 0.22f, 0.78f, 0.94f).forEach { fraction ->
                drawCircle(rail.copy(alpha = 0.95f), 3.dp.toPx(), Offset(left + width * fraction, base))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            marks.forEach { (label, time, color) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        time ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("照亮 ${(illumination * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = ZhishengTextSecondary)
            Text("白昼 $daylight", style = MaterialTheme.typography.titleSmall, color = ZhishengTextSecondary)
        }
    }
}

@Composable
private fun VistaAstroWave(
    riseText: String?,
    setText: String?,
    riseLabel: String,
    setLabel: String,
    nowMinutes: Int,
    color: Color,
    rail: Color,
) {
    val rise = clockMinutes(riseText)
    val set = clockMinutes(setText)
    if (rise == null || set == null || vistaAstroUpDuration(rise, set) == null) {
        if (riseText != null || setText != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            VistaAstroCaption(riseText ?: "时间暂缺", riseLabel, Modifier)
            VistaAstroCaption(setText ?: "时间暂缺", setLabel, Modifier)
        }
        return
    }
    val riseFrac = vistaAstroClockFraction(rise)
    val setFrac = vistaAstroClockFraction(set)
    val nowFrac = vistaAstroClockFraction(nowMinutes)
    val fillTop = color.copy(alpha = 0.18f)
    val fillBottom = color.copy(alpha = 0.02f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
            val chartW = maxWidth
            val labelWidth = 88.dp * LocalDensity.current.fontScale
            val captionMax = (chartW - labelWidth).coerceAtLeast(0.dp)
            // Captions use the same inset and clock axis as the curve. At large
            // font sizes or close rise/set times, use a flowing row instead.
            val riseX = (8.dp + (chartW - 16.dp) * riseFrac - labelWidth / 2).coerceIn(0.dp, captionMax)
            val setX = (8.dp + (chartW - 16.dp) * setFrac - labelWidth / 2).coerceIn(0.dp, captionMax)
            val flowingCaptions = LocalDensity.current.fontScale > 1.2f ||
                kotlin.math.abs(riseX.value - setX.value) < labelWidth.value
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp).clipToBounds(),
            ) {
                val left = 8.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val width = (right - left).coerceAtLeast(1f)
                val horizon = size.height * 0.53f
                val amp = size.height * 0.34f
                clipRect(left = left, right = right) {
                val up = vistaAstroUpDuration(rise, set)!!.toFloat()
                val down = VISTA_DAY_MINUTES - up
                val slope = minOf(PI.toFloat() / up, PI.toFloat() / down)
                fun x(minute: Float) = left + width * minute / VISTA_DAY_MINUTES
                // Two exact cubic segments per arc, with a shared tangent at
                // rise/set and a horizontal tangent at each crest/trough.
                fun arc(start: Float, duration: Float, sign: Float): Path = Path().apply {
                    val half = duration / 2f
                    val handle = sign * slope * half / 3f
                    moveTo(x(start), horizon)
                    cubicTo(x(start + half / 3f), horizon - amp * handle,
                        x(start + half * 2f / 3f), horizon - amp * sign,
                        x(start + half), horizon - amp * sign)
                    cubicTo(x(start + half + half / 3f), horizon - amp * sign,
                        x(start + duration - half / 3f), horizon - amp * handle,
                        x(start + duration), horizon)
                }
                val above = Path()
                val below = Path()
                val fill = Path()
                for (cycle in -1..1) {
                    val start = rise + cycle * VISTA_DAY_MINUTES.toFloat()
                    val upper = arc(start, up, 1f)
                    above.addPath(upper)
                    fill.addPath(Path().apply { addPath(upper); close() })
                    below.addPath(arc(start + up, down, -1f))
                }
                val barH = 1.dp.toPx()
                drawRoundRect(
                    rail.copy(alpha = 0.55f),
                    Offset(left, horizon - barH / 2f),
                    Size(width, barH),
                    CornerRadius(barH / 2f),
                )
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(fillTop, fillBottom), startY = horizon - amp, endY = horizon),
                )
                listOf(riseFrac, setFrac).forEach { fraction ->
                    val x = left + width * fraction
                    drawLine(rail, Offset(x, horizon), Offset(x, horizon + 6.dp.toPx()), 1.dp.toPx())
                }
                drawPath(above, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawPath(
                        below,
                        color.copy(alpha = 0.45f),
                        style = Stroke(
                            2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())),
                        ),
                    )
                }
                val nowAlt = vistaAstroAltitude(nowMinutes, rise, set)
                val dot = Offset(left + width * nowFrac, horizon - amp * nowAlt)
                drawCircle(color.copy(alpha = 0.12f), 7.dp.toPx(), dot)
                drawCircle(color, 3.5.dp.toPx(), dot)
            }
            if (flowingCaptions) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VistaAstroCaption(riseText!!, riseLabel, Modifier.weight(1f))
                VistaAstroCaption(setText!!, setLabel, Modifier.weight(1f))
            } else Box(Modifier.fillMaxWidth()) {
                VistaAstroCaption(riseText!!, riseLabel, Modifier.offset(x = riseX).widthIn(min = labelWidth))
                VistaAstroCaption(setText!!, setLabel, Modifier.offset(x = setX).widthIn(min = labelWidth))
            }
        }
    }
}

@Composable
private fun VistaAstroCaption(time: String, label: String, modifier: Modifier) {
    if (LocalDensity.current.fontScale <= 1.2f) {
        Row(modifier.widthIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, maxLines = 1)
            Text(time, style = MaterialTheme.typography.labelLarge, color = ZhishengText,
                fontWeight = FontWeight.Medium, maxLines = 1)
        }
        return
    }
    Column(modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            time,
            style = MaterialTheme.typography.labelLarge,
            color = ZhishengText,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
internal fun VistaMoonDisc(fraction: Double, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    val lit = if (palette.isLight) palette.textSecondary else palette.text
    val dark = if (palette.isLight) palette.cardBorder else palette.surface
    Canvas(modifier.semantics { contentDescription = "月面照明约 ${(fraction * 100).toInt()}%" }) {
        val radius = size.minDimension * 0.46f
        drawCircle(dark, radius, center)
        val illumination = fraction.coerceIn(0.0, 1.0).toFloat()
        val step = 1.dp.toPx()
        var yy = -radius
        while (yy <= radius) {
            val limb = sqrt((radius * radius - yy * yy).coerceAtLeast(0f))
            drawLine(
                lit,
                Offset(center.x + (1 - 2 * illumination) * limb, center.y + yy),
                Offset(center.x + limb, center.y + yy),
                step,
            )
            yy += step
        }
        drawCircle(palette.textTertiary.copy(alpha = 0.7f), radius, center, style = Stroke(1.dp.toPx()))
    }
}
