package com.zhisheng.weather.ui

import androidx.compose.ui.graphics.Color
import com.zhisheng.weather.data.Astronomy
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.cityZone
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 天气详情可视化页的循环顺序，与参考实现一致：
 * 空气质量 → 紫外线 → 体感温度 → 风力 → 气压 → 能见度 → 湿度 → 日出日落；
 * 最后追加「48小时天气」逐时温度页（迁移 yunsong 的 HourlyChartView，保留淡蓝渐变）。
 */
internal enum class DetailPage(val title: String) {
    AIR_QUALITY("空气质量"),
    UV("紫外线"),
    FEELS_LIKE("体感温度"),
    WIND("风力"),
    PRESSURE("气压"),
    VISIBILITY("能见度"),
    HUMIDITY("湿度"),
    SUN("日出日落");

    val metric: MetricKind? get() = MetricKind.entries.firstOrNull { it.page == this }

    companion object {
        fun fromKey(key: String?): DetailPage =
            entries.firstOrNull { it.name == key } ?: AIR_QUALITY
    }
}

/**
 * 可在逐小时详情页里绘制的六类指标。基础单位沿用项目内部口径：
 * 温度 ℃、风速 km/h、气压 hPa、能见度 km、湿度 %。
 */
internal enum class MetricKind(
    val page: DetailPage,
    val title: String,
    val unit: String,
    val colors: List<Color>,
    val about: String,
) {
    UV(
        DetailPage.UV, "紫外线", "级",
        listOf(Color(0xFF4CD669), Color(0xFFE5DF31), Color(0xFFFFAC23), Color(0xFFFF5457), Color(0xFFE73C91)),
        "紫外线指数（UVI）反映太阳紫外线对皮肤的潜在伤害，单位为无量纲指数。0–2 为低，3–5 为中等，6–7 为高，8–10 很高，11 以上极高。达到 3 级应使用防晒、遮阳帽或墨镜，正午尽量减少暴晒。",
    ),
    FEELS_LIKE(
        DetailPage.FEELS_LIKE, "体感温度", "℃",
        listOf(Color(0xFF4294FF), Color(0xFF54D368), Color(0xFFFFBF33), Color(0xFFFF504E)),
        "体感温度综合气温、湿度、风速和日照估算人体感受，常用单位为摄氏度。舒适度根据当前体感温度判断：偏高时及时补水、防晒，偏低时添衣并注意风寒；极端冷热时应减少长时间户外活动。",
    ),
    WIND(
        DetailPage.WIND, "风力", "km/h",
        listOf(Color(0xFFAAE7FF), Color(0xFF5FBDFF), Color(0xFF1C70DA)),
        "风力表示近地面空气运动速度，常用单位为 km/h 或风力等级；阵风是短时最大风速。风速超过 20km/h 骑行需更谨慎，超过 39km/h 应远离广告牌和临时搭建物，高空及水上活动需避风。",
    ),
    PRESSURE(
        DetailPage.PRESSURE, "气压", "hPa",
        listOf(Color(0xFF84CDFF), Color(0xFFB99DFF), Color(0xFFEFADFF)),
        "气压是单位面积空气柱的压力，常用单位 hPa。海平面附近常见约 1013hPa，但会随海拔不同而变化。气压快速升降常伴随天气变化；部分敏感人群可能感到不适，应保证休息并留意预警。",
    ),
    VISIBILITY(
        DetailPage.VISIBILITY, "能见度", "km",
        listOf(Color(0xFF4ACFA9), Color(0xFF53BCEB), Color(0xFF3987FF)),
        "能见度是能清晰辨认目标的最大距离，常用单位为 km。低于 1km 属于低能见度，驾车应减速、开启灯光并增大车距；5km 以下需留意雾、霾或降水，高速及水上出行宜提前规划。",
    ),
    HUMIDITY(
        DetailPage.HUMIDITY, "湿度", "%",
        listOf(Color(0xFF57D380), Color(0xFF32B4BF), Color(0xFF3E83FF)),
        "相对湿度表示空气接近饱和的程度，单位为百分比。40%–60% 通常较舒适；过低会令皮肤和呼吸道干燥，过高则闷热、衣物不易干。持续高湿时应加强通风、防潮和除霉。",
    );

    fun valueOf(hour: HourlyWeather): Double? = when (this) {
        UV -> hour.uvIndex?.toDouble()
        FEELS_LIKE -> hour.feelsLike
        WIND -> hour.windSpeed
        PRESSURE -> hour.pressure
        VISIBILITY -> hour.visibility
        HUMIDITY -> hour.humidity
    }

    /** 第二条对照曲线：体感页画实时温度，风力页画阵风。 */
    fun secondaryOf(hour: HourlyWeather): Double? = when (this) {
        FEELS_LIKE -> hour.temperature
        WIND -> hour.windGust
        else -> null
    }

    fun level(value: Double?): String {
        if (value == null || value.isNaN()) return "暂无等级"
        return when (this) {
            UV -> when {
                value <= 2 -> "很弱"
                value <= 5 -> "中等"
                value <= 7 -> "强"
                value <= 10 -> "很强"
                else -> "极强"
            }
            FEELS_LIKE -> when {
                value <= -10 -> "严寒"
                value <= 0 -> "寒冷"
                value <= 10 -> "偏冷"
                value <= 18 -> "微凉"
                value <= 26 -> "舒适"
                value <= 32 -> "偏热"
                value <= 38 -> "炎热"
                else -> "酷热"
            }
            WIND -> when {
                value < 12 -> "微风"
                value < 20 -> "和风"
                value < 39 -> "风感明显"
                else -> "较强"
            }
            PRESSURE -> when {
                value < 1000 -> "偏低"
                value > 1025 -> "偏高"
                else -> "平稳"
            }
            VISIBILITY -> when {
                value < 1 -> "较低"
                value < 5 -> "一般"
                else -> "良好"
            }
            HUMIDITY -> when {
                value < 30 -> "偏干"
                value > 80 -> "偏湿"
                else -> "舒适"
            }
        }
    }

    fun advice(value: Double?): String {
        if (value == null || value.isNaN()) return "暂未获取到该时段预测，请稍后刷新。"
        return when (this) {
            UV -> when {
                value >= 8 -> "防晒强度拉满，避开长时间直晒。"
                value >= 5 -> "外出做好防晒，午间尽量走阴凉处。"
                value >= 3 -> "日常防晒即可，别忘了补涂。"
                else -> "紫外线较弱，按需进行日常防护。"
            }
            FEELS_LIKE -> when {
                value <= -10 -> "体感严寒，尽量减少户外停留并做好防寒。"
                value <= 0 -> "体感寒冷，外出注意防风保暖。"
                value <= 10 -> "体感偏冷，添一件外套更稳妥。"
                value <= 18 -> "微凉，早晚可加一层薄外套。"
                value <= 26 -> "体感舒适，适合正常安排出行。"
                value <= 32 -> "体感偏热，注意补水并避开正午暴晒。"
                value <= 38 -> "体感炎热，减少长时间户外活动。"
                else -> "体感酷热，谨防中暑并及时补水。"
            }
            WIND -> when {
                value >= 39 -> "风力较强，远离临时搭建物和高空坠物。"
                value >= 20 -> "风感明显，骑行和高空活动请注意安全。"
                else -> "风力平稳，适合正常安排出行。"
            }
            PRESSURE -> "气压变化较快时，敏感人群可适当放慢活动节奏。"
            VISIBILITY -> when {
                value < 1 -> "能见度较低，驾车请减速并开启灯光。"
                value < 5 -> "视野一般，出行请留意道路情况。"
                else -> "视野良好，可正常安排出行。"
            }
            HUMIDITY -> when {
                value >= 80 -> "空气偏潮湿，注意通风与衣物干燥。"
                value <= 30 -> "空气较干燥，建议及时补水保湿。"
                else -> "湿度适宜，体感相对舒适。"
            }
        }
    }

    /** 页面大字读数：单位跟随「显示单位」设置（温度单位由调用方单独传入）。 */
    fun display(value: Double?, prefs: DisplayPrefs, tempUnit: String): String {
        if (value == null || value.isNaN()) return "暂无数据"
        return when (this) {
            UV -> "${value.roundToInt()}级"
            FEELS_LIKE -> "${Fmt.temp(value, tempUnit) ?: value.roundToInt().toString()}°"
            WIND -> when (prefs.windUnit) {
                "ms" -> String.format(Locale.US, "%.1f m/s", value / 3.6)
                "bft" -> "${beaufort(value)}级"
                else -> "${value.roundToInt()} km/h"
            }
            PRESSURE -> Fmt.pressure(value, prefs.pressureUnit) ?: "${value.roundToInt()} hPa"
            VISIBILITY -> "${oneDecimal(value)} km"
            HUMIDITY -> "${value.roundToInt()}%"
        }
    }

    /** 图表刻度：只给数值，单位由页面读数负责。 */
    fun axisText(value: Double, prefs: DisplayPrefs, tempUnit: String): String = when (this) {
        UV -> value.roundToInt().toString()
        FEELS_LIKE -> (Fmt.temp(value, tempUnit) ?: value.roundToInt().toString())
        WIND -> when (prefs.windUnit) {
            "ms" -> String.format(Locale.US, "%.1f", value / 3.6)
            "bft" -> beaufort(value).toString()
            else -> value.roundToInt().toString()
        }
        PRESSURE -> when (prefs.pressureUnit) {
            "mmhg" -> String.format(Locale.US, "%.0f", value * 0.750062)
            "inhg" -> String.format(Locale.US, "%.2f", value * 0.02953)
            else -> value.roundToInt().toString()
        }
        VISIBILITY -> oneDecimal(value)
        HUMIDITY -> value.roundToInt().toString()
    }

    /** 体感页的温差说明：体感比实际温度高 / 低多少。 */
    fun feelsLikeComparison(feels: Double?, actual: Double?, tempUnit: String): String? {
        if (feels == null || actual == null) return null
        val delta = feels - actual
        val shown = (Fmt.temp(abs(delta), tempUnit) ?: abs(delta).roundToInt().toString())
        return when {
            abs(delta) < 0.5 -> "体感与实际温度接近"
            delta > 0 -> "体感比实际温度高 ${shown}°"
            else -> "体感比实际温度低 ${shown}°"
        }
    }
}

