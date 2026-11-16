package com.HelloMeet.weather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.HelloMeet.weather.R
import com.HelloMeet.weather.ui.components.PhosphorIcon
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiPageControl
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection

/**
 * 服务接入引导的新版页面：气象数据源（和风 / 彩云 / 心知 / 墨迹）与定位地理服务
 * （高德 / 百度）共用同一条流程。页面骨架、进度、列表卡片、选项、输入框与按钮
 * 全部使用 mochi-compose 组件，凭据验证与写入仍复用 [ProviderSetupViewModel]。
 *
 * 与旧版 `ProviderWizard` 的契约一致：[onClose] 在取消、返回或完成后调用一次，
 * 调用方据此刷新「已接入」状态；组合销毁时取消未完成的验证，避免静默写入凭据。
 */
@Composable
fun MochiProviderWizard(kind: ProviderWizardKind, onClose: () -> Unit) {
    val factory = remember(kind) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProviderSetupViewModel(kind) as T
        }
    }
    val model: ProviderSetupViewModel = viewModel(
        key = "mochi-provider-setup-${kind.name}",
        factory = factory,
    )
    val state = model.state
    val currentOnClose by rememberUpdatedState(onClose)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun requestClose() {
        model.cancelVerification()
        model.reset(kind)
        currentOnClose()
    }

    fun requestBackOrClose() {
        when (providerBackAction(state)) {
            ProviderBackAction.IGNORE -> Unit
            ProviderBackAction.PREVIOUS -> model.previous()
            ProviderBackAction.CLOSE -> requestClose()
        }
    }

    BackHandler(enabled = true) { requestBackOrClose() }
    DisposableEffect(Unit) { onDispose { model.cancelVerification() } }

    fun primaryAction() {
        when {
            providerFinished(state) -> requestClose()
            providerFinalInputStep(state) -> {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                model.testAndSave()
            }
            else -> model.next()
        }
    }

    val (title, provider) = when (kind) {
        ProviderWizardKind.QWEATHER -> "接入和风天气" to "QWEATHER"
        ProviderWizardKind.CAIYUN -> "接入彩云天气" to "CAIYUN"
        ProviderWizardKind.AMAP -> "接入高德服务" to "AMAP"
        ProviderWizardKind.BAIDU -> "接入百度服务" to "BAIDU"
        ProviderWizardKind.SENIVERSE -> "接入心知天气" to "SENIVERSE"
        ProviderWizardKind.MOJI -> "接入墨迹天气" to "MOJI"
    }
    val labels = providerStepLabels(kind)
    val currentLabel = labels.getOrElse(state.step) { labels.last() }

    MochiPageFrame(
        title = title,
        subtitle = "$provider · PROVIDER LINK",
        onBack = ::requestBackOrClose,
        showSave = false,
        actions = { MochiBarButton("关闭", onClick = ::requestClose) },
    ) {
        item {
            MochiListSection(header = "接入进度", footer = "当前步骤：$currentLabel") {
                MochiListRow(
                    title = "%02d / %02d".format(state.step + 1, state.lastStep + 1),
                    subtitle = currentLabel,
                    leading = {
                        PhosphorIcon(R.drawable.ph_database, null, Modifier.size(22.dp), mochiLeadingIconTint())
                    },
                )
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MochiPageControl(
                        pageCount = state.lastStep + 1,
                        position = { state.step.toFloat() },
                        onPageSelect = {},
                    )
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                ProviderStepContent(state = state, model = model)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MochiButton(
                    onClick = { requestBackOrClose() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.testing,
                    style = MochiButtonStyle.Tonal,
                ) {
                    Text(if (state.step == 0) "取消" else "返回")
                }
                Spacer(Modifier.width(12.dp))
                MochiButton(
                    onClick = { primaryAction() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.testing,
                ) {
                    if (state.testing) {
                        MochiActivityIndicator(size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(providerPrimaryLabel(state))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
