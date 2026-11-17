package com.HelloMeet.weather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.HelloMeet.weather.data.LocalPlaces
import com.HelloMeet.weather.data.manualWeatherPlace
import com.HelloMeet.weather.model.City
import com.HelloMeet.weather.model.PlaceRegion
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette
import com.HelloMeet.weather.ui.theme.zhishengDialogPanel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiSheetDetent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.picker.MochiWheelPicker
import ui.feice.mochi.picker.MochiWheelPickerGroup
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.utils.rememberMochiHaptics

private const val ALL_PROVINCES = "全部省份"
private const val ALL_CITIES = "全省 / 全地区"

/** 添加地点页的两个浮层：地区筛选面板与自定义经纬度对话框，由页面上的按钮触发。 */
@Stable
internal class SearchPlaceToolsState {
    internal var areaSheetVisible by mutableStateOf(false)
    internal var manualDialogVisible by mutableStateOf(false)

    fun openAreaSheet() {
        areaSheetVisible = true
    }

    fun openManualDialog() {
        manualDialogVisible = true
    }
}

@Composable
internal fun rememberSearchPlaceToolsState(): SearchPlaceToolsState = remember { SearchPlaceToolsState() }

/**
 * 省市二级联调：底部面板 + 双列滚筒。左列省份、右列该省的城市；
 * 滚省份时即时换城市列表，点「确定」才提交筛选。
 */
