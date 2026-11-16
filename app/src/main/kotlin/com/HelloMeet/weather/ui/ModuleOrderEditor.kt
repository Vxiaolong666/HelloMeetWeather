package com.HelloMeet.weather.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.AccentTone
import com.HelloMeet.weather.data.AppIconManager
import com.HelloMeet.weather.data.AppIconStyle
import com.HelloMeet.weather.data.AmbienceLevel
import com.HelloMeet.weather.data.CaiyunApi
import com.HelloMeet.weather.data.HomeModule
import com.HelloMeet.weather.data.HomeBriefingStyle
import com.HelloMeet.weather.data.HomeSurfaceStyle
import com.HelloMeet.weather.data.LocationSource
import com.HelloMeet.weather.data.LifeIndexMetric
import com.HelloMeet.weather.data.LandscapeStandbyStyle
import com.HelloMeet.weather.data.InterfaceStyle
import com.HelloMeet.weather.data.QWeatherApi
import com.HelloMeet.weather.data.SecretStore
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.widget.WidgetConfigActivity
import com.HelloMeet.weather.widget.WidgetStyle
import com.HelloMeet.weather.data.ReleaseFeatures
import com.HelloMeet.weather.data.SourcePref
import com.HelloMeet.weather.data.TelemetryMetric
import com.HelloMeet.weather.data.ThemeMode
import com.HelloMeet.weather.ui.theme.ZhishengBg
import com.HelloMeet.weather.ui.theme.ZhishengCard
import com.HelloMeet.weather.ui.theme.ZhishengCardBorder
import com.HelloMeet.weather.ui.theme.ZhishengCyan
import com.HelloMeet.weather.ui.theme.ZhishengMint
import com.HelloMeet.weather.ui.theme.ZhishengOrange
import com.HelloMeet.weather.ui.theme.ZhishengRed
import com.HelloMeet.weather.ui.theme.ZhishengSurface
import com.HelloMeet.weather.ui.theme.ZhishengText
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import com.HelloMeet.weather.ui.theme.zhishengPanel
import com.HelloMeet.weather.ui.theme.zhishengScreen
import com.HelloMeet.weather.ui.theme.zhishengCompactPanel
import com.HelloMeet.weather.ui.theme.zhishengOverlayColor
import com.HelloMeet.weather.ui.theme.LocalZhishengChrome
import com.HelloMeet.weather.ui.theme.LocalHomeSurfaceStyle
import com.HelloMeet.weather.ui.theme.glassScrollHeader
import com.HelloMeet.weather.ui.theme.isPhosphorVista
import com.HelloMeet.weather.ui.components.HomeBackdrop
import com.HelloMeet.weather.ui.components.LocalHomeBackdrop
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════

// 设置（v0.1.0）
// 数据与位置 → 首页内容 → 外观与设备
// ═══════════════════════════════════════════════════════════

@Composable
internal fun ModuleOrderEditor(
    order: List<HomeModule>,
    onReorder: (List<HomeModule>) -> Unit,
) {
    val movable = order.withIndex().filterNot { !ReleaseFeatures.typhoon && it.value == HomeModule.TYPHOON }
    val modules = movable.map { it.value }
    val bounds = remember { mutableStateMapOf<HomeModule, Rect>() }
    val dragBounds = remember { mutableMapOf<HomeModule, Rect>() }
    var dragging by remember { mutableStateOf<HomeModule?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableStateOf<HomeModule?>(null) }
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            movable.forEachIndexed { displayIndex, entry ->
                val module = entry.value
                val isDragging = dragging == module
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                        .then(
                            if (isDragging) Modifier
                                .shadow(10.dp, RoundedCornerShape(10.dp))
                                .background(ZhishengCard, RoundedCornerShape(10.dp))
                            else Modifier,
                        )
                        .onGloballyPositioned { bounds[module] = it.boundsInParent() }
                        .pointerInput(module) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragBounds.clear()
                                    dragBounds.putAll(bounds)
                                    dragging = module
                                    target = module
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val origin = dragBounds[module]
                                    if (origin != null) {
                                        val centerY = origin.center.y + dragOffset
                                        dragBounds.entries
                                            .firstOrNull { centerY in it.value.top..it.value.bottom }
                                            ?.let { target = it.key }
                                    }
                                },
                                onDragEnd = {
                                    val from = dragging
                                    val to = target
                                    if (from != null && to != null && from != to) {
                                        val fromIndex = order.indexOf(from)
                                        val toIndex = order.indexOf(to)
                                        if (fromIndex >= 0 && toIndex >= 0) {
                                            onReorder(
                                                order.toMutableList().apply {
                                                    add(toIndex, removeAt(fromIndex))
                                                },
                                            )
                                        }
                                    }
                                    dragging = null
                                    target = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragging = null
                                    target = null
                                    dragOffset = 0f
                                },
                            )
                        }
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%02d".format(displayIndex + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengOrange,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    com.HelloMeet.weather.ui.components.PhosphorIcon(
                        homeModuleIcon(module),
                        null,
                        Modifier.size(16.dp),
                        if (displayIndex == 0) ZhishengMint else ZhishengTextSecondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(module.cn, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                        if (!isPhosphorVista) Text(module.en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.sp)
                    }
                    com.HelloMeet.weather.ui.components.PhosphorIcon(
                        R.drawable.ph_dots_six_vertical,
                        "${module.cn}拖动排序",
                        Modifier.padding(end = 6.dp).size(18.dp),
                        if (isDragging) ZhishengMint else ZhishengTextTertiary,
                    )
                }
                if (displayIndex < movable.lastIndex) SettingsDivider()
            }
        }

        val from = dragging
        val to = target
        if (from != null && to != null && from != to) {
            val fromDisplay = modules.indexOf(from)
            val toDisplay = modules.indexOf(to)
            val rect = bounds[to]
            if (fromDisplay >= 0 && toDisplay >= 0 && rect != null) {
                val edge = if (toDisplay > fromDisplay) rect.bottom else rect.top
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, edge.roundToInt() - 1) }
                        .padding(horizontal = 16.dp)
                        .height(2.dp)
                        .background(ZhishengMint, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}


@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = if (isPhosphorVista) Modifier.padding(horizontal = 18.dp) else Modifier,
        thickness = if (isPhosphorVista) 0.5.dp else 1.dp,
        color = ZhishengCardBorder.copy(alpha = if (isPhosphorVista) 0.32f else 1f),
    )
}

