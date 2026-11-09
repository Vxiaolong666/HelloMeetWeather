package com.zhisheng.weather.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.theme.zhishengScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 特色功能页：Open-Meteo 的「独立特色 API」与「预报接口未用字段」集中展示。
 * - 卫星辐射：satellite-api / archive（短波、直接、散射、倾斜辐照、地面辐射、日出日落、昼长日照）
 * - 洪水风险：flood-api / flood（河流流量及分位数）
 * - 预报扩展：api / forecast 未展示的逐时字段（高空风、土壤、辐射、UV、降雪、蒸散、CAPE 等）与逐日明细
 * - 海拔：api / elevation
 * 页面风格与空气质量页一致：FeaturePageHeader + PanelCard。
 */
@Composable
fun FeatureHubScreen(
    cityName: String?,
    latitude: Double,
    longitude: Double,
    onBack: () -> Unit,
) {
    var refreshTick by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(FeatureHubState(loading = true)) }
    LaunchedEffect(latitude, longitude, refreshTick) {
        state = FeatureHubState(loading = true)
        state = FeatureHubFetcher.fetch(latitude, longitude)
    }
    Box(Modifier.fillMaxSize().zhishengScreen()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            FeaturePageHeader(
                title = "特色功能",
                subtitle = (cityName?.let { "$it · " } ?: "") + "Open-Meteo 扩展",
                onBack = onBack,
                trailing = {
                    TextButton(onClick = { refreshTick++ }) {
                        Icon(painterResource(R.drawable.ph_arrow_clockwise), contentDescription = "刷新",
                            tint = ZhishengTextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刷新", color = ZhishengTextSecondary)
                    }
                },
            )
            when {
                state.loading -> LoadingBlock()
                state.error != null -> ErrorBlock(state.error!!) { refreshTick++ }
                else -> FeatureHubContent(state)
            }
        }
    }
}

// —— 展示状态 ——

private data class FeatureHubState(
    val loading: Boolean = true,
    val satellite: SatelliteSection? = null,
    val flood: FloodSection? = null,
    val forecast: ForecastSection? = null,
    val elevation: Double? = null,
    val error: String? = null,
)

private data class SatelliteSection(
    val latest: List<Pair<String, String>>,
    val today: List<Pair<String, String>>,
)

private data class FloodSection(
    val today: List<Pair<String, String>>,
)

private data class ForecastSection(
    val hourly: List<Pair<String, String>>,
    val daily: List<Pair<String, String>>,
)

// —— 取数 ——

private object FeatureHubFetcher {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(lat: Double, lon: Double): FeatureHubState = coroutineScope {
        val satellite = async { getSatellite(lat, lon) }
        val flood = async { getFlood(lat, lon) }
        val forecast = async { getForecast(lat, lon) }
        val elevation = async { getElevation(lat, lon) }
        val s = satellite.await()
        val f = flood.await()
        val fc = forecast.await()
        val e = elevation.await()
        val any = s != null || f != null || fc != null || e != null
        FeatureHubState(
            loading = false,
            satellite = s,
            flood = f,
            forecast = fc,
            elevation = e,
            error = if (any) null else "所有扩展接口均请求失败，请检查网络后重试。",
        )
    }

    private suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            okHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else json.parseToJsonElement(resp.body?.string() ?: "").jsonObject
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fmt(v: Double?, scale: Int = 1): String? = v
        ?.takeIf { it.isFinite() }
        ?.let { "%.${scale}f".format(it) }

    private fun fmtInt(v: Double?): String? = v
        ?.takeIf { it.isFinite() }
        ?.let { it.roundToInt().toString() }

    private fun asPercent(v: Double?): String? = v
        ?.takeIf { it.isFinite() }
        ?.let { "%.0f%%".format(it * 100.0) }

