package com.HelloMeet.weather.ui.game

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

internal enum class SkyBirdPhase { READY, FLYING, PAUSED, LANDED }
internal data class CloudGate(val id: Int, val x: Float, val centerY: Float,
    val passed: Boolean = false, val gap: Float = 122f)

/** 商店里的消耗品：买进库存，飞行中主动使用。 */
internal enum class SkyBirdItem(val title: String, val price: Int, val description: String) {
    SHIELD("护盾泡泡", 30, "抵挡一次碰撞，安全穿过云柱"),
    SLOW("时光沙漏", 25, "8 秒内放慢飞行与下坠"),
    MAGNET("吸金磁场", 20, "8 秒内自动吸取附近金币"),
    DOUBLE("双倍星光", 35, "10 秒内得分与金币翻倍"),
    ROCKET("冲天火箭", 60, "冲刺一段距离，无敌并自动穿云"),
    AUTOPILOT("自动导航", 70, "自动驾驶一段距离，免手动振翅"),
}

/** 永久技能：一次购买长期生效，最多 3 级。 */
internal enum class SkyBirdUpgrade(val title: String, val price: Int, val maxLevel: Int, val description: String) {
    WIDE_WINGS("宽翼训练", 80, 3, "每级永久加宽云缝 6"),
    GOLD_FEATHER("金羽加持", 60, 3, "每级永久提升金币收益 25%"),
    LUCKY_CHILD("幸运之子", 120, 3, "每级提升稀有掉落与好事概率"),
    ROCKET_FUEL("火箭燃料", 70, 3, "每级延长火箭冲刺 220"),
    NAV_CHIP("导航芯片", 70, 3, "每级延长自动导航 260"),
    PHOENIX_HEART("涅槃之心", 90, 3, "每级降低复活费用 15 金币"),
}

/** 开局时一次性注入引擎的技能汇总，飞行途中不再读取存档。 */
internal data class SkyBirdPerks(
    val gapBonus: Float = 0f,
    val coinBoost: Float = 1f,
    val luck: Float = 0f,
    val rocketDistance: Float = 0f,
    val autopilotDistance: Float = 0f,
    val reviveDiscount: Int = 0,
)

internal enum class SkyBirdPickupType { COIN, SHIELD, SLOW, MAGNET, DOUBLE, ROCKET, AUTOPILOT }

/** 随机场景道具：随云柱一起生成，位置由本局种子决定，可复现。 */
internal data class SkyBirdPickup(
    val id: Int,
    val x: Float,
    val y: Float,
    val type: SkyBirdPickupType,
    val collected: Boolean = false,
)

/** 飞行途中随机触发的世界事件；luck 越高，坏事越少、好事越频繁。 */
internal enum class SkyBirdEvent(val title: String, val detail: String, val rough: Boolean = false) {
    GOLD_RAIN("金币雨", "金币双倍，还有金币飘落"),
    TAILWIND("顺风而行", "重力减弱，身轻如羽"),
    CLOUD_GIFT("云中礼物", "捡到一袋金币"),
    STAR_SHOWER("流星雨", "额外获得 3 分"),
    LUCKY_CLOVER("幸运四叶草", "护盾与吸金一起加持"),
    COIN_FLURRY("金币旋风", "自动吸附附近金币"),
    WIND_GUST("调皮阵风", "云缝暂时收窄，小心", true),
}
internal data class SkyBirdEventNotice(val event: SkyBirdEvent, val age: Float = 0f)

internal enum class SkyBirdAdminCommand {
    ADD_COINS_100, ADD_COINS_1000, TOGGLE_GOD, FILL_ITEMS, MAX_SKILLS, ADD_SCORE,
}

internal data class SkyBirdState(
    val phase: SkyBirdPhase = SkyBirdPhase.READY,
    val height: Float = 520f,
    val birdY: Float = height * .46f,
    val velocity: Float = 0f,
    val distance: Float = 0f,
    val score: Int = 0,
    val gates: List<CloudGate> = listOf(CloudGate(0, 440f, height * .46f)),
    val randomState: Int = 1,
    val celebration: SkyBirdCelebration? = null,
    val pickups: List<SkyBirdPickup> = emptyList(),
    val earnedCoins: Int = 0,
    val shield: Boolean = false,
    val invincible: Float = 0f,
    val slow: Float = 0f,
    val magnet: Float = 0f,
    val double: Float = 0f,
    val revives: Int = 0,
    val perks: SkyBirdPerks = SkyBirdPerks(),
    val rocket: Float = 0f,
    val autopilot: Float = 0f,
    val goldRain: Float = 0f,
    val lightGravity: Float = 0f,
    val narrowGap: Float = 0f,
    val eventCountdown: Float = 620f,
    val eventSeed: Int = 1,
    val eventNotice: SkyBirdEventNotice? = null,
    val godMode: Boolean = false,
)

