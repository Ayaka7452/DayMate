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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.util.CountdownCalculator
import com.ayaka7452.daymate.data.db.EventEntity
import com.ayaka7452.daymate.data.festival.FestivalRepository
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 详情页渲染所需的全部字段。
 *
 * 主表事件与 Vault 事件（解密后）字段几乎一致，抽取成这个中立数据类后，
 * 两边共用同一套渲染与同一套倒计时口径——否则「主空间显示 3 天、Vault 显示 4 天」这类
 * 不一致迟早会出现。
 */
data class EventDetailData(
    val title: String,
    val note: String?,
    val targetDateEpochDay: Long,
    val refDays: Int? = null,
    val displayUnit: String? = null,
    val repeatRule: String? = null,
    val linkedFestival: String? = null,
    /** 「按时间倒数」的目标时刻（当天分钟数），null = 按日期倒数。 */
    val endMinuteOfDay: Int? = null,
    val isPinned: Boolean = false,
    /** 所在文件夹的展示名（已含图标 emoji），null = 不显示该行。 */
    val folderLabel: String? = null
)

/** 主表事件 → 详情数据。 */
fun EventEntity.toDetailData(folderLabel: String?): EventDetailData = EventDetailData(
    title = title,
    note = note,
    targetDateEpochDay = targetDateEpochDay,
    refDays = refDays,
    displayUnit = displayUnit,
    repeatRule = repeatRule,
    linkedFestival = linkedFestival,
    endMinuteOfDay = endMinuteOfDay,
    isPinned = isPinned,
    folderLabel = folderLabel
)

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
    // 假期显示总长开关：开启时假期段内详情 caption 附加「· 剩余X天」（剩余含今天，与节日卡同口径）
    val showSpanTotal by remember { container.settingsRepository.holidaySpanTotal }
        .collectAsState(initial = false)
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
                EventDetailBody(
                    data = e.toDetailData(f?.let { "${it.icon ?: "📁"} ${it.name}" }),
                    festivalRepo = container.festivalRepository,
                    showSpanTotal = showSpanTotal,
                    modifier = Modifier.padding(padding)
                )
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

/**
 * 详情正文（主空间与 Vault 共用）。
 *
 * 「按时间倒数」的事件会自建一个 ticker：不足 3 分钟时每秒刷新，否则 30 秒一次——
 * 秒级刷新只在真正需要看秒的时候跑，不会在列表/详情常驻时白耗电。
 */
@Composable
fun EventDetailBody(
    data: EventDetailData,
    festivalRepo: FestivalRepository? = null,
    showSpanTotal: Boolean = false,
    modifier: Modifier = Modifier
) {
    // ticker 只在时间模式存在；endMinuteOfDay 变化时重启
    var now by remember(data.endMinuteOfDay) { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(data.endMinuteOfDay, data.targetDateEpochDay) {
        val minute = data.endMinuteOfDay ?: return@LaunchedEffect
        while (true) {
            now = LocalDateTime.now()
            val c = CountdownCalculator.timedCountdown(data.targetDateEpochDay, minute, now)
            val fast = kotlin.math.abs(c.seconds) < CountdownCalculator.TIMED_TICK_SECONDS
            kotlinx.coroutines.delay(if (fast) 1_000L else 30_000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            data.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (!data.note.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                data.note!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(24.dp))

        // ===== 倒计时主视觉：大数字 + 单位 =====
        // 跟随节日的假期段内（如中秋、国庆连休中）覆盖为「假期第 x 天」口径
        val holidayDayN = remember(data.linkedFestival, LocalDate.now().toEpochDay()) {
            data.linkedFestival?.takeIf { it.isNotBlank() }
                ?.let { festivalRepo?.holidayDayIndexOf(it, LocalDate.now()) }
        }
        // 「假期显示总长」开启时附假期剩余天数（含今天口径，与节日卡一致）；仅剩最后一天（=1）时不附加
        val holidayRemaining = remember(holidayDayN, showSpanTotal, LocalDate.now().toEpochDay()) {
            if (holidayDayN != null && showSpanTotal) festivalRepo?.offDayRemainingLength(LocalDate.now()) else null
        }
        val cd = countdownDisplay(data, holidayDayN, holidayRemaining, now)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (data.endMinuteOfDay != null && holidayDayN == null) {
                // 按时间倒数：把「天」和「时/分/秒」拆成两行——上面是主题色大字，下面是淡蓝色小一号的字。
                // （原来大数字取「天」、副行再写「还剩 x 时 x 分」，看着像两套并列的读数，其实是拼起来才算完。）
                val p4 = CountdownCalculator.timedParts4(data.targetEpochDay, data.endMinuteOfDay, now)
                val timeText = "${p4.hours}${stringResource(R.string.unit_hours_short)}" +
                    "${p4.minutes}${stringResource(R.string.unit_minutes_short)}" +
                    "${p4.seconds}${stringResource(R.string.unit_seconds_short)}"
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
                Spacer(Modifier.height(2.dp))
                // 淡蓝稍大：primary 的浅色变体在浅底上既有颜色倾向又足够跳，
                // 小字号（bodyMedium）在 64sp 大数字下面几乎看不见。
                Text(
                    timeText,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    cd.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
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
        }

        Spacer(Modifier.height(28.dp))

        // ===== 信息行 =====
        val date = LocalDate.ofEpochDay(data.targetDateEpochDay)
        // 按时间倒数时日期与时刻拼成一行（两个独立信息行会读成两件不相干的事）
        val dateText = date.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd))) +
            " · " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag(Tr.s(R.string.locale_tag)))
        InfoRow(
            Tr.s(R.string.detail_target_date),
            data.endMinuteOfDay?.let { "$dateText ${minuteText(it)}" } ?: dateText
        )
        InfoRow(
            Tr.s(R.string.repeat_label),
            when {
                // 存的是锚定名，展示按当前语言译一遍
                data.linkedFestival != null -> Tr.s(
                    R.string.detail_follow_festival,
                    com.ayaka7452.daymate.data.festival.HolidayNames.displayLinked(data.linkedFestival.orEmpty())
                )
                data.repeatRule == CountdownCalculator.REPEAT_WEEKLY -> Tr.s(R.string.repeat_weekly)
                data.repeatRule == CountdownCalculator.REPEAT_MONTHLY -> Tr.s(R.string.repeat_monthly)
                data.repeatRule == CountdownCalculator.REPEAT_YEARLY -> Tr.s(R.string.repeat_yearly)
                else -> Tr.s(R.string.repeat_none)
            }
        )
        if (data.refDays != null && data.refDays > 0) {
            InfoRow(Tr.s(R.string.detail_ref_value), "${data.refDays} ${refUnitLabel(data.displayUnit)}")
        }
        if (data.folderLabel != null) InfoRow(Tr.s(R.string.detail_folder), data.folderLabel)
        if (data.isPinned) InfoRow(Tr.s(R.string.detail_pinned), Tr.s(R.string.detail_pinned_yes))
        Spacer(Modifier.height(16.dp))
    }
}

