package com.HelloMeet.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.WeatherData
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.home.LifeIndexUi
import com.HelloMeet.weather.ui.home.lifeIndexIcon
import com.HelloMeet.weather.ui.home.lifeIndexItems
import com.HelloMeet.weather.ui.theme.ZhishengText
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.effect.mask.mochiScrollEdgeBlur
import ui.feice.mochi.navigation.MochiScrollEdgeDefaults
import ui.feice.mochi.navigation.MochiTitleBarDefaults
import ui.feice.mochi.theme.MochiTheme
import kotlin.math.abs

/**
 * 「天气详情」可视化页：首页遥测标题行右侧的入口进入。
 *
 * 八类内容沿用 yunsong 的循环顺序（空气质量 → 紫外线 → 体感温度 → 风力 → 气压 →
 * 能见度 → 湿度 → 日出日落），单页横向翻页承载，左右滑动与顶栏上一项 / 下一项共用
 * 同一个 [androidx.compose.foundation.pager.PagerState]，顶栏标题随当前页切换。
 * 页面外壳仍走 [MochiColumnPageFrame]（库顶栏 + 统一返回），不自行处理返回或转场。
 */
@Composable
internal fun WeatherDetailScreen(
    weather: WeatherData?,
    city: City?,
    prefs: DisplayPrefs,
    tempUnit: String,
    onBack: () -> Unit,
    nearby: List<NearbyCityAqi> = emptyList(),
    initialPage: DetailPage = DetailPage.UV,
) {
    val pages = DetailPage.entries
    val pageCount = pages.size
    val startPage = pageCount * 500 + pages.indexOf(initialPage).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage) { pageCount * 1000 }
    val scope = rememberCoroutineScope()
    val current = pages[pagerState.currentPage.mod(pageCount)]
    val cityName = city?.displayName ?: "当前位置"
    val subtitle = when (current) {
        DetailPage.AIR_QUALITY -> weather?.updateTime
            ?.let { "$cityName · ${Fmt.clock(it, weather.utcOffsetSeconds)} 发布" }
            ?: "$cityName · 更新时间未知"
        DetailPage.SUN -> "$cityName · 全天太阳高度"
        else -> "$cityName · 未来24小时"
    }
    val barTint = MochiTheme.colorScheme.onSurface
    val haptic = LocalHapticFeedback.current
    // 顶栏浮在正文上（glassBar）：正文要自己留出状态栏 + 栏高，否则第一张卡片压到栏下面。
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + MochiTitleBarDefaults.Height
    LaunchedEffect(pagerState, haptic) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    MochiColumnPageFrame(
        title = current.title,
        subtitle = subtitle,
        onBack = onBack,
        glassBar = true,
        actions = {
            MochiBarIconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                contentKey = "weather-detail-prev",
            ) {
                PhosphorIcon(R.drawable.ph_arrow_left, "上一项", Modifier.size(18.dp), barTint)
            }
            MochiBarIconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                contentKey = "weather-detail-next",
            ) {
                PhosphorIcon(R.drawable.ph_arrow_right, "下一项", Modifier.size(18.dp), barTint)
            }
        },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            when (val page = pages[index.mod(pageCount)]) {
                DetailPage.AIR_QUALITY -> AqiDetailPage(
                    weather = weather,
                    city = city,
                    modifier = Modifier.fillMaxSize(),
                    topInset = topInset,
                )
                DetailPage.SUN -> SunDetailPage(
                    weather = weather,
                    city = city,
                    modifier = Modifier.fillMaxSize(),
                    topInset = topInset,
                )
                else -> MetricDetailPage(
                    kind = page.metric ?: MetricKind.UV,
                    weather = weather,
                    prefs = prefs,
                    tempUnit = tempUnit,
                    modifier = Modifier.fillMaxSize(),
                    topInset = topInset,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 六类逐小时指标
// ═══════════════════════════════════════════════════════════

@Composable
private fun MetricDetailPage(
    kind: MetricKind,
    weather: WeatherData?,
    prefs: DisplayPrefs,
    tempUnit: String,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    val now = weatherPresentationTime()
    val hours = remember(weather, now) { detailHourly(weather, now) }
    val times = remember(hours) { hours.map { it.timeMillis } }
    val values = remember(hours, kind) { hours.map { kind.valueOf(it) } }
    val secondary = remember(hours, kind) { hours.map { kind.secondaryOf(it) } }
    var selected by remember(hours) { mutableIntStateOf(nearestIndex(times, now)) }
    val utcOffset = weather?.utcOffsetSeconds
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(selected, haptic) {
        snapshotFlow { selected }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    if (hours.isEmpty()) {
        DetailEmptyState(modifier)
        return
    }

    val safeIndex = selected.coerceIn(0, hours.lastIndex)
    val timeMillis = times[safeIndex]
    val value = values.getOrNull(safeIndex)
    val second = secondary.getOrNull(safeIndex)
    val timeLabel = detailTimeLabel(timeMillis, now, utcOffset)
    val advice = kind.advice(value)
    val comparison = if (kind == MetricKind.FEELS_LIKE) {
        MetricKind.FEELS_LIKE.feelsLikeComparison(value, second, tempUnit)
    } else {
        null
    }
    val windMeta = if (kind == MetricKind.WIND) {
        val direction = detailWindDirection(hours[safeIndex].windDirectionDeg)
        val directionText = if (direction == null) "风向待更新" else "风向：$direction"
        val gustText = second?.takeIf { it.isFinite() }
            ?.let { "阵风：${Fmt.wind(it, prefs.windUnit)}" }
            ?: "暂无阵风数据"
        "$directionText · $gustText"
    } else {
        null
    }
    val lifeAdvice = remember(weather, prefs.lifeIndexMetrics) {
        weather?.let { lifeIndexItems(it, prefs.lifeIndexMetrics).take(3) }.orEmpty()
    }
    val listState = rememberLazyListState()
    val edge = MochiScrollEdgeDefaults.progress(listState)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .mochiScrollEdgeBlur(top = topInset, alpha = edge),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MochiPageCard(header = "信息摘要", animateHeight = false) {
                Text(
                    buildMetricSummary(kind, values, prefs, tempUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
            }
        }
        item {
            MochiPageCard(animateHeight = false) {
                Text(
                    "${kind.title} ${kind.display(value, prefs, tempUnit)} · ${kind.level(value)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = ZhishengText,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$timeLabel · $advice",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
                comparison?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ZhishengTextTertiary)
                }
                windMeta?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                }
            }
        }
        item {
            MochiPageCard(animateHeight = false) {
                MetricDetailChart(
                    kind = kind,
                    times = times,
                    values = values,
                    secondary = secondary,
                    utcOffsetSeconds = utcOffset,
                    prefs = prefs,
                    tempUnit = tempUnit,
                    selected = safeIndex,
                    onSelect = { selected = it },
                )
            }
        }
        item {
            MochiPageCard(header = "关于${kind.title}指标", animateHeight = false) {
                Text(
                    kind.about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
            }
        }
        if (lifeAdvice.isNotEmpty()) {
            item {
                MochiPageCard(header = "生活建议", animateHeight = false) {
                    lifeAdvice.forEachIndexed { index, item ->
                        LifeAdviceRow(item)
                        if (index < lifeAdvice.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(start = 34.dp),
                                color = MochiTheme.colorScheme.outlineVariant.copy(alpha = .5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeAdviceRow(item: LifeIndexUi) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhosphorIcon(
            lifeIndexIcon(item.en),
            null,
            Modifier.size(22.dp),
            MochiTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodySmall,
                color = ZhishengTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.period?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            }
        }
        Text(
            item.value,
            style = MaterialTheme.typography.bodyMedium,
            color = ZhishengText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ═══════════════════════════════════════════════════════════
// 日出日落
// ═══════════════════════════════════════════════════════════

@Composable
private fun SunDetailPage(
    weather: WeatherData?,
    city: City?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    val now = weatherPresentationTime()
    val timeline = remember(weather, city, now) {
        buildSunTimeline(weather, city, weather?.utcOffsetSeconds, now)
    }
    if (timeline == null) {
        DetailEmptyState(modifier)
        return
    }
    var selected by remember(timeline) { mutableIntStateOf(nearestIndex(timeline.points.map { it.first }, now)) }
    val index = selected.coerceIn(0, timeline.points.lastIndex)
    val point = timeline.points[index]
    val glow = remember(weather, now) { weather?.todayDaily(now) }
    val glowForecast = remember(glow) { sunGlow(glow) }
    val morning = timeline.blueMorning
    val evening = timeline.blueEvening
    val morningWindow = timeline.goldenMorning
    val eveningWindow = timeline.goldenEvening
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(selected, haptic) {
        snapshotFlow { selected }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val listState = rememberLazyListState()
    val edge = MochiScrollEdgeDefaults.progress(listState)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .mochiScrollEdgeBlur(top = topInset, alpha = edge),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MochiPageCard(header = "信息摘要", animateHeight = false) {
                Text(
                    sunSummary(timeline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
            }
        }
        item {
            MochiPageCard(animateHeight = false) {
                Text(
                    sunSelectionText(timeline, point.first, point.second),
                    style = MaterialTheme.typography.titleMedium,
                    color = ZhishengText,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    sunAdvice(timeline, point.first, point.second),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhishengTextSecondary,
                )
            }
        }
        item {
            MochiPageCard(animateHeight = false) {
                SunAltitudeChart(
                    timeline = timeline,
                    selected = index,
                    onSelect = { selected = it },
                )
            }
        }
        item {
            MochiPageCard(animateHeight = false) {
                Row(Modifier.fillMaxWidth()) {
                    SunStatColumn(
                        "太阳正午",
                        timeline.solarNoon?.let { Fmt.clock(it, timeline.utcOffsetSeconds) } ?: "--:--",
                        Modifier.weight(1f),
                    )
                    SunStatColumn("今日昼长", timeline.daylightText, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    SunStatColumn(
                        "蓝调时刻",
                        sunPeriodText(morning, evening, timeline.utcOffsetSeconds),
                        Modifier.weight(1f),
                    )
                    SunStatColumn(
                        "金色时刻",
                        sunPeriodText(morningWindow, eveningWindow, timeline.utcOffsetSeconds),
                        Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            MochiPageCard(header = "霞光预报", animateHeight = false) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("早霞", style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${sunWindowText(morningWindow, timeline.utcOffsetSeconds)} · ${glowForecast.level} ${glowForecast.score}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhishengTextSecondary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "日出前后，${glowForecast.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhishengTextTertiary,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("晚霞", style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${sunWindowText(eveningWindow, timeline.utcOffsetSeconds)} · ${glowForecast.level} ${glowForecast.score}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhishengTextSecondary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "日落前后，${glowForecast.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhishengTextTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SunStatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = ZhishengText,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun sunWindowText(window: Pair<Long, Long>?, utcOffsetSeconds: Int?): String =
    if (window == null) {
        "当地无此时段"
    } else {
        "${Fmt.clock(window.first, utcOffsetSeconds)}–${Fmt.clock(window.second, utcOffsetSeconds)}"
    }

/** 晨昏时段像参考实现一样分成「早晨 / 傍晚」两行，避免只展示一半。 */
private fun sunPeriodText(
    morning: Pair<Long, Long>?,
    evening: Pair<Long, Long>?,
    utcOffsetSeconds: Int?,
): String = "早晨 ${sunWindowText(morning, utcOffsetSeconds)}\n傍晚 ${sunWindowText(evening, utcOffsetSeconds)}"

// ═══════════════════════════════════════════════════════════

@Composable
internal fun DetailEmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "天气数据仍在加载，请稍后返回重试。",
            Modifier.padding(horizontal = 32.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = ZhishengTextSecondary,
        )
    }
}

/** 初始选中离当前时刻最近的一条；图表未就绪时落在第一项。 */
internal fun nearestIndex(times: List<Long>, now: Long): Int {
    if (times.isEmpty()) return 0
    return times.indices.minByOrNull { abs(times[it] - now) } ?: 0
}
