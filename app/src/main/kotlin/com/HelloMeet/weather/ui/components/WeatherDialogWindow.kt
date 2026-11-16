package com.HelloMeet.weather.ui.components

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.HelloMeet.weather.data.HomeSurfaceStyle
import com.HelloMeet.weather.ui.theme.LocalHomeSurfaceStyle
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette
import com.HelloMeet.weather.ui.theme.isPhosphorVista

/**
 * Full-page details own their bars; overlays draw their own scrim behind transparent bars.
 *
 * @param background 页面自身的底色（例如 mochi 分组背景）。给了就用它刷窗口和状态栏，
 *   免得系统栏颜色和页面顶部不是同一块；null 时沿用旧主题的 `palette.bg`。
 */
@Composable
internal fun WeatherDialogWindow(overlay: Boolean = false, background: Color? = null) {
    val view = LocalView.current
    val palette = LocalZhishengPalette.current
    val light = palette.isLight
    val pageBackground = background ?: if (overlay) Color.Transparent else palette.bg
    val backgroundArgb = pageBackground.toArgb()
    val navigationBackground = if (overlay) backgroundArgb else if (isPhosphorVista &&
        LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
    ) {
        (if (light) Color(0xFFD3E6F1) else Color(0xFF0F1B29)).toArgb()
    } else backgroundArgb
    DisposableEffect(view, light, backgroundArgb, navigationBackground, overlay) {
        val provider = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()
        val window = provider?.window
        if (window != null) {
            fun configureWindow() {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.attributes = window.attributes.apply {
                    setFitInsetsTypes(0)
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            } else if (android.os.Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawable(ColorDrawable(navigationBackground))
            window.statusBarColor = backgroundArgb
            window.navigationBarColor = navigationBackground
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                if (overlay) window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
            }
            configureWindow()
            // Run after Dialog's initial parameter update, which can restore inset fitting.
            val configure = Runnable { configureWindow() }
            view.post(configure)
            // Older Compose DialogLayout writes measured content dimensions to the Window
            // during layout. Keep the window full-size even when content excludes system bars.
            val observer = view.viewTreeObserver
            val keepFullScreen = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                val attributes = window.attributes
                if (attributes.width != WindowManager.LayoutParams.MATCH_PARENT ||
                    attributes.height != WindowManager.LayoutParams.MATCH_PARENT) {
                    window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                }
            }
            observer.addOnGlobalLayoutListener(keepFullScreen)
            onDispose {
                view.removeCallbacks(configure)
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(keepFullScreen)
            }
        } else {
            onDispose { }
        }
    }
}
