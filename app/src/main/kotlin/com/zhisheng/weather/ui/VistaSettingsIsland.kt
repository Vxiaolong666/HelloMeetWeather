package com.zhisheng.weather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** One physical gesture owner: touch drives the lens directly, release commits the page.
 *  The glass body and the moving lens are rendered with the Liquid Glass (Backdrop) library. */
@Composable
internal fun VistaSettingsIsland(selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null, background: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero, belowBarInsetPx: Float = 0f) {
    val labels = listOf("数据", "首页", "外观")
    val icons = listOf(R.drawable.ph_database, R.drawable.ph_sun, R.drawable.ph_sparkle)
    val haptic = LocalHapticFeedback.current
    val selection by rememberUpdatedState(selected)
    val commit by rememberUpdatedState(onSelected)
    val palette = LocalZhishengPalette.current
    val isLight = palette.isLight
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val itemWidth = maxWidth / labels.size
        val itemPixels = with(LocalDensity.current) { itemWidth.toPx() }.coerceAtLeast(1f)
        var touchPosition by remember(itemPixels) { mutableStateOf<Float?>(null) }
        var touchLift by remember { mutableFloatStateOf(0f) }
        var stretch by remember { mutableFloatStateOf(0f) }
        val settlingPosition = animateFloatAsState(touchPosition ?: selection.toFloat(),
            if (touchPosition != null) snap() else spring(dampingRatio = 0.76f, stiffness = 520f),
            label = "玻璃吸附")
        // Direct state while touching avoids even a one-frame animation lag.
        val position = { touchPosition ?: settlingPosition.value }
        val pressure = animateFloatAsState(if (touchPosition != null) 1f else 0f,
            spring(dampingRatio = 0.7f, stiffness = 650f), label = "玻璃按压")
        val elasticStretch = animateFloatAsState(stretch,
            if (touchPosition != null) snap() else spring(dampingRatio = 0.65f, stiffness = 480f),
            label = "玻璃形变复位")
        val lift = animateFloatAsState(touchLift,
            if (touchPosition != null) snap() else spring(dampingRatio = 0.7f, stiffness = 480f),
            label = "玻璃垂直牵引")
        val previewIndex by remember(itemPixels) { derivedStateOf { (touchPosition ?: settlingPosition.value).roundToInt().coerceIn(labels.indices) } }
        val shape = RoundedCornerShape(31.dp)
        val lensShape = RoundedCornerShape(30.dp)

        // The sampled settings screen, exposed to the library as a Backdrop offset to this bar.
        // The content column runs edge-to-edge behind the translucent navigation bar, so the lens
        // rim would otherwise pull the content-layer region below the bar into the glass. Stop the
        // content sampling at the bar's own bottom edge and fall back to the plain page background
        // underneath it. The page still displays the full content layer (SettingsScreen).
        val screenBackdrop = backdrop?.let { layer ->
            remember(layer, background, backdropOrigin, belowBarInsetPx) {
                OffsetScreenBackdrop(layer, background, backdropOrigin, belowBarInsetPx)
            }
        }
        val effectiveBackdrop = screenBackdrop ?: remember { emptyBackdrop() }
        // The tab labels are drawn exactly once, on top of the glass. Refracting a second, hidden
        // copy of them through the lens produced duplicated/overlapping glyphs while dragging.
        // Transparent glass body (previous-session look).
        val containerColor = if (isLight) Color(0xFFEAF5FB).copy(alpha = 0.42f) else Color(0xFF18232E).copy(alpha = 0.58f)

        Box(Modifier.fillMaxWidth().height(62.dp)
            .pointerInput(itemPixels) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var raw = down.position.x / itemPixels - 0.5f
                    var lastStop = raw.roundToInt().coerceIn(labels.indices)
                    touchPosition = resistedTabPosition(raw, labels.lastIndex)
                    touchLift = ((down.position.y - size.height / 2f) * 0.12f).coerceIn(-4.dp.toPx(), 4.dp.toPx())
                    stretch = 0.025f
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.id != down.id && it.pressed }) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            raw = change.position.x / itemPixels - 0.5f
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            touchPosition = resistedTabPosition(raw, labels.lastIndex)
                            touchLift = ((change.position.y - size.height / 2f) * 0.12f).coerceIn(-4.dp.toPx(), 4.dp.toPx())
                            stretch = (abs(tracker.calculateVelocity().x) / itemPixels * 0.015f).coerceIn(0.025f, 0.16f)
                            val stop = raw.roundToInt().coerceIn(labels.indices)
                            if (stop != lastStop) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastStop = stop
                            }
                        }
                        if (released) {
                            // Settle underneath the finger; a quick swipe must not jump an extra page.
                            val target = raw.roundToInt().coerceIn(labels.indices)
                            if (target != selection) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            commit(target)
                        }
                    } finally {
                        touchPosition = null
                        touchLift = 0f
                        stretch = 0f
                    }
                }
            }
            // Glass body: transparent liquid glass (previous-session look). The refraction source
            // stops at the bar's bottom edge, so only the content directly behind the glass is
            // refracted; below it the lens reads the page background instead.
            .drawBackdrop(
                backdrop = effectiveBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(14f.dp.toPx())
                    lens(22f.dp.toPx(), 22f.dp.toPx())
                },
                shadow = { Shadow(radius = 12f.dp, color = Color(0xFF425C70).copy(alpha = 0.5f), alpha = 0.32f) },
                onDrawSurface = { drawRect(containerColor) },
            )
        ) {
            // Moving lens tab: refracts only the page behind it. Labels are painted once above the
            // glass, so dragging cannot create a duplicated copy of the icon and text.
            // At rest it keeps a visible highlight rim + inner shadow so the active tab stays obvious.
            Box(Modifier.width(itemWidth).fillMaxHeight().graphicsLayer {
                translationX = position() * itemPixels
                translationY = lift.value
                scaleX = 1f + pressure.value * 0.035f + elasticStretch.value
                scaleY = 1f + pressure.value * 0.025f - elasticStretch.value * 0.32f
            }.padding(horizontal = 5.dp, vertical = 5.dp)
                .drawBackdrop(
                    backdrop = effectiveBackdrop,
                    shape = { lensShape },
                    effects = {
                        lens(12f.dp.toPx() * pressure.value, 14f.dp.toPx() * pressure.value,
                            chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.3f + 0.4f * pressure.value) },
                    shadow = { Shadow(radius = 6f.dp + 8f.dp * pressure.value, color = Color.Black.copy(alpha = 0.4f), alpha = 0.1f + 0.3f * pressure.value) },
                    innerShadow = { InnerShadow(radius = 6f.dp + 6f.dp * pressure.value, alpha = 0.16f + 0.46f * pressure.value) },
                    onDrawSurface = {
                        val tint = if (isLight) Color.Black.copy(0.06f) else Color.White.copy(0.05f)
                        drawRect(tint, alpha = 1f - pressure.value * 0.6f)
                    },
                )
            )
            // Visible, interactive labels — the only label layer.
            IslandTabRow(labels, icons, selection, previewIndex, position, { pressure.value }, commit, interactive = true,
                modifier = Modifier.fillMaxSize().selectableGroup())
        }
    }
}

