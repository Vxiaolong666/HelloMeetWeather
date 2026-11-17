package com.HelloMeet.weather.ui.home

/* Hallmark · macrostructure: instrument atlas · theme: existing DESIGN.md
 * Wide solar chart / asymmetric night + radar / tidal strip / compact archive.
 * Native Compose tokens, no animation or fabricated measurements. */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HelloMeet.weather.model.*
import com.HelloMeet.weather.R
import com.HelloMeet.weather.ui.DisplayPrefs
import com.HelloMeet.weather.data.AtlasContent
import com.HelloMeet.weather.data.ReleaseFeatures
import com.HelloMeet.weather.ui.MochiPageFrame
import com.HelloMeet.weather.ui.Text
import com.HelloMeet.weather.ui.rememberRainviewerDisclosureAccepted
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.theme.*
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import java.util.Locale

// public：HomeScreen（public）的 onScenePage 参数把它交给应用层路由，不能再是 internal。
enum class AtlasPage(val title: String, val signal: String) {
    GLOW("霞光时刻", "SOLAR WINDOW"), STARS("星空拍摄", "NIGHT WINDOW"),
    COAST("海岸气象", "COASTAL OUTLOOK"), DARK("暗空环境", "DARK SKY"),
}

internal fun vistaScenePages(scene: SceneWeatherData?, prefs: DisplayPrefs): Set<AtlasPage> = buildSet {
    if (prefs.showSkyPhotography) {
        if (scene?.sky?.glow != null) add(AtlasPage.GLOW)
        if (ReleaseFeatures.starPhotography && scene?.sky?.stars != null) add(AtlasPage.STARS)
    }
    if (ReleaseFeatures.coastalWeather && prefs.showCoastWeather && scene?.coast?.let { coast ->
        coast.points.any { it.seaLevelM?.isFinite() == true } ||
            coastalDisplayFacts(coast, "c").isNotEmpty() ||
            listOf(coast.waveDirectionDeg, coast.currentDirectionDeg).any { it?.isFinite() == true }
    } == true) add(AtlasPage.COAST)
}

@Composable
internal fun AtlasSectionHeading(index: Int) {
    if (isPhosphorVista) {
        Text("气象视界", color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = LocalZhishengChrome.current.pagePadding)
                .padding(top = 28.dp, bottom = 12.dp))
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (isPhosphorVista) "气象视界" else "%02d// 气象视界".format(index), color = if (isPhosphorVista) ZhishengText else ZhishengOrange, style = MaterialTheme.typography.titleMedium)
        if (!isPhosphorVista) Text("WEATHER ATLAS", color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp)
    }
}

