package com.zhisheng.weather.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** The opaque page backdrop captured by layerBackdrop(), shared by the header, bottom tabs,
 *  switches, segmented tabs and dialogs. It never contains the scrolling settings content. */
@Immutable
internal data class SettingsGlassScope(val backdrop: Backdrop)

internal val LocalSettingsGlass = compositionLocalOf<SettingsGlassScope?> { null }

/** Dialogs live in their own window, so root-relative sampling is meaningless there. */
private class StaticGlassBackdrop(private val light: Boolean) : Backdrop {
    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        // Semi-transparent so small controls still feel like glass rather than painted colour.
        drawRect(Brush.verticalGradient(
            if (light) listOf(Color(0xFFE8F3FA).copy(alpha = 0.72f), Color(0xFFC4DCEB).copy(alpha = 0.72f))
            else listOf(Color(0xFF1B2C3C).copy(alpha = 0.72f), Color(0xFF0E1A27).copy(alpha = 0.72f)),
        ))
    }
}

/** One Liquid Glass material for every settings control: vibrancy, blur, edge refraction,
 *  specular rim, drop shadow and an inner shadow. */
@Composable
internal fun Modifier.settingsLiquidGlass(
    shape: Shape,
    surfaceColor: Color? = null,
    refraction: Dp = 8.dp,
    blurRadius: Dp = 6.dp,
    highlightAlpha: Float = 0.5f,
    // Controls inside the captured page must NOT sample that page while it is being recorded.
    staticBackdrop: Boolean = true,
): Modifier {
    val scope = LocalSettingsGlass.current
    val light = LocalZhishengPalette.current.isLight
    val backdrop = remember(scope, staticBackdrop, light) {
        if (staticBackdrop || scope == null) StaticGlassBackdrop(light) else scope.backdrop
    }
    val tint = surfaceColor ?: if (light) Color.White.copy(alpha = 0.42f)
        else Color.White.copy(alpha = 0.10f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            if (blurRadius.value > 0f) blur(blurRadius.toPx())
            // lens() only supports rounded rectangles; a rectangular top bar must skip it.
            if (refraction.value > 0f && shape is CornerBasedShape) {
                lens(refraction.toPx(), refraction.toPx())
            }
        },
        highlight = { Highlight.Default.copy(alpha = highlightAlpha) },
        shadow = { Shadow(radius = 6.dp, color = Color.Black.copy(alpha = 0.18f), alpha = 0.45f) },
        innerShadow = { InnerShadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.12f), alpha = 0.35f) },
        onDrawSurface = { drawRect(tint) },
    )
}

/** BackdropFilter for the settings top bar: the captured page content is blurred behind it. */
@Composable
internal fun Modifier.settingsGlassTopBar(): Modifier {
    val scope = LocalSettingsGlass.current
    val light = LocalZhishengPalette.current.isLight
    val backdrop = remember(scope, light) { scope?.backdrop ?: StaticGlassBackdrop(light) }
    // A heavy white wash turns a blurred header into an opaque bar with a hard edge. Keep it thin
    // and fade it out downwards so the frosted content stays visible.
    val top = if (light) Color.White.copy(alpha = 0.16f) else Color(0xFF182736).copy(alpha = 0.22f)
    val bottom = if (light) Color.White.copy(alpha = 0.02f) else Color(0xFF182736).copy(alpha = 0.04f)
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        effects = {
            // Official DialogContent recipe: brighten/saturate the sampled content, then blur.
            colorControls(brightness = 0.10f, saturation = 1.25f)
            blur(16f.dp.toPx())
        },
        onDrawSurface = { drawRect(Brush.verticalGradient(listOf(top, bottom))) },
    )
}

/** Official `DialogContent` Liquid Glass for settings dialogs. A dialog is a separate window,
 *  so it samples its own field instead of relying on the page's CompositionLocal. */
@Composable
internal fun Modifier.settingsGlassDialog(shape: Shape): Modifier {
    val light = LocalZhishengPalette.current.isLight
    val backdrop = remember(light) { StaticGlassBackdrop(light) }
    val containerColor = if (light) Color(0xFFFAFAFA).copy(alpha = 0.62f)
        else Color(0xFF18232E).copy(alpha = 0.62f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(brightness = if (light) 0.15f else -0.08f, saturation = 1.4f)
            blur(16f.dp.toPx())
            if (shape is CornerBasedShape) {
                lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true, chromaticAberration = true)
            }
        },
        highlight = { Highlight.Plain },
        onDrawSurface = { drawRect(containerColor) },
    )
}

/** Floating circular glass button used by the settings header. */
@Composable
internal fun SettingsGlassCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val light = LocalZhishengPalette.current.isLight
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.size(44.dp)
            .settingsLiquidGlass(
                shape = CircleShape,
                surfaceColor = if (light) Color.White.copy(alpha = 0.58f)
                    else Color.White.copy(alpha = 0.14f),
                refraction = 10.dp,
                blurRadius = 6.dp,
                highlightAlpha = 0.75f,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Liquid Glass switch: a frosted capsule track with a glass thumb. Purely visual; the parent
 *  row owns the toggleable semantics. */
@Composable
internal fun SettingsGlassSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalZhishengPalette.current
    val track by animateColorAsState(
        when {
            !enabled -> lerp(palette.card, palette.textSecondary, 0.20f)
            checked -> palette.mint
            else -> lerp(palette.card, palette.textSecondary, 0.20f)
        },
        tween(180), label = "settings-glass-switch-track",
    )
    val position by animateDpAsState(if (checked) 22.dp else 3.dp, tween(180),
        label = "settings-glass-switch-thumb")
    Box(modifier.size(46.dp, 26.dp).clearAndSetSemantics { }) {
        Box(Modifier.fillMaxSize().settingsLiquidGlass(
            shape = CircleShape,
            surfaceColor = track.copy(alpha = if (checked) 0.58f else 0.34f),
            refraction = 4.dp,
            blurRadius = 3.dp,
            highlightAlpha = 0.35f,
        ))
        Box(Modifier.offset(x = position, y = 3.dp).size(20.dp).settingsLiquidGlass(
            shape = CircleShape,
            surfaceColor = if (palette.isLight) Color.White.copy(alpha = 0.92f)
                else Color(0xFFE8F2FA).copy(alpha = 0.86f),
            refraction = 3.dp,
            blurRadius = 2.dp,
            highlightAlpha = 0.9f,
        ))
    }
}