/** Park-Miller：随机性只由生成器消费，绘制和帧率永远不参与。 */
private class SkyBirdRandom(seed: Int) {
    var value = (seed.toLong() and 0x7fffffff).coerceAtLeast(1L)
        private set
    fun sample(): Float {
        value = value * 48271L % 2147483647L
        return (value.toDouble() / 2147483647.0).toFloat()
    }
    val seed: Int get() = value.toInt()
}

/** Each run has a fresh seed; replayable physics and generation do not depend on refresh rate. */
internal object SkyBirdEngine {
    const val WIDTH = 360f
    const val BIRD_X = 92f
    const val RADIUS = 10f
    const val GATE_WIDTH = 60f
    const val FLOOR = 32f
    const val GRAVITY = 1100f
    const val FLAP = -330f
    const val MAX_FALL = 420f
    const val ROCKET_RANGE = 620f
    const val AUTOPILOT_RANGE = 620f
    const val EVENT_NOTICE_SECONDS = 2.6f

    fun gap(height: Float, score: Int = 0, bonus: Float = 0f) =
        (((height * .235f).coerceIn(88f, 122f) - min(score * .7f, 16f)).coerceAtLeast(80f) + bonus)
            .coerceAtMost(170f)

    fun reviveCost(revives: Int, discount: Int = 0) = (50 + revives * 30 - discount).coerceAtLeast(10)

    fun start(
        height: Float = 520f,
        seed: Int = Random.nextInt(1, Int.MAX_VALUE),
        perks: SkyBirdPerks = SkyBirdPerks(),
    ): SkyBirdState {
        val h = height.coerceIn(260f, 700f)
        val (gate, nextSeed) = nextGate(CloudGate(-1, WIDTH - 188f, h * .46f), h, 0, seed, perks.gapBonus)
        // 事件使用独立的随机链，云柱生成序列不会因新增玩法而改变。
        return SkyBirdState(height = h, gates = listOf(gate), randomState = nextSeed,
            perks = perks, eventSeed = seed xor 0x5f3759df)
    }

    internal fun nextGate(
        previous: CloudGate,
        height: Float,
        score: Int,
        seed: Int,
        bonus: Float = 0f,
    ): Pair<CloudGate, Int> {
        val rng = SkyBirdRandom(seed)
        val opening = (gap(height, score, bonus) + (rng.sample() - .5f) * 10f).coerceAtLeast(80f)
        val margin = opening / 2f + 30f
        val delta = (height * .22f).coerceIn(60f, 112f)
        val lo = maxOf(margin, previous.centerY - delta)
        val hi = minOf(height - FLOOR - margin, previous.centerY + delta)
        val center = lo + rng.sample() * (hi - lo)
        val spacing = 202f + rng.sample() * 46f
        return CloudGate(previous.id + 1, previous.x + spacing, center, gap = opening) to rng.seed
    }

    /** 约三成云柱附近会刷出道具；幸运之子提高掉落率与稀有道具权重。 */
    internal fun nextPickup(gate: CloudGate, height: Float, seed: Int, luck: Float = 0f): Pair<SkyBirdPickup?, Int> {
        val rng = SkyBirdRandom(seed)
        val roll = rng.sample()
        if (roll > .72f + (luck * .06f).coerceAtMost(.15f)) return null to rng.seed
        val type = when {
            roll < .30f -> SkyBirdPickupType.COIN
            roll < .40f -> SkyBirdPickupType.SHIELD
            roll < .48f -> SkyBirdPickupType.SLOW
            roll < .56f -> SkyBirdPickupType.MAGNET
            roll < .62f -> SkyBirdPickupType.DOUBLE
            roll < .68f -> SkyBirdPickupType.ROCKET
            else -> SkyBirdPickupType.AUTOPILOT
        }
        val x = gate.x + 84f + rng.sample() * 56f
        val y = (gate.centerY + (rng.sample() - .5f) * (gate.gap - 34f))
            .coerceIn(38f, height - FLOOR - 38f)
        return SkyBirdPickup(gate.id, x, y, type) to rng.seed
    }