@Composable
internal fun WeatherAtlas(
    weather: WeatherData, scene: SceneWeatherData?, city: City?, unit: String,
    prefs: DisplayPrefs, modifier: Modifier = Modifier,
    onHistoryClick: () -> Unit, onRadarClick: () -> Unit,
    onScenePage: (AtlasPage) -> Unit = {},
    radarContent: (@Composable () -> Unit)? = null,
) {
    val vista = isPhosphorVista
    val radarAccepted by rememberRainviewerDisclosureAccepted()
    val radarPreview = if (ReleaseFeatures.radar && prefs.showSpacetime && radarAccepted && radarContent == null) rememberAtlasRadarPreview(city, weather.fetchedAt) else null
    // 可用性判定两主题统一：没有真实数据的专题不出示入口，
    // 经典侧不再用“等待数据”的占位瓦片兜底。
    val availablePages = vistaScenePages(scene, prefs)
    val showGlow = prefs.showSkyPhotography && AtlasPage.GLOW in availablePages
    val showStars = ReleaseFeatures.starPhotography && prefs.showSkyPhotography && AtlasPage.STARS in availablePages
    val showRadar = ReleaseFeatures.radar && prefs.showSpacetime
    val showCoast = ReleaseFeatures.coastalWeather && prefs.showCoastWeather && AtlasPage.COAST in availablePages
    // 「气象视界」内部分两组：天空与星空（霞光 + 星空 / 雷达）、海岸。
    // 组内顺序固定，组间顺序由「模块布局 → 气象视界内容」的拖拽结果决定。
    val showSkyGroup = showGlow || showStars || showRadar
    val atlasGroups = prefs.atlasContentOrder.filter { item ->
        when (item) {
            AtlasContent.SKY -> showSkyGroup
            AtlasContent.COAST -> showCoast
        }
    }
    val skyGroup: @Composable () -> Unit = {
        if (showGlow) {
            AtlasTile("霞光时刻", "SOLAR", onClick = { onScenePage(AtlasPage.GLOW) }, navSourceKey = "glow") {
                val glow = scene?.sky?.glow
                val zone = scene?.sky?.zoneId ?: "UTC"
                // 霞光示意与澄空一致：结论（今晚/明晚 + 等级）→ 参考时段 → 一句依据。
                // 经典侧不再画太阳高度图，换用同一套文字示意。
                VistaGlowSummary(glow, zone)
                AtlasStale(scene?.sky?.stale == true)
            }
            if (vista && (showStars || showRadar)) AtlasGroupDivider()
        }
        if (showStars || showRadar) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pair = showStars && showRadar
            val stack = maxWidth < (if (vista) 560.dp else 320.dp) || LocalDensity.current.fontScale > 1.15f
            val night: @Composable (Modifier) -> Unit = { m ->
                AtlasTile("星空拍摄", "NIGHT", m, { onScenePage(AtlasPage.STARS) }, navSourceKey = "stars") {
                    val stars = scene?.sky?.stars
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stars?.grade ?: "条件暂缺", color = ZhishengCyan, style = MaterialTheme.typography.titleLarge)
                            AtlasCaption("今晚参考时段")
                        }
                        AtlasMoon(stars?.moonIllumination, Modifier.size(52.dp))
                    }
                    stars?.let {
                        Text("${atlasClock(it.startMillis, scene!!.sky!!.zoneId)}–${atlasClock(it.endMillis, scene.sky.zoneId)}",
                            color = ZhishengText, style = MaterialTheme.typography.titleMedium)
                        AtlasNightBar(it, scene.sky.zoneId)
                    } ?: AtlasCaption(scene?.skyError ?: scene?.sky?.unavailableReason ?: "正在等待天空数据")
                    AtlasStale(scene?.sky?.stale == true)
                }
            }
            val radar: @Composable (Modifier) -> Unit = { m ->
                if (vista) HomeRadarCard(city, cityZone(weather.utcOffsetSeconds).id, radarPreview,
                    radarAccepted, m, onRadarClick, navSourceKey = "radar", content = radarContent)
                else AtlasTile("雷达回波", "RADAR", m, onRadarClick, navSourceKey = "radar") {
                    if (radarContent != null) radarContent()
                    else if (!radarAccepted) {
                        AtlasCaption("查看降雨变化与数据来源说明")
                        AtlasCaption("覆盖因地区而异；无回波不代表无雨")
                    } else AtlasRadarPreview(city, weather.fetchedAt, cityZone(weather.utcOffsetSeconds).id, radarPreview)
                }
            }
            if (pair && !stack) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    night(Modifier.weight(.56f).fillMaxHeight())
                    radar(Modifier.weight(.44f).fillMaxHeight())
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(if (vista) 0.dp else 12.dp)) {
                if (showStars) night(Modifier.fillMaxWidth())
                if (vista && showStars && showRadar) AtlasGroupDivider()
                if (showRadar) radar(Modifier.fillMaxWidth())
            }
        }
    }
    val coastGroup: @Composable () -> Unit = {
        AtlasTile("海岸气象", "COAST", onClick = { onScenePage(AtlasPage.COAST) }, navSourceKey = "coast") {
            val coast = scene?.coast
            if (coast != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (coast.points.any { it.seaLevelM?.isFinite() == true }) coast.trend.label else "海况预报",
                        Modifier.weight(1f), color = ZhishengCyan, style = MaterialTheme.typography.titleMedium)
                    coast.waveHeightM?.takeIf { it.isFinite() && it >= 0 }?.let { AtlasCaption("浪高 ${atlasNumber(it, "m")}") }
                }
                if (coast.points.any { it.seaLevelM?.isFinite() == true }) AtlasTideChart(coast, 68.dp)
                else coastalDisplayFacts(coast, unit).take(2).forEach { (label, value) -> AtlasCaption("$label $value") }
                AtlasCaption("模型趋势 · 非测站潮汐")
                AtlasStale(coast.stale)
            } else {
                // A missing coast is a compact state, never a made-up wave or a giant empty plot.
                AtlasCaption(scene?.coastError ?: "正在检查附近海域数据")
                AtlasCaption("海浪 / 潮位 / 海温")
            }
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = if (vista) LocalZhishengChrome.current.pagePadding else 20.dp)
        .then(if (vista) Modifier.zhishengPanel() else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (vista) 0.dp else 16.dp)) {
        atlasGroups.forEachIndexed { index, item ->
            if (index > 0 && vista) AtlasGroupDivider()
            when (item) {
                AtlasContent.SKY -> skyGroup()
                AtlasContent.COAST -> coastGroup()
            }
        }
        if (vista && atlasGroups.isNotEmpty() && prefs.showSpacetime) AtlasGroupDivider()
        if (prefs.showSpacetime) AtlasEntry("天气回看", "ARCHIVE", onHistoryClick, navSourceKey = "history") {
            val yesterday = weather.yesterday
            AtlasCaption(if (yesterday?.low != null && yesterday.high != null)
                "昨日 ${atlasTemperature(yesterday.low, unit)} — ${atlasTemperature(yesterday.high, unit)}"
                else "打开历史曲线与逐日记录")
        }
        // “暗空环境”暂不提供入口：波特尔等级数据尚未接入，
        // 两主题一致地不展示没有数据支撑的占位卡（组件与枚举保留给后续接入）。
    }
}

