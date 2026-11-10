package com.zhisheng.weather.ui.game

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
internal fun SkyBirdGameDialog(onWeatherDetails: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val store = remember(context) { context.getSharedPreferences("sky_bird", Context.MODE_PRIVATE) }
    val wallet = remember(store) { SkyBirdWallet(store) }
    var haptics by remember { mutableStateOf(store.getBoolean("flap_haptics", true)) }
    var best by remember { mutableIntStateOf(store.getInt("best", 0).coerceAtLeast(0)) }
    var godMode by remember { mutableStateOf(false) }
    var state by remember {
        mutableStateOf(SkyBirdEngine.start(perks = wallet.perks()).copy(godMode = godMode))
    }
    var settledCoins by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!foreground) state = SkyBirdEngine.pause(state)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(state.phase, foreground, inspection) {
        if (inspection || !foreground || state.phase != SkyBirdPhase.FLYING) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive && foreground && state.phase == SkyBirdPhase.FLYING) {
            withFrameNanos { frame ->
                state = SkyBirdEngine.step(state, (frame - previous) / 1_000_000_000f)
                previous = frame
            }
        }
    }
    // 引擎只累计本局收益，钱包在这里落账；重开一局计数归零，不会重复结算。
    LaunchedEffect(state.earnedCoins) {
        val delta = state.earnedCoins - settledCoins
        if (delta > 0) wallet.add(delta)
        if (delta != 0) settledCoins = state.earnedCoins
    }
    LaunchedEffect(state.phase, state.score) {
        if (state.phase == SkyBirdPhase.LANDED && state.score > best) {
            best = state.score
            store.edit().putInt("best", best).apply()
        }
    }
    WeatherOverlayDialog(onClose, maxWidth = 480.dp) { modifier ->
        SkyBirdPanel(state, best, haptics, modifier.fillMaxHeight(),
            onAction = {
                if (foreground) {
                    val next = when (state.phase) {
                        SkyBirdPhase.LANDED -> {
                            settledCoins = 0
                            SkyBirdEngine.flap(SkyBirdEngine.start(state.height,
                                perks = wallet.perks()).copy(godMode = godMode))
                        }
                        SkyBirdPhase.PAUSED -> SkyBirdEngine.resume(state)
                        else -> SkyBirdEngine.flap(state)
                    }
                    if (haptics && state.phase != SkyBirdPhase.PAUSED)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    state = next
                }
            }, onPause = { state = SkyBirdEngine.pause(state) }, onClose = onClose,
            onWeatherDetails = onWeatherDetails, onHaptics = { enabled ->
                haptics = enabled; store.edit().putBoolean("flap_haptics", enabled).apply()
            },
            wallet = wallet,
            onUseItem = { item ->
                if (wallet.consume(item)) state = SkyBirdEngine.activate(state, item)
            },
            onRevive = {
                if (wallet.spend(SkyBirdEngine.reviveCost(state.revives, state.perks.reviveDiscount)))
                    state = SkyBirdEngine.revive(state)
            },
            godMode = godMode,
            onAdminCommand = { command ->
                when (command) {
                    SkyBirdAdminCommand.ADD_COINS_100 -> wallet.add(100)
                    SkyBirdAdminCommand.ADD_COINS_1000 -> wallet.add(1000)
                    SkyBirdAdminCommand.TOGGLE_GOD -> {
                        godMode = !godMode
                        state = SkyBirdEngine.setGodMode(state, godMode)
                    }
                    SkyBirdAdminCommand.FILL_ITEMS -> wallet.fillStock()
                    SkyBirdAdminCommand.MAX_SKILLS -> wallet.maxSkills()
                    SkyBirdAdminCommand.ADD_SCORE -> state = SkyBirdEngine.addScore(state, 10)
                }
            })
    }
}

