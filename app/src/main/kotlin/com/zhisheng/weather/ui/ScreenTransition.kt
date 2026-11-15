package com.zhisheng.weather.ui

internal enum class AppScreen {
    HOME, SEARCH, DAILY_FORECAST, DAILY_DAY_DETAIL, HISTORY, RADAR, TYPHOON, PRECIPITATION,
    HOURLY_DETAIL, AIR_QUALITY, YESTERDAY_DETAIL, FEATURES, CITY_MANAGER,
    MOCHI_SETTINGS;

    /** 库导航用的路由名（rememberMochiNavigator / MochiNavHost 的 route）。 */
    val route: String get() = when (this) {
        HOME -> "home"
        SEARCH -> "search"
        DAILY_FORECAST -> "daily_forecast"
        DAILY_DAY_DETAIL -> "daily_day"
        HISTORY -> "history"
        RADAR -> "radar"
        TYPHOON -> "typhoon"
        PRECIPITATION -> "precipitation"
        HOURLY_DETAIL -> "hourly"
        AIR_QUALITY -> "air_quality"
        YESTERDAY_DETAIL -> "yesterday"
        FEATURES -> "features"
        CITY_MANAGER -> "city_manager"
        MOCHI_SETTINGS -> "settings"
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
            CITY_MANAGER -> null
            MOCHI_SETTINGS -> "settings"
            HISTORY -> "history$suffix"
            RADAR -> "radar$suffix"
            DAILY_FORECAST, DAILY_DAY_DETAIL -> "daily_forecast$suffix"
            PRECIPITATION -> "precipitation$suffix"
            HOURLY_DETAIL -> "hourly$suffix"
            AIR_QUALITY -> "air_quality$suffix"
            YESTERDAY_DETAIL -> "yesterday$suffix"
            FEATURES -> "features$suffix"
            TYPHOON -> "typhoon$suffix"
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
