package com.ayaka7452.daymate.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ayaka7452.daymate.R
import java.time.YearMonth

/**
 * 年月选择弹窗：管家日历与日历记事共用。
 *
 * 点顶部「xxxx年xx月」展开：年份用 ◀ ▶ 步进（无上限），下方 3×4 网格选月份，点中即回调并关闭。
 *
 * ⚠️ 刻意**不用** M3 的 `DatePicker`：它的进入模式只有「日历网格」与「手输文本」两种，
 * 想做「先年再月」的层级选择得自己画网格，反而更绕。
 *
 * [now] 只用来标出「本月」那一格；[selected] 是打开时的初始高亮。
 */
@Composable
fun MonthPickerDialog(
    selected: YearMonth,
    onDismiss: () -> Unit,
    onPick: (YearMonth) -> Unit,
    now: YearMonth = remember { YearMonth.now() }
) {
    var year by remember(selected) { mutableStateOf(selected.year) }
    val pickedMonth = if (year == selected.year) selected.monthValue else null

    // 用裸 Dialog 包一层：AlertDialog 会强塞正文边距与底部按钮行，
    // 这里的内容是自绘网格，边距自己掌控，所以不走 AlertDialog。
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
                    .navigationBarsPadding()
            ) {
                // 标题：说明这是「选年月」的面板，避免只有年份行时看不懂在选什么
                Text(
                    stringResource(R.string.month_picker_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                // ===== 年份步进 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { year -= 1 }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.month_picker_prev_year)
                        )
                    }
                    Text(
                        stringResource(R.string.month_picker_year, year),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { year += 1 }) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.month_picker_next_year)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // ===== 月份网格（3 列 × 4 行） =====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    MonthGrid(
                        highlightedMonth = pickedMonth,
                        isCurrentMonth = { m -> year == now.year && m == now.monthValue },
                        onPick = { m -> onPick(YearMonth.of(year, m)) }
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}

/**
 * 3 列月份网格：每格一个圆角方块，本月用细描边圈出。
 * 抽成独立组件是为了让「年月选择」与日历记事将来可能的「整月展开」共用同一套月份外观。
 */
@Composable
private fun MonthGrid(
    highlightedMonth: Int?,
    isCurrentMonth: (Int) -> Boolean,
    onPick: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until 3) {
                    val month = row * 3 + col + 1
                    MonthCell(
                        label = stringResource(R.string.month_picker_month, month),
                        selected = highlightedMonth == month,
                        isCurrent = isCurrentMonth(month),
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(month) }
                    )
                }
            }
        }
    }
}

/** 单个月份格：选中 = 实底主题色；本月 = 主题色描边（未选中时）。 */
@Composable
private fun MonthCell(
    label: String,
    selected: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else if (isCurrent) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (!selected && isCurrent) {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(50)
                    )
                } else Modifier
            )
            .background(container, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (selected || isCurrent) FontWeight.Medium else FontWeight.Normal
        )
    }
}
