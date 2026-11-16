package com.HelloMeet.weather.ui

import org.junit.Assert.*
import org.junit.Test

class AqiPresentationTest {
    @Test fun modelEstimatesRemainExplicitWithoutRelabellingProviderValues() {
        assertEquals("120（估算）", Fmt.aqiValueLabel(120, true))
        assertEquals("120", Fmt.aqiValueLabel(120))
        assertEquals("模型估算，非监测AQI", Fmt.aqiBasisLabel("美国", true))
        assertEquals("AQI · 美标", Fmt.aqiBasisLabel("美国"))
    }

    @Test fun missingIndexNeverDisplaysZeroOrAnExcellentLevel() {
        assertEquals("—", Fmt.aqiValueLabel(null, true))
        assertEquals("AQI暂无数据", Fmt.aqiLevelLabel(null, "优"))
        assertEquals("优", Fmt.aqiLevelLabel(0, "优"))
        assertEquals("空气质量", Fmt.aqiLevelLabel(40, null))
    }

    @Test fun summaryCombinesFutureRangeYesterdayDeltaAndAdvice() {
        val text = buildAqiSummary(current = 60, future = listOf(42, 88, 65), yesterday = 75)
        assertTrue(text, text.startsWith("未来24小时 AQI 为 42–88，"))
        assertTrue(text, text.contains("较昨日同期降15"))
        // 88 落在「良」档，建议文案与六档表同源。
        assertTrue(text, text.contains("空气质量可接受"))
    }

    @Test fun summaryKeepsHoldingWhenYesterdayIsClose() {
        val text = buildAqiSummary(current = 60, future = listOf(58), yesterday = 62)
        assertTrue(text, text.contains("未来24小时 AQI 为 58–58，"))
        assertTrue(text, text.contains("与昨日同期基本持平"))
    }

    @Test fun summaryExplainsMissingYesterdayAndMissingData() {
        val noYesterday = buildAqiSummary(current = 30, future = listOf(20, 40), yesterday = null)
        assertTrue(noYesterday, noYesterday.contains("昨日同期数据待补充"))
        val empty = buildAqiSummary(current = null, future = emptyList(), yesterday = 50)
        assertTrue(empty, empty.contains("暂未取得完整空气质量数据"))
    }
}
