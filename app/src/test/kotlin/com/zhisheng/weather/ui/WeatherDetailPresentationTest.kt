package com.zhisheng.weather.ui

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CoordinateKind
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 天气详情可视化页的展示口径：八类循环、摘要文案、单位换算与日出日落本地计算。 */
class WeatherDetailPresentationTest {

    private val prefs = DisplayPrefs()

    private val hourly = listOf(
        HourlyWeather(
            timeMillis = 0L,
            temperature = 26.0,
            feelsLike = 28.4,
            windSpeed = 12.0,
            windGust = 21.0,
            windDirectionDeg = 135.0,
            pressure = 1008.0,
            visibility = 8.5,
            humidity = 62.0,
            uvIndex = 3,
        ),
        HourlyWeather(
            timeMillis = 1L,
            temperature = 30.0,
            feelsLike = 33.0,
            windSpeed = 24.0,
            windGust = 40.0,
            windDirectionDeg = 350.0,
            pressure = 1012.0,
            visibility = 12.0,
            humidity = 88.0,
            uvIndex = 8,
        ),
    )

    @Test
    fun `循环顺序与参考实现一致`() {
        assertEquals(
            listOf("空气质量", "紫外线", "体感温度", "风力", "气压", "能见度", "湿度", "日出日落"),
            DetailPage.entries.map { it.title },
        )
        assertEquals(DetailPage.AIR_QUALITY, DetailPage.fromKey("AIR_QUALITY"))
        assertEquals(DetailPage.AIR_QUALITY, DetailPage.fromKey("unknown"))
        assertEquals(DetailPage.AIR_QUALITY, DetailPage.fromKey(null))
    }

    @Test
    fun `逐时指标读取项目内部单位口径`() {
        assertEquals("uv", 3.0, MetricKind.UV.valueOf(hourly[0])!!, 1e-9)
        assertEquals("feels", 28.4, MetricKind.FEELS_LIKE.valueOf(hourly[0])!!, 1e-9)
        assertEquals("wind", 12.0, MetricKind.WIND.valueOf(hourly[0])!!, 1e-9)
        assertEquals("pressure", 1008.0, MetricKind.PRESSURE.valueOf(hourly[0])!!, 1e-9)
        assertEquals("visibility", 8.5, MetricKind.VISIBILITY.valueOf(hourly[0])!!, 1e-9)
        assertEquals("humidity", 62.0, MetricKind.HUMIDITY.valueOf(hourly[0])!!, 1e-9)
        // 第二条对照曲线：体感页画温度，风力页画阵风。
        assertEquals("temperature", 26.0, MetricKind.FEELS_LIKE.secondaryOf(hourly[0])!!, 1e-9)
        assertEquals("gust", 21.0, MetricKind.WIND.secondaryOf(hourly[0])!!, 1e-9)
        assertEquals(null, MetricKind.UV.secondaryOf(hourly[0]))
    }

    @Test
    fun `读数与刻度跟随显示单位`() {
        assertEquals("28°", MetricKind.FEELS_LIKE.display(28.4, prefs, "c"))
        assertEquals("83°", MetricKind.FEELS_LIKE.display(28.4, prefs, "f"))
        assertEquals("12 km/h", MetricKind.WIND.display(12.0, prefs.copy(windUnit = "kmh"), "c"))
        assertEquals("3.3 m/s", MetricKind.WIND.display(12.0, prefs.copy(windUnit = "ms"), "c"))
        assertEquals("3级", MetricKind.WIND.display(12.0, prefs.copy(windUnit = "bft"), "c"))
        assertEquals("1008", MetricKind.PRESSURE.axisText(1008.0, prefs.copy(pressureUnit = "hpa"), "c"))
        assertEquals("756", MetricKind.PRESSURE.axisText(1008.0, prefs.copy(pressureUnit = "mmhg"), "c"))
        assertEquals("8.5", MetricKind.VISIBILITY.axisText(8.5, prefs, "c"))
        assertEquals("62", MetricKind.HUMIDITY.axisText(62.0, prefs, "c"))
    }

