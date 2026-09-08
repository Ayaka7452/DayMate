package com.ayaka7452.daymate.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.util.CountdownCalculator
import com.ayaka7452.daymate.data.db.EventEntity
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 事件详情页（只读）：点击列表行 / 桌面小组件进入，浏览而不误触编辑。
 * 右上角钢笔图标进入编辑表单；编辑保存返回后本页经 Room 失效通知自动刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    container: AppContainer,
    eventId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val event by container.eventRepository.observeById(eventId).collectAsState(initial = null)
    val folders by container.folderRepository.observeAll().collectAsState(initial = emptyList())
    // 一次性存在性检查：查不到（或已在回收站）直接退出，避免停留在空详情页
    var exists by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(eventId) {
        exists = container.eventRepository.getById(eventId)?.let { !it.isDeleted } == true
    }
    LaunchedEffect(exists) {
        if (exists == false) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("事件详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                }
            )
        }
    ) { padding ->
        val e = event
        when {
            e != null -> DetailContent(e, folders.firstOrNull { it.id == e.folderId }?.name,
                Modifier.padding(padding))
            exists == false -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "事件不存在或已移入回收站",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            // else：Flow 首帧未到，留白
        }
    }
}

@Composable
private fun DetailContent(e: EventEntity, folderName: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            e.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (!e.note.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                e.note!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(24.dp))

        // ===== 倒计时主视觉：大数字 + 单位 =====
        val cd = countdownDisplay(e)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    cd.number,
                    fontSize = 64.sp,
                    lineHeight = 68.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    cd.unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                cd.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(28.dp))

        // ===== 信息行 =====
        val date = LocalDate.ofEpochDay(e.targetDateEpochDay)
        InfoRow(
            "目标日期",
            date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) +
                " · " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
        )
        InfoRow(
            "循环",
            when {
                e.linkedFestival != null -> "跟随节日「${e.linkedFestival}」"
                e.repeatRule == CountdownCalculator.REPEAT_WEEKLY -> "每周"
                e.repeatRule == CountdownCalculator.REPEAT_MONTHLY -> "每月"
                e.repeatRule == CountdownCalculator.REPEAT_YEARLY -> "每年"
                else -> "不循环"
            }
        )
        if (e.refDays != null && e.refDays > 0) {
            InfoRow("对照值", "${e.refDays} ${refUnitLabel(e.displayUnit)}")
        }
        if (folderName != null) InfoRow("所在文件夹", "📂 $folderName")
        if (e.isPinned) InfoRow("置顶", "已置顶")
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

private fun refUnitLabel(unit: String?): String = when (unit) {
    CountdownCalculator.UNIT_MONTH -> "个月"
    CountdownCalculator.UNIT_YEAR -> "年"
    else -> "天"
}

/** 详情页大数字：按显示单位取整数，不足一个单位时逐级退回（与 formatCountdown 规则一致）。 */
private data class CountdownDisplay(val number: String, val unit: String, val caption: String)

private fun countdownDisplay(e: EventEntity): CountdownDisplay {
    val today = LocalDate.now()
    val diffDays = e.targetDateEpochDay - today.toEpochDay()
    val isFuture = diffDays >= 0
    val period = if (isFuture) {
        Period.between(today, LocalDate.ofEpochDay(e.targetDateEpochDay))
    } else {
        Period.between(LocalDate.ofEpochDay(e.targetDateEpochDay), today)
    }
    val totalMonths = period.years * 12L + period.months
    val hasRef = e.refDays != null && e.refDays > 0

    // 主数字：优先按显示单位取整，不足一个单位退回更小单位
    var n = diffDays
    var u = "天"
    when (e.displayUnit) {
        CountdownCalculator.UNIT_YEAR -> when {
            period.years > 0 -> { n = period.years.toLong(); u = "年" }
            totalMonths > 0 -> { n = totalMonths; u = "个月" }
        }
        CountdownCalculator.UNIT_MONTH -> if (totalMonths > 0) { n = totalMonths; u = "个月" }
    }
    // 已过且有对照值时数字区显示 X/N（对照值单位跟随显示单位）
    val number = if (!isFuture && hasRef) "$n / ${e.refDays}" else "$n"
    val caption = when {
        diffDays == 0L -> "就是今天"
        isFuture -> "距离目标日期"
        else -> "目标日期已过去"
    }
    return CountdownDisplay(number = number, unit = u, caption = caption)
}
