package com.HelloMeet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.HelloMeet.weather.data.*
import com.HelloMeet.weather.R
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.WeatherData
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette
import com.HelloMeet.weather.ui.components.PhosphorIcon
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.Text as MochiText
import ui.feice.mochi.theme.MochiTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Same inset and type scale as the surrounding settings; no nested scrolling surface. */
@Composable
internal fun WeatherModuleSettingsContent(
    policy: ModuleSourcePolicy,
    available: Set<SourcePref>,
    weather: WeatherData?,
    onCompare: () -> Unit,
    onChoose: (WeatherModule) -> Unit,
    showHeader: Boolean = true,
) {
    val palette = LocalZhishengPalette.current
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val titleStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)
    val captionStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    @Composable fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 18.dp),
        thickness = .5.dp, color = palette.cardBorder.copy(alpha = .32f))
    Column(Modifier.fillMaxWidth()) {
        if (showHeader) Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("按模块选择来源", color = palette.text, style = titleStyle, fontWeight = FontWeight.Medium)
            Text("点选各模块使用的天气来源", color = palette.textSecondary, style = captionStyle)
        }
        if (showHeader) Divider()
        WeatherModule.entries.forEachIndexed { index, module ->
            val selected = policy[module]
            val evidence = weather?.moduleEvidence?.get(module.key)
            val diagnostic = weather?.sourceDiagnostics?.get(module.key)
            val unavailable = selected != null && selected !in available
            val summary = when {
                unavailable -> "来源不可用 · 查看详情"
                evidence != null -> "当前：${moduleSourceName(evidence.source)}" + if (!diagnostic.isNullOrBlank()) " · 查看详情" else ""
                weather == null -> "等待天气数据"
                modulePresent(weather, module) -> "来源信息待更新"
                else -> "暂无${when (module) {
                    WeatherModule.CURRENT -> "实况"
                    WeatherModule.FORECAST -> "预报"
                    WeatherModule.MINUTELY -> "短时降水"
                    WeatherModule.ALERTS -> "预警"
                    WeatherModule.INDICES -> "生活指数"
                    WeatherModule.AIR -> "空气质量"
                }}数据"
            }
            @Composable fun SourceSummary() = Text(summary,
                color = if (unavailable || !diagnostic.isNullOrBlank()) palette.orange else palette.textSecondary,
                style = captionStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (index > 0) Divider()
            Box {
                BoxWithConstraints(Modifier.fillMaxWidth().clickable(role = Role.Button) { onChoose(module) }
                    .heightIn(min = 68.dp).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    val choiceWidth = maxWidth * .45f
                    val stacked = LocalDensity.current.fontScale > 1.2f || maxWidth < 240.dp
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                PhosphorIcon(weatherModuleIcon(module), null, Modifier.size(15.dp), palette.textSecondary)
                                Spacer(Modifier.width(6.dp))
                                Text(module.cn, Modifier.weight(1f), color = palette.text, style = titleStyle,
                                    fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp).rotate(-90f), tint = palette.textSecondary)
                            }
                            Text(selected?.cn ?: "跟随主源", color = palette.mint,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp))
                            SourceSummary()
                        }
                    } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhosphorIcon(weatherModuleIcon(module), null, Modifier.size(17.dp), palette.textSecondary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(module.cn, color = palette.text, style = titleStyle, fontWeight = FontWeight.Medium)
                            SourceSummary()
                        }
                        Row(Modifier.widthIn(max = choiceWidth), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(selected?.cn ?: "跟随主源", Modifier.weight(1f, fill = false), color = palette.mint,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp).rotate(-90f), tint = palette.textSecondary)
                        }
                    }
                    }
                }

            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { detailsExpanded = !detailsExpanded }
            .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("来源详情与说明", Modifier.weight(1f), color = palette.textSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp))
            Icon(Icons.Default.ArrowDropDown,
                if (detailsExpanded) "收起来源详情" else "展开来源详情",
                Modifier.size(18.dp).rotate(if (detailsExpanded) 180f else 0f), tint = palette.textSecondary)
        }
        if (detailsExpanded) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("跟随主源会沿用主天气源。单独指定的来源失败或缺少该模块时，保留主源数据；缺失数据不会补成预报。", color = palette.textSecondary, style = captionStyle)
                weather?.locationMatch?.dataResolution?.let { resolution ->
                    Text("天气覆盖：$resolution${weather.locationMatch.matchedRegionName?.let { " · $it" }.orEmpty()}。设备定位误差不代表天气分辨率。", color = palette.textSecondary, style = captionStyle)
                }
                weather?.sourceDiagnostics?.get("location")?.let { Text(it, color = palette.textSecondary, style = captionStyle) }
                WeatherModule.entries.forEach { module ->
                    val selected = policy[module]
                    val evidence = weather?.moduleEvidence?.get(module.key)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(module.cn, color = palette.text, style = captionStyle, fontWeight = FontWeight.Medium)
                        if (selected != null) {
                            val note = if (selected !in available) "所选来源当前不可用，请检查数据接入设置。"
                            else WeatherSourceCapabilities.map.getValue(selected).note
                            if (note.isNotBlank()) Text(note, color = palette.textSecondary, style = captionStyle)
                        }
                        if (evidence != null) {
                            Text("实际来源：${moduleSourceName(evidence.source)}", color = palette.textSecondary, style = captionStyle)
                            Text(evidence.observationTime?.let { "数据时间：${moduleEvidenceTime(it, weather.utcOffsetSeconds)}" }
                                ?: "上游未提供数据发布时间", color = palette.textSecondary, style = captionStyle)
                            evidence.fetchedAt?.let { Text("取得时间：${moduleEvidenceTime(it, weather.utcOffsetSeconds)}", color = palette.textSecondary, style = captionStyle) }
                        }
                        weather?.sourceDiagnostics?.get(module.key)?.let { Text(it, color = palette.orange, style = captionStyle) }
                    }
                }
            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onCompare)
            .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("比较天气来源", Modifier.weight(1f), color = palette.mint,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
            Text("→", color = palette.mint, fontSize = 18.sp)
        }
    }
}

