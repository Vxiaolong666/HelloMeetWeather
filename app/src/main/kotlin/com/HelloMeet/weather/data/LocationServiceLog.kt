package com.HelloMeet.weather.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 定位与逆地理链路的内存日志：只保留最近 [Capacity] 条，不落盘、不含 Key 与坐标，
 * 供「设置 → 位置服务引擎 → 定位服务日志」页展示实际生效的服务商与失败/降级过程。
 */
internal object LocationServiceLog {

    enum class Level { INFO, WARN, ERROR }

    data class Entry(
        val timeMillis: Long,
        val level: Level,
        val tag: String,
        val message: String,
    )

    /** 最近一次定位真正生效的结果：引擎偏好 + 实际返回地址的服务商 + 街道状态。 */
    data class Resolution(
        val timeMillis: Long,
        val engine: String,
        val source: String,
        val street: String?,
        val streetStatus: LocationSource.StreetStatus,
        val accuracyMeters: Float?,
        val fixAgeSeconds: Long,
    )

    private const val Capacity = 200
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val entries = ArrayDeque<Entry>()

    var lastResolution: Resolution? = null
        private set

    fun info(tag: String, message: String) = note(Level.INFO, tag, message)
    fun warn(tag: String, message: String) = note(Level.WARN, tag, message)
    fun error(tag: String, message: String) = note(Level.ERROR, tag, message)

    fun note(level: Level, tag: String, message: String) {
        synchronized(entries) {
            entries.addLast(Entry(System.currentTimeMillis(), level, tag, message))
            while (entries.size > Capacity) entries.removeFirst()
        }
    }

    fun recordResolution(resolution: Resolution) { lastResolution = resolution }

    /** 旧→新顺序快照，展示端自行反转。 */
    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() {
        synchronized(entries) { entries.clear() }
        lastResolution = null
    }

    fun formatTime(timeMillis: Long): String = timeFormat.format(Date(timeMillis))
}
