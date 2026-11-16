package com.HelloMeet.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.HelloMeet.weather.R
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette

/** CHANGELOG.md 中的单个版本。 */
internal data class ChangelogVersion(
    val version: String,
    val groups: List<ChangelogGroup>,
) {
    val itemCount: Int get() = groups.sumOf { it.items.size }
}

internal data class ChangelogGroup(val title: String?, val items: List<String>)

/** 解析仓库根目录的 CHANGELOG.md：`## v…` 版本、`### …` 分组、`- …` 条目，忽略其它行。 */
internal fun parseChangelog(markdown: String): List<ChangelogVersion> {
    val versions = mutableListOf<ChangelogVersion>()
    var version: String? = null
    var groupTitle: String? = null
    var items = mutableListOf<String>()
    var groups = mutableListOf<ChangelogGroup>()

    fun flushGroup() {
        if (items.isNotEmpty() || groupTitle != null) {
            groups += ChangelogGroup(groupTitle, items.toList())
        }
        items = mutableListOf()
        groupTitle = null
    }

    fun flushVersion() {
        flushGroup()
        version?.let { name ->
            versions += ChangelogVersion(name, groups)
        }
        groups = mutableListOf()
    }

    markdown.lineSequence().forEach { raw ->
        val line = raw.trim()
        when {
            line.startsWith("## ") -> {
                flushVersion()
                version = line.removePrefix("## ").trim().removePrefix("v")
            }
            line.startsWith("### ") -> {
                flushGroup()
                groupTitle = line.removePrefix("### ").trim().removePrefix("v")
            }
            line.startsWith("- ") -> {
                val text = line.removePrefix("- ").trim().stripInlineMarkdown()
                if (text.isNotEmpty()) items += text
            }
            else -> Unit
        }
    }
    flushVersion()
    return versions.filter { it.version.isNotBlank() && it.itemCount > 0 }
}

private fun String.stripInlineMarkdown(): String =
    replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace("**", "")
        .replace("`", "")

@Composable
fun ChangelogDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        ChangelogDialogContent(onClose)
    }
}

@Composable
internal fun ChangelogDialogContent(onClose: () -> Unit) {
    val p = LocalZhishengPalette.current
    val context = LocalContext.current
    val versions = remember {
        runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().use { parseChangelog(it.readText()) }
        }.getOrDefault(emptyList())
    }
    var expanded by remember(versions) { mutableStateOf(versions.firstOrNull()?.version) }
    val shape = RoundedCornerShape(18.dp)

    Box(
        Modifier.fillMaxSize().clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .fillMaxHeight(.9f)
                .clip(shape)
                .background(p.surface)
                .border(1.dp, p.cardBorder.copy(alpha = .7f), shape)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "更新日志", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    PhosphorIcon(R.drawable.ph_x, "关闭更新日志", Modifier.size(21.dp), p.textSecondary)
                }
            }
            HorizontalDivider(color = p.cardBorder.copy(alpha = .65f))
            if (versions.isEmpty()) {
                Text(
                    "暂无更新日志",
                    color = p.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                )
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) {
                    items(versions, key = { it.version }) { item ->
                        ChangelogVersionBlock(
                            item = item,
                            expanded = expanded == item.version,
                            onToggle = { expanded = if (expanded == item.version) null else item.version },
                        )
                    }
                }
            }
            HorizontalDivider(color = p.cardBorder.copy(alpha = .65f))
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp).heightIn(min = 50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.text, contentColor = p.surface),
            ) {
                Text(
                    "知道了", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(20.dp), p.surface)
            }
        }
    }
}

@Composable
private fun ChangelogVersionBlock(
    item: ChangelogVersion,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val p = LocalZhishengPalette.current
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "changelogChevron")
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "v${item.version}", color = p.text, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text("${item.itemCount} 条", color = p.textTertiary, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            PhosphorIcon(
                R.drawable.ph_arrow_right, null, Modifier.size(14.dp).graphicsLayer { this.rotationZ = rotation },
                p.textSecondary,
            )
        }
        if (expanded) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item.groups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.title?.let {
                            Text(
                                it, color = p.cyan, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            )
                        }
                        group.items.forEach { entry ->
                            Row {
                                Text("·", color = p.textTertiary, fontSize = 13.sp, lineHeight = 21.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    entry, color = p.textSecondary, fontSize = 13.sp, lineHeight = 21.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = p.cardBorder.copy(alpha = .6f))
    }
}
