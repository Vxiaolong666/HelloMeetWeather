package com.HelloMeet.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.CityRepository
import com.HelloMeet.weather.data.LocalPlaces
import com.HelloMeet.weather.data.PlaceFailure
import com.HelloMeet.weather.data.SecretStore
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.data.mergePlaceSearchResults
import com.HelloMeet.weather.model.CoordinateKind
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.PlaceRegion
import com.HelloMeet.weather.model.filterPlaceRegion
import com.HelloMeet.weather.model.placeRegion
import com.HelloMeet.weather.model.regionLabel
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.theme.LocalZhishengChrome
import com.HelloMeet.weather.ui.theme.ZhishengCyan
import com.HelloMeet.weather.ui.theme.ZhishengMint
import com.HelloMeet.weather.ui.theme.ZhishengOrange
import com.HelloMeet.weather.ui.theme.ZhishengText
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import com.HelloMeet.weather.ui.theme.isPhosphorVista
import com.HelloMeet.weather.ui.theme.zhishengCompactPanel
import com.HelloMeet.weather.ui.theme.zhishengPanel
import com.HelloMeet.weather.ui.theme.zhishengScreen
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiEmptyState
import ui.feice.mochi.basic.MochiSearchBar
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.theme.MochiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SearchUiState(
    val query: String = "", val preciseAddress: Boolean = false,
    val results: List<City> = emptyList(), val searching: Boolean = false,
    val onlineAttempted: Boolean = false, val failure: PlaceFailure? = null,
    val indexUnavailable: Boolean = false, val region: PlaceRegion = PlaceRegion.DOMESTIC,
    val province: String = "", val municipality: String = "",
    val provinces: List<String> = emptyList(), val municipalities: List<String> = emptyList(),
)

@Composable
fun SearchScreen(onCityPicked: (City) -> Unit, onBack: () -> Unit) {
    val amap by SecretStore.amapRuntimeFlow.collectAsState(initial = SecretStore.amapRuntime)
    val developerMode by SettingsRepository.developerMode.collectAsState(initial = false)
    LiveSearchScreen(onCityPicked, onBack,
        localSearch = { CityRepository.searchLocal(it) },
        onlineSearch = { term, precise -> CityRepository.searchOnline(term, precise) },
        regionalSearch = { term, precise, region -> CityRepository.searchOnline(term, precise, region) },
        areaSearch = { term, precise, region, areas -> CityRepository.searchOnline(term, precise, region, areas) },
        amapEnhanced = developerMode && amap.ready)
}