private fun moduleSourceName(raw: String): String = raw.split('+').map { it.trim() }.distinct().joinToString("、") { code ->
    SourcePref.entries.firstOrNull { it.en.equals(code, ignoreCase = true) }?.cn ?: code
}

private fun moduleEvidenceTime(millis: Long, offsetSeconds: Int?): String {
    val offset = offsetSeconds?.takeIf { it in -18 * 3600..18 * 3600 }
    val zone = offset?.let(ZoneOffset::ofTotalSeconds) ?: ZoneId.systemDefault()
    return DateTimeFormatter.ofPattern("MM-dd HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone)) +
        if (offset == null) "（手机时间）" else "（地点时间）"
}

/** 关闭对话框即取消其 effect 与这一批来源请求。 */
@Composable
fun WeatherSourceComparison(city: City, onDismiss: () -> Unit) {
    var rows by remember(city.locationKey, city.latitude, city.longitude) { mutableStateOf<List<WeatherRepository.SourceComparison>?>(null) }
    var error by remember(city.locationKey) { mutableStateOf<String?>(null) }
    var retry by remember(city.locationKey) { mutableIntStateOf(0) }
    LaunchedEffect(city.locationKey, city.latitude, city.longitude, retry) {
        rows = null; error = null
        try { rows = WeatherRepository.fetchComparison(city) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = "暂时无法完成比较，请检查网络后重试" }
    }
    val loading = rows == null && error == null
    MochiDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "比较天气来源",
        message = "同一地点同时查询，各源观测时刻不同。数值是真实返回值，只作参照，不作准确率排名。",
        icon = {
            PhosphorIcon(R.drawable.ph_eye, null, Modifier.size(34.dp), MochiTheme.colorScheme.primary)
        },
        actions = persistentListOf(
            MochiDialogAction(
                label = if (error == null) "重新比较" else "重试",
                enabled = !loading,
                closeOnClick = false,
            ) { retry++ },
            MochiDialogAction("关闭", MochiDialogActionRole.Primary),
        ),
    ) {
        when {
            error != null -> SourceComparisonHint(R.drawable.ph_warning, error.orEmpty(), MochiTheme.colorScheme.error)
            loading -> Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = MochiTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                )
                MochiText(
                    "正在查询，关闭即可取消",
                    style = MochiTheme.textStyles.footnote,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
            rows.isNullOrEmpty() -> SourceComparisonHint(
                R.drawable.ph_info,
                "没有可比较的来源，请先在开发者模式里接入天气服务。",
                MochiTheme.colorScheme.onSurfaceVariant,
            )
            else -> WeatherSourceComparisonBody(city, rows.orEmpty())
        }
    }
}