@Composable
internal fun AtlasTile(title: String, signal: String, modifier: Modifier = Modifier,
    onClick: () -> Unit, navSourceKey: String? = null,
    content: @Composable ColumnScope.() -> Unit) {
    // 「打开应用式」来源登记：页面从这张瓦片长成整页，返回缩回瓦片。
    Column(modifier.fillMaxWidth()
        .then(if (navSourceKey != null) Modifier.mochiNavSource(
            navSourceKey + LocalNavSourceSuffix.current,
            if (isPhosphorVista) MochiListDefaults.SectionCorner else 0.dp,
            MochiDefaults.groupedContainer(),
        ) else Modifier)
        .then(if (isPhosphorVista) Modifier
        else Modifier.background(ZhishengCard).border(1.dp, ZhishengCardBorder))
        .clickable(role = Role.Button, onClickLabel = "查看${title}详情", onClick = onClick)
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isPhosphorVista) 12.dp else 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!isPhosphorVista) Text(signal, color = ZhishengTextSecondary, fontSize = 10.sp, letterSpacing = 1.4.sp)
                Text(title, color = if (isPhosphorVista) ZhishengTextSecondary else ZhishengText,
                    style = if (isPhosphorVista) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium)
            }
            if (isPhosphorVista) {
                PhosphorIcon(atlasIcon(title), null, Modifier.size(18.dp), ZhishengTextSecondary)
                Spacer(Modifier.width(12.dp))
            }
            PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(16.dp), ZhishengTextSecondary)
        }
        content()
    }
}

