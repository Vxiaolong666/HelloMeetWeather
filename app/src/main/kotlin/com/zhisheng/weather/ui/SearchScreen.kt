package com.zhisheng.weather.ui

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
import com.zhisheng.weather.R
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.LocalPlaces
import com.zhisheng.weather.data.PlaceFailure
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.mergePlaceSearchResults
import com.zhisheng.weather.model.CoordinateKind
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.PlaceRegion
import com.zhisheng.weather.model.filterPlaceRegion
import com.zhisheng.weather.model.placeRegion
import com.zhisheng.weather.model.regionLabel
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.LocalZhishengChrome
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.theme.zhishengCompactPanel
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.theme.zhishengScreen
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
    onlineSearch: suspend (String, Boolean) -> com.zhisheng.weather.data.PlaceSearchOutcome,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    regionalSearch: (suspend (String, Boolean, PlaceRegion) -> com.zhisheng.weather.data.PlaceSearchOutcome)? = null,
    amapEnhanced: Boolean = false,
    areaSearch: (suspend (String, Boolean, PlaceRegion, List<String>) -> com.zhisheng.weather.data.PlaceSearchOutcome)? = null) {
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
        fun scoped(values: List<City>) = values.filter { com.zhisheng.weather.data.cityMatchesSearchArea(it, requestArea) }
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
    Box(Modifier.fillMaxSize().zhishengScreen().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = chrome.contentMaxWidth)
            .fillMaxWidth().padding(horizontal = if (isPhosphorVista) chrome.pagePadding else 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = { dismissKeyboard(); onBack() }) {
                    if (isPhosphorVista) PhosphorIcon(R.drawable.ph_arrow_left, "返回", Modifier.size(24.dp), ZhishengText)
                    else Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ZhishengOrange)
                }
                Column(Modifier.weight(1f)) {
                    Text("添加地点", style = MaterialTheme.typography.titleLarge, color = ZhishengText)
                    Text(
                        "搜索城市、区县或街道，点结果即可加入首页",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                    )
                }
            }
            OutlinedTextField(value = state.query, onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                placeholder = { Text(if (state.preciseAddress) "城市、街道或地点" else "城市、区县或拼音", color = ZhishengTextTertiary) },
                leadingIcon = if (isPhosphorVista) ({ PhosphorIcon(R.drawable.ph_magnifying_glass, null, Modifier.size(22.dp), ZhishengTextSecondary) }) else null,
                trailingIcon = if (state.query.isNotEmpty()) ({ androidx.compose.material3.IconButton(onClick = { onQueryChanged("") }) {
                    PhosphorIcon(R.drawable.ph_x, "清空搜索", Modifier.size(20.dp), ZhishengTextSecondary)
                } }) else null,
                singleLine = true, shape = chrome.compactShape,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { dismissKeyboard(); onSearch() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZhishengMint,
                    unfocusedBorderColor = ZhishengTextTertiary.copy(alpha = 0.35f),
                    focusedTextColor = ZhishengText, unfocusedTextColor = ZhishengText, cursorColor = ZhishengMint))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.region.label} · ${state.results.size} 个结果", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                androidx.compose.material3.TextButton(onClick = { dismissKeyboard(); onSearch() }, modifier = Modifier.heightIn(min = 48.dp), enabled = state.query.isNotBlank() && !state.searching) {
                    Text(if (state.searching) "搜索中…" else "联网搜索", color = ZhishengMint)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Modifier.fillMaxWidth().padding(top = 14.dp).zhishengPanel().padding(16.dp),
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
                if (state.query.isNotBlank() && state.results.isEmpty() && !state.searching && state.failure == null) item {
                    if (state.region == PlaceRegion.DOMESTIC) {
                        androidx.compose.material3.TextButton(onClick = { onRegionChanged(PlaceRegion.OVERSEAS) }) { Text("切换海外搜索", color = ZhishengMint) }
                    }
                    SearchMessage(if (!state.onlineAttempted) "本地未找到，点联网搜索查更多地点"
                        else "没有找到「${state.query}」，请补充城市或区县名", ZhishengTextSecondary)
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
    }
}

@Composable
private fun SearchResultRow(city: City, index: Int, onCityPicked: (City) -> Unit) {
    val address = city.coordinateKind == com.zhisheng.weather.model.CoordinateKind.PLACE ||
        city.coordinateKind == com.zhisheng.weather.model.CoordinateKind.DEVICE ||
        (city.coordinateKind == com.zhisheng.weather.model.CoordinateKind.UNKNOWN && city.isPreciseLocation)
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
                        if (city.coordinateKind == com.zhisheng.weather.model.CoordinateKind.DEVICE) R.drawable.ph_crosshair else R.drawable.ph_map_pin,
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
