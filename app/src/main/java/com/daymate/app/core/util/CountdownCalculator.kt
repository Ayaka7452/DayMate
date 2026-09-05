package com.ayaka7452.daymate.core.util

import java.time.LocalDate
import java.time.Period

object CountdownCalculator {

    /** 显示单位：按天（默认）。 */
    const val UNIT_DAY = "DAY"

    /** 显示单位：按月。 */
    const val UNIT_MONTH = "MONTH"

    /** 显示单位：按年。 */
    const val UNIT_YEAR = "YEAR"

    /** 循环规则：每周同一天（如每周五）。 */
    const val REPEAT_WEEKLY = "WEEKLY"

    /** 循环规则：每月同一天。 */
    const val REPEAT_MONTHLY = "MONTHLY"

    /** 循环规则：每年同月同日。 */
    const val REPEAT_YEARLY = "YEARLY"

    /**
     * 循环事件的日期锚定：目标日期已过（< today）时，按规则滚动到下一个周期的同位日期。
     * - WEEKLY：锚定同一星期几（如原定周五，过期后顺延到下一个周五）；
     * - MONTHLY：锚定同一「日」（1月31日过期后 → 2月28/29日，再往后为每月 28/29/30/31 自动取月末）；
     * - YEARLY：锚定同一月日（月日与上年相同；闰年 2月29日 在平年落地为 2月28日）。
     * 目标日期在今天或未来、规则未知时不滚动，返回 null。
     */
    fun nextOccurrence(targetEpochDay: Long, rule: String?, today: LocalDate = LocalDate.now()): LocalDate? {
        if (rule == null) return null
        var date = LocalDate.ofEpochDay(targetEpochDay)
        if (date >= today) return null
        when (rule) {
            REPEAT_WEEKLY -> while (date < today) date = date.plusWeeks(1)
            REPEAT_MONTHLY -> while (date < today) date = date.plusMonths(1)
            REPEAT_YEARLY -> while (date < today) date = date.plusYears(1)
            else -> return null
        }
        return date
    }

    /** 目标日期相对今天还有多少天；负数表示已过。 */
    fun daysUntil(targetEpochDay: Long, today: LocalDate = LocalDate.now()): Long =
        targetEpochDay - today.toEpochDay()

    /** 每年重复：返回距离下一次发生的天数（今天则 0）。 */
    fun daysUntilNextOccurrence(targetEpochDay: Long, today: LocalDate = LocalDate.now()): Long {
        val target = LocalDate.ofEpochDay(targetEpochDay)
        val thisYear = target.withYear(today.year)
        if (thisYear.toEpochDay() >= today.toEpochDay()) {
            return thisYear.toEpochDay() - today.toEpochDay()
        }
        return thisYear.plusYears(1).toEpochDay() - today.toEpochDay()
    }

    /**
     * 按用户选择的单位格式化倒计时文本（事件列表行用）。
     * refDays 的单位跟随 unit（即用户在表单里按当前显示单位填写的对照值）。
     * - DAY / null / 未知值：按天；已过且 refDays>0 时显示「已过 X/N 天」。
     * - MONTH：显示整月数；已过且 refDays>0 时显示「已过 X/N 个月」；
     *   不足 1 个月时退回按天（此时对照值单位不符，N 不再拼接）。
     * - YEAR：显示整年数；已过且 refDays>0 时显示「已过 X/N 年」；
     *   不足 1 年退回按月（N 不拼接），再不足 1 个月退回按天。
     */
    fun formatCountdown(targetEpochDay: Long, unit: String?, refDays: Int? = null): String {
        val today = LocalDate.now()
        val diffDays = targetEpochDay - today.toEpochDay()
        val isFuture = diffDays >= 0
        val period = if (isFuture) {
            Period.between(today, LocalDate.ofEpochDay(targetEpochDay))
        } else {
            Period.between(LocalDate.ofEpochDay(targetEpochDay), today)
        }
        val totalMonths = period.years * 12L + period.months
        val hasRef = refDays != null && refDays > 0
        return when (unit) {
            UNIT_MONTH -> when {
                isFuture -> if (totalMonths > 0) "还有 $totalMonths 个月" else dayText(diffDays, null, true)
                hasRef && totalMonths > 0 -> "已过 $totalMonths/$refDays 个月"
                totalMonths > 0 -> "已过 $totalMonths 个月"
                else -> dayText(diffDays, null, false)
            }
            UNIT_YEAR -> when {
                isFuture -> when {
                    period.years > 0 -> "还有 ${period.years} 年"
                    totalMonths > 0 -> "还有 $totalMonths 个月"
                    else -> dayText(diffDays, null, true)
                }
                hasRef && period.years > 0 -> "已过 ${period.years}/$refDays 年"
                period.years > 0 -> "已过 ${period.years} 年"
                totalMonths > 0 -> "已过 $totalMonths 个月"
                else -> dayText(diffDays, null, false)
            }
            else -> dayText(diffDays, refDays, isFuture)
        }
    }

    private fun dayText(diffDays: Long, refDays: Int?, isFuture: Boolean): String = when {
        isFuture -> "还有 $diffDays 天"
        refDays != null && refDays > 0 -> "已过 ${-diffDays}/$refDays 天"
        else -> "已过 ${-diffDays} 天"
    }
}