    fun flap(state: SkyBirdState): SkyBirdState = when (state.phase) {
        SkyBirdPhase.READY -> state.copy(phase = SkyBirdPhase.FLYING, velocity = FLAP)
        SkyBirdPhase.FLYING -> state.copy(velocity = FLAP)
        else -> state
    }
    fun pause(state: SkyBirdState) = if (state.phase == SkyBirdPhase.FLYING)
        state.copy(phase = SkyBirdPhase.PAUSED) else state
    fun resume(state: SkyBirdState) = if (state.phase == SkyBirdPhase.PAUSED)
        state.copy(phase = SkyBirdPhase.FLYING) else state

    /** 商店消耗品在飞行中主动使用；火箭与自动导航按剩余距离消耗。 */
    fun activate(state: SkyBirdState, item: SkyBirdItem): SkyBirdState {
        if (state.phase != SkyBirdPhase.FLYING) return state
        return when (item) {
            SkyBirdItem.SHIELD -> state.copy(shield = true)
            SkyBirdItem.SLOW -> state.copy(slow = 8f)
            SkyBirdItem.MAGNET -> state.copy(magnet = 8f)
            SkyBirdItem.DOUBLE -> state.copy(double = 10f)
            SkyBirdItem.ROCKET -> state.copy(rocket = ROCKET_RANGE + state.perks.rocketDistance,
                autopilot = 0f, shield = true)
            SkyBirdItem.AUTOPILOT -> state.copy(autopilot = AUTOPILOT_RANGE + state.perks.autopilotDistance,
                rocket = 0f)
        }
    }

    /** 花金币复活：保留分数与本局收益，附近云缝放宽并给短暂无敌。 */
    fun revive(state: SkyBirdState): SkyBirdState {
        if (state.phase != SkyBirdPhase.LANDED) return state
        val safeY = (state.height * .46f).coerceIn(RADIUS + 2f, state.height - FLOOR - RADIUS - 2f)
        val gates = state.gates.map { gate ->
            if (gate.x < BIRD_X + GATE_WIDTH + 30f && gate.x + GATE_WIDTH > BIRD_X - 30f)
                gate.copy(centerY = safeY, gap = (gate.gap + 24f).coerceAtMost(170f), passed = true)
            else gate
        }
        return state.copy(phase = SkyBirdPhase.FLYING, birdY = safeY, velocity = 0f,
            gates = gates, invincible = 2f, shield = true, revives = state.revives + 1,
            celebration = null, rocket = 0f, autopilot = 0f,
            goldRain = 0f, lightGravity = 0f, narrowGap = 0f,
            eventCountdown = 620f, eventNotice = null)
    }

    /** 管理员直接改分数或开关无敌，不绕过正常物理流程。 */
    fun addScore(state: SkyBirdState, amount: Int) =
        if (amount > 0) state.copy(score = state.score + amount) else state
    fun setGodMode(state: SkyBirdState, enabled: Boolean) = state.copy(godMode = enabled)

    fun step(state: SkyBirdState, elapsedSeconds: Float): SkyBirdState {
        if (state.phase != SkyBirdPhase.FLYING || !elapsedSeconds.isFinite() || elapsedSeconds <= 0f) return state
        // A delayed frame never teleports through an obstacle. Lifecycle resumes reset the clock.
        var remaining = elapsedSeconds.coerceAtMost(.1f)
        var next = state
        while (remaining > .000001f && next.phase == SkyBirdPhase.FLYING) {
            val dt = min(remaining, 1f / 120f)
            next = tick(next, dt)
            remaining -= dt
        }
        return next
    }

