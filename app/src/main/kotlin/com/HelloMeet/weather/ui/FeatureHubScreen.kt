package com.HelloMeet.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.ui.components.PhosphorIcon
import kotlinx.collections.immutable.persistentListOf
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
import kotlin.math.roundToInt
import okhttp3.OkHttpClient
import okhttp3.Request
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiSkeleton
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.rememberMochiNavSettled
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 特色功能页：Open-Meteo 的「独立特色 API」与「预报接口未用字段」集中展示。
 * - 卫星辐射：satellite-api / archive（短波、直接、散射、倾斜辐照、地面辐射、日出日落、昼长日照）
 * - 洪水风险：flood-api / flood（河流流量及分位数 + 逐日柱状图 + 历史分位区间）
 * - 预报扩展：api / forecast 未展示的逐时字段（高空风、土壤、辐射、UV、降雪、蒸散、CAPE 等）与逐日明细
 * - 海拔：api / elevation
 * 每个指标可点按，弹窗展示数值与含义；页面外壳与二级设置页统一使用 mochi 组件库。
 */
@Composable
fun FeatureHubScreen(
    cityName: String?,
    latitude: Double,
    longitude: Double,
    onBack: () -> Unit,
) {
    var refreshTick by remember { mutableIntStateOf(0) }
    var selectedMetric by remember { mutableStateOf<FeatureMetric?>(null) }
    var state by remember { mutableStateOf(FeatureHubState(loading = true)) }
    // 照 demo 的骨架预载：转场没落定前只铺骨架、不发请求，落定后再取数并原位淡入。
    val navSettled by rememberMochiNavSettled()
    LaunchedEffect(latitude, longitude, refreshTick, navSettled) {
        if (!navSettled) return@LaunchedEffect
        state = FeatureHubState(loading = true)
        state = FeatureHubFetcher.fetch(latitude, longitude)
    }
    MochiPageFrame(
        title = "特色功能",
        subtitle = (cityName?.let { "$it · " } ?: "") + "Open-Meteo 扩展",
        onBack = onBack,
        actions = { MochiBarButton("刷新", onClick = { refreshTick++ }) },
        overlay = { MetricMeaningDialog(selectedMetric) { selectedMetric = null } },
    ) {
        when {
            state.loading -> item { LoadingBlock() }
            state.error != null -> item { ErrorBlock(state.error!!) { refreshTick++ } }
            else -> FeatureHubContent(state) { selectedMetric = it }
        }
    }
}

// —— 展示状态 ——

private data class FeatureHubState(
    val loading: Boolean = true,
    val satellite: SatelliteSection? = null,
    val flood: FloodSection? = null,
    val forecast: ForecastSection? = null,
    val climate: ClimateSection? = null,
    val seasonal: SeasonalSection? = null,
    val elevation: Double? = null,
    val error: String? = null,
)

/** 单项指标：label 显示名，value 为带单位的展示串，meaning 为含义（非空时可点按弹窗）。 */
private data class FeatureMetric(
    val label: String,
    val value: String,
    val meaning: String? = null,
)

private data class SatelliteSection(
    val latest: List<FeatureMetric>,
    val today: List<FeatureMetric>,
)

private data class FloodSection(
    val today: List<FeatureMetric>,
    /** 未来逐日流量（可能含 null），用于柱状图。 */
    val days: List<Double?>,
    val discharge: Double?,
    val min: Double?,
    val p25: Double?,
    val median: Double?,
    val p75: Double?,
    val max: Double?,
)

private data class ForecastSection(
    val hourly: List<FeatureMetric>,
    val daily: List<FeatureMetric>,
    val highC: Double? = null,
    val lowC: Double? = null,
)

/** 气候常年同期（1991–2020 均值）与今日预报对比。 */
private data class ClimateSection(
    val normals: List<FeatureMetric>,
    val meanC: Double? = null,
    val highC: Double? = null,
    val lowC: Double? = null,
)

