package com.HelloMeet.weather.ui

internal enum class ReleaseNotesCategory(val label: String) {
    ALL("全部"), WIDGETS("小组件"), WEATHER("天气与城市"), EXPERIENCE("日常体验"),
}
internal data class ReleaseNote(val title: String, val detail: String)
internal data class ReleaseNotesSection(
    val id: String, val category: ReleaseNotesCategory, val title: String, val notes: List<ReleaseNote>,
)

/** Implementation changes since public v1.2.1. Keep the current release summary short. */
internal val WhatsNewSections = listOf(
    ReleaseNotesSection("release", ReleaseNotesCategory.EXPERIENCE, "本次更新", listOf(
        ReleaseNote("设置四段式", "设置页改为「数据与位置 / 首页内容 / 外观与设备 / 关于」四个底部标签，关于不再单独开页。"),
        ReleaseNote("预览转场", "天气效果预览登记来源卡片，进入 / 返回走容器变换，不再从左右两侧滑入。"),
        ReleaseNote("定位秒数", "「X 秒前的位置」改为每秒动态刷新，数字滚动显示。"),
    )),
    ReleaseNotesSection("weather", ReleaseNotesCategory.WEATHER, "天气与城市", listOf(
        ReleaseNote("五日动画", "修复当前定位城市模式下五天天气卡片进入 / 退出动画异常，坐标刷新不再重播交错动画。"),
        ReleaseNote("更新卡片", "首页底部检查更新卡片补齐与上方卡片的间距。"),
    )),
)

internal fun releaseNotesFor(category: ReleaseNotesCategory): List<ReleaseNotesSection> =
    WhatsNewSections.filter { category == ReleaseNotesCategory.ALL || it.category == category }
