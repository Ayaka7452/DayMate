package com.ayaka7452.daymate.core.util

import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
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
                isFuture -> if (totalMonths > 0) Tr.s(R.string.unit_months_future, totalMonths) else dayText(diffDays, null, true)
                hasRef && totalMonths > 0 -> Tr.s(R.string.unit_months_past_ref, totalMonths, refDays)
                totalMonths > 0 -> Tr.s(R.string.unit_months_past, totalMonths)
                else -> dayText(diffDays, null, false)
            }
            UNIT_YEAR -> when {
                isFuture -> when {
                    period.years > 0 -> Tr.s(R.string.unit_years_future, period.years)
                    totalMonths > 0 -> Tr.s(R.string.unit_months_future, totalMonths)
                    else -> dayText(diffDays, null, true)
                }
                hasRef && period.years > 0 -> Tr.s(R.string.unit_years_past_ref, period.years, refDays)
                period.years > 0 -> Tr.s(R.string.unit_years_past, period.years)
                totalMonths > 0 -> Tr.s(R.string.unit_months_past, totalMonths)
                else -> dayText(diffDays, null, false)
            }
            else -> dayText(diffDays, refDays, isFuture)
        }
    }

    private fun dayText(diffDays: Long, refDays: Int?, isFuture: Boolean): String = when {
        isFuture -> Tr.s(R.string.unit_days_future, diffDays)
        refDays != null && refDays > 0 -> Tr.s(R.string.unit_days_past_ref, -diffDays, refDays)
        else -> Tr.s(R.string.unit_days_past, -diffDays)
    }

    // ===================== 「按时间倒数」（精确到分，见 EventEntity.endMinuteOfDay） =====================

    /**
     * 时间模式倒计时的展示结果。
     *
     * [number]/[unit] 给详情页大数字用（取最大的有效单位，逐级退到秒）；
     * [caption] 是次级说明（「还剩 X 小时 Y 分」这类），列表行与详情页的「非时间模式」副行共用。
     */
    data class TimedCountdown(
        val number: String,
        val unit: String,
        val caption: String,
        /** 剩余秒数（正=还没到，负=已过）。 */
        val seconds: Long
    )

    /**
     * 「天 + 时 + 分 + 秒」四段独立数值（时间模式详情页用）。
     *
     * 为什么不给拼好的整串：详情页要把「天」用主题大字、「时分秒」用淡蓝小字分两行渲染，
     * 拼成一串就得再拆回来。数值只取整（不补零），标签由调用方按语言取。
     */
    data class TimedParts4(
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val isFuture: Boolean
    )

    /** 取「天/时/分/秒」四段（已过一侧同样返回正数，方向看 [TimedParts4.isFuture]）。 */
    fun timedParts4(
        targetEpochDay: Long,
        endMinuteOfDay: Int,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now()
    ): TimedParts4 {
        val p = timedParts(targetEpochDay, endMinuteOfDay, now)
        return TimedParts4(p.days, p.hours, p.minutes, p.seconds, p.isFuture)
    }

    /**
     * 按「目标日期 + 目标时刻（当天分钟数）」计算倒计时。
     *
     * 分段规则（用户定）：有整天就看天+小时，不足 1 小时看分钟，不足 3 分钟看秒；
     * 已过一侧完全镜像（同样先看天、再小时、再分、再秒）。
     */
    fun timedCountdown(
        targetEpochDay: Long,
        endMinuteOfDay: Int,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now()
    ): TimedCountdown {
        val (d, h, m, s, isFuture) = timedParts(targetEpochDay, endMinuteOfDay, now)
        val number = when {
            d >= 1 -> d.toString()
            h >= 1 -> h.toString()
            m >= 3 -> m.toString()
            else -> s.toString()
        }
        val unit = when {
            d >= 1 -> Tr.s(R.string.unit_days)
            h >= 1 -> Tr.s(R.string.unit_hours)
            m >= 3 -> Tr.s(R.string.unit_minutes)
            else -> Tr.s(R.string.unit_seconds)
        }
        val totalSecs = d * 86_400 + h * 3_600 + m * 60 + s
        return TimedCountdown(number, unit, timedCaption(d, h, m, s, isFuture), if (isFuture) totalSecs else -totalSecs)
    }

    /** 列表行用的时间模式文案：带最大单位的两段（「还剩 3 天 5 小时」）。 */
    fun formatTimedShort(
        targetEpochDay: Long,
        endMinuteOfDay: Int,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now()
    ): String {
        val (d, h, m, s, isFuture) = timedParts(targetEpochDay, endMinuteOfDay, now)
        return when {
            d >= 1 -> pair(R.string.timed_remaining_dh, R.string.timed_past_dh, d, h, isFuture)
            h >= 1 -> pair(R.string.timed_remaining_hm, R.string.timed_past_hm, h, m, isFuture)
            m >= 3 -> pair(R.string.timed_remaining_ms, R.string.timed_past_ms, m, s, isFuture)
            else -> single(R.string.timed_remaining_s, R.string.timed_past_s, s, isFuture)
        }
    }

    /** 时间模式的分解结果：天/小时/分/秒 + 是否还没到点。 */
    data class TimedParts(
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val isFuture: Boolean
    )

    private fun timedParts(
        targetEpochDay: Long,
        endMinuteOfDay: Int,
        now: java.time.LocalDateTime
    ): TimedParts {
        val minute = endMinuteOfDay.coerceIn(0, 24 * 60 - 1)
        val target = java.time.LocalDate.ofEpochDay(targetEpochDay)
            .atTime(minute / 60, minute % 60)
        val isFuture = !target.isBefore(now)
        val secs = if (isFuture) {
            java.time.Duration.between(now, target).seconds
        } else {
            java.time.Duration.between(target, now).seconds
        }
        return TimedParts(secs / 86_400, secs % 86_400 / 3_600, secs % 3_600 / 60, secs % 60, isFuture)
    }

    /** 次级说明：主数字已占用最大单位，这里只报更小的那一段。 */
    private fun timedCaption(d: Long, h: Long, m: Long, s: Long, isFuture: Boolean): String = when {
        d >= 1 && h > 0 -> pair(R.string.timed_remaining_hm, R.string.timed_past_hm, h, m, isFuture)
        d >= 1 && m > 0 -> pair(R.string.timed_remaining_ms, R.string.timed_past_ms, m, s, isFuture)
        h >= 1 && m > 0 -> pair(R.string.timed_remaining_ms, R.string.timed_past_ms, m, s, isFuture)
        h >= 1 && s > 0 -> single(R.string.timed_remaining_s, R.string.timed_past_s, s, isFuture)
        m >= 3 && s > 0 -> single(R.string.timed_remaining_s, R.string.timed_past_s, s, isFuture)
        else -> Tr.s(if (isFuture) R.string.detail_until_time else R.string.detail_past_time)
    }

    private fun pair(futureRes: Int, pastRes: Int, a: Long, b: Long, isFuture: Boolean): String =
        Tr.s(if (isFuture) futureRes else pastRes, a, b)

    private fun single(futureRes: Int, pastRes: Int, a: Long, isFuture: Boolean): String =
        Tr.s(if (isFuture) futureRes else pastRes, a)
}