private fun oneDecimal(value: Double): String =
    String.format(Locale.US, "%.1f", value).removeSuffix(".0")

private fun beaufort(kmh: Double): Int = when {
    kmh < 1.0 -> 0
    kmh < 6.0 -> 1
    kmh < 12.0 -> 2
    kmh < 20.0 -> 3
    kmh < 29.0 -> 4
    kmh < 39.0 -> 5
    kmh < 50.0 -> 6
    kmh < 62.0 -> 7
    kmh < 75.0 -> 8
    kmh < 89.0 -> 9
    kmh < 103.0 -> 10
    kmh < 118.0 -> 11
    else -> 12
}

/** 未来 24 小时（从当前小时起）的逐时点；源数据不足时退回最后 24 条。 */
internal fun detailHourly(weather: WeatherData?, now: Long = System.currentTimeMillis()): List<HourlyWeather> =
    detailHourlyWindow(weather, now, 24)

/** 未来 48 小时（从当前小时起）的逐时点；源数据不足时退回最后 48 条。 */
internal fun detailHourly48(weather: WeatherData?, now: Long = System.currentTimeMillis()): List<HourlyWeather> =
    detailHourlyWindow(weather, now, 48)

private fun detailHourlyWindow(
    weather: WeatherData?,
    now: Long,
    count: Int,
): List<HourlyWeather> {
    val all = weather?.hourly.orEmpty()
    if (all.isEmpty()) return emptyList()
    val start = all.indexOfFirst { it.timeMillis >= now - 30L * 60_000L }
    val window = if (start >= 0) all.drop(start) else all
    return window.take(count).ifEmpty { all.takeLast(count) }
}

