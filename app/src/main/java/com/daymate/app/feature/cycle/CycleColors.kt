package com.ayaka7452.daymate.feature.cycle

import androidx.compose.ui.graphics.Color

/**
 * 周期阶段的固定配色（日历记事 + 周期管家共用）。
 *
 * 为什么不用主题色：主题 primary 会随 accent 切换（蓝/绿/橙/紫），
 * 一旦用户选到 purple，经期点就会和「倒数事件点」（主题青蓝）撞色。
 * 阶段色是**语义色**（经期=蓝、排卵=灰红），必须跨主题稳定，故写死。
 *
 * ⚠️ 这两个色**只用于阶段本身**（圆环分段、日历格子底色、点位、图例、阶段标签底色）。
 * 选中框/今日框等**交互态**属于界面语言，一律用主题 primary，不要从这里取色
 * （v1.18.9 曾误用 periodColor 画选中框，框会跟着阶段色变形变色）。
 *
 * - [Period] 经期雾蓝 #5C7CBA：灰调蓝，与 [Ovulation] 灰红配成一套莫兰迪；
 *   避开主题青蓝（#00668C）以免与事件点混淆。
 * - [Ovulation] 排卵灰红 #B3554E：原正红 #B3261E 压饱和加灰调后的结果（用户嫌太红）。
 */
object CycleColors {
    val Period = Color(0xFF5C7CBA)
    val Ovulation = Color(0xFFB3554E)
}
