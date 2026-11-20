package com.HelloMeet.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.model.WeatherCondition
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.components.WeatherIcon
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole

internal const val WhatsNewVersion = "1.2.2"
internal val WhatsNewVersionRange = "1.2.1 → 1.2.2"
internal const val WhatsNewPreferenceFile = "zhisheng_whats_new"
internal const val WhatsNewSeenKey = "last_seen_version"

@Composable
fun WhatsNewDialog(onClose: () -> Unit) {
    MochiDialog(
        visible = true,
        onDismissRequest = onClose,
        title = "更新说明",
        message = WhatsNewVersionRange,
        maxWidth = 420.dp,
        dismissOnOutsideTap = true,
        actions = persistentListOf(
            MochiDialogAction("知道了，开始使用", MochiDialogActionRole.Primary),
        ),
    ) {
        WhatsNewDialogContent(onClose, animateEntrance = false)
    }
}

/** Same content for the real modal and native layout previews. */
@Composable
internal fun WhatsNewDialogContent(
    onClose: () -> Unit,
    initialCategory: ReleaseNotesCategory = ReleaseNotesCategory.ALL,
    animateEntrance: Boolean = true,
) {
    val p = LocalZhishengPalette.current
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val largeText = LocalDensity.current.fontScale >= 1.4f
    val shortWindow = LocalConfiguration.current.screenHeightDp < 420
    val compactHeader = largeText || shortWindow
    // 外壳（标题 / 关闭 / 按钮 / 出现动画 / 点外关闭）统一由 MochiDialog 负责；
    // 这里只保留版本特色正文：渐变头图、分类、更新条目，供预览单独复用。
    Column(Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(
            listOf(p.surface, p.cyan.copy(alpha = .035f))))) {
            Canvas(Modifier.matchParentSize()) {
                repeat(3) { i ->
                    val path = Path().apply {
                        moveTo(size.width * .66f + i * 22.dp.toPx(), -20.dp.toPx())
                        cubicTo(size.width * .42f, size.height * .7f,
                            size.width * 1.03f, size.height * .36f, size.width * 1.08f, size.height * 1.3f)
                    }
                    drawPath(path, p.cyan.copy(alpha = .065f), style = Stroke(1.dp.toPx()))
                }
            }
            if (shortWindow) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("更新说明", color = p.text, fontSize = 22.sp, lineHeight = 30.sp,
                        fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    Text(WhatsNewVersionRange, color = p.cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 16.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("更新说明", color = p.text, fontSize = 28.sp, lineHeight = 36.sp,
                            fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(10.dp))
                        Text(WhatsNewVersionRange, color = p.cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (!compactHeader) {
                        Spacer(Modifier.width(10.dp))
                        WeatherIcon(WeatherCondition.PARTLY_CLOUDY, Modifier.size(76.dp).clearAndSetSemantics {})
                    }
                }
            }
        }
        val contentCategories = ReleaseNotesCategory.entries.filter { it != ReleaseNotesCategory.ALL && releaseNotesFor(it).isNotEmpty() }
        val tabs = if (contentCategories.size > 1) ReleaseNotesCategory.entries.filter { it == ReleaseNotesCategory.ALL || releaseNotesFor(it).isNotEmpty() } else listOf(ReleaseNotesCategory.ALL)
        if (tabs.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = if (shortWindow) 10.dp else 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { item ->
                val active = category == item
                Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                        .background(if (active) p.text else p.cardBorder.copy(alpha = .22f))
                        .semantics { selected = active }
                        .clickable(role = Role.Tab) {
                            category = item
                            scope.launch { scroll.scrollTo(0) }
                        }.padding(horizontal = 13.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(item.label, fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif, color = if (active) p.surface else p.textSecondary)
                }
            }
        }
        Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            releaseNotesFor(category).forEach { section -> ReleaseSection(section) }
            Text("谢谢你把每一个小问题告诉我们。", color = p.textTertiary, fontSize = 12.sp,
                lineHeight = 19.sp, fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 20.dp))
        }
        if (!shortWindow) {
            Text(
                if (scroll.canScrollForward) (if (compactHeader) "向上滑动看更新" else "向上滑动，看看完整更新")
                else "设置里可再次查看",
                fontSize = 11.sp, lineHeight = 17.sp, fontFamily = FontFamily.SansSerif,
                color = p.textTertiary,
                modifier = Modifier.padding(start = 22.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ReleaseSection(section: ReleaseNotesSection) {
    val p = LocalZhishengPalette.current
    val icon = when (section.id) {
        "sources" -> R.drawable.ph_cloud
        "refresh" -> R.drawable.ph_clock
        "widgets" -> R.drawable.ph_cloud
        "cities" -> R.drawable.ph_map_pin
        "weather" -> R.drawable.ph_cloud_rain
        "atmosphere" -> R.drawable.ph_sun
        "daily" -> R.drawable.ph_clock
        else -> R.drawable.ph_arrow_right
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhosphorIcon(icon, null, Modifier.size(20.dp), p.cyan)
            Spacer(Modifier.width(9.dp))
            Text(section.title, color = p.text, fontSize = 16.sp, lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif,
                modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
            section.notes.forEach { note ->
                Column {
                    Text(note.title, color = p.text, fontSize = 14.sp, lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
                    Spacer(Modifier.height(4.dp))
                    Text(note.detail, color = p.textSecondary, fontSize = 13.sp, lineHeight = 22.sp,
                        fontFamily = FontFamily.SansSerif)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = p.cardBorder.copy(alpha = .6f))
    }
}