    @Test
    fun `等级与建议按阈值给出人话`() {
        assertEquals("很弱", MetricKind.UV.level(2.0))
        assertEquals("强", MetricKind.UV.level(6.0))
        assertEquals("极强", MetricKind.UV.level(11.0))
        assertEquals("舒适", MetricKind.FEELS_LIKE.level(24.0))
        assertEquals("偏冷", MetricKind.FEELS_LIKE.level(8.0))
        assertEquals("较强", MetricKind.WIND.level(45.0))
        assertEquals("偏低", MetricKind.PRESSURE.level(998.0))
        assertEquals("一般", MetricKind.VISIBILITY.level(3.0))
        assertEquals("偏湿", MetricKind.HUMIDITY.level(88.0))
        assertEquals("暂无等级", MetricKind.UV.level(null))
        assertTrue(MetricKind.VISIBILITY.advice(0.6).contains("减速"))
        assertTrue(MetricKind.HUMIDITY.advice(88.0).contains("通风"))
    }

    @Test
    fun `摘要只给未来区间并明确昨日同期缺失`() {
        val values = listOf(3.0, 8.0, null, 5.0)
        val summary = buildMetricSummary(MetricKind.UV, values, prefs, "c")
        assertTrue(summary.contains("未来24小时紫外线范围 3级–8级"))
        assertTrue(summary.contains("未提供昨日同期数据"))
        // 空序列不编造范围。
        val empty = buildMetricSummary(MetricKind.WIND, listOf(null, null), prefs, "c")
        assertTrue(empty.contains("暂未取得风力数据"))
    }

    @Test
    fun `体感温差说明按显示单位书写`() {
        assertEquals(
            "体感比实际温度高 5°",
            MetricKind.FEELS_LIKE.feelsLikeComparison(33.0, 28.0, "c"),
        )
        assertEquals(
            "体感比实际温度低 3°",
            MetricKind.FEELS_LIKE.feelsLikeComparison(25.0, 28.0, "c"),
        )
        assertEquals(
            "体感与实际温度接近",
            MetricKind.FEELS_LIKE.feelsLikeComparison(28.2, 28.0, "c"),
        )
        assertEquals(null, MetricKind.FEELS_LIKE.feelsLikeComparison(null, 28.0, "c"))
    }

    @Test
    fun `风向按八方位输出`() {
        assertEquals("北风", detailWindDirection(0.0))
        assertEquals("东北风", detailWindDirection(45.0))
        assertEquals("东风", detailWindDirection(90.0))
        assertEquals("南风", detailWindDirection(180.0))
        assertEquals("西风", detailWindDirection(270.0))
        assertEquals("北风", detailWindDirection(359.0))
        assertEquals(null, detailWindDirection(null))
    }

    @Test
    fun `选中时刻跨天时标注明日`() {
        val now = Instant.parse("2026-06-01T10:00:00+08:00").toEpochMilli()
        val sameDay = Instant.parse("2026-06-01T18:00:00+08:00").toEpochMilli()
        val nextDay = Instant.parse("2026-06-02T02:00:00+08:00").toEpochMilli()
        assertEquals("18:00", detailTimeLabel(sameDay, now, 8 * 3600))
        assertEquals("明日 02:00", detailTimeLabel(nextDay, now, 8 * 3600))
        assertEquals("预测时段", detailTimeLabel(0L, now, 8 * 3600))
    }

    @Test
    fun `日出日落由本地太阳高度补算`() {
        val now = Instant.parse("2026-06-01T04:00:00Z").toEpochMilli()
        val timeline = buildSunTimeline(
            weather = null,
            city = City(
                name = "广州",
                affiliation = "广东省",
                latitude = 23.13,
                longitude = 113.26,
                locationKey = "test_guangzhou",
                coordinateKind = CoordinateKind.ADMINISTRATIVE,
            ),
            utcOffsetSeconds = 8 * 3600,
            now = now,
        )!!
        val sunrise = timeline.sunrise!!
        val sunset = timeline.sunset!!
        assertTrue(sunrise < sunset)
        // 六月初广州昼长约 13 小时。
        val daylight = timeline.daylightMinutes!!
        assertTrue("daylight=$daylight", daylight in 720..840)
        assertTrue(timeline.daylightText.endsWith("分"))
        val solarNoon = timeline.solarNoon!!
        assertTrue(solarNoon in sunrise..sunset)
        // 晨昏窗口按太阳高度交点给出，晨段早于昏段。
        val morning = timeline.goldenMorning!!
        assertTrue(morning.first < morning.second)
    }

