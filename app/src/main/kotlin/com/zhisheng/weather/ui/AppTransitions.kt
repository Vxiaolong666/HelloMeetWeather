package com.zhisheng.weather.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import ui.feice.mochi.anim.MochiSprings
import ui.feice.mochi.navigation.MochiNavDefaults
import ui.feice.mochi.navigation.MochiNavSpec

/**
 * 全站页面进入 / 返回 / 退出 / 转场的唯一入口。
 *
 * 标准实现是「设置 → 生活指数数据」：来源行用 `mochiNavSource` 登记卡片，push 经 [pushFrom]
 * 把来源 key 交给库导航（[AppTransitions.navSpec] 生成 [MochiNavSpec]），页面外壳用
 * MochiPageFrame / MochiSettingsScaffold，顶栏返回用库的 MochiBackButton。
 * 所有新页面必须经由此类与上述统一组件接入转场，不得自写转场动画或自拼返回按钮；
 * 页面内部的元素级动画（详情展开、提示条淡入淡出）不在此列，不受本契约约束。
 */
internal object AppTransitions {

    /** 富逐日详情页的来源 key：多日天气预报页上按天登记的「查看该日完整详情」卡片。
     * 含城市后缀防跨城串位；key 与 DailyTrendScreen 的 mochiNavSource 登记完全一致。 */
    fun dailyTrendDaySourceKey(dateMillis: Long, cityKey: String?): String {
        val suffix = cityKey?.let { ":$it" } ?: ""
        return "daily_trend_day:$dateMillis$suffix"
    }

    /**
     * 「打开应用式」下没有可返回的已登记来源卡片时的角落容器变换哨兵 key：
     * 库导航收到未登记的 key 会把整页向角落小卡片插值（进入从左上角展开，
     * 返回按发起侧缩回左上 / 右上角），而不是原地缩放。
     */
    const val CORNER_KEY = "app.corner"

    /**
     * push / setTopSource 时交给库的来源 key：「打开应用式」优先用真实登记 key，
     * 没有来源卡片的页面兜底成 [CORNER_KEY] 走角落变换；其余样式返回 null 走库的侧推。
     */
    fun pushFrom(style: PageTransitionStyle, dest: AppScreen, cityKey: String?): String? =
        if (style == PageTransitionStyle.APP) dest.navSourceKey(cityKey) ?: CORNER_KEY else null

    /**
     * 「应用转场」是全站页面转场的唯一契约：弹簧、下层页缩放、压暗都按当前样式生成，
     * 偏好 / 系统动画开关一变就重建 spec，下一次打开或返回立即采用新设置。
     * 「打开应用式」额外开启库的角落容器变换（cornerWhenNoSource），没有登记来源的页面
     * 也按「从小卡展开 / 缩回小卡」进出。
     */
    @Composable
    fun navSpec(prefs: MochiUiPrefs, systemMotion: Boolean = true): MochiNavSpec {
        val style = PageTransitionStyle.from(prefs.pageTransition)
        val instant = tween<Float>(0)
        // 无动画：所有弹簧瞬时，且不缩放、不压暗，任何返回入口都不会出现中间帧。
        if (!appTransitionsEnabled(style, systemMotion)) {
            return MochiNavDefaults.spec(
                open = instant,
                close = instant,
                cancel = instant,
                push = instant,
                openMove = instant,
                closeMove = instant,
                underScale = 1f,
                backScale = 1f,
                scrim = Color.Transparent,
            )
        }
        val spring: AnimationSpec<Float> = when (style) {
            PageTransitionStyle.SLIDE -> MochiSprings.crisp(320, visibilityThreshold = 0.0005f)
            PageTransitionStyle.DEPTH -> MochiSprings.smooth(420, visibilityThreshold = 0.0005f)
            PageTransitionStyle.APP -> MochiSprings.smooth(380, extraBounce = 0.08, visibilityThreshold = 0.0005f)
            else -> MochiSprings.smooth(300, visibilityThreshold = 0.0005f)
        }
        val scale = when (prefs.backScale) {
            "none" -> 1f
            "deep" -> 0.88f
            else -> 0.95f
        }
        return MochiNavDefaults.spec(
            open = spring,
            close = spring,
            cancel = spring,
            push = spring,
            openMove = spring,
            closeMove = spring,
            // 「下层页缩放」作用在来源转场的背景页（underScale），而不是预测性返回的手势缩放。
            // 交叉淡变不缩放，景深再收一点，各自与设置里的样式对应。
            underScale = when (style) {
                PageTransitionStyle.FADE -> 1f
                PageTransitionStyle.DEPTH -> minOf(scale, 0.92f)
                else -> scale
            },
            backScale = 0.86f,
            cornerWhenNoSource = style == PageTransitionStyle.APP,
        )
    }

    /**
     * 非库导航的整页切换（横竖屏待机、首次定位引导的阶段切换）用的内容转场：
     * 弹簧与库导航同一条（MochiSprings.smooth(300)），样式映射与 [navSpec] 一致，
     * 保证同一个「应用转场」设置在库导航和 AnimatedContent 里观感相同。
     */
    fun contentTransform(
        motionEnabled: Boolean,
        style: PageTransitionStyle,
        swipeBack: Boolean,
    ): ContentTransform {
        if (!motionEnabled || style == PageTransitionStyle.NONE) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val spring = MochiSprings.smooth(300, visibilityThreshold = 0.0005f)
        // 侧推的位移是 IntOffset，用同一条 smooth 弹簧的整数版（阈值按像素收敛）
        val move = MochiSprings.smooth(300, visibilityThreshold = IntOffset.Zero)
        val enterFade = fadeIn(spring)
        val exitFade = fadeOut(spring)
        return when {
            style == PageTransitionStyle.SLIDE && swipeBack ->
                (slideInHorizontally(move) { fullWidth -> fullWidth / 4 } + enterFade) togetherWith
                    (slideOutHorizontally(move) { fullWidth -> -fullWidth / 5 } + exitFade)
            style == PageTransitionStyle.DEPTH ->
                (scaleIn(spring, initialScale = 0.92f) + enterFade) togetherWith
                    (scaleOut(spring, targetScale = 0.92f) + exitFade)
            style == PageTransitionStyle.APP ->
                (scaleIn(spring, initialScale = 0.28f) + enterFade) togetherWith
                    (scaleOut(spring, targetScale = 0.28f) + exitFade)
            else -> enterFade togetherWith exitFade
        }
    }
}
