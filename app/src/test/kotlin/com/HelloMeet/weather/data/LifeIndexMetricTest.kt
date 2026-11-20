package com.HelloMeet.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeIndexMetricTest {
    @Test
    fun newUsersSeeEveryLifeIndexMetric() {
        assertEquals(LifeIndexMetric.entries.toSet(), LifeIndexMetric.selectionFrom(null))
    }

    @Test
    fun selectionRoundTripsInStableDisplayOrder() {
        val selected = setOf(LifeIndexMetric.SUNSCREEN, LifeIndexMetric.CAR_WASH, LifeIndexMetric.COMFORT)
        val stored = LifeIndexMetric.selectionKey(selected)

        val restored = LifeIndexMetric.selectionFrom(stored)
        // 存储里的选择必须原样还原：用户关闭的新增项不能在读取时被补回来。
        assertEquals(selected, restored)
        assertTrue(LifeIndexMetric.MORNING_EXERCISE !in restored)
        assertTrue(stored.startsWith(LifeIndexMetric.CAR_WASH.key))
    }

    @Test
    fun emptySelectionIsPreserved() {
        assertEquals(
            emptySet<LifeIndexMetric>(),
            LifeIndexMetric.selectionFrom(LifeIndexMetric.selectionKey(emptySet())),
        )
    }

    @Test
    fun providerLabelsResolveToSharedMetrics() {
        assertEquals(LifeIndexMetric.AIR_POLLUTION, LifeIndexMetric.fromEnglish("AIR POLLUTION"))
        assertEquals(LifeIndexMetric.AIR_POLLUTION, LifeIndexMetric.fromEnglish("AIR"))
        assertEquals(LifeIndexMetric.SUNGLASSES, LifeIndexMetric.fromEnglish("SUNGLASSES"))
        assertEquals(LifeIndexMetric.SUNGLASSES, LifeIndexMetric.fromEnglish("GLASSES"))
    }

    @Test
    fun weatherComCnKeysResolveToSharedMetrics() {
        assertEquals(LifeIndexMetric.CAR_WASH, LifeIndexMetric.fromEnglish("wcn:xc"))
        assertEquals(LifeIndexMetric.MORNING_EXERCISE, LifeIndexMetric.fromEnglish("wcn:cl"))
        assertEquals(LifeIndexMetric.UMBRELLA, LifeIndexMetric.fromEnglish("wcn:ys"))
        assertEquals(LifeIndexMetric.HEATSTROKE, LifeIndexMetric.fromEnglish("wcn:zs"))
    }
}
