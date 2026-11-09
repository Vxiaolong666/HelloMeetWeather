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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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

/** Settings bottom tabs rebuilt on the official Liquid Glass (Backdrop) catalog pattern:
 *  `BackdropDemoScaffold` captures ONLY the page background with `layerBackdrop()`, and
 *  `LiquidBottomTabs` draws a capsule glass body plus a moving lens over that backdrop.
 *
 *  The scrolling settings content is deliberately NOT part of the backdrop. Sampling a layer
 *  that is itself being recorded nests a RenderNode inside itself (crash), and any transparent
 *  gap in a content layer lets raw list text show through the glass. An opaque background
 *  backdrop keeps the bar fully covered. */
@Composable
internal fun VistaSettingsIsland(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val labels = listOf("数据", "首页", "外观", "关于")
    val icons = listOf(R.drawable.ph_database, R.drawable.ph_sun, R.drawable.ph_sparkle, R.drawable.ph_info)
    val haptic = LocalHapticFeedback.current
    val selection by rememberUpdatedState(selected)
    val commit by rememberUpdatedState(onSelected)
    val palette = LocalZhishengPalette.current
    val isLight = palette.isLight
    val fallbackBackdrop = remember { emptyBackdrop() }
    val pageBackdrop = backdrop ?: fallbackBackdrop
    // Keep the wash thin: the refracted page content must stay visible through the glass.
    // A heavy white overlay is what makes a Liquid Glass bar look like an opaque frosted slab.
    val containerColor = if (isLight) Color.White.copy(alpha = 0.14f)
        else Color(0xFF18232E).copy(alpha = 0.18f)

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
            // Glass body — official LiquidBottomTabs material: vibrancy, 8dp blur, 24dp lens.
            .drawBackdrop(
                backdrop = pageBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                shadow = { Shadow(radius = 12f.dp, color = Color(0xFF425C70).copy(alpha = 0.5f), alpha = 0.32f) },
                onDrawSurface = { drawRect(containerColor) },
            )
        ) {
            // Moving lens — official: pressure-driven lens with dispersion, specular highlight,
            // drop shadow and inner shadow. Only the opaque page backdrop is refracted.
            Box(Modifier.width(itemWidth).fillMaxHeight().graphicsLayer {
                translationX = position() * itemPixels
                translationY = lift.value
                scaleX = 1f + pressure.value * 0.035f + elasticStretch.value
                scaleY = 1f + pressure.value * 0.025f - elasticStretch.value * 0.32f
            }.padding(horizontal = 5.dp, vertical = 5.dp)
                .drawBackdrop(
                    backdrop = pageBackdrop,
                    shape = { lensShape },
                    effects = {
                        val progress = pressure.value
                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress,
                            chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.3f + 0.5f * pressure.value) },
                    shadow = { Shadow(radius = 6f.dp + 8f.dp * pressure.value, color = Color.Black.copy(alpha = 0.4f), alpha = 0.1f + 0.3f * pressure.value) },
                    innerShadow = { InnerShadow(radius = 6f.dp + 6f.dp * pressure.value, alpha = 0.16f + 0.46f * pressure.value) },
                    onDrawSurface = {
                        val tint = if (isLight) Color.White.copy(0.08f) else Color.Black.copy(0.08f)
                        drawRect(tint, alpha = 1f - pressure.value * 0.5f)
                    },
                )
            )
            // Exactly one label layer. The catalog's hidden tinted copy is intentionally omitted:
            // refracting a second copy of the glyphs produced overlapping text while dragging.
            IslandTabRow(labels, icons, selection, previewIndex, position, { pressure.value }, commit,
                interactive = true, modifier = Modifier.fillMaxSize().selectableGroup())
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