    private fun tick(state: SkyBirdState, dt: Float): SkyBirdState {
        val rocketOn = state.rocket > 0f
        val autoOn = state.autopilot > 0f
        val autoActive = rocketOn || autoOn
        val slowFactor = if (state.slow > 0f) .72f else 1f
        val gravity = GRAVITY * (if (state.slow > 0f) .78f else 1f) *
            (if (state.lightGravity > 0f) .70f else 1f)
        val speed = (155f + min(state.score * 1.5f, 30f)) * slowFactor * (if (rocketOn) 1.5f else 1f)
        val velocity = (state.velocity + gravity * dt).coerceAtMost(MAX_FALL * slowFactor)
        val accelerating = ((MAX_FALL * slowFactor - state.velocity) / gravity).coerceIn(0f, dt)
        val y = state.birdY + state.velocity * accelerating + gravity * accelerating * accelerating / 2f +
            MAX_FALL * slowFactor * (dt - accelerating)
        var gates = state.gates.map { it.copy(x = it.x - speed * dt) }
        var pickups = state.pickups.map { it.copy(x = it.x - speed * dt) }

        // 火箭 / 自动导航：优先追前方最近的金币或道具，其次对齐下一道云缝；
        // 按飞行距离而不是时间结束，无敌保护下可以放心穿过云柱去拾取。
        val gateY = gates.firstOrNull { it.x + GATE_WIDTH > BIRD_X - 12f }?.centerY ?: state.height * .46f
        val targetY = if (autoActive) {
            pickups.filter { !it.collected && it.x > BIRD_X - 18f && it.x < BIRD_X + 240f }
                .minByOrNull { it.x }?.y ?: gateY
        } else gateY
        val birdY = if (autoActive)
            (state.birdY + (targetY - state.birdY) * (dt * 10f).coerceAtMost(1f))
                .coerceIn(RADIUS + 2f, state.height - FLOOR - RADIUS - 2f)
        else y

        var shield = state.shield
        var slow = (state.slow - dt).coerceAtLeast(0f)
        var magnet = (state.magnet - dt).coerceAtLeast(0f)
        var double = (state.double - dt).coerceAtLeast(0f)
        var invincible = (state.invincible - dt).coerceAtLeast(0f)
        var rocket = (state.rocket - speed * dt).coerceAtLeast(0f)
        var autopilot = (state.autopilot - speed * dt).coerceAtLeast(0f)
        var goldRain = (state.goldRain - dt).coerceAtLeast(0f)
        var lightGravity = (state.lightGravity - dt).coerceAtLeast(0f)
        var narrowGap = (state.narrowGap - dt).coerceAtLeast(0f)
        var earned = state.earnedCoins
        val multiplier = if (state.double > 0f) 2 else 1
        val coinMultiplier = if (goldRain > 0f) 2 else 1
        val radius = RADIUS + 12f
        pickups = pickups.map { pickup ->
            if (pickup.collected) return@map pickup
            val dx = BIRD_X - pickup.x
            val dy = birdY - pickup.y
            val attracted = (magnet > 0f || autoActive) && pickup.type == SkyBirdPickupType.COIN &&
                abs(dx) < 110f && abs(dy) < 95f
            if (!attracted && dx * dx + dy * dy > radius * radius) return@map pickup
            when (pickup.type) {
                SkyBirdPickupType.COIN -> earned += (5 * state.perks.coinBoost * multiplier * coinMultiplier).toInt()
                SkyBirdPickupType.SHIELD -> shield = true
                SkyBirdPickupType.SLOW -> slow = 8f
                SkyBirdPickupType.MAGNET -> magnet = 8f
                SkyBirdPickupType.DOUBLE -> double = 10f
                SkyBirdPickupType.ROCKET -> {
                    rocket = ROCKET_RANGE + state.perks.rocketDistance; autopilot = 0f
                }
                SkyBirdPickupType.AUTOPILOT -> {
                    autopilot = AUTOPILOT_RANGE + state.perks.autopilotDistance; rocket = 0f
                }
            }
            pickup.copy(collected = true)
        }

        val hitsGate = gates.any { gate ->
            val halfGap = gate.gap / 2f
            val nearestX = BIRD_X.coerceIn(gate.x, gate.x + GATE_WIDTH)
            val upperBottom = gate.centerY - halfGap
            val lowerTop = gate.centerY + halfGap
            fun touches(nearestY: Float): Boolean {
                val dx = BIRD_X - nearestX
                val dy = birdY - nearestY
                return dx * dx + dy * dy <= RADIUS * RADIUS
            }
            touches(birdY.coerceAtMost(upperBottom)) || touches(birdY.coerceAtLeast(lowerTop))
        }
        val outOfBounds = birdY - RADIUS <= 0f || birdY + RADIUS >= state.height - FLOOR
        val danger = outOfBounds || hitsGate
        val guarded = danger && (state.godMode || rocketOn || autoOn || invincible > 0f || shield)
        val landed = danger && !guarded
        if (guarded && invincible <= 0f && !state.godMode && !rocketOn && !autoOn) {
            shield = false
            invincible = 1.2f
        }
        val safeY = if (guarded) birdY.coerceIn(RADIUS + 2f, state.height - FLOOR - RADIUS - 2f) else birdY

        var score = state.score
        if (!landed) gates = gates.map {
            if (!it.passed && it.x + GATE_WIDTH < BIRD_X - RADIUS) {
                score += multiplier
                earned += (state.perks.coinBoost * multiplier * coinMultiplier).toInt()
                it.copy(passed = true)
            } else it
        }

        // 世界事件：独立随机链，按飞行距离触发，暂停与刷新率都不影响结果。
        var eventCountdown = state.eventCountdown - speed * dt
        var eventSeed = state.eventSeed
        var eventNotice = state.eventNotice?.let { it.copy(age = it.age + dt) }
            ?.takeIf { it.age < EVENT_NOTICE_SECONDS }
        if (!landed && eventCountdown <= 0f) {
            val rng = SkyBirdRandom(eventSeed)
            val bad = rng.sample() < (.15f - state.perks.luck * .04f).coerceAtLeast(.02f)
            val pool = if (bad) listOf(SkyBirdEvent.WIND_GUST)
                else SkyBirdEvent.entries.filter { !it.rough }
            val event = pool[(rng.sample() * pool.size).toInt().coerceAtMost(pool.size - 1)]
            eventCountdown = (520f + rng.sample() * 380f - state.perks.luck * 60f).coerceAtLeast(260f)
            eventSeed = rng.seed
            eventNotice = SkyBirdEventNotice(event)
            when (event) {
                SkyBirdEvent.GOLD_RAIN -> {
                    goldRain = 9f
                    pickups = pickups + (0 until 3).map { i ->
                        SkyBirdPickup(-(state.distance.toInt() + i + 1), WIDTH + i * 46f,
                            60f + rng.sample() * (state.height - FLOOR - 120f), SkyBirdPickupType.COIN)
                    }
                }
                SkyBirdEvent.TAILWIND -> lightGravity = 9f
                SkyBirdEvent.CLOUD_GIFT -> earned += (30 * state.perks.coinBoost).toInt()
                SkyBirdEvent.STAR_SHOWER -> score += 3
                SkyBirdEvent.LUCKY_CLOVER -> { shield = true; magnet = 8f }
                SkyBirdEvent.COIN_FLURRY -> {
                    magnet = 10f; earned += (10 * state.perks.coinBoost).toInt()
                }
                SkyBirdEvent.WIND_GUST -> narrowGap = 9f
            }
        }
        val milestones = if (!landed) score / 10 - state.score / 10 else 0
        if (milestones > 0) earned += (15 * state.perks.coinBoost).toInt() * milestones

        val last = gates.last()
        var randomState = state.randomState
        if (last.x < WIDTH - 120f) {
            val (gate, gateSeed) = nextGate(last, state.height, score, randomState,
                state.perks.gapBonus - if (narrowGap > 0f) 14f else 0f)
            gates = gates + gate
            val (pickup, pickupSeed) = nextPickup(gate, state.height, gateSeed, state.perks.luck)
            if (pickup != null) pickups = pickups + pickup
            randomState = pickupSeed
        }
        val celebration = if (!landed && score / 10 > state.score / 10)
            SkyBirdCelebration(score / 10 * 10, birdY) else state.celebration?.let {
                it.copy(age = it.age + dt).takeIf { reward -> reward.age < SkyBirdJourney.CELEBRATION_SECONDS }
            }
        return state.copy(
            phase = if (landed) SkyBirdPhase.LANDED else SkyBirdPhase.FLYING,
            birdY = safeY.coerceIn(RADIUS, state.height - FLOOR - RADIUS),
            velocity = if (guarded || autoActive) 0f else velocity,
            distance = state.distance + speed * dt, score = score,
            gates = gates.filter { it.x + GATE_WIDTH > -16f },
            pickups = pickups.filter { !it.collected && it.x > -30f },
            randomState = randomState, celebration = celebration,
            earnedCoins = earned, shield = shield, invincible = invincible,
            slow = slow, magnet = magnet, double = double,
            rocket = rocket, autopilot = autopilot,
            goldRain = goldRain, lightGravity = lightGravity, narrowGap = narrowGap,
            eventCountdown = eventCountdown, eventSeed = eventSeed, eventNotice = eventNotice,
        )
    }
}
