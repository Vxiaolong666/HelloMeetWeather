package com.HelloMeet.weather.ui

internal enum class ReleaseNotesCategory(val label: String) {
    ALL("全部"), WIDGETS("小组件"), WEATHER("天气与城市"), EXPERIENCE("日常体验"),
}
internal data class ReleaseNote(val title: String, val detail: String)
internal data class ReleaseNotesSection(
    val id: String, val category: ReleaseNotesCategory, val title: String, val notes: List<ReleaseNote>,
)

/** Implementation changes since public v1.2.2. Keep the current release summary short. */
internal val WhatsNewSections = listOf(
    ReleaseNotesSection("release", ReleaseNotesCategory.EXPERIENCE, "本次更新", listOf(
        ReleaseNote("统一多选", "遥测数据、生活指数数据页与「模块布局」一致：编辑 / 保存、全选 / 全不选、批量开启 / 关闭。"),
        ReleaseNote("更新对话框", "检查更新、历史日志、版本特色改用组件库对话框，支持加宽，样式与返回手势统一。"),
        ReleaseNote("揭示转场", "独立城市管理页选城、首页顶栏进入城市管理，改从触点揭圆洞露出目标页。"),
    )),
    ReleaseNotesSection("weather", ReleaseNotesCategory.WEATHER, "天气与城市", listOf(
        ReleaseNote("五日样式", "五天天气卡片补齐低温、温度区间条与高温显示，字重与分隔线对齐参考图。"),
        ReleaseNote("城市搜索", "搜索无结果改用组件库空状态遮罩；从城市管理添加城市后返回城市列表而非首页。"),
        ReleaseNote("生活指数", "修复开关无法关闭、关了又自动打开的问题。"),
    )),
)

internal fun releaseNotesFor(category: ReleaseNotesCategory): List<ReleaseNotesSection> =
    WhatsNewSections.filter { category == ReleaseNotesCategory.ALL || it.category == category }
