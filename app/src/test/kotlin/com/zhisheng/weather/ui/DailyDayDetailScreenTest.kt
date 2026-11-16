package com.zhisheng.weather.ui

import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailyDayDetailScreenTest {

    private val palette = TrendChartPalette.forTheme(isLight = true)

    private fun todayMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `per-day summary reports range advice and today marker`() {
        val now = System.currentTimeMillis()
        val day = DailyWeather(
            dateMillis = todayMillis(),
            high = 28.0,
            low = 18.0,
            condition = WeatherCondition.CLEAR,
            precipProbability = 30,
        )
        val hours = listOf(
            HourlyWeather(timeMillis = now, temperature = 21.0, feelsLike = 24.0, precipProb = 30, windSpeed = 12.0, uvIndex = 6, humidity = 70.0, pressure = 1013.0, visibility = 10.0, aqi = 80),
        )

        val metrics = buildDailyTrendSummaryMetrics(day, hours, "c", "kmh", now, palette)

        val temp = metrics.first { it.kind == TrendSummaryKind.TEMPERATURE }
        assertEquals("18°–28°", temp.value)
        assertEquals("晴 · 当日温差10°", temp.detail)
        assertEquals("昼夜温差较大，建议分层穿衣", temp.advice)
        assertEquals("21°", temp.currentValue)
        assertEquals(0.3f, temp.currentProgress!!, 0.001f)

        val rain = metrics.first { it.kind == TrendSummaryKind.RAIN }
        assertEquals("30%", rain.value)
        assertEquals("有雨可能，出门带伞", rain.advice)

        val wind = metrics.first { it.kind == TrendSummaryKind.WIND }
        assertEquals("12–12km/h", wind.value)

        val uv = metrics.first { it.kind == TrendSummaryKind.UV }
        assertEquals("6级", uv.value)

        val aqi = metrics.first { it.kind == TrendSummaryKind.AQI }
        assertEquals("AQI 80–80", aqi.value)
        assertEquals("整体良好", aqi.detail)
    }

    @Test
    fun `per-day summary falls back without data`() {
        val now = System.currentTimeMillis()
        val day = DailyWeather(dateMillis = todayMillis(), condition = WeatherCondition.CLEAR)

        val metrics = buildDailyTrendSummaryMetrics(day, emptyList(), "c", "kmh", now, palette)

        val temp = metrics.first { it.kind == TrendSummaryKind.TEMPERATURE }
        assertEquals("—", temp.value)
        assertEquals("当日数据暂缺", temp.detail)
        assertEquals("以已提供的数据为准", temp.advice)
        assertNull(temp.currentValue)
    }

    @Test
    fun `per-day summary omits today marker on a future day`() {
        val now = System.currentTimeMillis()
        val future = todayMillis() + 24L * 3600_000L
        val day = DailyWeather(dateMillis = future, high = 20.0, low = 10.0, condition = WeatherCondition.CLEAR)
        val hours = listOf(HourlyWeather(timeMillis = future, temperature = 15.0))

        val metrics = buildDailyTrendSummaryMetrics(day, hours, "c", "kmh", now, palette)

        val temp = metrics.first { it.kind == TrendSummaryKind.TEMPERATURE }
        assertNull(temp.currentValue)
        assertNull(temp.currentProgress)
    }

    @Test
    fun `daily metric maps value secondary and text per tab`() {
        val hour = HourlyWeather(
            timeMillis = 0L,
            temperature = 21.0,
            feelsLike = 24.0,
            windSpeed = 20.0,
            windGust = 25.0,
            precipProb = 60,
            uvIndex = 6,
            humidity = 70.0,
            pressure = 1013.0,
            visibility = 10.0,
            aqi = 80,
        )

        assertEquals(24.0, DailyTrendMetric.TEMPERATURE.value(hour)!!, 0.001)
        assertEquals(21.0, DailyTrendMetric.TEMPERATURE.secondary(hour)!!, 0.001)
        assertEquals("24°", DailyTrendMetric.TEMPERATURE.text(24.0, "c", "kmh"))

        assertEquals(60.0, DailyTrendMetric.RAIN.value(hour)!!, 0.001)
        assertEquals("60%", DailyTrendMetric.RAIN.text(60.0, "c", "kmh"))

        assertEquals(20.0, DailyTrendMetric.WIND.value(hour)!!, 0.001)
        assertEquals(25.0, DailyTrendMetric.WIND.secondary(hour)!!, 0.001)

        assertEquals(6.0, DailyTrendMetric.UV.value(hour)!!, 0.001)
        assertEquals("6级", DailyTrendMetric.UV.text(6.0, "c", "kmh"))

        assertEquals(70.0, DailyTrendMetric.HUMIDITY.value(hour)!!, 0.001)
        assertEquals("70%", DailyTrendMetric.HUMIDITY.text(70.0, "c", "kmh"))

        assertEquals(1013.0, DailyTrendMetric.PRESSURE.value(hour)!!, 0.001)

        assertEquals(10.0, DailyTrendMetric.VISIBILITY.value(hour)!!, 0.001)

        assertEquals(80.0, DailyTrendMetric.AQI.value(hour)!!, 0.001)
        assertEquals("80 AQI", DailyTrendMetric.AQI.text(80.0, "c", "kmh"))
    }

    @Test
    fun `advice helpers map their bands`() {
        assertEquals("今日无需备伞", trendRainAdvice(0))
        assertEquals("或有零星降水，备折叠伞", trendRainAdvice(20))
        assertEquals("降水概率高，注意避雨", trendRainAdvice(90))

        assertEquals("微风轻拂，适宜户外", trendWindAdvice(3.0))
        assertEquals("风力适中，骑行防风", trendWindAdvice(20.0))

        assertEquals("紫外线适中，建议防晒", trendUvAdvice(5.0))
        assertEquals("紫外线极强，减少暴晒", trendUvAdvice(12.0))

        assertEquals("空气优，适宜户外", trendAirAdvice(40.0))
        assertEquals("敏感人群减少户外", trendAirAdvice(120.0))
    }

    @Test
    fun `percent normalizes zero-to-one and clamps`() {
        assertEquals(60, trendPercent(0.6))
        assertEquals(60, trendPercent(60.0))
        assertNull(trendPercent(null))
    }

    @Test
    fun `range summary distinguishes short-lived degradation`() {
        assertEquals("整体优", trendAirRangeSummary(30.0, 40.0))
        assertEquals("整体良好，短时轻度污染", trendAirRangeSummary(80.0, 120.0))
    }

    @Test
    fun `uv semantic labels band by index`() {
        assertEquals("很弱", trendUvSemantic(1))
        assertEquals("中等", trendUvSemantic(5))
        assertEquals("极强", trendUvSemantic(12))
    }

    @Test
    fun `compact time prefixes the relative day`() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 3600_000L
        assertEquals("今天10点", trendCompactTime(now, now))
        assertEquals("明天10点", trendCompactTime(now + 24 * 3600_000L, now))
        assertTrue(trendCompactTime(now + 48 * 3600_000L, now).contains("10点"))
    }
}
