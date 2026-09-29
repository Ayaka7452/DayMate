package com.ayaka7452.daymate.feature.cycle

import androidx.compose.ui.graphics.Color

/**
 * 周期阶段的固定配色（日历记事 + 周期管家共用）。
 *
 * 为什么不用主题色：主题 primary 会随 accent 切换（蓝/绿/橙/紫），
 * 一旦用户选到 purple，经期点就会和「倒数事件点」（主题青蓝）撞色。
 * 阶段色是**语义色**（经期=紫、排卵=红），必须跨主题稳定，故写死。
 *
 * - [Period] 经期灰紫 #8E6FA8：莫兰迪灰调，比 Material Purple 700（#8E24AA）压饱和降明度，
 *   原本那个在小圆点上像荧光紫、太扎眼（用户 2026-09-29 反馈「好丑」）。
 * - [Ovulation] 排卵红 #B3261E：沿用「排卵=红」的既定语义（M3 error 红，与紫/青蓝都拉得开）。
 */
object CycleColors {
    val Period = Color(0xFF8E6FA8)
    val Ovulation = Color(0xFFB3261E)
}
