package com.zhisheng.weather.ui

import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyTrendScreenTest {

    @Test
    fun `summary reports the temperature range across the window`() {
        val days = listOf(
            day(high = 30.0, low = 22.0),
            day(high = 31.0, low = 23.0),
            day(high = 29.0, low = 21.0),
            day(high = 32.0, low = 24.0),
            day(high = 28.0, low = 20.0),
        )

        val summary = buildDailyTrendSummary(days, "c")

        assertEquals(
            "未来5日温度 20°–32°，无明显降水，昼夜温差大，注意增减衣物。",
            summary,
        )
    }

    @Test
    fun `summary notes a wet window when several days carry precipitation`() {
        val days = listOf(
            day(high = 30.0, low = 22.0, condition = WeatherCondition.RAIN, prob = 60),
            day(high = 29.0, low = 21.0, condition = WeatherCondition.RAIN, prob = 80),
            day(high = 28.0, low = 20.0, condition = WeatherCondition.RAIN, prob = 70),
            day(high = 27.0, low = 19.0),
            day(high = 26.0, low = 18.0),
        )

        val summary = buildDailyTrendSummary(days, "c")

        assertEquals(
            "未来5日温度 18°–30°，降水天数较多，昼夜温差大，注意增减衣物。",
            summary,
        )
    }

    @Test
    fun `summary falls back gracefully without temperatures`() {
        val days = listOf(
            DailyWeather(dateMillis = 0L, condition = WeatherCondition.CLEAR),
            DailyWeather(dateMillis = 1L, condition = WeatherCondition.CLEAR),
        )

        assertEquals("多日温度暂未提供，请稍后刷新。", buildDailyTrendSummary(days, "c"))
    }

    @Test
    fun `summary picks a single value when the window is nearly flat`() {
        val days = listOf(
            day(high = 25.2, low = 25.0),
            day(high = 25.3, low = 25.1),
            day(high = 25.4, low = 25.2),
        )

        assertEquals("未来3日温度 25°，无明显降水，气温相对平稳。", buildDailyTrendSummary(days, "c"))
    }

    @Test
    fun `column hit test maps the inset plot area onto the day count`() {
        // 5 天、宽 1000、左右各 32 的留白：x=32 → 第 0 天，x=968 → 第 4 天。
        assertEquals(0, columnAt(32f, 1000f, 32f, 5))
        assertEquals(4, columnAt(968f, 1000f, 32f, 5))
        assertEquals(2, columnAt(500f, 1000f, 32f, 5))
        // 点击留白外仍被钳到最近的一天。
        assertEquals(0, columnAt(0f, 1000f, 32f, 5))
        assertEquals(4, columnAt(1000f, 1000f, 32f, 5))
    }

    @Test
    fun `column hit test handles degenerate windows`() {
        assertEquals(0, columnAt(500f, 1000f, 32f, 1))
        assertNull(columnAt(-1f, 1000f, 32f, 5))
        assertNull(columnAt(500f, 0f, 32f, 5))
    }

    private fun day(
        high: Double,
        low: Double,
        condition: WeatherCondition = WeatherCondition.CLEAR,
        prob: Int? = null,
    ) = DailyWeather(
        dateMillis = 0L,
        high = high,
        low = low,
        condition = condition,
        precipProbability = prob,
    )
}
