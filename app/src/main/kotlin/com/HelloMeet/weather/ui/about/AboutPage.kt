// 迁移自 mochi-compose demo 的关于页（demo 的图片 logo avatar_feice 不迁移，开发者头像改用文字首字母占位）。
// 顶部品牌图标已重设为天气主题的太阳 + 云 + 小雨，戳按 / 长按蓄力 / 彩蛋飘带逻辑保留。

package com.HelloMeet.weather.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiAvatarButton
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiToastState
import com.HelloMeet.weather.ui.MochiTransitionConfig
import com.HelloMeet.weather.ui.mochiSettingsSourceKey
import com.HelloMeet.weather.ui.about.common.AppIcon
import com.HelloMeet.weather.ui.about.common.DemoIcon
import com.HelloMeet.weather.ui.about.common.IconColors
import com.HelloMeet.weather.ui.about.common.Icons
import com.HelloMeet.weather.ui.about.common.Tag
import com.HelloMeet.weather.ui.about.common.Tone
import com.HelloMeet.weather.ui.about.common.demoItem
import com.HelloMeet.weather.ui.about.common.tile
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListDisclosure
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.emphasized
import ui.feice.mochi.utils.rememberMochiHaptics

private const val GitHub = "https://github.com/Vxiaolong666/HelloMeetWeather"
private const val Afdian = "https://afdian.com/a/hellowather"
private const val AfdianCreator = "https://afdian.com/a/hellowather"

/** 长按天气图标多久进入彩蛋 */
private const val ChargeMillis = 800

/**
 * 关于页：作者、赞助者支持、鸣谢。顶部的天气图标能戳（云被按扁、太阳让开、雨滴散开），
 * 长按蓄满进入彩蛋（[RibbonEggOverlay]：玻璃飘带、折射和色散）；赞助者支持只写入群步骤，群号由爱发电在赞助后私信发出。
 */
