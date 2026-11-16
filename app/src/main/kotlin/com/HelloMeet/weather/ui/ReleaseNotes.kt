package com.HelloMeet.weather.ui

internal enum class ReleaseNotesCategory(val label: String) {
    ALL("全部"), WIDGETS("小组件"), WEATHER("天气与城市"), EXPERIENCE("日常体验"),
}
internal data class ReleaseNote(val title: String, val detail: String)
internal data class ReleaseNotesSection(
    val id: String, val category: ReleaseNotesCategory, val title: String, val notes: List<ReleaseNote>,
)

/** Implementation changes since public beta10.5. Keep the current release summary short. */
internal val WhatsNewSections = listOf(
    ReleaseNotesSection("release", ReleaseNotesCategory.EXPERIENCE, "本次更新", listOf(
        ReleaseNote("应用更名", "应用名称改为「遇见天气」，包名迁移到 com.HelloMeet.weather，并新增默认、多彩、简约三款启动图标。"),
        ReleaseNote("页面转场", "全站统一转场入口，默认「打开应用式」；上一级没有可返回卡片时从角落展开与缩回，来源卡片可原路缩回。"),
        ReleaseNote("更新与赞助", "关于页新增检查更新与更新日志入口，赞助者支持通过爱发电查看。"),
        ReleaseNote("定位与设置", "定位反馈完整展示识别地址、精度与时间，设置页说明文字与图标选项一并整理。"),
    )),
    ReleaseNotesSection("weather", ReleaseNotesCategory.WEATHER, "天气与空气", listOf(
        ReleaseNote("天气详情", "新增八类可视化详情页、48 小时天气、多日天气预报与未来 15 日逐日详情。"),
        ReleaseNote("空气质量", "新增未来 24 小时摘要、昨日同期对比与全国排名卡片。"),
        ReleaseNote("公告", "公告列表支持下拉刷新，正文支持 Markdown 与 HTML 混排及图片预览。"),
    )),
)

internal fun releaseNotesFor(category: ReleaseNotesCategory): List<ReleaseNotesSection> =
    WhatsNewSections.filter { category == ReleaseNotesCategory.ALL || it.category == category }
