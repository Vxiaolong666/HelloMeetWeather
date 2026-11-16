package com.HelloMeet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.HomeModule
import com.HelloMeet.weather.data.LifeIndexMetric
import com.HelloMeet.weather.data.TelemetryMetric
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.home.lifeIndexIcon
import com.HelloMeet.weather.ui.theme.ZhishengCardBorder
import com.HelloMeet.weather.ui.theme.ZhishengMint
import com.HelloMeet.weather.ui.theme.ZhishengText
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import com.HelloMeet.weather.ui.theme.zhishengPanel

/** 遥测指标图标（与首页遥测卡片一致）。 */
internal fun telemetryMetricIcon(metric: TelemetryMetric): Int = when (metric) {
    TelemetryMetric.HUMIDITY -> R.drawable.ph_drop
    TelemetryMetric.WIND, TelemetryMetric.WIND_GUST -> R.drawable.ph_wind
    TelemetryMetric.PRESSURE -> R.drawable.ph_gauge
    TelemetryMetric.UV -> R.drawable.ph_sun
    TelemetryMetric.VISIBILITY -> R.drawable.ph_eye
    TelemetryMetric.DEW_POINT -> R.drawable.ph_thermometer
    TelemetryMetric.CLOUD_COVER -> R.drawable.ph_cloud
    TelemetryMetric.PRECIPITATION -> R.drawable.ph_cloud_rain
    TelemetryMetric.LUMINARY -> R.drawable.ph_moon_stars
}

/** 首页模块图标。 */
internal fun homeModuleIcon(module: HomeModule): Int = when (module) {
    HomeModule.HOURLY -> R.drawable.ph_clock
    HomeModule.PRECIP -> R.drawable.ph_cloud_rain
    HomeModule.SPACETIME -> R.drawable.ph_map_trifold
    HomeModule.DAILY -> R.drawable.ph_list
    HomeModule.TELEMETRY -> R.drawable.ph_gauge
    HomeModule.AQI -> R.drawable.ph_leaf
    HomeModule.OUTFIT -> R.drawable.ph_t_shirt
    HomeModule.INDICES -> R.drawable.ph_person_simple_walk
    HomeModule.YESTERDAY -> R.drawable.ph_arrow_clockwise
    HomeModule.TYPHOON -> R.drawable.ph_warning
    HomeModule.FEATURES -> R.drawable.ph_sparkle
}

/** 遥测项目选择弹窗：全选 / 清空 + 带图标的逐项开关。 */
@Composable
internal fun TelemetryMetricsDialog(
    selected: Set<TelemetryMetric>,
    onToggle: (TelemetryMetric) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ContentDialogShell(
        title = "遥测项目",
        subtitle = "${selected.size} / ${TelemetryMetric.entries.size} 项显示在首页",
        icon = R.drawable.ph_gauge,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onSelectAll, enabled = selected.size != TelemetryMetric.entries.size) {
                Text("全选", color = ZhishengMint)
            }
            TextButton(onClick = onClear, enabled = selected.isNotEmpty()) {
                Text("清空", color = ZhishengTextSecondary)
            }
        },
    ) {
        TelemetryMetric.entries.forEach { metric ->
            MetricToggleRow(
                icon = telemetryMetricIcon(metric),
                title = metric.cn,
                subtitle = metric.en,
                checked = metric in selected,
                onToggle = { onToggle(metric) },
            )
        }
    }
}

/** 生活指数项目选择弹窗。 */
@Composable
internal fun LifeIndexMetricsDialog(
    selected: Set<LifeIndexMetric>,
    onToggle: (LifeIndexMetric) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ContentDialogShell(
        title = "生活指数项目",
        subtitle = "${selected.size} / ${LifeIndexMetric.entries.size} 项显示在首页",
        icon = R.drawable.ph_person_simple_walk,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onSelectAll, enabled = selected.size != LifeIndexMetric.entries.size) {
                Text("全选", color = ZhishengMint)
            }
            TextButton(onClick = onClear, enabled = selected.isNotEmpty()) {
                Text("清空", color = ZhishengTextSecondary)
            }
        },
    ) {
        LifeIndexMetric.entries.forEach { metric ->
            MetricToggleRow(
                icon = lifeIndexIcon(metric.en),
                title = metric.cn,
                subtitle = metric.en,
                checked = metric in selected,
                onToggle = { onToggle(metric) },
            )
        }
    }
}

/** 模块布局弹窗：长按拖动排序 + 恢复默认。 */
@Composable
internal fun ModuleOrderDialog(
    order: List<HomeModule>,
    onReorder: (List<HomeModule>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ContentDialogShell(
        title = "模块布局",
        subtitle = "长按并拖动，调整首页模块顺序",
        icon = R.drawable.ph_list,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onReset, enabled = order != HomeModule.defaultOrder) {
                Text("恢复默认顺序", color = ZhishengMint)
            }
        },
    ) {
        ModuleOrderEditor(order, onReorder)
    }
}

@Composable
private fun ContentDialogShell(
    title: String,
    subtitle: String,
    icon: Int,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelMaxHeight = (maxHeight - 24.dp).coerceIn(240.dp, 640.dp)
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable { onDismiss() },
            )
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = panelMaxHeight).zhishengPanel(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(ZhishengMint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            PhosphorIcon(icon, null, Modifier.size(16.dp), ZhishengMint)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ZhishengText,
                            )
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = ZhishengTextSecondary,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭", tint = ZhishengTextSecondary)
                        }
                    }
                    HorizontalDivider(color = ZhishengCardBorder.copy(alpha = 0.4f))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        content = actions,
                    )
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        content()
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricToggleRow(
    icon: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhosphorIcon(
            icon,
            null,
            Modifier.size(17.dp),
            if (checked) ZhishengMint else ZhishengTextTertiary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = ZhishengText)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = ZhishengMint,
                checkedThumbColor = Color.White,
            ),
        )
    }
}