/** Draws a pre-recorded [GraphicsLayer] (the settings screen) translated so that the pixel
 *  under this element's root position is aligned with the element — the Backdrop library
 *  supplies each element's own [coordinates], so the container and the moving lens each
 *  sample the correct region of the screen.
 *
 *  [background] fills the whole sampled layer first, so every rim (and a lens dragged to any
 *  tab) always has opaque pixels to refract instead of showing the raw page through the glass.
 *  Only the content layer above the floating bar's bottom edge is drawn on top of it:
 *  [belowBarInset] is the distance from the screen bottom to that edge (system navigation bar
 *  plus the bar's bottom gap). Below it the settings content never enters the refraction. */
private class OffsetScreenBackdrop(
    private val layer: GraphicsLayer,
    private val background: GraphicsLayer?,
    private val origin: Offset,
    private val belowBarInset: Float,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val here = coordinates?.let { runCatching { it.positionInRoot() }.getOrNull() } ?: return
        val offset = origin - here
        // The sampled screen layer may be recycled on a theme change; draw what we can, never crash.
        try {
            translate(offset.x, offset.y) {
                val layerWidth = layer.size.width.toFloat()
                val layerHeight = layer.size.height.toFloat()
                if (layerWidth <= 0f || layerHeight <= 0f) return@translate
                // Opaque page background first, over the layer's *whole* width. The default
                // clipRect bounds (0..size.width) are element-local, but the sampled layer is
                // translated by the element's root offset; clipping to them emptied the backdrop
                // as soon as the lens moved right, and left the left rim without pixels. Both
                // made the raw page content show through the glass.
                val bg = background
                if (bg != null) {
                    val bgWidth = bg.size.width.toFloat()
                    clipRect(left = -bgWidth, top = 0f, right = layerWidth, bottom = layerHeight) {
                        // Mirror the background's right edge into the left padding strip.
                        if (bgWidth > 0f) translate(-bgWidth, 0f) { drawLayer(bg) }
                        drawLayer(bg)
                    }
                }
                // Content layer only above the bar's bottom edge; below it the page background
                // stays visible so the content under the navigation bar is never refracted.
                val clipBottom = (layerHeight - belowBarInset).coerceIn(0f, layerHeight)
                if (clipBottom > 0f) {
                    clipRect(left = 0f, top = 0f, right = layerWidth, bottom = clipBottom) {
                        drawLayer(layer)
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("Backdrop", "screen layer unavailable", t)
        }
    }
}

@Composable
private fun IslandTabRow(
    labels: List<String>,
    icons: List<Int>,
    selection: Int,
    previewIndex: Int,
    position: () -> Float,
    pressure: () -> Float,
    commit: (Int) -> Unit,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalZhishengPalette.current
    Row(modifier) {
        labels.forEachIndexed { index, label ->
            val isPreview = previewIndex == index
            val labelColor by animateColorAsState(
                if (isPreview) palette.text else palette.textSecondary,
                tween(90), label = "设置分类文字$index")
            Box(Modifier.weight(1f).fillMaxHeight()
                // Accessibility/keyboard action remains available. Physical input is owned above.
                .then(if (interactive) Modifier.selectable(
                    selected = selection == index, role = Role.Tab,
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { commit(index) }) else Modifier),
                contentAlignment = Alignment.Center) {
                Column(Modifier.graphicsLayer {
                    val magnification = (1f - abs(position() - index)).coerceIn(0f, 1f) * pressure()
                    scaleX = 1f + magnification * 0.045f
                    scaleY = scaleX
                }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PhosphorIcon(icons[index], null, Modifier.size(20.dp), labelColor)
                    Text(label, color = labelColor, fontSize = 11.sp,
                        fontWeight = if (isPreview) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

