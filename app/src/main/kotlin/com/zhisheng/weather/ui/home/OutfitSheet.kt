package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.rememberMochiColorScheme
import com.zhisheng.weather.ui.weatherPresentationTime
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

private enum class OutfitPeriod { NOW, NEXT_SIX_HOURS }

private data class OutfitSheetColors(
    val ink: Color,
    val secondary: Color,
    val accent: Color,
    val surface: Color,
    val border: Color,
)

@Composable
private fun outfitSheetColors(): OutfitSheetColors {
    val cs = MochiTheme.colorScheme
    return OutfitSheetColors(
        ink = cs.onSurface,
        secondary = cs.onSurfaceVariant,
        accent = cs.primary,
        surface = cs.surfaceVariant,
        border = cs.outlineVariant,
    )
}

/** 插画配色：青岚 / 暖阳 / 雾蓝，与推荐档位里的配色名对应。 */
internal enum class OutfitPaletteStyle(
    val label: String,
    val shirt: Color,
    val pants: Color,
    val shoes: Color,
    val accent: Color,
    val backdrop: Color,
) {
    QINGLAN("青岚", Color(0xFFE8EDE5), Color(0xFF244653), Color(0xFFD7C49B), Color(0xFF2E857B), Color(0xFFEFF5F4)),
    NUANYANG("暖阳", Color(0xFFF2E4D0), Color(0xFF7B5232), Color(0xFFDCC7A4), Color(0xFFC08A3E), Color(0xFFF7F1E8)),
    WULAN("雾蓝", Color(0xFFE2E9F0), Color(0xFF3B4C72), Color(0xFFE4D9BE), Color(0xFF4B6089), Color(0xFFEDF2F7)),
}

internal fun outfitStyleFor(name: String): OutfitPaletteStyle = when {
    "暖阳" in name -> OutfitPaletteStyle.NUANYANG
    "雾蓝" in name -> OutfitPaletteStyle.WULAN
    else -> OutfitPaletteStyle.QINGLAN
}

/** 场景与冷暖偏好对四个部位、袖长和外搭层的修正。 */
internal data class OutfitAdjustment(
    val top: String,
    val outer: String,
    val bottom: String,
    val shoes: String,
    val shortSleeve: Boolean,
    val outerLayer: Boolean,
)

internal fun adjustOutfit(base: OutfitRecommendation, plan: String, comfort: String): OutfitAdjustment {
    var top = base.top
    var outer = base.outer
    var bottom = base.bottom
    when (comfort) {
        "我怕冷" -> {
            if (outer == "不必加外套") outer = "薄外套备着"
            if ("短袖" in top) top = "长袖T恤"
        }
        "我怕热" -> {
            outer = "不必加外套"
            if ("长袖" in top) top = "透气短袖"
        }
    }
    when (plan) {
        "走动多" -> {
            outer = "不必加外套"
            if ("厚" in bottom || "加绒" in bottom) bottom = "轻薄长裤"
        }
        "通勤" -> if (outer == "不必加外套") outer = "薄外套随身备"
    }
    val shortSleeve = "短袖" in top || comfort == "我怕热" || plan == "走动多"
    val outerLayer = outer != "不必加外套" || comfort == "我怕冷"
    return OutfitAdjustment(top, outer, bottom, base.shoes, shortSleeve, outerLayer)
}