/** Real production panel also used by native measured-layout checks. */
@Composable
internal fun SkyBirdPanel(state: SkyBirdState, best: Int, haptics: Boolean, modifier: Modifier = Modifier,
    onAction: () -> Unit, onPause: () -> Unit, onClose: () -> Unit, onHaptics: (Boolean) -> Unit,
    onWeatherDetails: () -> Unit = {}, wallet: SkyBirdWallet? = null,
    onUseItem: (SkyBirdItem) -> Unit = {}, onRevive: () -> Unit = {},
    godMode: Boolean = false, onAdminCommand: (SkyBirdAdminCommand) -> Unit = {}) {
    val p = LocalZhishengPalette.current
    val shape = RoundedCornerShape(28.dp)
    var shopOpen by remember { mutableStateOf(false) }
    Column(modifier.clip(shape).background(p.surface).border(1.dp, p.cardBorder, shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("云间小鸟", color = p.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("${SkyBirdJourney.scenery(state.score).title} · 最好 $best", color = p.textSecondary, fontSize = 11.sp)
            }
            if (wallet != null) {
                PhosphorIcon(R.drawable.ph_star, "金币", Modifier.size(15.dp), Color(0xFFC98A22))
                Text("${wallet.coins}", color = Color(0xFFC98A22), fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 2.dp))
                IconButton(onClick = { shopOpen = true },
                    enabled = state.phase != SkyBirdPhase.FLYING,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "云间商店" }) {
                    PhosphorIcon(R.drawable.ph_storefront, "云间商店", Modifier.size(22.dp), p.textSecondary)
                }
            }
            if (state.phase == SkyBirdPhase.FLYING) IconButton(onClick = onPause,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "暂停游戏" }) {
                Canvas(Modifier.size(18.dp)) {
                    drawRoundRect(p.textSecondary, size = Size(size.width * .28f, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                    drawRoundRect(p.textSecondary, topLeft = Offset(size.width * .72f, 0f),
                        size = Size(size.width * .28f, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                PhosphorIcon(R.drawable.ph_x, "关闭游戏", Modifier.size(20.dp), p.textSecondary)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(22.dp))) {
            val compact = maxHeight < 320.dp
            val actionLabel = when (state.phase) {
                SkyBirdPhase.READY -> "开始飞行"
                SkyBirdPhase.PAUSED -> "继续飞行"
                SkyBirdPhase.LANDED -> "再飞一次"
                SkyBirdPhase.FLYING -> "振翅"
            }
            SkyBirdScene(state, p.isLight, Modifier.fillMaxSize()
                .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onAction)
                .semantics { contentDescription = "云间小鸟飞行区域" })
            if (state.phase == SkyBirdPhase.FLYING) Column(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.score}", color = if (p.isLight && SkyBirdJourney.scenery(state.score) != SkyBirdScenery.COSMOS)
                    Color(0xFF244A54) else Color(0xFFECF8FA), fontSize = 38.sp, fontWeight = FontWeight.Light)
                val buffs = buildList {
                    if (state.godMode) add("管理员无敌")
                    if (state.rocket > 0f) add("火箭 ${state.rocket.toInt()}")
                    if (state.autopilot > 0f) add("自动 ${state.autopilot.toInt()}")
                    if (state.shield) add("护盾")
                    if (state.invincible > 0f) add("无敌 ${state.invincible.toInt() + 1}s")
                    if (state.slow > 0f) add("慢速 ${state.slow.toInt() + 1}s")
                    if (state.magnet > 0f) add("吸金 ${state.magnet.toInt() + 1}s")
                    if (state.double > 0f) add("双倍 ${state.double.toInt() + 1}s")
                    if (state.goldRain > 0f) add("金币雨")
                    if (state.lightGravity > 0f) add("顺风")
                    if (state.narrowGap > 0f) add("阵风")
                }
                if (buffs.isNotEmpty()) Text(buffs.joinToString(" · "), Modifier.widthIn(max = 270.dp),
                    color = p.cyan, fontSize = 11.sp, textAlign = TextAlign.Center)
                state.eventNotice?.let { notice ->
                    Column(Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (notice.event.rough) Color(0x33E07C5B) else Color(0x3396D9C8))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${notice.event.title} · ${notice.event.detail}", Modifier.fillMaxWidth(),
                            color = p.text, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                state.celebration?.let { reward ->
                    Column(Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(16.dp))
                        .background(p.surface.copy(alpha = .93f))
                        .semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${reward.threshold} 根 · ${SkyBirdJourney.medalTitle(reward.threshold)}徽章",
                            Modifier.fillMaxWidth(), color = p.text, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Text("抵达${SkyBirdJourney.scenery(reward.threshold).title}", Modifier.fillMaxWidth(),
                            color = p.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            else {
                Column(Modifier.align(Alignment.Center).padding(if (compact) 12.dp else 20.dp).widthIn(max = 290.dp)
                    .heightIn(max = maxHeight).clip(RoundedCornerShape(22.dp))
                    .background(if (p.isLight) Color(0xEEF4FAFC) else Color(0xEE172C3D))
                    .padding(if (compact) 12.dp else 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    Text(when (state.phase) {
                        SkyBirdPhase.READY -> "趁云间放晴"
                        SkyBirdPhase.PAUSED -> "歇一会儿"
                        else -> "这次飞过 ${state.score} 片云"
                    }, Modifier.fillMaxWidth(), color = p.text, fontSize = 23.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text(if (state.phase == SkyBirdPhase.READY) "轻点屏幕振翅\n每10根换景，奖励升级" else
                        if (state.phase == SkyBirdPhase.PAUSED) "准备好再继续" else
                            if (state.score >= 10) "收获${SkyBirdJourney.medalTitle(state.score)}徽章\n已完成 ${state.score / 10} 次换景" else "再试一次，飞得更远一点",
                        Modifier.fillMaxWidth(), color = p.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.cyan,
                        contentColor = if (p.isLight) Color.White else Color(0xFF102634))) { Text(actionLabel) }
                    if (state.phase == SkyBirdPhase.LANDED && wallet != null) {
                        val cost = SkyBirdEngine.reviveCost(state.revives, state.perks.reviveDiscount)
                        Button(onClick = onRevive, enabled = wallet.coins >= cost,
                            modifier = Modifier.heightIn(min = 44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC98A22),
                                contentColor = Color.White)) {
                            Text("花 $cost 金币复活", fontSize = 13.sp)
                        }
                        Text("当前金币 ${wallet.coins}", color = p.textTertiary, fontSize = 10.sp)
                    }
                }
            }
            if (state.phase == SkyBirdPhase.FLYING && wallet != null) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.surface.copy(alpha = .9f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkyBirdItem.entries.forEach { item ->
                        val count = wallet.amount(item)
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (count > 0) p.cyan.copy(alpha = .16f) else Color.Transparent)
                                .clickable(enabled = count > 0) { onUseItem(item) }
                                .semantics { contentDescription = "使用${item.title}" }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PhosphorIcon(skyBirdIcon(item), item.title, Modifier.size(20.dp),
                                if (count > 0) p.cyan else p.textTertiary)
                            Text("×$count", color = p.textSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("振翅震动", Modifier.weight(1f), color = p.textSecondary, fontSize = 13.sp)
                Switch(checked = haptics, onCheckedChange = onHaptics,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "振翅震动" },
                    colors = SwitchDefaults.colors(checkedThumbColor = p.surface, checkedTrackColor = p.cyan))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("捡金币进商店 · 撞云可用护盾", Modifier.weight(1f), color = p.textTertiary, fontSize = 10.sp)
                TextButton(onClick = onWeatherDetails, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("降水详情", color = p.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
    if (shopOpen && wallet != null) SkyBirdShop(wallet, godMode, onAdminCommand) { shopOpen = false }
}

@Composable
internal fun SkyBirdBadge(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = min(size.width, size.height) / 42f
        withTransform({ translate(size.width / 2f, size.height / 2f); scale(scale, scale, Offset.Zero) }) {
            drawSkyBird(0f, 0f, -.12f)
        }
    }
}

@Composable
internal fun SkyBirdScene(state: SkyBirdState, light: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawSkyBirdLandscape(state, light)
        val viewport = SkyBirdViewport.fit(size.width, size.height, state.height)
        withTransform({ translate(viewport.left, viewport.top); scale(viewport.scale, viewport.scale, Offset.Zero) }) {
            val cloudColor = when (SkyBirdJourney.scenery(state.score)) {
                SkyBirdScenery.VALLEY -> if (light) Color(0xFFA6D1CC) else Color(0xFF598D98)
                SkyBirdScenery.PRAIRIE -> if (light) Color(0xFFE0C590) else Color(0xFFB49B76)
                SkyBirdScenery.PEAKS -> if (light) Color(0xFFB6D1E8) else Color(0xFF849FC0)
                SkyBirdScenery.COAST -> if (light) Color(0xFFCEBCCA) else Color(0xFFB69CAF)
                SkyBirdScenery.COSMOS -> Color(0xFF9B9CCB)
            }
            state.gates.forEach { gate ->
                val gap = gate.gap / 2f
                val start = viewport.minY - 20f
                drawCloudPillar(gate.x, start, gate.centerY - gap - start, cloudColor, false)
                drawCloudPillar(gate.x, gate.centerY + gap, viewport.maxY + 20f - gate.centerY - gap,
                    cloudColor, true)
            }
            val ground = state.height - SkyBirdEngine.FLOOR
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .35f), Color.White.copy(alpha = .1f)),
                startY = ground, endY = state.height), Offset(0f, ground), Size(SkyBirdEngine.WIDTH, SkyBirdEngine.FLOOR))
            val cloudBank = Path().apply {
                moveTo(0f, ground)
                repeat(49) { i ->
                    val x = i * SkyBirdEngine.WIDTH / 48f
                    val wave = sin(x * .033f) * 4f + sin(x * .071f) * 2f
                    lineTo(x, ground + 8f + wave)
                }
                lineTo(SkyBirdEngine.WIDTH, state.height); lineTo(0f, state.height); close()
            }
            drawPath(cloudBank, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (light) .58f else .23f),
                Color.White.copy(alpha = .06f)), ground, state.height))
            drawLine(Color.White.copy(alpha = .36f), Offset(0f, ground), Offset(SkyBirdEngine.WIDTH, ground), 1f)
            // The colored body is the collision core; the wing and tail are decorative.
            drawSkyBirdEventWeather(state)
            drawSkyBirdPickups(state)
            drawSkyBirdRewards(state)
            drawSkyBirdTrail(state)
            drawSkyBird(SkyBirdEngine.BIRD_X, state.birdY, (state.velocity / 500f).coerceIn(-.45f, .9f), state.distance)
            drawSkyBirdAura(state)
        }
    }
}

