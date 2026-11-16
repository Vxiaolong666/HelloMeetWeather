package com.HelloMeet.weather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.HelloMeet.weather.data.AmbienceLevel
import com.HelloMeet.weather.model.WeatherData
import com.HelloMeet.weather.ui.theme.isPhosphorVista

/** Compatibility entry for secondary surfaces. The natural-light renderer is native and shared. */
@Composable
fun WeatherAmbience(
    weather: WeatherData?,
    level: AmbienceLevel,
    modifier: Modifier = Modifier,
    night: Boolean = false,
    parallax: () -> Float = { 0f },
) {
    if (!com.HelloMeet.weather.data.ReleaseFeatures.weatherAtmosphere) return
    if (level == AmbienceLevel.OFF) return
    if (!isPhosphorVista) return
    val now = com.HelloMeet.weather.ui.weatherPresentationTime()
    val minutes = weather?.utcOffsetSeconds?.let { Math.floorMod(now / 60_000L + it / 60L, 1440L).toInt() }
        ?: java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    val state = naturalLightState(weather, night, minutes, now)
    NaturalWeatherBackdrop(state, naturalLightPalette(state, com.HelloMeet.weather.ui.theme.LocalZhishengPalette.current.isLight),
        level, parallax = parallax, modifier = modifier)
}
