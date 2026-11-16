package com.HelloMeet.weather.ui.home

import com.HelloMeet.weather.R
import com.HelloMeet.weather.model.CoastalForecast
import com.HelloMeet.weather.model.MarinePoint

/** Keep missing timestamps as gaps; never connect a curve across missing observations. */
internal fun coastalDisplayPoints(points: List<MarinePoint>): List<MarinePoint> = points
    .groupBy { it.timeMillis }.toSortedMap().values.map { sameTime ->
        sameTime.firstOrNull { it.seaLevelM?.isFinite() == true } ?: sameTime.first().copy(seaLevelM = null)
    }

internal fun coastalDisplayFacts(coast: CoastalForecast, unit: String): List<Pair<String, String>> = buildList {
    fun addValue(label: String, value: Double?, suffix: String) {
        value?.takeIf { it.isFinite() && it >= 0 }?.let { add(label to atlasNumber(it, suffix)) }
    }
    // 可正可负（倒置气压计为海面气压响应，单位 m）
    fun addSigned(label: String, value: Double?, suffix: String) {
        value?.takeIf { it.isFinite() }?.let { add(label to atlasNumber(it, suffix)) }
    }
    addValue("有效浪高", coast.waveHeightM, "m")
    addValue("浪周期", coast.wavePeriodSeconds, "s")
    addValue("峰值周期", coast.wavePeakPeriodSeconds, "s")
    addValue("风浪高度", coast.windWaveHeightM, "m")
    addValue("风浪周期", coast.windWavePeriodSeconds, "s")
    addValue("风浪峰值周期", coast.windWavePeakPeriodSeconds, "s")
    addValue("涌浪高度", coast.swellHeightM, "m")
    addValue("涌浪周期", coast.swellPeriodSeconds, "s")
    addValue("涌浪峰值周期", coast.swellWavePeakPeriodSeconds, "s")
    addValue("次级涌浪高度", coast.secondarySwellHeightM, "m")
    addValue("次级涌浪周期", coast.secondarySwellPeriodSeconds, "s")
    addValue("次级涌浪来向", coast.secondarySwellDirectionDeg, "°")
    addValue("三级涌浪高度", coast.tertiarySwellHeightM, "m")
    addValue("三级涌浪周期", coast.tertiarySwellPeriodSeconds, "s")
    addValue("三级涌浪来向", coast.tertiarySwellDirectionDeg, "°")
    addSigned("倒置气压", coast.invertBarometerHeightM, "m")
    coast.seaTemperatureC?.takeIf(Double::isFinite)?.let { add("海温" to atlasTemperature(it, unit)) }
    addValue("海流速度", coast.currentVelocityKmh?.div(3.6), "m/s")
    addValue("风浪来向", coast.windWaveDirectionDeg, "°")
    addValue("涌浪来向", coast.swellDirectionDeg, "°")
}

/** 海况要素图标：标签与 [coastalDisplayFacts] 保持一致。 */
internal fun coastalFactIcon(label: String): Int = when {
    label.endsWith("来向") -> R.drawable.ph_compass
    label.startsWith("海温") -> R.drawable.ph_thermometer
    label.startsWith("海流") -> R.drawable.ph_navigation_arrow
    label.startsWith("峰值周期") || label.startsWith("风浪峰值") || label.startsWith("涌浪峰值") -> R.drawable.ph_wave_sine
    label.startsWith("次级涌浪") -> R.drawable.ph_waves
    label.startsWith("三级涌浪") -> R.drawable.ph_waves
    label.startsWith("风浪") -> R.drawable.ph_wind
    label.startsWith("涌浪") -> R.drawable.ph_wind
    label.startsWith("倒置气压") -> R.drawable.ph_gauge
    label.startsWith("浪") -> R.drawable.ph_gauge
    else -> R.drawable.ph_info
}
