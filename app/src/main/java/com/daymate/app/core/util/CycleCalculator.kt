package com.ayaka7452.daymate.core.util

import java.time.LocalDate

/**
 * 周期管家：经期/排卵期/黄体期推算。
 *
 * 医学口径（日历法，严谨性说明）：
 *  - 黄体期：排卵后到下次月经来潮，医学共识近似为固定 14 天
 *    （实际个体波动 11~17 天，且同一人不同周期也有波动，此处取标准近似值）。
 *  - 排卵日 ≈ 预测下次经期首日 − 14 天。
 *  - 排卵期窗口 = 排卵日前 5 天 ～ 排卵日后 1 天，共约 7 天：
 *    精子在体内最长存活约 5 天，卵子排出后可受精约 24 小时。
 *  - 月经期 = 经期首日 ～ 首日 + 持续天数 − 1；卵泡期 = 月经期结束后 ～ 排卵日前一日。
 *  - 周期天数 = 相邻两次经期首日的间隔。有效周期记录取 15~60 天；
 *    近期均值最多参考最近 3 次（部分周期受压力/作息/疾病影响，均值仅供参考）。
 *
 * ⚠️ 日历法推算仅供参考，不能作为避孕或医学诊断依据。
 */
object CycleCalculator {

    const val DEFAULT_CYCLE_DAYS = 28
    const val DEFAULT_PERIOD_DAYS = 5
    const val MIN_CYCLE_DAYS = 15
    const val MAX_CYCLE_DAYS = 60
    const val MIN_PERIOD_DAYS = 2
    const val MAX_PERIOD_DAYS = 10

    /** 参与均值计算的记录条数（与主流经期 App 口径一致：近 3 次）。 */
    const val AVG_WINDOW = 3

    /** 黄体期固定长度（天）：排卵日 = 下次经期首日 − LUTEAL_DAYS。 */
    const val LUTEAL_DAYS = 14L

    /** 排卵期窗口：排卵日前 5 天 ～ 排卵日后 1 天。 */
    const val OVULATION_BEFORE = 5L
    const val OVULATION_AFTER = 1L

    /** 周期天数合法区间（超出提醒用户确认，但不强制拦截——身体情况因人而异）。 */
    fun isCycleDaysValid(days: Int) = days in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS

    fun isPeriodDaysValid(days: Int) = days in MIN_PERIOD_DAYS..MAX_PERIOD_DAYS

    /** 相邻两次经期首日算出一个周期长度；不在 15~60 天内视为无效记录（漏记/异常周期）。 */
    fun cycleLengthBetween(prevStart: Long, nextStart: Long): Int? {
        val diff = (nextStart - prevStart).toInt()
        return if (diff in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS) diff else null
    }

    /**
     * 近 N 次（最多 3 次）实测周期均值：传入按日期降序的经期首日列表。
     * 不足 2 次记录时返回 null（无法计算均值）。
     */
    fun averageCycleDays(startDaysDesc: List<Long>): Int? {
        if (startDaysDesc.size < 2) return null
        val lengths = mutableListOf<Int>()
        for (i in 0 until startDaysDesc.size - 1) {
            if (lengths.size >= AVG_WINDOW) break
            cycleLengthBetween(startDaysDesc[i + 1], startDaysDesc[i])?.let { lengths.add(it) }
        }
        if (lengths.isEmpty()) return null
        return Math.round(lengths.sum().toDouble() / lengths.size).toInt()
    }

    /**
     * 近 N 次（最多 3 次）记录的经期持续天数均值：传入按日期降序记录的 periodDays 列表。
     * 无记录时返回 null。单条记录也能得出均值（该条本身就是样本）。
     */
    fun averagePeriodDays(periodDaysDesc: List<Int>): Int? {
        if (periodDaysDesc.isEmpty()) return null
        val sample = periodDaysDesc.take(AVG_WINDOW)
        return Math.round(sample.sum().toDouble() / sample.size).toInt()
    }

    /** 预测下一次经期首日 = 已知首日 + 周期天数。 */
    fun nextStartAfter(lastStartEpochDay: Long, cycleDays: Int): Long =
        lastStartEpochDay + cycleDays

