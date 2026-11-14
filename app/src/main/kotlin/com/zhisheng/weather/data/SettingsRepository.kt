package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.zhisheng.weather.model.RadarSource

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings", produceMigrations = { listOf(WeatherAppearanceDefaultsMigration()) })

// 数据源偏好：AUTO 默认小米→Open-Meteo（0.1.5-beta6 起百度/高德天气下线，两把 AK 只服务定位地理；
// 天气源设置不再列出「百度天气 / 高德天气」，持久化的旧 amap/baidu 经 effective() 一律回落 AUTO）。
// 需凭据的数据源只在开发者模式下可锁定；手动锁定时保持纯源，便于判断真实表现。
enum class SourcePref(val key: String, val cn: String, val en: String, val desc: String) {
    AUTO("auto", "自动优选", "AUTO", "按功能优选·失败降级"),
    // 自有品牌融合源：并发抓取小米/中央气象台/腾讯/天气网/Open-Meteo 多模型（有 key 时加和风/彩云），
    // 端侧加权共识 + 实况纠偏 + 完整性补齐。它是唯一允许补缺的手动源——"融合+完整"就是它的identity。
    ZHISHENG("zhisheng", "枳生天气源", "ZHISHENG", "多源共识·免配置"),
    NMC("nmc", "中央气象台", "NMC", "免配置·官方"),
    QWEATHER("qweather", "和风天气", "QWEATHER", "开发者·需凭据"),
    CAIYUN("caiyun", "彩云天气", "CAIYUN", "开发者·需 Token"),
    AMAP("amap", "高德天气", "AMAP", "用户 Key·区县实况与日预报"),
    BAIDU("baidu", "百度天气", "BAIDU", "用户 AK·区县天气·按授权返回"),
    TENCENT("tencent", "腾讯天气", "TENCENT", "免配置·城市天气·网站通道"),
    WEATHERCN("weathercn", "中国天气网", "WEATHERCN", "免配置·城市实况与当日预报"),
    SENIVERSE("seniverse", "心知天气", "SENIVERSE", "个人 Key·免费版城市天气"),
    MOJI("moji", "墨迹天气", "MOJI", "个人 AppCode·按订阅返回"),
    XIAOMI("xiaomi", "小米公开接口", "XIAOMI", "免配置·国内"),
    OPEN_METEO("openmeteo", "Open-Meteo", "OPEN-METEO", "免配置·全球");

    companion object {
        // 旧版本持久化的 "amap"/"baidu" 因枚举已无对应项，经 from() 自动回落 AUTO
        fun from(v: String?): SourcePref = entries.firstOrNull { it.key == v } ?: AUTO

        fun visible(developerMode: Boolean): List<SourcePref> = buildList {
            add(AUTO)
            add(ZHISHENG)
            add(XIAOMI)
            add(NMC)
            add(TENCENT)
            add(WEATHERCN)
            add(OPEN_METEO)
            if (developerMode) {
                add(CAIYUN)
                add(QWEATHER)
                add(SENIVERSE)
                add(MOJI)
            }
        }
    }

    fun effective(developerMode: Boolean): SourcePref = when {
        // 百度/高德是定位地理服务，不作为天气源：即使持久化了旧 amap/baidu 也一律回落 AUTO。
        this == AMAP || this == BAIDU -> AUTO
        (this in setOf(QWEATHER, CAIYUN, SENIVERSE, MOJI)) && !developerMode -> AUTO
        else -> this
    }

    fun matches(dataSource: String?): Boolean = when (this) {
        // AUTO 只读自身降级链（小米/中央气象台/Open-Meteo）的缓存；仍不接受仅手动锁定的和风/彩云缓存，
        // 也不把融合源缓存算作自身（AUTO 与枳生源切换时重拉一次，保持设置页语义严格一致）
        AUTO -> dataSource in setOf("XIAOMI", "NMC", "OPEN-METEO")
        ZHISHENG -> dataSource == "ZHISHENG"
        NMC -> dataSource == "NMC"
        QWEATHER -> dataSource == "QWEATHER"
        CAIYUN -> dataSource == "CAIYUN"
        AMAP -> dataSource == "AMAP"
        BAIDU -> dataSource == "BAIDU"
        TENCENT -> dataSource == "TENCENT"
        WEATHERCN -> dataSource == "WEATHERCN"
        SENIVERSE -> dataSource == "SENIVERSE"
        MOJI -> dataSource == "MOJI"
        XIAOMI -> dataSource == "XIAOMI"
        OPEN_METEO -> dataSource == "OPEN-METEO"
    }

    fun matches(data: com.zhisheng.weather.model.WeatherData): Boolean =
        matches(data.dataSource) &&
            // 枳生源的 blockSources 记录的是参与源（小米/中央气象台/…），身份只由 dataSource 唯一确定
            (this == AUTO || this == ZHISHENG || data.blockSources.values.all { it == data.dataSource })
}

// 定位地理服务引擎偏好：决定逆地理（坐标 → 城市/街道）时优先尝试的定位服务，
// 与天气来源平行。AUTO 按「可用凭据 → 系统 Geocoder」的顺序依次尝试并失败降级。
// 高德/百度需要用户 Key，枳生位置服务需要接入授权，系统定位免配置。
enum class LocationEnginePref(val key: String, val cn: String, val en: String, val desc: String) {
    AUTO("auto", "自动优选", "AUTO", "按可用定位能力依次尝试·失败降级"),
    SYSTEM("system", "系统定位", "SYSTEM", "免配置·系统 Geocoder"),
    AMAP("amap", "高德定位", "AMAP", "用户 Key·区县街道定位"),
    BAIDU("baidu", "百度定位", "BAIDU", "用户 AK·街道级定位"),
    SHARED("shared", "枳生位置服务", "SHARED", "需接入授权·自有服务");

    companion object {
        fun from(v: String?): LocationEnginePref = entries.firstOrNull { it.key == v } ?: AUTO
    }
}

// beta10.5 默认强烈自然天光与流光；持久化用户选择仍优先于缺省值。
enum class SoftGlowLevel(val key: String, val cn: String, val strength: Float, val drift: Float, val cycleSeconds: Double) {
    SUBTLE("subtle", "柔和", 1f, 1f, 72.0),
    FLOWING("flowing", "流光", 1.7f, 2.1f, 42.0);

    companion object {
        fun from(value: String?): SoftGlowLevel = entries.firstOrNull { it.key == value } ?: FLOWING
    }
}

enum class AmbienceLevel(val key: String, val cn: String, val factor: Float) {
    OFF("off", "关闭", 0f),
    SUBTLE("subtle", "克制", 1f),
    VIVID("vivid", "明显", 3.1f),
    INTENSE("intense", "强烈", 4.8f);

    val vivid: Boolean get() = this == VIVID || this == INTENSE
    val motionScale: Float get() = when (this) {
        INTENSE -> 1.62f
        VIVID -> 1.32f
        else -> 1f
    }

    companion object {
        fun from(v: String?): AmbienceLevel = entries.firstOrNull { it.key == v } ?: INTENSE
    }
}

