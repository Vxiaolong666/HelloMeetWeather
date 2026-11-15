package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocationStreetLabelTest {
    @Test
    fun combinesSubdistrictAndRoadWithoutRepeatingCity() {
        assertEquals(
            "新华路街道·北京路",
            streetLabel(
                subLocality = "新华路街道",
                thoroughfare = "北京路",
                locality = "金昌市",
                subAdminArea = "金川区",
                cityName = "金川区",
            ),
        )
    }

    @Test
    fun administrativeOnlyResultFallsBackToCity() {
        assertNull(
            streetLabel(
                subLocality = "金川区",
                thoroughfare = null,
                locality = "金昌市",
                subAdminArea = "金川区",
                cityName = "金川区",
            ),
        )
    }

    @Test
    fun systemAddressUsesDistrictWhenStreetIsMissing() {
        val city = systemAddressCity(
            locality = "茂名市", adminArea = "广东省", subAdminArea = "茂南区",
            subLocality = null, thoroughfare = null, featureName = null,
            latitude = 21.6, longitude = 110.9,
        )
        assertEquals("茂名市", city?.name)
        assertEquals("茂南区", city?.street)
        assertEquals("茂名市·茂南区", city?.displayName)
        assertEquals("广东省", city?.affiliation)
    }

    @Test
    fun systemAddressKeepsStreetWhenBothPresent() {
        val city = systemAddressCity(
            locality = "金昌市", adminArea = "甘肃省", subAdminArea = "金川区",
            subLocality = "新华路街道", thoroughfare = "北京路", featureName = null,
            latitude = 38.52, longitude = 102.19,
        )
        assertEquals("金昌市", city?.name)
        assertEquals("新华路街道·北京路", city?.street)
    }

    @Test
    fun cityDisplayKeepsStreetSeparateFromWeatherLookupName() {
        val city = City("金川区", "甘肃省·金昌市", 38.52, 102.19, "101161401", "新华路街道")

        assertEquals("金川区", city.name)
        assertEquals("金川区·新华路街道", city.displayName)
        assertEquals("甘肃省·金昌市 · 新华路街道", city.contextLabel)
    }

    @Test
    fun preciseAddressGetsAStableLocalKeyWithoutLosingProviderKey() {
        val key = preciseLocationKey(32.1234567, 118.7654321)
        val city = City(
            "六合区", "江苏省·南京市", 32.1234567, 118.7654321,
            locationKey = key,
            street = "平顶山路",
            weatherLocationKey = "weathercn:101190107",
        )

        assertEquals("geo:32.123,118.765", key)
        assertTrue(city.isPreciseLocation)
        assertEquals("weathercn:101190107", city.weatherLocationKey)
    }

    @Test
    fun precisePermissionRequestsCoarseAndFineTogether() {
        assertEquals(
            listOf(LocationSource.PERMISSION, LocationSource.PRECISE_PERMISSION),
            LocationSource.requestedPermissions(precise = true).toList(),
        )
    }

    @Test
    fun locationSourceSupportsFusedVendorAndSystemGeocoderFallbacks() {
        val root = File(requireNotNull(System.getProperty("user.dir")))
        val source = File(root, "src/main/kotlin/com/zhisheng/weather/data/LocationSource.kt").readText()

        assertTrue(source.contains("FUSED_PROVIDER"))
        assertTrue(source.contains("lm.allProviders"))
        assertTrue(source.contains("geocodeAddress(context, lat, lon)"))
    }
}
