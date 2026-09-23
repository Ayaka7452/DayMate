package com.ayaka7452.daymate.feature.common

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.R
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
    const val BOTTOM = "bottom"
}

/**
 * 行内「…」菜单里的四个重排项（上移 / 下移 / 移到顶部 / 移到底部）。
 * 主页的事件行、文件夹行与 Vault 的事件行、文件夹行共用，避免同一段菜单代码复制四份。
 * [dismissMenu] 在每次点击后收起菜单。
 */
@Composable
fun ReorderMenuItems(onReorder: (String) -> Unit, dismissMenu: () -> Unit) {
    DropdownMenuItem(
        modifier = Modifier.heightIn(min = 64.dp),
        text = { Text(stringResource(R.string.reorder_up)) },
        onClick = { dismissMenu(); onReorder(ReorderActions.UP) }
    )
    DropdownMenuItem(
        modifier = Modifier.heightIn(min = 64.dp),
        text = { Text(stringResource(R.string.reorder_down)) },
        onClick = { dismissMenu(); onReorder(ReorderActions.DOWN) }
    )
    DropdownMenuItem(
        modifier = Modifier.heightIn(min = 64.dp),
        text = { Text(stringResource(R.string.reorder_top)) },
        onClick = { dismissMenu(); onReorder(ReorderActions.TOP) }
    )
    DropdownMenuItem(
        modifier = Modifier.heightIn(min = 64.dp),
        text = { Text(stringResource(R.string.reorder_bottom)) },
        onClick = { dismissMenu(); onReorder(ReorderActions.BOTTOM) }
    )
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