/** 目标时刻展示文本：24 小时制 HH:mm。 */
internal fun minuteText(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

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

private fun countdownDisplay(
    data: EventDetailData,
    holidayDayN: Int? = null,
    holidayRemaining: Int? = null,
    now: LocalDateTime = LocalDateTime.now()
): CountdownDisplay {
    // 假期段内（跟随节日）：大数字即假期第几天，caption 同文案（不显示「今天/已过去」）；
    // 「假期显示总长」开启且假期还有多于 1 天时，附加「· 剩余X天」
    if (holidayDayN != null) {
        val cap = if (holidayRemaining != null && holidayRemaining > 1) {
            Tr.s(R.string.unit_holiday_day_n, holidayDayN) + " · " +
                Tr.s(R.string.detail_holiday_remaining, holidayRemaining)
        } else {
            Tr.s(R.string.unit_holiday_day_n, holidayDayN)
        }
        return CountdownDisplay(
            number = "$holidayDayN",
            unit = Tr.s(R.string.unit_days),
            caption = cap
        )
    }
    // 按时间倒数：走精确到分的分段逻辑（天+小时 → 分钟 → 秒），已过一侧镜像
    data.endMinuteOfDay?.let { minute ->
        val t = CountdownCalculator.timedCountdown(data.targetDateEpochDay, minute, now)
        return CountdownDisplay(number = t.number, unit = t.unit, caption = t.caption)
    }
    val today = now.toLocalDate()
    val diffDays = data.targetDateEpochDay - today.toEpochDay()
    val isFuture = diffDays >= 0
    val period = if (isFuture) {
        Period.between(today, LocalDate.ofEpochDay(data.targetDateEpochDay))
    } else {
        Period.between(LocalDate.ofEpochDay(data.targetDateEpochDay), today)
    }
    val totalMonths = period.years * 12L + period.months
    val hasRef = data.refDays != null && data.refDays > 0

    // 主数字：优先按显示单位取整，不足一个单位退回更小单位
    // 统一显示正数：天按绝对值，月/年因 Period 已按过去方向计算本就为正；
    // 是否已过由下方 caption（距离目标日期 / 目标日期已过去）明确提示
    var n = if (isFuture) diffDays else -diffDays
    var u = Tr.s(R.string.unit_days)
    when (data.displayUnit) {
        CountdownCalculator.UNIT_YEAR -> when {
            period.years > 0 -> { n = period.years.toLong(); u = Tr.s(R.string.unit_years) }
            totalMonths > 0 -> { n = totalMonths; u = Tr.s(R.string.unit_months) }
        }
        CountdownCalculator.UNIT_MONTH -> if (totalMonths > 0) { n = totalMonths; u = Tr.s(R.string.unit_months) }
    }
    // 已过且有对照值时数字区显示 X/N（对照值单位跟随显示单位）
    val number = if (!isFuture && hasRef) "$n / ${data.refDays}" else "$n"
    val caption = when {
        diffDays == 0L -> Tr.s(R.string.detail_today)
        isFuture -> Tr.s(R.string.detail_until)
        else -> Tr.s(R.string.detail_past)
    }
    return CountdownDisplay(number = number, unit = u, caption = caption)
}
