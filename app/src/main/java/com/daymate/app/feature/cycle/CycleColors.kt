package com.ayaka7452.daymate.feature.cycle

import androidx.compose.ui.graphics.Color

/**
 * 周期阶段的固定配色（日历记事 + 周期管家共用）。
 *
 * 为什么不用主题色：主题 primary 会随 accent 切换（蓝/绿/橙/紫），
 * 一旦用户选到 purple，经期点就会和「倒数事件点」（主题青蓝）撞色。
 * 阶段色是**语义色**（经期=紫、排卵=红），必须跨主题稳定，故写死。
 *
 * - [Period] 经期紫 #8E24AA：与 accent 里的 purple（#6A1B9A）同系但稍亮，兼顾协调与区分。
 * - [Ovulation] 排卵红 #B3261E：沿用「排卵=红」的既定语义（M3 error 红，与紫/青蓝都拉得开）。
 */
object CycleColors {
    val Period = Color(0xFF8E24AA)
    val Ovulation = Color(0xFFB3261E)
}
