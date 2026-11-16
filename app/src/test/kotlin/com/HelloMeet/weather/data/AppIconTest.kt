package com.HelloMeet.weather.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconTest {
    @Test
    fun defaultIconIsThePreferenceFallback() {
        assertEquals(AppIconStyle.DEFAULT, AppIconStyle.from(null))
        assertEquals(AppIconStyle.DEFAULT, AppIconStyle.from("unknown"))
        // 旧版天气娘 / 深色 / classic / 浅色 图标统一迁移到新的默认图标。
        assertEquals(AppIconStyle.DEFAULT, AppIconStyle.from("character"))
        assertEquals(AppIconStyle.DEFAULT, AppIconStyle.from("dark"))
        assertEquals(AppIconStyle.DEFAULT, AppIconStyle.from("classic"))
        assertEquals(AppIconStyle.DEFAULT, AppIconStyle.from("light"))
        assertEquals(AppIconStyle.COLORFUL, AppIconStyle.from("colorful"))
        assertEquals(AppIconStyle.MINIMAL, AppIconStyle.from("minimal"))
    }

    @Test
    fun launcherAliasesAreMutuallyConfiguredAndSettingsExposeThreeChoices() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val manifest = File(projectDir, "src/main/AndroidManifest.xml").readText()
        val settings = File(
            projectDir,
            "src/main/kotlin/com/HelloMeet/weather/ui/MochiSettingsScreen.kt",
        ).readText()

        assertTrue(manifest.contains("android:name=\".IconDefault\""))
        assertTrue(manifest.contains("android:name=\".IconColorful\""))
        assertTrue(manifest.contains("android:name=\".IconMinimal\""))
        assertFalse(manifest.contains("IconCharacter"))
        assertFalse(manifest.contains("IconClassic"))
        assertFalse(manifest.contains("IconLight"))
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher_colorful\""))
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher_minimal\""))
        assertTrue(manifest.contains("android:targetActivity=\".MainActivity\""))
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(projectDir, "src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) as org.w3c.dom.Element }
            .first { it.getAttribute("android:name") == ".MainActivity" }
        assertEquals(0, main.getElementsByTagName("category").length)
        assertTrue(settings.contains("\"应用图标\""))
        assertEquals(listOf("默认" to "default", "多彩" to "colorful", "简约" to "minimal"),
            AppIconStyle.entries.map { it.cn to it.key })
        assertTrue(settings.contains("AppIconManager.apply(context, selected)"))
    }
}