private fun DrawScope.drawCloudPillar(x: Float, y: Float, height: Float, color: Color, bottom: Boolean) {
    if (height <= 0f) return
    val w = SkyBirdEngine.GATE_WIDTH
    drawRoundRect(Brush.horizontalGradient(listOf(color.copy(alpha = .95f), color, color,
        color.copy(red = color.red * .70f, green = color.green * .83f, blue = color.blue * .87f))),
        Offset(x, y), Size(w, height), CornerRadius(12f))
    drawRoundRect(Color(0xFF224C58).copy(alpha = .25f), Offset(x + 1f, y + 1f),
        Size(w - 2f, height - 2f), CornerRadius(11f), style = Stroke(2f))
    // Every highlight and cloud seam stays inside the solid silhouette.
    val edge = if (bottom) y else y + height - 17f
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF3F1D8).copy(alpha = .8f), color)),
        Offset(x + 2f, edge + 2f), Size(w - 4f, 13f), CornerRadius(6f))
    repeat((height / 48f).toInt()) { i ->
        val seamY = y + 26f + i * 48f
        if (seamY + 8f < y + height - 18f) {
            val seam = Path().apply {
                moveTo(x + 10f, seamY + 4f)
                cubicTo(x + 18f, seamY - 5f, x + 24f, seamY - 5f, x + 31f, seamY + 2f)
                cubicTo(x + 37f, seamY - 2f, x + 44f, seamY + 1f, x + 50f, seamY + 5f)
            }
            drawPath(seam, Color.White.copy(alpha = .23f), style = Stroke(2f))
        }
    }
    drawRoundRect(Color.White.copy(alpha = .33f), Offset(x + 7f, y + 18f),
        Size(4f, (height - 36f).coerceAtLeast(0f)), CornerRadius(2f))
}

