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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.weatherPresentationTime

private enum class OutfitPeriod { NOW, NEXT_SIX_HOURS }

// 弹层固定白底 + 深色文字，不跟随深浅主题变化，避免深色主题下白底白字。
private val SheetInk = Color(0xFF1B2B32)
private val SheetInkSecondary = Color(0xFF56666E)
private val SheetAccent = Color(0xFF2E7D72)
private val SheetSurface = Color(0xFFF1F7F6)
private val SheetBorder = Color(0xFFD9E4E3)

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

/** 「搭配一下」弹层：与生活指数弹出层一致的标准底部弹层，默认约 80% 高。 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        tonalElevation = 0.dp,
    ) {
        // 拖动指示器由 ModalBottomSheet 提供，这里不再重复绘制。
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = screenHeight * 0.8f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "天气穿搭",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SheetInk,
                    )
                    Text(
                        "${cityName ?: "当前位置"} · 参考打开时的天气",
                        style = MaterialTheme.typography.bodySmall,
                        color = SheetInkSecondary,
                    )
                }
                Text(
                    "关闭",
                    Modifier.clickable(role = Role.Button) { onDismiss() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SheetInkSecondary,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutfitTabChip("现在出门", period == OutfitPeriod.NOW, Modifier.weight(1f)) {
                    period = OutfitPeriod.NOW
                }
                OutfitTabChip("接下来6小时", period == OutfitPeriod.NEXT_SIX_HOURS, Modifier.weight(1f)) {
                    period = OutfitPeriod.NEXT_SIX_HOURS
                }
            }
            Text(
                outfit.headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SheetInk,
            )
            Text(
                "体感参考 · ${outfit.feelLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = SheetAccent,
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SheetSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${style.label} · 当前搭配",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = SheetInkSecondary,
                    )
                    OutfitPaletteStyle.entries.forEach { candidate ->
                        val active = candidate == style
                        Box(
                            Modifier
                                .padding(start = 8.dp)
                                .size(if (active) 22.dp else 18.dp)
                                .clip(RoundedCornerShape(50))
                                .background(candidate.accent)
                                .border(
                                    2.dp,
                                    if (active) SheetInk else Color.Transparent,
                                    RoundedCornerShape(50),
                                )
                                .clickable(role = Role.RadioButton) { style = candidate },
                        )
                    }
                }
                // 固定宽度 + 足够高度，避免宽屏下人物被横向拉扁。
                Box(
                    Modifier.fillMaxWidth().height(280.dp),
                    contentAlignment = Alignment.Center,
                ) {
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
                            OutfitPiece(
                                icon = icon,
                                label = label,
                                value = value,
                                selected = label == selectedPart,
                                onClick = { selectedPart = label },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowPieces.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(12.dp),
                ) {
                    Text(outfit.advice, style = MaterialTheme.typography.bodySmall, color = SheetInkSecondary)
                }
            }

            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SheetAccent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(outfit.tag, style = MaterialTheme.typography.labelSmall, color = SheetAccent)
            }
            OutfitChoiceGroup("出门怎么安排", listOf("日常", "通勤", "走动多"), plan) { plan = it }
            OutfitChoiceGroup("你平时的冷暖感受", listOf("我怕冷", "刚刚好", "我怕热"), comfort) { comfort = it }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "这套搭配的考虑",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SheetInk,
                )
                Text(outfit.advice, style = MaterialTheme.typography.bodyMedium, color = SheetInkSecondary)
                Text(consideration, style = MaterialTheme.typography.bodyMedium, color = SheetInkSecondary)
            }
            HorizontalDivider(color = SheetBorder)
            Text(
                if (evidenceOpen) "收起建议依据 ↑" else "查看建议依据 ↓",
                Modifier.clickable(role = Role.Button) { evidenceOpen = !evidenceOpen },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SheetAccent,
            )
            if (evidenceOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "冷暖分级优先参考数据源提供的体感温度，风雨、紫外线和所选时段另作考虑。衣物是搭配示例，可用相近厚薄的现有衣服替换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SheetInkSecondary,
                    )
                    Text(
                        "体感参考 ${outfit.feelLabel} · 风速 ${Fmt.wind(cur.windSpeed, windUnit) ?: "—"} · 紫外线 ${cur.uvIndex ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SheetInkSecondary,
                    )
                    listOf(
                        "体感与穿衣分级 · 深圳市气象局",
                        "分层穿衣 · 中国天气网",
                        "紫外线与遮阳 · 世界卫生组织",
                    ).forEach { source ->
                        Text(source, style = MaterialTheme.typography.bodySmall, color = SheetAccent)
                    }
                    Text(
                        "建议用于日常出门参考，个人活动量与冷暖感受仍需自行调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SheetInkSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OutfitTabChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) SheetBorder.copy(alpha = 0.6f) else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else SheetBorder, shape)
            .clickable(role = Role.Tab) { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) SheetInk else SheetInkSecondary,
        )
    }
}

@Composable
private fun OutfitChoiceGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = SheetInk)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                val active = option == selected
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier
                        .clip(shape)
                        .background(if (active) SheetBorder.copy(alpha = 0.6f) else Color.Transparent)
                        .border(1.dp, if (active) Color.Transparent else SheetBorder, shape)
                        .clickable(role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) SheetInk else SheetInkSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutfitPiece(
    icon: Int,
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) SheetAccent.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) SheetAccent.copy(alpha = 0.45f) else Color.Transparent,
                shape,
            )
            .clickable(role = Role.RadioButton) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhosphorIcon(icon, null, Modifier.size(18.dp), if (selected) SheetAccent else SheetInkSecondary)
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SheetInkSecondary)
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = SheetInk,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