/** 比较结果：每个来源一张卡，三个指标各自带图标与相对第一行来源的差值。 */
@Composable
private fun WeatherSourceComparisonBody(city: City, items: List<WeatherRepository.SourceComparison>) {
    val scheme = MochiTheme.colorScheme
    val caption = MochiTheme.textStyles.footnote
    val requestedAt = items.firstOrNull()?.requestedAt
    val commonHour = remember(items) {
        commonComparisonHour(items, requestedAt ?: System.currentTimeMillis())
    }
    // 第一行有实况的来源作为差值基准，避免「和谁比」说不清。
    val baseline = remember(items) {
        items.firstOrNull { row ->
            row.data?.current?.let { it.temperature != null || it.humidity != null || it.windSpeed != null } == true
        }?.data?.current
    }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MochiText(
                "请求时刻 ${comparisonTime(requestedAt, city)}",
                style = caption, color = scheme.onSurfaceVariant,
            )
            MochiText(
                commonHour?.let { "共同预报时刻 ${comparisonTime(it, city)}" }
                    ?: "暂无两个来源共同返回的未来小时，缺失值不补画。",
                style = caption, color = scheme.onSurfaceVariant,
            )
        }
        items.forEach { row ->
            val data = row.data
            Column(
                Modifier.fillMaxWidth()
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhosphorIcon(
                        weatherSourceIcon(row.source), null, Modifier.size(18.dp),
                        scheme.onSurfaceVariant,
                    )
                    MochiText(
                        row.source.cn,
                        style = MochiTheme.textStyles.subhead.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    MochiText(
                        data?.let { "观测 ${comparisonTime(it.updateTime, city)}" } ?: "未返回",
                        style = caption, color = scheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                if (data == null) {
                    MochiText(
                        row.diagnostic ?: "未返回数据",
                        style = caption, color = scheme.onSurfaceVariant,
                    )
                } else if (data.current == null) {
                    MochiText("未返回当前实况", style = caption, color = scheme.onSurfaceVariant)
                } else {
                    val current = data.current
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SourceComparisonMetric(
                            R.drawable.ph_thermometer, "温度",
                            comparisonValue(current.temperature, "℃", "—"),
                            sourceComparisonDiff(current.temperature, baseline?.temperature, 1, "℃", current === baseline),
                            Modifier.weight(1f),
                        )
                        SourceComparisonMetric(
                            R.drawable.ph_drop, "湿度",
                            comparisonValue(current.humidity, "%", "—"),
                            sourceComparisonDiff(current.humidity, baseline?.humidity, 0, "%", current === baseline),
                            Modifier.weight(1f),
                        )
                        SourceComparisonMetric(
                            R.drawable.ph_wind, "风速",
                            comparisonValue(current.windSpeed, "km/h", "—"),
                            sourceComparisonDiff(current.windSpeed, baseline?.windSpeed, 1, "", current === baseline),
                            Modifier.weight(1f),
                        )
                    }
                    val facts = buildList {
                        add(current.weatherText ?: current.condition?.label ?: "天气现象缺失")
                        data.aqi?.let { aqi ->
                            add("AQI ${Fmt.aqiValueLabel(aqi.value, aqi.estimated)}" +
                                if (aqi.estimated) " · ${Fmt.AQI_ESTIMATION_LABEL}" else "")
                        }
                        add("逐时 ${data.hourly.size} · 逐日 ${data.daily.size} · 预警 ${data.alerts.size}")
                    }
                    MochiText(
                        facts.joinToString(" · "),
                        style = caption, color = scheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        MochiText(
            "箭头表示与第一行有实况的来源的差值；AQI 估算值与不同标准不能直接横比。",
            style = caption, color = scheme.onSurfaceVariant,
        )
    }
}

/** 单个指标小卡：图标 + 名称、主值、与基准的差值。 */
@Composable
private fun SourceComparisonMetric(
    icon: Int, label: String, value: String, diff: String?, modifier: Modifier = Modifier,
) {
    val scheme = MochiTheme.colorScheme
    Column(
        modifier.background(scheme.surfaceContainerHigh.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            PhosphorIcon(icon, null, Modifier.size(12.dp), scheme.onSurfaceVariant)
            MochiText(label, style = MochiTheme.textStyles.footnote, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        MochiText(
            value,
            style = MochiTheme.textStyles.subhead.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        MochiText(
            diff ?: " ",
            style = MochiTheme.textStyles.footnote,
            color = scheme.secondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SourceComparisonHint(icon: Int, text: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhosphorIcon(icon, null, Modifier.size(18.dp), tint)
        MochiText(text, style = MochiTheme.textStyles.footnote, color = MochiTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 与基准来源的差值标签：基准行显示「基准」，其余显示 ↑/↓ + 差值（四舍五入到 [decimals] 位）。
 */
private fun sourceComparisonDiff(
    value: Double?, base: Double?, decimals: Int, suffix: String, isBaseline: Boolean,
): String? {
    if (isBaseline) return "基准"
    if (value == null || base == null || !value.isFinite() || !base.isFinite()) return null
    val delta = value - base
    val threshold = if (decimals == 0) 0.5 else 0.05
    if (kotlin.math.abs(delta) < threshold) return "持平"
    val arrow = if (delta > 0) "↑" else "↓"
    val amount = String.format(java.util.Locale.ROOT, "%.${decimals}f", kotlin.math.abs(delta))
    return arrow + amount + suffix
}

private fun comparisonValue(value: Double?, unit: String, missing: String = "缺失"): String =
    value?.takeIf(Double::isFinite)?.let { "${String.format(java.util.Locale.ROOT, "%.1f", it)} $unit" } ?: missing

internal fun commonComparisonHour(rows: List<WeatherRepository.SourceComparison>, nowMillis: Long): Long? =
    rows.flatMap { row -> row.data?.hourly.orEmpty().filter { it.timeMillis >= nowMillis && it.temperature?.isFinite() == true }.map { it.timeMillis }.distinct() }
        .groupingBy { it }.eachCount().filterValues { it >= 2 }.keys.minOrNull()

private fun comparisonTime(millis: Long?, city: City): String {
    // For global coordinates the actual provider offset is passed by the row below where possible;
    // UTC labels keep all providers on one unambiguous clock and avoid phone-timezone substitution.
    return millis?.let { DateTimeFormatter.ofPattern("MM-dd HH:mm 'UTC'").format(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC)) } ?: "上游未提供"
}
