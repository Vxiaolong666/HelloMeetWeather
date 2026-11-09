package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherCondition

/**
 * 穿搭建议：完全由本地规则从体感温度 + 天气现象 + 风 + 湿度推导。
 * 不依赖任何供应商的"穿衣指数"字段；Open-Meteo 预报接口的
 * apparent_temperature / weather_code / wind_speed_10m / relative_humidity_2m 足以支撑。
 */
data class OutfitRecommendation(
    val headline: String,
    val advice: String,
    val feelLabel: String,
    val top: String,
    val outer: String,
    val bottom: String,
    val shoes: String,
    val tag: String,
    val palette: String,
)

/** 体感温度分档（°C）。22–25 档与参考卡（23°C / 长袖衬衫 / 不必加外套 / 直筒长裤 / 舒适包脚鞋）对齐。 */
internal fun outfitRecommendation(cur: CurrentWeather, unit: String): OutfitRecommendation? {
    val feel = cur.feelsLike ?: cur.temperature ?: return null
    if (!feel.isFinite()) return null
    // 基准档位（体感 °C）
    val band = when {
        feel >= 35 -> Band("酷热速干短袖", "速干短袖", "防晒衣备着", "轻薄短裤", "透气凉鞋", "清凉穿搭", "薄荷配色")
        feel >= 30 -> Band("短袖清凉刚好", "短袖T恤", "薄开衫备着", "薄长裤", "轻便运动鞋", "清凉穿搭", "薄荷配色")
        feel >= 26 -> Band("薄长袖正好", "长袖衬衫", "不必加外套", "薄长裤", "舒适包脚鞋", "舒适穿搭", "青岚配色")
        feel >= 22 -> Band("轻薄长袖就好", "长袖衬衫", "不必加外套", "直筒长裤", "舒适包脚鞋", "舒适穿搭", "青岚配色")
        feel >= 18 -> Band("薄外套正合适", "长袖T恤", "薄外套", "休闲长裤", "舒适休闲鞋", "舒适穿搭", "青岚配色")
        feel >= 13 -> Band("风衣夹克安排", "针织衫或卫衣", "风衣或夹克", "厚长裤", "运动鞋", "保暖穿搭", "暖阳配色")
        feel >= 7 -> Band("厚外套防寒", "厚毛衣", "厚外套或薄羽绒", "加绒长裤", "保暖休闲鞋", "保暖穿搭", "暖阳配色")
        feel >= 0 -> Band("羽绒服护体", "厚打底+毛衣", "羽绒服", "加绒厚裤", "防滑保暖靴", "防寒穿搭", "雾蓝配色")
        else -> Band("厚羽绒全副武装", "厚羊毛衫+加绒打底", "长款羽绒服", "加绒棉裤", "防滑雪地靴", "防寒穿搭", "雾蓝配色")
    }
    val rain = cur.condition?.isPrecipitation == true
    val snow = cur.condition == WeatherCondition.SNOW || cur.condition == WeatherCondition.SLEET
    val wind = (cur.windSpeed ?: 0.0).takeIf { it.isFinite() } ?: 0.0
    val humidity = (cur.humidity ?: 0.0).takeIf { it.isFinite() } ?: 0.0
    // 天气修正：只调整局部，不改变分档主线
    var outer = band.outer
    var shoes = band.shoes
    val adviceExtra = buildList {
        if (rain || snow) {
            if (snow) add("有降雪，防滑保暖")
            else add("记得带伞")
            if (outer == "不必加外套") outer = "防水外套备着"
            if (shoes == "舒适包脚鞋" || shoes == "运动鞋" || shoes == "轻便运动鞋") shoes = "防滑防水鞋"
        }
        if (wind >= 30) {
            add("风大，选防风外套")
            if (outer == "不必加外套") outer = "薄风衣备着"
        }
        if (humidity >= 85 && feel >= 26) add("闷热，透气面料优先")
    }
    val advice = if (adviceExtra.isEmpty()) "按这套出门，再随冷暖微调" else adviceExtra.joinToString("，") + "。"
    return OutfitRecommendation(
        headline = band.headline,
        advice = advice,
        feelLabel = com.zhisheng.weather.ui.Fmt.temp(feel, unit)?.let { "$it°" } ?: "—",
        top = band.top,
        outer = outer,
        bottom = band.bottom,
        shoes = shoes,
        tag = band.tag,
        palette = band.palette,
    )
}

private data class Band(
    val headline: String,
    val top: String,
    val outer: String,
    val bottom: String,
    val shoes: String,
    val tag: String,
    val palette: String,
)
