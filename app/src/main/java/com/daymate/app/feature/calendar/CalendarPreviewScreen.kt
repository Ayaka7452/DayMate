package com.ayaka7452.daymate.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.i18n.LocaleWrap
import com.ayaka7452.daymate.core.util.CycleCalculator
import com.ayaka7452.daymate.core.util.LunarCalendar
import com.ayaka7452.daymate.feature.cycle.AddNoteDialog
import com.ayaka7452.daymate.feature.cycle.CycleDayDetail
import com.ayaka7452.daymate.feature.cycle.DeleteNotesDialog
import com.ayaka7452.daymate.feature.cycle.EditNoteDialog
import com.ayaka7452.daymate.feature.common.FestivalBadge
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * 日历记事（主页菜单入口）：
 *  - 整月日历，一格一意：日号 + 农历/节日次行小字 + 法定节假日/调休补班底色（绿=休/橙=班）
 *    + 数字下一枚倒数日点（一天只一枚，颜色跟事件）+ 右上角小点标当天有周期管家记录；
 *  - 详情栏常驻：未选中任何一天时显示今天的信息，选中后显示选中日（左右换天带方向滑动动画），
 *    备注管理完全复用周期管家那一套（CycleDayDetail + 三个记录弹窗）；
 *  - 倒数日只取主表 events（Vault 是独立私密空间，不上墙）。循环/节日跟随事件的
 *    targetDateEpochDay 已由 rollForwardRepeating 在启动/跨天时滚到当前显示目标日，这里直接取用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarPreviewScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now().toEpochDay() }

    // 月份偏移：0 = 本月。翻月后顶栏「今天」可一键回到当前月
    var monthOffset by remember { mutableStateOf(0) }
    val month = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }

    // ===== 数据（与周期管家同一套口径） =====
    val events by container.eventRepository.observeAll().collectAsState(initial = emptyList())
    val logs by container.cycleRepository.observeAll().collectAsState(initial = emptyList())
    val notes by container.cycleNoteRepository.observeAll().collectAsState(initial = emptyList())
    val cycleManual by container.settingsRepository.cycleDays
        .collectAsState(initial = CycleCalculator.DEFAULT_CYCLE_DAYS)
    val cycleAuto by container.settingsRepository.cycleCycleAuto.collectAsState(initial = true)
    val cycleDays = container.cycleRepository.effectiveCycleDays(logs, cycleManual, cycleAuto)
    // 详情栏显示开关：与周期管家共用同一组设置
    val showNoteTags by container.settingsRepository.cycleShowNotes.collectAsState(initial = false)
    val showPrediction by container.settingsRepository.cycleShowPrediction.collectAsState(initial = true)

    // 节假日数据：remember 里同步读一次缓存（就近预载，防首帧跳动——铁律 13）。
    // 年份缺缓存时整月无角标，底部图例区给出「数据未下载」提示。
    val festivalByDate = remember(month) {
        container.festivalRepository
            .daysOfYears(setOf(month.year))
            .filter { it.date.year == month.year }
            .associateBy { it.date.toEpochDay() }
    }
    val hasFestivalData = remember(month) {
        container.festivalRepository.cachedYears().contains(month.year)
    }

    // 选中日（null = 未选中，详情栏显示今天）；弹窗状态与周期管家同构
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    var showAddNote by remember { mutableStateOf(false) }
    var deletingNote by remember { mutableStateOf<Long?>(null) } // 删除确认针对的日期
    var editingNote by remember { mutableStateOf<com.ayaka7452.daymate.data.db.CycleNoteEntity?>(null) }

    // 当月每天的目标日期 → 第一枚倒数日点的颜色（一天只显示一枚点，多了也不堆）。
    // null = 事件未自定义颜色，绘制处再回落到主题色（remember 块内不能取 MaterialTheme）
    val dotColorByDay = remember(events, month) {
        val map = mutableMapOf<Long, Color?>()
        for (e in events) {
            val d = e.targetDateEpochDay
            if (YearMonth.from(LocalDate.ofEpochDay(d)) == month && !map.containsKey(d)) {
                map[d] = e.color?.let { Color(it) }
            }
        }
        map
    }
    // 有周期管家日常记录的日期（右上角一枚小点，与周期管家日历同一套视觉语言）
    val noteDays = remember(notes) { notes.map { it.dateEpochDay }.toSet() }

    // 农历只在中文环境下显示（农历本身没有自然的外语译法，其他语言留白更干净）
    val showLunar = remember { LocaleWrap.locale().language == "zh" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            monthOffset = 0
                            selectedDay = null
                        },
                        enabled = monthOffset != 0 || selectedDay != null
                    ) { Text(stringResource(R.string.calendar_today)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 月份导航（与周期管家日历同一套：左右箭头 + YM 居中）
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { monthOffset -= 1 }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cycle_prev_month)
                    )
                }
                Text(
                    month.format(LocaleWrap.dateFormatter(R.string.date_pattern_ym)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { monthOffset += 1 }) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cycle_next_month)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // 星期表头（周一开始，与周期管家一致）
            Row(Modifier.fillMaxWidth()) {
                val weekLocale = LocaleWrap.locale()
                (1..7).map {
                    DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, weekLocale)
                }.forEach { d ->
                    Text(
                        d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // 日期网格（YearMonth 经 atDay() 落到具体 LocalDate 再取星期/epochDay）
            val leading = month.atDay(1).dayOfWeek.value - 1 // 周一=1 → 前导空格数
            val daysInMonth = month.lengthOfMonth()
            val rows = (leading + daysInMonth + 6) / 7
            Column(Modifier.fillMaxWidth()) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val idx = r * 7 + c
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.9f)
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                            val dayNum = idx - leading + 1
                                if (dayNum in 1..daysInMonth) {
                                    val date = month.atDay(dayNum)
                                    val epochDay = date.toEpochDay()
                                    val festival = festivalByDate[epochDay]
                                    val dow = date.dayOfWeek
                                    val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
                                    val isToday = epochDay == today
                                    val isSelected = selectedDay == epochDay
                                    val dotColor = dotColorByDay[epochDay]
                                    val hasNote = noteDays.contains(epochDay)

                                    val offGreen = Color(0xFF1E8E3E)
                                    val makeupOrange = Color(0xFFE8710A)
                                    val bg = when {
                                        // 休/班底色与角标同源（绿=休 / 橙=班），淡底不抢日号
                                        festival?.isOffDay == true -> offGreen.copy(alpha = 0.10f)
                                        festival != null -> makeupOrange.copy(alpha = 0.10f)
                                        else -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    1.6.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(8.dp)
                                                ) else Modifier
                                            )
                                            .clickable { selectedDay = epochDay },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                dayNum.toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = if (isWeekend && !isToday) 0.55f else 1f
                                                )
                                            )
                                            // 次行小字：法定节日名（随休/班配色）→ 农历节日名 → 农历日。
                                            // 没有农历需求（非中文环境）且当天无节日时用空位补齐，点行高不跳动
                                            if (festival != null) {
                                                Text(
                                                    festival.name,
                                                    fontSize = 8.sp,
                                                    lineHeight = 9.sp,
                                                    maxLines = 1,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (festival.isOffDay) offGreen else makeupOrange
                                                )
                                            } else if (showLunar) {
                                                Text(
                                                    LunarCalendar.labelText(date),
                                                    fontSize = 8.sp,
                                                    lineHeight = 9.sp,
                                                    maxLines = 1,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                )
                                            } else {
                                                Spacer(Modifier.height(9.dp))
                                            }
                                            // 倒数日目标点：一天只一枚，颜色跟随首个事件
                                            if (dotColor != null) {
                                                Box(
                                                    Modifier
                                                        .size(5.dp)
                                                        .background(dotColor, CircleShape)
                                                )
                                            } else {
                                                Spacer(Modifier.height(5.dp))
                                            }
                                        }
                                        // 当天有周期管家记录：右上角一枚小点（与周期管家日历同款）
                                        if (hasNote) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 3.dp, end = 3.dp)
                                                    .size(4.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                                        CircleShape
                                                    )
                                            )
                                        }
                                        // 休/班角标压在格子右上角（与节日卡、小组件同一套配色）；
                                        // 有记录小点时角标内收让位，避免叠在一起
                                        if (festival != null) {
                                            Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                                                FestivalBadge(isOffDay = festival.isOffDay)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 图例：休 / 班 / 倒数日；年份无缓存时提示去下载
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                LegendDot(Color(0xFF1E8E3E))
                Text(
                    stringResource(R.string.calendar_legend_off),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(12.dp))
                LegendDot(Color(0xFFE8710A))
                Text(
                    stringResource(R.string.calendar_legend_makeup),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(12.dp))
                LegendDot(MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.calendar_legend_event),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (!hasFestivalData) {
                Text(
                    stringResource(R.string.calendar_no_festival_data),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            // 详情栏常驻：未选中显示今天，选中显示选中日；换天走方向滑动动画
            val detailDay = selectedDay ?: today
            AnimatedContent(
                targetState = detailDay,
                transitionSpec = {
                    val spec = tween<IntOffset>(260, easing = FastOutSlowInEasing)
                    val fade = tween<Float>(180, easing = FastOutSlowInEasing)
                    // 新日期在旧日期右边（更晚）→ 内容从右滑入、旧内容向左滑出；更早则反向
                    val forward = targetState > initialState
                    if (forward) {
                        (slideInHorizontally(spec) { it } + fadeIn(fade)) togetherWith
                            (slideOutHorizontally(spec) { -it } + fadeOut(fade))
                    } else {
                        (slideInHorizontally(spec) { -it } + fadeIn(fade)) togetherWith
                            (slideOutHorizontally(spec) { it } + fadeOut(fade))
                    }
                },
                label = "calendar_detail_slide"
            ) { day ->
                CycleDayDetail(
                    day = day,
                    logs = logs,
                    dayNotes = notes.filter { it.dateEpochDay == day },
                    cycleDays = cycleDays,
                    today = today,
                    showNoteTags = showNoteTags,
                    showPrediction = showPrediction,
                    onAdd = { showAddNote = true },
                    onEdit = { editingNote = it },
                    onDelete = { deletingNote = day }
                )
            }
        }
    }

    // ===== 弹窗（与周期管家同构）；未选中任何一天时登记/删除都落在今天 =====
    val day = selectedDay ?: today
    if (showAddNote) {
        AddNoteDialog(
            day = day,
            onDismiss = { showAddNote = false },
            onConfirm = { newNotes ->
                scope.launch { container.cycleNoteRepository.addAll(newNotes) }
                showAddNote = false
            }
        )
    }
    editingNote?.let { note ->
        EditNoteDialog(
            note = note,
            onDismiss = { editingNote = null },
            onConfirm = { updated ->
                scope.launch { container.cycleNoteRepository.update(updated) }
                editingNote = null
            }
        )
    }
    deletingNote?.let { d ->
        DeleteNotesDialog(
            day = d,
            dayNotes = notes.filter { it.dateEpochDay == d },
            onDismiss = { deletingNote = null },
            onConfirm = { ids ->
                scope.launch { container.cycleNoteRepository.deleteByIds(ids) }
                deletingNote = null
            }
        )
    }
}

/** 图例小圆点。 */
@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(8.dp)
            .background(color, CircleShape)
    )
}