@Composable
internal fun AreaWheelSheet(
    state: SearchUiState,
    tools: SearchPlaceToolsState,
    onAreaChanged: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val sheet = rememberMochiSheetState(listOf(MochiSheetDetent.Medium))
    var draftProvince by remember { mutableStateOf("") }
    var draftMunicipality by remember { mutableStateOf("") }
    var municipalityOptions by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(tools.areaSheetVisible) {
        if (!tools.areaSheetVisible) return@LaunchedEffect
        draftProvince = state.province
        draftMunicipality = state.municipality
        sheet.show()
    }
    LaunchedEffect(draftProvince) {
        municipalityOptions = if (draftProvince.isBlank()) {
            emptyList()
        } else {
            LocalPlaces.areas(context, draftProvince)
        }
    }

    val provinceItems: ImmutableList<String> = remember(state.provinces) {
        (listOf(ALL_PROVINCES) + state.provinces).toImmutableList()
    }
    val cityItems: ImmutableList<String> = remember(draftProvince, municipalityOptions) {
        if (draftProvince.isBlank()) {
            listOf(ALL_CITIES).toImmutableList()
        } else {
            (listOf(ALL_CITIES) + municipalityOptions).toImmutableList()
        }
    }
    val provinceIndex = (state.provinces.indexOf(draftProvince) + 1).coerceIn(0, provinceItems.lastIndex)
    val cityIndex = (municipalityOptions.indexOf(draftMunicipality) + 1).coerceIn(0, cityItems.lastIndex)

    fun close() {
        tools.areaSheetVisible = false
        sheet.hide()
    }

    MochiSheet(sheet, onDismiss = { tools.areaSheetVisible = false }) {
        Column(Modifier.fillMaxSize()) {
            MochiSheetHeader(
                title = "选择地区",
                navigationIcon = {
                    TextButton(onClick = ::close) { Text("取消", color = MochiTheme.colorScheme.primary) }
                },
            )
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                MochiWheelPickerGroup(Modifier.fillMaxWidth()) {
                    MochiWheelPicker(
                        items = provinceItems,
                        selectedIndex = provinceIndex,
                        onSelectedIndexChange = { index ->
                            draftProvince = if (index == 0) "" else state.provinces[index - 1]
                            draftMunicipality = ""
                        },
                        modifier = Modifier.weight(1.2f),
                    )
                    MochiWheelPicker(
                        items = cityItems,
                        selectedIndex = cityIndex,
                        onSelectedIndexChange = { index ->
                            draftMunicipality =
                                if (index == 0 || draftProvince.isBlank()) "" else municipalityOptions[index - 1]
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                if (draftProvince.isBlank()) "不限省份，按关键词搜索国内地点" else {
                    "筛选：$draftProvince · ${draftMunicipality.ifBlank { "全部城市" }}"
                },
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = MochiTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MochiTheme.textStyles.footnote,
            )
            Spacer(Modifier.height(12.dp))
            MochiButton(
                onClick = {
                    onAreaChanged(draftProvince, draftMunicipality)
                    close()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                // Material 的 Text 不继承库按钮的内容色，浅色模式下会变黑，这里显式给。
                Text("确定", color = MochiTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 自定义经纬度地点：用组件库对话框承载输入。
 * 信息不全时「添加地点」置灰；坐标格式不对时整框左右抖一下并轻振，不关闭对话框。
 */
@Composable
internal fun ManualPlaceDialog(tools: SearchPlaceToolsState, onPick: (City) -> Unit) {
    val systems = persistentListOf("WGS84", "GCJ02", "BD09")
    var name by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf("WGS84") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val complete = name.isNotBlank() && lat.isNotBlank() && lon.isNotBlank()
    val city = manualWeatherPlace(name, lat, lon, system)

    val shake = remember { Animatable(0f) }
    val haptics = rememberMochiHaptics()
    val scope = rememberCoroutineScope()
    val distance = with(LocalDensity.current) { 12.dp.toPx() }

    fun reject() {
        haptics.threshold()
        scope.launch {
            shake.snapTo(0f)
            // 左右来回、幅度递减，最后回到原位。
            listOf(-distance, distance * 0.8f, -distance * 0.55f, distance * 0.3f, 0f).forEach { target ->
                shake.animateTo(target, tween(48, easing = LinearEasing))
            }
        }
    }

    LaunchedEffect(tools.manualDialogVisible) {
        if (!tools.manualDialogVisible) return@LaunchedEffect
        attempted = false
        shake.snapTo(0f)
    }

    MochiDialog(
        visible = tools.manualDialogVisible,
        onDismissRequest = { tools.manualDialogVisible = false },
        modifier = Modifier.graphicsLayer { translationX = shake.value },
        title = "自定义天气地点",
        message = "无需定位权限。输入地图提供的坐标，并选择对应坐标系；天气精细程度仍取决于天气源。",
        actions = persistentListOf(
            MochiDialogAction("取消", MochiDialogActionRole.Cancel) { tools.manualDialogVisible = false },
            MochiDialogAction(
                "添加地点",
                MochiDialogActionRole.Primary,
                enabled = complete,
                // 校验不过时保持打开，由这里自己决定何时关闭。
                closeOnClick = false,
            ) {
                attempted = true
                val place = manualWeatherPlace(name, lat, lon, system)
                if (place != null) {
                    tools.manualDialogVisible = false
                    onPick(place)
                } else {
                    reject()
                }
            },
        ),
    ) {
        MochiTextField(
            name,
            { name = it.take(40) },
            Modifier.fillMaxWidth(),
            placeholder = "地点名称",
        )
        MochiTextField(
            lat,
            { lat = it.take(24) },
            Modifier.fillMaxWidth(),
            placeholder = "纬度（−90 至 90）",
            error = if (attempted && city == null && lat.isNotBlank()) "纬度格式不正确" else null,
        )
        MochiTextField(
            lon,
            { lon = it.take(24) },
            Modifier.fillMaxWidth(),
            placeholder = "经度（−180 至 180）",
            error = if (attempted && city == null && lon.isNotBlank()) "经度格式不正确" else null,
        )
        MochiSegmentedControl(
            selectedIndex = systems.indexOf(system).coerceAtLeast(0),
            onSelect = { system = systems[it] },
            items = systems,
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempted && city == null) {
            Text(
                "请填写地点名和有效的经纬度，使用小数点，勿填写度分秒。",
                color = MochiTheme.colorScheme.error,
                style = MochiTheme.textStyles.footnote,
            )
        }
    }
}

/**
 * 旧版搜索页使用的工具条：保留省份 / 城市与经纬度入口，浮层与新版共用。
 * 新版添加地点页把入口放到搜索栏右侧与顶栏右上角，不再调用这个组合。
 */
@Composable
internal fun SearchPlaceTools(state: SearchUiState, onAreaChanged: (String, String) -> Unit, onPick: (City) -> Unit) {
    val tools = rememberSearchPlaceToolsState()
    val primary = MochiTheme.colorScheme.primary
    if (state.region == PlaceRegion.DOMESTIC) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = tools::openAreaSheet, modifier = Modifier.weight(1f)) {
                Text(state.province.ifBlank { "先选省份 ▾" }, color = primary)
            }
            TextButton(onClick = tools::openAreaSheet, modifier = Modifier.weight(1f)) {
                Text(state.municipality.ifBlank { "再选城市 ▾" }, color = primary)
            }
        }
    }
    TextButton(onClick = tools::openManualDialog, modifier = Modifier.fillMaxWidth()) {
        Text("用经纬度添加自定义地点", color = primary)
    }
    AreaWheelSheet(state, tools, onAreaChanged)
    ManualPlaceDialog(tools, onPick)
}

/** 快照测试用的内嵌面板版本（不进应用页面）。 */
@Composable
internal fun ManualPlacePanel(onDismiss: () -> Unit, onPick: (City) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalZhishengPalette.current
    var name by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf("WGS84") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val city = manualWeatherPlace(name, lat, lon, system)
    Column(
        modifier.zhishengDialogPanel(glass = true).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("自定义天气地点", color = p.text, style = MaterialTheme.typography.titleLarge)
        Text(
            "无需定位权限。输入地图提供的坐标，并选择对应坐标系；天气精细程度仍取决于天气源。",
            color = p.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("地点名称") }, singleLine = true)
        OutlinedTextField(
            lat, { lat = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("纬度（−90 至 90）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
        OutlinedTextField(
            lon, { lon = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("经度（−180 至 180）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
        VistaSettingsChoices(
            listOf("GPS / WGS84" to "WGS84", "高德 / GCJ02" to "GCJ02", "百度 / BD09" to "BD09"),
            system,
            { system = it },
        )
        if (attempted && city == null) {
            Text("请填写地点名和有效的经纬度，使用小数点，勿填写度分秒。", color = p.red, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onDismiss) { Text("取消", color = p.textSecondary) }
            Button(onClick = {
                attempted = true
                city?.let(onPick)
            }) { Text("添加地点") }
        }
    }
}