/** 扁平人物穿搭插画：头 / 上衣 / 衣袖 / 外搭层 / 裤腿 / 鞋 / 地面影子。 */
@Composable
internal fun OutfitFigure(
    style: OutfitPaletteStyle,
    modifier: Modifier = Modifier,
    shortSleeve: Boolean = false,
    hasOuter: Boolean = false,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val skin = Color(0xFFEDE7DC)
        drawCircle(style.backdrop, w * 0.45f, Offset(cx, h * 0.50f))
        drawOval(Color(0x1A22303A), Offset(cx - w * 0.30f, h * 0.91f), Size(w * 0.60f, h * 0.035f))
        drawCircle(skin, w * 0.105f, Offset(cx, h * 0.15f))
        val shoulder = h * 0.255f
        val hip = h * 0.555f
        val sleeveHeight = if (shortSleeve) h * 0.12f else h * 0.23f
        drawRoundRect(
            style.shirt,
            Offset(cx - w * 0.135f, shoulder),
            Size(w * 0.27f, hip - shoulder),
            CornerRadius(w * 0.035f),
        )
        drawRoundRect(
            style.shirt,
            Offset(cx - w * 0.215f, shoulder + h * 0.01f),
            Size(w * 0.085f, sleeveHeight),
            CornerRadius(w * 0.03f),
        )
        drawRoundRect(
            style.shirt,
            Offset(cx + w * 0.130f, shoulder + h * 0.01f),
            Size(w * 0.085f, sleeveHeight),
            CornerRadius(w * 0.03f),
        )
        if (hasOuter) {
            drawRoundRect(
                style.accent.copy(alpha = 0.55f),
                Offset(cx - w * 0.205f, shoulder + h * 0.01f),
                Size(w * 0.055f, (hip - shoulder) * 0.92f),
                CornerRadius(w * 0.02f),
            )
            drawRoundRect(
                style.accent.copy(alpha = 0.55f),
                Offset(cx + w * 0.150f, shoulder + h * 0.01f),
                Size(w * 0.055f, (hip - shoulder) * 0.92f),
                CornerRadius(w * 0.02f),
            )
        }
        val handY = shoulder + sleeveHeight + h * 0.025f
        drawCircle(skin, w * 0.038f, Offset(cx - w * 0.172f, handY))
        drawCircle(skin, w * 0.038f, Offset(cx + w * 0.172f, handY))
        drawRoundRect(
            style.pants,
            Offset(cx - w * 0.130f, hip),
            Size(w * 0.115f, h * 0.33f),
            CornerRadius(w * 0.02f),
        )
        drawRoundRect(
            style.pants,
            Offset(cx + w * 0.015f, hip),
            Size(w * 0.115f, h * 0.33f),
            CornerRadius(w * 0.02f),
        )
        drawRoundRect(
            style.shoes,
            Offset(cx - w * 0.145f, h * 0.865f),
            Size(w * 0.14f, h * 0.05f),
            CornerRadius(w * 0.02f),
        )
        drawRoundRect(
            style.shoes,
            Offset(cx + w * 0.005f, h * 0.865f),
            Size(w * 0.14f, h * 0.05f),
            CornerRadius(w * 0.02f),
        )
        drawCircle(style.accent, w * 0.012f, Offset(w * 0.13f, h * 0.87f))
    }
}