enum class TelemetryMetric(val key: String, val cn: String, val en: String) {
    HUMIDITY("humidity", "湿度", "HUMIDITY"),
    WIND("wind", "风向风速", "WIND"),
    PRESSURE("pressure", "气压", "PRESS"),
    UV("uv", "紫外线", "UV"),
    VISIBILITY("visibility", "能见度", "VIS"),
    DEW_POINT("dew_point", "露点", "DEW"),
    CLOUD_COVER("cloud_cover", "云量", "CLOUD"),
    WIND_GUST("wind_gust", "阵风", "GUST"),
    PRECIPITATION("precipitation", "当前雨强", "PRECIP"),
    LUMINARY("luminary", "日月", "LUMINARY");

    companion object {
        private const val NONE = "__none__"
        val defaultSelection: Set<TelemetryMetric> = entries.toSet()

        fun selectionFrom(raw: String?): Set<TelemetryMetric> = when {
            raw == null -> defaultSelection
            raw == NONE -> emptySet()
            else -> raw.split(',')
                .mapNotNull { key -> entries.firstOrNull { it.key == key.trim() } }
                .toSet()
        }

        fun selectionKey(selection: Set<TelemetryMetric>): String =
            if (selection.isEmpty()) NONE else entries.filter(selection::contains).joinToString(",") { it.key }
    }
}

enum class LifeIndexMetric(val key: String, val cn: String, val en: String) {
    CAR_WASH("car_wash", "洗车", "CAR WASH"),
    SPORTS("sports", "运动", "SPORTS"),
    DRESS("dress", "穿衣", "DRESS"),
    FISHING("fishing", "钓鱼", "FISHING"),
    UV("uv", "紫外线", "UV"),
    TRAVEL("travel", "旅游", "TRAVEL"),
    ALLERGY("allergy", "过敏", "ALLERGY"),
    COMFORT("comfort", "舒适度", "COMFORT"),
    COLD("cold", "感冒", "COLD"),
    AIR_POLLUTION("air_pollution", "空气扩散", "AIR"),
    AIR_CONDITIONER("air_conditioner", "空调", "A/C"),
    SUNGLASSES("sunglasses", "太阳镜", "GLASSES"),
    MAKEUP("makeup", "化妆", "MAKEUP"),
    DRYING("drying", "晾晒", "DRYING"),
    TRAFFIC("traffic", "交通", "TRAFFIC"),
    SUNSCREEN("sunscreen", "防晒", "SPF"),
    // 中国天气网 dataZS 实际返回的 30 项里，原先未纳入的指数类型。
    MORNING_EXERCISE("morning_exercise", "晨练", "MORNING EXERCISE"),
    SHOPPING("shopping", "逛街", "SHOPPING"),
    BEER("beer", "啤酒", "BEER"),
    BOATING("boating", "划船", "BOATING"),
    WIND_CHILL("wind_chill", "风寒", "WIND CHILL"),
    KITE("kite", "放风筝", "KITE"),
    NIGHTLIFE("nightlife", "夜生活", "NIGHTLIFE"),
    MOOD("mood", "心情", "MOOD"),
    DATE("date", "约会", "DATE"),
    HAIR_CARE("hair_care", "美发", "HAIR CARE"),
    UMBRELLA("umbrella", "雨伞", "UMBRELLA"),
    HEATSTROKE("heatstroke", "中暑", "HEATSTROKE"),
    DRYNESS("dryness", "干燥", "DRYNESS");

    companion object {
        private const val NONE = "__none__"
        val defaultSelection: Set<LifeIndexMetric> = entries.toSet()
        // 旧存档只可能含这 16 个 key。升级后新增类型默认开启，
        // 既不覆盖用户主动取消过的旧项，也不会让指数突然消失。
        private val legacyKeys = setOf(
            "car_wash", "sports", "dress", "fishing", "uv", "travel", "allergy", "comfort",
            "cold", "air_pollution", "air_conditioner", "sunglasses", "makeup", "drying",
            "traffic", "sunscreen",
        )

        fun selectionFrom(raw: String?): Set<LifeIndexMetric> = when {
            raw == null -> defaultSelection
            raw == NONE -> emptySet()
            else -> {
                val parsed = raw.split(',')
                    .mapNotNull { key -> entries.firstOrNull { it.key == key.trim() } }
                    .toMutableSet()
                parsed.addAll(entries.filter { it.key !in legacyKeys })
                parsed.toSet()
            }
        }

        fun selectionKey(selection: Set<LifeIndexMetric>): String =
            if (selection.isEmpty()) NONE else entries.filter(selection::contains).joinToString(",") { it.key }

        fun fromEnglish(raw: String): LifeIndexMetric? {
            // 中国天气网把来源前缀写进 en（wcn:cl），先剥离再统一按别名匹配。
            val key = raw.trim().uppercase().substringAfterLast(':')
            return when (key) {
                "CAR WASH", "XC" -> CAR_WASH
                "SPORTS", "YD" -> SPORTS
                "DRESS", "CT" -> DRESS
                "FISHING", "DY" -> FISHING
                "UV" -> UV
                "TRAVEL", "TR" -> TRAVEL
                "ALLERGY", "AG" -> ALLERGY
                "COMFORT", "CO" -> COMFORT
                "COLD", "GM" -> COLD
                "AIR POLLUTION", "AIR", "PL" -> AIR_POLLUTION
                "A/C", "AC" -> AIR_CONDITIONER
                "SUNGLASSES", "GLASSES", "GL" -> SUNGLASSES
                "MAKEUP", "PP" -> MAKEUP
                "DRYING", "LS" -> DRYING
                "TRAFFIC", "JT", "LK" -> TRAFFIC
                "SPF", "FS" -> SUNSCREEN
                "MORNING EXERCISE", "CL" -> MORNING_EXERCISE
                "SHOPPING", "GJ" -> SHOPPING
                "BEER", "PJ" -> BEER
                "BOATING", "HC" -> BOATING
                "WIND CHILL", "WC" -> WIND_CHILL
                "KITE", "PK" -> KITE
                "NIGHTLIFE", "NL" -> NIGHTLIFE
                "MOOD", "XQ" -> MOOD
                "DATE", "YH" -> DATE
                "HAIR CARE", "MF" -> HAIR_CARE
                "UMBRELLA", "YS" -> UMBRELLA
                "HEATSTROKE", "ZS" -> HEATSTROKE
                "DRYNESS", "GZ" -> DRYNESS
                else -> null
            }
        }
    }
}

// 主题模式（v0.0.5）：默认深色保持磷光终端品牌，可切纸面浅色或跟随系统
enum class ThemeMode(val key: String, val cn: String) {
    DARK("dark", "深色"),
    LIGHT("light", "浅色"),
    SYSTEM("system", "跟随系统");

    companion object {
        fun from(v: String?): ThemeMode = entries.firstOrNull { it.key == v } ?: LIGHT
    }
}

/**
 * 整套界面语言。经典终端保留 beta6 的原始视觉；澄空终端是 beta6-3 的默认新界面。
 * 只改变 Compose 表现层，不参与天气数据、定位、缓存或页面路由判断。
 */
enum class InterfaceStyle(val key: String, val cn: String, val en: String) {
    PHOSPHOR_VISTA("phosphor_vista", "澄空终端", "CLEAR SKY TERMINAL"),
    CLASSIC_TERMINAL("classic_terminal", "经典终端", "CLASSIC TERMINAL");

    companion object {
        fun from(v: String?): InterfaceStyle =
            entries.firstOrNull { it.key == v } ?: PHOSPHOR_VISTA
    }
}

