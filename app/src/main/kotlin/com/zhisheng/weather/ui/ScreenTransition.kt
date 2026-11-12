package com.zhisheng.weather.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import ui.feice.mochi.anim.MochiSprings
import kotlin.math.abs

internal enum class AppScreen {
    HOME, SEARCH, DAILY_FORECAST, DAILY_DAY_DETAIL, HISTORY, RADAR, TYPHOON, PRECIPITATION,
    HOURLY_DETAIL, AIR_QUALITY, YESTERDAY_DETAIL, FEATURES,
    MOCHI_SETTINGS
}

/** 应用转场样式：由「设置 → 外观与设备 → 应用转场与侧推」持久化选择。 */
internal enum class PageTransitionStyle(val key: String, val cn: String) {
    FADE("fade", "交叉淡变"),
    SLIDE("slide", "侧推"),
    DEPTH("depth", "景深"),
    APP("app", "打开应用式"),
    NONE("none", "无动画");

    companion object {
        fun from(key: String?): PageTransitionStyle = entries.firstOrNull { it.key == key } ?: FADE
    }
}

// 全站只使用这一套运动参数：纯层合成（不触发重新测量）。
/*
 * 覆盖层的进出场弹簧必须与库导航（设置页内部 push/pop）完全同一条：
 * MochiNavDefaults.spec() 的 open/close 是 MochiSprings.smooth(300)。
 * 之前这里用 tween(210/160)，设置页返回是 300ms 弹簧、天气页返回是 160ms 淡变，
 * 观感自然像两套互不相干的转场。
 */
internal val overlayMotionSpec: AnimationSpec<Float> =
    MochiSprings.smooth(300, visibilityThreshold = 0.0005f)

private const val STACK_ENTER_MS = 150
private const val STACK_EXIT_MS = 110
private const val OVERLAY_SCALE = 0.985f
private const val DEPTH_SCALE = 0.92f
// 「打开应用式」：从按下的那一点长成整屏，返回时缩回同一点（库导航里的真来源是 mochiNavSource）。
private const val APP_SCALE = 0.28f

/** 下层页（首页）被覆盖层压住时的缩放，取值与设置里的「下层页缩放」一致。 */
internal fun overlayUnderScale(backScale: String): Float = when (backScale) {
    "none" -> 1f
    "deep" -> 0.88f
    else -> 0.95f
}

/** 下层页压暗程度，与 MochiNavDefaults.spec() 的 scrim 默认值一致。 */
internal const val OVERLAY_SCRIM_ALPHA = 0.18f

/** 覆盖层的起始缩放：进度 0 时用它，进度 1 时回到 1。进入与返回严格互为反向。 */
internal fun overlayStartScale(style: PageTransitionStyle): Float = when (style) {
    PageTransitionStyle.DEPTH -> DEPTH_SCALE
    PageTransitionStyle.APP -> APP_SCALE
    else -> OVERLAY_SCALE
}

/** 覆盖层的起始横向位移（相对自身宽度）：只有开着侧推返回的「侧推」样式才横移。 */
internal fun overlayStartShiftFraction(style: PageTransitionStyle, swipeBack: Boolean): Float =
    if (style == PageTransitionStyle.SLIDE && swipeBack) 1f / 3f else 0f

/**
 * 二级页之间：默认一次短交叉淡变；侧推样式采用库导航同款的横向推入，
 * 景深样式用轻微缩放，避免两份重型页面长时间共存。
 */
internal fun screenTransition(
    motionEnabled: Boolean = true,
    style: PageTransitionStyle = PageTransitionStyle.FADE,
    swipeBack: Boolean = true,
): ContentTransform {
    if (!motionEnabled || style == PageTransitionStyle.NONE) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val enterFade = fadeIn(tween(STACK_ENTER_MS, easing = FastOutSlowInEasing))
    val exitFade = fadeOut(tween(STACK_EXIT_MS, easing = FastOutSlowInEasing))
    return when {
        style == PageTransitionStyle.SLIDE && swipeBack ->
            (slideInHorizontally { fullWidth -> fullWidth / 4 } + enterFade) togetherWith
                (slideOutHorizontally { fullWidth -> -fullWidth / 5 } + exitFade)
        style == PageTransitionStyle.DEPTH ->
            (scaleIn(tween(STACK_ENTER_MS, easing = FastOutSlowInEasing), initialScale = DEPTH_SCALE) + enterFade) togetherWith
                (scaleOut(tween(STACK_EXIT_MS, easing = FastOutSlowInEasing), targetScale = DEPTH_SCALE) + exitFade)
        style == PageTransitionStyle.APP ->
            (scaleIn(tween(STACK_ENTER_MS, easing = FastOutSlowInEasing), initialScale = APP_SCALE) + enterFade) togetherWith
                (scaleOut(tween(STACK_EXIT_MS, easing = FastOutSlowInEasing), targetScale = APP_SCALE) + exitFade)
        else -> enterFade togetherWith exitFade
    }
}

/**
 * 主站 overlay 的右滑返回手势。只在左边缘起手、且判定为横向拖动后才消费事件，
 * 因此不会抢走二级页内部的横向列表/图表滚动；手势生效时整页跟随手指右移。
 */
internal fun Modifier.appSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier = composed {
    var raw by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // AwaitPointerEventScope 是受限协程作用域，里面不能调用 Animatable.snapTo 这类挂起函数，
    // 因此位移用状态驱动，松手后由 animateFloatAsState 平滑归位。
    val shown by animateFloatAsState(
        targetValue = if (dragging) raw else 0f,
        animationSpec = tween(if (dragging) 0 else 180),
        label = "app-swipe-back",
    )
    val density = LocalDensity.current
    val edge = with(density) { 40.dp.toPx() }
    val threshold = with(density) { 110.dp.toPx() }
    val slop = LocalViewConfiguration.current.touchSlop
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent(PointerEventPass.Initial)
                    .changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                if (down.position.x > edge) continue
                val startX = down.position.x
                val startY = down.position.y
                val pointerId = down.id
                var tracking = false
                var total = 0f
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial)
                        .changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - change.previousPosition.x
                    val movedX = change.position.x - startX
                    val movedY = change.position.y - startY
                    if (!tracking && abs(movedX) > slop && abs(movedX) > abs(movedY)) {
                        tracking = true
                        dragging = true
                    }
                    if (tracking) {
                        total = (total + dx).coerceAtLeast(0f)
                        raw = total
                        change.consume()
                    }
                }
                if (tracking && total > threshold) onBack()
                dragging = false
                raw = 0f
            }
        }
    }.graphicsLayer { translationX = shown }
}