/** 「搭配一下」弹层：库底部面板 + 库分段控件 / 分组卡片，人物插画等自定义内容放在卡片内部。 */
@Composable
internal fun OutfitStylingSheet(
    cur: CurrentWeather,
    unit: String,
    windUnit: String,
    cityName: String?,
    hourly: List<HourlyWeather>,
    onDismiss: () -> Unit,
) {
    var period by remember { mutableStateOf(OutfitPeriod.NOW) }
    var style by remember { mutableStateOf(OutfitPaletteStyle.QINGLAN) }
    var plan by remember { mutableStateOf("日常") }
    var comfort by remember { mutableStateOf("刚刚好") }
    var selectedPart by remember { mutableStateOf<String?>(null) }
    var evidenceOpen by remember { mutableStateOf(false) }

    val nowMillis = weatherPresentationTime()
    val effective = remember(cur, hourly, nowMillis, period) {
        if (period == OutfitPeriod.NOW) cur else {
            val upcoming = hourly.filter { it.timeMillis >= nowMillis }.take(6)
            val feel = upcoming.mapNotNull { it.feelsLike ?: it.temperature }.takeIf { it.isNotEmpty() }?.average()
            cur.copy(
                feelsLike = feel ?: cur.feelsLike,
                temperature = feel ?: cur.temperature,
                windSpeed = upcoming.mapNotNull { it.windSpeed }.maxOrNull() ?: cur.windSpeed,
                condition = upcoming.firstOrNull()?.condition ?: cur.condition,
            )
        }
    }
    val outfit = remember(effective, unit) { outfitRecommendation(effective, unit) } ?: return
    val adjusted = remember(outfit, plan, comfort) { adjustOutfit(outfit, plan, comfort) }
    val pieces = listOf(
        Triple(R.drawable.ph_t_shirt, "上身", adjusted.top),
        Triple(R.drawable.ph_t_shirt, "外搭", adjusted.outer),
        Triple(R.drawable.ph_pants, "下装", adjusted.bottom),
        Triple(R.drawable.ph_sneaker, "鞋履", adjusted.shoes),
    )
    val consideration = buildString {
        append("· 已考虑")
        append(
            when (comfort) {
                "我怕冷" -> "怕冷偏好，优先保留保暖层"
                "我怕热" -> "怕热偏好，优先方便穿脱；寒冷时仍保留必要保暖"
                else -> "适中体感，按日常分层"
            },
        )
        append("；")
        append(
            when (plan) {
                "通勤" -> "通勤时段按室内外温差微调"
                "走动多" -> "活动量偏大时减少一层"
                else -> "按这套出门即可"
            },
        )
        append("。")
    }

    val fallbackSheet = rememberMochiSheetState(listOf(MochiSheetDetent.Large))
    val sheet = LocalHomeSheetStates.current?.outfit ?: fallbackSheet
    LaunchedEffect(Unit) { sheet.show() }

    MochiTheme(colors = rememberMochiColorScheme(), systemBarAppearance = false) {
        val c = outfitSheetColors()
        MochiSheet(state = sheet, onDismiss = onDismiss) {
            MochiSheetHeader(
                title = "天气穿搭",
                actions = { MochiBarButton("关闭", onClick = { sheet.hide() }) },
            )
            MochiSheetContent {
                Text("${cityName ?: "当前位置"} · 参考打开时的天气",
                    style = MaterialTheme.typography.bodySmall, color = c.secondary)
                MochiSegmentedControl(
                    selectedIndex = if (period == OutfitPeriod.NOW) 0 else 1,
                    onSelect = { period = if (it == 0) OutfitPeriod.NOW else OutfitPeriod.NEXT_SIX_HOURS },
                    items = persistentListOf("现在出门", "接下来6小时"),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(outfit.headline, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = c.ink)
                Text("体感参考 · ${outfit.feelLabel}", style = MaterialTheme.typography.bodyMedium, color = c.accent)

                MochiListSection(header = "当前搭配") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${style.label} · 配色", Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium, color = c.secondary)
                            OutfitPaletteStyle.entries.forEach { candidate ->
                                val active = candidate == style
                                Box(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .size(if (active) 22.dp else 18.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(candidate.accent)
                                        .border(2.dp, if (active) c.ink else Color.Transparent, RoundedCornerShape(50))
                                        .clickable(role = Role.RadioButton) { style = candidate },
                                )
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                            OutfitFigure(
                                style,
                                Modifier.width(200.dp).fillMaxHeight(),
                                shortSleeve = adjusted.shortSleeve,
                                hasOuter = adjusted.outerLayer,
                            )
                        }
                        pieces.chunked(2).forEach { rowPieces ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowPieces.forEach { (icon, label, value) ->
                                    OutfitPiece(icon, label, value, label == selectedPart,
                                        { selectedPart = label }, c, Modifier.weight(1f))
                                }
                                if (rowPieces.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.surface.copy(alpha = .45f)).padding(12.dp)) {
                            Text(outfit.advice, style = MaterialTheme.typography.bodySmall, color = c.secondary)
                        }
                    }
                }

                MochiListSection(header = "出门偏好") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(outfit.tag, style = MaterialTheme.typography.labelSmall, color = c.accent)
                        OutfitChoiceGroup("出门怎么安排", listOf("日常", "通勤", "走动多"), plan, c) { plan = it }
                        OutfitChoiceGroup("你平时的冷暖感受", listOf("我怕冷", "刚刚好", "我怕热"), comfort, c) { comfort = it }
                    }
                }

                MochiListSection(header = "这套搭配的考虑") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(outfit.advice, style = MaterialTheme.typography.bodyMedium, color = c.secondary)
                        Text(consideration, style = MaterialTheme.typography.bodyMedium, color = c.secondary)
                    }
                }

                MochiBarButton(if (evidenceOpen) "收起建议依据" else "查看建议依据",
                    onClick = { evidenceOpen = !evidenceOpen })
                if (evidenceOpen) {
                    MochiListCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("冷暖分级优先参考数据源提供的体感温度，风雨、紫外线和所选时段另作考虑。衣物是搭配示例，可用相近厚薄的现有衣服替换。",
                                style = MaterialTheme.typography.bodySmall, color = c.secondary)
                            Text("体感参考 ${outfit.feelLabel} · 风速 ${Fmt.wind(cur.windSpeed, windUnit) ?: "—"} · 紫外线 ${cur.uvIndex ?: 0}",
                                style = MaterialTheme.typography.labelSmall, color = c.secondary)
                            listOf(
                                "体感与穿衣分级 · 深圳市气象局",
                                "分层穿衣 · 中国天气网",
                                "紫外线与遮阳 · 世界卫生组织",
                            ).forEach { source ->
                                Text(source, style = MaterialTheme.typography.bodySmall, color = c.accent)
                            }
                            Text("建议用于日常出门参考，个人活动量与冷暖感受仍需自行调整。",
                                style = MaterialTheme.typography.bodySmall, color = c.secondary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun OutfitChoiceGroup(
    title: String,
    options: List<String>,
    selected: String,
    colors: OutfitSheetColors,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = colors.ink)
        MochiSegmentedControl(
            selectedIndex = options.indexOf(selected).coerceAtLeast(0),
            onSelect = { onSelect(options[it]) },
            items = options.toImmutableList(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OutfitPiece(
    icon: Int,
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    colors: OutfitSheetColors,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.45f) else Color.Transparent, shape)
            .clickable(role = Role.RadioButton) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhosphorIcon(icon, null, Modifier.size(18.dp), if (selected) colors.accent else colors.secondary)
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
            Text(value, style = MaterialTheme.typography.titleSmall, color = colors.ink,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
