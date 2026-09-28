package com.ayaka7452.daymate.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.i18n.LocaleWrap
import com.ayaka7452.daymate.core.util.CycleCalculator
import com.ayaka7452.daymate.core.util.LunarCalendar
import com.ayaka7452.daymate.core.util.NoteCatalog
import com.ayaka7452.daymate.data.db.CycleNoteEntity
import com.ayaka7452.daymate.feature.cycle.DeleteNotesDialog
import com.ayaka7452.daymate.feature.cycle.describeDay
import com.ayaka7452.daymate.feature.cycle.phaseChipColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** 休（法定节假日）底色，与节日卡/小组件同一套绿。 */
private val OFF_GREEN = Color(0xFF1E8E3E)
/** 班（调休补班）底色，与节日卡/小组件同一套橙。 */
private val MAKEUP_ORANGE = Color(0xFFE8710A)

/**
 * 日历记事（主页菜单入口）：整月一格一格，与周期管家同一套方块语言——
 *  - 每天都有一个浅色方块打底（管家靠底色分期，这里默认同款浅底，只有休/班才换成绿/橙）；
 *  - 绿底 = 法定节假日、橙底 = 调休补班（只靠颜色表达，不放角标；图例说明，点上去详情栏写明是哪个节日）；
 *  - 日号 + 农历/节日次行小字 + 数字下一枚倒数日点（一天只一枚，颜色跟事件）+ 右上角小点标当天有记事；
 *  - 选中/今日粗细框与周期管家同一套；左右拖动整块月历翻月（与管家同款手势）；
 *  - 详情栏常驻在日历正下方（未选中显示今天），是「标题 + 备注」的日常记事，不是经期记录。
 *
 * 经期信息是否外露由周期管家设置里的两个开关决定，**默认都不显示**：
 * 经期阶段/预测走 [显示经期信息]，管家登记的自定义记录走 [显示经期记录]。
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

    // ===== 数据 =====
    val events by container.eventRepository.observeAll().collectAsState(initial = emptyList())
    val logs by container.cycleRepository.observeAll().collectAsState(initial = emptyList())
    val notes by container.cycleNoteRepository.observeAll().collectAsState(initial = emptyList())
    val cycleManual by container.settingsRepository.cycleDays
        .collectAsState(initial = CycleCalculator.DEFAULT_CYCLE_DAYS)
    val cycleAuto by container.settingsRepository.cycleCycleAuto.collectAsState(initial = true)
    val cycleDays = container.cycleRepository.effectiveCycleDays(logs, cycleManual, cycleAuto)
    // 经期信息外露开关（周期管家设置里调，默认都关）
    val showCycleNotes by container.settingsRepository.cycleShowNotes.collectAsState(initial = false)
    val showPeriod by container.settingsRepository.cycleShowPrediction.collectAsState(initial = false)

    // 节假日数据：remember 里同步读一次缓存（就近预载，防首帧跳动——铁律 13）
    val festivalByDate = remember(month) {
        container.festivalRepository
            .daysOfYears(setOf(month.year))
            .filter { it.date.year == month.year }
            .associateBy { it.date.toEpochDay() }
    }
    val hasFestivalData = remember(month) {
        container.festivalRepository.cachedYears().contains(month.year)
    }

    // 本页自己写的记事 vs 经期管家的记录：同表按 category 分流，两边互不串味
    val ownNotes = remember(notes) { notes.filter { it.category == NoteCatalog.CALENDAR_KEY } }
    val trackerNotes = remember(notes) { notes.filter { it.category != NoteCatalog.CALENDAR_KEY } }
    val ownNoteDays = remember(ownNotes) { ownNotes.map { it.dateEpochDay }.toSet() }
    // 管家的记录只有在开关打开时才参与渲染（默认关：性行为等标签不上日历）
    val trackerNoteDays = remember(trackerNotes, showCycleNotes) {
        if (showCycleNotes) trackerNotes.map { it.dateEpochDay }.toSet() else emptySet()
    }

    // 选中日（null = 未选中，详情栏显示今天）
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    var showAddNote by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<CycleNoteEntity?>(null) }
    var deletingDay by remember { mutableStateOf<Long?>(null) }

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

    // 农历只在中文环境下显示（农历本身没有自然的外语译法，其他语言留白更干净）
    val showLunar = remember { LocaleWrap.locale().language == "zh" }

    // ===== 左右拖动翻月（与周期管家日历同一套：跟手平移 + 过阈值翻页） =====
    val slideX = remember { Animatable(0f) }
    var gridWidth by remember { mutableStateOf(0f) }
    var flipping by remember { mutableStateOf(false) }

    fun flipMonth(dir: Int) {
        if (flipping) return
        flipping = true
        scope.launch {
            slideX.animateTo(-dir * gridWidth, tween(140, easing = FastOutSlowInEasing))
            monthOffset += dir
            slideX.snapTo(dir * gridWidth)
            slideX.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
            flipping = false
        }
    }

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
                IconButton(onClick = { if (!flipping) monthOffset -= 1 }) {
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
                IconButton(onClick = { if (!flipping) monthOffset += 1 }) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cycle_next_month)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // 星期表头 + 日期网格（一起跟手平移）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()                 // 平移出界的部分裁掉，免得在屏幕边缘留残影
                    .onSizeChanged { gridWidth = it.width.toFloat() }
                    .graphicsLayer { translationX = slideX.value }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragCancel = {
                                scope.launch { slideX.animateTo(0f, tween(160, easing = FastOutSlowInEasing)) }
                            },
                            onDragEnd = {
                                val w = gridWidth
                                val dx = slideX.value
                                if (!flipping && w > 0f && kotlin.math.abs(dx) >= w * 0.2f) {
                                    // 向左滑 = 翻下个月，向右滑 = 翻回上个月
                                    flipMonth(if (dx < 0f) 1 else -1)
                                } else {
                                    scope.launch { slideX.animateTo(0f, tween(160, easing = FastOutSlowInEasing)) }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!flipping) {
                                    change.consume()
                                    val next = (slideX.value + dragAmount).coerceIn(-gridWidth, gridWidth)
                                    scope.launch { slideX.snapTo(next) }
                                }
                            }
                        )
                    }
            ) {
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
                                        .aspectRatio(0.9f),
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
                                        val hasOwnNote = ownNoteDays.contains(epochDay)
                                        val hasTrackerNote = trackerNoteDays.contains(epochDay)

                                        // 常态就是一枚浅色方块（与周期管家「每天都有底色」同一套视觉）；
                                        // 休/班换绿/橙——只靠颜色表达，不放角标挡日期。
                                        val bg = when {
                                            festival?.isOffDay == true -> OFF_GREEN.copy(alpha = 0.18f)
                                            festival != null -> MAKEUP_ORANGE.copy(alpha = 0.18f)
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(1.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(bg)
                                                .clickable { selectedDay = epochDay },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 2.dp, vertical = 3.dp),
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
                                                // 次行小字：法定节日名（随休绿/班橙）→ 农历
                                                val sub = when {
                                                    festival != null -> festival.name
                                                    showLunar -> LunarCalendar.labelText(date)
                                                    else -> null
                                                }
                                                if (sub != null) {
                                                    Text(
                                                        sub,
                                                        fontSize = 8.sp,
                                                        lineHeight = 9.sp,
                                                        maxLines = 1,
                                                        fontWeight = if (festival != null) FontWeight.Medium else FontWeight.Normal,
                                                        color = when {
                                                            festival?.isOffDay == true -> OFF_GREEN
                                                            festival != null -> MAKEUP_ORANGE
                                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                        }
                                                    )
                                                } else {
                                                    Spacer(Modifier.height(9.dp))
                                                }
                                                // 倒数日目标点：一天只一枚，颜色跟随首个事件
                                                if (dotColor != null) {
                                                    Box(
                                                        Modifier
                                                            .size(5.dp)
                                                            .background(
                                                                dotColor ?: MaterialTheme.colorScheme.primary,
                                                                CircleShape
                                                            )
                                                    )
                                                } else {
                                                    Spacer(Modifier.height(5.dp))
                                                }
                                            }
                                            // 当天有记录：右上角一枚小点。自己写的记事用主色，
                                            // 管家记录（开关打开时）用中性灰，两者不会混淆。
                                            if (hasOwnNote || hasTrackerNote) {
                                                Box(
                                                    Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(top = 4.dp, end = 4.dp)
                                                        .size(4.dp)
                                                        .background(
                                                            if (hasOwnNote) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                                            CircleShape
                                                        )
                                                )
                                            }
                                            // 选中/今日边框与周期管家同一套：粗主色框 + 白衬，一眼可分。
                                            // 描边走外框、内容已内缩 3dp，不会压到日期数字上。
                                            if (isSelected) {
                                                Box(
                                                    Modifier
                                                        .matchParentSize()
                                                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp))
                                                )
                                                Box(
                                                    Modifier
                                                        .matchParentSize()
                                                        .padding(3.dp)
                                                        .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                                                )
                                            } else if (isToday) {
                                                Box(
                                                    Modifier
                                                        .matchParentSize()
                                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp))
                                                )
                                                Box(
                                                    Modifier
                                                        .matchParentSize()
                                                        .padding(1.5.dp)
                                                        .border(1.5.dp, Color.White, RoundedCornerShape(7.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 图例：休 / 班 / 倒数日（休班用小方块呼应底色，倒数日用圆点呼应格内圆点）
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                LegendSwatch(OFF_GREEN)
                Text(
                    stringResource(R.string.calendar_legend_off),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(12.dp))
                LegendSwatch(MAKEUP_ORANGE)
                Text(
                    stringResource(R.string.calendar_legend_makeup),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(12.dp))
                LegendDot()
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

            // 详情栏紧跟日历下方（不再塞弹性空白把它顶到页面底部）；换天走方向滑动动画
            Spacer(Modifier.height(12.dp))
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
                val festival = festivalByDate[day]
                CalendarDayDetail(
                    day = day,
                    today = today,
                    lunar = if (showLunar) LunarCalendar.labelText(LocalDate.ofEpochDay(day)) else null,
                    // 经期信息只在开关打开时才算、才显示（默认关）
                    phase = if (showPeriod) describeDay(day, logs, cycleDays, today) else null,
                    festival = festival?.let {
                        if (it.isOffDay) {
                            stringResource(R.string.calendar_detail_holiday, it.name) to OFF_GREEN
                        } else {
                            stringResource(R.string.calendar_detail_makeup, it.name) to MAKEUP_ORANGE
                        }
                    },
                    notes = ownNotes.filter { it.dateEpochDay == day } +
                        (if (showCycleNotes) trackerNotes.filter { it.dateEpochDay == day } else emptyList()),
                    canDelete = ownNotes.any { it.dateEpochDay == day },
                    onAdd = { showAddNote = true },
                    onEdit = { editingNote = it },
                    onDelete = { deletingDay = day }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // ===== 弹窗：未选中任何一天时，新增/删除都落在今天 =====
    val day = selectedDay ?: today
    if (showAddNote) {
        CalendarNoteDialog(
            day = day,
            editing = null,
            onDismiss = { showAddNote = false },
            onConfirm = { title, extra ->
                scope.launch {
                    container.cycleNoteRepository.addAll(
                        listOf(
                            CycleNoteEntity(
                                dateEpochDay = day,
                                category = NoteCatalog.CALENDAR_KEY,
                                presetKey = null,
                                label = title,
                                note = extra,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    )
                }
                showAddNote = false
            }
        )
    }
    editingNote?.let { note ->
        CalendarNoteDialog(
            day = note.dateEpochDay,
            editing = note,
            onDismiss = { editingNote = null },
            onConfirm = { title, extra ->
                scope.launch { container.cycleNoteRepository.update(note.copy(label = title, note = extra)) }
                editingNote = null
            }
        )
    }
    deletingDay?.let { d ->
        DeleteNotesDialog(
            day = d,
            dayNotes = ownNotes.filter { it.dateEpochDay == d } +
                (if (showCycleNotes) trackerNotes.filter { it.dateEpochDay == d } else emptyList()),
            // 只能删自己写的记事：管家的记录即便显示出来也只是「看」
            deletable = { it.category == NoteCatalog.CALENDAR_KEY },
            onDismiss = { deletingDay = null },
            onConfirm = { ids ->
                scope.launch { container.cycleNoteRepository.deleteByIds(ids) }
                deletingDay = null
            }
        )
    }
}

/**
 * 选中日（或今天）的详情卡：日期 + 农历 / 经期 chip（可选）/ 节日说明 / 记事列表 / 增删入口。
 *
 * 这里是**日常记事**的视角——标题 + 备注，不是经期记录。经期阶段与管家登记的自定义记录
 * 都由用户在周期管家里显式打开开关后才出现，默认一条都不显示。
 */
