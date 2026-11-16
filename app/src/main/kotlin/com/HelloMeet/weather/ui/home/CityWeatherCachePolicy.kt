package com.HelloMeet.weather.ui.home

import com.HelloMeet.weather.data.CachedWeather
import com.HelloMeet.weather.data.SourcePref
import com.HelloMeet.weather.data.isUsableOfflineAt
import com.HelloMeet.weather.data.weatherCoordinatesMatch
import com.HelloMeet.weather.model.City

internal const val CITY_WEATHER_REFRESH_MS = 10 * 60_000L

/** Offline data may remain visible while refreshing, but never survives a source or age mismatch. */
internal fun usableCityWeatherCache(entry: CachedWeather?, source: SourcePref, now: Long,
    configurationCompatible: Boolean? = null, city: City? = null): CachedWeather? =
    entry?.takeIf {
        it.data.current != null && it.data.error == null &&
            (configurationCompatible ?: source.matches(it.data)) && it.isUsableOfflineAt(now) &&
            (city == null || weatherCoordinatesMatch(city, it.data))
    }

internal fun chooseCityWeatherCache(
    memory: CachedWeather?, disk: CachedWeather?, source: SourcePref, now: Long,
): CachedWeather? = listOfNotNull(
    usableCityWeatherCache(memory, source, now), usableCityWeatherCache(disk, source, now),
).maxByOrNull { it.savedAtMillis }

internal fun cityWeatherNeedsRefresh(entry: CachedWeather?, source: SourcePref, now: Long): Boolean =
    usableCityWeatherCache(entry, source, now)?.let {
        now - it.savedAtMillis >= CITY_WEATHER_REFRESH_MS
    } ?: true
