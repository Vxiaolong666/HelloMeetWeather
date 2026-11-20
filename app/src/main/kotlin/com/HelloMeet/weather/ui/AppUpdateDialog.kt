package com.HelloMeet.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.HelloMeet.weather.BuildConfig
import com.HelloMeet.weather.R
import com.HelloMeet.weather.data.AppUpdate
import com.HelloMeet.weather.data.AppUpdateCheck
import com.HelloMeet.weather.data.AppUpdateInfo
import com.HelloMeet.weather.ui.components.PhosphorIcon
import com.HelloMeet.weather.ui.theme.LocalZhishengPalette
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole

private sealed class UpdateUi {
    data object Checking : UpdateUi()
    data object UpToDate : UpdateUi()
    data class Available(val info: AppUpdateInfo) : UpdateUi()
    data class NeedPermission(val info: AppUpdateInfo) : UpdateUi()
    data class Downloading(val info: AppUpdateInfo) : UpdateUi()
    data class Ready(val info: AppUpdateInfo) : UpdateUi()
    data class Failed(val message: String, val info: AppUpdateInfo? = null) : UpdateUi()
}

@Composable
fun AppUpdateDialog(
    initialInfo: AppUpdateInfo? = null,
    downloadOnOpen: Boolean = false,
    onDeclineVersion: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember(initialInfo) {
        mutableStateOf<UpdateUi>(initialInfo?.let(UpdateUi::Available) ?: UpdateUi.Checking)
    }
    var progress by remember { mutableFloatStateOf(0f) }
    var requestedDownload by remember { mutableStateOf(false) }
    LaunchedEffect(ui, downloadOnOpen) {
        val info = (ui as? UpdateUi.Available)?.info
        if (downloadOnOpen && !requestedDownload && info != null && AppUpdate.canSelfUpdate()) {
            requestedDownload = true
            if (!AppUpdate.canInstall(context)) ui = UpdateUi.NeedPermission(info)
            else scope.launch { downloadAndInstall(context, info, { progress = it }, { ui = it }) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val info = (ui as? UpdateUi.NeedPermission)?.info ?: return@rememberLauncherForActivityResult
        if (AppUpdate.canInstall(context)) {
            scope.launch { downloadAndInstall(context, info, { progress = it }, { ui = it }) }
        }
    }

    LaunchedEffect(initialInfo) {
        if (initialInfo == null) {
            // 无预置信息 = 用户主动点开检查：绕过进程级缓存强制重查
            ui = when (val result = AppUpdate.check(refresh = true)) {
                is AppUpdateCheck.Available -> UpdateUi.Available(result.info)
                AppUpdateCheck.UpToDate -> UpdateUi.UpToDate
                is AppUpdateCheck.Failed -> UpdateUi.Failed(result.message)
            }
        }
    }

    val primary = primaryAction(ui)
    val enabled = ui !is UpdateUi.Checking && ui !is UpdateUi.Downloading
    val secondary = secondaryAction(ui)
    // 主操作点完是否自动关：只有「关闭」真正收起对话框；下载 / 授权 / 重试都要留在弹窗里
    // 看进度或结果，交给调用方按新状态重绘内容。
    val closeAfterPrimary = ui is UpdateUi.UpToDate || ui is UpdateUi.Ready

    MochiDialog(
        visible = true,
        onDismissRequest = onClose,
        title = "检查更新",
        message = "当前版本 v${BuildConfig.VERSION_NAME}",
        maxWidth = 420.dp,
        dismissOnOutsideTap = true,
        actions = buildList {
            if (secondary != null) {
                add(
                    MochiDialogAction(secondary.first) {
                        openUrl(context, secondary.second)
                    },
                )
            }
            add(
                MochiDialogAction(
                    label = primary,
                    role = MochiDialogActionRole.Primary,
                    enabled = enabled,
                    closeOnClick = closeAfterPrimary,
                ) {
                    when (val current = ui) {
                        UpdateUi.Checking, is UpdateUi.Downloading -> Unit
                        UpdateUi.UpToDate, is UpdateUi.Ready -> onClose()
                        is UpdateUi.Available -> {
                            if (!AppUpdate.canSelfUpdate()) {
                                openUrl(context, current.info.pageUrl)
                            } else if (!AppUpdate.canInstall(context)) {
                                ui = UpdateUi.NeedPermission(current.info)
                            } else {
                                scope.launch {
                                    downloadAndInstall(
                                        context,
                                        current.info,
                                        { progress = it },
                                        { ui = it },
                                    )
                                }
                            }
                        }
                        is UpdateUi.NeedPermission -> {
                            permissionLauncher.launch(AppUpdate.installPermissionIntent(context))
                        }
                        is UpdateUi.Failed -> {
                            ui = UpdateUi.Checking
                            scope.launch {
                                // 用户点"重试"：强制刷新，不吃失败/旧缓存
                                ui = when (val result = AppUpdate.check(refresh = true)) {
                                    is AppUpdateCheck.Available -> UpdateUi.Available(result.info)
                                    AppUpdateCheck.UpToDate -> UpdateUi.UpToDate
                                    is AppUpdateCheck.Failed -> UpdateUi.Failed(result.message)
                                }
                            }
                        }
                    }
                },
            )
            if (onDeclineVersion != null) {
                add(MochiDialogAction("稍后再说", MochiDialogActionRole.Cancel))
                add(MochiDialogAction("不再提醒此版本") { onDeclineVersion() })
            }
        }.toPersistentList(),
    ) {
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            UpdateStatus(ui, progress)
            val available = ui as? UpdateUi.Available
            if (available != null && available.info.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                UpdateNotesPanel(available.info.notes)
            }
        }
    }
}

@Composable
private fun UpdateStatus(ui: UpdateUi, progress: Float) {
    val p = LocalZhishengPalette.current
    val accent = when (ui) {
        is UpdateUi.Available, is UpdateUi.Ready -> p.mint
        is UpdateUi.Failed, is UpdateUi.NeedPermission -> p.orange
        else -> p.cyan
    }
    val icon = when (ui) {
        UpdateUi.Checking -> R.drawable.ph_arrow_clockwise
        UpdateUi.UpToDate -> R.drawable.ph_check
        is UpdateUi.Available -> R.drawable.ph_rocket_launch
        is UpdateUi.NeedPermission -> R.drawable.ph_shield
        is UpdateUi.Downloading -> R.drawable.ph_arrow_clockwise
        is UpdateUi.Ready -> R.drawable.ph_check
        is UpdateUi.Failed -> R.drawable.ph_warning
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) {
            PhosphorIcon(icon, null, Modifier.size(20.dp), accent)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                headline(ui),
                color = p.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                body(ui),
                color = p.textSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            if (ui is UpdateUi.Downloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = p.mint,
                    trackColor = p.cardBorder,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (progress > 0f) "${(progress * 100).toInt()}%" else "准备中",
                    color = p.textTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun UpdateNotesPanel(notes: String) {
    val p = LocalZhishengPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.card.copy(alpha = .55f))
            .border(1.dp, p.cardBorder.copy(alpha = .5f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            "更新内容",
            color = p.cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        notes.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val text = line.removePrefix("- ").trim()
                Row(Modifier.padding(bottom = 6.dp)) {
                    Text("·", color = p.textTertiary, fontSize = 12.sp, lineHeight = 19.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text,
                        color = p.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
    }
}

private suspend fun downloadAndInstall(
    context: android.content.Context,
    info: AppUpdateInfo,
    onProgress: (Float) -> Unit,
    setUi: (UpdateUi) -> Unit,
) {
    setUi(UpdateUi.Downloading(info))
    onProgress(0f)
    try {
        val file = AppUpdate.download(context, info) { value ->
            if (value != null) onProgress(value)
        }
        AppUpdate.install(context, file)
        setUi(UpdateUi.Ready(info))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (t: Exception) {
        setUi(UpdateUi.Failed(t.message?.takeIf { it.isNotBlank() } ?: "下载或安装失败", info))
    }
}

private fun headline(ui: UpdateUi): String = when (ui) {
    UpdateUi.Checking -> "正在检查"
    UpdateUi.UpToDate -> "已是最新版本"
    is UpdateUi.Available -> "发现新版本 v${ui.info.versionName}"
    is UpdateUi.NeedPermission -> "需要允许安装"
    is UpdateUi.Downloading -> "正在下载 v${ui.info.versionName}"
    is UpdateUi.Ready -> "已打开系统安装页"
    is UpdateUi.Failed -> "暂时无法完成"
}

private fun body(ui: UpdateUi): String = when (ui) {
    UpdateUi.Checking -> "正在查询公共版版本信息，不会自动下载。"
    UpdateUi.UpToDate -> "当前安装的就是最新公共版。有新版本时，再点检查更新即可下载。"
    is UpdateUi.Available ->
        if (AppUpdate.canSelfUpdate()) {
            "下载后会打开系统安装页，需要再确认一次，不会在后台自动安装。"
        } else {
            "当前版本需从 ${ui.info.channel.displayName} 获取更新，不能由公共版直接覆盖。"
        }
    is UpdateUi.NeedPermission -> "系统要求先允许遇见天气安装未知应用，然后才能打开安装页。授权后会自动继续下载。"
    is UpdateUi.Downloading -> "安装包正在下载，请保持网络连接。"
    is UpdateUi.Ready -> "请在系统安装页确认覆盖安装。如果取消了，可以再点检查更新。"
    is UpdateUi.Failed -> ui.message
}

private fun primaryAction(ui: UpdateUi): String = when (ui) {
    UpdateUi.Checking -> "检查中"
    UpdateUi.UpToDate, is UpdateUi.Ready -> "关闭"
    is UpdateUi.Available -> if (AppUpdate.canSelfUpdate()) "立即更新" else pageActionLabel(ui.info)
    is UpdateUi.NeedPermission -> "去授权"
    is UpdateUi.Downloading -> "下载中"
    is UpdateUi.Failed -> "重试"
}

private fun secondaryAction(ui: UpdateUi): Pair<String, String>? = when (ui) {
    is UpdateUi.Available, is UpdateUi.NeedPermission, is UpdateUi.Downloading, is UpdateUi.Ready -> null
    is UpdateUi.Failed -> if (AppUpdate.canSelfUpdate()) null else ui.info?.let { pageActionLabel(it) to it.pageUrl }
        ?: "打开 Gitee" to AppUpdate.GITEE_RELEASES_PAGE
    else -> null
}

private fun pageActionLabel(info: AppUpdateInfo): String = "打开 ${info.channel.displayName}"

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