private fun DrawScope.drawSkyBird(x: Float, y: Float, tilt: Float, distance: Float = 0f) {
    withTransform({ translate(x, y); rotate(tilt * 57.3f, Offset.Zero) }) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A5).copy(alpha = .45f), Color.Transparent), Offset.Zero, 28f), 28f, Offset.Zero)
        val tail = Path().apply { moveTo(-9f, 1f); lineTo(-21f, -4f); lineTo(-17f, 7f); close() }
        drawPath(tail, Color(0xFF27777F))
        drawCircle(Color(0xFF318A90), 10f, Offset.Zero)
        drawOval(Color(0xFFF4E9C4), Offset(-5f, 1f), Size(14f, 9f))
        val wing = sin(distance * .09f) * 3f
        drawOval(Color(0xFF73B8B2), Offset(-12f, -2f + wing), Size(13f, 9f))
        val beak = Path().apply { moveTo(9f, -1f); lineTo(17f, 2f); lineTo(9f, 5f); close() }
        drawPath(beak, Color(0xFFE9B775))
        drawCircle(Color.White, 3.3f, Offset(5f, -3f))
        drawCircle(Color(0xFF173B46), 1.7f, Offset(6f, -3f))
        drawCircle(Color(0xFFE8AF83).copy(alpha = .75f), 2f, Offset(7f, 3f))
    }
}

