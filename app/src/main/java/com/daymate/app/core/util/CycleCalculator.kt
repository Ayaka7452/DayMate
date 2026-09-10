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

    /**
     * 提前天数阈值：距预测下次经期提前超过该天数登记经期，视为可能非经期出血。
     * 医学依据：FIGO/ACOG 正常月经标准为周期 24~38 天、相邻周期波动 ≤7~9 天，
     * 提前 7 天内的出血仍属正常波动；提前超过 7 天（周期 <21 天左右）属频发出血/异常范围。
     */
    const val EARLY_PERIOD_THRESHOLD_DAYS = 7

    /** 排卵期窗口：排卵日前 5 天 ～ 排卵日后 1 天。 */
    const val OVULATION_BEFORE = 5L
    const val OVULATION_AFTER = 1L

    /** 动态推算保底：卵泡期最少天数（经期过长时排卵日/窗口后移收窄，保证卵泡期不被吃光）。 */
    const val MIN_FOLLICULAR_DAYS = 2L

    /** 动态推算保底：黄体期最短天数（医学共识黄体期波动 11~17 天，压缩仍在合理区间）。 */
    const val MIN_LUTEAL_DAYS = 11L

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
     * 与周期均值同口径：不足 2 条记录视为数据不满足测算要求，返回 null（回落手动值）。
     */
    fun averagePeriodDays(periodDaysDesc: List<Int>): Int? {
        if (periodDaysDesc.size < 2) return null
        val sample = periodDaysDesc.take(AVG_WINDOW)
        return Math.round(sample.sum().toDouble() / sample.size).toInt()
    }

    /** 预测下一次经期首日 = 已知首日 + 周期天数。 */
    fun nextStartAfter(lastStartEpochDay: Long, cycleDays: Int): Long =
        lastStartEpochDay + cycleDays

    /** 排卵日 = 预测下次经期首日 − 14 天（黄体期固定近似）。 */
    fun ovulationDay(nextStartEpochDay: Long): Long =
        nextStartEpochDay - LUTEAL_DAYS

    /**
     * 动态排卵日：标准口径为下次经期首日 − 14。
     * 经期较长挤占卵泡期时排卵日自动后移（卵泡期保底 MIN_FOLLICULAR_DAYS 天，
     * 黄体期最短 MIN_LUTEAL_DAYS 天——仍在医学共识 11~17 天区间内），
     * 避免「经期一长，卵泡期直接消失、经期后立刻排卵」的怪象。
     */
    fun effectiveOvulationDay(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): Long {
        val standard = nextStartEpochDay - LUTEAL_DAYS
        // 卵泡期保底 2 天：排卵日不得早于经期结束次日 + 保底天数
        val earliest = startEpochDay + periodDays + MIN_FOLLICULAR_DAYS
        // 黄体期最短 11 天：排卵日不得晚于下次经期首日 − 11
        val latest = nextStartEpochDay - MIN_LUTEAL_DAYS
        return maxOf(standard, earliest).coerceAtMost(latest)
    }

    /** 一次经期的月经期区间 [首日, 首日 + periodDays − 1]。 */
    fun periodRange(startEpochDay: Long, periodDays: Int): LongRange =
        startEpochDay..(startEpochDay + periodDays - 1)

    /**
     * 动态排卵期窗口：默认排卵日前 5 ～ 后 1 天；经期过后空间不足时窗口自动收窄
     * （保证窗口之前仍留有至少 MIN_FOLLICULAR_DAYS 天卵泡期）。
     */
    fun ovulationWindow(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): LongRange {
        val ovu = effectiveOvulationDay(startEpochDay, periodDays, nextStartEpochDay)
        val periodEnd = startEpochDay + periodDays - 1
        val before = OVULATION_BEFORE
            .coerceAtMost(ovu - periodEnd - MIN_FOLLICULAR_DAYS - 1)
            .coerceAtLeast(0L)
        return (ovu - before)..(ovu + OVULATION_AFTER)
    }

    /** 黄体期区间 [动态排卵日, 下次经期首日 − 1]（下次经期当天回到月经期）。 */
    fun lutealRange(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): LongRange =
        effectiveOvulationDay(startEpochDay, periodDays, nextStartEpochDay)..(nextStartEpochDay - 1)

    /** 卵泡期区间 [月经期结束次日, 排卵期窗口前一日]。 */
    fun follicularRange(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): LongRange {
        val from = startEpochDay + periodDays
        val to = ovulationWindow(startEpochDay, periodDays, nextStartEpochDay).first - 1
        return if (from <= to) from..to else LongRange.EMPTY
    }

    /** 当前处于哪个阶段（相对下一次预测经期）。 */
    fun phaseOf(todayEpochDay: Long, lastStartEpochDay: Long, periodDays: Int, cycleDays: Int): Phase {
        val nextStart = nextStartAfter(lastStartEpochDay, cycleDays)
        return when {
            todayEpochDay in periodRange(lastStartEpochDay, periodDays) -> Phase.PERIOD
            todayEpochDay in ovulationWindow(lastStartEpochDay, periodDays, nextStart) -> Phase.OVULATION
            todayEpochDay in lutealRange(lastStartEpochDay, periodDays, nextStart) -> Phase.LUTEAL
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
        // 锚点 = 最后一个不晚于目标日的记录（含其持续天数）；记录都在未来时向前虚拟推算
        var anchorStart = logsDesc.lastOrNull { it.first <= epochDay }?.first
        var anchorPd = logsDesc.lastOrNull { it.first <= epochDay }?.second
        if (anchorStart == null || anchorPd == null) {
            val first = logsDesc.last()
            val k = (first.first - epochDay + cycleDays - 1) / cycleDays
            anchorStart = first.first - k * cycleDays
            anchorPd = first.second
        } else {
            while (anchorStart + cycleDays <= epochDay) anchorStart += cycleDays
        }
        val nextStart = anchorStart + cycleDays
        return when {
            epochDay in ovulationWindow(anchorStart, anchorPd, nextStart) -> Phase.OVULATION
            epochDay in lutealRange(anchorStart, anchorPd, nextStart) -> Phase.LUTEAL
            else -> Phase.FOLLICULAR
        }
    }

    /**
     * 四阶段天数分段（月经期/卵泡期/排卵期/黄体期），总和 = 周期天数。
     * 圆环分段与日历着色共用同一套动态推算，保证两个视图一致。
     */
    fun phaseSegments(startEpochDay: Long, periodDays: Int, cycleDays: Int): List<Int> {
        val nextStart = startEpochDay + cycleDays
        val window = ovulationWindow(startEpochDay, periodDays, nextStart)
        // 病态输入（超长经期+超短周期）下窗口可能落在经期区间内，clamp 保证分段有序
        val ovuFirst = maxOf(window.first, startEpochDay + periodDays)
        val ovuLast = maxOf(window.last, ovuFirst).coerceAtMost(startEpochDay + cycleDays - 1)
        val period = periodDays
        val follicular = (ovuFirst - startEpochDay - periodDays).toInt().coerceAtLeast(0)
        val ovulation = (ovuLast - ovuFirst + 1).toInt().coerceAtLeast(0)
        val luteal = (cycleDays - period - follicular - ovulation).coerceAtLeast(0)
        return listOf(period, follicular, ovulation, luteal)
    }

    enum class Phase(val label: String) {
        PERIOD("月经期"),
        FOLLICULAR("卵泡期"),
        OVULATION("排卵期"),
        LUTEAL("黄体期")
    }
}
