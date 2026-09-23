@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ayaka7452.daymate.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.data.festival.FestivalDay
import com.ayaka7452.daymate.data.festival.HolidayNames
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FESTIVAL_OFF_GREEN = Color(0xFF1E8E3E)
private val FESTIVAL_WORK_ORANGE = Color(0xFFE8710A)

/** 预告专用淡蓝：只用于「明天起」的预告角标，与当天实况（绿=休 / 橙=班）明显区分。 */
private val FESTIVAL_PREVIEW_BLUE = Color(0xFF5C9CE6)

/**
 * 「休 / 班」小圆角角标：当天实况 绿=放假、橙=调休上班。
 * [preview]=true 表示这是「预告」（明天起的状态）而非当天实况——统一淡蓝底，
 * 且状态条文案会带「明天起/明日」字样，避免误读为今天已经开始休息/补班。
 */
@Composable
fun FestivalBadge(isOffDay: Boolean, modifier: Modifier = Modifier, preview: Boolean = false) {
    Surface(
        color = if (preview) FESTIVAL_PREVIEW_BLUE
        else if (isOffDay) FESTIVAL_OFF_GREEN else FESTIVAL_WORK_ORANGE,
        contentColor = Color.White,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            if (isOffDay) Tr.s(R.string.festival_badge_off) else Tr.s(R.string.festival_badge_work),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/**
 * 节日倒数卡片（列表顶部常驻）：
 *  - 未下载节假日数据 → 点击跳转设置下载（提示引导）；
 *  - 节日就是今天 → 标题「今天就是 XXX」、右侧大字「今天」（不再显示 0 天）；
 *  - 有数据 → 显示下一个放假节日与剩余天数，点击快捷创建「跟随节日」的倒数事件。
 * 底部状态条：今日休/班、明日补班预告或连休天数统一收在卡片内展示（原独立横幅已并入）。
 * 右侧角标为可配置 emoji（默认 ☀️）。
 */
@Composable
fun FestivalCountdownCard(
    hasData: Boolean,
    festival: FestivalDay?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeEmoji: String = "☀️",
    today: FestivalDay? = null,
    tomorrowMakeup: FestivalDay? = null,
    spanDays: Int = 0,
    spanRemaining: Int = 0,
    showSpanTotal: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // 比默认 surfaceVariant 更浅的一档灰
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        )
    ) {
        when {
            !hasData -> Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(Tr.s(R.string.festival_ui_no_data), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        Tr.s(R.string.festival_ui_go_download),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            festival == null -> Text(
                Tr.s(R.string.festival_ui_all_finished),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
            else -> {
                val isToday = festival.date == LocalDate.now()
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (isToday) {
                                Text(
                                    Tr.s(R.string.festival_ui_today_is, HolidayNames.display(festival)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    festival.date.format(
                                        DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_md))
                                    ),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            } else {
                                Text(
                                    Tr.s(R.string.festival_ui_next),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                val dateStr = festival.date.format(
                                    DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_md))
                                )
                                Text(
                                    "${HolidayNames.display(festival)} · $dateStr",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                Tr.s(R.string.festival_ui_create_event),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (isToday) {
                            // 今天就是节日：大字「今天」，不再显示 0 天
                            Text(
                                Tr.s(R.string.common_today),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            val days = festival.date.toEpochDay() - LocalDate.now().toEpochDay()
                            Row(horizontalArrangement = Arrangement.End) {
                                Text(
                                    days.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.alignByBaseline()
                                )
                                Text(
                                    Tr.s(R.string.unit_days),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.alignByBaseline()
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            badgeEmoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                        festivalStatusBar(
                            today, tomorrowMakeup, festival, spanDays, spanRemaining, showSpanTotal
                        )?.let { st ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FestivalBadge(st.isOffDay, preview = st.preview)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    st.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                        }
                }
            }
        }
    }
}

/** 底部状态条内容：角标（休/班，预告为淡蓝）+ 说明文字；无可展示内容时为 null（不画状态条）。 */
private data class FestivalStatus(val isOffDay: Boolean, val preview: Boolean, val text: String)

/**
 * 状态条取值（优先级从高到低）：
 *  1. 今天在假期里 → 绿「休」+ 天数口径（当天实况）：
 *     - 假期最后一天 → 「剩余 1 天，假期余额不足」；
 *     - 假期中段 → 只显示剩余「还剩 X 天」，或（开关开启时）「休息 X 天 · 还剩 Y 天」；
 *     - 假期第一天（剩余=总长）→ 「休息 X 天」。
 *  2. 今天是调休上班日 → 橙「班」+「今日XX调休补班」（当天实况）；
 *  3. 明天要补班 → 淡蓝「班」+「明日需补班：XX调休」（预告，提前一天）；
 *  4. 明天开始放假 → 淡蓝「休」+「明天起休息 X 天」（预告，提前一天，总长口径）。
 * 预告只在「明天」出现：不到日子不提前剧透，且淡蓝角标 + 「明天起/明日」文案
 * 与当天实况（绿/橙）双重区分。连休的「连」字不用——统一说「休息 X 天」。
 */
private fun festivalStatusBar(
    today: FestivalDay?,
    tomorrowMakeup: FestivalDay?,
    festival: FestivalDay,
    spanDays: Int,
    spanRemaining: Int,
    showSpanTotal: Boolean
): FestivalStatus? = when {
    today != null && today.isOffDay -> FestivalStatus(
        true, false,
        when {
            spanDays > 1 && spanRemaining <= 1 -> Tr.s(R.string.festival_ui_span_last)
            spanDays > 1 && spanRemaining < spanDays && showSpanTotal ->
                Tr.s(R.string.festival_ui_span_total_left, spanDays, spanRemaining)
            spanDays > 1 && spanRemaining < spanDays ->
                Tr.s(R.string.festival_ui_span_left, spanRemaining)
            else -> Tr.s(R.string.festival_ui_span_days, spanDays)
        }
    )
    today != null -> FestivalStatus(
        false, false, Tr.s(R.string.festival_banner_makeup, HolidayNames.display(today))
    )
    tomorrowMakeup != null -> FestivalStatus(
        false, true, Tr.s(R.string.festival_banner_makeup_tomorrow, HolidayNames.display(tomorrowMakeup))
    )
    festival.date == LocalDate.now().plusDays(1) && festival.isOffDay -> FestivalStatus(
        true, true, Tr.s(R.string.festival_ui_span_tomorrow, spanDays)
    )
    else -> null
}

/**
 * 最近的倒数日卡片（主页顶部卡片可切换为该模式）：
 * 显示最近的（剩余天数最少的未过期）倒数事件，点击进入编辑。
 * @param past true 表示没有任何未过期事件，此时展示最近一个已过去的事件。
 */
@Composable
fun EventCountdownCard(
    title: String,
    dateStr: String,
    days: Int,
    past: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (past) Tr.s(R.string.festival_ui_recent_expired) else Tr.s(R.string.festival_ui_recent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "$title · $dateStr",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    Tr.s(R.string.festival_ui_view_event),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Row(horizontalArrangement = Arrangement.End) {
                Text(
                    days.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (past) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    stringResource(R.string.unit_days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
    }
}
