package com.HelloMeet.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * 生态环境部城市 AQI 实时报的轻量读取器（口径移植自 yunsong 的 MeeAirQualityRankRepository）。
 *
 * 只读官方同一批次发布的数据：页面格式失配、行数不足或城市未覆盖时明确返回不可用，
 * 绝不与商业天气源的 AQI 混排，也不把不同发布时间的行拼成「全国排名」。
 */
internal object MeeAirQualityRankRepository {
    // 两个地址均为生态环境部数据服务入口；镜像只在主站不可达时回退。
    private val urls = listOf(
        "https://www.mee.gov.cn/websjzx/homepages/airDatas.vm",
        "https://datacenter.mee.gov.cn/websjzx/homepages/airDatas.vm",
    )
    private val timeFormat = SimpleDateFormat("yyyy年MM月dd日 HH时", Locale.CHINA)
    // 页面把城市名、同批次发布时间和 AQI 放在同一个 openWin2 行。
    private val rowPattern = Regex(
        """openWin2\(&#39;([^&]+?)&#39;,\s*&#39;([^&]+?)&#39;.*?<i\s+class=\"ii\">\s*(\d+)\s*</i>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val reportTimePattern = Regex("""(\d{4}年\d{2}月\d{2}日\s+\d{1,2}时)""")

    // 政府站会从 www.mee.gov.cn 跳转到 datacenter 镜像，而天气源共用的
    // domesticWeatherClient 明确禁用了重定向，所以这里单独建一个跟随重定向的客户端。
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    sealed interface Result {
        data class Available(
            val rank: Int,
            val total: Int,
            val topPercent: Int,
            val reportedAtMillis: Long,
            /** 官方实时报里实际命中的城市名，用于说明是否按上级城市统计。 */
            val matchedCity: String = "",
            /** 调用方最先请求的名称（区/县/街道），与 [matchedCity] 不同即为回退。 */
            val requestedCity: String = "",
        ) : Result

        /** 页面可解析，但同一批次里没有这个城市。 */
        data object CityNotCovered : Result

        /** 网络失败、页面改版或批次行数不足：明确不可用。 */
        data object Unavailable : Result
    }

    private data class PublishedRow(val name: String, val reportTime: String, val aqi: Int)

    suspend fun fetch(city: String): Result = fetch(listOf(city))

    /**
     * 按候选顺序查找：区/县/街道这类下级地名在实时报里通常不存在，
     * 依次回退到上级城市即可避免「暂未覆盖该城市」。
     *
     * 但整批数据只抓一次、只解析一次：候选之间只做内存匹配，不会为每个候选多请求一次。
     */
    suspend fun fetch(cities: List<String>): Result = withContext(Dispatchers.IO) {
        val candidates = cities.map(String::trim).filter(String::isNotBlank).distinct()
        if (candidates.isEmpty()) return@withContext Result.CityNotCovered
        urls.forEach { url ->
            val text = runCatching {
                weatherAwaitText(client, Request.Builder().url(url).build())
            }.getOrNull() ?: return@forEach
            val parsed = runCatching { parse(text, candidates) }.getOrDefault(Result.Unavailable)
            if (parsed !is Result.Unavailable) return@withContext parsed
        }
        Result.Unavailable
    }

    fun parse(html: String, city: String): Result = parse(html, listOf(city))

    fun parse(html: String, cities: List<String>): Result {
        val parsedRows = rowPattern.findAll(html).mapNotNull { match ->
            val name = decode(match.groupValues[1]).trim()
            val reportTime = decode(match.groupValues[2]).trim()
            val aqi = match.groupValues[3].toIntOrNull()
            if (name.isBlank() || reportTime.isBlank() || aqi == null) null
            else PublishedRow(name, reportTime, aqi)
        }.toList()
        // 只采用数量最多的同一发布时间：既避免混入页面残留表格，也不拼接不同批次。
        val reportTime = parsedRows.groupingBy { it.reportTime }.eachCount()
            .maxByOrNull { it.value }?.key ?: return Result.Unavailable
        val rows = parsedRows.filter { it.reportTime == reportTime }
        if (rows.size < 100) return Result.Unavailable

        val candidates = cities.map(String::trim).filter(String::isNotBlank).distinct()
        if (candidates.isEmpty()) return Result.CityNotCovered
        val normalized = candidates.map { it to normalize(it) }.filter { it.second.isNotEmpty() }
        // 先按全部候选做精确匹配，再退回包含匹配：上级城市精确命中优先于下级地名的模糊命中。
        val matched = normalized.firstNotNullOfOrNull { (_, requested) ->
            rows.firstOrNull { normalize(it.name) == requested }
        } ?: normalized.firstNotNullOfOrNull { (_, requested) ->
            rows.firstOrNull {
                val rowName = normalize(it.name)
                rowName.length >= 2 && (rowName.contains(requested) || requested.contains(rowName))
            }
        } ?: return Result.CityNotCovered

        val rank = rows.count { it.aqi < matched.aqi } + 1
        val topPercent = ceil(rank * 100.0 / rows.size).toInt().coerceIn(1, 100)
        val time = reportTimePattern.find(reportTime)?.groupValues?.getOrNull(1)
            ?.let { runCatching { timeFormat.parse(it)?.time }.getOrNull() } ?: 0L
        return Result.Available(rank, rows.size, topPercent, time, matched.name, candidates.first())
    }

    /**
     * 全国排名的候选城市链：先查本身，再逐级退回上级（深圳市 → 广东省）。
     * 直辖市的 affiliation 往往只有「北京市」，区名会直接回退成直辖市。
     */
    internal fun rankCandidateChain(name: String?, affiliation: String?): List<String> {
        val chain = ArrayList<String>()
        fun add(value: String?) {
            val text = value?.trim().orEmpty()
            if (text.isNotEmpty() && chain.none { it == text }) chain += text
        }
        add(name)
        affiliation.orEmpty()
            .split('·', '•', '・', '·', ',', '，', '/', '|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .asReversed()
            .forEach(::add)
        return chain
    }

    internal fun normalize(value: String): String = value
        .trim()
        .replace("市", "")
        .replace("地区", "")
        .replace("自治州", "")
        .replace(Regex("\\s+"), "")

    private fun decode(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}
