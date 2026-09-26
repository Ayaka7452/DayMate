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
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
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
    // 用 remember 固定 Flow 实例：否则每次重组都会新建 Flow，collectAsState 反复取消/重建观察者
    // （编辑保存返回后可能漏掉本次变更，与 HomeScreen 同一处理）
    val event by remember(eventId) { container.eventRepository.observeById(eventId) }
        .collectAsState(initial = null)
    val folders by remember { container.folderRepository.observeAll() }
        .collectAsState(initial = emptyList())
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
                title = { Text(Tr.s(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Tr.s(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = Tr.s(R.string.common_edit))
                    }
                }
            )
        }
    ) { padding ->
        val e = event
        when {
            e != null -> {
                // 用文件夹自己的 emoji（与主页/文件夹列表一致），缺省回落到默认文件夹图标
                val f = folders.firstOrNull { it.id == e.folderId }
                DetailContent(e, f?.let { "${it.icon ?: "📁"} ${it.name}" }, Modifier.padding(padding))
            }
            exists == false -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    Tr.s(R.string.detail_not_found),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            // else：Flow 首帧未到，留白
        }
    }
}

@Composable
// folderLabel 已带文件夹自己的 emoji（调用方拼好），此处不再补硬编码图标
private fun DetailContent(e: EventEntity, folderLabel: String?, modifier: Modifier = Modifier) {
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
        // 跟随节日的假期段内（如中秋、国庆连休中）覆盖为「假期第 x 天」口径
        val holidayDayN = remember(e.linkedFestival, LocalDate.now().toEpochDay()) {
            e.linkedFestival?.takeIf { it.isNotBlank() }
                ?.let { container.festivalRepository.holidayDayIndexOf(it, LocalDate.now()) }
        }
        val cd = countdownDisplay(e, holidayDayN)
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
            Tr.s(R.string.detail_target_date),
            date.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd))) +
                " · " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag(Tr.s(R.string.locale_tag)))
        )
        InfoRow(
            Tr.s(R.string.repeat_label),
            when {
                // 存的是锚定名，展示按当前语言译一遍
                e.linkedFestival != null -> Tr.s(
                    R.string.detail_follow_festival,
                    com.ayaka7452.daymate.data.festival.HolidayNames.displayLinked(e.linkedFestival.orEmpty())
                )
                e.repeatRule == CountdownCalculator.REPEAT_WEEKLY -> Tr.s(R.string.repeat_weekly)
                e.repeatRule == CountdownCalculator.REPEAT_MONTHLY -> Tr.s(R.string.repeat_monthly)
                e.repeatRule == CountdownCalculator.REPEAT_YEARLY -> Tr.s(R.string.repeat_yearly)
                else -> Tr.s(R.string.repeat_none)
            }
        )
        if (e.refDays != null && e.refDays > 0) {
            InfoRow(Tr.s(R.string.detail_ref_value), "${e.refDays} ${refUnitLabel(e.displayUnit)}")
        }
        if (folderLabel != null) InfoRow(Tr.s(R.string.detail_folder), folderLabel)
        if (e.isPinned) InfoRow(Tr.s(R.string.detail_pinned), Tr.s(R.string.detail_pinned_yes))
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
    CountdownCalculator.UNIT_MONTH -> Tr.s(R.string.unit_months)
    CountdownCalculator.UNIT_YEAR -> Tr.s(R.string.unit_years)
    else -> Tr.s(R.string.unit_days)
}

/** 详情页大数字：按显示单位取整数，不足一个单位时逐级退回（与 formatCountdown 规则一致）。 */
private data class CountdownDisplay(val number: String, val unit: String, val caption: String)

private fun countdownDisplay(e: EventEntity, holidayDayN: Int? = null): CountdownDisplay {
    // 假期段内（跟随节日）：大数字即假期第几天，caption 同文案（不显示「今天/已过去」）
    if (holidayDayN != null) {
        return CountdownDisplay(
            number = "$holidayDayN",
            unit = Tr.s(R.string.unit_days),
            caption = Tr.s(R.string.unit_holiday_day_n, holidayDayN)
        )
    }
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
    // 统一显示正数：天按绝对值，月/年因 Period 已按过去方向计算本就为正；
    // 是否已过由下方 caption（距离目标日期 / 目标日期已过去）明确提示
    var n = if (isFuture) diffDays else -diffDays
    var u = Tr.s(R.string.unit_days)
    when (e.displayUnit) {
        CountdownCalculator.UNIT_YEAR -> when {
            period.years > 0 -> { n = period.years.toLong(); u = Tr.s(R.string.unit_years) }
            totalMonths > 0 -> { n = totalMonths; u = Tr.s(R.string.unit_months) }
        }
        CountdownCalculator.UNIT_MONTH -> if (totalMonths > 0) { n = totalMonths; u = Tr.s(R.string.unit_months) }
    }
    // 已过且有对照值时数字区显示 X/N（对照值单位跟随显示单位）
    val number = if (!isFuture && hasRef) "$n / ${e.refDays}" else "$n"
    val caption = when {
        diffDays == 0L -> Tr.s(R.string.detail_today)
        isFuture -> Tr.s(R.string.detail_until)
        else -> Tr.s(R.string.detail_past)
    }
    return CountdownDisplay(number = number, unit = u, caption = caption)
}
