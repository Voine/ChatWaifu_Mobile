package com.chatwaifu.mobile.data.memory

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Description: 记忆层里所有「给模型看的时间」都从这里出，格式统一：
 * 日期用「10月12日」，时刻带星期，间隔说成「4 天」而不是毫秒数。
 *
 * 时区取设备当前时区——陪伴场景里「现在是晚上 11 点」要和用户墙上的钟一致。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
internal object MemoryTime {

    private val day = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val moment = DateTimeFormatter.ofPattern("M月d日 EEE HH:mm", Locale.CHINA)

    fun dayLabel(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        day.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** 「10月15日 周三 23:10」 */
    fun momentLabel(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        moment.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** 间隔的口语说法；不到 [MIN_GAP_MS] 返回 null（刚聊过不值得提） */
    fun gapLabel(gapMs: Long): String? = when {
        gapMs < MIN_GAP_MS -> null
        gapMs < DAY_MS -> "${gapMs / HOUR_MS} 小时"
        else -> "${gapMs / DAY_MS} 天"
    }

    private const val HOUR_MS = 60L * 60 * 1000
    private const val DAY_MS = 24 * HOUR_MS
    const val MIN_GAP_MS = HOUR_MS
}
