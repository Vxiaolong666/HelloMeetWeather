// 关于页的品牌图标：太阳 + 云 + 小雨，纯 Canvas 自绘，跟着 App 的天气主题重设。
// 动效沿用原来的三套弹簧参数：poke（点一下云被按扁、太阳往左上让开、雨滴被挤散）、
// swell（长按整枚图标鼓起、光芒变长）、tremble（蓄力时轻颤）。

package com.HelloMeet.weather.ui.about

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

internal object WeatherMarkColors {
    val SunCore = Color(0xFFFFD66B)
    val SunEdge = Color(0xFFFF8A3D)
    val Ray = Color(0xFFFFB23E)
    val Cloud = Color(0xFFFFFFFF)
    val CloudShade = Color(0xFFD4E5F4)
    val Rain = Color(0xFF3E9BE8)
    val Ink = Color(0xFFE8763A)

    /** 长按彩蛋里抽出三条飘带的颜色 */
    val Ribbons = listOf(SunEdge, Rain, SunCore)
}

/**
 * 天气图标。全部在 100×100 的逻辑坐标里画，按短边等比缩放。
 * @param poke 「戳一下」：正值云被压扁、太阳让开、光芒收缩；负值（弹簧回弹）时反向拉长
 * @param swell 长按蓄力：整体鼓起、太阳变大、光芒变长
 * @param tremble 蓄力时的轻颤角度（度）
 * @param tint 给了就画成单色（小尺寸图标用），不画渐变和高光
 */
@Composable
internal fun WeatherMark(
    modifier: Modifier = Modifier,
    poke: () -> Float = { 0f },
    swell: () -> Float = { 0f },
    tremble: () -> Float = { 0f },
    tint: Color? = null,
) {
    Canvas(modifier) { drawWeatherMark(poke(), swell(), tremble(), tint) }
}

internal fun DrawScope.drawWeatherMark(
    poke: Float = 0f,
    swell: Float = 0f,
    tremble: Float = 0f,
    tint: Color? = null,
) {
    val k = size.minDimension / 100f
    translate((size.width - 100f * k) / 2f, (size.height - 100f * k) / 2f) {
        scale(k, k, Offset.Zero) {
            val s = 1f + 0.1f * swell
            scale(s, s, Offset(50f, 50f)) {
                rotate(tremble, Offset(50f, 50f)) {
                    // 太阳：被戳时往上让开
                    translate(0f, -5f * poke) { drawSun(poke, swell, tint) }
                    // 云：被戳时横向鼓、纵向扁，和原来「中间那颗被压扁」同一套手感
                    scale(1f + 0.18f * poke, 1f - 0.26f * poke, Offset(54f, 68f)) { drawCloud(tint) }
                    // 雨滴：被挤开往下、左右散开
                    translate(0f, 5f * poke) { drawRain(poke, tint) }
                }
            }
        }
    }
}

private fun DrawScope.drawSun(poke: Float, swell: Float, tint: Color?) {
    val c = Offset(37f, 36f)
    val r = 16f * (1f + 0.08f * swell - 0.05f * poke)
    val ray = (5f + 3f * swell - 2f * poke).coerceAtLeast(1f)
    val inner = r + 3.5f
    for (i in 0 until 8) {
        val a = Math.toRadians((i * 45f).toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            tint?.copy(alpha = 0.8f) ?: WeatherMarkColors.Ray,
            Offset(c.x + dx * inner, c.y + dy * inner),
            Offset(c.x + dx * (inner + ray), c.y + dy * (inner + ray)),
            strokeWidth = 3.2f,
            cap = StrokeCap.Round,
        )
    }
    if (tint == null) {
        drawCircle(
            Brush.linearGradient(
                listOf(WeatherMarkColors.SunCore, WeatherMarkColors.SunEdge),
                start = Offset(c.x - r, c.y - r),
                end = Offset(c.x + r, c.y + r),
            ),
            r,
            c,
        )
        drawCircle(Color.White.copy(alpha = 0.34f), r * 0.4f, Offset(c.x - r * 0.3f, c.y - r * 0.32f))
    } else {
        drawCircle(tint, r, c)
    }
}

private fun DrawScope.drawCloud(tint: Color?) {
    // 先画一层下移的浅蓝阴影，云才有厚度；单色模式用半透明同色
    drawCloudShape(tint?.copy(alpha = 0.45f) ?: WeatherMarkColors.CloudShade, Offset(0f, 2.5f))
    drawCloudShape(tint ?: WeatherMarkColors.Cloud, Offset.Zero)
}

private fun DrawScope.drawCloudShape(color: Color, d: Offset) {
    drawRoundRect(
        color,
        topLeft = Offset(24f, 61f) + d,
        size = Size(60f, 17f),
        cornerRadius = CornerRadius(8.5f, 8.5f),
    )
    drawCircle(color, 13f, Offset(36f, 62f) + d)
    drawCircle(color, 17.5f, Offset(55f, 55f) + d)
    drawCircle(color, 11f, Offset(72f, 64f) + d)
}

private fun DrawScope.drawRain(poke: Float, tint: Color?) {
    val color = tint?.copy(alpha = 0.7f) ?: WeatherMarkColors.Rain
    listOf(38f, 54f, 70f).forEachIndexed { i, x ->
        val spread = (i - 1) * 3f * poke
        drawLine(
            color,
            Offset(x + spread, 80f),
            Offset(x + spread - 3f, 88f),
            strokeWidth = 3.4f,
            cap = StrokeCap.Round,
        )
    }
}
