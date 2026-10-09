package com.ayaka7452.daymate.core.util

import com.ayaka7452.daymate.R

/**
 * 周期管家：经期/卵泡期/排卵期/黄体期推算。
 *
 * 医学口径（日历法，严谨性说明）：
 *  - 黄体期：排卵后到下次月经来潮，医学共识近似为固定 14 天
 *    （实际个体波动 11~17 天，且同一人不同周期也有波动，此处取标准近似值）。
 *  - 排卵日 ≈ 预测下次经期首日 − 14 天；周期偏短/经期偏长时按下方两个保底后移。
 *  - **着色窗口 ≠ 受孕窗口**（2026-10-09 修，用户报告「经期刚结束就排卵」）：
 *      · 排卵期**着色**只取 [OVULATION_BEFORE, OVULATION_AFTER]（各 1 天）——排卵日前 5 天
 *        仍是**卵泡期**（卵子还没排）。早先着色也用 5 天前，把整段卵泡期末梢挖成排卵色，
 *        周期偏短时卵泡期只剩 2 天，日历上读起来就是「经期一结束立刻排卵」。
 *      · 受孕窗口 [FERTILE_BEFORE, OVULATION_AFTER]（共 7 天）**只用于文案提示**，依据是
 *        精子在体内最长存活约 5 天、卵子排出后可受精约 24 小时。
 *      · 两者必须分开：既保住医学上正确的阶段着色，又不丢「易孕期」提示。
 *  - 月经期 = 经期首日 ～ 首日 + 持续天数 − 1；卵泡期 = 月经期结束后 ～ 排卵期着色前一日。
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

    /**
     * 相邻两次「经期」首日最小间隔（天）：不重叠但间隔不足该值时视为明显不合理。
     * 医学依据：FIGO 正常月经频发界限为周期 <24 天；考虑个体差异后，
     * 间隔 <15 天的两次出血基本不可能是两次独立经期（多为经间期/排卵期出血），
     * 故取 15 天作为「明显不合理」的提示阈值；15 天以上的短周期由提前 7 天规则覆盖。
     */
    const val MIN_PERIOD_INTERVAL_DAYS = 15

    /** 排卵期着色窗口：排卵日前 1 天 ～ 后 1 天（阶段口径，**不是**受孕窗口）。 */
    const val OVULATION_BEFORE = 1L
    const val OVULATION_AFTER = 1L

    /** 受孕窗口：排卵日前 5 天 ～ 后 1 天。**只给文案提示用，不参与日历着色**。 */
    const val FERTILE_BEFORE = 5L

    /** 着色口径下卵泡期最少天数（保底，避免「经期刚结束就排卵」）。 */
    const val MIN_FOLLICULAR_DAYS = 6L

    /** 着色口径下黄体期最短天数（医学共识黄体期波动 11~17 天）。 */
    const val MIN_LUTEAL_DAYS = 11L

    /** 相邻两次经期首日算出一个周期长度；不在 15~60 天内视为无效记录（漏记/异常周期）。 */
    fun cycleLengthBetween(prevStart: Long, nextStart: Long): Int? {
        val diff = (nextStart - prevStart).toInt()
        return if (diff in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS) diff else null
    }

    /**
     * 均值结果：**天数** + **实际参与计算的样本数**。
     *
     * [sampleCount] 供设置页动态显示「近 N 次平均」——此前直接把 [AVG_WINDOW]（写死的 3）
     * 塞进文案，用户只登记 2 期时也显示「近 3 次」，与实际不符（2026-10-07 用户报告）。
     *
     * ⚠️ 两个口径的样本含义**不同**，别混：
     *  - [cycleAverage]：样本 = **相邻两期的间隔数**（登记 2 期 → 1 个周期 → 显示「近 1 次」）
     *  - [periodAverage]：样本 = **登记条数**（登记 2 期 → 显示「近 2 次」）
     */
    data class Average(val days: Int, val sampleCount: Int)

    /**
     * 近 N 次（最多 3 次）实测周期均值：传入按日期降序的经期首日列表。
     * 不足 2 次记录时返回 null（无法计算均值）。
     */
    fun cycleAverage(startDaysDesc: List<Long>): Average? {
        if (startDaysDesc.size < 2) return null
        val lengths = mutableListOf<Int>()
        for (i in 0 until startDaysDesc.size - 1) {
            if (lengths.size >= AVG_WINDOW) break
            cycleLengthBetween(startDaysDesc[i + 1], startDaysDesc[i])?.let { lengths.add(it) }
        }
        if (lengths.isEmpty()) return null
        return Average(Math.round(lengths.sum().toDouble() / lengths.size).toInt(), lengths.size)
    }

    /**
     * 近 N 次（最多 3 次）记录的经期持续天数均值：传入按日期降序记录的 periodDays 列表。
     * 与周期均值同口径：不足 2 条记录视为数据不满足测算要求，返回 null（回落手动值）。
     */
    fun periodAverage(periodDaysDesc: List<Int>): Average? {
        if (periodDaysDesc.size < 2) return null
        val sample = periodDaysDesc.take(AVG_WINDOW)
        return Average(Math.round(sample.sum().toDouble() / sample.size).toInt(), sample.size)
    }

    /** 预测下一次经期首日 = 已知首日 + 周期天数。 */
    fun nextStartAfter(lastStartEpochDay: Long, cycleDays: Int): Long =
        lastStartEpochDay + cycleDays

    /**
     * 动态排卵日：标准口径为下次经期首日 − 14。
     * 周期偏短或经期偏长把卵泡期挤没时，排卵日自动后移——两个保底**都要把着色窗口
     * 自身的宽度算进去**，否则实际着色后阶段天数会少一天：
     *  - 卵泡期 = 经期结束次日 ～ ovu−OVULATION_BEFORE−1 ≥ MIN_FOLLICULAR_DAYS
     *    → ovu ≥ 经期结束次日 + MIN_FOLLICULAR_DAYS + OVULATION_BEFORE
     *  - 黄体期 = ovu+OVULATION_AFTER+1 ～ 下次经期−1 ≥ MIN_LUTEAL_DAYS
     *    → ovu ≤ 下次经期 − MIN_LUTEAL_DAYS − OVULATION_AFTER − 1
     * 两者冲突时（周期太短装不下）以黄体期保底优先——黄体期短于 11 天的影响（黄体功能不足）
     * 比卵泡期略短更大。
     */
    fun effectiveOvulationDay(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): Long {
        val standard = nextStartEpochDay - LUTEAL_DAYS
        val earliest = startEpochDay + periodDays + MIN_FOLLICULAR_DAYS + OVULATION_BEFORE
        val latest = nextStartEpochDay - MIN_LUTEAL_DAYS - OVULATION_AFTER - 1
        return maxOf(standard, earliest).coerceAtMost(latest)
    }

    /** 一次经期的月经期区间 [首日, 首日 + periodDays − 1]。 */
    fun periodRange(startEpochDay: Long, periodDays: Int): LongRange =
        startEpochDay..(startEpochDay + periodDays - 1)

    /**
     * 排卵期**着色**区间 [ovu − OVULATION_BEFORE, ovu + OVULATION_AFTER]。
     *
     * 只覆盖排卵日附近：再往前都是卵泡期（卵子尚未排出）。clamp 两个方向是为了
     * 病态输入（超长经期 + 超短周期）时不侵入经期、不越过下次经期前一日；
     * 此时返回的区间可能为空（lo > hi，Kotlin 的 `in` 判定自然为 false）。
     */
    fun ovulationPhaseRange(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): LongRange {
        val ovu = effectiveOvulationDay(startEpochDay, periodDays, nextStartEpochDay)
        val lo = maxOf(ovu - OVULATION_BEFORE, startEpochDay + periodDays)
        val hi = minOf(ovu + OVULATION_AFTER, nextStartEpochDay - 1)
        return lo..hi
    }

    /**
     * 受孕窗口 [ovu − FERTILE_BEFORE, ovu + OVULATION_AFTER]，共约 7 天。
     *
     * **只给文案提示用**（「排卵日 X · 窗口 Y ~ Z」），不参与日历着色——着色窄是刻意的，
     * 见类注释里「着色窗口 ≠ 受孕窗口」。
     */
    fun fertileWindow(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): LongRange {
        val ovu = effectiveOvulationDay(startEpochDay, periodDays, nextStartEpochDay)
        val lo = maxOf(ovu - FERTILE_BEFORE, startEpochDay + periodDays)
        val hi = minOf(ovu + OVULATION_AFTER, nextStartEpochDay - 1)
        return lo..hi
    }

    /** 黄体期区间 [排卵期着色结束次日, 下次经期首日 − 1]（下次经期当天回到月经期）。 */
    fun lutealRange(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): LongRange =
        (ovulationPhaseRange(startEpochDay, periodDays, nextStartEpochDay).last + 1)..(nextStartEpochDay - 1)

    /** 卵泡期区间 [月经期结束次日, 排卵期着色前一日]。 */
    fun follicularRange(startEpochDay: Long, periodDays: Int, nextStartEpochDay: Long): LongRange {
        val from = startEpochDay + periodDays
        val to = ovulationPhaseRange(startEpochDay, periodDays, nextStartEpochDay).first - 1
        return if (from <= to) from..to else LongRange.EMPTY
    }

    /** 当前处于哪个阶段（相对下一次预测经期）。 */
    fun phaseOf(todayEpochDay: Long, lastStartEpochDay: Long, periodDays: Int, cycleDays: Int): Phase {
        val nextStart = nextStartAfter(lastStartEpochDay, cycleDays)
        return when {
            todayEpochDay in periodRange(lastStartEpochDay, periodDays) -> Phase.PERIOD
            todayEpochDay in ovulationPhaseRange(lastStartEpochDay, periodDays, nextStart) -> Phase.OVULATION
            todayEpochDay in lutealRange(lastStartEpochDay, periodDays, nextStart) -> Phase.LUTEAL
            todayEpochDay in follicularRange(lastStartEpochDay, periodDays, nextStart) -> Phase.FOLLICULAR
            // 今天已在预测周期之外（下次经期理论上今天或之前来但还没登记）：
            // 若落在以 lastStart + periodDays 为终点的经期区间之后，视为黄体期延迟，仍按黄体期提示
            else -> Phase.LUTEAL
        }
    }

    /**
     * 推算任意日期所处阶段（日历视图着色用）。logsDesc：(经期首日, 该次持续天数) 列表，按首日降序。
     *  - **早于最早一次登记经期** → [Phase.NONE]（无数据区，画中性灰）
     *  - 落在任一**已登记**经期区间内（按该记录自身的持续天数）→ [Phase.PERIOD]
     *  - 否则以「最后一个不晚于该日的记录」为锚点按周期向后推算：
     *    经期区间 → [Phase.PREDICTED_PERIOD]（该来没登记），其余为排卵/黄体/卵泡
     *
     * ⚠️ **不再向前虚拟外推**（2026-10-07 修）：早于最早记录的日子若照常推算，会凭空造出一段
     * 「上一个周期」——用户明明一次都没登记，日历上最早记录之前却整块显示成经期深色，读起来
     * 就像「那几天来过月经」。没有记录就是没有记录，返回 [Phase.NONE] 交给 UI 画中性灰。
     */
    fun phaseOfAnyDay(
        epochDay: Long,
        logsDesc: List<Pair<Long, Int>>,
        cycleDays: Int
    ): Phase {
        if (logsDesc.isEmpty()) return Phase.FOLLICULAR
        // 早于最早一次登记：无数据区。记录都在未来（提前登记）时同样适用。
        if (epochDay < logsDesc.minOf { it.first }) return Phase.NONE
        for ((s, pd) in logsDesc) {
            if (epochDay in periodRange(s, pd)) return Phase.PERIOD
        }
        // 锚点 = 最后一个不晚于目标日的记录。目标日已 ≥ 最早记录，故必然存在（兜底走最早一条）。
        val anchor = logsDesc.lastOrNull { it.first <= epochDay } ?: logsDesc.last()
        var anchorStart = anchor.first
        val anchorPd = anchor.second
        while (anchorStart + cycleDays <= epochDay) anchorStart += cycleDays
        // 锚点推进后的预测周期：目标日落在锚点经期区间内 → 预测经期
        // （此前漏判，预测经期首日会被渲染成卵泡期/黄体期，日历上从未点亮）
        // 注意这里返回 PREDICTED_PERIOD 而非 PERIOD：UI 要能区分「实登记」与「预测」用不同颜色。
        if (epochDay in periodRange(anchorStart, anchorPd)) return Phase.PREDICTED_PERIOD
        val nextStart = anchorStart + cycleDays
        return when {
            epochDay in ovulationPhaseRange(anchorStart, anchorPd, nextStart) -> Phase.OVULATION
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
        val window = ovulationPhaseRange(startEpochDay, periodDays, nextStart)
        // 病态输入（超长经期+超短周期）下窗口可能落在经期区间内，clamp 保证分段有序
        val ovuFirst = maxOf(window.first, startEpochDay + periodDays)
        val ovuLast = maxOf(window.last, ovuFirst).coerceAtMost(startEpochDay + cycleDays - 1)
        val period = periodDays
        val follicular = (ovuFirst - startEpochDay - periodDays).toInt().coerceAtLeast(0)
        val ovulation = (ovuLast - ovuFirst + 1).toInt().coerceAtLeast(0)
        val luteal = (cycleDays - period - follicular - ovulation).coerceAtLeast(0)
        return listOf(period, follicular, ovulation, luteal)
    }

    enum class Phase(val labelRes: Int) {
        /**
         * 四阶段之外的「无数据」态：该日**早于最早一次登记经期**。
         *
         * 这一段是纯向前外推、没有任何记录支撑。照常着色成经期/阶段色会让用户误读成
         * 「那几天来过月经」（2026-10-07 用户报告）。日历上画中性灰，不参与任何推导。
         */
        NONE(R.string.cycle_no_records),
        /** 已登记经期：该日落在某条记录自身的经期区间内。 */
        PERIOD(R.string.cycle_phase_period),
        /**
         * 预测经期：按周期推算「该来但还没登记」的日子。
         *
         * 与 [PERIOD] **分开**是为了让日历能用浅色把它和实登记区分开——此前两者都返回 [PERIOD]，
         * 逾期未登记时这些日子 `epochDay <= today` 就被渲染成实心深色，读起来像「已经登记过了」
         * （2026-10-07 用户报告）。预测经期**不落库、不参与任何均值计算**，只是提示；
         * 点它可以就地补登记。
         */
        PREDICTED_PERIOD(R.string.cycle_predict_period),
        FOLLICULAR(R.string.cycle_phase_follicular),
        OVULATION(R.string.cycle_phase_ovulation),
        LUTEAL(R.string.cycle_phase_luteal)
    }
}
