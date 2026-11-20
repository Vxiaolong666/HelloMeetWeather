package com.HelloMeet.weather.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewDialogTest {
    @Test
    fun `release notes show current changes without forced empty page height`() {
        assertEquals("1.2.2", WhatsNewVersion)
        assertEquals("1.2.1 → 1.2.2", WhatsNewVersionRange)
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val dialog = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/ui/WhatsNewDialog.kt").readText()
        val activity = File(projectDir, "src/main/kotlin/com/HelloMeet/weather/MainActivity.kt").readText()

        assertTrue(dialog.contains(".verticalScroll(scroll)"))
        assertFalse(dialog.contains("Modifier.weight(1f),\n                ) { currentPage"))
        val copy = WhatsNewSections.flatMap { it.notes }.joinToString { it.title + it.detail }
        listOf("设置四段式", "预览转场", "定位秒数", "五日动画", "更新卡片").forEach {
            assertTrue("missing current summary $it", copy.contains(it))
        }
        listOf("全机型已适配", "彻底防止烧屏", "三星自动更新已修复", "霞光时刻", "历史天气").forEach {
            assertFalse("unverified release claim $it", copy.contains(it))
        }
        assertTrue(activity.contains("shouldShowWhatsNew()"))
        assertTrue(activity.contains("markWhatsNewSeen()"))
        // 外壳统一用组件库对话框：关闭 / 返回手势 / 点外面 / 出现动画都由库负责。
        assertTrue(dialog.contains("MochiDialog("))
        assertTrue(dialog.contains("MochiDialogActionRole.Primary"))
        assertTrue(dialog.contains("maxWidth = 420.dp"))
        assertFalse(dialog.contains("androidx.compose.ui.window.Dialog"))
    }

    @Test fun `categories preserve every release note without duplication`() {
        val categories = ReleaseNotesCategory.entries.filter { it != ReleaseNotesCategory.ALL }
        val filtered = categories.flatMap(::releaseNotesFor)
        assertEquals(WhatsNewSections.toSet(), filtered.toSet())
        assertEquals(filtered.size, filtered.distinctBy { it.id }.size)
        assertEquals(WhatsNewSections, releaseNotesFor(ReleaseNotesCategory.ALL))
        categories.forEach { category ->
            val sections = releaseNotesFor(category)
            assertTrue(sections.all { it.category == category && it.notes.isNotEmpty() })
            assertEquals(WhatsNewSections.filter { it.category == category }, sections)
        }
        assertTrue(categories.any { releaseNotesFor(it).isNotEmpty() })
    }
}