/** 选中时刻的显示：跨天时加「明日」。 */
internal fun detailTimeLabel(timeMillis: Long, now: Long, utcOffsetSeconds: Int?): String {
    if (timeMillis <= 0L) return "预测时段"
    val today = Instant.ofEpochMilli(now).atZone(cityZone(utcOffsetSeconds)).toLocalDate()
    val selected = Instant.ofEpochMilli(timeMillis).atZone(cityZone(utcOffsetSeconds)).toLocalDate()
    val clock = Fmt.clock(timeMillis, utcOffsetSeconds)
    return if (selected == today) clock else "明日 $clock"
}

/**
 * 信息摘要：未来 24 小时区间 + 与昨日同期对比 + 指标建议，口径与 yunsong 一致。
 * 本项目逐时序列没有昨日同期值，因此对比段落说明数据源未提供，绝不估算。
 */
internal fun buildMetricSummary(
    kind: MetricKind,
    values: List<Double?>,
    prefs: DisplayPrefs,
    tempUnit: String,
): String {
    val valid = values.filterNotNull().filter { it.isFinite() }
    if (valid.isEmpty()) return "未来24小时暂未取得${kind.title}数据，请稍后刷新。"
    val min = valid.min()
    val max = valid.max()
    val range = if (abs(max - min) < 0.05) kind.display(min, prefs, tempUnit)
    else "${kind.display(min, prefs, tempUnit)}–${kind.display(max, prefs, tempUnit)}"
    return "未来24小时${kind.title}范围 $range，选中天气源未提供昨日同期数据。${kind.advice(max)}"
}