@Composable
private fun CalendarDayDetail(
    day: Long,
    today: Long,
    /** 农历文案；null = 不显示（非中文环境）。 */
    lunar: String?,
    /** 周期阶段 (标签, 副说明)；null = 经期信息不显示。 */
    phase: Pair<String, String>?,
    /** 节日说明（法定节假日 / 调休补班）及其配色；null = 普通日子。 */
    festival: Pair<String, Color>?,
    notes: List<CycleNoteEntity>,
    /** 删除按钮是否可用（只删自己写的记事）。 */
    canDelete: Boolean,
    onAdd: () -> Unit,
    onEdit: (CycleNoteEntity) -> Unit,
    onDelete: () -> Unit
) {
    val date = LocalDate.ofEpochDay(day)
    val dateText = date.format(LocaleWrap.dateFormatter(R.string.date_pattern_md)) + " " +
        date.dayOfWeek.getDisplayName(TextStyle.FULL, LocaleWrap.locale())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (day == today) stringResource(R.string.cycle_today_prefix, dateText) else dateText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (lunar != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            lunar,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
                if (phase != null) {
                    val (chipBg, chipFg) = phaseChipColors(phase.first)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(chipBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(phase.first, style = MaterialTheme.typography.labelSmall, color = chipFg)
                    }
                }
            }
            // 经期副说明（距下次经期 / 周期第几天）：只有开关打开、且确有记录时才有意义
            if (phase != null && phase.second.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    phase.second,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (festival != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(festival.second, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        festival.first,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.calendar_notes_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            if (notes.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_no_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            } else {
                notes.forEach { n ->
                    CalendarNoteRow(
                        note = n,
                        editable = n.category == NoteCatalog.CALENDAR_KEY,
                        onEdit = { onEdit(n) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // 层级约定：实心 = 会动数据的执行动作；空心 = 次级/破坏性动作
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.calendar_add_note), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.calendar_delete_note), maxLines = 1)
                }
            }
        }
    }
}

