package com.HelloMeet.weather.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndCityListStructureTest {
    private val projectDir = File(requireNotNull(System.getProperty("user.dir")))
    @Test
    fun orientationRestoresWeatherButDoesNotRestoreAnOpenCityDrawer() {
        val home = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/ui/home/HomeScreen.kt").readText()
        assertTrue(home.contains("val sidebar = rememberMochiSidebarState()"))
        assertFalse(home.contains("val drawerState = rememberDrawerState("))
        val activity = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/MainActivity.kt").readText()
        assertTrue(activity.contains("orientationState.SaveableStateProvider"))
    }

    @Test
    fun cityDrawerKeepsActionsVisibleAndCitiesScrollable() {
        val home = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/ui/home/HomeScreen.kt").readText()
        val drawer = home.substringAfter("private fun CityDrawer(").substringBefore("internal fun formatAlertTime")

        assertTrue(drawer.contains("LazyColumn("))
        assertTrue(drawer.contains("Modifier.weight(1f).fillMaxWidth()"))
        assertTrue(drawer.contains("itemsIndexed("))
        assertTrue(drawer.contains("contentDescription = \"添加城市\""))
        assertTrue(drawer.contains("R.drawable.ph_star"))
        assertTrue(drawer.contains("R.drawable.ph_trash"))
        assertTrue(drawer.contains("navigationBarsPadding().padding(top = 8.dp, bottom = 12.dp)"))
        assertTrue(drawer.contains("onDismiss: () -> Unit"))
        assertTrue(drawer.contains("MochiSidebarHeader("))
        assertTrue(drawer.contains("MochiSidebarHeaderButton(onClick = { onDismiss(); onAddCity() })"))
        assertFalse(drawer.contains("uiState.cities.forEachIndexed"))
        // 定位与权限逻辑已抽到 CityLocateActions，侧边栏与独立城市管理页共用同一实现。
        assertTrue(drawer.contains("CityLocateActions("))

        val locate = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/ui/home/CityManagerPage.kt").readText()
        assertTrue(locate.contains("RequestMultiplePermissions()"))
        assertTrue(locate.contains("LocationSource.requestedPermissions(precise = preciseEnabled)"))
        assertFalse(locate.contains("LocationSource.requestedPermissions(precise = true)"))
        assertTrue(locate.contains("定位当前位置"))
    }

    @Test
    fun lastAlertDoesNotLeaveADuplicateBottomGap() {
        val home = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/ui/home/HomeScreen.kt").readText()
        val alerts = home.substringAfter("private fun AlertSection(").substringBefore("private fun hazardStripes")

        assertTrue(alerts.contains("alerts.forEachIndexed { index, alert ->"))
        assertTrue(alerts.contains("if (index == alerts.lastIndex) 0.dp else 8.dp"))
    }

    @Test
    fun citySearchHasAnExplicitLabeledBackControl() {
        val search = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/ui/SearchScreen.kt").readText()

        assertTrue(search.contains("MochiPageFrame("))
        assertTrue(search.contains("title = \"添加地点\""))
        // 返回按钮由库标题脚手架统一渲染并带无障碍标签；这里只校验动作仍然接上。
        assertTrue(search.contains("onBack = { dismissKeyboard(); onBack() }"))
    }

    @Test
    fun citySelectionUsesLibraryThemeRevealInsteadOfHandDrawnMask() {
        val activity = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/MainActivity.kt").readText()

        assertTrue(activity.contains("rememberMochiThemeRevealState()"))
        assertTrue(activity.contains("MochiThemeRevealHost(cityReveal"))
        assertTrue(activity.contains("cityReveal.reveal("))
        assertTrue(activity.contains("origin = Offset(rect.center.x, rect.center.y)"))
        assertFalse(activity.contains("CityExpandRequest"))
        assertFalse(activity.contains("cityExpand"))
    }

    @Test
    fun homeTopBarCityManagerEntryUsesTheSameReveal() {
        val activity = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/MainActivity.kt").readText()
        val entry = activity.substringAfter("onCityManagerClick = {")
            .substringBefore("onScenePage =")

        assertTrue(entry.contains("cityReveal.reveal(change = change)"))

        // 独立城市管理页里「当前定位」和「已添加城市」两组卡片共用同一段点击处理，
        // 都优先走 onSelectAt → cityReveal.reveal，动画不能按分组分叉。
        val list = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/ui/home/CityWeatherList.kt").readText()
        val groups = list.substringAfter("val groups = listOf(")
            .substringBefore("CompositionLocalProvider")
        assertTrue(groups.contains("\"当前定位\""))
        assertTrue(groups.contains("\"已添加城市\""))
        assertEquals(1, groups.split("val at = onSelectAt").size - 1)
        assertFalse(groups.contains("if (label == \"当前定位\")"))
    }
}