/** 风向中文名：正北为 0°，顺时针。 */
internal fun detailWindDirection(degrees: Double?): String? {
    if (degrees == null || !degrees.isFinite()) return null
    val names = arrayOf("北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风")
    return names[(((degrees % 360 + 360) % 360 + 22.5) / 45).toInt() % names.size]
}

// ═══════════════════════════════════════════════════════════
// 日出日落：全天太阳高度、晨昏时刻与霞光展望
// ═══════════════════════════════════════════════════════════

internal data class SunTimeline(
    val points: List<Pair<Long, Double>>,
    val sunrise: Long?,
    val sunset: Long?,
    val solarNoon: Long?,
    val blueMorning: Pair<Long, Long>?,
    val blueEvening: Pair<Long, Long>?,
    val goldenMorning: Pair<Long, Long>?,
    val goldenEvening: Pair<Long, Long>?,
    val daylightMinutes: Int?,
    val yesterdayDaylightMinutes: Int?,
    val latitude: Double,
    val longitude: Double,
    val utcOffsetSeconds: Int?,
) {
    val daylightText: String
        get() = daylightMinutes?.let { "${it / 60}小时${it % 60}分" } ?: "--"
}

private const val SUNRISE_ALTITUDE = -0.833
private const val SUN_STEP_MS = 10L * 60_000L

internal fun buildSunTimeline(
    weather: WeatherData?,
    city: City?,
    utcOffsetSeconds: Int? = weather?.utcOffsetSeconds,
    now: Long = System.currentTimeMillis(),
): SunTimeline? {
    if (city == null) return null
    val zone = cityZone(utcOffsetSeconds)
    val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val points = sunCurve(dayStart, city.latitude, city.longitude)
    val today = weather?.daily?.firstOrNull {
        Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() == date
    }
    val sunrise = parseClock(today?.sunrise, dayStart, zone)
        ?: crossing(points, SUNRISE_ALTITUDE, rising = true)
    val sunset = parseClock(today?.sunset, dayStart, zone)
        ?: crossing(points, SUNRISE_ALTITUDE, rising = false)
    val yesterdayStart = date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val yesterdayPoints = sunCurve(yesterdayStart, city.latitude, city.longitude)
    val yesterdayRise = crossing(yesterdayPoints, SUNRISE_ALTITUDE, rising = true)
    val yesterdaySet = crossing(yesterdayPoints, SUNRISE_ALTITUDE, rising = false)
    return SunTimeline(
        points = points,
        sunrise = sunrise,
        sunset = sunset?.takeIf { sunrise == null || it > sunrise },
        solarNoon = points.maxByOrNull { it.second }?.first,
        blueMorning = window(points, -6.0, -4.0, rising = true),
        blueEvening = window(points, -4.0, -6.0, rising = false),
        goldenMorning = window(points, -4.0, 6.0, rising = true),
        goldenEvening = window(points, 6.0, -4.0, rising = false),
        daylightMinutes = if (sunrise != null && sunset != null && sunset > sunrise) {
            ((sunset - sunrise) / 60_000L).toInt()
        } else {
            null
        },
        yesterdayDaylightMinutes = if (yesterdayRise != null && yesterdaySet != null && yesterdaySet > yesterdayRise) {
            ((yesterdaySet - yesterdayRise) / 60_000L).toInt()
        } else {
            null
        },
        latitude = city.latitude,
        longitude = city.longitude,
        utcOffsetSeconds = utcOffsetSeconds,
    )
}