/** 澄空首页模块的材质独立于界面主题；「模块外观」设置已移除，默认固定为无界香氛。 */
enum class HomeSurfaceStyle(val key: String, val cn: String) {
    CURRENT("current", "无界香氛"),
    FRAGRANCE_GLASS("fragrance_glass", "流光玻璃");

    companion object {
        fun from(value: String?): HomeSurfaceStyle = entries.firstOrNull { it.key == value } ?: CURRENT
    }
}

/** 首页日月卡的两种画法：波形（原版）/ 同心弧（2026-10-23 新版）。卡片右上角按钮切换。 */
enum class LuminaryStyle(val key: String) {
    WAVE("wave"),
    ARC("arc");

    companion object {
        fun from(v: String?): LuminaryStyle = entries.firstOrNull { it.key == v } ?: WAVE
    }
}

// 强调色亮度只调整数据绿与线框蓝，橙/红等语义色保持稳定。
enum class AccentTone(val key: String, val cn: String) {
    STANDARD("standard", "标准"),
    SOFT("soft", "柔和");

    companion object {
        fun from(v: String?): AccentTone = entries.firstOrNull { it.key == v } ?: STANDARD
    }
}

enum class AppIconStyle(val key: String, val cn: String) {
    CHARACTER("character", "天气娘"),
    DARK("dark", "深色"),
    LIGHT("light", "浅色"),
    MINIMAL("minimal", "简约");

    companion object {
        fun from(v: String?): AppIconStyle = when (v) {
            // beta5 及更早版本使用 classic；无感迁移为同一枚深色图标。
            "classic" -> DARK
            else -> entries.firstOrNull { it.key == v } ?: CHARACTER
        }
    }
}

/** 主页首屏播报样式：默认保留天气娘，也为偏好纯天气工具的用户提供无人物模式。 */
enum class HomeBriefingStyle(val key: String, val cn: String) {
    WEATHER_GIRL("weather_girl", "天气娘"),
    TIPS("tips", "文字提示"),
    OFF("off", "关闭");

    companion object {
        fun from(v: String?): HomeBriefingStyle =
            entries.firstOrNull { it.key == v } ?: TIPS
    }
}

/** 横屏待机界面保留经典版，同时允许用户切换到新的沉浸式气象中枢。 */
enum class LandscapeStandbyStyle(val key: String, val cn: String) {
    CLASSIC("classic", "经典终端"),
    WEATHER_CORE("weather_core", "气象中枢");

    companion object {
        fun from(v: String?): LandscapeStandbyStyle =
            entries.firstOrNull { it.key == v } ?: WEATHER_CORE
    }
}

/** 城市管理入口：沿用首页侧边栏，或改为独立的整屏城市管理页。 */
enum class CityManagerStyle(val key: String, val cn: String) {
    SIDEBAR("sidebar", "侧边栏"),
    PAGE("page", "独立页面");

    companion object {
        fun from(v: String?): CityManagerStyle =
            entries.firstOrNull { it.key == v } ?: SIDEBAR
    }
}

enum class HomeModule(val key: String, val cn: String, val en: String) {
    HOURLY("hourly", "逐时预报", "HOURLY"),
    PRECIP("precip", "短时降水", "NOWCAST"),
    SPACETIME("spacetime", "气象视界", "WEATHER ATLAS"),
    DAILY("daily", "五天天气", "5-DAY FORECAST"),
    TELEMETRY("telemetry", "遥测数据", "TELEMETRY"),
    AQI("aqi", "空气质量", "AIR QUALITY"),
    OUTFIT("outfit", "穿搭", "OUTFIT"),
    INDICES("indices", "生活指数", "INDICES"),
    YESTERDAY("yesterday", "昨日复盘", "RETRO"),
    TYPHOON("typhoon", "台风关注", "TYPHOON"),
    FEATURES("features", "特色功能", "FEATURE HUB");

    companion object {
        // 默认阅读顺序先回答用户最常看的三件事：接下来几小时、是否马上下雨、
        // 未来几天怎样；回看与雷达属于主动查看工具，放在核心预报之后。
        // 用户已经保存的自定义排序仍按原顺序读取，不会被默认值覆盖。
        val defaultOrder: List<HomeModule> = listOf(
            HOURLY,
            PRECIP,
            DAILY,
            OUTFIT,
            TELEMETRY,
            AQI,
            SPACETIME,
            INDICES,
            YESTERDAY,
            TYPHOON,
            FEATURES,
        )

        // v2 标记 = 用户在本机主动保存过的排序。旧格式串（无标记）才走
        // 旧版迁移清洗；否则用户恰好拖成与旧默认/声明序一致的顺序时，
        // 读写两端都会把它当历史遗留静默重置，怎么拖都存不上。
        const val ORDER_MARKER = "v2:"

        fun orderFrom(raw: String?): List<HomeModule> {
            if (raw.isNullOrBlank()) return defaultOrder
            val userSaved = raw.startsWith(ORDER_MARKER)
            val source = if (userSaved) raw.substring(ORDER_MARKER.length) else raw
            val selected = source.split(',')
                .mapNotNull { key ->
                    when (key.trim()) {
                        // 0.1.4 早期体验版曾拆成两个序号；升级后无损并回一个模块。
                        "history", "radar", "weather_tools", "sky", "coast" -> SPACETIME
                        else -> entries.firstOrNull { it.key == key.trim() }
                    }
                }
                .distinct()
                .toMutableList()
            if (!userSaved) {
                // 旧版本曾把完整默认序列写入偏好；这不代表用户主动排序。
                // 仅当它仍与旧默认完全一致时迁移到新默认，任何真实改动过的顺序都原样保留。
                val previousDefault = listOf(HOURLY, PRECIP, DAILY, SPACETIME, TELEMETRY, AQI, INDICES, YESTERDAY, TYPHOON)
                val previousDeclaration = entries.filterNot { it == OUTFIT }
                val previousReadingOrder = listOf(HOURLY, PRECIP, DAILY, TELEMETRY, AQI, SPACETIME, INDICES, YESTERDAY, TYPHOON)
                if (selected == entries.toList() || selected == previousDeclaration || selected == previousDefault || selected == previousReadingOrder) return defaultOrder
                // 旧版自定义顺序缺少专题时，以空气质量为锚；其余模块的相对顺序不变。
                if (SPACETIME !in selected) {
                    val anchor = selected.indexOf(AQI).takeIf { it >= 0 } ?: selected.indexOf(TELEMETRY)
                    val insertion = if (anchor >= 0) anchor + 1 else selected.size
                    selected.add(insertion, SPACETIME)
                }
            }
            // 新增穿搭模块固定补在五天天气之后；其余已保存模块的相对顺序一律不动。
            if (OUTFIT !in selected) {
                val daily = selected.indexOf(DAILY)
                selected.add(if (daily >= 0) daily + 1 else selected.size, OUTFIT)
            }
            selected += defaultOrder.filterNot(selected::contains)
            return selected
        }
    }
}

// 设置仓储
object SettingsRepository {
    /** 液态玻璃默认对全部浮层生效；单项关闭后该处退回纯色（菜单、警告框、面板按组件库的回退顺序取上一档）。 */
    val MOCHI_GLASS_TARGETS_DEFAULT: Set<String> =
        setOf("bar", "tabbar", "control", "menu", "alert", "sheet", "clear")