/** 金币、消耗品库存与永久升级；无存档时只走内存，方便布局检查直接调用面板。 */
internal class SkyBirdWallet(private val store: android.content.SharedPreferences?) {
    var coins by mutableIntStateOf(store?.getInt("coins", 0)?.coerceAtLeast(0) ?: 0)
        private set
    private val stock = mutableStateMapOf<SkyBirdItem, Int>().apply {
        SkyBirdItem.entries.forEach {
            put(it, store?.getInt("stock_${it.name}", 0)?.coerceAtLeast(0) ?: 0)
        }
    }
    private val levels = mutableStateMapOf<SkyBirdUpgrade, Int>().apply {
        SkyBirdUpgrade.entries.forEach {
            put(it, (store?.getInt("upgrade_${it.name}", 0)?.coerceAtLeast(0) ?: 0)
                .coerceAtMost(it.maxLevel))
        }
    }

    fun amount(item: SkyBirdItem) = stock[item] ?: 0
    fun level(upgrade: SkyBirdUpgrade) = levels[upgrade] ?: 0
    fun price(upgrade: SkyBirdUpgrade) = upgrade.price + level(upgrade) * 40
    fun gapBonus() = level(SkyBirdUpgrade.WIDE_WINGS) * 6f
    fun coinBoost() = 1f + level(SkyBirdUpgrade.GOLD_FEATHER) * .25f
    fun perks() = SkyBirdPerks(
        gapBonus = gapBonus(),
        coinBoost = coinBoost(),
        luck = level(SkyBirdUpgrade.LUCKY_CHILD) * .22f,
        rocketDistance = level(SkyBirdUpgrade.ROCKET_FUEL) * 220f,
        autopilotDistance = level(SkyBirdUpgrade.NAV_CHIP) * 260f,
        reviveDiscount = level(SkyBirdUpgrade.PHOENIX_HEART) * 15,
    )

    fun add(amount: Int) {
        if (amount <= 0) return
        coins += amount
        store?.edit()?.putInt("coins", coins)?.apply()
    }
    fun spend(amount: Int): Boolean {
        if (coins < amount) return false
        coins -= amount
        store?.edit()?.putInt("coins", coins)?.apply()
        return true
    }
    fun buy(item: SkyBirdItem): Boolean {
        if (!spend(item.price)) return false
        val next = amount(item) + 1
        stock[item] = next
        store?.edit()?.putInt("stock_${item.name}", next)?.apply()
        return true
    }
    fun buy(upgrade: SkyBirdUpgrade): Boolean {
        val current = level(upgrade)
        if (current >= upgrade.maxLevel || !spend(price(upgrade))) return false
        levels[upgrade] = current + 1
        store?.edit()?.putInt("upgrade_${upgrade.name}", current + 1)?.apply()
        return true
    }
    fun consume(item: SkyBirdItem): Boolean {
        val remaining = amount(item)
        if (remaining <= 0) return false
        stock[item] = remaining - 1
        store?.edit()?.putInt("stock_${item.name}", remaining - 1)?.apply()
        return true
    }
    fun fillStock() {
        val edit = store?.edit()
        SkyBirdItem.entries.forEach { item ->
            val next = amount(item) + 5
            stock[item] = next
            edit?.putInt("stock_${item.name}", next)
        }
        edit?.apply()
    }
    fun maxSkills() {
        val edit = store?.edit()
        SkyBirdUpgrade.entries.forEach { upgrade ->
            levels[upgrade] = upgrade.maxLevel
            edit?.putInt("upgrade_${upgrade.name}", upgrade.maxLevel)
        }
        edit?.apply()
    }
}

private const val SKY_BIRD_ADMIN_PASSWORD = "admin123456"
private enum class SkyBirdAdminStage { HIDDEN, PASSWORD, PANEL }