internal fun sunSummary(timeline: SunTimeline): String {
    val sunrise = timeline.sunrise
    val sunset = timeline.sunset
    if (sunrise == null || sunset == null) {
        return "当地可能处于极昼、极夜，或暂无可用的日出日落交点；晨昏时段仍会按本地太阳高度展示。"
    }
    val hours = timeline.daylightMinutes?.div(60) ?: 0
    val minutes = timeline.daylightMinutes?.rem(60) ?: 0
    val change = timeline.yesterdayDaylightMinutes?.let { previous ->
        val delta = (timeline.daylightMinutes ?: previous) - previous
        when {
            abs(delta) <= 1 -> "与昨日昼长基本持平"
            delta > 0 -> "较昨日长约${delta}分钟"
            else -> "较昨日短约${-delta}分钟"
        }
    } ?: "昨日昼长待计算"
    val rise = Fmt.clock(sunrise, timeline.utcOffsetSeconds)
    val set = Fmt.clock(sunset, timeline.utcOffsetSeconds)
    return "日出 $rise，日落 $set，昼长 ${hours}小时${minutes}分钟，$change。"
}

/** 选中时刻的太阳位置说明：时间 · 高度 · 方位 · 时段。 */
internal fun sunSelectionText(timeline: SunTimeline, timeMillis: Long, elevation: Double): String {
    val azimuth = sunAzimuth(timeMillis, timeline.latitude, timeline.longitude)
    val phase = when {
        elevation < -18 -> "深夜"
        elevation < -6 -> "天文晨昏"
        elevation < 0 -> "蓝调时刻"
        elevation < 6 -> "金色时刻"
        else -> "白昼"
    }
    return "${Fmt.clock(timeMillis, timeline.utcOffsetSeconds)} · 太阳高度 ${String.format(Locale.CHINA, "%.1f", elevation)}° · 方位 ${azimuth.roundToInt()}° · $phase"
}

internal fun sunAdvice(timeline: SunTimeline, timeMillis: Long, elevation: Double): String {
    val nearRise = timeline.sunrise?.let { abs(timeMillis - it) <= 12L * 60_000L } == true
    val nearSet = timeline.sunset?.let { abs(timeMillis - it) <= 12L * 60_000L } == true
    return when {
        nearRise -> "日出时刻，光线快速增强，注意低角度眩光。"
        nearSet -> "日落时刻，天色转暗，外出请留意照明。"
        elevation < -6 -> "天色较暗，户外活动请留意照明与路况。"
        elevation < 0 -> "处于蓝调时刻，光线变化快，拍摄可适当提高曝光。"
        elevation < 6 -> "处于金色时刻，阳光角度低，注意逆光与眩光。"
        elevation > 45 -> "日照较强，外出可结合紫外线指数做好防晒。"
        else -> "光线平稳，适合按日常安排出行。"
    }
}

internal data class SunGlow(val score: Int, val level: String, val description: String)