    /** 排卵日 = 预测下次经期首日 − 14 天（黄体期固定近似）。 */
    fun ovulationDay(nextStartEpochDay: Long): Long =
        nextStartEpochDay - LUTEAL_DAYS

    /** 一次经期的月经期区间 [首日, 首日 + periodDays − 1]。 */
    fun periodRange(startEpochDay: Long, periodDays: Int): LongRange =
        startEpochDay..(startEpochDay + periodDays - 1)

    /** 排卵期窗口 [排卵日 − 5, 排卵日 + 1]。 */
    fun ovulationRange(nextStartEpochDay: Long): LongRange =
        (ovulationDay(nextStartEpochDay) - OVULATION_BEFORE)..(ovulationDay(nextStartEpochDay) + OVULATION_AFTER)

    /** 黄体期区间 [排卵日, 下次经期首日 − 1]（下次经期当天回到月经期）。 */
    fun lutealRange(nextStartEpochDay: Long): LongRange =
        ovulationDay(nextStartEpochDay)..(nextStartEpochDay - 1)

    /** 卵泡期区间 [月经期结束次日, 排卵日前一日]；经期过长时可能为空区间。 */
    fun follicularRange(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): LongRange {
        val from = startEpochDay + periodDays
        val to = ovulationDay(nextStartEpochDay) - 1
        return if (from <= to) from..to else LongRange.EMPTY
    }

    /** 当前处于哪个阶段（相对下一次预测经期）。 */
    fun phaseOf(todayEpochDay: Long, lastStartEpochDay: Long, periodDays: Int, cycleDays: Int): Phase {
        val nextStart = nextStartAfter(lastStartEpochDay, cycleDays)
        return when {
            todayEpochDay in periodRange(lastStartEpochDay, periodDays) -> Phase.PERIOD
            todayEpochDay in ovulationRange(nextStart) -> Phase.OVULATION
            todayEpochDay in lutealRange(nextStart) -> Phase.LUTEAL
            todayEpochDay in follicularRange(lastStartEpochDay, periodDays, nextStart) -> Phase.FOLLICULAR
            // 今天已在预测周期之外（下次经期理论上今天或之前来但还没登记）：
            // 若落在以 lastStart + periodDays 为终点的经期区间之后，视为黄体期延迟，仍按黄体期提示
            else -> Phase.LUTEAL
        }
    }

    /**
     * 推算任意日期所处阶段（日历视图着色用）。logsDesc：(经期首日, 该次持续天数) 列表，按首日降序。
     *  - 落在任一已登记经期区间内（按该记录自身的持续天数）→ 月经期
     *  - 否则以「最后一个不晚于该日的记录」为锚点推算（记录都在未来时，用最早记录按周期向前虚拟推算）
     *  - 锚点推进保证 nextStart 晚于目标日；逾期未登记的未来日子按预测月经期着色
     */
    fun phaseOfAnyDay(
        epochDay: Long,
        logsDesc: List<Pair<Long, Int>>,
        cycleDays: Int
    ): Phase {
        if (logsDesc.isEmpty()) return Phase.FOLLICULAR
        for ((s, pd) in logsDesc) {
            if (epochDay in periodRange(s, pd)) return Phase.PERIOD
        }
        var anchor = logsDesc.lastOrNull { it.first <= epochDay }?.first
        if (anchor == null) {
            val first = logsDesc.last().first
            val k = (first - epochDay + cycleDays - 1) / cycleDays
            anchor = first - k * cycleDays
        } else {
            while (anchor + cycleDays <= epochDay) anchor += cycleDays
        }
        val nextStart = anchor + cycleDays
        return when {
            epochDay in ovulationRange(nextStart) -> Phase.OVULATION
            epochDay in lutealRange(nextStart) -> Phase.LUTEAL
            else -> Phase.FOLLICULAR
        }
    }

    enum class Phase(val label: String) {
        PERIOD("月经期"),
        FOLLICULAR("卵泡期"),
        OVULATION("排卵期"),
        LUTEAL("黄体期")
    }
}
