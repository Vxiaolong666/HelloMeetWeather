package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengRed
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
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

/** 附近城市的空气质量徽标（城市名 + AQI；AQI 为 null 表示暂无数据；坐标为 null 时不出现在地图上）。 */
data class NearbyCityAqi(
    val cityName: String,
    val aqi: Int?,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

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

    // 不透明页面背景 + 状态栏/导航栏内边距：防止下层首页（TopBar/城市名）透出。
    Box(Modifier.fillMaxSize().zhishengScreen()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            FeaturePageHeader("空气质量", subtitle = publishLabel, onBack = onBack)

            // 头部：城市 + 发布时间
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(cityName ?: "当前位置", style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                Spacer(Modifier.height(3.dp))
                Text(publishLabel, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
            }

            Spacer(Modifier.height(8.dp))

            // 大数字 + 等级 + 建议 + 污染物
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        aqi?.value?.toString() ?: "—",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = aqiColor(aqi?.value),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
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
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    aqiAdvice(aqi?.value, aqi?.level, aqi?.suggest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
                Spacer(Modifier.height(18.dp))
                PollutantGrid(aqi)
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
                    )
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

/** AQI 国标六档：优/良/轻度/中度/重度/严重 → 0..5。 */
private fun aqiBand(aqi: Int?): Int = when {
    aqi == null -> 0
    aqi <= 50 -> 0
    aqi <= 100 -> 1
    aqi <= 150 -> 2
    aqi <= 200 -> 3
    aqi <= 300 -> 4
    else -> 5
}

private fun aqiBandColor(band: Int): Color = when (band) {
    1 -> Color(0xFFE0C24B) // 良
    2 -> Color(0xFFE87A2C) // 轻度污染
    3 -> Color(0xFFD94141) // 中度污染
    4 -> Color(0xFF8E3AA0) // 重度污染
    5 -> Color(0xFF6E1F1F) // 严重污染
    else -> Color(0xFF2FA06B) // 优
}

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
                circleColor(aqiBandColor(band).toArgb()),
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

@Composable
private fun PollutantGrid(aqi: AqiInfo?) {
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
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            value ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ZhishengMint,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                    }
                }
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
                    color = if (selected) ZhishengText else ZhishengTextSecondary,
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
    AqiBars(points, sparseLabels = true)
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
    AqiBars(points, sparseLabels = false)
}

@Composable
private fun AqiBars(items: List<AqiBarPoint>, sparseLabels: Boolean) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { index, point ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val barHeight = ((point.aqi / 300f).coerceIn(0.06f, 1f) * 120).dp
                Box(
                    Modifier
                        .width(16.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(aqiColor(point.aqi)),
                )
                Spacer(Modifier.height(6.dp))
                val show = !sparseLabels || index == 0 || index % 4 == 0 || index == items.lastIndex
                Text(
                    if (show) point.label else "",
                    fontSize = 9.sp,
                    color = ZhishengTextSecondary,
                    maxLines = 1,
                )
            }
        }
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
private fun aqiColor(value: Int?): Color = when {
    value == null -> ZhishengTextTertiary
    value <= 50 -> ZhishengMint
    value <= 100 -> ZhishengMint
    value <= 150 -> ZhishengOrange
    value <= 200 -> ZhishengOrange
    value <= 300 -> ZhishengRed
    else -> ZhishengRed
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