/** 本地霞光展望：只看当天天气类型与云量，不冒充接口实况字段。 */
internal fun sunGlow(day: com.zhisheng.weather.model.DailyWeather?): SunGlow {
    val condition = day?.condition
    val score = when {
        condition?.isPrecipitation == true || condition == WeatherCondition.THUNDERSTORM -> 16
        condition == WeatherCondition.FOG || condition == WeatherCondition.HAZE ||
            condition == WeatherCondition.SAND -> 24
        condition == WeatherCondition.PARTLY_CLOUDY || condition == WeatherCondition.PARTLY_CLOUDY_NIGHT -> 78
        condition == WeatherCondition.CLOUDY || condition == WeatherCondition.OVERCAST -> 42
        condition == WeatherCondition.CLEAR || condition == WeatherCondition.CLEAR_NIGHT -> 58
        else -> {
            val cloud = day?.cloudCover
            when {
                cloud == null -> 50
                cloud <= 20 -> 58
                cloud <= 50 -> 78
                cloud <= 80 -> 42
                else -> 24
            }
        }
    }
    val level = when {
        score >= 72 -> "较佳"
        score >= 52 -> "一般"
        score >= 32 -> "偏弱"
        else -> "不明显"
    }
    val note = when {
        score <= 16 -> "降水或强对流影响，霞光不易显现"
        score <= 24 -> "能见度受影响，色彩层次会偏弱"
        score >= 72 -> "云量疏密适中，较有机会形成层次"
        score <= 42 -> "云层偏厚，留意云隙透光"
        else -> "晴空为主，通透但云彩层次较少"
    }
    return SunGlow(score, level, note)
}

private fun sunCurve(dayStart: Long, latitude: Double, longitude: Double): List<Pair<Long, Double>> =
    (0..144).map { step ->
        val time = dayStart + step * SUN_STEP_MS
        time to Astronomy.sunAltitudeDegrees(time, latitude, longitude)
    }

private fun crossing(points: List<Pair<Long, Double>>, target: Double, rising: Boolean): Long? {
    for (index in 1 until points.size) {
        val (beforeTime, before) = points[index - 1]
        val (afterTime, after) = points[index]
        val crossesUp = before < target && after >= target
        val crossesDown = before >= target && after < target
        if ((rising && crossesUp) || (!rising && crossesDown)) {
            val fraction = ((target - before) / (after - before)).coerceIn(0.0, 1.0)
            return beforeTime + ((afterTime - beforeTime) * fraction).toLong()
        }
    }
    return null
}

private fun window(
    points: List<Pair<Long, Double>>,
    from: Double,
    to: Double,
    rising: Boolean,
): Pair<Long, Long>? {
    // 晨段两个阈值都向上穿越，昏段都向下穿越；同一方向取两次交点。
    val first = crossing(points, from, rising) ?: return null
    val second = crossing(points, to, rising) ?: return null
    if (second <= first) return null
    return first to second
}

private fun parseClock(value: String?, dayStart: Long, zone: java.time.ZoneId): Long? {
    val text = value?.trim().orEmpty()
    if (text.length !in 4..5) return null
    return runCatching {
        val time = LocalTime.parse(if (text.length == 4) "0$text" else text)
        dayStart + time.toSecondOfDay() * 1000L
    }.getOrNull()
}

/** NOAA 近似太阳方位角：正北 0°，顺时针增加。 */
internal fun sunAzimuth(timeMillis: Long, latitude: Double, longitude: Double): Double {
    val instant = Instant.ofEpochMilli(timeMillis)
    val utc = instant.atZone(java.time.ZoneOffset.UTC)
    val day = utc.dayOfYear
    val hour = utc.hour + utc.minute / 60.0
    val gamma = 2.0 * PI / instant.atZone(java.time.ZoneOffset.UTC).toLocalDate().lengthOfYear() *
        (day - 1 + (hour - 12.0) / 24.0)
    val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
        0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
    val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
        0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
        0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
    val minutes = hour * 60.0 + eqTime + 4.0 * longitude
    var degrees = minutes / 4.0 - 180.0
    while (degrees < -180.0) degrees += 360.0
    while (degrees > 180.0) degrees -= 360.0
    val hourAngle = degrees * PI / 180.0
    val lat = latitude * PI / 180.0
    return ((atan2(
        sin(hourAngle),
        cos(hourAngle) * sin(lat) - kotlin.math.tan(decl) * cos(lat),
    ) * 180.0 / PI + 180.0) + 360.0) % 360.0
}
