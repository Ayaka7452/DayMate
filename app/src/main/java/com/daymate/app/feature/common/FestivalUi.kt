@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ayaka7452.daymate.feature.common

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

/** 「休 / 班」小圆角角标：绿色=放假，橙色=调休上班。 */
@Composable
fun FestivalBadge(isOffDay: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (isOffDay) FESTIVAL_OFF_GREEN else FESTIVAL_WORK_ORANGE,
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
 * 今日节日横幅：今天恰逢法定节假日或调休上班日时，在列表顶部显示。
 * 例：「今天 · 春节 · 休」「今日国庆节调休补班 · 班」。
 */
@Composable
fun FestivalTodayBanner(day: FestivalDay, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (day.isOffDay) {
                Text(Tr.s(R.string.common_today), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    // 走 HolidayNames 而不是 day.name：数据源给的名字是源国语言的，
                    // 中文用户切到日本节日时得看到「成人の日 → 成人节」而不是日文原名
                    HolidayNames.display(day),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // 调休补班日：单独句式点明「为哪个节日补班」，避免「国庆节（班）」读成「过国庆节的班」
                Text(
                    Tr.s(R.string.festival_banner_makeup, HolidayNames.display(day)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            FestivalBadge(day.isOffDay)
        }
    }
}

/**
 * 下一节日倒数卡片（列表顶部常驻）：
 *  - 未下载节假日数据 → 点击跳转设置下载（提示引导）；
 *  - 有数据 → 显示下一个放假节日与剩余天数，点击快捷创建「跟随节日」的倒数事件。
 * 右侧角标为可配置 emoji（默认 ☀️）：卡片只展示放假节日，无需「休/班」标记。
 */
@Composable
fun FestivalCountdownCard(
    hasData: Boolean,
    festival: FestivalDay?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeEmoji: String = "☀️"
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
            else -> Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Tr.s(R.string.festival_ui_next),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    val dateStr = festival.date.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_md)))
                    Text(
                        "${HolidayNames.display(festival)} · $dateStr",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        Tr.s(R.string.festival_ui_create_event),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
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
                Spacer(Modifier.width(10.dp))
                Text(
                    badgeEmoji,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
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
