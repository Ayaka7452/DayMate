package com.ayaka7452.daymate.data.festival

import com.ayaka7452.daymate.core.util.LunarCalendar
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * 内置「固定节日」推算——**在线数据尚未覆盖某年份时的兜底**，不联网、不读缓存。
 *
 * ## 为什么需要它
 *
 * 次年的官方放假安排一般要到当年 11 月才由国务院公布。国庆过后到公布前这段时间，
 * 缓存里查不到任何 `>= 今天` 的条目，节日卡片只能显示「已缓存的法定节假日已全部结束」——
 * 而下一个节日其实是**已知的**：元旦必然在 1 月 1 日。
 *
 * ## 哪些能推、哪些不能
 *
 *  - **公历固定日**：元旦 1/1、劳动节 5/1、国庆节 10/1 —— 日历年本身就是确定的；
 *  - **农历固定日**：春节（正月初一）、端午节（五月初五）、中秋节（八月十五）——
 *    农历是确定的天文历法，[LunarCalendar.solarDatesOf] 用系统 ICU 换算得到**准确公历日期**，
 *    误差为零（这里不是「估计」）；
 *  - **节气**：清明节按太阳黄经 15° 的通式算，公历日落在 4/4~4/6；
 *  - **推不出来的**：调休怎么挪、连休几天。那是行政安排，只能等官方公布。
 *
 * 所以本表给出的日期**可信**，缺的只是「放几天、哪天补班」——因此整条结果标
 * [FestivalDay.isEstimate] = true，由 UI 明确告知用户这是推算值，下载到官方数据后自动覆盖。
 *
 * ## 只服务中国源
 *
 * 表里全是中国的法定节日，换到美/日/韩数据源时凭空冒出「春节」是错的，
 * 故由调用方（[FestivalRepository.nextOffDayOrEstimate]）限定只在 CN 源下使用。
 */
object FestivalEstimator {

    /** 公历固定日期的法定节日：锚定名 → (月, 日)。锚定名与数据源一致，才能被 HolidayNames 翻译。 */
    private val SOLAR_FIXED = listOf(
        "元旦" to (1 to 1),
        "劳动节" to (5 to 1),
        "国庆节" to (10 to 1)
    )

    /** 农历固定日期的法定节日：锚定名 → (农历月, 农历日)，闰月不计。 */
    private val LUNAR_FIXED = listOf(
        "春节" to (1 to 1),
        "端午节" to (5 to 5),
        "中秋节" to (8 to 15)
    )

    private const val QINGMING = "清明节"

    /**
     * 按年缓存：一年只需扫一次农历（365 次 ICU 取值）。
     * 主页的冷启动预载是**同步**跑的，不能每次都重扫。
     */
    private val yearCache = ConcurrentHashMap<Int, List<FestivalDay>>()

    /** [year] 年全部可推算的固定节日（按日期升序）。 */
    fun ofYear(year: Int): List<FestivalDay> = yearCache.getOrPut(year) { build(year) }

    /**
     * [from] 当天或之后的下一个可推算节日；先看当年，再看次年。
     * 返回 null 只可能出现在「from 晚于次年的最后一个固定节日」这种极端情况（正常不会）。
     */
    fun next(from: LocalDate): FestivalDay? =
        listOf(from.year, from.year + 1)
            .asSequence()
            .flatMap { ofYear(it).asSequence() }
            .firstOrNull { !it.date.isBefore(from) }

    private fun build(year: Int): List<FestivalDay> {
        val out = mutableListOf<FestivalDay>()

        SOLAR_FIXED.forEach { (name, md) ->
            out += FestivalDay(
                name = name,
                date = LocalDate.of(year, md.first, md.second),
                isOffDay = true,
                isEstimate = true
            )
        }

        // 一次遍历拿齐三个农历节日（各扫全年会白跑两遍）
        val lunar = LunarCalendar.solarDatesOf(year, LUNAR_FIXED.map { it.second })
        LUNAR_FIXED.forEach { (name, md) ->
            lunar[md]?.let { date ->
                out += FestivalDay(name = name, date = date, isOffDay = true, isEstimate = true)
            }
        }

        // 清明是二十四节气（太阳黄经 15°），既非公历固定也不是农历日期。
        // 万一日号算出界（历史年份用别的通式才可能），宁可不显示也不要崩——这里只是兜底展示。
        runCatching {
            out += FestivalDay(
                name = QINGMING,
                date = LocalDate.of(year, 4, qingmingDay(year)),
                isOffDay = true,
                isEstimate = true
            )
        }

        return out.sortedBy { it.date }
    }

    /**
     * 清明节所在公历日：21 世纪通式 `⌊Y×0.2422 + 4.81⌋ − ⌊Y/4⌋`（Y = 年份后两位，整数除法即下取整）。
     * 校验：2025 → 4/4、2026 → 4/5、2027 → 4/5、2028 → 4/4，与官方公布一致。
     */
    private fun qingmingDay(year: Int): Int {
        val y = year % 100
        return (y * 0.2422 + 4.81).toInt() - y / 4
    }
}