/** 商店：消耗品进货、永久技能；副标题 5 连击 + 密码才进入管理员面板。 */
@Composable
private fun SkyBirdShop(wallet: SkyBirdWallet, godMode: Boolean,
    onAdminCommand: (SkyBirdAdminCommand) -> Unit, onDismiss: () -> Unit) {
    val p = LocalZhishengPalette.current
    val container = if (p.isLight) Color.White else p.card
    var message by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(SkyBirdAdminStage.HIDDEN) }
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    when (stage) {
        SkyBirdAdminStage.HIDDEN -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = container,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhosphorIcon(R.drawable.ph_storefront, "云间商店", Modifier.size(20.dp), p.cyan)
                    Text("云间商店", Modifier.weight(1f).padding(start = 8.dp), color = p.text,
                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    PhosphorIcon(R.drawable.ph_star, "金币", Modifier.size(15.dp), Color(0xFFC98A22))
                    Text("${wallet.coins}", color = Color(0xFFC98A22), fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("飞行途中捡到的金币与过云奖励都会存进这里。",
                        Modifier.fillMaxWidth().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            taps = if (now - lastTap < 1200L) taps + 1 else 1
                            lastTap = now
                            if (taps >= 5) {
                                taps = 0
                                password = ""
                                passwordError = false
                                stage = SkyBirdAdminStage.PASSWORD
                            }
                        },
                        color = p.textSecondary, fontSize = 12.sp)
                    Text("道具 · 飞行中随时使用", color = p.text, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium)
                    SkyBirdItem.entries.forEach { item ->
                        SkyBirdShopRow(skyBirdIcon(item), item.title, item.description,
                            "持有 ${wallet.amount(item)}", "${item.price} 金币",
                            wallet.coins >= item.price) {
                            message = if (wallet.buy(item)) "已买入${item.title}" else "金币不够"
                        }
                    }
                    HorizontalDivider(color = p.cardBorder)
                    Text("永久技能 · 每局自动生效", color = p.text, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium)
                    SkyBirdUpgrade.entries.forEach { upgrade ->
                        val lv = wallet.level(upgrade)
                        val maxed = lv >= upgrade.maxLevel
                        SkyBirdShopRow(skyBirdIcon(upgrade), "${upgrade.title} Lv.$lv/${upgrade.maxLevel}",
                            upgrade.description, if (maxed) "已满级" else "每级 ${wallet.price(upgrade)} 金币",
                            if (maxed) "—" else "升级", !maxed && wallet.coins >= wallet.price(upgrade)) {
                            message = if (maxed) "已满级" else if (wallet.buy(upgrade)) "升级成功" else "金币不够"
                        }
                    }
                    if (message.isNotEmpty()) Text(message, color = p.cyan, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = p.cyan) } },
        )
        SkyBirdAdminStage.PASSWORD -> AlertDialog(
            onDismissRequest = { stage = SkyBirdAdminStage.HIDDEN },
            containerColor = container,
            title = { Text("管理员验证", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入管理员密码", color = p.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; passwordError = false },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "管理员密码" },
                    )
                    if (passwordError) Text("密码不对，再试一次", color = Color(0xFFD2603C), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (password == SKY_BIRD_ADMIN_PASSWORD) {
                        stage = SkyBirdAdminStage.PANEL
                        password = ""
                    } else passwordError = true
                }) { Text("进入", color = p.cyan) }
            },
            dismissButton = {
                TextButton(onClick = { stage = SkyBirdAdminStage.HIDDEN }) { Text("取消", color = p.textSecondary) }
            },
        )
        SkyBirdAdminStage.PANEL -> AlertDialog(
            onDismissRequest = { stage = SkyBirdAdminStage.HIDDEN },
            containerColor = container,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhosphorIcon(R.drawable.ph_gear, "管理员", Modifier.size(20.dp), p.cyan)
                    Text("管理员 · 云间小鸟", Modifier.padding(start = 8.dp), color = p.text,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前金币 ${wallet.coins}", color = Color(0xFFC98A22), fontSize = 13.sp)
                    Text("以下操作只影响本机的云间小鸟存档。", color = p.textSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkyBirdAdminButton("+100 金币", Modifier.weight(1f)) {
                            onAdminCommand(SkyBirdAdminCommand.ADD_COINS_100); message = "已增加 100 金币"
                        }
                        SkyBirdAdminButton("+1000 金币", Modifier.weight(1f)) {
                            onAdminCommand(SkyBirdAdminCommand.ADD_COINS_1000); message = "已增加 1000 金币"
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkyBirdAdminButton(if (godMode) "关闭无敌" else "开启无敌", Modifier.weight(1f)) {
                            onAdminCommand(SkyBirdAdminCommand.TOGGLE_GOD)
                            message = if (godMode) "已关闭无敌" else "已开启无敌"
                        }
                        SkyBirdAdminButton("+10 分", Modifier.weight(1f)) {
                            onAdminCommand(SkyBirdAdminCommand.ADD_SCORE); message = "已增加 10 分"
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkyBirdAdminButton("补满道具", Modifier.weight(1f)) {
                            onAdminCommand(SkyBirdAdminCommand.FILL_ITEMS); message = "道具已补齐"
                        }
                        SkyBirdAdminButton("技能满级", Modifier.weight(1f)) {
                            onAdminCommand(SkyBirdAdminCommand.MAX_SKILLS); message = "技能已满级"
                        }
                    }
                    Text("无敌：${if (godMode) "开启" else "关闭"} · 下一局自动继承",
                        color = p.textTertiary, fontSize = 11.sp)
                    if (message.isNotEmpty()) Text(message, color = p.cyan, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = p.cyan) } },
            dismissButton = {
                TextButton(onClick = { stage = SkyBirdAdminStage.HIDDEN }) { Text("返回商店", color = p.textSecondary) }
            },
        )
    }
}

