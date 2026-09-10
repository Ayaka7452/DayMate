package com.ayaka7452.daymate.feature.cycle

import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.core.util.CycleCalculator
import com.ayaka7452.daymate.data.db.CycleLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DateFmt = DateTimeFormatter.ofPattern("M月d日")

/**
 * 周期管家：
 *  - 主视图：圆环周期图（月经期/卵泡期/排卵期/黄体期闭环）+ 当前位置 + 关键日期
 *  - 设置视图：登记/历史/周期与持续天数/密码保护/快捷事件（右上齿轮进入）
 *  - 密码保护默认关闭；开启后复用 Vault 密码与指纹验证（Vault 未设密码时先引导去设置）。
 */
@Composable
fun CycleScreen(
    container: AppContainer,
    onExit: () -> Unit
) {
    // 密码开关：首帧同步读一次偏好（DataStore 已被主题读取预热，走内存缓存），
    // 避免首帧按「未设密码」渲染内容、下一帧才切到密码门的闪现；后续仍跟随设置变化
    val initialPasswordEnabled = remember {
        runCatching {
            runBlocking { container.settingsRepository.cyclePasswordEnabled.first() }
        }.getOrDefault(false)
    }
    val passwordEnabled by container.settingsRepository.cyclePasswordEnabled
        .collectAsState(initial = initialPasswordEnabled)
    // 本次会话是否已通过验证。锁定态必须派生：collectAsState 首帧 initial=false，
    // 若直接 mutableStateOf(!passwordEnabled) 会在数据到达前把 unlocked 固定为 true，密码门永远不弹
    var unlockedByUser by remember { mutableStateOf(false) }
    val locked = passwordEnabled && !unlockedByUser
    var showSettings by remember { mutableStateOf(false) }
    // 设置子页是同 Activity 内的状态切换：返回手势先回到主视图，而不是退出功能
    BackHandler(enabled = showSettings) { showSettings = false }
    // 密码开关变化时即时生效：关闭即解锁；会话内重新开启需重新验证
    LaunchedEffect(passwordEnabled) {
        if (!passwordEnabled) unlockedByUser = false
    }

    // 防截屏/最近任务缩略图遮挡（与 Vault 一致）
    val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // 密码门 ⇄ 内容淡入淡出，解锁后不生硬跳变
    Crossfade(targetState = locked, label = "cycle_gate") { isLocked ->
        if (isLocked) {
            CycleUnlockGate(
                container = container,
                onUnlocked = { unlockedByUser = true },
                onExit = onExit
            )
        } else {
            // 主视图 ⇄ 设置页淡入淡出，避免生硬跳变
            Crossfade(targetState = showSettings, label = "cycle_pages") { settings ->
                if (settings) {
                    CycleSettingsScreen(
                        container = container,
                        onBack = { showSettings = false }
                    )
                } else {
                    CycleOverviewScreen(
                        container = container,
                        onExit = onExit,
                        onOpenSettings = { showSettings = true }
                    )
                }
            }
        }
    }
}

/** 登记/补记在不合理时间时（与已有记录重叠 / 比预测经期提前超过阈值）的二次确认信息。 */
private data class PendingSpecialLog(val startDay: Long, val days: Int, val reason: String)

/** 日期选择器共用：禁选未来日期——经期的开始不可能是未来（登记/补记/修订统一限制）。 */
@OptIn(ExperimentalMaterial3Api::class)
private val pastAndTodayOnly = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= LocalDate.now().toEpochDay() * 86400000L
}

