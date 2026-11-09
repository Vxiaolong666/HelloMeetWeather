package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.theme.zhishengScreen
import kotlinx.coroutines.launch

/** 单个自有服务接入项：用于把设置页原来内联的 12 行操作收敛成可遍历的清单。 */
private data class ProviderAccessItem(
    val title: String,
    val description: String,
    val icon: Int,
    val ready: Boolean,
    val kind: ProviderWizardKind,
    val clear: suspend () -> Unit,
)

private data class ProviderAccessGroup(
    val title: String,
    val caption: String,
    val items: List<ProviderAccessItem>,
)

/**
 * 自有服务接入二级页：按「气象数据 / 地理服务」分组，每项带图标、接入状态与独立操作，
 * 页面骨架与空气质量等二级页一致（zhishengScreen + FeaturePageHeader + zhishengPanel）。
 */
@Composable
fun ProviderAccessScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val qwRt by SecretStore.qwRuntimeFlow.collectAsState(initial = SecretStore.qwRuntime)
    val caiyunRt by SecretStore.caiyunRuntimeFlow.collectAsState(initial = SecretStore.caiyunRuntime)
    val amapRt by SecretStore.amapRuntimeFlow.collectAsState(initial = SecretStore.amapRuntime)
    val baiduRt by SecretStore.baiduRuntimeFlow.collectAsState(initial = SecretStore.baiduRuntime)
    val seniverseRt by SecretStore.seniverseRuntimeFlow.collectAsState(initial = SecretStore.seniverseRuntime)
    val mojiRt by SecretStore.mojiRuntimeFlow.collectAsState(initial = SecretStore.mojiRuntime)
    var wizard by rememberSaveable { mutableStateOf<ProviderWizardKind?>(null) }
    var pendingClear by remember { mutableStateOf<ProviderAccessItem?>(null) }

    val groups = remember(
        qwRt.ready, caiyunRt.ready, amapRt.ready, baiduRt.ready, seniverseRt.ready, mojiRt.ready,
    ) {
        listOf(
            ProviderAccessGroup("气象数据", "接入后可在大气来源中单独指定", listOf(
                ProviderAccessItem("和风天气", "预报、逐时与预警 · 个人开发版", R.drawable.ph_wind,
                    qwRt.ready, ProviderWizardKind.QWEATHER) { SecretStore.clearQw() },
                ProviderAccessItem("彩云天气", "分钟级降水与雷达回波", R.drawable.ph_cloud_rain,
                    caiyunRt.ready, ProviderWizardKind.CAIYUN) { SecretStore.clearCaiyun() },
                ProviderAccessItem("心知天气", "天气实况与生活指数 · 个人免费版", R.drawable.ph_leaf,
                    seniverseRt.ready, ProviderWizardKind.SENIVERSE) { SecretStore.clearSeniverse() },
                ProviderAccessItem("墨迹天气", "天气与预警 · 个人订阅", R.drawable.ph_cloud,
                    mojiRt.ready, ProviderWizardKind.MOJI) { SecretStore.clearMoji() },
            )),
            ProviderAccessGroup("地理服务", "地名搜索、坐标转换与街道识别", listOf(
                ProviderAccessItem("高德服务", "地理编码与逆地址解析", R.drawable.ph_map_trifold,
                    amapRt.ready, ProviderWizardKind.AMAP) { SecretStore.clearAmap() },
                ProviderAccessItem("百度服务", "地理编码与坐标定位", R.drawable.ph_map_pin,
                    baiduRt.ready, ProviderWizardKind.BAIDU) { SecretStore.clearBaidu() },
            )),
        )
    }
    val total = groups.sumOf { it.items.size }
    val connected = groups.sumOf { group -> group.items.count { it.ready } }

    Box(Modifier.fillMaxSize().zhishengScreen()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            FeaturePageHeader("服务接入", subtitle = "PROVIDER ACCESS", onBack = onBack)

            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("自有服务凭据", style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                Spacer(Modifier.height(3.dp))
                Text(
                    "已接入 $connected / $total · 凭据仅保存在本机",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengTextSecondary,
                )
            }

            Spacer(Modifier.height(6.dp))

            groups.forEach { group ->
                GroupLabel(group.title, group.caption)
                group.items.forEach { item ->
                    ProviderCard(
                        item = item,
                        onConfigure = { wizard = item.kind },
                        onClear = { pendingClear = item },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    pendingClear?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            containerColor = ZhishengBg,
            title = { Text("清除 ${item.title} 凭据？", color = ZhishengText) },
            text = {
                Text(
                    "清除后该服务将回到未接入状态，需要重新配置才能继续使用。",
                    color = ZhishengTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingClear = null
                    scope.launch { item.clear() }
                }) { Text("清除", color = ZhishengOrange) }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) {
                    Text("取消", color = ZhishengTextSecondary)
                }
            },
        )
    }

    wizard?.let { ProviderWizard(kind = it, onClose = { wizard = null }) }
}

@Composable
private fun GroupLabel(title: String, caption: String) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = ZhishengText,
        )
        Spacer(Modifier.height(2.dp))
        Text(caption, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
    }
}

@Composable
private fun ProviderCard(item: ProviderAccessItem, onConfigure: () -> Unit, onClear: () -> Unit) {
    val tone = if (item.ready) ZhishengMint else ZhishengTextSecondary
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).zhishengPanel()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tone.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PhosphorIcon(item.icon, null, Modifier.size(18.dp), tone)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ZhishengText,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(item.ready)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderAction(
                    label = if (item.ready) "重新配置" else "接入",
                    enabled = true,
                    color = ZhishengMint,
                    onClick = onConfigure,
                    modifier = Modifier.weight(1f),
                )
                ProviderAction(
                    label = "清除凭据",
                    enabled = item.ready,
                    color = ZhishengOrange,
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(ready: Boolean) {
    val tone = if (ready) ZhishengMint else ZhishengTextTertiary
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tone.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(5.dp))
        Text(
            if (ready) "已接入" else "未接入",
            style = MaterialTheme.typography.labelSmall,
            color = tone,
        )
    }
}

@Composable
private fun ProviderAction(
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = if (enabled) color else ZhishengTextTertiary
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = if (enabled) 0.14f else 0.06f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tone,
        )
    }
}
