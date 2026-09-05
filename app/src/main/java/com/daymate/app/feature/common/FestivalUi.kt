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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayaka7452.daymate.data.festival.FestivalDay
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
            if (isOffDay) "休" else "班",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/**
 * 今日节日横幅：今天恰逢法定节假日或调休上班日时，在列表顶部显示。
 * 例：「今天 · 春节 · 休」「今天 · 调休上班（春节）· 班」。
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
            Text("今天", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                day.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            FestivalBadge(day.isOffDay)
        }
    }
}

/**
 * 下一节日倒数卡片（列表顶部常驻）：
 *  - 未下载节假日数据 → 点击跳转设置下载（提示引导）；
 *  - 有数据 → 显示下一个放假节日与剩余天数，点击快捷创建「跟随节日」的倒数事件。
 */
@Composable
fun FestivalCountdownCard(
    hasData: Boolean,
    festival: FestivalDay?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
                    Text("节假日数据未下载", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "点击前往「设置 → 节假日数据」下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            festival == null -> Text(
                "数据中的法定节假日已全部结束，可到设置更新",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
            else -> Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "下一个节日",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    val dateStr = festival.date.format(DateTimeFormatter.ofPattern("M月d日"))
                    Text(
                        "${festival.name} · $dateStr",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "点击创建倒数事件",
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
                        "天",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline()
                    )
                }
                Spacer(Modifier.width(10.dp))
                FestivalBadge(festival.isOffDay)
            }
        }
    }
}
