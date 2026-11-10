package com.zhisheng.weather.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin

internal enum class AppScreen {
    HOME, SEARCH, DAILY_FORECAST, DAILY_DAY_DETAIL, HISTORY, RADAR, TYPHOON, SETTINGS, ATMOSPHERE_LAB, PRECIPITATION,
    HOURLY_DETAIL, AIR_QUALITY, YESTERDAY_DETAIL, PROVIDER_ACCESS, WEATHER_SOURCE, FEATURES
}

// 全站只使用这一套运动参数：短时长、单一缓动、纯层合成（不触发重新测量）。
private const val ENTER_MS = 210
private const val EXIT_MS = 160
private const val STACK_ENTER_MS = 150
private const val STACK_EXIT_MS = 110
private const val OVERLAY_SCALE = 0.985f

/**
 * 首页 → 二级页：淡入 + 从按下位置极轻微展开。
 * 旧实现把整页缩到 0.9 并叠加 22% 屏宽的横向位移；动画期间首页仍常驻，
 * 加上新旧两份重型页面，三份内容同时组合合成是掉帧主因。
 * 现在缩放接近 1、只做层变换，并用统一缓动与更短的时长。
 */
internal fun overlayEnter(
    motionEnabled: Boolean = true,
    origin: TransformOrigin = TransformOrigin.Center,
): EnterTransition {
    if (!motionEnabled) return EnterTransition.None
    return fadeIn(tween(ENTER_MS, easing = FastOutSlowInEasing)) +
        scaleIn(
            tween(ENTER_MS, easing = FastOutSlowInEasing),
            initialScale = OVERLAY_SCALE,
            transformOrigin = origin,
        )
}

/** 返回首页：与进入严格反向，从同一触点轻微收回。 */
internal fun overlayExit(
    motionEnabled: Boolean = true,
    origin: TransformOrigin = TransformOrigin.Center,
): ExitTransition {
    if (!motionEnabled) return ExitTransition.None
    return fadeOut(tween(EXIT_MS, easing = FastOutSlowInEasing)) +
        scaleOut(
            tween(EXIT_MS, easing = FastOutSlowInEasing),
            targetScale = OVERLAY_SCALE,
            transformOrigin = origin,
        )
}

/**
 * 二级页之间：只做一次短交叉淡入淡出，不再整页横移。
 * 两份重型页面共存时间由 240ms 降到 110ms，显著减少同时组合与合成开销。
 */
internal fun screenTransition(motionEnabled: Boolean = true): ContentTransform {
    if (!motionEnabled) return EnterTransition.None togetherWith ExitTransition.None
    return fadeIn(tween(STACK_ENTER_MS, easing = FastOutSlowInEasing)) togetherWith
        fadeOut(tween(STACK_EXIT_MS, easing = FastOutSlowInEasing))
}