/** 一行记事：圆点 + 标题 +（可选）备注 +（可选）修改入口。管家记录只读、不给修改入口。 */
@Composable
private fun CalendarNoteRow(
    note: CycleNoteEntity,
    editable: Boolean,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (editable) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                    CircleShape
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                NoteCatalog.displayLabel(note.presetKey, note.label),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val extra = note.note
            if (!extra.isNullOrBlank()) {
                Text(
                    extra,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (editable) {
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.common_edit),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

/**
 * 记事弹窗：标题（必填）+ 备注（可选）。
 *
 * 刻意**不给任何预设项**——日历记事是「开会 / 买菜 / 缴费」这类日常事务，
 * 经期出血、性生活那些是周期管家的语义，混进来只会让记事变得不知所谓。
 */
@Composable
private fun CalendarNoteDialog(
    day: Long,
    /** null = 新增；非 null = 修改这条。 */
    editing: CycleNoteEntity?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, note: String?) -> Unit
) {
    // 以记录 id 作 remember key：连续修改不同记录时，上一次的草稿必须重来一遍
    var titleText by remember(editing?.id) { mutableStateOf(editing?.label.orEmpty()) }
    var noteText by remember(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }
    val canSave = titleText.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editing == null) R.string.calendar_add_note else R.string.calendar_edit_note
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it.take(NoteCatalog.MAX_LABEL_LENGTH) },
                    label = { Text(stringResource(R.string.calendar_note_title)) },
                    placeholder = { Text(stringResource(R.string.calendar_note_title_ph)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(NoteCatalog.MAX_NOTE_LENGTH) },
                    label = { Text(stringResource(R.string.calendar_note_content)) },
                    placeholder = { Text(stringResource(R.string.calendar_note_content_ph)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 日期写在弹窗底部：标题里塞日期会让「改哪一天的记事」这件事要读两遍才看清
                Text(
                    LocalDate.ofEpochDay(day)
                        .format(LocaleWrap.dateFormatter(R.string.date_pattern_md)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onConfirm(titleText.trim(), noteText.trim().ifEmpty { null }) }
            ) { Text(stringResource(R.string.common_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** 图例小方块（休/班底色同款）。 */
@Composable
private fun LegendSwatch(color: Color) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(10.dp)
            .background(color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .border(1.dp, color, RoundedCornerShape(3.dp))
    )
}

/** 图例小圆点（倒数日目标点同款）。 */
@Composable
private fun LegendDot() {
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(8.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}
