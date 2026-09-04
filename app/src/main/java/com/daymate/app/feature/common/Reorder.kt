package com.ayaka7452.daymate.feature.common

import com.ayaka7452.daymate.core.util.CountdownCalculator

/** 排序模式常量（对应 SettingsRepository.defaultSort 的取值）。 */
object SortModes {
    const val REMAINING_ASC = "remaining_asc"
    const val REMAINING_DESC = "remaining_desc"
    const val MANUAL = "manual"
}

/** 行内「…」菜单的移动动作。 */
object ReorderActions {
    const val UP = "up"
    const val DOWN = "down"
    const val TOP = "top"
}

/**
 * 把 [from] 位置的元素移动到 [to]（越界自动钳制，from == to 时不动）。
 * 只改本地列表；调用方负责随后把新顺序持久化到 sortIndex。
 */
fun <T> MutableList<T>.moveItem(from: Int, to: Int) {
    if (from !in indices) return
    val target = to.coerceIn(0, lastIndex)
    if (target == from) return
    add(target, removeAt(from))
}

/** 按「上移/下移/移到顶部/移到底部」动作计算目标下标。 */
fun targetIndexForAction(index: Int, size: Int, action: String): Int = when (action) {
    ReorderActions.UP -> index - 1
    ReorderActions.DOWN -> index + 1
    ReorderActions.TOP -> 0
    ReorderActions.BOTTOM -> size - 1
    else -> index
}.coerceIn(0, (size - 1).coerceAtLeast(0))

/**
 * 按排序模式重排事件（remaining_asc/remaining_desc 按剩余天数；manual/未知值保持原序）。
 * 用 daysUntil 与列表显示口径一致（已过为负）。
 */
fun <T> sortEventsForDisplay(events: List<T>, mode: String, daysUntil: (T) -> Long): List<T> =
    when (mode) {
        SortModes.REMAINING_ASC -> events.sortedBy(daysUntil)
        SortModes.REMAINING_DESC -> events.sortedByDescending(daysUntil)
        else -> events
    }

/** 事件剩余天数（与列表显示一致的口径）。 */
fun eventDaysUntil(targetDateEpochDay: Long): Long =
    CountdownCalculator.daysUntil(targetDateEpochDay)