/** 季节集合预报（未来数月逐日均温 + 降水）。 */
private data class SeasonalSection(
    val temps: List<Double?>,
    val precipitation: List<Double?>,
    val summary: String,
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
        val climate = async { getClimate(lat, lon) }
        val seasonal = async { getSeasonal(lat, lon) }
        val elevation = async { getElevation(lat, lon) }
        val s = satellite.await()
        val f = flood.await()
        val fc = forecast.await()
        val cl = climate.await()
        val se = seasonal.await()
        val e = elevation.await()
        val any = s != null || f != null || fc != null || cl != null || se != null || e != null
        FeatureHubState(
            loading = false,
            satellite = s,
            flood = f,
            forecast = fc,
            climate = cl,
            seasonal = se,
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

    private fun metric(label: String, value: String?, meaning: String? = null): FeatureMetric? =
        value?.takeIf(String::isNotBlank)?.let { FeatureMetric(label, it, meaning) }

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
        val latestItems = listOfNotNull(
            metric("短波辐射", fmt(latest("shortwave_radiation"), 0)?.let { "$it W/m²" }, "到达地面的太阳短波辐射总功率。"),
            metric("直接辐射", fmt(latest("direct_radiation"), 0)?.let { "$it W/m²" }, "垂直于阳光方向的地表直接辐射。"),
            metric("散射辐射", fmt(latest("diffuse_radiation"), 0)?.let { "$it W/m²" }, "经大气散射后到达地表的辐射。"),
            metric("倾斜辐照", fmt(latest("global_tilted_irradiance"), 0)?.let { "$it W/m²" }, "倾斜表面接收的全球辐照（光伏常用）。"),
            metric("地面辐射", fmt(latest("terrestrial_radiation"), 0)?.let { "$it W/m²" }, "地球向太空发射的长波辐射。"),
        )
        val todayItems = buildList {
            todayTime("sunrise")?.let { add(FeatureMetric("日出", Fmt.clock(it, offset), "当地日出时刻。")) }
            todayTime("sunset")?.let { add(FeatureMetric("日落", Fmt.clock(it, offset), "当地日落时刻。")) }
            today("daylight_duration")?.let { add(FeatureMetric("昼长", "${fmt(it / 3600.0, 1)} 小时", "白昼总时长。")) }
            today("sunshine_duration")?.let { add(FeatureMetric("日照", "${fmt(it / 3600.0, 1)} 小时", "实际日照时长（受云影响）。")) }
            today("shortwave_radiation_sum")?.let { add(FeatureMetric("辐射总量", "$it MJ/m²", "全天到达地面的太阳辐射总量。")) }
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
        val days = ((daily["river_discharge"] as? JsonArray))
            ?.map { (it as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite) } ?: emptyList()
        fun today(key: String): Double? = ((daily[key] as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }?.firstOrNull()
        val discharge = today("river_discharge")
        val items = listOfNotNull(
            metric("今日流量", fmt(discharge, 1)?.let { "$it m³/s" }, "今日河流流量估算。"),
            metric("均值", fmt(today("river_discharge_mean"), 1)?.let { "$it m³/s" }, "历史同期平均流量。"),
            metric("中位数", fmt(today("river_discharge_median"), 1)?.let { "$it m³/s" }, "历史同期中位流量。"),
            metric("最大", fmt(today("river_discharge_max"), 1)?.let { "$it m³/s" }, "历史同期最大流量。"),
            metric("最小", fmt(today("river_discharge_min"), 1)?.let { "$it m³/s" }, "历史同期最小流量。"),
            metric("P25", fmt(today("river_discharge_p25"), 1)?.let { "$it m³/s" }, "历史同期第 25 百分位流量（偏低区间）。"),
            metric("P75", fmt(today("river_discharge_p75"), 1)?.let { "$it m³/s" }, "历史同期第 75 百分位流量（偏高区间）。"),
        )
        if (items.isEmpty()) null else FloodSection(
            today = items,
            days = days,
            discharge = discharge,
            min = today("river_discharge_min"),
            p25 = today("river_discharge_p25"),
            median = today("river_discharge_median"),
            p75 = today("river_discharge_p75"),
            max = today("river_discharge_max"),
        )
    } catch (e: Exception) { null }

    private suspend fun getForecast(lat: Double, lon: Double): ForecastSection? = try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=shortwave_radiation,direct_radiation,diffuse_radiation,direct_normal_irradiance," +
            "uv_index_clear_sky,wind_speed_80m,wind_speed_120m,wind_speed_180m," +
            "soil_temperature_0cm,soil_temperature_6cm,soil_temperature_18cm,soil_temperature_54cm," +
            "soil_moisture_0_to_1cm,soil_moisture_1_to_3cm,soil_moisture_3_to_9cm," +
            "soil_moisture_9_to_27cm,soil_moisture_27_to_81cm," +
            "snowfall,snow_depth,rain,showers,evapotranspiration,et0_fao_evapotranspiration," +
            "vapour_pressure_deficit,cape,freezing_level_height," +
            "sunshine_duration,pressure_msl,surface_pressure" +
            "&daily=apparent_temperature_max,apparent_temperature_min,rain_sum,showers_sum," +
            "snowfall_sum,precipitation_hours,daylight_duration,sunshine_duration," +
            "shortwave_radiation_sum,et0_fao_evapotranspiration,uv_index_clear_sky_max," +
            "uv_index_max,cloud_cover_mean,wind_gusts_10m_max,pressure_msl_mean," +
            "relative_humidity_2m_mean,wind_speed_10m_mean,snow_depth_max," +
            "temperature_2m_max,temperature_2m_min," +
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
        val hourlyItems = listOfNotNull(
            metric("短波辐射", fmt(at("shortwave_radiation"), 0)?.let { "$it W/m²" }, "当前小时到达地面的太阳短波辐射功率。"),
            metric("直接辐射", fmt(at("direct_radiation"), 0)?.let { "$it W/m²" }, "当前小时垂直于阳光方向的地表直接辐射。"),
            metric("散射辐射", fmt(at("diffuse_radiation"), 0)?.let { "$it W/m²" }, "当前小时经大气散射到达地表的辐射。"),
            metric("法向辐照", fmt(at("direct_normal_irradiance"), 0)?.let { "$it W/m²" }, "当前小时垂直于太阳光线的直接法向辐照度（DNI）。"),
            metric("晴空UV", fmt(at("uv_index_clear_sky"), 1), "当前小时无云条件下的紫外线指数（越大越强）。"),
            metric("日照时长", fmt(at("sunshine_duration"), 0)?.let { "$it 秒" }, "当前小时实际日照秒数。"),
            metric("80m风速", fmt(at("wind_speed_80m"), 0)?.let { "$it km/h" }, "距地 80 米高度的风速。"),
            metric("120m风速", fmt(at("wind_speed_120m"), 0)?.let { "$it km/h" }, "距地 120 米高度的风速。"),
            metric("180m风速", fmt(at("wind_speed_180m"), 0)?.let { "$it km/h" }, "距地 180 米高度的风速（风电机组轮毂附近）。"),
            metric("地温0cm", fmt(at("soil_temperature_0cm"), 1)?.let { "$it °C" }, "地表（0 厘米）土壤温度。"),
            metric("地温6cm", fmt(at("soil_temperature_6cm"), 1)?.let { "$it °C" }, "6 厘米深度土壤温度。"),
            metric("地温18cm", fmt(at("soil_temperature_18cm"), 1)?.let { "$it °C" }, "18 厘米深度土壤温度。"),
            metric("地温54cm", fmt(at("soil_temperature_54cm"), 1)?.let { "$it °C" }, "54 厘米深度土壤温度。"),
            metric("湿度0-1cm", asPercent(at("soil_moisture_0_to_1cm")), "0–1 厘米表层土壤体积含水量。"),
            metric("湿度1-3cm", asPercent(at("soil_moisture_1_to_3cm")), "1–3 厘米土壤体积含水量。"),
            metric("湿度3-9cm", asPercent(at("soil_moisture_3_to_9cm")), "3–9 厘米土壤体积含水量。"),
            metric("湿度9-27cm", asPercent(at("soil_moisture_9_to_27cm")), "9–27 厘米土壤体积含水量。"),
            metric("湿度27-81cm", asPercent(at("soil_moisture_27_to_81cm")), "27–81 厘米土壤体积含水量。"),
            metric("降雪量", fmt(at("snowfall"), 1)?.let { "$it cm" }, "当前小时降雪量（新雪）。"),
            metric("积雪深度", fmt(at("snow_depth"), 1)?.let { "$it cm" }, "当前积雪深度。"),
            metric("降雨量", fmt(at("rain"), 1)?.let { "$it mm" }, "当前小时降雨量。"),
            metric("阵雨量", fmt(at("showers"), 1)?.let { "$it mm" }, "当前小时阵雨量（局地性降水）。"),
            metric("蒸散发", fmt(at("evapotranspiration"), 2)?.let { "$it mm" }, "当前小时实际蒸散发量（地表+植被蒸腾）。"),
            metric("ET₀", fmt(at("et0_fao_evapotranspiration"), 2)?.let { "$it mm" }, "参考作物蒸散发量（FAO 标准）。"),
            metric("饱和差VPD", fmt(at("vapour_pressure_deficit"), 2)?.let { "$it kPa" }, "水汽压饱和差，空气干燥程度指标（越大越干）。"),
            metric("CAPE", fmtInt(at("cape"))?.let { "$it J/kg" }, "对流有效位能，大气不稳定度（越大越易强对流）。"),
            metric("冻结层高度", fmtInt(at("freezing_level_height"))?.let { "$it m" }, "0°C 等温线高度（低于此高度可能降雪）。"),
            metric("海平面气压", fmt(at("pressure_msl"), 1)?.let { "$it hPa" }, "换算到海平面的大气压。"),
            metric("地面气压", fmt(at("surface_pressure"), 1)?.let { "$it hPa" }, "当地海拔处的大气压。"),
        )
        val dailyItems = buildList {
            today("apparent_temperature_max")?.let { add(FeatureMetric("体感最高", "${fmt(it, 1)}°C", "今日最高体感温度。")) }
            today("apparent_temperature_min")?.let { add(FeatureMetric("体感最低", "${fmt(it, 1)}°C", "今日最低体感温度。")) }
            today("temperature_2m_max")?.let { add(FeatureMetric("最高气温", "${fmt(it, 1)}°C", "今日最高气温（2 米）。")) }
            today("temperature_2m_min")?.let { add(FeatureMetric("最低气温", "${fmt(it, 1)}°C", "今日最低气温（2 米）。")) }
            today("uv_index_max")?.let { add(FeatureMetric("紫外线最高", "${fmt(it, 1)}", "今日最高紫外线指数（含云影响）。")) }
            today("rain_sum")?.let { add(FeatureMetric("降雨合计", "${fmt(it, 1)} mm", "今日累计降雨量。")) }
            today("showers_sum")?.let { add(FeatureMetric("阵雨合计", "${fmt(it, 1)} mm", "今日累计阵雨量。")) }
            today("snowfall_sum")?.let { add(FeatureMetric("降雪合计", "${fmt(it, 1)} cm", "今日累计降雪量。")) }
            today("snow_depth_max")?.let { add(FeatureMetric("最大积雪", "${fmt(it, 1)} cm", "今日最大积雪深度。")) }
            today("precipitation_hours")?.let { add(FeatureMetric("降水时长", "${fmt(it, 1)} 小时", "今日降水总小时数。")) }
            today("cloud_cover_mean")?.let { add(FeatureMetric("平均云量", "${fmt(it, 0)}%", "今日平均总云量。")) }
            today("wind_gusts_10m_max")?.let { add(FeatureMetric("最大阵风", "${fmt(it, 1)} km/h", "今日最大阵风风速。")) }
            today("wind_speed_10m_mean")?.let { add(FeatureMetric("平均风速", "${fmt(it, 1)} km/h", "今日平均风速。")) }
            today("pressure_msl_mean")?.let { add(FeatureMetric("平均气压", "${fmt(it, 1)} hPa", "今日平均海平面气压。")) }
            today("relative_humidity_2m_mean")?.let { add(FeatureMetric("平均湿度", "${fmt(it, 0)}%", "今日平均相对湿度。")) }
            today("daylight_duration")?.let { add(FeatureMetric("昼长", "${fmt(it / 3600.0, 1)} 小时", "今日白昼时长。")) }
            today("sunshine_duration")?.let { add(FeatureMetric("日照时长", "${fmt(it / 3600.0, 1)} 小时", "今日实际日照时长。")) }
            today("shortwave_radiation_sum")?.let { add(FeatureMetric("辐射总量", "$it MJ/m²", "今日到达地面的太阳辐射总量。")) }
            today("et0_fao_evapotranspiration")?.let { add(FeatureMetric("ET₀", "$it mm", "今日参考作物蒸散发总量。")) }
            today("uv_index_clear_sky_max")?.let { add(FeatureMetric("晴空UV最高", "$it", "今日无云条件下最高紫外线指数。")) }
            todayTime("moonrise")?.let { add(FeatureMetric("月出", Fmt.clock(it, offset), "今日月出时刻。")) }
            todayTime("moonset")?.let { add(FeatureMetric("月落", Fmt.clock(it, offset), "今日月落时刻。")) }
            moonPhase(today("moon_phase"))?.let { add(FeatureMetric("月相", it, "今日月相（0–1 对应亮度占比）。")) }
        }
        if (hourlyItems.isEmpty() && dailyItems.isEmpty()) null else ForecastSection(
            hourly = hourlyItems,
            daily = dailyItems,
            highC = today("temperature_2m_max"),
            lowC = today("temperature_2m_min"),
        )
    } catch (e: Exception) { null }

    private suspend fun getElevation(lat: Double, lon: Double): Double? = try {
        val url = "https://api.open-meteo.com/v1/elevation?latitude=$lat&longitude=$lon"
        val root = getJson(url) ?: return null
        ((root["elevation"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.doubleOrNull
    } catch (e: Exception) { null }

    /** 气候常年同期：1991–2020 均值，按「今天」日期返回（用于对比）。 */
    private suspend fun getClimate(lat: Double, lon: Double): ClimateSection? = try {
        val today = java.time.LocalDate.now().toString()
        val url = "https://climate-api.open-meteo.com/v1/climate?latitude=$lat&longitude=$lon" +
            "&start_date=$today&end_date=$today" +
            "&daily=temperature_2m_mean,temperature_2m_max,temperature_2m_min," +
            "precipitation_sum,snowfall_sum,shortwave_radiation_sum&timezone=auto"
        val root = getJson(url) ?: return null
        val daily = root["daily"] as? JsonObject ?: return null
        fun v(key: String): Double? = ((daily[key] as? JsonArray))
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }?.firstOrNull()
        val normals = listOfNotNull(
            metric("常年均温", fmt(v("temperature_2m_mean"), 1)?.let { "$it°C" }, "1991–2020 年同日均温。"),
            metric("常年最高", fmt(v("temperature_2m_max"), 1)?.let { "$it°C" }, "1991–2020 年同日最高均温。"),
            metric("常年最低", fmt(v("temperature_2m_min"), 1)?.let { "$it°C" }, "1991–2020 年同日最低均温。"),
            metric("常年降水", fmt(v("precipitation_sum"), 1)?.let { "$it mm" }, "1991–2020 年同日降水均值。"),
            metric("常年降雪", fmt(v("snowfall_sum"), 1)?.let { "$it cm" }, "1991–2020 年同日降雪均值。"),
            metric("常年辐射", fmt(v("shortwave_radiation_sum"), 2)?.let { "$it MJ/m²" }, "1991–2020 年同日太阳辐射均值。"),
        )
        if (normals.isEmpty()) null else ClimateSection(
            normals = normals,
            meanC = v("temperature_2m_mean"),
            highC = v("temperature_2m_max"),
            lowC = v("temperature_2m_min"),
        )
    } catch (e: Exception) { null }

    /** 季节集合预报：未来数月逐日均温（集合均值）与降水，取前 30 天画趋势。 */
    private suspend fun getSeasonal(lat: Double, lon: Double): SeasonalSection? = try {
        val url = "https://seasonal-api.open-meteo.com/v1/seasonal?latitude=$lat&longitude=$lon" +
            "&daily=temperature_2m_mean,precipitation_sum&forecast_months=3&timezone=auto"
        val root = getJson(url) ?: return null
        val daily = root["daily"] as? JsonObject ?: return null
        fun series(key: String, n: Int): List<Double?> = ((daily[key] as? JsonArray))
            ?.map { (it as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite) }
            ?.take(n) ?: emptyList()
        val temps = series("temperature_2m_mean", 30)
        val precip = series("precipitation_sum", 30)
        val valid = temps.filterNotNull()
        val summary = if (valid.size >= 2) {
            val first = valid.first()
            val last = valid.last()
            val trend = last - first
            val dir = when {
                trend > 0.5 -> "升温 ${"%.1f".format(trend)}°C"
                trend < -0.5 -> "降温 ${"%.1f".format(-trend)}°C"
                else -> "波动不大"
            }
            "未来 30 天集合均温 ${"%.1f".format(first)}→${"%.1f".format(last)}°C，预计$dir"
        } else null
        if (temps.isEmpty()) null else SeasonalSection(temps, precip, summary ?: "季节集合预报暂无趋势")
    } catch (e: Exception) { null }
}

// —— 展示 ——

/** 加载态：照 demo 用库 [MochiSkeleton] 拼出页面布局骨架，分组标题 + 大卡片 + 三列数值格子。 */
@Composable
private fun LoadingBlock() {
    val cs = MochiTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        MochiSkeleton(loading = true, shape = MochiCapsule) {
            Text("正在获取特色数据", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
        // 照真实内容铺骨架：每一组都是「标题行 + 圆角卡片 + 指标网格」，尺寸与 FeaturePanel 一致，
        // 数据回来时同一位置直接换上真内容，不会跳版。
        repeat(4) {
            Column {
                MochiSkeleton(
                    loading = true,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    shape = MochiCapsule,
                ) {
                    Box(Modifier.fillMaxWidth(0.42f).height(14.dp))
                }
                MochiListSection {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        repeat(2) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                repeat(3) {
                                    MochiSkeleton(
                                        loading = true,
                                        modifier = Modifier.weight(1f),
                                        shape = MochiRoundedRectangle(10.dp),
                                    ) {
                                        Box(Modifier.fillMaxWidth().height(52.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    val cs = MochiTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 260.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(painterResource(R.drawable.ph_info), contentDescription = null,
                tint = cs.tertiary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(10.dp))
            Text(message, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            MochiBarButton("重试", onClick = onRetry)
        }
    }
}

private fun LazyListScope.FeatureHubContent(state: FeatureHubState, onMetric: (FeatureMetric) -> Unit) {
    state.satellite?.let { sat ->
        item {
            FeaturePanel("卫星辐射", R.drawable.ph_broadcast) {
                MetricGrid(sat.latest, hint = "点按指标查看含义", onMetric = onMetric)
                if (sat.today.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("今日", style = MaterialTheme.typography.labelMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    MetricGrid(sat.today, onMetric = onMetric)
                }
            }
        }
    }
    state.flood?.let { flood ->
        item {
            FeaturePanel("洪水风险", R.drawable.ph_waves) {
                FloodVisual(flood)
                Spacer(Modifier.height(16.dp))
                MetricGrid(flood.today, hint = "点按指标查看含义", onMetric = onMetric)
            }
        }
    }
    state.climate?.let { cl ->
        item {
            FeaturePanel("气候对比 · 常年同期", R.drawable.ph_calendar) {
                ClimatePanel(cl, state.forecast?.highC, state.forecast?.lowC, onMetric = onMetric)
            }
        }
    }
    state.seasonal?.let { se ->
        item {
            FeaturePanel("季节趋势 · 集合预报", R.drawable.ph_wave_sine) { SeasonalPanel(se) }
        }
    }
    state.forecast?.let { fc ->
        item {
            FeaturePanel("预报扩展字段", R.drawable.ph_list) {
                val hourlyByLabel = fc.hourly.associateBy { it.label }
                val dailyByLabel = fc.daily.associateBy { it.label }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhosphorIcon(R.drawable.ph_clock, null, Modifier.size(14.dp), MochiTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("逐时（当前小时）· 点按查看含义", style = MaterialTheme.typography.labelMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
                    }
                    // 天然成组的字段合并为可视化：廓线画折线、同单位量级画条形，数值与含义点按保留
                    listOf("80m风速", "120m风速", "180m风速").mapNotNull(hourlyByLabel::get)
                        .takeIf { it.size >= 2 }?.let {
                            MetricTrend("风廓线 · 距地 80–180 米", it, onMetric)
                        }
                    listOf("地温0cm", "地温6cm", "地温18cm", "地温54cm").mapNotNull(hourlyByLabel::get)
                        .takeIf { it.size >= 2 }?.let {
                            MetricTrend("土壤温度剖面 · 0–54 厘米", it, onMetric)
                        }
                    listOf("湿度0-1cm", "湿度1-3cm", "湿度3-9cm", "湿度9-27cm", "湿度27-81cm").mapNotNull(hourlyByLabel::get)
                        .takeIf { it.size >= 2 }?.let {
                            MetricTrend("土壤湿度剖面 · 0–81 厘米", it, onMetric)
                        }
                    listOf("短波辐射", "直接辐射", "散射辐射", "法向辐照").mapNotNull(hourlyByLabel::get)
                        .takeIf { it.isNotEmpty() }?.let {
                            MetricBars("辐射组成 · W/m²", it, onMetric)
                        }
                    listOf("降雨量", "阵雨量", "降雪量", "积雪深度").mapNotNull(hourlyByLabel::get)
                        .takeIf { it.isNotEmpty() }?.let {
                            MetricBars("降水拆分 · 毫米/厘米", it, onMetric)
                        }
                    MetricGrid(fc.hourly.filter { it.label !in HourlyVisualizedLabels }, onMetric = onMetric)
                    if (fc.daily.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PhosphorIcon(R.drawable.ph_sun, null, Modifier.size(14.dp), MochiTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("逐日（今日）", style = MaterialTheme.typography.labelMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
                        }
                        listOf("昼长", "日照时长").mapNotNull(dailyByLabel::get)
                            .takeIf { it.isNotEmpty() }?.let {
                                MetricBars("昼长与日照 · 小时", it, onMetric)
                            }
                        listOf("降雨合计", "阵雨合计", "降雪合计", "最大积雪").mapNotNull(dailyByLabel::get)
                            .takeIf { it.isNotEmpty() }?.let {
                                MetricBars("降水与积雪 · 毫米/厘米", it, onMetric)
                            }
                        MetricGrid(fc.daily.filter { it.label !in DailyVisualizedLabels }, onMetric = onMetric)
                    }
                }
            }
        }
    }
    state.elevation?.let { elev ->
        item {
            FeaturePanel("海拔", R.drawable.ph_gauge) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%.0f".format(elev), style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, color = MochiTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("米", style = MaterialTheme.typography.titleMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (state.satellite == null && state.flood == null && state.forecast == null && state.climate == null && state.seasonal == null && state.elevation == null) {
        item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("部分扩展接口暂无数据。", color = MochiTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// —— 气候对比 · 常年同期 ——

@Composable
private fun ClimatePanel(
    cl: ClimateSection,
    forecastHigh: Double?,
    forecastLow: Double?,
    onMetric: (FeatureMetric) -> Unit,
) {
    MetricGrid(cl.normals, hint = "点按指标查看含义", onMetric = onMetric)
    // 预报 vs 常年 对比行
    if (forecastHigh != null || forecastLow != null) {
        Spacer(Modifier.height(16.dp))
        Text("今日预报 vs 常年同期", style = MaterialTheme.typography.labelMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompareTile("最高", forecastHigh, cl.highC, Modifier.weight(1f))
            CompareTile("最低", forecastLow, cl.lowC, Modifier.weight(1f))
            if (cl.meanC != null) CompareTile("均温", null, cl.meanC, Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
        }
        // 文案
        val highDiff = if (forecastHigh != null && cl.highC != null) forecastHigh - cl.highC else null
        val lowDiff = if (forecastLow != null && cl.lowC != null) forecastLow - cl.lowC else null
        val parts = buildList {
            highDiff?.let { add("最高${signed(it)}°C") }
            lowDiff?.let { add("最低${signed(it)}°C") }
        }
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "与常年同期相比：${parts.joinToString("、")}。",
                style = MaterialTheme.typography.bodySmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun signed(v: Double): String = if (v > 0) "+${"%.1f".format(v)}" else "%.1f".format(v)

@Composable
private fun CompareTile(label: String, forecast: Double?, normal: Double?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MochiTheme.colorScheme.outlineVariant.copy(alpha = .12f))
            .border(1.dp, MochiTheme.colorScheme.outlineVariant.copy(alpha = .22f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        if (forecast != null) {
            Text(
                "${"%.1f".format(forecast)}°C",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MochiTheme.colorScheme.primary,
            )
        } else if (normal != null) {
            Text(
                "${"%.1f".format(normal)}°C",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MochiTheme.colorScheme.primary,
            )
        } else {
            Text("—", style = MaterialTheme.typography.titleMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
        }
        normal?.let {
            Spacer(Modifier.height(2.dp))
            Text("常年 ${"%.1f".format(it)}°C", style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// —— 季节趋势 · 集合预报 ——

@Composable
private fun SeasonalPanel(se: SeasonalSection) {
    Text(se.summary, style = MaterialTheme.typography.bodyMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    SeasonalChart(se.temps, se.precipitation)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("第 1 天", style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
        Text("第 30 天", style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "折线为集合均温，橙柱为每日降水；数据来自 seasonal-api 集合预报（50 个成员）。",
        style = MaterialTheme.typography.bodySmall,
        color = MochiTheme.colorScheme.onSurfaceVariant,
    )
}

/** 季节折线图：x=天索引，y=均温（薄荷绿折线），降水用橙色柱（底部对齐）。 */
@Composable
private fun SeasonalChart(temps: List<Double?>, precipitation: List<Double?>) {
    val lineColor = MochiTheme.colorScheme.primary
    val barColor = MochiTheme.colorScheme.tertiary
    val validTemps = temps.filterNotNull()
    if (validTemps.isEmpty()) {
        Text("季节集合预报暂无数据。", style = MaterialTheme.typography.bodySmall, color = MochiTheme.colorScheme.onSurfaceVariant)
        return
    }
    val minT = validTemps.minOrNull() ?: 0.0
    val maxT = validTemps.maxOrNull() ?: 0.0
    val range = (maxT - minT).takeIf { it > 0.001 } ?: 1.0
    val maxP = (precipitation.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(0.001)
    val refColor = MochiTheme.colorScheme.onSurface.copy(alpha = .2f)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = temps.size
        val slot = size.width / n
        val chartTop = 4.dp.toPx()
        val chartBottom = size.height - 16.dp.toPx()
        val barH = (chartBottom - chartTop) * 0.32f
        val chartH = chartBottom - chartTop
        // 降水柱（底部基线对齐，橙色）
        precipitation.forEachIndexed { i, p ->
            if (p != null && p > 0) {
                val h = ((p / maxP) * barH).toFloat().coerceAtLeast(2f)
                val x = slot * i + slot * 0.28f
                drawRoundRect(
                    color = barColor.copy(alpha = .5f),
                    topLeft = Offset(x, chartBottom - h),
                    size = Size(slot * 0.44f, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        // 均温折线
        val pts = temps.mapIndexedNotNull { i, t ->
            if (t == null) null else {
                val x = slot * i + slot * 0.5f
                val y = chartBottom - (((t - minT) / range) * chartH).toFloat()
                Offset(x, y)
            }
        }
        if (pts.size >= 2) {
            pts.zipWithNext { a, b -> drawLine(lineColor, a, b, 2.dp.toPx()) }
        }
        pts.forEach { drawCircle(lineColor, 2.5.dp.toPx(), it) }
        // 温度参考线（中值）
        val midT = (minT + maxT) / 2.0
        val midY = chartBottom - (((midT - minT) / range) * chartH).toFloat()
        drawLine(
            refColor,
            Offset(0f, midY),
            Offset(size.width, midY),
            1.dp.toPx(),
        )
    }
}

// —— 洪水可视化 ——

@Composable
private fun FloodVisual(flood: FloodSection) {
    val barColor = MochiTheme.colorScheme.primary
    val trackColor = MochiTheme.colorScheme.outlineVariant.copy(alpha = .45f)
    val bandColor = MochiTheme.colorScheme.primary.copy(alpha = .22f)
    val textColor = MochiTheme.colorScheme.onSurface.copy(alpha = .55f)
    // 逐日柱状图
    val days = flood.days
    val hasSeries = days.isNotEmpty() && days.any { it != null && it > 0 }
    if (hasSeries) {
        Text("未来 5 天流量", style = MaterialTheme.typography.labelMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val values = days.map { it ?: 0.0 }
            val max = values.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0
            val slot = size.width / days.size
            days.forEachIndexed { i, v ->
                val x = slot * i
                val barW = slot * 0.56f
                val h = (((v ?: 0.0) / max) * size.height).toFloat().coerceAtLeast(3f)
                drawRoundRect(
                    color = if (v == null) trackColor.copy(alpha = .25f) else barColor.copy(alpha = if (i == 0) 1f else .55f),
                    topLeft = Offset(x + slot * 0.22f, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今天", style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
            Text("第 5 天", style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
    }
    // 历史分位区间条
    val min = flood.min
    val max = flood.max
    val cur = flood.discharge
    if (min != null && max != null && cur != null && max > min) {
        Text("今日流量相对历史同期", style = MaterialTheme.typography.labelMedium, color = MochiTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            fun x(v: Double): Float = (((v - min) / (max - min)) * size.width).toFloat().coerceIn(0f, size.width)
            // 轨道
            drawRoundRect(trackColor, Offset(0f, size.height * 0.38f), Size(size.width, size.height * 0.24f), CornerRadius(4.dp.toPx()))
            // P25–P75 区间带
            val a = flood.p25?.let { x(it) }
            val b = flood.p75?.let { x(it) }
            if (a != null && b != null && b > a) {
                drawRoundRect(bandColor, Offset(a, size.height * 0.2f), Size(b - a, size.height * 0.6f), CornerRadius(4.dp.toPx()))
            }
            // 中位数刻度
            flood.median?.let { m ->
                val mx = x(m)
                drawLine(textColor, Offset(mx, size.height * 0.18f), Offset(mx, size.height * 0.82f), 1.dp.toPx())
            }
            // 当前值指针
            val cx = x(cur)
            drawLine(barColor, Offset(cx, 0f), Offset(cx, size.height), 2.dp.toPx())
            drawCircle(barColor, 4.dp.toPx(), Offset(cx, size.height * 0.5f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("最小 ${fmt1(min)}", style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
            Text("P25–P75", style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
            Text("最大 ${fmt1(max)}", style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
    }
}

private fun fmt1(v: Double): String = if (abs(v) >= 100) "%.0f".format(v) else "%.1f".format(v)

/** 分组面板：卡片外标题统一带左侧图标，图标与文字共用同一缩进，卡片仍用库列表容器。 */
@Composable
private fun FeaturePanel(title: String, icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Column {
        // 库的 MochiListSectionHeader 自带 16dp 横向内边距，图标放在 Row 前面会被甩到
        // 缩进之外；这里按同一缩进自绘标题行，保证图标紧贴文字。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhosphorIcon(icon, null, Modifier.size(14.dp), MochiTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MochiTheme.textStyles.navTitle,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MochiListSection {
            Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    }
}

@Composable
private fun MetricGrid(
    items: List<FeatureMetric>,
    hint: String? = null,
    onMetric: (FeatureMetric) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { m ->
                    if (m.meaning != null) {
                        MetricTile(
                            m,
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MochiTheme.colorScheme.outlineVariant.copy(alpha = .12f))
                                .border(1.dp, MochiTheme.colorScheme.outlineVariant.copy(alpha = .22f), RoundedCornerShape(8.dp))
                                .clickable { onMetric(m) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        )
                    } else {
                        MetricTile(m, Modifier.weight(1f))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (hint != null) {
        Spacer(Modifier.height(8.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MochiTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricTile(m: FeatureMetric, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            m.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MochiTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(m.label, style = MaterialTheme.typography.labelSmall, color = MochiTheme.colorScheme.onSurfaceVariant)
    }
}

/** 点按指标后弹窗：库对话框展示数值与含义。 */
@Composable
private fun MetricMeaningDialog(metric: FeatureMetric?, onDismiss: () -> Unit) {
    val cs = MochiTheme.colorScheme
    MochiDialog(
        visible = metric != null,
        onDismissRequest = onDismiss,
        title = metric?.label.orEmpty(),
        message = metric?.meaning,
        dismissOnOutsideTap = true,
        actions = persistentListOf(MochiDialogAction("关闭", MochiDialogActionRole.Cancel)),
    ) {
        metric?.let {
            Text(it.value, Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = cs.primary, fontWeight = FontWeight.Bold)
        }
    }
}

/** 已合并进折线 / 条形可视化的逐时字段，不再重复出现在数值格子里。 */
private val HourlyVisualizedLabels = setOf(
    "短波辐射", "直接辐射", "散射辐射", "法向辐照",
    "80m风速", "120m风速", "180m风速",
    "地温0cm", "地温6cm", "地温18cm", "地温54cm",
    "湿度0-1cm", "湿度1-3cm", "湿度3-9cm", "湿度9-27cm", "湿度27-81cm",
    "降雨量", "阵雨量", "降雪量", "积雪深度",
)

/** 已合并进条形可视化的逐日字段。 */
private val DailyVisualizedLabels = setOf(
    "昼长", "日照时长", "降雨合计", "阵雨合计", "降雪合计", "最大积雪",
)

/** 从指标展示串里解析数值前缀（"23.5 °C" → 23.5；"09:17" → null）。 */
private fun FeatureMetric.numericValue(): Float? =
    value.trim().split(' ', '\u00A0').firstOrNull()?.toFloatOrNull()

/** 同一单位的指标画横向条形，量级对比一目了然；条行仍可点按查看含义。 */
@Composable
private fun MetricBars(
    title: String,
    items: List<FeatureMetric>,
    onMetric: (FeatureMetric) -> Unit = {},
) {
    val cs = MochiTheme.colorScheme
    val values = items.map { it.numericValue() ?: 0f }
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        items.forEachIndexed { index, metric ->
            val fraction = (values[index] / max).coerceIn(0.04f, 1f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (metric.meaning != null) Modifier.clickable { onMetric(metric) } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    metric.label,
                    Modifier.width(88.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(cs.outlineVariant.copy(alpha = .18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(cs.primary),
                    )
                }
                Text(
                    metric.value,
                    Modifier.width(72.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 同一单位、按顺序排布的指标画折线（风廓线、土壤剖面）；横轴为给定字段顺序。 */
@Composable
private fun MetricTrend(
    title: String,
    points: List<FeatureMetric>,
    onMetric: (FeatureMetric) -> Unit = {},
) {
    val cs = MochiTheme.colorScheme
    val values = points.map { it.numericValue() }
    if (values.count { it != null } < 2) return
    val known = values.filterNotNull()
    val min = known.minOrNull() ?: return
    val max = known.maxOrNull() ?: return
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val color = cs.primary
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        // 折线区域也支持点按：按横轴最近的数据点弹出对应含义
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val stepPx = size.width / ((points.size - 1).coerceAtLeast(1))
                        val index = (tap.x / stepPx).roundToInt().coerceIn(0, points.lastIndex)
                        if (points[index].meaning != null) onMetric(points[index])
                    }
                },
        ) {
            val padTop = 10.dp.toPx()
            val padBottom = 10.dp.toPx()
            val chartHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
            val step = size.width / ((points.size - 1).coerceAtLeast(1))
            // 缺失值按区间中值补位，保持折线连续
            val coords = points.mapIndexed { index, _ ->
                val value = values[index] ?: ((min + max) / 2f)
                Offset(step * index, padTop + chartHeight * (1f - (value - min) / span))
            }
            val line = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                coords.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            coords.forEach { drawCircle(color, 4.dp.toPx(), it) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            points.forEach { metric ->
                Column(
                    Modifier
                        .weight(1f)
                        .then(if (metric.meaning != null) Modifier.clickable { onMetric(metric) } else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        metric.value,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(metric.label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
