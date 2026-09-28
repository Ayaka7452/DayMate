package com.ayaka7452.daymate.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * 农历显示（仅用于日历格子的次行小字）。
 *
 * 实现走系统自带的 ICU4J [android.icu.util.ChineseCalendar]（API 24+，minSdk 26 可用），
 * 不手抄万年历数据表——天文算法由系统维护，无表错年份的风险。
 *
 * 显示口径（与主流中文日历一致）：
 *  - 农历节日（春节/元宵/端午/七夕/中秋/重阳/除夕/腊八）→ 显示节日名；
 *  - 初一 → 显示月名（正月、八月……）；
 *  - 其余 → 显示日名（初一…十五…三十）。
 */
object LunarCalendar {

    private val MONTH_NAMES =
        arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")

    private val DAY_NAMES = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    /** 农历节日：key = "月-日"（1 起，闰月不记节日）。 */
    private val LUNAR_FESTIVALS = mapOf(
        1 to 1 to "春节",
        1 to 15 to "元宵节",
        5 to 5 to "端午节",
        7 to 7 to "七夕",
        8 to 15 to "中秋节",
        9 to 9 to "重阳节",
        12 to 8 to "腊八"
    )

    /** 该日期的农历显示文本（节日名 / 月名 / 日名）。 */
    fun labelText(date: LocalDate): String {
        val (month, day) = monthDayOf(date)
        LUNAR_FESTIVALS[month to day]?.let { return it }
        // 除夕 = 春节的前一天（腊月廿九或三十，直接看「明天是否正月初一」最稳）
        if (month == 12 || month == -12) {
            val (nextMonth, nextDay) = monthDayOf(date.plusDays(1))
            if (nextMonth == 1 && nextDay == 1) return "除夕"
        }
        if (day == 1) return MONTH_NAMES[absMonth(month) - 1]
        return DAY_NAMES[day - 1]
    }

    /** 返回 (月, 日)；月为负数表示闰月（如 -6 = 闰六月）。 */
    private fun monthDayOf(date: LocalDate): Pair<Int, Int> {
        val cc = android.icu.util.ChineseCalendar()
        cc.timeInMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // ICU 的 MONTH 从 0 起；IS_LEAP_MONTH=1 表示该月为闰月
        val month0 = cc.get(Calendar.MONTH)
        val isLeap = cc.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1
        val day = cc.get(Calendar.DAY_OF_MONTH)
        val month = month0 + 1
        return (if (isLeap) -month else month) to day
    }

    private fun absMonth(month: Int): Int = if (month < 0) -month else month
}