@Composable
private fun SkyBirdAdminButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalZhishengPalette.current
    Button(onClick = onClick, modifier = modifier.heightIn(min = 44.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.cyan,
            contentColor = if (p.isLight) Color.White else Color(0xFF102634))) {
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SkyBirdShopRow(iconRes: Int, title: String, detail: String, trailing: String,
    price: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalZhishengPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(p.card.copy(alpha = .6f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(p.cyan.copy(alpha = .2f)),
            contentAlignment = Alignment.Center) {
            PhosphorIcon(iconRes, title, Modifier.size(20.dp), p.cyan)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = p.textSecondary, fontSize = 10.sp)
            Text(trailing, color = p.textTertiary, fontSize = 10.sp)
        }
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.cyan,
                contentColor = if (p.isLight) Color.White else Color(0xFF102634))) {
            Text(price, fontSize = 11.sp)
        }
    }
}

@DrawableRes
private fun skyBirdIcon(item: SkyBirdItem): Int = when (item) {
    SkyBirdItem.SHIELD -> R.drawable.ph_shield
    SkyBirdItem.SLOW -> R.drawable.ph_hourglass
    SkyBirdItem.MAGNET -> R.drawable.ph_magnet
    SkyBirdItem.DOUBLE -> R.drawable.ph_sparkle
    SkyBirdItem.ROCKET -> R.drawable.ph_rocket_launch
    SkyBirdItem.AUTOPILOT -> R.drawable.ph_robot
}

@DrawableRes
private fun skyBirdIcon(upgrade: SkyBirdUpgrade): Int = when (upgrade) {
    SkyBirdUpgrade.WIDE_WINGS -> R.drawable.ph_wind
    SkyBirdUpgrade.GOLD_FEATHER -> R.drawable.ph_leaf
    SkyBirdUpgrade.LUCKY_CHILD -> R.drawable.ph_clover
    SkyBirdUpgrade.ROCKET_FUEL -> R.drawable.ph_gauge
    SkyBirdUpgrade.NAV_CHIP -> R.drawable.ph_navigation_arrow
    SkyBirdUpgrade.PHOENIX_HEART -> R.drawable.ph_arrow_clockwise
}