@Composable
internal fun LiveSearchScreen(onCityPicked: (City) -> Unit, onBack: () -> Unit,
    localSearch: suspend (String) -> List<City>,
    onlineSearch: suspend (String, Boolean) -> com.HelloMeet.weather.data.PlaceSearchOutcome,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    regionalSearch: (suspend (String, Boolean, PlaceRegion) -> com.HelloMeet.weather.data.PlaceSearchOutcome)? = null,
    amapEnhanced: Boolean = false,
    areaSearch: (suspend (String, Boolean, PlaceRegion, List<String>) -> com.HelloMeet.weather.data.PlaceSearchOutcome)? = null) {
    var region by rememberSaveable { mutableStateOf(PlaceRegion.DOMESTIC) }
    var query by rememberSaveable { mutableStateOf("") }
    var precise by rememberSaveable { mutableStateOf(false) }
    var submitted by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var generation by remember { mutableStateOf(0L) }
    var state by remember { mutableStateOf(SearchUiState()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var province by rememberSaveable { mutableStateOf("") }
    var municipality by rememberSaveable { mutableStateOf("") }
    var provinces by remember { mutableStateOf(emptyList<String>()) }
    var municipalities by remember { mutableStateOf(emptyList<String>()) }
    androidx.compose.runtime.LaunchedEffect(province) {
        provinces = LocalPlaces.areas(context)
        municipalities = if (province.isBlank()) emptyList() else LocalPlaces.areas(context, province)
    }
    val requestRegion = region
    val requestQuery = query
    val requestPrecise = precise
    val requestSubmitted = submitted
    val requestGeneration = generation
    val requestArea = listOf(province, municipality).filter(String::isNotBlank).distinct()
    androidx.compose.runtime.LaunchedEffect(requestQuery, requestPrecise, requestSubmitted, retry, requestGeneration, requestRegion) {
        if (generation != requestGeneration) return@LaunchedEffect
        fun publish(value: SearchUiState) { if (generation == requestGeneration) state = value }
        val term = requestQuery.trim()
        publish(SearchUiState(requestQuery, requestPrecise, region = requestRegion))
        if (term.isEmpty() && requestArea.isEmpty()) return@LaunchedEffect
        val scopedTerm = (requestArea + term).filter(String::isNotBlank).joinToString(" ")
        fun scoped(values: List<City>) = values.filter { com.HelloMeet.weather.data.cityMatchesSearchArea(it, requestArea) }
        delay(100)
        val local = withContext(ioDispatcher) { scoped(filterPlaceRegion(localSearch(scopedTerm), requestRegion)) }
        if (generation != requestGeneration) return@LaunchedEffect
        publish(state.copy(results = local, indexUnavailable = LocalPlaces.unavailable))
        if (requestSubmitted != term) return@LaunchedEffect
        publish(state.copy(searching = true, onlineAttempted = true))
        try {
            val onlineTerm = if (requestPrecise) scopedTerm else term.ifBlank { requestArea.lastOrNull().orEmpty() }
            val online = withContext(ioDispatcher) {
                areaSearch?.invoke(onlineTerm, requestPrecise, requestRegion, requestArea)
                    ?: regionalSearch?.invoke(onlineTerm, requestPrecise, requestRegion) ?: onlineSearch(onlineTerm, requestPrecise)
            }
            publish(state.copy(results = scoped(filterPlaceRegion(mergePlaceSearchResults(local, online.cities), requestRegion)), failure = online.failure))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publish(state.copy(failure = PlaceFailure.UNAVAILABLE))
        } finally {
            publish(state.copy(searching = false))
        }
    }
    // Controlled input must echo the edit in the same composition, independently
    // of debounced lookup effects. Delaying this value can reorder fast IME edits.
    SearchScreenContent(state.copy(query = query, preciseAddress = precise, region = region,
        province = province, municipality = municipality, provinces = provinces, municipalities = municipalities),
        onQueryChanged = { query = it.take(120); submitted = null; generation++; state = SearchUiState(query, precise, region = region) },
        onScopeChanged = { precise = it; submitted = null; generation++; state = SearchUiState(query, precise, region = region) },
        onSearch = { submitted = query.trim(); retry++; generation++ },
        onCityPicked = onCityPicked, onBack = onBack,
        onRegionChanged = { region = it; province = ""; municipality = ""; submitted = null; generation++; state = SearchUiState(query, precise, region = region) },
        onAreaChanged = { first, second -> province = first; municipality = second; submitted = null; generation++ },
        amapEnhanced = amapEnhanced)
}

/** The same production surface is rendered in the native layout checks with offline fixtures. */
@Composable
internal fun SearchScreenContent(state: SearchUiState, onQueryChanged: (String) -> Unit,
    onScopeChanged: (Boolean) -> Unit, onSearch: () -> Unit,
    onCityPicked: (City) -> Unit, onBack: () -> Unit,
    onRegionChanged: (PlaceRegion) -> Unit = {},
    onAreaChanged: (String, String) -> Unit = { _, _ -> },
    amapEnhanced: Boolean = false) {
    val chrome = LocalZhishengChrome.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    fun dismissKeyboard() { focus.clearFocus(force = true); keyboard?.hide() }
    // 与设置二级页完全同一个外壳：库标题脚手架 + 液态玻璃材质 + 越界手感。
    MochiPageFrame(
        title = "添加地点",
        subtitle = "搜索城市、区县或街道，点结果即可加入首页",
        onBack = { dismissKeyboard(); onBack() },
    ) {
        item {
            MochiSearchBar(
                query = state.query,
                onQueryChange = onQueryChanged,
                modifier = Modifier.padding(vertical = 4.dp),
                placeholder = if (state.preciseAddress) "城市、街道或地点" else "城市、区县或拼音",
                onSearch = { dismissKeyboard(); onSearch() },
                onCancel = { dismissKeyboard() },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.region.label} · ${state.results.size} 个结果", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                androidx.compose.material3.TextButton(onClick = { dismissKeyboard(); onSearch() }, modifier = Modifier.heightIn(min = 48.dp), enabled = state.query.isNotBlank() && !state.searching) {
                    Text(if (state.searching) "搜索中…" else "联网搜索", color = ZhishengMint)
                }
            }
        }
                item(key = "search-scope-controls") {
                    Column {
                        SearchPlaceTools(state, onAreaChanged, onCityPicked)
                        Row(Modifier.fillMaxWidth().selectableGroup().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchScopeButton(R.drawable.ph_map_pin, "国内", state.region == PlaceRegion.DOMESTIC, Modifier.weight(1f)) { onRegionChanged(PlaceRegion.DOMESTIC) }
                            SearchScopeButton(R.drawable.ph_globe, "海外", state.region == PlaceRegion.OVERSEAS, Modifier.weight(1f)) { onRegionChanged(PlaceRegion.OVERSEAS) }
                        }
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchScopeButton(R.drawable.ph_buildings, "城市与区县", !state.preciseAddress, Modifier.weight(1f)) { onScopeChanged(false) }
                            SearchScopeButton(R.drawable.ph_map_pin, "街道与地点", state.preciseAddress, Modifier.weight(1f)) { onScopeChanged(true) }
                        }
                        if (amapEnhanced && state.region == PlaceRegion.DOMESTIC) {
                            Text("已接入你的高德 Key，街道和地点优先用高德查找",
                                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                        }
                    }
                }
                if (state.query.isBlank()) item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 14.dp).mochiCardSurface().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                    .background(ZhishengMint.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                PhosphorIcon(
                                    if (state.preciseAddress) R.drawable.ph_map_pin else R.drawable.ph_magnifying_glass,
                                    null,
                                    Modifier.size(18.dp),
                                    ZhishengMint,
                                )
                            }
                            Text(
                                if (state.preciseAddress) "搜索街道或地点" else "查找天气地点",
                                style = MaterialTheme.typography.titleMedium,
                                color = ZhishengText,
                            )
                        }
                        listOf(
                            R.drawable.ph_magnifying_glass to "输入城市名、区县或拼音，本地结果即时出现",
                            R.drawable.ph_globe to "没有合适结果时，点「联网搜索」补充",
                            R.drawable.ph_plus to "点任意结果即可添加到首页",
                        ).forEach { (icon, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PhosphorIcon(icon, null, Modifier.size(15.dp), ZhishengTextTertiary)
                                Text(label, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                            }
                        }
                        Text(
                            if (state.region == PlaceRegion.DOMESTIC) "国内范围含港澳台" else "海外结果会标注国家或地区",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengTextTertiary,
                        )
                    }
                }
                if (state.indexUnavailable) item { SearchMessage("本地地名数据暂不可用，可选收藏或联网搜索", ZhishengTextSecondary) }
                if (state.failure != null) item {
                    SearchMessage(if (state.failure == PlaceFailure.CONFIG_CHANGED) "接入设置已变化，请重新搜索"
                        else "在线服务暂不可用，本地结果仍可选择", ZhishengTextSecondary)
                    androidx.compose.material3.TextButton(onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试", color = ZhishengMint) }
                }
                if (state.searching) item { SearchMessage("正在补充在线结果，本地结果可先选择", ZhishengTextSecondary) }
                if (state.query.isNotBlank() && state.results.isEmpty() && !state.searching && state.failure == null) {
                    // 搜索无结果照组件库 demo 用 MochiEmptyState 做遮罩：模糊淡入、居中图标 + 标题 + 说明 + 一个动作。
                    item(key = "search-empty") {
                        MochiEmptyState(
                            title = "没有找到「${state.query}」",
                            message = if (!state.onlineAttempted) "本地地名库没有匹配，点「联网搜索」再查一次。"
                                else "换个城市或区县名，也可以切换搜索范围。",
                            icon = {
                                PhosphorIcon(
                                    R.drawable.ph_magnifying_glass, null, Modifier.size(44.dp),
                                    MochiTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            action = {
                                MochiButton(
                                    onClick = if (state.region == PlaceRegion.DOMESTIC) {
                                        { onRegionChanged(PlaceRegion.OVERSEAS) }
                                    } else onSearch,
                                    style = MochiButtonStyle.Tonal,
                                ) {
                                    Text(if (state.region == PlaceRegion.DOMESTIC) "切换海外搜索" else "重新搜索")
                                }
                            },
                        )
                    }
                }
                items(state.results.size, key = { state.results[it].locationKey }) { index ->
                    val city = state.results[index]
                    val category = if (city.placeRegion() == null) "地区待确认" else if (state.region == PlaceRegion.OVERSEAS) "海外地点" else if (city.coordinateKind == CoordinateKind.PLACE || city.street != null) "街道与地点" else "城市与区县"
                    val previous = state.results.getOrNull(index - 1)
                    val previousCategory = previous?.let { if (it.placeRegion() == null) "地区待确认" else if (state.region == PlaceRegion.OVERSEAS) "海外地点" else if (it.coordinateKind == CoordinateKind.PLACE || it.street != null) "街道与地点" else "城市与区县" }
                    if (category != previousCategory) Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PhosphorIcon(
                            when (category) {
                                "海外地点" -> R.drawable.ph_globe
                                "街道与地点" -> R.drawable.ph_map_pin
                                "地区待确认" -> R.drawable.ph_magnifying_glass
                                else -> R.drawable.ph_buildings
                            },
                            null,
                            Modifier.size(13.dp),
                            ZhishengTextTertiary,
                        )
                        Text(category, style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                    }
                    SearchResultRow(city, index) { dismissKeyboard(); onCityPicked(it) }
                }
                item {
                    Text("地名数据：GeoNames · CC BY 4.0", Modifier.fillMaxWidth().clickable {
                        runCatching { uri.openUri("https://www.geonames.org/") }
                    }.padding(vertical = 12.dp), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                }
    }
}

@Composable
private fun SearchResultRow(city: City, index: Int, onCityPicked: (City) -> Unit) {
    val address = city.coordinateKind == com.HelloMeet.weather.model.CoordinateKind.PLACE ||
        city.coordinateKind == com.HelloMeet.weather.model.CoordinateKind.DEVICE ||
        (city.coordinateKind == com.HelloMeet.weather.model.CoordinateKind.UNKNOWN && city.isPreciseLocation)
    val primary = if (address) city.street ?: city.name else city.name
    val secondary = if (address) {
        listOf(city.name, city.affiliation).filter(String::isNotBlank).distinct().joinToString(" · ")
    } else city.affiliation
    val locationContext = listOf(city.regionLabel(), secondary).filter(String::isNotBlank).distinct().joinToString(" · ")
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (isPhosphorVista) Modifier.zhishengPanel() else Modifier.zhishengCompactPanel())
                .clickable { onCityPicked(city) }
                .heightIn(min = 76.dp)
                .padding(horizontal = if (isPhosphorVista) 12.dp else 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPhosphorVista) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                        .background(ZhishengMint.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PhosphorIcon(
                        if (city.coordinateKind == com.HelloMeet.weather.model.CoordinateKind.DEVICE) R.drawable.ph_crosshair else R.drawable.ph_map_pin,
                        null,
                        Modifier.size(19.dp),
                        ZhishengMint,
                    )
                }
            } else {
                Text("%02d".format(index + 1), style = MaterialTheme.typography.labelSmall, color = ZhishengOrange)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(primary, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                if (locationContext.isNotBlank()) Text(locationContext, style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                Text(
                    "${city.coordinateLabel} · %.4f, %.4f".format(city.latitude, city.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                )
            }
            if (isPhosphorVista) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                        .background(ZhishengMint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PhosphorIcon(R.drawable.ph_plus, "添加$primary", Modifier.size(17.dp), ZhishengMint)
                }
            } else {
                Text("[+]", style = MaterialTheme.typography.labelMedium, color = ZhishengMint)
            }
        }
    }
}

@Composable
private fun SearchMessage(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun SearchScopeButton(
    icon: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .clip(shape)
            .background(if (selected) ZhishengMint.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) ZhishengMint.copy(alpha = 0.45f) else ZhishengTextTertiary.copy(alpha = 0.22f),
                shape,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PhosphorIcon(icon, null, Modifier.size(17.dp), if (selected) ZhishengMint else ZhishengTextTertiary)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ZhishengMint else ZhishengTextSecondary,
            maxLines = 1,
        )
    }
}
