package com.HelloMeet.weather.ui

internal enum class AppScreen {
    HOME, SEARCH, DAILY_FORECAST, DAILY_TREND, DAILY_DAY_DETAIL, DAILY_TREND_DAY_DETAIL, HISTORY, RADAR, TYPHOON, PRECIPITATION,
    HOURLY_DETAIL, HOURLY_48, AIR_QUALITY, WEATHER_DETAIL, YESTERDAY_DETAIL, FEATURES, CITY_MANAGER,
    MOCHI_SETTINGS, SCENE_GLOW, SCENE_STARS, SCENE_COAST;

    /** 库导航用的路由名（rememberMochiNavigator / MochiNavHost 的 route）。 */
    val route: String get() = when (this) {
        HOME -> "home"
        SEARCH -> "search"
        DAILY_FORECAST -> "daily_forecast"
        DAILY_TREND -> "daily_trend"
        DAILY_DAY_DETAIL -> "daily_day"
        DAILY_TREND_DAY_DETAIL -> "daily_trend_day"
        HISTORY -> "history"
        RADAR -> "radar"
        TYPHOON -> "typhoon"
        PRECIPITATION -> "precipitation"
        HOURLY_DETAIL -> "hourly"
        HOURLY_48 -> "hourly_48"
        AIR_QUALITY -> "air_quality"
        WEATHER_DETAIL -> "weather_detail"
        YESTERDAY_DETAIL -> "yesterday"
        FEATURES -> "features"
        CITY_MANAGER -> "city_manager"
        MOCHI_SETTINGS -> "settings"
        SCENE_GLOW -> "scene_glow"
        SCENE_STARS -> "scene_stars"
        SCENE_COAST -> "scene_coast"
    }

    companion object {
        fun fromRoute(route: String): AppScreen = entries.firstOrNull { it.route == route } ?: HOME
    }

    /**
     * 「打开应用式」转场的来源元素 key（首页卡片 `mochiNavSource` 的登记名）：
     * push 时交给库，页面从那张卡片长成整页，返回时缩回卡片。
     * 首页多城市分页里每个城市各有一套卡片，登记 key 带 ":locationKey" 后缀防止跨城串位；
     * 没有来源元素的页面（首页、搜索、城市管理）返回 null，由 [AppTransitions.pushFrom]
     * 兜底成角落容器变换的哨兵 key。
     */
    fun navSourceKey(cityKey: String?): String? {
        val suffix = cityKey?.let { ":$it" } ?: ""
        return when (this) {
            HOME -> null
            SEARCH -> null
            // 城市管理页从首页顶栏的汉堡菜单按钮长成整页：按钮在共享顶栏上、不随城市分页变化，
            // 所以登记 key 不带城市后缀。
            CITY_MANAGER -> "city_manager"
            MOCHI_SETTINGS -> "settings"
            HISTORY -> "history$suffix"
            RADAR -> "radar$suffix"
            DAILY_FORECAST, DAILY_TREND, DAILY_DAY_DETAIL, DAILY_TREND_DAY_DETAIL -> "daily_forecast$suffix"
            PRECIPITATION -> "precipitation$suffix"
            HOURLY_DETAIL -> "hourly$suffix"
            HOURLY_48 -> "hourly_48$suffix"
            AIR_QUALITY -> "air_quality$suffix"
            WEATHER_DETAIL -> "weather_detail$suffix"
            YESTERDAY_DETAIL -> "yesterday$suffix"
            FEATURES -> "features$suffix"
            TYPHOON -> "typhoon$suffix"
            SCENE_GLOW -> "glow$suffix"
            SCENE_STARS -> "stars$suffix"
            SCENE_COAST -> "coast$suffix"
        }
    }
}

/** 应用转场样式：由「设置 → 外观与设备 → 应用转场与侧推」持久化选择。 */
internal enum class PageTransitionStyle(val key: String, val cn: String) {
    FADE("fade", "交叉淡变"),
    SLIDE("slide", "侧推"),
    DEPTH("depth", "景深"),
    APP("app", "打开应用式"),
    NONE("none", "无动画");

    companion object {
        // 兜底保持淡变：未知 key 多半是数据被写坏，宁可退回最保守的动画也不放大异常。
        fun from(key: String?): PageTransitionStyle = entries.firstOrNull { it.key == key } ?: FADE
    }
}
