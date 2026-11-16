package com.HelloMeet.weather.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.LocationSource
import com.HelloMeet.weather.data.SettingsRepository
import com.HelloMeet.weather.ui.HomeUiState
import com.HelloMeet.weather.ui.MochiAppTheme
import com.HelloMeet.weather.ui.Text
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.rememberMochiColorScheme
import com.HelloMeet.weather.ui.theme.ZhishengCard
import com.HelloMeet.weather.ui.theme.ZhishengCyan
import com.HelloMeet.weather.ui.theme.ZhishengMint
import com.HelloMeet.weather.ui.theme.ZhishengOrange
import com.HelloMeet.weather.ui.theme.ZhishengTextSecondary
import com.HelloMeet.weather.ui.theme.ZhishengTextTertiary
import com.HelloMeet.weather.ui.theme.isPhosphorVista
import com.HelloMeet.weather.ui.theme.zhishengCompactPanel
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/**
 * 独立城市管理页：与首页侧边栏共用同一份城市列表与定位逻辑，只是改成整屏排版。
 * 入口由「设置 → 外观与设备 → 城市管理页面 → 页面样式」切换。
 */
@Composable
fun CityManagerScreen(
    uiState: HomeUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLocate: () -> Unit,
    onClearLocateMessage: () -> Unit,
    onAddCity: () -> Unit,
    // 选城时把被点卡片的屏幕矩形一并上报，由应用根部播「卡片扩散成整屏首页」的反向容器变换。
    onSelectAt: (String, androidx.compose.ui.geometry.Rect) -> Unit = { key, _ -> onSelect(key) },
) {
    val colors = rememberMochiColorScheme()
    MochiAppTheme(colors = colors) {
        ProvideMochiOverscroll {
            Column(
                Modifier.fillMaxSize()
                    .background(MochiDefaults.groupedBackground())
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MochiBackButton(onClick = onBack)
                    Text(
                        "城市管理",
                        Modifier.weight(1f).padding(horizontal = 8.dp),
                        style = MochiTheme.textStyles.navTitle,
                        color = MochiTheme.colorScheme.onSurface,
                    )
                    MochiBarIconButton(onClick = onAddCity) {
                        PhosphorIcon(
                            R.drawable.ph_plus, "添加城市", Modifier.size(22.dp),
                            MochiTheme.colorScheme.primary,
                        )
                    }
                }
                CityWeatherList(
                    ui = uiState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    onAdd = onAddCity,
                    onSelect = onSelect,
                    onSelectAt = onSelectAt,
                    onFavorite = onToggleFavorite,
                    onRemove = onRemove,
                    active = true,
                )
                CityLocateActions(
                    uiState = uiState,
                    onLocate = onLocate,
                    onClearLocateMessage = onClearLocateMessage,
                    onAddCity = onAddCity,
                    modifier = Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 城市列表底部的定位与反馈区：侧边栏与独立城市管理页共用同一套权限流程。 */
@Composable
internal fun CityLocateActions(
    uiState: HomeUiState,
    onLocate: () -> Unit,
    onClearLocateMessage: () -> Unit,
    onAddCity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preciseEnabled by SettingsRepository.preciseLocationEnabled.collectAsState(initial = true)
    val precisePermissionAsked by SettingsRepository.preciseLocationPermissionAsked.collectAsState(initial = false)
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var locationServicesOff by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (permissionDenied && LocationSource.hasPermission(context)) permissionDenied = false
        if (locationServicesOff && LocationSource.locationEnabledOnDevice(context)) locationServicesOff = false
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        scope.launch {
            SettingsRepository.setPreciseLocationPermissionAsked()
            if (LocationSource.hasPermission(context)) {
                permissionDenied = false
                onLocate()
            } else {
                permissionDenied = true
            }
        }
    }
    val requestPreciseLocation = {
        onClearLocateMessage()
        permissionDenied = false
        locationServicesOff = !LocationSource.locationEnabledOnDevice(context)
        if (!locationServicesOff) {
            scope.launch {
                val canLocate = LocationSource.hasPermission(context) &&
                    (!preciseEnabled || LocationSource.hasPrecisePermission(context) || precisePermissionAsked)
                if (canLocate) onLocate()
                else permissionLauncher.launch(LocationSource.requestedPermissions(precise = preciseEnabled))
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val feedback = when {
            permissionDenied -> "已拒绝位置权限，请在系统设置中选择“使用应用时允许”"
            locationServicesOff -> "系统定位服务未开启"
            else -> uiState.locateMessage
        }
        val approximateOnly = feedback?.contains("系统仅授予大致位置") == true
        feedback?.let { message ->
            Column(
                modifier = Modifier.fillMaxWidth().background(ZhishengCard).padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    if (isPhosphorVista) message else "> $message",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.startsWith("已精确") || message.startsWith("已定位")) ZhishengMint else ZhishengOrange,
                )
                if (permissionDenied || locationServicesOff || approximateOnly) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            locationServicesOff -> "[ 开启手机定位服务 ]"
                            approximateOnly -> "[ 提升为精确位置 ]"
                            else -> "[ 去应用权限设置 ]"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = ZhishengCyan,
                        modifier = Modifier.clickable(role = Role.Button) {
                            val intent = if (!locationServicesOff) {
                                Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            } else {
                                Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)
                            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }.padding(vertical = 3.dp),
                    )
                }
            }
        }
        if (isPhosphorVista) {
            TextButton(onClick = requestPreciseLocation, enabled = !uiState.locating,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                PhosphorIcon(R.drawable.ph_crosshair, null, Modifier.size(17.dp), ZhishengTextSecondary)
                Spacer(Modifier.width(7.dp))
                Text(if (uiState.locating) "定位中…" else "定位当前位置", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zhishengCompactPanel(
                        containerColor = ZhishengCard,
                        borderColor = ZhishengCyan.copy(alpha = 0.55f),
                    )
                    .clickable(enabled = !uiState.locating, onClickLabel = "精确定位当前位置") {
                        requestPreciseLocation()
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhosphorIcon(R.drawable.ph_crosshair, null, Modifier.size(21.dp), ZhishengCyan)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (uiState.locating) "定位中 ..." else "定位当前位置",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (uiState.locating) ZhishengTextTertiary else ZhishengCyan,
                    letterSpacing = 1.sp,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zhishengCompactPanel(
                        containerColor = ZhishengCard,
                        borderColor = ZhishengMint.copy(alpha = 0.5f),
                    )
                    .clickable(onClickLabel = "添加城市") { onAddCity() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Add, contentDescription = null, tint = ZhishengMint, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("添加城市", style = MaterialTheme.typography.titleSmall, color = ZhishengMint, letterSpacing = 1.sp)
            }
        }
    }
}
