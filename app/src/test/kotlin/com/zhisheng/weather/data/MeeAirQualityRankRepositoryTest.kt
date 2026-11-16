package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeeAirQualityRankRepositoryTest {
    private fun rows(
        count: Int,
        time: String = "2026年11月15日 10时",
        aqiFor: (Int) -> Int = { it },
        nameFor: (Int) -> String = { "测试城市${it}市" },
    ): String = (1..count).joinToString("\n") { i ->
        val name = nameFor(i)
        """<a href="javascript:openWin2(&#39;$name&#39;, &#39;$time&#39;, &#39;$i&#39;);"><span><i class="ii">${aqiFor(i)}</i></span></a>"""
    }

    private fun batchWithTarget(targetAqi: Int = 42): String = rows(
        count = 120,
        nameFor = { if (it == 42) "茂名市" else "测试城市${it}市" },
        aqiFor = { if (it == 42) targetAqi else it },
    )

    @Test
    fun ranksCityByAqiWithinOnePublishedBatch() {
        val result = MeeAirQualityRankRepository.parse(batchWithTarget(), "茂名市")
        assertTrue(result is MeeAirQualityRankRepository.Result.Available)
        val available = result as MeeAirQualityRankRepository.Result.Available
        // AQI 小于 42 的城市有 41 个，名次 = 41 + 1。
        assertEquals(42, available.rank)
        assertEquals(120, available.total)
        assertEquals(35, available.topPercent)
        assertTrue(available.reportedAtMillis > 0L)
    }

    @Test
    fun cityNameIsMatchedAfterNormalizingSuffix() {
        val result = MeeAirQualityRankRepository.parse(batchWithTarget(), "茂名")
        assertTrue(result is MeeAirQualityRankRepository.Result.Available)
    }

    @Test
    fun cityMissingFromPublishedBatchIsReportedSeparately() {
        val result = MeeAirQualityRankRepository.parse(batchWithTarget(), "不存在市")
        assertEquals(MeeAirQualityRankRepository.Result.CityNotCovered, result)
    }

    @Test
    fun shortBatchOrUnparseablePageIsUnavailable() {
        assertEquals(
            MeeAirQualityRankRepository.Result.Unavailable,
            MeeAirQualityRankRepository.parse(rows(50), "茂名市"),
        )
        assertEquals(
            MeeAirQualityRankRepository.Result.Unavailable,
            MeeAirQualityRankRepository.parse("<html>页面已改版</html>", "茂名市"),
        )
    }

    @Test
    fun staleRowsFromAnotherReportTimeAreIgnored() {
        val html = batchWithTarget() + "\n" + rows(5, time = "2026年11月15日 09时", nameFor = { "旧批次${it}市" })
        val result = MeeAirQualityRankRepository.parse(html, "茂名市")
        val available = result as MeeAirQualityRankRepository.Result.Available
        assertEquals(120, available.total)
        assertEquals(42, available.rank)
    }

    @Test
    fun normalizeDropsAdministrativeSuffixesAndSpaces() {
        assertEquals("茂名", MeeAirQualityRankRepository.normalize(" 茂名市 "))
        assertEquals("大兴安岭", MeeAirQualityRankRepository.normalize("大兴安岭地区"))
    }

    @Test
    fun districtFallsBackToItsUpperCity() {
        val html = rows(
            count = 120,
            nameFor = { if (it == 42) "深圳市" else "测试城市${it}市" },
        )
        val result = MeeAirQualityRankRepository.parse(html, listOf("光明区", "深圳市", "广东省"))
        val available = result as MeeAirQualityRankRepository.Result.Available
        assertEquals(42, available.rank)
        assertEquals(120, available.total)
        assertEquals("深圳市", available.matchedCity)
        assertEquals("光明区", available.requestedCity)
    }

    @Test
    fun exactMatchWinsOverLooseMatchAcrossCandidates() {
        // 「测试」会模糊命中「测试城市42市」，但候选里的「茂名市」能精确命中，应优先采用。
        val result = MeeAirQualityRankRepository.parse(batchWithTarget(), listOf("测试", "茂名市"))
        val available = result as MeeAirQualityRankRepository.Result.Available
        assertEquals("茂名市", available.matchedCity)
        assertEquals(42, available.rank)
    }

    @Test
    fun candidateChainWalksUpFromDistrictToProvince() {
        assertEquals(
            listOf("光明区", "深圳市", "广东省"),
            MeeAirQualityRankRepository.rankCandidateChain("光明区", "广东省·深圳市"),
        )
        assertEquals(
            listOf("朝阳区", "北京市"),
            MeeAirQualityRankRepository.rankCandidateChain("朝阳区", "北京市"),
        )
        assertEquals(
            listOf("茂名市"),
            MeeAirQualityRankRepository.rankCandidateChain("茂名市", null),
        )
    }

    @Test
    fun emptyCandidateListIsNotCoveredInsteadOfUnavailable() {
        assertEquals(
            MeeAirQualityRankRepository.Result.CityNotCovered,
            MeeAirQualityRankRepository.parse(batchWithTarget(), listOf("  ", "")),
        )
    }
}
