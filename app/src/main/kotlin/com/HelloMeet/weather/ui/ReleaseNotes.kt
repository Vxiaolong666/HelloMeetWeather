package com.HelloMeet.weather.ui

internal enum class ReleaseNotesCategory(val label: String) {
    ALL("全部"), WIDGETS("小组件"), WEATHER("天气与城市"), EXPERIENCE("日常体验"),
}
internal data class ReleaseNote(val title: String, val detail: String)
internal data class ReleaseNotesSection(
    val id: String, val category: ReleaseNotesCategory, val title: String, val notes: List<ReleaseNote>,
)

/** Implementation changes since public v1.0. Keep the current release summary short. */
internal val WhatsNewSections = listOf(
    ReleaseNotesSection("release", ReleaseNotesCategory.EXPERIENCE, "本次更新", listOf(
        ReleaseNote("首次引导", "全新首次启动引导：欢迎、定位、外观、动效、偏好与完成六步流程，可随时跳过。"),
        ReleaseNote("检查更新", "首页新增检查更新模块：启动自动检查，发现新版本可直接查看或忽略该版本。"),
        ReleaseNote("定位日志", "设置 → 位置服务新增定位服务日志，记录每次定位与逆地理请求、失败原因与结果。"),
    )),
    ReleaseNotesSection("weather", ReleaseNotesCategory.WEATHER, "天气与空气", listOf(
        ReleaseNote("定位更稳", "坐标转换失败时自动回退原始坐标；高德逆地理字段解析与街道级诊断更准确。"),
        ReleaseNote("来源比较", "天气来源比较改为统一对话框，用指标卡直观展示各来源的数值差异。"),
        ReleaseNote("空气详情", "天气详情 AQI 仪表盘改用连续色阶渐变弧，读数与等级更清晰。"),
    )),
)

internal fun releaseNotesFor(category: ReleaseNotesCategory): List<ReleaseNotesSection> =
    WhatsNewSections.filter { category == ReleaseNotesCategory.ALL || it.category == category }
