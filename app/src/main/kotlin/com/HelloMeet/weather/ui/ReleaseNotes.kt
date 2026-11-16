package com.HelloMeet.weather.ui

internal enum class ReleaseNotesCategory(val label: String) {
    ALL("全部"), WIDGETS("小组件"), WEATHER("天气与城市"), EXPERIENCE("日常体验"),
}
internal data class ReleaseNote(val title: String, val detail: String)
internal data class ReleaseNotesSection(
    val id: String, val category: ReleaseNotesCategory, val title: String, val notes: List<ReleaseNote>,
)

/** Implementation changes since public beta14. Keep the current release summary short. */
internal val WhatsNewSections = listOf(
    ReleaseNotesSection("release", ReleaseNotesCategory.EXPERIENCE, "本次更新", listOf(
        ReleaseNote("应用更名", "应用名称改为「遇见天气」，包名迁移到 com.HelloMeet.weather，启动图标提供默认、多彩、简约三款。"),
        ReleaseNote("页面转场", "霞光、星空拍摄、海岸气象改为路由页，首页天气回看、雷达、降水与城市管理统一从来源卡片展开与缩回。"),
        ReleaseNote("检查更新", "检查更新改为全新卡片页面，显示版本说明、下载进度与百分比，失败可直接重试。"),
        ReleaseNote("更新日志", "关于页新增当前版本更新与历史更新日志两个独立入口，历史版本随包内置、离线可查。"),
    )),
    ReleaseNotesSection("weather", ReleaseNotesCategory.WEATHER, "天气与空气", listOf(
        ReleaseNote("全国排名", "新增生态环境部空气质量全国排名，按城市上级匹配并显示全国排名与百分比。"),
        ReleaseNote("天气详情", "新增 48 小时天气页与未来 15 日逐日详情，八类指标支持渐变图表与选点交互。"),
        ReleaseNote("公告", "公告列表支持下拉刷新，正文支持 Markdown 与 HTML 混排及图片预览。"),
    )),
)

internal fun releaseNotesFor(category: ReleaseNotesCategory): List<ReleaseNotesSection> =
    WhatsNewSections.filter { category == ReleaseNotesCategory.ALL || it.category == category }