    @Test
    fun `霞光展望只依据天气类型与云量`() {
        assertEquals(78, sunGlow(DailyWeather(0L, condition = WeatherCondition.PARTLY_CLOUDY)).score)
        assertEquals(16, sunGlow(DailyWeather(0L, condition = WeatherCondition.RAIN)).score)
        assertEquals(58, sunGlow(DailyWeather(0L, condition = WeatherCondition.CLEAR)).score)
        assertEquals(24, sunGlow(DailyWeather(0L, condition = WeatherCondition.HAZE)).score)
        assertEquals(78, sunGlow(DailyWeather(0L, cloudCover = 40.0)).score)
        assertEquals(50, sunGlow(null).score)
    }

    @Test
    fun `太阳方位角落在合法区间`() {
        val now = Instant.parse("2026-06-01T04:00:00Z").toEpochMilli()
        val azimuth = sunAzimuth(now, 23.13, 113.26)
        assertTrue("azimuth=$azimuth", azimuth >= 0.0 && azimuth < 360.0)
    }

    @Test
    fun `aqi逐时从当前小时起取窗口并跳过缺失日`() {
        val now = Instant.parse("2026-06-01T02:00:00+08:00").toEpochMilli()
        val data = WeatherData(
            hourly = (0..29).map { index ->
                HourlyWeather(
                    timeMillis = now + index * 3_600_000L,
                    aqi = if (index == 2) null else 50 + index,
                )
            },
            utcOffsetSeconds = 8 * 3600,
        )
        val points = aqiDetailHourly(data, now)
        assertEquals(23, points.size)
        assertEquals("02:00", points.first().axisLabel)
        assertEquals(50, points.first().value)
        assertEquals("01:00", points.last().axisLabel)
        // 空源不产出柱。
        assertTrue(aqiDetailHourly(null, now).isEmpty())
    }

    @Test
    fun `aqi逐日从今天起至多7天且跳过缺失`() {
        val now = Instant.parse("2026-06-01T02:00:00+08:00").toEpochMilli()
        fun day(offsetDays: Int, aqi: Int?) = DailyWeather(
            dateMillis = Instant.parse("2026-06-0${1 + offsetDays}T00:00:00+08:00").toEpochMilli(),
            aqi = aqi,
        )
        val data = WeatherData(
            daily = listOf(day(0, 55), day(1, null), day(2, 70)),
            utcOffsetSeconds = 8 * 3600,
        )
        val points = aqiDetailDaily(data, now)
        assertEquals(2, points.size)
        assertEquals("今天", points[0].axisLabel)
        assertEquals("6/3", points[1].axisLabel)
        assertEquals(70, points[1].value)
    }

    @Test
    fun `aqi等级按国标阈值分档`() {
        assertEquals("优", aqiDetailLevelLabel(42))
        assertEquals("良", aqiDetailLevelLabel(80))
        assertEquals("轻度污染", aqiDetailLevelLabel(120))
        assertEquals("中度污染", aqiDetailLevelLabel(180))
        assertEquals("重度污染", aqiDetailLevelLabel(260))
        assertEquals("严重污染", aqiDetailLevelLabel(420))
        assertEquals("暂无", aqiDetailLevelLabel(null))
    }

    @Test
    fun `48小时窗口从当前小时起至多48条`() {
        val now = Instant.parse("2026-06-01T02:00:00+08:00").toEpochMilli()
        val data = WeatherData(
            hourly = (0..59).map { index ->
                HourlyWeather(timeMillis = now + index * 3_600_000L, temperature = 20.0 + index % 10)
            },
            utcOffsetSeconds = 8 * 3600,
        )
        val hours = detailHourly48(data, now)
        assertEquals(48, hours.size)
        assertEquals(now, hours.first().timeMillis)
        // 源不足 48 条时退回剩余全部。
        val short = detailHourly48(WeatherData(hourly = listOf(HourlyWeather(timeMillis = now))), now)
        assertEquals(1, short.size)
        // 空源不产出。
        assertTrue(detailHourly48(null, now).isEmpty())
    }
}