// ============================ 主视图（圆环） ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleOverviewScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val logs by container.cycleRepository.observeAll().collectAsState(initial = emptyList())
    val cycleManual by container.settingsRepository.cycleDays.collectAsState(initial = CycleCalculator.DEFAULT_CYCLE_DAYS)
    val periodManual by container.settingsRepository.cyclePeriodDays.collectAsState(initial = CycleCalculator.DEFAULT_PERIOD_DAYS)
    val cycleAuto by container.settingsRepository.cycleCycleAuto.collectAsState(initial = true)
    val periodAuto by container.settingsRepository.cyclePeriodAuto.collectAsState(initial = true)
    // 生效参数：自动开启且数据足够时按近 3 次记录均值推算，否则回落到手动设置值
    val cycleDays = container.cycleRepository.effectiveCycleDays(logs, cycleManual, cycleAuto)
    val periodDays = container.cycleRepository.effectivePeriodDays(logs, periodManual, periodAuto)

    // 登记/补记在不合理时间的判断：返回提示文案（null=合理，直接保存）
    //  ① 与已有记录重叠：同一时段重复登记经期通常不合理
    //  ② 比预测下次经期提前超过 7 天（FIGO：相邻周期波动 ≤7~9 天属正常，超此范围可能为非经期出血）
    fun unreasonableLogReason(day: Long, days: Int): String? {
        val last = logs.firstOrNull()?.startDateEpochDay
            ?: return null
        if (logs.any {
                day <= it.startDateEpochDay + it.periodDays - 1 &&
                    day + days - 1 >= it.startDateEpochDay
            }
        ) {
            return "所选日期与已有经期记录重叠，同一时段重复登记经期通常不合理"
        }
        // 与任一真实经期记录首日间隔不足 15 天（不重叠）：两次「经期」间隔过短，
        // 基本不可能是两次独立经期，多为经间期出血。特殊情况记录（带备注的单日标记）不参与判断
        val tooClose = logs.filter { it.note == null }.firstOrNull {
            val gap = kotlin.math.abs(it.startDateEpochDay - day)
            gap in 1 until CycleCalculator.MIN_PERIOD_INTERVAL_DAYS
        }
        if (tooClose != null) {
            val gap = kotlin.math.abs(tooClose.startDateEpochDay - day)
            return "所选开始日期与已有经期记录（" + formatDate(tooClose.startDateEpochDay) + "）仅相差 " + gap +
                " 天：间隔不足 " + CycleCalculator.MIN_PERIOD_INTERVAL_DAYS +
                " 天的两次出血通常不是两次独立经期，可能是排卵期出血等非经期出血，建议咨询医生"
        }
        val next = CycleCalculator.nextStartAfter(last, cycleDays)
        if (day > last && day < next - CycleCalculator.EARLY_PERIOD_THRESHOLD_DAYS) {
            return "所选开始日期比预测下次经期（" + formatDate(next) + "）提前了 " + (next - day) +
                " 天（提前超过 " + CycleCalculator.EARLY_PERIOD_THRESHOLD_DAYS +
                " 天属异常出血范围），可能是排卵期出血等非经期出血，建议咨询医生"
        }
        return null
    }

    val today = LocalDate.now().toEpochDay()
    // 推算锚点只用真正的经期记录；特殊情况记录（带备注的单日标记）不作为「上次经期」
    val lastLog = logs.firstOrNull { it.note == null }
    val scope = rememberCoroutineScope()
    var showRegister by remember { mutableStateOf(false) }
    var showBackfill by remember { mutableStateOf(false) }
    var showTips by remember { mutableStateOf(false) }
    // 登记/补记与已有记录日期重叠时的二次确认（可能为非经期出血的特殊情况，可附备注）
    var pendingSpecial by remember { mutableStateOf<PendingSpecialLog?>(null) }
    // 手动结束本次经期的二次确认：避免误触，同时把「今天计为经期最后一天」的口径说清楚
    var showEndConfirm by remember { mutableStateOf(false) }
    // 默认视图：首帧同步读一次偏好（DataStore 在启动阶段已被主题等读取过，此处走内存缓存，耗时极短）。
    // 首帧即为正确视图，彻底避免「先空白/先圆环再切日历」的闪变；手动切换只改本页状态，不写回设置
    val initialCalendar = remember {
        runCatching {
            runBlocking { container.settingsRepository.cycleDefaultCalendar.first() }
        }.getOrDefault(false)
    }
    var manualCalendar by remember { mutableStateOf<Boolean?>(null) }
    val showCalendar = manualCalendar ?: initialCalendar
    val overdue = lastLog != null && today >= CycleCalculator.nextStartAfter(lastLog.startDateEpochDay, cycleDays)
    // 结束本次经期：仅当今天仍落在该次经期记录的区间内时提供入口；
    // 今天已越过记录结束日 → 经期按记录自动结束，同位置换成「修订上次经期」供微调
    // 结束本次经期：只对真正的经期记录响应；特殊情况记录（备注标记的单日出血）不参与
    val activeLog = logs.firstOrNull { it.note == null && it.startDateEpochDay <= today }
    val activeDiff = activeLog?.let { (today - it.startDateEpochDay + 1).toInt() }
    var editingLog by remember { mutableStateOf<CycleLogEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("周期管家") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // ===== 逾期提醒卡：已到预测经期日，引导登记实际日期 =====
            if (overdue && lastLog != null) {
                val predicted = CycleCalculator.nextStartAfter(lastLog.startDateEpochDay, cycleDays)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "已到预测经期日（" + formatDate(predicted) + "）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "请登记本次实际开始日期，推算会更准确",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { showRegister = true }) { Text("登记本次经期") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ===== 圆环周期图 / 日历视图 =====
            // 首帧即按默认视图渲染（偏好已在上方同步读取），无闪变
            // Crossfade 内部按 TopStart 摆放子项，必须包一层全宽居中 Box，否则切换瞬间圆环会在左侧闪现
            // animateContentSize 让圆环/日历高度差过渡平滑，下方内容跟随滑动而不是跳变
            Crossfade(
                targetState = showCalendar,
                modifier = Modifier.animateContentSize(),
                label = "cycle_view"
            ) { cal ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (cal) {
                        CycleCalendarMonth(
                            logs = logs,
                            cycleDays = cycleDays,
                            today = today
                        )
                    } else {
                        CycleRing(
                            lastStart = lastLog?.startDateEpochDay,
                            periodDays = periodDays,
                            cycleDays = cycleDays,
                            today = today
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 视图切换：圆环 / 日历（放视图下方） =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            RoundedCornerShape(50)
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ViewToggle("圆环", selected = !showCalendar) { manualCalendar = false }
                    ViewToggle("日历", selected = showCalendar) { manualCalendar = true }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 操作按钮：2×2 网格（结束经期 / 修订上次经期 / 开始新经期 / 补记历史经期） =====
            // 经期进行中「结束本次经期」可点（一键把持续天数调整为到今天）；已到/已过记录结束日置灰；
            // 修订上次经期从轻量文字入口升级为按钮，与大按钮并排，四个按钮两行对齐
            if (lastLog != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeLog != null && activeDiff != null) {
                        val endDay = activeLog.startDateEpochDay + activeLog.periodDays - 1
                        val alreadyToday = today == endDay
                        val pastEnd = today > endDay
                        Button(
                            onClick = { showEndConfirm = true },
                            enabled = !alreadyToday && !pastEnd,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when {
                                    pastEnd -> "本次经期已结束"
                                    alreadyToday -> "已记录到今天"
                                    else -> "结束本次经期"
                                },
                                maxLines = 1
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { editingLog = lastLog },
                        modifier = if (activeLog != null && activeDiff != null) Modifier.weight(1f)
                        else Modifier.fillMaxWidth()
                    ) {
                        Text("修订上次经期", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // ===== 开始新经期 / 补记历史经期：常驻主视图操作行 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showRegister = true },
                    modifier = Modifier.weight(1f)
                ) { Text("开始新经期") }
                OutlinedButton(
                    onClick = { showBackfill = true },
                    modifier = Modifier.weight(1f)
                ) { Text("补记历史经期") }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 图例 =====
            val legendPeriod = MaterialTheme.colorScheme.primary
            val legendFollicular = MaterialTheme.colorScheme.secondaryContainer
            val legendOvulation = MaterialTheme.colorScheme.tertiary
            // 深色模式下用 onSurface 透明度（半透明白）而非固定半透明黑
            val legendLuteal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(legendPeriod, "月经期")
                LegendDot(legendFollicular, "卵泡期")
                LegendDot(legendOvulation, "排卵期")
                LegendDot(legendLuteal, "黄体期")
            }

            Spacer(Modifier.height(20.dp))

            // ===== 关键日期（2×2 网格卡片，填满版面不留大空白） =====
            if (lastLog != null) {
                val nextStart = CycleCalculator.nextStartAfter(lastLog.startDateEpochDay, cycleDays)
                val overdue = today >= nextStart
                val phase = CycleCalculator.phaseOf(today, lastLog.startDateEpochDay, periodDays, cycleDays)
                val ovuRange = CycleCalculator.ovulationWindow(lastLog.startDateEpochDay, periodDays, nextStart)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoCard(
                        "上次经期",
                        formatRange(lastLog.startDateEpochDay, lastLog.periodDays),
                        "共 " + lastLog.periodDays + " 天",
                        Modifier.weight(1f)
                    )
                    InfoCard(
                        "当前阶段",
                        phase.label,
                        "周期第 " + (today - lastLog.startDateEpochDay + 1) + " 天",
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoCard(
                        "下次经期",
                        formatDate(nextStart),
                        if (overdue) "已到预测日期，请登记" else "还有 " + (nextStart - today) + " 天",
                        Modifier.weight(1f)
                    )
                    InfoCard(
                        "排卵日",
                        formatDate(CycleCalculator.effectiveOvulationDay(lastLog.startDateEpochDay, periodDays, nextStart)),
                        "窗口 " + formatDate(ovuRange.first) + " ~ " + formatDate(ovuRange.last),
                        Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    "还没有登记记录。\n点击「开始新经期」登记最近一次经期首日后，这里会显示完整的周期推算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "温馨提示...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showTips = true }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "日历法推算仅供参考，不能作为避孕或医学诊断依据。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // 登记 / 开始新经期弹窗（主视图直达，默认选中今天）
    if (showRegister) {
        // 默认选中今天：直接点确定即登记今天，避免不触摸日期选择器时静默无效果
        val registerState = rememberDatePickerState(
            initialSelectedDateMillis = today * 86400000L,
            selectableDates = pastAndTodayOnly
        )
        DatePickerDialog(
            onDismissRequest = { showRegister = false },
            confirmButton = {
                TextButton(onClick = {
                    registerState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        when (val prompt = unreasonableLogReason(day, periodDays)) {
                            null -> scope.launch {
                                container.cycleRepository.add(
                                    CycleLogEntity(startDateEpochDay = day, periodDays = periodDays)
                                )
                                scope.launch { runCatching { container.cycleEventBridge.syncEvent() } }
                            }
                            else -> pendingSpecial = PendingSpecialLog(day, periodDays, prompt)
                        }
                    }
                    showRegister = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRegister = false }) { Text("取消") } }
        ) { DatePicker(state = registerState) }
    }

    // 补记历史经期：区间选择（几号到几号），纯补充记录
    if (showBackfill) {
        val rangeState = rememberDateRangePickerState(selectableDates = pastAndTodayOnly)
        val selStart = rangeState.selectedStartDateMillis
        val selEnd = rangeState.selectedEndDateMillis
        val startDay = selStart?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        }
        val days = if (selStart != null && selEnd != null && selEnd >= selStart)
            ((selEnd - selStart) / 86400000L + 1).toInt() else 0
        val valid = days in CycleCalculator.MIN_PERIOD_DAYS..CycleCalculator.MAX_PERIOD_DAYS
        DatePickerDialog(
            onDismissRequest = { showBackfill = false },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        if (startDay != null) {
                            when (val prompt = unreasonableLogReason(startDay, days)) {
                                null -> scope.launch {
                                    container.cycleRepository.add(
                                        CycleLogEntity(startDateEpochDay = startDay, periodDays = days)
                                    )
                                    scope.launch { runCatching { container.cycleEventBridge.syncEvent() } }
                                }
                                else -> pendingSpecial = PendingSpecialLog(startDay, days, prompt)
                            }
                        }
                        showBackfill = false
                    }
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showBackfill = false }) { Text("取消") } }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    "补记历史经期",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    when {
                        startDay == null || selEnd == null -> "选择这次经期的开始与结束日期"
                        !valid -> "持续天数需在 ${CycleCalculator.MIN_PERIOD_DAYS}~${CycleCalculator.MAX_PERIOD_DAYS} 天之间"
                        startDay != null && unreasonableLogReason(startDay, days) != null ->
                            "时间不合理（与已有记录重叠或提前过多）：保存时需确认是否作为特殊情况记录"
                        else -> "将记录 " + formatRange(startDay, days) + "，共 " + days + " 天"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                )
                DateRangePicker(
                    state = rangeState,
                    showModeToggle = false,
                    modifier = Modifier.height(420.dp)
                )
            }
        }
    }

    // 重叠二次确认弹窗：登记/补记区间与已有记录重叠时出现，可选填备注作为特殊情况保存
    pendingSpecial?.let { pending ->
        var noteText by remember(pending) { mutableStateOf("可能为非经期出血") }
        AlertDialog(
            onDismissRequest = { pendingSpecial = null },
            title = { Text("确认作为特殊情况记录？") },
            text = {
                Column {
                    Text(pending.reason + "。\n特殊情况只标记当天（单日记录），不会按经期天数向后延伸。可附备注说明。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(50) },
                        label = { Text("备注（可选）") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val note = noteText.trim().ifEmpty { null }
                    scope.launch {
                        // 特殊情况（非经期出血等）只标记当天：单日记录，不污染经期天数均值与预测
                        container.cycleRepository.add(
                            CycleLogEntity(
                                startDateEpochDay = pending.startDay,
                                periodDays = 1,
                                note = note
                            )
                        )
                        runCatching { container.cycleEventBridge.syncEvent() }
                    }
                    pendingSpecial = null
                }) { Text("作为特殊情况记录") }
            },
            dismissButton = { TextButton(onClick = { pendingSpecial = null }) { Text("取消") } }
        )
    }

    // 结束本次经期确认：避免误触；明确「今天计为经期最后一天」的口径
    if (showEndConfirm) {
        val log = activeLog
        if (log != null) {
            val diff = (today - log.startDateEpochDay + 1).toInt()
            AlertDialog(
                onDismissRequest = { showEndConfirm = false },
                title = { Text("结束本次经期？") },
                text = {
                    Text(
                        "将把本次经期记录为 " + formatRange(log.startDateEpochDay, diff) + "，共 " + diff +
                            " 天；今天（" + formatDate(today) + "）计为经期的最后一天，之后出血请单独补记。" +
                            "如果想让它昨天就结束，请改用「修订上次经期」。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            container.cycleRepository.update(log.copy(periodDays = diff))
                            scope.launch { runCatching { container.cycleEventBridge.syncEvent() } }
                        }
                        showEndConfirm = false
                    }) { Text("确认结束") }
                },
                dismissButton = { TextButton(onClick = { showEndConfirm = false }) { Text("取消") } }
            )
        } else {
            showEndConfirm = false
        }
    }

    // 修订上次经期弹窗（与历史记录「调整」共用同一交互）
    if (editingLog != null) {
        val log = editingLog!!
        CycleLogEditDialog(
            log = log,
            logs = logs,
            onDismiss = { editingLog = null },
            onSave = { startDay, days, note ->
                scope.launch {
                    container.cycleRepository.update(
                        log.copy(startDateEpochDay = startDay, periodDays = days, note = note)
                    )
                    runCatching { container.cycleEventBridge.syncEvent() }
                    editingLog = null
                }
            }
        )
    }

    // 温馨提示弹窗
    if (showTips) {
        AlertDialog(
            onDismissRequest = { showTips = false },
            confirmButton = {
                TextButton(onClick = { showTips = false }) { Text("我知道了") }
            },
            title = { Text("温馨提示") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "这是什么？",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "周期管家通过记录每次经期首日，用日历法推算你的月经周期：预测下次经期、排卵日与排卵期，并把下次经期同步为首页倒数事件。所有数据只保存在本机。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "周期四个阶段",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "· 月经期：经期出血的第 1 天到结束，通常 3～7 天，对应圆环的深色段。\n· 卵泡期：月经结束后到排卵前，卵泡逐渐发育成熟，是子宫内膜重新增厚的阶段。\n· 排卵期：排卵日一般在下次经期前 14 天左右，其前后各约 2 天是受孕概率最高的窗口。\n· 黄体期：排卵后到下次经期来临前，身体分泌孕激素维持内膜；未受孕则内膜脱落，进入下一个月经期。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "关于准确性",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "每个人的周期长度、经期天数都不同，情绪、压力、作息、出行、疾病等都可能让周期提前或延后；日历法按平均值推算，与实际排卵时间可能有数天误差。请把这里的推算当作参考，不要作为避孕或医学诊断依据；如周期长期紊乱或伴有不适，请及时咨询医生。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }
}

/** 圆环周期图：四段闭环 + 当前位置指针。 */
@Composable
private fun CycleRing(
    lastStart: Long?,
    periodDays: Int,
    cycleDays: Int,
    today: Long
) {
    // 颜色在 Composable 体内解析（Canvas 绘制闭包里不能调用 composable）
    val periodColor = MaterialTheme.colorScheme.primary
    val follicularColor = MaterialTheme.colorScheme.secondaryContainer
    val ovulationColor = MaterialTheme.colorScheme.tertiary
    // 黄体期/底环用 onSurface 透明度：浅色=半透明黑，深色=半透明白，两种模式都可见
    val lutealColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26.dp.toPx()
            val ringSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            // 分段：与日历着色共用同一套动态推算（经期过长时排卵日/窗口自动后移收窄，
            // 卵泡期保底 2 天，黄体期最短 11 天），总和恒等于周期天数
            val seg = CycleCalculator.phaseSegments(lastStart ?: 0L, periodDays, cycleDays)
            val segments = listOf(
                seg[0].toFloat() to periodColor,
                seg[1].toFloat() to follicularColor,
                seg[2].toFloat() to ovulationColor,
                seg[3].toFloat() to lutealColor
            )
            // 底环
            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = ringSize,
                style = Stroke(stroke, cap = StrokeCap.Butt)
            )
            // 各段（段间留 2° 间隙）
            var angle = -90f
            segments.forEach { (days, color) ->
                val sweep = days / cycleDays * 360f
                if (sweep > 3f) {
                    drawArc(
                        color = color,
                        startAngle = angle + 1f, sweepAngle = sweep - 2f, useCenter = false,
                        topLeft = topLeft, size = ringSize,
                        style = Stroke(stroke, cap = StrokeCap.Butt)
                    )
                }
                angle += sweep
            }
            // 当前位置指针
            if (lastStart != null) {
                val dayInCycle = (today - lastStart + 1).toFloat().coerceIn(1f, cycleDays.toFloat())
                val pos = -90f + 360f * (dayInCycle - 1f) / cycleDays
                val radius = ringSize.width / 2
                val center = Offset(topLeft.x + radius, topLeft.y + radius)
                val radians = Math.toRadians(pos.toDouble())
                val markerCenter = Offset(
                    center.x + radius * Math.cos(radians).toFloat(),
                    center.y + radius * Math.sin(radians).toFloat()
                )
                drawCircle(Color.White, radius = stroke * 0.62f, center = markerCenter)
                drawCircle(periodColor, radius = stroke * 0.42f, center = markerCenter)
            }
        }
        // 环中心文字
        if (lastStart == null) {
            Text(
                "暂无记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } else {
            val nextStart = CycleCalculator.nextStartAfter(lastStart, cycleDays)
            val overdue = today >= nextStart
            val phase = CycleCalculator.phaseOf(today, lastStart, periodDays, cycleDays)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    phase.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (phase) {
                        CycleCalculator.Phase.PERIOD -> periodColor
                        CycleCalculator.Phase.OVULATION -> ovulationColor
                        else -> onSurfaceColor
                    }
                )
                Spacer(Modifier.height(4.dp))
                if (overdue) {
                    Text(
                        "请登记本次经期",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "周期第 " + (today - lastStart + 1) + " 天",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        "距下次经期 " + (nextStart - today) + " 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================ 设置视图 ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val logs by container.cycleRepository.observeAll().collectAsState(initial = emptyList())
    val cycleManual by container.settingsRepository.cycleDays.collectAsState(initial = CycleCalculator.DEFAULT_CYCLE_DAYS)
    val periodManual by container.settingsRepository.cyclePeriodDays.collectAsState(initial = CycleCalculator.DEFAULT_PERIOD_DAYS)
    val cycleAuto by container.settingsRepository.cycleCycleAuto.collectAsState(initial = true)
    val periodAuto by container.settingsRepository.cyclePeriodAuto.collectAsState(initial = true)
    val passwordEnabled by container.settingsRepository.cyclePasswordEnabled.collectAsState(initial = false)
    val eventEnabled by container.settingsRepository.cycleEventEnabled.collectAsState(initial = false)
    val eventTitle by container.settingsRepository.cycleEventTitle.collectAsState(initial = "周期管家")
    val vaultSet by container.settingsRepository.vaultPasswordSet.collectAsState(initial = false)

    var editingLog by remember { mutableStateOf<CycleLogEntity?>(null) }
    var deletingLog by remember { mutableStateOf<CycleLogEntity?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showEventNameDialog by remember { mutableStateOf(false) }
    var showPasswordNeedVault by remember { mutableStateOf(false) }
    var showNeedLog by remember { mutableStateOf(false) }

    val today = LocalDate.now().toEpochDay()
    // 推算锚点只用真正的经期记录；特殊情况记录（带备注的单日标记）不作为「上次经期」
    val lastLog = logs.firstOrNull { it.note == null }
    // 生效参数：自动开启且数据足够时按近 3 次记录均值推算，否则回落到手动设置值
    val cycleDays = container.cycleRepository.effectiveCycleDays(logs, cycleManual, cycleAuto)
    val periodDays = container.cycleRepository.effectivePeriodDays(logs, periodManual, periodAuto)
    val avg = container.cycleRepository.averageCycleDays(logs)
    val periodAvg = container.cycleRepository.averagePeriodDays(logs)
    val nextStart = lastLog?.let { CycleCalculator.nextStartAfter(it.startDateEpochDay, cycleDays) }
    val overdue = nextStart != null && today >= nextStart

    fun syncEvent() {
        scope.launch { runCatching { container.cycleEventBridge.syncEvent() } }
    }

    // 历史记录管理子页 ⇄ 设置页淡入淡出；返回手势先回设置页
    BackHandler(enabled = showHistory) { showHistory = false }
    Crossfade(targetState = showHistory, label = "cycle_history") { hist ->
        if (hist) {
            CycleHistoryScreen(
                logs = logs,
                onBack = { showHistory = false },
                onEdit = { editingLog = it },
                onDelete = { deletingLog = it }
            )
        } else {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("周期管家设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ===== 周期参数（自动测算 ⇄ 手动设置开关） =====
            Text("周期参数", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            ToggleRow(
                title = "自动测算周期天数",
                subtitle = when {
                    cycleAuto && avg != null -> "当前周期天数为自动测算（近${CycleCalculator.AVG_WINDOW}次均值 $avg 天）"
                    cycleAuto -> "当前数据未满足测算要求，仍以手动设置为准"
                    else -> "已关闭，使用下方手动设置的值"
                },
                checked = cycleAuto,
                enabled = true
            ) { want ->
                scope.launch {
                    container.settingsRepository.setCycleCycleAuto(want)
                    syncEvent()
                }
            }
            SettingStepper(
                label = "周期天数",
                value = cycleDays,
                range = CycleCalculator.MIN_CYCLE_DAYS..CycleCalculator.MAX_CYCLE_DAYS,
                // 数据不满足测算要求时生效值就是手动值，± 应保持可用
                enabled = !cycleAuto || avg == null
            ) { v ->
                scope.launch {
                    container.settingsRepository.setCycleDays(v)
                    syncEvent()
                }
            }
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = "自动测算经期持续天数",
                subtitle = when {
                    periodAuto && periodAvg != null -> "当前经期持续天数为自动测算（近${CycleCalculator.AVG_WINDOW}次均值 $periodAvg 天）"
                    periodAuto -> "当前数据未满足测算要求，仍以手动设置为准"
                    else -> "已关闭，使用下方手动设置的值"
                },
                checked = periodAuto,
                enabled = true
            ) { want ->
                scope.launch {
                    container.settingsRepository.setCyclePeriodAuto(want)
                    syncEvent()
                }
            }
            SettingStepper(
                label = "经期持续天数",
                value = periodDays,
                range = CycleCalculator.MIN_PERIOD_DAYS..CycleCalculator.MAX_PERIOD_DAYS,
                // 数据不满足测算要求时生效值就是手动值，± 应保持可用
                enabled = !periodAuto || periodAvg == null
            ) { v ->
                scope.launch {
                    container.settingsRepository.setCyclePeriodDays(v)
                    syncEvent()
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 默认视图 =====
            val defaultCalendar by container.settingsRepository.cycleDefaultCalendar
                .collectAsState(initial = false)
            Text("默认视图", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row {
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            RoundedCornerShape(50)
                        )
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ViewToggle("圆环", selected = !defaultCalendar) {
                        scope.launch { container.settingsRepository.setCycleDefaultCalendar(false) }
                    }
                    ViewToggle("日历", selected = defaultCalendar) {
                        scope.launch { container.settingsRepository.setCycleDefaultCalendar(true) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "进入周期管家时默认显示的视图；主视图里的手动切换不会改变这个设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )

            Spacer(Modifier.height(20.dp))

            if (logs.isEmpty()) {
                Text(
                    "还没有记录。登记最近一次经期首日后，主视图会显示月经期、卵泡期、排卵期和黄体期的推算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                // 历史记录管理：独立子页（点击进入，返回手势回设置页）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHistory = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "历史记录管理（${logs.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "进入",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 隐私与快捷事件 =====
            Text("隐私与快捷事件", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            ToggleRow(
                title = "密码保护",
                subtitle = if (vaultSet) "进入本功能需验证 Vault 密码或指纹" else "需要先在 Vault 中设置密码",
                checked = passwordEnabled,
                enabled = vaultSet
            ) { want ->
                if (want && !vaultSet) {
                    showPasswordNeedVault = true
                } else {
                    scope.launch { container.settingsRepository.setCyclePasswordEnabled(want) }
                }
            }
            ToggleRow(
                title = "在主页显示快捷事件",
                subtitle = "作为一个普通事件存在：可移动、放入文件夹、置顶；点击直达本功能，日期自动跟随预测滚动",
                checked = eventEnabled,
                enabled = true
            ) { want ->
                if (want && logs.isEmpty()) {
                    // 没有登记记录时事件无法计算日期——明确提示，而不是静默失败
                    showNeedLog = true
                } else {
                    scope.launch {
                        container.settingsRepository.setCycleEventEnabled(want)
                        syncEvent()
                    }
                }
            }
            if (eventEnabled) {
                TextButton(onClick = { showEventNameDialog = true }) {
                    Text("自定义事件名称：$eventTitle")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "⚠️ 本功能采用日历法推算（排卵日 ≈ 预测下次经期首日 − 14 天；经期较长时排卵日与阶段划分自动微调，" +
                    "卵泡期保底 2 天、黄体期最短 11 天，均在医学共识波动区间内）。" +
                    "周期受压力、作息、疾病等影响存在波动，结果仅供参考，不能作为避孕或医学诊断依据；" +
                    "如有月经异常或健康疑问，请咨询医生。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        }
        }
    }

    // ===== 弹窗层 =====
    if (editingLog != null) {
        val log = editingLog!!
        CycleLogEditDialog(
            log = log,
            logs = logs,
            onDismiss = { editingLog = null },
            onSave = { startDay, days, note ->
                scope.launch {
                    container.cycleRepository.update(
                        log.copy(startDateEpochDay = startDay, periodDays = days, note = note)
                    )
                    runCatching { container.cycleEventBridge.syncEvent() }
                    editingLog = null
                }
            }
        )
    }

    if (deletingLog != null) {
        val log = deletingLog!!
        AlertDialog(
            onDismissRequest = { deletingLog = null },
            title = { Text("删除这条记录？") },
            text = { Text(formatRange(log.startDateEpochDay, log.periodDays) + " 的经期记录将被删除，推算将基于剩余的记录进行。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.cycleRepository.delete(log)
                        deletingLog = null
                        syncEvent()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingLog = null }) { Text("取消") } }
        )
    }

    if (showEventNameDialog) {
        var name by remember { mutableStateOf(eventTitle) }
        AlertDialog(
            onDismissRequest = { showEventNameDialog = false },
            title = { Text("快捷事件名称") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("名称（默认「周期管家」）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.settingsRepository.setCycleEventTitle(name.ifBlank { "周期管家" })
                        syncEvent()
                        showEventNameDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showEventNameDialog = false }) { Text("取消") } }
        )
    }

    if (showPasswordNeedVault) {
        AlertDialog(
            onDismissRequest = { showPasswordNeedVault = false },
            title = { Text("需要先设置 Vault 密码") },
            text = { Text("周期管家的密码保护复用 Vault 密码。请先进入 Vault 设置密码后，再回来开启。") },
            confirmButton = {
                TextButton(onClick = { showPasswordNeedVault = false }) { Text("知道了") }
            }
        )
    }

    if (showNeedLog) {
        AlertDialog(
            onDismissRequest = { showNeedLog = false },
            title = { Text("请先登记一次经期") },
            text = { Text("快捷事件的日期来自周期推算，需要至少一次经期登记。请先回到主视图点「开始新经期」登记，登记后快捷事件会自动创建并显示在主页。") },
            confirmButton = {
                TextButton(onClick = { showNeedLog = false }) { Text("知道了") }
            }
        )
    }
}

// ============================ 通用组件 ============================

/** 经期记录修订弹窗：日期区间选择（预设当前区间），起止一起改；校验天数范围与与其他记录重叠。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleLogEditDialog(
    log: CycleLogEntity,
    logs: List<CycleLogEntity>,
    onDismiss: () -> Unit,
    onSave: (startDay: Long, days: Int, note: String?) -> Unit
) {
    val editState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = log.startDateEpochDay * 86400000L,
        initialSelectedEndDateMillis = (log.startDateEpochDay + log.periodDays - 1) * 86400000L,
        selectableDates = pastAndTodayOnly
    )
    var noteText by remember(log.id) { mutableStateOf(log.note ?: "") }
    val selStart = editState.selectedStartDateMillis
    val selEnd = editState.selectedEndDateMillis
    val startDay = selStart?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
    }
    val days = if (selStart != null && selEnd != null && selEnd >= selStart)
        ((selEnd - selStart) / 86400000L + 1).toInt() else 0
    val valid = days in CycleCalculator.MIN_PERIOD_DAYS..CycleCalculator.MAX_PERIOD_DAYS
    // 与其他记录重叠会让均值与着色失真：保存前校验（排除本记录自身）
    val overlap = startDay != null && logs.any { other ->
        other.id != log.id &&
            startDay <= other.startDateEpochDay + other.periodDays - 1 &&
            startDay + days - 1 >= other.startDateEpochDay
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = valid && !overlap,
                onClick = {
                    if (startDay != null) {
                        onSave(startDay, days, noteText.trim().ifEmpty { null })
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text(
                "修订这条记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                when {
                    startDay == null || selEnd == null -> "重新选择这次经期的开始与结束日期"
                    overlap -> "与另一条记录的日期重叠，请调整"
                    !valid -> "持续天数需在 ${CycleCalculator.MIN_PERIOD_DAYS}~${CycleCalculator.MAX_PERIOD_DAYS} 天之间"
                    else -> "将改为 " + formatRange(startDay, days) + "，共 " + days + " 天"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
            DateRangePicker(
                state = editState,
                showModeToggle = false,
                modifier = Modifier.height(360.dp)
            )
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it.take(50) },
                label = { Text("备注（特殊情况说明，可选）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingStepper(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean = true,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { if (value - 1 >= range.first) onChange(value - 1) },
            enabled = enabled
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(
            "$value 天",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        OutlinedButton(
            onClick = { if (value + 1 <= range.last) onChange(value + 1) },
            enabled = enabled
        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

// ============================ 解锁门 ============================

/** 解锁门：验证 Vault 密码（只验证不解密），支持指纹（跟随 Vault 的指纹开关）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleUnlockGate(
    container: AppContainer,
    onUnlocked: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val hash by container.settingsRepository.vaultPasswordHash.collectAsState(initial = null)
    val salt by container.settingsRepository.vaultSalt.collectAsState(initial = null)
    val biometricEnabled by container.settingsRepository.vaultBiometricEnabled
        .collectAsState(initial = false)

    val biometricAvailable = remember(activity) {
        activity != null &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
            BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateWithBiometric() {
        val act = activity ?: return
        val prompt = BiometricPrompt(
            act,
            ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证身份")
            .setSubtitle("解锁周期管家")
            .setNegativeButtonText("取消")
            .build()
        prompt.authenticate(info)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("周期管家") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("输入密码解锁", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text("密码（与 Vault 一致）") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val h = hash ?: return@Button
                    val s = salt ?: return@Button
                    if (password.isBlank()) {
                        error = "请输入密码"
                        return@Button
                    }
                    val ok = try {
                        VaultCrypto.hash(password, s) == h
                    } catch (e: Exception) {
                        false
                    }
                    if (ok) onUnlocked() else error = "密码错误"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("解锁")
            }
            if (biometricAvailable && biometricEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { authenticateWithBiometric() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("使用指纹")
                }
            }
        }
    }
}

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateFmt)

/** 「几号到几号」区间格式：X月X日 ~ X月X日 */
private fun formatRange(startEpochDay: Long, days: Int): String =
    formatDate(startEpochDay) + " ~ " + formatDate(startEpochDay + days - 1)

/** 历史记录管理子页：全部经期记录 + 调整/删除入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleHistoryScreen(
    logs: List<CycleLogEntity>,
    onBack: () -> Unit,
    onEdit: (CycleLogEntity) -> Unit,
    onDelete: (CycleLogEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(logs, key = { it.id }) { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                formatRange(log.startDateEpochDay, log.periodDays) + " · " + log.periodDays + "天",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val dow = LocalDate.ofEpochDay(log.startDateEpochDay)
                                .dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
                            Text(
                                dow,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            log.note?.let { note ->
                                Text(
                                    "备注：" + note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        TextButton(onClick = { onEdit(log) }) { Text("调整") }
                        IconButton(onClick = { onDelete(log) }) {
                            Icon(
                                Icons.Default.Delete, contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

/** 圆环/日历切换 pill。 */
@Composable
private fun ViewToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/** 关键日期网格卡片：label + 主值 + 副说明。 */
@Composable
private fun InfoCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 月历视图：每天底色 = 当日所处阶段，圆点 = 已登记经期日；可翻月。已登记区间按各记录自身的持续天数。 */
@Composable
private fun CycleCalendarMonth(
    logs: List<CycleLogEntity>,
    cycleDays: Int,
    today: Long
) {
    var monthOffset by remember { mutableStateOf(0) }
    val month = LocalDate.now().plusMonths(monthOffset.toLong())

    // 颜色在 Composable 体内解析
    val periodColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val follicularColor = MaterialTheme.colorScheme.secondaryContainer
    val onFollicularColor = MaterialTheme.colorScheme.onSecondaryContainer
    val ovulationColor = MaterialTheme.colorScheme.tertiary
    val onOvulationColor = MaterialTheme.colorScheme.onTertiary
    val lutealColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val plainTextColor = MaterialTheme.colorScheme.onSurface

    val logEntries = logs.map { it.startDateEpochDay to it.periodDays }
    val loggedDays = remember(logs) {
        val set = mutableSetOf<Long>()
        logs.forEach { set.addAll(CycleCalculator.periodRange(it.startDateEpochDay, it.periodDays)) }
        set
    }

    Column(Modifier.fillMaxWidth()) {
        // 月份导航
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { monthOffset -= 1 }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                month.year.toString() + "年" + month.monthValue + "月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { monthOffset += 1 }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下个月")
            }
        }
        Spacer(Modifier.height(4.dp))
        // 星期表头（周一开始）
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
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
        // 日期网格
        val leading = month.dayOfWeek.value - 1 // 周一=1 → 前导空格数
        val daysInMonth = month.lengthOfMonth()
        val rows = (leading + daysInMonth + 6) / 7
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
                            val epochDay = month.withDayOfMonth(dayNum).toEpochDay()
                            val phase = CycleCalculator.phaseOfAnyDay(epochDay, logEntries, cycleDays)
                            // 未来经期日不点亮：预测的下次经期，或已登记但尚未到来的区间尾部——
                            // 统一浅蓝底 + 空心圆点，与已到来（实心点亮）和卵泡期区分
                            val futurePeriod =
                                phase == CycleCalculator.Phase.PERIOD && epochDay > today
                            val bg = when {
                                futurePeriod -> periodColor.copy(alpha = 0.14f)
                                phase == CycleCalculator.Phase.PERIOD -> periodColor
                                phase == CycleCalculator.Phase.FOLLICULAR -> follicularColor
                                phase == CycleCalculator.Phase.OVULATION -> ovulationColor
                                else -> lutealColor
                            }
                            val fg = when {
                                futurePeriod -> plainTextColor
                                phase == CycleCalculator.Phase.PERIOD -> onPrimaryColor
                                phase == CycleCalculator.Phase.FOLLICULAR -> onFollicularColor
                                phase == CycleCalculator.Phase.OVULATION -> onOvulationColor
                                else -> plainTextColor
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1.dp)
                                    .background(bg, RoundedCornerShape(8.dp)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    dayNum.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = fg,
                                    fontWeight = if (epochDay == today) FontWeight.Bold else FontWeight.Normal
                                )
                                when {
                                    loggedDays.contains(epochDay) ->
                                        Box(Modifier.size(4.dp).background(fg, CircleShape))
                                    futurePeriod ->
                                        // 空心圆点：尚未到来的经期日（到来/登记确认后变实心）
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .border(1.2.dp, periodColor, CircleShape)
                                        )
                                    else -> Spacer(Modifier.height(4.dp))
                                }
                            }
                            if (epochDay == today) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "底色代表当天所处阶段。\n" +
                "实心圆点 = 已经来的经期日（登记过的）；空心圆点 = 还没来的经期日（预测的下次经期，到来后变实心）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
