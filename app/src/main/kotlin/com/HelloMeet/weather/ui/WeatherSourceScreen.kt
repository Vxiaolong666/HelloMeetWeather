package com.HelloMeet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.LocationEnginePref
import com.HelloMeet.weather.data.SourcePref
import com.HelloMeet.weather.data.WeatherModule
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.components.WeatherOverlayDialog
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette
import com.HelloMeet.weather.ui.theme.ZhishengText
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import com.HelloMeet.weather.ui.theme.zhishengDialogPanel
import com.HelloMeet.weather.ui.theme.zhishengPanel

/** 每个天气来源对应的 Phosphor 图标，供来源列表与模块行统一使用。 */
internal fun weatherSourceIcon(source: SourcePref?): Int = when (source) {
    null -> R.drawable.ph_arrow_clockwise
    SourcePref.AUTO -> R.drawable.ph_sparkle
    SourcePref.ZHISHENG -> R.drawable.ph_sparkle
    SourcePref.NMC -> R.drawable.ph_broadcast
    SourcePref.QWEATHER -> R.drawable.ph_wind
    SourcePref.CAIYUN -> R.drawable.ph_cloud_rain
    SourcePref.AMAP -> R.drawable.ph_map_trifold
    SourcePref.BAIDU -> R.drawable.ph_map_pin
    SourcePref.TENCENT -> R.drawable.ph_cloud
    SourcePref.WEATHERCN -> R.drawable.ph_sun
    SourcePref.SENIVERSE -> R.drawable.ph_leaf
    SourcePref.MOJI -> R.drawable.ph_cloud_rain
    SourcePref.XIAOMI -> R.drawable.ph_cloud
    SourcePref.OPEN_METEO -> R.drawable.ph_waves
}

/** 各天气模块的图标。 */
internal fun weatherModuleIcon(module: WeatherModule): Int = when (module) {
    WeatherModule.CURRENT -> R.drawable.ph_gauge
    WeatherModule.FORECAST -> R.drawable.ph_cloud
    WeatherModule.MINUTELY -> R.drawable.ph_cloud_rain
    WeatherModule.ALERTS -> R.drawable.ph_warning
    WeatherModule.INDICES -> R.drawable.ph_person_simple_walk
    WeatherModule.AIR -> R.drawable.ph_leaf
}

/** 每个定位地理服务引擎对应的 Phosphor 图标，供引擎列表与入口行统一使用。 */
internal fun locationEngineIcon(engine: LocationEnginePref): Int = when (engine) {
    LocationEnginePref.AUTO -> R.drawable.ph_crosshair
    LocationEnginePref.SYSTEM -> R.drawable.ph_compass
    LocationEnginePref.AMAP -> R.drawable.ph_map_pin
    LocationEnginePref.BAIDU -> R.drawable.ph_map_trifold
    LocationEnginePref.SHARED -> R.drawable.ph_database
}

/**
 * 模块来源选择弹窗：点模块行后不再整页切换，列表上下文保持不动。
 * 选项复用 [SourceOption]，不可用项保持最淡灰。
 */
@Composable
internal fun ModuleSourcePickerDialog(
    module: WeatherModule,
    primary: SourcePref,
    current: SourcePref?,
    visible: Set<SourcePref>,
    available: Set<SourcePref>,
    saving: Boolean,
    onSelect: (SourcePref?) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    val palette = LocalZhishengPalette.current
    WeatherOverlayDialog(onDismiss = onDismiss, maxWidth = 480.dp) { modifier ->
        Column(
            modifier.zhishengDialogPanel().padding(vertical = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${module.cn} · 选择来源",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ZhishengText,
                    )
                    Text(
                        "跟随主源：${primary.cn}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextSecondary,
                    )
                }
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(if (saving) "保存中…" else "取消", color = palette.mint)
                }
            }
            if (error != null) {
                Text(
                    error,
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.red,
                )
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                weatherSourceChoices(module, visible, available, current).forEach { choice ->
                    SourceOption(
                        source = choice.source,
                        hint = if (!choice.enabled) {
                            "当前不可用或不支持该模块"
                        } else {
                            choice.source?.desc ?: "跟随主源 ${primary.cn}"
                        },
                        selected = choice.source == current,
                        enabled = choice.enabled && !saving,
                        onSelect = { onSelect(choice.source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(source: SourcePref?, active: Boolean, muted: Boolean = false) {
    val palette = LocalZhishengPalette.current
    val tone = when {
        muted -> ZhishengTextTertiary.copy(alpha = DisabledInkTint)
        active -> palette.mint
        else -> ZhishengTextSecondary
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        PhosphorIcon(weatherSourceIcon(source), null, Modifier.size(18.dp), tone)
    }
}

/** 不可用/不支持该模块的置灰：比正文三级文字更淡，避免读起来仍像可选。 */
internal const val DisabledInkTint = 0.45f

@Composable
internal fun SourceOption(
    source: SourcePref?,
    hint: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalZhishengPalette.current
    val disabledInk = ZhishengTextTertiary.copy(alpha = DisabledInkTint)
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).zhishengPanel()) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected, enabled = enabled, role = Role.RadioButton) { onSelect() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceBadge(source, active = selected, muted = !enabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    source?.cn ?: "跟随主源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (enabled) ZhishengText else disabledInk,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) ZhishengTextSecondary else disabledInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = palette.mint,
                    unselectedColor = ZhishengTextTertiary.copy(alpha = DisabledInkTint),
                ),
            )
        }
    }
}
