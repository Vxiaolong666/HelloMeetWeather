package com.HelloMeet.weather.data

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class AmapLookupResult(
    val ok: Boolean,
    val street: String? = null,
    val formattedAddress: String? = null,
    val info: String? = null,
    val infocode: String? = null,
    val adcode: String? = null,
    val district: String? = null,
    val city: String? = null,
    val province: String? = null,
    val failure: PlaceFailure? = null,
    /** 诊断用：解析出的关键字段摘要，不含 Key 与坐标。 */
    val detail: String? = null,
)

internal data class AmapPlaceResult(
    val name: String,
    val address: String?,
    val district: String?,
    val city: String?,
    val province: String?,
    val latitude: Double,
    val longitude: Double,
    val sourceId: String? = null,
)

private data class AmapRawResult(val result: AmapLookupResult, val root: JsonObject? = null)

/**
 * 高德 Web 服务只作为开发者可选的街道名称增强，不接管系统定位。
 * Android LocationManager 给出的坐标按 GPS/WGS84 处理，先走官方坐标转换，
 * 再逆地理编码；任一步失败都由调用方无感回退到系统 Geocoder。
 */
internal object AmapApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun reverseStreetFromWgs84(
        key: String,
        latitude: Double,
        longitude: Double,
        cityName: String?,
    ): AmapLookupResult {
        val candidate = key.trim()
        if (candidate.isEmpty()) return AmapLookupResult(false, info = "EMPTY_KEY")
        PlaceServiceGate.check(candidate, "amap:reverse")
        return try {
            val converted = request(
                path = listOf("v3", "assistant", "coordinate", "convert"),
                query = mapOf(
                    "key" to candidate,
                    "locations" to coordinate(longitude, latitude),
                    "coordsys" to "gps",
                ),
            )
            // 坐标转换失败不再中止链路：直接用原始 WGS 坐标尝试逆地理，
            // 高德可容忍约百米级偏移，避免 regeo 请求根本不发出。
            val location = converted.result.formattedAddress?.takeIf(::validCoordinatePair)
                ?: coordinate(longitude, latitude)
            reverseGcj(candidate, location, cityName).also { result ->
                if (result.ok) PlaceServiceGate.clear(candidate, "amap:reverse")
                else PlaceServiceGate.record(candidate, "amap:reverse", amapFailure(result.infocode))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (error: PlaceServiceException) {
            PlaceServiceGate.record(candidate, "amap:reverse", error.failure, error.retryAfterMillis)
            AmapLookupResult(false, info = "高德街道服务暂不可用", failure = error.failure)
        } catch (_: Exception) {
            AmapLookupResult(false, info = "高德街道服务暂不可用")
        }
    }

    /** 保存前真实验证 Web 服务 Key；北京测试点已是 GCJ-02，因此只消耗一次逆地理请求。 */
    suspend fun verifyKey(key: String): AmapLookupResult {
        val candidate = key.trim()
        if (candidate.isEmpty()) return AmapLookupResult(false, info = "请填写 Web 服务 API Key")
        return try {
            PlaceServiceGate.clear(candidate, "amap:reverse")
            PlaceServiceGate.clear(candidate, "amap:search")
            reverseGcj(candidate, "116.397428,39.909230", "北京")
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            AmapLookupResult(false, info = "无法连接高德 Web 服务")
        }
    }

    suspend fun searchPlaces(key: String, keyword: String, cityName: String? = null): List<AmapPlaceResult> {
        val candidate = key.trim()
        if (candidate.isEmpty() || keyword.trim().length < 2) return emptyList()
        PlaceServiceGate.check(candidate, "amap:search")
        return try {
            val query = linkedMapOf(
                "key" to candidate,
                "keywords" to keyword.trim(),
                "offset" to "10",
                "page" to "1",
                "extensions" to "base",
            )
            cityName?.trim()?.takeIf(String::isNotBlank)?.let { query["city"] = it }
            val raw = request(listOf("v3", "place", "text"), query)
            if (!raw.result.ok) {
                val failure = amapFailure(raw.result.infocode)
                PlaceServiceGate.record(candidate, "amap:search", failure)
                throw PlaceServiceException(failure)
            }
            PlaceServiceGate.clear(candidate, "amap:search")
            (raw.root?.get("pois") as? JsonArray ?: throw PlaceServiceException(PlaceFailure.UNAVAILABLE)).mapNotNull { item ->
                val poi = item.asObject() ?: return@mapNotNull null
                val parts = poi.string("location")?.split(',') ?: return@mapNotNull null
                val gcjLon = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val gcjLat = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                val wgs = gcj02ToWgs84(gcjLat, gcjLon)
                AmapPlaceResult(
                    name = poi.string("name") ?: return@mapNotNull null,
                    address = poi.string("address"),
                    district = poi.string("adname"),
                    city = poi.string("cityname"),
                    province = poi.string("pname"),
                    latitude = wgs.latitude,
                    longitude = wgs.longitude,
                    sourceId = poi.string("id"),
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (error: PlaceServiceException) {
            PlaceServiceGate.record(candidate, "amap:search", error.failure, error.retryAfterMillis)
            throw error
        } catch (_: Exception) {
            throw PlaceServiceException(PlaceFailure.UNAVAILABLE)
        }
    }

    private suspend fun reverseGcj(key: String, location: String, cityName: String?): AmapLookupResult {
        val raw = request(
            path = listOf("v3", "geocode", "regeo"),
            query = mapOf(
                "key" to key,
                "location" to location,
                "extensions" to "all",
                "radius" to "1000",
                "roadlevel" to "0",
            ),
        )
        if (!raw.result.ok) return raw.result
        val root = raw.root ?: return AmapLookupResult(false, info = "高德响应无法解析")
        val regeocode = root["regeocode"]?.asObject()
            ?: return AmapLookupResult(false, info = "高德未返回逆地理结果")
        val component = regeocode["addressComponent"]?.asObject()
        val township = component?.textValue("township")
        // 高德在 streetNumber.street 无数据时返回空数组 []，有值时返回字符串，两种都要兼容。
        val street = component?.get("streetNumber")?.asObject()?.textValue("street")
        val formatted = regeocode.string("formatted_address")
        val adcode = component?.string("adcode")
        // 乡镇与街道号皆空时（城区常见），用最近的 POI/AOI 名称兜底成街道级标签。
        val poiName = (regeocode["pois"] as? JsonArray)
            ?.mapNotNull { it.asObject()?.string("name") }?.firstOrNull()
        val aoiName = (regeocode["aois"] as? JsonArray)
            ?.mapNotNull { it.asObject()?.string("name") }?.firstOrNull()
        return AmapLookupResult(
            ok = true,
            street = amapStreetLabel(township, street ?: poiName ?: aoiName, cityName),
            formattedAddress = formatted,
            info = raw.result.info,
            infocode = raw.result.infocode,
            adcode = adcode,
            district = component?.string("district"),
            city = component?.string("city") ?: component?.string("province"),
            province = component?.string("province"),
            detail = "完整地址=${formatted ?: "无"} 乡镇=${township ?: "空"} 街道号=${street ?: "空"} " +
                "POI=${poiName ?: "空"} AOI=${aoiName ?: "空"}",
        )
    }

    // 返回值只沿当前调用链传递；对象不记录 URL/Key，也不写日志。
    private suspend fun request(path: List<String>, query: Map<String, String>): AmapRawResult =
        withContext(Dispatchers.IO) {
            val builder = "https://restapi.amap.com/".toHttpUrl().newBuilder()
            path.forEach(builder::addPathSegment)
            query.forEach(builder::addQueryParameter)
            val response = radarRequestBytes(client, Request.Builder().url(builder.build()).get().build(), 1_000_000)
            if (!response.successful) {
                if (response.code == 429) throw PlaceServiceException(PlaceFailure.QUOTA, placeRetryAfterMillis(response.retryAfter))
                return@withContext AmapRawResult(
                    AmapLookupResult(false, info = "高德服务返回 HTTP ${response.code}"),
                )
            }
            val root = runCatching {
                json.parseToJsonElement(response.bytes.toString(Charsets.UTF_8)).jsonObject
            }.getOrNull()
            val status = root?.string("status")
            val info = root?.string("info")
            val infocode = root?.string("infocode")
            if (status != "1") {
                return@withContext AmapRawResult(
                    AmapLookupResult(false, info = info ?: "高德鉴权未通过", infocode = infocode),
                    root,
                )
            }
            val locations = root?.string("locations")
            AmapRawResult(
                AmapLookupResult(true, formattedAddress = locations, info = info, infocode = infocode),
                root,
            )
        }

    private fun coordinate(longitude: Double, latitude: Double): String =
        String.format(Locale.US, "%.6f,%.6f", longitude, latitude)

    private fun validCoordinatePair(value: String): Boolean {
        val parts = value.split(',')
        return parts.size == 2 && parts.all { it.trim().toDoubleOrNull() != null }
    }
}

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonObject.string(name: String): String? =
    runCatching { get(name)?.jsonPrimitive?.contentOrNull?.trim() }
        .getOrNull()?.takeIf(String::isNotBlank)

/** 高德 v3 的空字段可能返回 []，有值时返回字符串；两种形态都收敛为首个非空文本。 */
private fun JsonElement.textValueOrNull(): String? = when (this) {
    is kotlinx.serialization.json.JsonPrimitive -> runCatching { contentOrNull?.trim() }.getOrNull()
        ?.takeIf(String::isNotBlank)
    is JsonArray -> firstOrNull()?.textValueOrNull()
    else -> null
}

private fun JsonObject.textValue(name: String): String? = get(name)?.textValueOrNull()

internal fun amapStreetLabel(township: String?, street: String?, cityName: String?): String? =
    listOf(township, street)
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .filterNot { it == cityName?.trim() }
        .distinct()
        .take(2)
        .joinToString("·")
        .ifBlank { null }