    private val KEY_SOURCE_PRIORITY = stringPreferencesKey("weather_source_priority_v1")
    val sourcePriority: Flow<List<SourcePref>> by lazy {
        store.data.map { prefs -> prefs[KEY_SOURCE_PRIORITY].orEmpty().split(',')
            .mapNotNull { key -> SourcePref.entries.firstOrNull { it.key == key } }.distinct() }.distinctUntilChanged()
    }
    suspend fun setSourcePriority(sources: List<SourcePref>) = editWeatherContext { prefs ->
        prefs[KEY_SOURCE_PRIORITY] = sources.distinct().filter { it != SourcePref.AUTO && it != SourcePref.ZHISHENG }
            .joinToString(",") { it.key }
    }
    private val KEY_MODULE_SOURCES = stringPreferencesKey("weather_module_sources_v1")
    val moduleSources: Flow<ModuleSourcePolicy> by lazy {
        store.data.map { ModuleSourcePolicy.decode(it[KEY_MODULE_SOURCES]) }.distinctUntilChanged()
    }
    suspend fun setModuleSource(module: WeatherModule, source: SourcePref?) = editWeatherContext { prefs ->
        prefs[KEY_MODULE_SOURCES] = ModuleSourcePolicy.decode(prefs[KEY_MODULE_SOURCES]).with(module, source).encode()
    }
    private val KEY_USAGE_STATISTICS = PrivacyDefaults.statistics
    val usageStatistics: Flow<Boolean> by lazy { store.data.map(PrivacyDefaults::statisticsEnabled).distinctUntilChanged() }
    suspend fun setUsageStatistics(enabled: Boolean) = store.edit { it[KEY_USAGE_STATISTICS] = enabled }
    private val KEY_SERVICE_DIAGNOSTICS = PrivacyDefaults.diagnostics
    internal val serviceDiagnostics: Flow<Boolean> by lazy { store.data.map(PrivacyDefaults::diagnosticsEnabled).distinctUntilChanged() }
    internal suspend fun setServiceDiagnostics(enabled: Boolean) = store.edit { it[KEY_SERVICE_DIAGNOSTICS] = enabled }

    private lateinit var store: DataStore<Preferences>

    private suspend fun editWeatherContext(transform: suspend (MutablePreferences) -> Unit): Preferences =
        reminderDeliveryGate.changeContext { store.edit(transform) }

    private val KEY_TEMP_UNIT = stringPreferencesKey("temp_unit")
    private val KEY_SHOW_TYPHOON = booleanPreferencesKey("show_typhoon")
    private val KEY_SOURCE = stringPreferencesKey("source_pref")
    private val KEY_AMBIENCE = WeatherAppearanceDefaultsMigration.AMBIENCE
    private val KEY_LIVING_SKY = WeatherAppearanceDefaultsMigration.LIVING_SKY
    private val KEY_SCANLINES = booleanPreferencesKey("scanlines")
    private val KEY_RELEASE_APPEARANCE = booleanPreferencesKey("release_appearance_20260907")
    private val KEY_RELEASE_BRIEFING = booleanPreferencesKey("release_briefing_20260907")
    private val KEY_SOFT_GLOW = WeatherAppearanceDefaultsMigration.SOFT_GLOW
    private val KEY_SOFT_GLOW_LEVEL = WeatherAppearanceDefaultsMigration.GLOW_LEVEL
    private val KEY_STYLE_RESTORED = booleanPreferencesKey("style_choice_beta6_6")
    private val KEY_LOCATION_ENABLED = booleanPreferencesKey("location_enabled")
    private val KEY_PRECISE_LOCATION = booleanPreferencesKey("precise_location")
    private val KEY_PRECISE_LOCATION_ASKED = booleanPreferencesKey("precise_location_asked")
    private val KEY_LOCATION_ENGINE = stringPreferencesKey("location_engine")
    private val KEY_WIND_UNIT = stringPreferencesKey("wind_unit")
    private val KEY_PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
    private val KEY_SHOW_AQI = booleanPreferencesKey("show_aqi")
    private val KEY_SHOW_INDICES = booleanPreferencesKey("show_indices")
    private val KEY_SHOW_YESTERDAY = booleanPreferencesKey("show_yesterday")
    private val KEY_SHOW_PRECIP = booleanPreferencesKey("show_precip")
    private val KEY_SHOW_TELEMETRY = booleanPreferencesKey("show_telemetry")
    private val KEY_SHOW_WEATHER_TOOLS = booleanPreferencesKey("show_weather_tools")
    private val KEY_SHOW_HISTORY = booleanPreferencesKey("show_history")
    private val KEY_SHOW_RADAR = booleanPreferencesKey("show_radar")
    private val KEY_SHOW_SPACETIME = booleanPreferencesKey("show_spacetime")
    private val KEY_SHOW_SKY = booleanPreferencesKey("show_sky_photography")
    private val KEY_SHOW_COAST = booleanPreferencesKey("show_coast_weather")
    private val KEY_SHOW_FEATURES = booleanPreferencesKey("show_features")
    private val KEY_SHOW_OUTFIT = booleanPreferencesKey("show_outfit")
    private val KEY_BOOT_ANIM = booleanPreferencesKey("boot_anim")
    private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    private val KEY_STANDBY_BURN_IN_PROTECTION = booleanPreferencesKey("standby_burn_in_protection")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_INTERFACE_STYLE = stringPreferencesKey("interface_style")
    private val KEY_ACCENT_TONE = stringPreferencesKey("accent_tone")
    private val KEY_APP_ICON = stringPreferencesKey("app_icon")
    private val KEY_HOME_BRIEFING_STYLE = stringPreferencesKey("home_briefing_style")
    private val KEY_HOME_SURFACE_STYLE = stringPreferencesKey("home_surface_style")
    private val KEY_LAST_AUTO_LOCATE = longPreferencesKey("last_auto_locate_at")
    private val KEY_MODULE_ORDER = stringPreferencesKey("home_module_order")
    private val KEY_RADAR_SOURCE = stringPreferencesKey("radar_source")
    private val KEY_DEVELOPER = booleanPreferencesKey("developer_mode")
    private val KEY_LANDSCAPE_STANDBY = booleanPreferencesKey("landscape_standby")
    private val KEY_LANDSCAPE_STANDBY_STYLE = stringPreferencesKey("landscape_standby_style")
    private val KEY_CITY_MANAGER_STYLE = stringPreferencesKey("city_manager_style")
    private val KEY_TELEMETRY_METRICS = stringPreferencesKey("telemetry_metrics")
    private val KEY_LIFE_INDEX_METRICS = stringPreferencesKey("life_index_metrics")
    private val KEY_LUMINARY_STYLE = stringPreferencesKey("luminary_style")
    private val KEY_TIANDITU_TOKEN = stringPreferencesKey("tianditu_token")
    // mochi UI 个性化：画面效果档位 / HDR / 按压高光 / 转场与侧推
    private val KEY_MOCHI_GRAPHICS_LEVEL = stringPreferencesKey("mochi_graphics_level")
    private val KEY_MOCHI_HDR_ENABLED = booleanPreferencesKey("mochi_hdr_enabled")
    private val KEY_MOCHI_HDR_STRENGTH = floatPreferencesKey("mochi_hdr_strength")
    private val KEY_MOCHI_PRESS_TINTED = booleanPreferencesKey("mochi_press_glow_tinted")
    private val KEY_MOCHI_PRESS_STRENGTH = floatPreferencesKey("mochi_press_glow_strength")
    private val KEY_MOCHI_PREDICTIVE_BACK = booleanPreferencesKey("mochi_predictive_back")
    private val KEY_MOCHI_PAGE_TRANSITION = stringPreferencesKey("mochi_page_transition")
    private val KEY_MOCHI_SWIPE_BACK = booleanPreferencesKey("mochi_swipe_back")
    private val KEY_MOCHI_BACK_SCALE = stringPreferencesKey("mochi_back_scale")
    // Beta 功能提示：用户勾选「不再提示」后不再弹出进入页提醒
    private val KEY_MOCHI_HDR_BETA_ACK = booleanPreferencesKey("mochi_hdr_beta_ack")
    private val KEY_MOCHI_GLASS_BETA_ACK = booleanPreferencesKey("mochi_glass_beta_ack")
    // mochi UI 个性化（第二批）：主题配色 / 主题色 / 取色算法 / 果冻感 / 底部标签栏
    private val KEY_MOCHI_COLOR_MODE = stringPreferencesKey("mochi_color_mode")
    private val KEY_MOCHI_SEED = intPreferencesKey("mochi_seed")
    private val KEY_MOCHI_PALETTE = stringPreferencesKey("mochi_palette")
    private val KEY_MOCHI_CONTRAST = floatPreferencesKey("mochi_contrast")
    private val KEY_MOCHI_GEL = floatPreferencesKey("mochi_gel")
    private val KEY_MOCHI_TAB_LAYOUT = stringPreferencesKey("mochi_tab_layout")
    private val KEY_MOCHI_TAB_LABEL_BLUR = booleanPreferencesKey("mochi_tab_label_blur")
    private val KEY_MOCHI_TAB_DROPLET = booleanPreferencesKey("mochi_tab_droplet")
    private val KEY_MOCHI_TAB_COLLAPSE = booleanPreferencesKey("mochi_tab_collapse")
    // 打开应用式转场返回时，是否用主题色铺一下来源卡片。默认关闭（用卡片真实底色）。
    private val KEY_MOCHI_SOURCE_HIGHLIGHT = booleanPreferencesKey("mochi_source_highlight")
    // mochi UI 个性化（第三批）：液态玻璃材质与使用范围
    private val KEY_MOCHI_GLASS = booleanPreferencesKey("mochi_glass")
    private val KEY_MOCHI_GLASS_BLUR = booleanPreferencesKey("mochi_glass_blur")
    private val KEY_MOCHI_GLASS_TINT = floatPreferencesKey("mochi_glass_tint")
    private val KEY_MOCHI_GLASS_BLUR_ONLY = booleanPreferencesKey("mochi_glass_blur_only")
    private val KEY_MOCHI_GLASS_STYLE = stringPreferencesKey("mochi_glass_style")
    private val KEY_MOCHI_GLASS_LIGHT = floatPreferencesKey("mochi_glass_light")
    private val KEY_MOCHI_GLASS_DARK = floatPreferencesKey("mochi_glass_dark")
    private val KEY_MOCHI_GLASS_THEME_ACCENT = booleanPreferencesKey("mochi_glass_theme_accent")
    private val KEY_MOCHI_GLASS_TARGETS = stringSetPreferencesKey("mochi_glass_targets")