/** 场景里随机刷出的金币与能力道具。 */
private fun DrawScope.drawSkyBirdPickups(state: SkyBirdState) {
    state.pickups.forEach { pickup ->
        if (pickup.collected) return@forEach
        val c = Offset(pickup.x, pickup.y + sin(state.distance * .02f + pickup.id * 1.7f) * 3f)
        when (pickup.type) {
            SkyBirdPickupType.COIN -> {
                drawCircle(Color(0xFFFFD86A), 11f, c)
                drawCircle(Color(0xFFB57C1C), 11f, c, style = Stroke(2f))
                drawCircle(Color(0xFFFFF4C9), 4f, c)
            }
            SkyBirdPickupType.SHIELD -> {
                drawCircle(Color(0xFF7FD9F0).copy(alpha = .28f), 13f, c)
                drawCircle(Color(0xFF8FE6FF), 13f, c, style = Stroke(2.5f))
            }
            SkyBirdPickupType.SLOW -> {
                drawCircle(Color(0xFFB79BF0).copy(alpha = .28f), 13f, c)
                drawCircle(Color(0xFFD9C7FF), 13f, c, style = Stroke(2.5f))
                drawLine(Color(0xFFD9C7FF), c, Offset(c.x + 5f, c.y - 6f), 2f)
            }
            SkyBirdPickupType.MAGNET -> drawArc(Color(0xFFFF8E7A), 200f, 140f, false,
                topLeft = Offset(c.x - 11f, c.y - 11f), size = Size(22f, 22f), style = Stroke(4f))
            SkyBirdPickupType.DOUBLE -> {
                drawCircle(Color(0xFFFFB759).copy(alpha = .28f), 13f, c)
                drawCircle(Color(0xFFFFD08A), 3.5f, Offset(c.x - 5f, c.y - 2f))
                drawCircle(Color(0xFFFFD08A), 3.5f, Offset(c.x + 5f, c.y + 2f))
            }
            SkyBirdPickupType.ROCKET -> {
                drawCircle(Color(0xFFFF9B59).copy(alpha = .28f), 13f, c)
                val flame = Path().apply {
                    moveTo(c.x, c.y - 9f); lineTo(c.x + 6f, c.y + 7f)
                    lineTo(c.x, c.y + 4f); lineTo(c.x - 6f, c.y + 7f); close()
                }
                drawPath(flame, Color(0xFFFFD08A))
            }
            SkyBirdPickupType.AUTOPILOT -> {
                drawCircle(Color(0xFF8AD2F0).copy(alpha = .28f), 13f, c)
                drawRect(Color(0xFFBFE9FF), Offset(c.x - 7f, c.y - 6f), Size(14f, 12f))
                drawCircle(Color(0xFF2A5C6E), 2f, Offset(c.x - 3f, c.y))
                drawCircle(Color(0xFF2A5C6E), 2f, Offset(c.x + 3f, c.y))
            }
        }
    }
}

/** 金币雨、顺风与阵风的场景提示，纯装饰不参与物理。 */
private fun DrawScope.drawSkyBirdEventWeather(state: SkyBirdState) {
    if (state.goldRain > 0f) {
        repeat(9) { i ->
            val x = (i * 41f + state.distance * .35f) % SkyBirdEngine.WIDTH
            val y = (i * 53f + state.distance * .8f) % (state.height - SkyBirdEngine.FLOOR)
            drawCircle(Color(0xFFFFD86A).copy(alpha = .55f), 3.5f, Offset(x + 3f, y))
        }
    }
    if (state.lightGravity > 0f || state.narrowGap > 0f) {
        val color = if (state.narrowGap > 0f) Color(0xFFE07C5B) else Color(0xFF9CE6D8)
        repeat(6) { i ->
            val y = 40f + i * (state.height - 80f) / 6f
            val x = (state.distance * .6f + i * 47f) % SkyBirdEngine.WIDTH
            drawLine(color.copy(alpha = .35f), Offset(x, y), Offset(x + 24f, y - 7f), 2f)
        }
    }
}

/** 火箭冲刺时的火焰尾迹。 */
private fun DrawScope.drawSkyBirdTrail(state: SkyBirdState) {
    if (state.rocket <= 0f) return
    repeat(5) { i ->
        val x = SkyBirdEngine.BIRD_X - 14f - i * 9f
        drawCircle(Color(0xFFFFB347).copy(alpha = .7f - i * .12f), 7f - i * 1.1f,
            Offset(x, state.birdY + sin(state.distance * .12f + i) * 3f))
    }
}

/** 护盾、无敌、火箭与自动导航时小鸟外围的光环。 */
private fun DrawScope.drawSkyBirdAura(state: SkyBirdState) {
    if (!state.shield && state.invincible <= 0f && state.rocket <= 0f &&
        state.autopilot <= 0f && !state.godMode) return
    val c = Offset(SkyBirdEngine.BIRD_X, state.birdY)
    val color = when {
        state.rocket > 0f -> Color(0xFFFFC36B)
        state.godMode -> Color(0xFFF3E19A)
        state.autopilot > 0f -> Color(0xFF9BD9FF)
        else -> Color(0xFF9CEBFF)
    }
    val radius = SkyBirdEngine.RADIUS + if (state.rocket > 0f) 15f else 11f
    drawCircle(color.copy(alpha = .16f), radius, c)
    drawCircle(color.copy(alpha = .8f), radius, c, style = Stroke(2f))
}