    private suspend fun getSatellite(lat: Double, lon: Double): SatelliteSection? = try {
        val url = "https://satellite-api.open-meteo.com/v1/archive?latitude=$lat&longitude=$lon" +
            "&hourly=shortwave_radiation,direct_radiation,diffuse_radiation," +
            "global_tilted_irradiance,terrestrial_radiation" +
            "&daily=sunrise,sunset,daylight_duration,sunshine_duration,shortwave_radiation_sum" +
            "&forecast_days=2&timezone=auto&timeformat=unixtime"
        val root = getJson(url) ?: return null
        val offset = (root["utc_offset_seconds"] as? JsonPrimitive)?.intOrNull
        val hourly = root["hourly"] as? JsonObject
        val daily = root["daily"] as? JsonObject
        // 卫星归档数据滞后 1-2 小时，取最近一个非空值作为「最新」。
        fun latest(key: String): Double? = ((hourly?.get(key) as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }?.lastOrNull()
        fun today(key: String): Double? = ((daily?.get(key) as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }?.firstOrNull()
        fun todayTime(key: String): Long? = ((daily?.get(key) as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }?.firstOrNull()?.times(1000)
        val latestItems = listOf(
            "短波辐射" to fmt(latest("shortwave_radiation"), 0),
            "直接辐射" to fmt(latest("direct_radiation"), 0),
            "散射辐射" to fmt(latest("diffuse_radiation"), 0),
            "倾斜辐照" to fmt(latest("global_tilted_irradiance"), 0),
            "地面辐射" to fmt(latest("terrestrial_radiation"), 0),
        ).mapNotNull { (k, v) -> v?.let { k to "$it W/m²" } }
        val todayItems = buildList {
            todayTime("sunrise")?.let { add("日出" to Fmt.clock(it, offset)) }
            todayTime("sunset")?.let { add("日落" to Fmt.clock(it, offset)) }
            today("daylight_duration")?.let { add("昼长" to "${fmt(it / 3600.0, 1)} 小时") }
            today("sunshine_duration")?.let { add("日照" to "${fmt(it / 3600.0, 1)} 小时") }
            today("shortwave_radiation_sum")?.let { add("辐射总量" to "$it MJ/m²") }
        }
        if (latestItems.isEmpty() && todayItems.isEmpty()) null else SatelliteSection(latestItems, todayItems)
    } catch (e: Exception) { null }

    private suspend fun getFlood(lat: Double, lon: Double): FloodSection? = try {
        val url = "https://flood-api.open-meteo.com/v1/flood?latitude=$lat&longitude=$lon" +
            "&daily=river_discharge,river_discharge_mean,river_discharge_median," +
            "river_discharge_max,river_discharge_min,river_discharge_p25,river_discharge_p75" +
            "&forecast_days=5&timezone=auto"
        val root = getJson(url) ?: return null
        val daily = root["daily"] as? JsonObject ?: return null
        fun today(key: String): Double? = ((daily[key] as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }?.firstOrNull()
        val items = listOf(
            "今日流量" to fmt(today("river_discharge"), 1),
            "均值" to fmt(today("river_discharge_mean"), 1),
            "中位数" to fmt(today("river_discharge_median"), 1),
            "最大" to fmt(today("river_discharge_max"), 1),
            "最小" to fmt(today("river_discharge_min"), 1),
            "P25" to fmt(today("river_discharge_p25"), 1),
            "P75" to fmt(today("river_discharge_p75"), 1),
        ).mapNotNull { (k, v) -> v?.let { k to "$it m³/s" } }
        if (items.isEmpty()) null else FloodSection(items)
    } catch (e: Exception) { null }

    private suspend fun getForecast(lat: Double, lon: Double): ForecastSection? = try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=shortwave_radiation,direct_radiation,diffuse_radiation,direct_normal_irradiance," +
            "uv_index_clear_sky,wind_speed_80m,wind_speed_120m,wind_speed_180m," +
            "soil_temperature_0cm,soil_temperature_6cm,soil_temperature_18cm,soil_temperature_54cm," +
            "soil_moisture_0_to_1cm,soil_moisture_1_to_3cm,soil_moisture_3_to_9cm," +
            "soil_moisture_9_to_27cm,soil_moisture_27_to_81cm," +
            "snowfall,snow_depth,rain,showers,evapotranspiration,et0_fao_evapotranspiration," +
            "vapour_pressure_deficit,cape,freezing_level_height" +
            "&daily=apparent_temperature_max,apparent_temperature_min,rain_sum,showers_sum," +
            "snowfall_sum,precipitation_hours,daylight_duration,sunshine_duration," +
            "shortwave_radiation_sum,et0_fao_evapotranspiration,uv_index_clear_sky_max," +
            "moonrise,moonset,moon_phase" +
            "&forecast_days=2&timezone=auto&timeformat=unixtime"
        val root = getJson(url) ?: return null
        val offset = (root["utc_offset_seconds"] as? JsonPrimitive)?.intOrNull
        val hourly = root["hourly"] as? JsonObject ?: return null
        val daily = root["daily"] as? JsonObject ?: return null
        // 用时间戳对齐「当前小时」。
        val times = ((hourly["time"] as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.longOrNull } ?: emptyList()
        val nowSec = System.currentTimeMillis() / 1000
        val idx = times.indices.minByOrNull { abs(times[it] - nowSec) } ?: 0
        fun at(key: String): Double? = ((hourly[key] as? JsonArray))?.getOrNull(idx)
            ?.let { (it as? JsonPrimitive)?.doubleOrNull }
        fun today(key: String): Double? = ((daily[key] as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }?.firstOrNull()
        fun todayTime(key: String): Long? = ((daily[key] as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }?.firstOrNull()?.times(1000)
        fun moonPhase(p: Double?): String? = when {
            p == null || !p.isFinite() -> null
            p < 0.03 || p > 0.97 -> "新月"
            p < 0.22 -> "蛾眉月"
            p < 0.28 -> "上弦月"
            p < 0.47 -> "盈凸月"
            p < 0.53 -> "满月"
            p < 0.72 -> "亏凸月"
            p < 0.78 -> "下弦月"
            else -> "残月"
        }
        val hourlyItems = listOf(
            "短波辐射" to fmt(at("shortwave_radiation"), 0),
            "直接辐射" to fmt(at("direct_radiation"), 0),
            "散射辐射" to fmt(at("diffuse_radiation"), 0),
            "法向辐照" to fmt(at("direct_normal_irradiance"), 0),
            "晴空UV" to fmt(at("uv_index_clear_sky"), 1),
            "80m风速" to fmt(at("wind_speed_80m"), 0),
            "120m风速" to fmt(at("wind_speed_120m"), 0),
            "180m风速" to fmt(at("wind_speed_180m"), 0),
            "地温0cm" to fmt(at("soil_temperature_0cm"), 1),
            "地温6cm" to fmt(at("soil_temperature_6cm"), 1),
            "地温18cm" to fmt(at("soil_temperature_18cm"), 1),
            "地温54cm" to fmt(at("soil_temperature_54cm"), 1),
            "湿度0-1cm" to asPercent(at("soil_moisture_0_to_1cm")),
            "湿度1-3cm" to asPercent(at("soil_moisture_1_to_3cm")),
            "湿度3-9cm" to asPercent(at("soil_moisture_3_to_9cm")),
            "湿度9-27cm" to asPercent(at("soil_moisture_9_to_27cm")),
            "湿度27-81cm" to asPercent(at("soil_moisture_27_to_81cm")),
            "降雪量" to fmt(at("snowfall"), 1),
            "积雪深度" to fmt(at("snow_depth"), 1),
            "降雨量" to fmt(at("rain"), 1),
            "阵雨量" to fmt(at("showers"), 1),
            "蒸散发" to fmt(at("evapotranspiration"), 2),
            "ET₀" to fmt(at("et0_fao_evapotranspiration"), 2),
            "饱和差VPD" to fmt(at("vapour_pressure_deficit"), 2),
            "CAPE" to fmtInt(at("cape")),
            "冻结层高度" to fmtInt(at("freezing_level_height")),
        ).mapNotNull { (k, v) -> v?.let { k to it } }
        val dailyItems = buildList {
            today("apparent_temperature_max")?.let { add("体感最高" to "$it°C") }
            today("apparent_temperature_min")?.let { add("体感最低" to "$it°C") }
            today("rain_sum")?.let { add("降雨合计" to "$it mm") }
            today("showers_sum")?.let { add("阵雨合计" to "$it mm") }
            today("snowfall_sum")?.let { add("降雪合计" to "$it cm") }
            today("precipitation_hours")?.let { add("降水时长" to "$it 小时") }
            today("daylight_duration")?.let { add("昼长" to "${fmt(it / 3600.0, 1)} 小时") }
            today("sunshine_duration")?.let { add("日照时长" to "${fmt(it / 3600.0, 1)} 小时") }
            today("shortwave_radiation_sum")?.let { add("辐射总量" to "$it MJ/m²") }
            today("et0_fao_evapotranspiration")?.let { add("ET₀" to "$it mm") }
            today("uv_index_clear_sky_max")?.let { add("晴空UV最高" to "$it") }
            todayTime("moonrise")?.let { add("月出" to Fmt.clock(it, offset)) }
            todayTime("moonset")?.let { add("月落" to Fmt.clock(it, offset)) }
            moonPhase(today("moon_phase"))?.let { add("月相" to it) }
        }
        if (hourlyItems.isEmpty() && dailyItems.isEmpty()) null else ForecastSection(hourlyItems, dailyItems)
    } catch (e: Exception) { null }

    private suspend fun getElevation(lat: Double, lon: Double): Double? = try {
        val url = "https://api.open-meteo.com/v1/elevation?latitude=$lat&longitude=$lon"
        val root = getJson(url) ?: return null
        ((root["elevation"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.doubleOrNull
    } catch (e: Exception) { null }
}

// —— 展示 ——

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ZhishengMint)
            Spacer(Modifier.height(12.dp))
            Text("正在获取特色数据…", color = ZhishengTextSecondary)
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(painterResource(R.drawable.ph_info), contentDescription = null,
                tint = ZhishengOrange, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(10.dp))
            Text(message, color = ZhishengTextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            TextButton(onClick = onRetry) {
                Icon(painterResource(R.drawable.ph_arrow_clockwise), contentDescription = null,
                    tint = ZhishengMint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("重试", color = ZhishengMint)
            }
        }
    }
}

@Composable
private fun FeatureHubContent(state: FeatureHubState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        state.satellite?.let { sat ->
            FeaturePanel("卫星辐射", R.drawable.ph_sun) {
                MetricGrid(sat.latest, hint = "W/m²（最新观测）")
                if (sat.today.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("今日", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                    Spacer(Modifier.height(10.dp))
                    MetricGrid(sat.today)
                }
            }
        }
        state.flood?.let { flood ->
            FeaturePanel("洪水风险", R.drawable.ph_waves) {
                MetricGrid(flood.today, hint = "m³/s（河流流量）")
            }
        }
        state.forecast?.let { fc ->
            FeaturePanel("预报扩展字段", R.drawable.ph_database) {
                Text("逐时（当前小时）", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                Spacer(Modifier.height(10.dp))
                MetricGrid(fc.hourly)
                if (fc.daily.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("逐日（今日）", style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                    Spacer(Modifier.height(10.dp))
                    MetricGrid(fc.daily)
                }
            }
        }
        state.elevation?.let { elev ->
            FeaturePanel("海拔", R.drawable.ph_map_pin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%.0f".format(elev),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = ZhishengMint,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("米", style = MaterialTheme.typography.titleMedium, color = ZhishengTextSecondary)
                }
            }
        }
        if (state.satellite == null && state.flood == null && state.forecast == null && state.elevation == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("部分扩展接口暂无数据。", color = ZhishengTextTertiary)
            }
        }
    }
}

@Composable
private fun FeaturePanel(title: String, iconRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).zhishengPanel()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(iconRes), contentDescription = null,
                    tint = ZhishengOrange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = ZhishengText)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun MetricGrid(items: List<Pair<String, String>>, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            value,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ZhishengMint,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (hint != null) {
        Spacer(Modifier.height(8.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = ZhishengTextTertiary)
    }
}