@Composable
internal fun AboutPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenNotices: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onOpenCurrentNotes: () -> Unit = {},
    onOpenChangelog: () -> Unit = {},
) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val toast = rememberMochiToastState()
    val haptics = rememberMochiHaptics()
    val scope = rememberCoroutineScope()
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val open: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }.onFailure {
            // 没有能打开链接的应用：复制下来，让用户自己粘贴
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("遇见天气", url))
            toast.show("已复制链接", subtitle = url)
        }
    }

    val poke = remember { Animatable(0f) }
    val charge = remember { Animatable(0f) }
    var taps by remember { mutableIntStateOf(0) }
    var egg by remember { mutableStateOf<RibbonEggState?>(null) }
    var root by remember { mutableStateOf(Offset.Zero) }
    var mark by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun startEgg() {
        val lc = mark ?: return
        val center = lc.positionInRoot() + Offset(lc.size.width / 2f, lc.size.height / 2f) - root
        egg = RibbonEggState(center)
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { root = it.positionInRoot() }) {
        var more by remember { mutableStateOf(false) }
        MochiLazyCompactTitleScaffold(
            title = "关于",
            // 彩蛋：整页透过一层流动的玻璃看，效果直接作用在页面图层上
            modifier = Modifier.ribbonEggEffect(egg),
            navigationIcon = { MochiBackButton(onClick = onBack) },
        ) {
            // —— 天气图标 + 名字 ——
            demoItem {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(136.dp)
                            .onGloballyPositioned { mark = it }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    val job = scope.launch {
                                        coroutineScope {
                                            // 蓄力：每过 1/4 轻震一下，图标越鼓越抖
                                            val ticker = launch {
                                                var step = 0
                                                snapshotFlow { (charge.value * 4).toInt() }.collect {
                                                    if (it > step) {
                                                        step = it
                                                        haptics.tick()
                                                    }
                                                }
                                            }
                                            charge.animateTo(1f, tween(ChargeMillis, easing = LinearEasing))
                                            ticker.cancel()
                                        }
                                        haptics.confirm()
                                        startEgg()
                                        // 图标弹回原样，飘带从它这里抽出来
                                        scope.launch { charge.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 240f)) }
                                    }
                                    val up = waitForUpOrCancellation()
                                    if (job.isActive) {
                                        job.cancel()
                                        val tap = up != null && charge.value < 0.3f
                                        scope.launch { charge.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
                                        if (tap) {
                                            haptics.tick()
                                            scope.launch {
                                                poke.animateTo(1f, tween(70))
                                                poke.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 480f))
                                            }
                                            if (++taps == 5) toast.show("试试长按天气图标？", subtitle = "按住别松手")
                                        }
                                    }
                                }
                            },
                    ) {
                        WeatherMark(
                            Modifier.fillMaxSize(),
                            poke = { poke.value },
                            swell = { charge.value },
                            tremble = { charge.value.let { c -> 2.5f * c * sin(c * 70f) } },
                        )
                    }
                    Text("遇见天气", style = ts.largeTitle, modifier = Modifier.padding(top = 4.dp))
                    Text("HELLO WEATHER", style = ts.titleMedium.emphasized(), color = WeatherMarkColors.Ink)
                    Text(
                        "看得见天气，也看得见填空 · 遇见每个好天气",
                        style = ts.subhead.copy(textAlign = TextAlign.Center),
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Tag("v$version", cs.surfaceContainerHigh, cs.onSurfaceVariant, Modifier.padding(top = 12.dp))
                }
            }
            // —— 作者 ——
            demoItem {
                MochiListSection(
                    header = "开发者",
                    footer = "免费开源、无广告、简单优雅",
                ) {
                    MochiListRow(
                        "开发者信息",
                        subtitle = "作者Vxiaolong666、727w7w8w",
                        leading = {
                            MochiAvatarButton(onClick = { open(GitHub) }, size = 48.dp) {
                                Text("飞", style = MochiTheme.textStyles.titleSmall.emphasized(), color = cs.onSecondaryContainer)
                            }
                        },
                    )
                    LinkRow("GitHub", "Vxiaolong666", Icons.GITHUB, IconColors(Color(0xFF24292F), Color.White)) { open(GitHub) }
                    LinkRow("爱发电", "yyyolala", Icons.HEART, IconColors(Color(0xFF946CE6), Color.White)) { open(AfdianCreator) }
                }
            }
            // —— 软件更新：检查新版本与查看更新日志，直接在设置内弹层 ——
            demoItem {
                MochiListSection(header = "软件更新") {
                    MochiListRow(
                        "检查更新",
                        subtitle = "从 GitHub 与 Gitee 获取最新版本",
                        onClick = onCheckUpdate,
                        chevron = true,
                        leading = {
                            AppIcon(
                                Icons.UPDATE,
                                Tone.Secondary.tile(),
                                MochiListDefaults.LeadingSize,
                                corner = MochiListDefaults.LeadingCorner,
                            )
                        },
                    )
                    MochiListRow(
                        "当前版本更新",
                        subtitle = "v$version 的更新内容",
                        onClick = onOpenCurrentNotes,
                        chevron = true,
                        leading = {
                            AppIcon(
                                Icons.NOTE,
                                Tone.Primary.tile(),
                                MochiListDefaults.LeadingSize,
                                corner = MochiListDefaults.LeadingCorner,
                            )
                        },
                    )
                    MochiListRow(
                        "历史更新日志",
                        subtitle = "按版本查看全部历史更新",
                        onClick = onOpenChangelog,
                        chevron = true,
                        leading = {
                            AppIcon(
                                Icons.CHANGELOG,
                                Tone.Tertiary.tile(),
                                MochiListDefaults.LeadingSize,
                                corner = MochiListDefaults.LeadingCorner,
                            )
                        },
                    )
                }
            }
            // —— 公告：云端通知与版本提醒，独立页面 ——
            demoItem {
                MochiListSection(header = "公告") {
                    MochiListRow(
                        "软件公告",
                        subtitle = "版本通知与使用提醒",
                        onClick = onOpenNotices,
                        chevron = true,
                        modifier = Modifier.mochiNavSource(
                            mochiSettingsSourceKey("settings.notice"),
                            MochiListDefaults.SectionCorner,
                            if (MochiTransitionConfig.sourceHighlight) {
                                MochiTheme.colorScheme.primaryContainer
                            } else {
                                MochiDefaults.groupedContainer()
                            },
                        ),
                        leading = {
                            AppIcon(
                                Icons.BELL,
                                Tone.Primary.tile(),
                                MochiListDefaults.LeadingSize,
                                corner = MochiListDefaults.LeadingCorner,
                            )
                        },
                    )
                }
            }
            // —— 赞助者支持：只写步骤，群号在赞助后由爱发电私信发出 ——
            demoItem {
                MochiListSection(header = "加入赞助者支持", footer = "赞助者专属交流群，申请入群时会核对订单号。") {
                    StepRow(1, "在爱发电赞助任意一档", onClick = { open(AfdianCreator) })
                    StepRow(2, "赞助成功后，私信里会收到群号")
                    StepRow(3, "申请入群时填写爱发电订单号", subtitle = "在爱发电「我的订单」里可以看到")
                }
            }
            // —— 鸣谢：与 README「致谢」保持一致，列出本项目实际用到的上游项目 ——
            demoItem {
                MochiListSection(
                    header = "鸣谢",
                    footer = "天气数据版权归相关提供方，数据仅供参考。",
                ) {
                    LinkRow(
                        "枳生天气",
                        "MIT",
                        Icons.GITHUB,
                        Tone.Primary.tile(),
                        subtitle = "本项目的基础：界面设计与桌面小组件",
                    ) { open("https://github.com/zhishengplus/ZhishengWeather") }
                    LinkRow(
                        "mochi-compose",
                        "Apache-2.0",
                        Icons.GITHUB,
                        Tone.Tertiary.tile(),
                        subtitle = "项目组件库，软、弹、跟手的交互体验",
                    ) { open("https://github.com/feice11/mochi-compose") }
                    MochiListDisclosure(
                        "其他开源项目",
                        expanded = more,
                        onExpandedChange = { more = it },
                        leading = {
                            AppIcon(
                                Icons.GIFT,
                                Tone.Neutral.tile(),
                                MochiListDefaults.LeadingSize,
                                corner = MochiListDefaults.LeadingCorner,
                            )
                        },
                    ) {
                        MochiListRow("Capsule", subtitle = "Kyant0 · Mochi 使用的形状库", chevron = true, onClick = {
                            open("https://github.com/Kyant0/Capsule")
                        })
                        MochiListRow("Backdrop", subtitle = "Kyant0 · Liquid Glass 背景玻璃效果", chevron = true, onClick = {
                            open("https://github.com/Kyant0/Backdrop")
                        })
                        MochiListRow("MapLibre Native Android", subtitle = "地图渲染", chevron = true, onClick = {
                            open("https://github.com/maplibre/maplibre-native-android")
                        })
                        MochiListRow("Ionicons", subtitle = "MIT · Mochi 图标库所用图标", chevron = true, onClick = {
                            open("https://ionic.io/ionicons")
                        })
                        MochiListRow("commons-suncalc", subtitle = "日月升落计算", chevron = true, onClick = {
                            open("https://github.com/shred/commons-suncalc")
                        })
                    }
                }
            }
            demoItem {
                Text(
                    "基于枳生天气，接入mochi-compose组件库二次开发",
                    style = ts.footnote.copy(textAlign = TextAlign.Center),
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        egg?.let { state -> RibbonEggOverlay(state, onClose = { egg = null }) }
        MochiToastHost(toast)
    }
}

@Composable
private fun LinkRow(
    title: String,
    value: String?,
    icon: DemoIcon,
    colors: IconColors,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    MochiListRow(
        title,
        onClick = onClick,
        value = value,
        subtitle = subtitle,
        chevron = true,
        leading = { AppIcon(icon, colors, MochiListDefaults.LeadingSize, corner = MochiListDefaults.LeadingCorner) },
    )
}

@Composable
private fun StepRow(n: Int, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    val cs = MochiTheme.colorScheme
    MochiListRow(
        title,
        subtitle = subtitle,
        onClick = onClick,
        chevron = onClick != null,
        leading = {
            Box(
                Modifier.size(MochiListDefaults.LeadingSize).background(cs.secondaryContainer, MochiCapsule),
                contentAlignment = Alignment.Center,
            ) { Text("$n", style = MochiTheme.textStyles.titleSmall.emphasized(), color = cs.onSecondaryContainer) }
        },
    )
}
