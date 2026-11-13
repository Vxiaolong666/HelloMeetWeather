package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCitySwitchRegressionTest {
    private fun homeSource(): String = sequenceOf(
        File("app/src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt"),
        File("src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt"),
    ).first { it.isFile }.readText()

    @Test
    fun deckDragUsesAcceleratedStepSwitching() {
        val source = homeSource()
        // 加速步进：首卡 56dp，之后每跨一张 ×0.8 递减、下限 20dp；整卡动画切换并震动。
        assertTrue(source.contains("56.dp.toPx()"))
        assertTrue(source.contains("20.dp.toPx()"))
        assertTrue(source.contains("0.8f.pow(crossingsThisGesture.toFloat())"))
        assertTrue(source.contains("deckPagerState.animateScrollToPage(target)"))
        assertTrue(source.contains("haptic.performHapticFeedback(HapticFeedbackType.LongPress)"))
        // 不再实时跟随手指，也没有上推锁定态。
        assertFalse(source.contains("deckPagerState.dispatchRawDelta"))
        assertFalse(source.contains("cityDeckPinned"))
    }

    @Test
    fun longPressShowsDeckAndReleaseSelectsFocusedCity() {
        val source = homeSource()
        assertTrue(source.contains("detectDragGesturesAfterLongPress"))
        assertTrue(source.contains("cityDeckVisible = true"))
        assertTrue(source.contains("val targetKey = uiState.cities"))
        assertTrue(source.contains("viewModel.selectCity(targetKey)"))
        assertFalse(source.contains("普通的主页横向滑动仍不会触发城市切换。\n                                cityDeckVisible = false"))
    }

    @Test
    fun shortTapIsBoundedByLongPressTimeoutAndScrollsOnlyTheActiveCity() {
        val source = homeSource()
        assertTrue(source.contains("withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis)"))
        assertTrue(source.contains("waitForUpOrCancellation()"))
        assertTrue(source.contains("if (up != null && !cityDeckVisible) scrollToTopRequest++"))
        assertTrue(source.contains("if (scrollToTopRequest > 0 && selected)"))
        assertTrue(source.contains("weatherListState.animateScrollToItem(0)"))
        assertTrue(source.contains("enabled = uiState.cities.size > 1"))
    }

    @Test
    fun heldDeckCounterClearsTheBottomSensor() {
        val source = homeSource()
        assertTrue(source.contains("modifier = Modifier.padding(bottom = 64.dp)"))
    }

    @Test
    fun deckCardsRenderThroughMochiCarousel() {
        val source = homeSource()
        // 卡片几何、缩放与透明度过渡统一交给 MochiCarousel；自带指示器关闭，
        // 由底部锚点处的 MochiPageControl 承担。
        assertTrue(source.contains("MochiCarousel("))
        assertTrue(source.contains("showPageControl = false"))
        assertTrue(source.contains("state = pagerState"))
        assertTrue(source.contains("MochiPageControl("))
        assertFalse(source.contains("scaleXValue = scale * 1.045f"))
    }

    @Test
    fun cityDeckCardCornersAreDelegatedToCarousel() {
        val source = homeSource()
        // 圆角由 MochiCarousel 的 cornerRadius 统一处理，卡片自身不再双层裁切。
        assertTrue(source.contains("cornerRadius = if (vista) 22.dp else 18.dp"))
        assertFalse(source.contains(".clip(innerShape)"))
    }
}
