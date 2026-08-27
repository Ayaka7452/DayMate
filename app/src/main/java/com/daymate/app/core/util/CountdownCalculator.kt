package com.daymate.app.core.util

import java.time.LocalDate

object CountdownCalculator {

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
}