@Composable
private fun AtlasEntry(title: String, signal: String, onClick: () -> Unit, navSourceKey: String? = null, content: @Composable () -> Unit) {
    // 同 AtlasTile：登记行本身为转场来源，页面从这一行展开 / 缩回。
    val sourceModifier = if (navSourceKey != null) Modifier.mochiNavSource(
        navSourceKey + LocalNavSourceSuffix.current,
        if (isPhosphorVista) MochiListDefaults.SectionCorner else 0.dp,
        MochiDefaults.groupedContainer(),
    ) else Modifier
    if (isPhosphorVista) {
        Row(sourceModifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "查看$title", onClick = onClick)
            .padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = ZhishengText, style = MaterialTheme.typography.bodyMedium)
                content()
            }
            PhosphorIcon(atlasIcon(title), null, Modifier.size(18.dp), ZhishengTextSecondary)
            PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(16.dp), ZhishengTextSecondary)
        }
        return
    }
    Row(sourceModifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = ZhishengText, style = MaterialTheme.typography.titleMedium)
            content()
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(signal, color = ZhishengTextSecondary, fontSize = 10.sp)
            PhosphorIcon(R.drawable.ph_arrow_right, null, Modifier.size(20.dp), ZhishengTextSecondary)
        }
    }
    HorizontalDivider(color = ZhishengCardBorder)
}

@Composable
private fun AtlasGroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = ZhishengCardBorder.copy(alpha = 0.24f))
}

private fun atlasIcon(title: String): Int = when (title) {
    "霞光时刻" -> R.drawable.ph_sun
    "星空拍摄" -> R.drawable.ph_camera
    "雷达回波" -> R.drawable.ph_broadcast
    "海岸气象" -> R.drawable.ph_waves
    "天气回看" -> R.drawable.ph_arrow_clockwise
    "暗空环境" -> R.drawable.ph_moon_stars
    else -> R.drawable.ph_binoculars
}

@Composable
private fun atlasAccent(title: String): Color = when (title) {
    "霞光时刻", "天气回看" -> ZhishengOrange
    "星空拍摄", "雷达回波", "海岸气象", "暗空环境" -> ZhishengCyan
    else -> ZhishengMint
}

@Composable
internal fun AtlasCaption(value: String, color: Color = ZhishengTextSecondary) {
    Text(value, color = color, style = MaterialTheme.typography.bodySmall, fontFamily = ZhishengReading)
}

@Composable
internal fun AtlasStale(stale: Boolean) {
    if (stale) AtlasCaption("缓存数据 · 可能已过期", ZhishengOrange)
}

internal fun atlasNumber(value: Double?, unit: String = "") = value?.takeIf(Double::isFinite)
    ?.let { String.format(Locale.US, "%.1f%s", if (kotlin.math.abs(it) < .05) 0.0 else it, unit) } ?: "—"
internal fun atlasTemperature(value: Double?, unit: String) = atlasNumber(value?.let { if (unit == "f") it * 1.8 + 32 else it }, if (unit == "f") "°F" else "°C")

@Composable
internal fun SceneAtlasPage(page: AtlasPage, scene: SceneWeatherData?, city: City?, unit: String, onBack: () -> Unit) {
    // 与设置二级页完全同一个外壳：库标题脚手架 + 液态玻璃材质 + 越界手感。
    // 系统栏底色取库的分组背景，和顶栏是同一块颜色；状态栏内边距由库顶栏自己处理，不能再加 safeDrawing。
    // 原来是页内 Dialog 自开窗口自播进出动画，绕过了统一转场契约；现在作为 AppScreen 路由页，
    // 进出由 MochiNavHost 按「应用转场」设置执行，来源登记在首页对应的那张瓦片上。
    MochiPageFrame(
        title = page.title,
        subtitle = page.signal,
        onBack = onBack,
    ) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AtlasCaption(city?.displayName ?: "当前地点未选择")
                if (city != null) AtlasCaption("经纬度 ${com.HelloMeet.weather.ui.Fmt.coordinates(city.latitude, city.longitude, precise = city.isPreciseLocation)}")
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    AtlasPage.GLOW -> AtlasGlowDetail(scene, city)
                    AtlasPage.STARS -> AtlasStarsDetail(scene, city)
                    AtlasPage.COAST -> AtlasCoastDetail(scene, unit)
                    AtlasPage.DARK -> AtlasDarkDetail()
                }
            }
        }
    }
}
