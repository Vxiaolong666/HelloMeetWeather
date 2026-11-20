package com.HelloMeet.weather.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val markdown: String = sequenceOf(File("CHANGELOG.md"), File("../CHANGELOG.md"))
        .first { it.isFile }
        .readText()

    @Test
    fun `history parses every released version with its groups`() {
        val versions = parseChangelog(markdown)

        assertTrue("至少解析出三个历史版本", versions.size >= 3)
        assertEquals("1.2.31", versions.first().version)
        assertTrue(versions.all { it.itemCount > 0 })
        assertTrue(versions.any { version -> version.groups.any { it.title != null } })
    }

    @Test
    fun `history entries drop inline markdown noise`() {
        val entries = parseChangelog(markdown).flatMap { it.groups }.flatMap { it.items }

        assertFalse(entries.any { "**" in it || "](" in it || '`' in it })
    }

    @Test
    fun `history dialog uses the shared component library shell`() {
        val dialog = sequenceOf(
            File("src/main/kotlin/com/HelloMeet/weather/ui/ChangelogDialog.kt"),
            File("app/src/main/kotlin/com/HelloMeet/weather/ui/ChangelogDialog.kt"),
        ).first { it.isFile }.readText()

        assertTrue(dialog.contains("MochiDialog("))
        assertTrue(dialog.contains("MochiDialogAction(\"知道了\""))
        assertTrue(dialog.contains("maxWidth = 420.dp"))
        assertTrue(dialog.contains("parseChangelog("))
        assertFalse(dialog.contains("androidx.compose.ui.window.Dialog"))
    }
}