    fun init(context: Context) {
        store = context.applicationContext.settingsStore
    }

    // c=摄氏度 f=华氏度
    val tempUnit: Flow<String> by lazy { store.data.map { it[KEY_TEMP_UNIT] ?: "c" } }
    val showTyphoon: Flow<Boolean> by lazy { store.data.map { ReleaseFeatures.typhoon && (it[KEY_SHOW_TYPHOON] ?: true) } }
    val developerMode: Flow<Boolean> by lazy {
        store.data.map { it[KEY_DEVELOPER] ?: false }.distinctUntilChanged()
    }
    val sourcePref: Flow<SourcePref> by lazy {
        store.data.map { prefs ->
            SourcePref.from(prefs[KEY_SOURCE]).effective(prefs[KEY_DEVELOPER] ?: false)
        }.distinctUntilChanged()
    }
    val ambience: Flow<AmbienceLevel> by lazy { store.data.map { if (ReleaseFeatures.weatherAtmosphere) AmbienceLevel.from(it[KEY_AMBIENCE]) else AmbienceLevel.OFF } }
    val livingSky: Flow<Boolean> by lazy {
        store.data.map { ReleaseFeatures.weatherAtmosphere && (it[KEY_LIVING_SKY] ?: true) }.distinctUntilChanged()
    }
    val scanlines: Flow<Boolean> by lazy { store.data.map { ReleaseFeatures.weatherAtmosphere && (it[KEY_SCANLINES] ?: true) } }
    val softGlow: Flow<Boolean> by lazy { store.data.map { it[KEY_SOFT_GLOW] ?: true }.distinctUntilChanged() }
    val softGlowLevel: Flow<SoftGlowLevel> by lazy { store.data.map { SoftGlowLevel.from(it[KEY_SOFT_GLOW_LEVEL]) }.distinctUntilChanged() }
    // 定位总开关：关闭时 App 完全不碰位置权限（默认关，兑现「不主动获取权限」）
    val locationEnabled: Flow<Boolean> by lazy { store.data.map { it[KEY_LOCATION_ENABLED] ?: false } }
    // 定位地理服务引擎：默认自动优选。
    val locationEngine: Flow<LocationEnginePref> by lazy {
        store.data.map { LocationEnginePref.from(it[KEY_LOCATION_ENGINE]) }.distinctUntilChanged()
    }
    // 街道级定位为单独的可选能力；关闭时仍只使用粗略位置。
    val preciseLocationEnabled: Flow<Boolean> by lazy {
        store.data.map { it[KEY_PRECISE_LOCATION] ?: false }.distinctUntilChanged()
    }
    val preciseLocationPermissionAsked: Flow<Boolean> by lazy {
        store.data.map { it[KEY_PRECISE_LOCATION_ASKED] ?: false }.distinctUntilChanged()
    }
    // kmh / ms / bft
    val windUnit: Flow<String> by lazy { store.data.map { it[KEY_WIND_UNIT] ?: "kmh" } }
    // hpa / mmhg / inhg
    val pressureUnit: Flow<String> by lazy { store.data.map { it[KEY_PRESSURE_UNIT] ?: "hpa" } }
    val showAqi: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_AQI] ?: true } }
    val showIndices: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_INDICES] ?: true } }
    val showYesterday: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_YESTERDAY] ?: true } }
    val showPrecip: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_PRECIP] ?: true } }
    val showTelemetry: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_TELEMETRY] ?: true } }
    val showSpacetime: Flow<Boolean> by lazy {
        store.data.map {
            it[KEY_SHOW_SPACETIME]
                ?: it[KEY_SHOW_WEATHER_TOOLS]
                ?: ((it[KEY_SHOW_HISTORY] ?: true) || (it[KEY_SHOW_RADAR] ?: true))
        }
    }
    val showSkyPhotography: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_SKY] ?: true } }
    val showCoastWeather: Flow<Boolean> by lazy { store.data.map { ReleaseFeatures.coastalWeather && (it[KEY_SHOW_COAST] ?: true) } }
    val showFeatures: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_FEATURES] ?: true } }
    val showOutfit: Flow<Boolean> by lazy { store.data.map { it[KEY_SHOW_OUTFIT] ?: true } }
    val bootAnim: Flow<Boolean> by lazy { store.data.map { it[KEY_BOOT_ANIM] ?: true } }
    val keepScreenOn: Flow<Boolean> by lazy { store.data.map { it[KEY_KEEP_SCREEN_ON] ?: false } }
    val standbyBurnInProtection: Flow<Boolean> by lazy {
        store.data.map { it[KEY_STANDBY_BURN_IN_PROTECTION] ?: true }.distinctUntilChanged()
    }
    val landscapeStandby: Flow<Boolean> by lazy {
        store.data.map { it[KEY_LANDSCAPE_STANDBY] ?: true }.distinctUntilChanged()
    }
    val landscapeStandbyStyle: Flow<LandscapeStandbyStyle> by lazy {
        store.data.map { if (ReleaseFeatures.classicTheme) LandscapeStandbyStyle.from(it[KEY_LANDSCAPE_STANDBY_STYLE]) else LandscapeStandbyStyle.WEATHER_CORE }.distinctUntilChanged()
    }
    val cityManagerStyle: Flow<CityManagerStyle> by lazy {
        store.data.map { CityManagerStyle.from(it[KEY_CITY_MANAGER_STYLE]) }.distinctUntilChanged()
    }
    val telemetryMetrics: Flow<Set<TelemetryMetric>> by lazy {
        store.data.map { TelemetryMetric.selectionFrom(it[KEY_TELEMETRY_METRICS]) }.distinctUntilChanged()
    }
    val lifeIndexMetrics: Flow<Set<LifeIndexMetric>> by lazy {
        store.data.map { LifeIndexMetric.selectionFrom(it[KEY_LIFE_INDEX_METRICS]) }.distinctUntilChanged()
    }
    val luminaryStyle: Flow<LuminaryStyle> by lazy {
        store.data.map { LuminaryStyle.from(it[KEY_LUMINARY_STYLE]) }.distinctUntilChanged()
    }
    // 天地图凭据：填了就改用天地图底图，留空回落 OpenFreeMap（台风地图页使用）。
    val tiandituToken: Flow<String?> by lazy {
        store.data.map { it[KEY_TIANDITU_TOKEN]?.takeIf { v -> v.isNotBlank() } }.distinctUntilChanged()
    }
    // 深浅主题的唯一来源是「主题配色」页的黑暗模式（mochi_color_mode）。
    // 这里派生出 ThemeMode 给 ZhishengWeatherTheme、闪屏与桌面图标联动使用，避免两套主题打架。
    val themeMode: Flow<ThemeMode> by lazy {
        mochiColorMode.map { mode ->
            when (mode) {
                "dark" -> ThemeMode.DARK
                "system" -> ThemeMode.SYSTEM
                else -> ThemeMode.LIGHT
            }
        }.distinctUntilChanged()
    }
    val interfaceStyle: Flow<InterfaceStyle> by lazy {
        store.data.map {
            if (it[KEY_STYLE_RESTORED] == true) ReleaseFeatures.interfaceStyle(InterfaceStyle.from(it[KEY_INTERFACE_STYLE]))
            else InterfaceStyle.PHOSPHOR_VISTA
        }.distinctUntilChanged()
    }
    val accentTone: Flow<AccentTone> by lazy {
        store.data.map { AccentTone.from(it[KEY_ACCENT_TONE]) }.distinctUntilChanged()
    }
    val appIconStyle: Flow<AppIconStyle> by lazy {
        store.data.map { AppIconStyle.from(it[KEY_APP_ICON]) }.distinctUntilChanged()
    }
    val homeBriefingStyle: Flow<HomeBriefingStyle> by lazy {
        store.data.map { if (it[KEY_RELEASE_BRIEFING] == true) HomeBriefingStyle.from(it[KEY_HOME_BRIEFING_STYLE]) else HomeBriefingStyle.TIPS }.distinctUntilChanged()
    }
    // 「模块外观」设置已移除：全站固定使用「无界香氛」，忽略历史保存的流光玻璃值。
    val homeSurfaceStyle: Flow<HomeSurfaceStyle> by lazy {
        store.data.map { HomeSurfaceStyle.CURRENT }.distinctUntilChanged()
    }
    // 1B：自动定位闸门持久化——上次自动定位时间落盘，进程重启不归零
    val lastAutoLocateAt: Flow<Long> by lazy {
        store.data.map { it[KEY_LAST_AUTO_LOCATE] ?: 0L }
    }
    suspend fun setLastAutoLocateAt(millis: Long) = store.edit {
        it[KEY_LAST_AUTO_LOCATE] = millis
    }
    val moduleOrder: Flow<List<HomeModule>> by lazy {
        store.data.map { HomeModule.orderFrom(it[KEY_MODULE_ORDER]) }.distinctUntilChanged()
    }

    // 雷达页数据源：RainViewer / 彩云拼图，用户选择后持久化
    val radarSource: Flow<RadarSource> by lazy {
        store.data.map { RadarSource.fromKey(it[KEY_RADAR_SOURCE]) }.distinctUntilChanged()
    }

    suspend fun setRadarSource(source: RadarSource) = store.edit { it[KEY_RADAR_SOURCE] = source.key }

    // UI 个性化偏好：mid/high/extra 三档画面效果；HDR 强度与按压高光亮度都是 0..1。
    val mochiGraphicsLevel: Flow<String> by lazy {
        store.data.map { it[KEY_MOCHI_GRAPHICS_LEVEL] ?: "high" }.distinctUntilChanged()
    }
    val mochiHdrEnabled: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_HDR_ENABLED] ?: false }.distinctUntilChanged()
    }
    val mochiHdrStrength: Flow<Float> by lazy {
        store.data.map { it[KEY_MOCHI_HDR_STRENGTH] ?: 0.6f }.distinctUntilChanged()
    }
    val mochiPressTinted: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_PRESS_TINTED] ?: false }.distinctUntilChanged()
    }
    val mochiPressStrength: Flow<Float> by lazy {
        store.data.map { it[KEY_MOCHI_PRESS_STRENGTH] ?: 0.45f }.distinctUntilChanged()
    }
    val mochiPredictiveBack: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_PREDICTIVE_BACK] ?: true }.distinctUntilChanged()
    }
    val mochiPageTransition: Flow<String> by lazy {
        store.data.map { it[KEY_MOCHI_PAGE_TRANSITION] ?: "fade" }.distinctUntilChanged()
    }
    val mochiSwipeBack: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_SWIPE_BACK] ?: true }.distinctUntilChanged()
    }
    val mochiBackScale: Flow<String> by lazy {
        store.data.map { it[KEY_MOCHI_BACK_SCALE] ?: "subtle" }.distinctUntilChanged()
    }
    val mochiHdrBetaAck: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_HDR_BETA_ACK] ?: false }.distinctUntilChanged()
    }
    val mochiGlassBetaAck: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_BETA_ACK] ?: false }.distinctUntilChanged()
    }

    suspend fun setMochiGraphicsLevel(v: String) = store.edit { it[KEY_MOCHI_GRAPHICS_LEVEL] = v }
    suspend fun setMochiHdrEnabled(v: Boolean) = store.edit { it[KEY_MOCHI_HDR_ENABLED] = v }
    suspend fun setMochiHdrStrength(v: Float) = store.edit { it[KEY_MOCHI_HDR_STRENGTH] = v.coerceIn(0f, 1f) }
    suspend fun setMochiPressTinted(v: Boolean) = store.edit { it[KEY_MOCHI_PRESS_TINTED] = v }
    suspend fun setMochiPressStrength(v: Float) = store.edit { it[KEY_MOCHI_PRESS_STRENGTH] = v.coerceIn(0.05f, 0.8f) }
    suspend fun setMochiPredictiveBack(v: Boolean) = store.edit { it[KEY_MOCHI_PREDICTIVE_BACK] = v }
    suspend fun setMochiPageTransition(v: String) = store.edit { it[KEY_MOCHI_PAGE_TRANSITION] = v }
    suspend fun setMochiSwipeBack(v: Boolean) = store.edit { it[KEY_MOCHI_SWIPE_BACK] = v }
    suspend fun setMochiBackScale(v: String) = store.edit { it[KEY_MOCHI_BACK_SCALE] = v }
    suspend fun setMochiHdrBetaAck(v: Boolean) = store.edit { it[KEY_MOCHI_HDR_BETA_ACK] = v }
    suspend fun setMochiGlassBetaAck(v: Boolean) = store.edit { it[KEY_MOCHI_GLASS_BETA_ACK] = v }

    // 黑暗模式：light / dark / system 三档，是全站深浅的唯一来源。
    // 兼容旧数据：历史 app（跟随应用）/ monet（系统取色）或缺省时，回退到旧 theme_mode 的值。
    val mochiColorMode: Flow<String> by lazy {
        store.data.map { prefs ->
            when (val saved = prefs[KEY_MOCHI_COLOR_MODE]) {
                "light", "dark", "system" -> saved
                else -> when (prefs[KEY_THEME_MODE]) {
                    "dark" -> "dark"
                    "system" -> "system"
                    else -> "light"
                }
            }
        }.distinctUntilChanged()
    }
    val mochiSeed: Flow<Int> by lazy {
        store.data.map { it[KEY_MOCHI_SEED] ?: 0xFF2B6A5Au.toInt() }.distinctUntilChanged()
    }
    val mochiPalette: Flow<String> by lazy {
        store.data.map { it[KEY_MOCHI_PALETTE] ?: "TonalSpot" }.distinctUntilChanged()
    }
    val mochiContrast: Flow<Float> by lazy {
        store.data.map { it[KEY_MOCHI_CONTRAST] ?: 0f }.distinctUntilChanged()
    }
    val mochiGel: Flow<Float> by lazy {
        store.data.map { it[KEY_MOCHI_GEL] ?: 0.6f }.distinctUntilChanged()
    }
    val mochiTabLayout: Flow<String> by lazy {
        store.data.map { it[KEY_MOCHI_TAB_LAYOUT] ?: "inline" }.distinctUntilChanged()
    }
    val mochiTabLabelBlur: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_TAB_LABEL_BLUR] ?: true }.distinctUntilChanged()
    }
    val mochiTabDroplet: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_TAB_DROPLET] ?: true }.distinctUntilChanged()
    }
    val mochiTabCollapse: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_TAB_COLLAPSE] ?: false }.distinctUntilChanged()
    }
    val mochiSourceHighlight: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_SOURCE_HIGHLIGHT] ?: false }.distinctUntilChanged()
    }
    val mochiGlass: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS] ?: true }.distinctUntilChanged()
    }
    val mochiGlassBlur: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_BLUR] ?: true }.distinctUntilChanged()
    }
    val mochiGlassTint: Flow<Float> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_TINT] ?: 0.5f }.distinctUntilChanged()
    }
    val mochiGlassBlurOnly: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_BLUR_ONLY] ?: false }.distinctUntilChanged()
    }
    val mochiGlassStyle: Flow<String> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_STYLE] ?: "ios26" }.distinctUntilChanged()
    }
    val mochiGlassLight: Flow<Float> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_LIGHT] ?: 0f }.distinctUntilChanged()
    }
    val mochiGlassDark: Flow<Float> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_DARK] ?: 0f }.distinctUntilChanged()
    }
    val mochiGlassThemeAccent: Flow<Boolean> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_THEME_ACCENT] ?: true }.distinctUntilChanged()
    }
    val mochiGlassTargets: Flow<Set<String>> by lazy {
        store.data.map { it[KEY_MOCHI_GLASS_TARGETS] ?: MOCHI_GLASS_TARGETS_DEFAULT }
            .distinctUntilChanged()
    }

    suspend fun setMochiColorMode(v: String) = store.edit { it[KEY_MOCHI_COLOR_MODE] = v }
    suspend fun setMochiSeed(v: Int) = store.edit { it[KEY_MOCHI_SEED] = v }
    suspend fun setMochiPalette(v: String) = store.edit { it[KEY_MOCHI_PALETTE] = v }
    suspend fun setMochiContrast(v: Float) = store.edit { it[KEY_MOCHI_CONTRAST] = v.coerceIn(-1f, 1f) }
    suspend fun setMochiGel(v: Float) = store.edit { it[KEY_MOCHI_GEL] = v.coerceIn(0f, 1f) }
    suspend fun setMochiTabLayout(v: String) = store.edit { it[KEY_MOCHI_TAB_LAYOUT] = v }
    suspend fun setMochiTabLabelBlur(v: Boolean) = store.edit { it[KEY_MOCHI_TAB_LABEL_BLUR] = v }
    suspend fun setMochiTabDroplet(v: Boolean) = store.edit { it[KEY_MOCHI_TAB_DROPLET] = v }
    suspend fun setMochiTabCollapse(v: Boolean) = store.edit { it[KEY_MOCHI_TAB_COLLAPSE] = v }
    suspend fun setMochiSourceHighlight(v: Boolean) = store.edit { it[KEY_MOCHI_SOURCE_HIGHLIGHT] = v }
    suspend fun setMochiGlass(v: Boolean) = store.edit { it[KEY_MOCHI_GLASS] = v }
    suspend fun setMochiGlassBlur(v: Boolean) = store.edit { it[KEY_MOCHI_GLASS_BLUR] = v }
    suspend fun setMochiGlassTint(v: Float) = store.edit { it[KEY_MOCHI_GLASS_TINT] = v.coerceIn(0f, 1f) }
    suspend fun setMochiGlassBlurOnly(v: Boolean) = store.edit { it[KEY_MOCHI_GLASS_BLUR_ONLY] = v }
    suspend fun setMochiGlassStyle(v: String) = store.edit { it[KEY_MOCHI_GLASS_STYLE] = v }
    suspend fun setMochiGlassLight(v: Float) = store.edit { it[KEY_MOCHI_GLASS_LIGHT] = v.coerceIn(-1f, 1f) }
    suspend fun setMochiGlassDark(v: Float) = store.edit { it[KEY_MOCHI_GLASS_DARK] = v.coerceIn(-1f, 1f) }
    suspend fun setMochiGlassThemeAccent(v: Boolean) = store.edit { it[KEY_MOCHI_GLASS_THEME_ACCENT] = v }
    suspend fun setMochiGlassTarget(key: String, on: Boolean) = store.edit {
        val current = it[KEY_MOCHI_GLASS_TARGETS] ?: MOCHI_GLASS_TARGETS_DEFAULT
        it[KEY_MOCHI_GLASS_TARGETS] = if (on) current + key else current - key
    }

    suspend fun setTempUnit(unit: String) = store.edit { it[KEY_TEMP_UNIT] = unit }
    suspend fun setShowTyphoon(show: Boolean) = store.edit { it[KEY_SHOW_TYPHOON] = show }
    suspend fun setSourcePref(p: SourcePref) = editWeatherContext { it[KEY_SOURCE] = p.key }
    suspend fun setDeveloperMode(v: Boolean) = editWeatherContext { PlaceConfigurationRevision.changed(); it[KEY_DEVELOPER] = v }
    suspend fun qweatherUnlocked(): Boolean {
        SecretStore.currentQw()
        return QWeatherApi.enabled && developerMode.first()
    }

    suspend fun caiyunUnlocked(): Boolean {
        SecretStore.currentCaiyun()
        return SecretStore.caiyunReady && developerMode.first()
    }
    suspend fun amapUnlocked(): Boolean {
        SecretStore.currentAmap()
        return SecretStore.amapReady && developerMode.first()
    }
    suspend fun baiduUnlocked(): Boolean {
        SecretStore.currentBaidu()
        return SecretStore.baiduReady && developerMode.first()
    }
    suspend fun purgeRetiredProviderData() = store.edit { prefs ->
        listOf("caiyun_app_key", "caiyun_app_secret", "caiyun_credential")
            .map(::stringPreferencesKey)
            .forEach(prefs::remove)
    }
    suspend fun setAmbience(a: AmbienceLevel) = store.edit { it[KEY_AMBIENCE] = a.key }
    suspend fun setLivingSky(enabled: Boolean) = store.edit { it[KEY_LIVING_SKY] = enabled }
    suspend fun setScanlines(v: Boolean) = store.edit { it[KEY_SCANLINES] = v }
    suspend fun setSoftGlow(v: Boolean) = store.edit { it[KEY_SOFT_GLOW] = v }
    suspend fun setSoftGlowLevel(v: SoftGlowLevel) = store.edit { it[KEY_SOFT_GLOW_LEVEL] = v.key }
    suspend fun setLocationEnabled(v: Boolean) = editWeatherContext { PlaceConfigurationRevision.changed(); it[KEY_LOCATION_ENABLED] = v }
    suspend fun setLocationEngine(v: LocationEnginePref) = editWeatherContext {
        PlaceConfigurationRevision.changed()
        it[KEY_LOCATION_ENGINE] = v.key
    }
    suspend fun setPreciseLocationEnabled(v: Boolean) = editWeatherContext {
        PlaceConfigurationRevision.changed()
        it[KEY_PRECISE_LOCATION] = v
        it[KEY_PRECISE_LOCATION_ASKED] = false
    }
    suspend fun setPreciseLocationPermissionAsked() = store.edit { it[KEY_PRECISE_LOCATION_ASKED] = true }
    suspend fun setWindUnit(v: String) = store.edit { it[KEY_WIND_UNIT] = v }
    suspend fun setPressureUnit(v: String) = store.edit { it[KEY_PRESSURE_UNIT] = v }
    suspend fun setShowAqi(v: Boolean) = store.edit { it[KEY_SHOW_AQI] = v }
    suspend fun setShowIndices(v: Boolean) = store.edit { it[KEY_SHOW_INDICES] = v }
    suspend fun setShowYesterday(v: Boolean) = store.edit { it[KEY_SHOW_YESTERDAY] = v }
    suspend fun setShowPrecip(v: Boolean) = store.edit { it[KEY_SHOW_PRECIP] = v }
    suspend fun setShowTelemetry(v: Boolean) = store.edit { it[KEY_SHOW_TELEMETRY] = v }
    suspend fun setShowSpacetime(v: Boolean) = store.edit { it[KEY_SHOW_SPACETIME] = v }
    suspend fun setShowSkyPhotography(v: Boolean) = store.edit { it[KEY_SHOW_SKY] = v }
    suspend fun setShowCoastWeather(v: Boolean) = store.edit { it[KEY_SHOW_COAST] = v }
    suspend fun setShowFeatures(v: Boolean) = store.edit { it[KEY_SHOW_FEATURES] = v }
    suspend fun setShowOutfit(v: Boolean) = store.edit { it[KEY_SHOW_OUTFIT] = v }
    suspend fun setBootAnim(v: Boolean) = store.edit { it[KEY_BOOT_ANIM] = v }
    suspend fun setKeepScreenOn(v: Boolean) = store.edit { it[KEY_KEEP_SCREEN_ON] = v }
    suspend fun setStandbyBurnInProtection(v: Boolean) = store.edit { it[KEY_STANDBY_BURN_IN_PROTECTION] = v }
    suspend fun setLandscapeStandby(v: Boolean) = store.edit { it[KEY_LANDSCAPE_STANDBY] = v }
    suspend fun setLandscapeStandbyStyle(style: LandscapeStandbyStyle) = store.edit {
        it[KEY_LANDSCAPE_STANDBY_STYLE] = style.key
    }
    suspend fun setCityManagerStyle(style: CityManagerStyle) = store.edit {
        it[KEY_CITY_MANAGER_STYLE] = style.key
    }
    suspend fun setTelemetryMetrics(selection: Set<TelemetryMetric>) = store.edit {
        it[KEY_TELEMETRY_METRICS] = TelemetryMetric.selectionKey(selection)
    }
    suspend fun setLifeIndexMetrics(selection: Set<LifeIndexMetric>) = store.edit {
        it[KEY_LIFE_INDEX_METRICS] = LifeIndexMetric.selectionKey(selection)
    }
    suspend fun setLuminaryStyle(style: LuminaryStyle) = store.edit { it[KEY_LUMINARY_STYLE] = style.key }

    suspend fun setTiandituToken(token: String) = store.edit { it[KEY_TIANDITU_TOKEN] = token.trim() }
    // 兼容旧调用点：深浅统一写回黑暗模式这一个键。
    suspend fun setThemeMode(mode: ThemeMode) = store.edit {
        it[KEY_MOCHI_COLOR_MODE] = mode.key
        it[KEY_RELEASE_APPEARANCE] = true
    }
    suspend fun setInterfaceStyle(style: InterfaceStyle) = store.edit {
        it[KEY_INTERFACE_STYLE] = style.key
        it[KEY_STYLE_RESTORED] = true
    }
    suspend fun setAccentTone(tone: AccentTone) = store.edit { it[KEY_ACCENT_TONE] = tone.key }
    suspend fun setAppIconStyle(style: AppIconStyle) = store.edit { it[KEY_APP_ICON] = style.key }
    suspend fun setHomeBriefingStyle(style: HomeBriefingStyle) = store.edit {
        it[KEY_HOME_BRIEFING_STYLE] = style.key
        it[KEY_RELEASE_BRIEFING] = true
    }
    suspend fun setHomeSurfaceStyle(style: HomeSurfaceStyle) = store.edit {
        it[KEY_HOME_SURFACE_STYLE] = style.key
    }
    suspend fun setModuleOrder(order: List<HomeModule>) = store.edit {
        it[KEY_MODULE_ORDER] = HomeModule.ORDER_MARKER + HomeModule.orderFrom(
            HomeModule.ORDER_MARKER + order.joinToString(",") { module -> module.key })
            .joinToString(",") { module -> module.key }
    }
}
