package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.R
import com.zhisheng.weather.data.ChineseAqi2026
import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.zhishengOverlayColor
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.theme.zhishengScreen
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/** 附近城市的空气质量徽标（城市名 + AQI；AQI 为 null 表示暂无数据；坐标为 null 时不出现在地图上）。 */
data class NearbyCityAqi(
    val cityName: String,
    val aqi: Int?,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** 国标 HJ 633 六级：名称、区间、亮/暗两套图表色与健康提示。全页颜色与图例共用这一份。 */
private data class AqiLevel(
    val label: String,
    val short: String,
    val range: String,
    val lightColor: Color,
    val darkColor: Color,
    val advice: String,
)

private val AqiLevels = listOf(
    AqiLevel("优", "优", "0–50", Color(0xFF21A356), Color(0xFF35D95A),
        "空气质量令人满意，适合各类户外活动"),
    AqiLevel("良", "良", "51–100", Color(0xFFEFB800), Color(0xFFFFE14D),
        "空气质量可接受，极少数异常敏感人群应减少户外活动"),
    AqiLevel("轻度污染", "轻度", "101–150", Color(0xFFFF7E00), Color(0xFFFF9E2C),
        "儿童、老年人及心脏病、呼吸系统疾病患者应减少长时间户外锻炼"),
    AqiLevel("中度污染", "中度", "151–200", Color(0xFFE8382B), Color(0xFFFF5A4F),
        "敏感人群避免户外活动，一般人群适当减少户外运动"),
    AqiLevel("重度污染", "重度", "201–300", Color(0xFF8F3F97), Color(0xFFC77BE0),
        "尽量留在室内，外出佩戴口罩并减少开窗"),
    AqiLevel("严重污染", "严重", ">300", Color(0xFF7E0023), Color(0xFFE07A6E),
        "避免户外活动，做好防护并关注官方预警"),
)

private val AqiUnknownColor = Color(0xFF8A949E)

private fun aqiLevelIndex(value: Int?): Int? = when {
    value == null -> null
    value <= 50 -> 0
    value <= 100 -> 1
    value <= 150 -> 2
    value <= 200 -> 3
    value <= 300 -> 4
    else -> 5
}

private fun AqiLevel.color(light: Boolean): Color = if (light) lightColor else darkColor

private fun aqiLevelColor(value: Int?, light: Boolean): Color =
    aqiLevelIndex(value)?.let { AqiLevels[it].color(light) } ?: AqiUnknownColor

/** 空气质量二级详情页：AQI 大数字 + 六项污染物 + 24 小时预报 + 附近城市空气质量。 */
@Composable
fun AirQualityScreen(
    weather: WeatherData?,
    cityName: String?,
    onBack: () -> Unit,
    nearby: List<NearbyCityAqi> = emptyList(),
) {
    val aqi = weather?.aqi
    val utcOffset = weather?.utcOffsetSeconds
    val publishLabel = weather?.updateTime?.let { "${Fmt.clock(it, utcOffset)} 发布" } ?: "更新时间未知"
    var showLegend by remember { mutableStateOf(false) }

    // 不透明页面背景 + 状态栏/导航栏内边距：防止下层首页（TopBar/城市名）透出。
    Box(Modifier.fillMaxSize().zhishengScreen()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            FeaturePageHeader(
                "空气质量",
                subtitle = publishLabel,
                onBack = onBack,
                trailing = {
                    IconButton(onClick = { showLegend = true }, modifier = Modifier.size(44.dp)) {
                        PhosphorIcon(R.drawable.ph_info, "空气质量图例与帮助", Modifier.size(20.dp), ZhishengTextSecondary)
                    }
                },
            )

            // 头部：城市 + 发布时间
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(cityName ?: "当前位置", style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                Spacer(Modifier.height(3.dp))
                Text(publishLabel, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
            }

            Spacer(Modifier.height(8.dp))

            // 参考首页空气质量卡片：大数字 + 等级 + 首要污染物 + 六档污染指数指示器
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        aqi?.value?.toString() ?: "—",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = aqiColor(aqi?.value),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            Fmt.aqiLevelLabel(aqi?.value, aqi?.level),
                            style = MaterialTheme.typography.titleLarge,
                            color = aqiColor(aqi?.value),
                        )
                        Text(
                            if (aqi?.estimated == true) Fmt.AQI_ESTIMATION_LABEL else Fmt.aqiBasisLabel(aqi?.standard),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhishengTextSecondary,
                        )
                        aqi?.primary?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "首要污染物 $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = ZhishengTextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                AqiIndicator(aqi?.value)
                Spacer(Modifier.height(16.dp))
                Text(
                    aqiAdvice(aqi?.value, aqi?.level, aqi?.suggest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
                Spacer(Modifier.height(18.dp))
                PollutantGrid(aqi)
            }

            Spacer(Modifier.height(14.dp))

            // Open-Meteo 空气质量扩展：EU/US AQI、CO₂、甲烷、气溶胶、沙尘、UV、花粉
            PanelCard {
                Text("空气质量扩展指标", style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                Spacer(Modifier.height(14.dp))
                ExtensionGrid(aqi)
                Spacer(Modifier.height(14.dp))
                Text("花粉（CAMS 欧洲域）", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                Spacer(Modifier.height(10.dp))
                PollenGrid(aqi)
                Spacer(Modifier.height(10.dp))
                Text(
                    "EU/US AQI 与 CO₂/甲烷/气溶胶/沙尘/UV 来自 Open-Meteo CAMS；氨与花粉仅欧洲域提供，国内显示为—。",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengTextTertiary,
                )
            }

            Spacer(Modifier.height(14.dp))

            // 24 小时空气质量预报（逐时 / 逐日切换）
            PanelCard {
                var mode by remember { mutableIntStateOf(0) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "24小时空气质量预报",
                        style = MaterialTheme.typography.titleMedium,
                        color = ZhishengText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    IconButton(onClick = { showLegend = true }, modifier = Modifier.size(34.dp)) {
                        PhosphorIcon(R.drawable.ph_info, "查看颜色图例与帮助", Modifier.size(17.dp), ZhishengTextSecondary)
                    }
                    ModeToggle(mode) { mode = it }
                }
                Spacer(Modifier.height(14.dp))
                if (mode == 0) HourlyAqiChart(weather, utcOffset) else DailyAqiChart(weather)
            }

            Spacer(Modifier.height(14.dp))

            // 附近城市空气质量：地图 + 数值徽标排
            PanelCard {
                Text("附近空气质量", style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                Spacer(Modifier.height(14.dp))
                if (nearby.isEmpty()) {
                    Text("暂无附近城市数据", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                } else {
                    val markers = nearby.filter { it.latitude != null && it.longitude != null }
                    if (markers.isNotEmpty()) {
                        NearbyAqiMap(
                            items = markers,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        nearby.take(4).forEach { NearbyBadge(it.cityName, it.aqi, Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
        if (showLegend) AqiLegendDialog { showLegend = false }
    }
}

/** 附近城市 AQI 地图：复用 RadarScreen 的 MapLibre 方式（MapView + AndroidView + 开源底图）。 */
@Composable
private fun NearbyAqiMap(items: List<NearbyCityAqi>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val palette = LocalZhishengPalette.current
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    // 数据类列表按内容相等比较，仅在实际数据变化时重建地图层。
    val markers = remember(items) { items.filter { it.latitude != null && it.longitude != null } }

    val mapView = remember {
        // 只在页面真正打开时加载原生地图引擎，避免每次进程启动都初始化。
        org.maplibre.android.MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { readyMap ->
                readyMap.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isDoubleTapGesturesEnabled = true
                    isQuickZoomGesturesEnabled = true
                    isAttributionEnabled = false
                    isLogoEnabled = false
                }
                mapRef.value = readyMap
            }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // 底图随主题切换重载；加载完成后安装 AQI 标记并居中。
    LaunchedEffect(mapRef.value, palette.isLight, markers) {
        val readyMap = mapRef.value ?: return@LaunchedEffect
        val center = markersCameraCenter(markers)
        readyMap.setStyle(weatherMapBaseStyle(palette)) { style ->
            installNearbyMarkers(style, markers, palette)
            if (center != null) {
                readyMap.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(center).zoom(8.5).build(),
                    ),
                )
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val NEARBY_AQI_SOURCE = "zhisheng-aqi-nearby"
private const val NEARBY_AQI_LAYER = "zhisheng-aqi-nearby-marker"

/** 国标六档分组，与指示器 / 图表 / 图例共用同一套阈值和颜色。 */
private fun aqiBand(aqi: Int?): Int = aqiLevelIndex(aqi) ?: 0

private fun aqiBandColor(band: Int, light: Boolean): Color = AqiLevels[band].color(light)

/**
 * 以城市 AQI 值分档着色。按档分组为独立 source+layer（每档一个固定色），
 * 与 RadarScreen 一致使用 circleColor(Int) 重载，避免 Expression 的 API 差异。
 */
private fun installNearbyMarkers(style: Style, markers: List<NearbyCityAqi>, palette: ZhishengPalette) {
    style.removeLayer(NEARBY_AQI_LAYER)
    style.removeSource(NEARBY_AQI_SOURCE)
    if (markers.isEmpty()) return
    markers.groupBy { aqiBand(it.aqi) }.forEach { (band, group) ->
        val sourceId = "$NEARBY_AQI_SOURCE-$band"
        val layerId = "$NEARBY_AQI_LAYER-$band"
        style.removeLayer(layerId)
        style.removeSource(sourceId)
        val features = group.map { m ->
            Feature.fromGeometry(Point.fromLngLat(m.longitude!!, m.latitude!!))
        }
        style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features)))
        style.addLayer(
            CircleLayer(layerId, sourceId).withProperties(
                circleRadius(9f),
                circleColor(aqiBandColor(band, palette.isLight).toArgb()),
                circleStrokeColor(palette.text.toArgb()),
                circleStrokeWidth(2f),
            ),
        )
    }
}

private fun markersCameraCenter(markers: List<NearbyCityAqi>): LatLng? {
    val points = markers.mapNotNull { m ->
        val lat = m.latitude ?: return@mapNotNull null
        val lng = m.longitude ?: return@mapNotNull null
        lat to lng
    }
    if (points.isEmpty()) return null
    return LatLng(points.map { it.first }.average(), points.map { it.second }.average())
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).zhishengPanel(),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

private data class PollutantTip(
    val name: String,
    val unit: String,
    val limit: Double,
    val description: String,
)

/**
 * 判定依据：GB 3095—2012（及修改单）二级浓度限值、HJ 633—2026 表 3 分指数断点。
 * limit 为 IAQI = 100 对应的二级浓度限值（一小时平均口径），与 App 内
 * ChineseAqi2026 的 AQI 估算使用同一张断点表，避免卡片等级与提示互相矛盾。
 */
private fun pollutantTip(label: String): PollutantTip = when (label) {
    "PM2.5" -> PollutantTip("细颗粒物", "μg/m³", 60.0,
        "粒径小于 2.5 微米，可深入肺泡并进入血液，长期暴露会影响呼吸与心血管系统。")
    "PM10" -> PollutantTip("可吸入颗粒物", "μg/m³", 120.0,
        "可进入呼吸道的颗粒物，浓度升高时易引起咳嗽、咽喉不适与呼吸道炎症。")
    "SO₂" -> PollutantTip("二氧化硫", "μg/m³", 500.0,
        "有刺激性气味的气体，会刺激眼鼻与呼吸道，哮喘人群尤其需要注意。")
    "NO₂" -> PollutantTip("二氧化氮", "μg/m³", 200.0,
        "主要来自燃烧排放，高浓度会刺激肺部并加重呼吸道症状。")
    "O₃" -> PollutantTip("臭氧", "μg/m³", 200.0,
        "近地面臭氧在日照强烈时升高，会刺激眼睛与呼吸道，午后应减少剧烈户外活动。")
    else -> PollutantTip("一氧化碳", "mg/m³", 10.0,
        "一氧化碳是无色、无臭、无味气体，但吸入对人体有十分大的危害。")
}

/** 断点口径：CO 用 mg/m³，其余用 μg/m³；把供应商单位归一到该口径后再判定。 */
private fun normalizedConcentration(label: String, value: Double, unit: String): Pair<Double, String> {
    val milligram = unit.contains("mg", ignoreCase = true)
    return if (label == "CO") {
        (if (milligram) value else value / 1000.0) to "mg/m³"
    } else {
        (if (milligram) value * 1000.0 else value) to "μg/m³"
    }
}

private fun pollutantUnit(label: String, aqi: AqiInfo?): String {
    val key = when (label) {
        "PM2.5" -> "pm2p5"
        "PM10" -> "pm10"
        "SO₂" -> "so2"
        "NO₂" -> "no2"
        "O₃" -> "o3"
        else -> "co"
    }
    return aqi?.pollutantUnits?.get(key) ?: pollutantTip(label).unit
}

private fun pollutantBand(iaqi: Int): Int = when {
    iaqi <= 50 -> 0
    iaqi <= 100 -> 1
    iaqi <= 150 -> 2
    iaqi <= 200 -> 3
    iaqi <= 300 -> 4
    else -> 5
}

private fun formatConcentration(value: Double): String {
    val rounded = (value * 100.0).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

@Composable
private fun PollutantGrid(aqi: AqiInfo?) {
    val tint = aqiColor(aqi?.value)
    var selected by remember { mutableStateOf<String?>(null) }
    val items = listOf(
        "PM2.5" to aqi?.pm25,
        "PM10" to aqi?.pm10,
        "SO₂" to aqi?.so2,
        "NO₂" to aqi?.no2,
        "O₃" to aqi?.o3,
        "CO" to aqi?.co,
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { selected = if (selected == label) null else label },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                value ?: "—",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = tint,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                        }
                        if (selected == label) PollutantTooltip(label, value, aqi) { selected = null }
                    }
                }
            }
        }
    }
}

/**
 * 独立 Popup 窗口：不挂在卡片布局里，不会被页面框架裁剪，也不挤占原有排版。
 * 底部小尖角指向被点击的那一列；判定用分项 IAQI 而非固定阈值的二元判断。
 */
@Composable
private fun PollutantTooltip(label: String, value: String?, aqi: AqiInfo?, onDismiss: () -> Unit) {
    val palette = LocalZhishengPalette.current
    val light = palette.isLight
    val tip = pollutantTip(label)
    val surface = if (light) Color.White else palette.card
    val raw = value?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    val normalized = raw?.let { normalizedConcentration(label, it, pollutantUnit(label, aqi)) }
    val iaqi = normalized?.let { ChineseAqi2026.subIndex(label, it.first) }
    val band = iaqi?.let(::pollutantBand)
    val status = when {
        iaqi == null -> "数据暂缺"
        iaqi > 100 -> "${AqiLevels[band!!].label} · 超标"
        else -> "${AqiLevels[band!!].label} · 达标"
    }
    val statusColor = band?.let { AqiLevels[it].color(light) } ?: ZhishengTextTertiary
    val shape = RoundedCornerShape(20.dp)
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, with(density) { -12.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.widthIn(max = 300.dp)
                    .shadow(10.dp, shape)
                    .clip(shape)
                    .background(surface)
                    .border(1.dp, palette.cardBorder.copy(alpha = 0.5f), shape)
                    .clickable { onDismiss() }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tip.name, style = MaterialTheme.typography.titleMedium,
                        color = ZhishengText, fontWeight = FontWeight.Bold)
                    Text("｜", style = MaterialTheme.typography.titleSmall, color = ZhishengTextTertiary)
                    Text(status, style = MaterialTheme.typography.titleSmall,
                        color = statusColor, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("单位: ${normalized?.second ?: tip.unit}",
                        style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                }
                Text(tip.description, style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary)
                HorizontalDivider(color = ZhishengCardBorder)
                Text(
                    "二级限值 ${formatConcentration(tip.limit)} ${tip.unit} · 分指数 ${iaqi ?: "—"}",
                    style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary,
                )
                Text(
                    normalized?.let { "当前 ${formatConcentration(it.first)} ${it.second}" } ?: "当前读数暂缺",
                    style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary,
                )
                Text(
                    "按 HJ 633—2026 一小时平均断点分级，分指数 >100 即超过国标二级限值；" +
                        "当前为瞬时浓度，仅供参考。",
                    style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary,
                )
            }
            Box(
                Modifier.size(14.dp).offset(y = (-7).dp).rotate(45f)
                    .background(surface)
                    .border(1.dp, palette.cardBorder.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Open-Meteo 扩展指标网格：EU/US AQI、CO₂、甲烷、氨、气溶胶、沙尘、UV。 */
@Composable
private fun ExtensionGrid(aqi: AqiInfo?) {
    val items = listOf(
        "EU AQI" to aqi?.europeanAqi,
        "US AQI" to aqi?.usAqi,
        "CO₂" to aqi?.co2,
        "CH₄" to aqi?.methane,
        "NH₃" to aqi?.ammonia,
        "气溶胶" to aqi?.aerosolOpticalDepth,
        "沙尘" to aqi?.dust,
        "UV指数" to aqi?.uvIndex,
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            value ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ZhishengMint,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                    }
                }
                if (row.size < 4) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 花粉网格（CAMS 欧洲域，6 类；国内为 null → 显示—）。 */
@Composable
private fun PollenGrid(aqi: AqiInfo?) {
    val items = listOf(
        "桤木" to aqi?.alderPollen,
        "桦树" to aqi?.birchPollen,
        "禾草" to aqi?.grassPollen,
        "艾草" to aqi?.mugwortPollen,
        "橄榄" to aqi?.olivePollen,
        "豚草" to aqi?.ragweedPollen,
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            value ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ZhishengMint,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                    }
                }
                if (row.size < 3) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: Int, onMode: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(ZhishengCardBorder.copy(alpha = 0.35f)).padding(3.dp),
    ) {
        listOf("逐时", "逐日").forEachIndexed { index, label ->
            val selected = mode == index
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) ZhishengMint else Color.Transparent)
                    .clickable { onMode(index) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.White else ZhishengTextSecondary,
                )
            }
        }
    }
}

private data class AqiBarPoint(val label: String, val aqi: Int)

@Composable
private fun HourlyAqiChart(weather: WeatherData?, utcOffset: Int?) {
    val points = weather?.hourly.orEmpty()
        .asSequence()
        .filter { it.aqi != null }
        .take(24)
        .mapIndexed { i, h -> AqiBarPoint(if (i == 0) "现在" else Fmt.clock(h.timeMillis, utcOffset), h.aqi!!) }
        .toList()
    if (points.isEmpty()) {
        EmptyChart("暂无逐时 AQI 数据")
        return
    }
    AqiBars(points, sparseLabels = true, selected = remember { mutableIntStateOf(0) })
}

@Composable
private fun DailyAqiChart(weather: WeatherData?) {
    val points = weather?.currentAndFutureDaily().orEmpty()
        .take(7)
        .mapIndexedNotNull { i, d ->
            d.aqi?.let { AqiBarPoint(dayShortLabel(d.dateMillis, weather?.utcOffsetSeconds, i), it) }
        }
    if (points.isEmpty()) {
        EmptyChart("暂无逐日 AQI 数据")
        return
    }
    AqiBars(points, sparseLabels = false, selected = remember { mutableIntStateOf(0) })
}

/**
 * AQI 柱状图：点按或横向拖动选中某一根柱子，顶部气泡给出该时刻的具体数值。
 * 逐时 24 根一屏展示（柱顶数值仅显示选中柱，避免文字挤压），逐日 7 根全部显示数值。
 */
@Composable
private fun AqiBars(items: List<AqiBarPoint>, sparseLabels: Boolean, selected: MutableIntState) {
    val measurer = rememberTextMeasurer()
    val bubbleFill = ZhishengBg
    val bubbleText = ZhishengText
    val valueText = ZhishengTextSecondary
    val light = LocalZhishengPalette.current.isLight
    fun barColor(value: Int): Color = aqiLevelColor(value, light)
    Canvas(
        Modifier.fillMaxWidth().height(140.dp)
            .pointerInput(items.size) {
                fun pick(xPos: Float): Int {
                    val n = items.size
                    if (n <= 1) return 0
                    return ((xPos / size.width.toFloat()) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                }
                detectTapGestures { pos -> selected.intValue = pick(pos.x) }
            }
            .pointerInput(items.size) {
                fun pick(xPos: Float): Int {
                    val n = items.size
                    if (n <= 1) return 0
                    return ((xPos / size.width.toFloat()) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                }
                detectDragGestures { change, dragAmount ->
                    // 只接管横向拖动，纵向手势留给外层滚动。
                    if (abs(dragAmount.x) >= abs(dragAmount.y)) {
                        change.consume()
                        selected.intValue = pick(change.position.x)
                    }
                }
            }
            .semantics { contentDescription = "空气质量柱状图，点按或拖动查看具体数值" },
    ) {
        val n = items.size
        if (n == 0) return@Canvas
        val si = selected.intValue.coerceIn(0, n - 1)
        val top = 24.dp.toPx()
        val base = size.height - 16.dp.toPx()
        val plotHeight = (base - top).coerceAtLeast(1f)
        val ceiling = (items.maxOf { it.aqi } * 1.12f).coerceAtLeast(60f)
        val inset = 4.dp.toPx()
        val slot = (size.width - inset * 2) / n
        val barWidth = (slot * 0.52f).coerceAtMost(14.dp.toPx())

        items.forEachIndexed { index, point ->
            val cx = inset + slot * index + slot / 2f
            val barHeight = ((point.aqi / ceiling).coerceIn(0.04f, 1f) * plotHeight)
            val barTop = base - barHeight
            drawRoundRect(
                color = if (index == si) barColor(point.aqi) else barColor(point.aqi).copy(alpha = 0.72f),
                topLeft = Offset(cx - barWidth / 2f, barTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            if (!sparseLabels || index == si) {
                val label = measurer.measure("${point.aqi}", TextStyle(fontSize = 8.sp, color = barColor(point.aqi)))
                drawText(label, topLeft = Offset(
                    (cx - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)),
                    (barTop - label.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ))
            }
            val showLabel = !sparseLabels || index == 0 || index % 4 == 0 || index == n - 1
            if (showLabel) {
                val label = measurer.measure(point.label, TextStyle(fontSize = 9.sp, color = valueText))
                drawText(label, topLeft = Offset(
                    (cx - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)),
                    base + 3.dp.toPx(),
                ))
            }
        }

        // 选中气泡：时刻 + AQI 具体数值
        val point = items[si]
        val levelLabel = aqiLevelIndex(point.aqi)?.let { AqiLevels[it].label }
        val layout = measurer.measure(
            "${point.label}  AQI ${point.aqi}${levelLabel?.let { " · $it" } ?: ""}",
            TextStyle(fontSize = 9.sp, color = bubbleText),
        )
        val bubbleW = layout.size.width + 16.dp.toPx()
        val bubbleH = layout.size.height + 8.dp.toPx()
        val sx = inset + slot * si + slot / 2f
        val bx = (sx - bubbleW / 2f).coerceIn(1.dp.toPx(), (size.width - bubbleW - 1.dp.toPx()).coerceAtLeast(1.dp.toPx()))
        val by = (top - 6.dp.toPx() - bubbleH).coerceAtLeast(1.dp.toPx())
        drawRoundRect(bubbleFill, Offset(bx, by), Size(bubbleW, bubbleH), CornerRadius(6.dp.toPx()))
        drawRoundRect(
            barColor(point.aqi).copy(alpha = 0.55f), Offset(bx, by), Size(bubbleW, bubbleH),
            CornerRadius(6.dp.toPx()), style = Stroke(0.9.dp.toPx()),
        )
        drawText(layout, topLeft = Offset(bx + 8.dp.toPx(), by + 4.dp.toPx()))
    }
}

@Composable
private fun EmptyChart(message: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
    }
}

@Composable
private fun NearbyBadge(name: String, aqi: Int?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary, maxLines = 1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(aqiColor(aqi).copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                aqi?.toString() ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = aqiColor(aqi),
            )
        }
    }
}

private fun dayShortLabel(dateMillis: Long, utcOffsetSeconds: Int?, index: Int): String {
    val zone: ZoneId = utcOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }
}

@Composable
private fun aqiColor(value: Int?): Color =
    aqiLevelColor(value, LocalZhishengPalette.current.isLight)

/** 六档污染指数指示器：色带按等级分段，当前档高亮并用游标标出。 */
@Composable
private fun AqiIndicator(value: Int?) {
    val light = LocalZhishengPalette.current.isLight
    val index = aqiLevelIndex(value)
    val activeColor = aqiColor(value)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("污染指数", style = MaterialTheme.typography.labelLarge, color = ZhishengText)
            Spacer(Modifier.weight(1f))
            Text(
                if (value == null || index == null) "暂无数据" else "${AqiLevels[index].label} · AQI $value",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = activeColor,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val gap = 3.dp.toPx()
            val segment = (size.width - gap * (AqiLevels.size - 1)) / AqiLevels.size
            val radius = CornerRadius(size.height / 2f)
            AqiLevels.forEachIndexed { i, level ->
                val left = i * (segment + gap)
                val selected = i == index
                drawRoundRect(
                    color = level.color(light).copy(alpha = if (index == null || selected) 0.95f else 0.32f),
                    topLeft = Offset(left, 0f),
                    size = Size(segment, size.height),
                    cornerRadius = radius,
                )
                if (selected) {
                    val cx = left + segment / 2f
                    val cy = size.height / 2f
                    drawCircle(Color.White, radius = size.height * 0.34f, center = Offset(cx, cy))
                    drawCircle(level.color(light), radius = size.height * 0.18f, center = Offset(cx, cy))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            AqiLevels.forEachIndexed { i, level ->
                Text(
                    level.short,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (i == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == index) activeColor else ZhishengTextTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 图例 + 帮助弹窗：六档颜色、区间、健康提示与读数说明。 */
@Composable
private fun AqiLegendDialog(onDismiss: () -> Unit) {
    val palette = LocalZhishengPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = zhishengOverlayColor(),
        title = {
            Text(
                "空气质量图例与帮助",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ZhishengText,
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "AQI（空气质量指数）越大，空气污染程度越高。页面里的大数字、指示器、柱状图和附近城市标记使用同一套六档颜色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengTextSecondary,
                )
                AqiLevels.forEach { level ->
                    val tint = level.color(palette.isLight)
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.padding(top = 3.dp).size(14.dp)
                                .clip(RoundedCornerShape(4.dp)).background(tint),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${level.label} · AQI ${level.range}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = tint,
                            )
                            Text(
                                level.advice,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZhishengTextSecondary,
                            )
                        }
                    }
                }
                HorizontalDivider(color = ZhishengCardBorder)
                Text(
                    "读数与数据说明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ZhishengText,
                )
                listOf(
                    "· 色带按六档等宽排列，当前档高亮，白色圆点标出所处等级。",
                    "· 柱状图可点按或左右拖动，选中柱显示具体 AQI 与等级名称。",
                    "· AQI 口径以卡片标注为准（国标 / 美标等）；带「估算」标记的数值由本地模型推算，仅供参考。",
                    "· 逐时数据来自小时预报，逐日数据来自多日预报，附近城市为收藏城市的最近一次快照。",
                    "· 颜色与健康提示仅用于日常参考，防护措施请以当地官方发布为准。",
                ).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = palette.mint, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

private fun aqiAdvice(value: Int?, level: String?, suggest: String?): String {
    if (!suggest.isNullOrBlank()) return suggest
    return when {
        value == null -> "空气质量数据暂不可用"
        value <= 50 -> "空气很好，快呼吸新鲜空气，拥抱大自然吧"
        value <= 100 -> "空气质量可以接受，适合进行户外活动"
        value <= 150 -> "轻度污染，敏感人群应减少户外活动"
        value <= 200 -> "中度污染，建议减少户外活动，外出佩戴口罩"
        value <= 300 -> "重度污染，尽量留在室内，减少外出"
        else -> "严重污染，避免户外活动，做好防护"
    }
}
