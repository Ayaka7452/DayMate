package com.ayaka7452.daymate.feature.cycle

import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.core.content.ContextCompat
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.core.util.CycleCalculator
import com.ayaka7452.daymate.core.util.NoteCatalog
import com.ayaka7452.daymate.data.db.CycleLogEntity
import com.ayaka7452.daymate.data.db.CycleNoteEntity
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

    // 防截屏/最近任务缩略图遮挡（与 Vault 一致）。
    // 默认阻止，可在「设置 → 隐私 → 周期管家允许截图」放开；密码验证门（locked）
    // 无论开关如何都始终阻止，避免密码被截屏。
    val allowScreenshot by container.settingsRepository.allowScreenshotCycle
        .collectAsState(initial = false)
    val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity
    DisposableEffect(allowScreenshot, locked) {
        if (!allowScreenshot || locked) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
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
    // 日常记录（症状/情绪/性生活/自定义）：只用于日历标记与当日详情，不参与任何周期推算
    val notes by container.cycleNoteRepository.observeAll().collectAsState(initial = emptyList())
    val cycleManual by container.settingsRepository.cycleDays.collectAsState(initial = CycleCalculator.DEFAULT_CYCLE_DAYS)
    val periodManual by container.settingsRepository.cyclePeriodDays.collectAsState(initial = CycleCalculator.DEFAULT_PERIOD_DAYS)
    val cycleAuto by container.settingsRepository.cycleCycleAuto.collectAsState(initial = true)
    val periodAuto by container.settingsRepository.cyclePeriodAuto.collectAsState(initial = true)
    // 生效参数：自动开启且数据足够时按近 3 次记录均值推算，否则回落到手动设置值
    val cycleDays = container.cycleRepository.effectiveCycleDays(logs, cycleManual, cycleAuto)
    val periodDays = container.cycleRepository.effectivePeriodDays(logs, periodManual, periodAuto)

    // 登记/补记在不合理时间的判断：返回提示文案（null=合理，直接保存）
    //  ① 与已有记录重叠：同一时段重复登记经期通常不合理
    //  ② 与任一真实经期首日间隔不足 MIN_PERIOD_INTERVAL_DAYS 天（不重叠）：两次独立经期不可能这么近
    //  ③ 比预测下次经期提前超过 EARLY_PERIOD_THRESHOLD_DAYS 天（FIGO：相邻周期波动 ≤7~9 天属正常）
    fun unreasonableLogReason(day: Long, days: Int): String? {
        val last = logs.firstOrNull()?.startDateEpochDay
            ?: return null
        if (logs.any {
                day <= it.startDateEpochDay + it.periodDays - 1 &&
                    day + days - 1 >= it.startDateEpochDay
            }
        ) {
            return "与已有经期记录日期重叠"
        }
        // 与任一真实经期记录首日间隔不足 15 天（不重叠）：两次「经期」间隔过短，
        // 基本不可能是两次独立经期，多为经间期出血。特殊情况记录（带备注的单日标记）不参与判断
        val tooClose = logs.filter { it.note == null }.firstOrNull {
            val gap = kotlin.math.abs(it.startDateEpochDay - day)
            gap in 1 until CycleCalculator.MIN_PERIOD_INTERVAL_DAYS
        }
        if (tooClose != null) {
            val gap = kotlin.math.abs(tooClose.startDateEpochDay - day)
            return "与已有经期记录（" + formatDate(tooClose.startDateEpochDay) + "）仅相差 " + gap +
                " 天。间隔不足 " + CycleCalculator.MIN_PERIOD_INTERVAL_DAYS +
                " 天的两次出血通常不是两次独立经期，可能是非经期出血。建议咨询医生。"
        }
        val next = CycleCalculator.nextStartAfter(last, cycleDays)
        if (day > last && day < next - CycleCalculator.EARLY_PERIOD_THRESHOLD_DAYS) {
            return "比预测下次经期（" + formatDate(next) + "）提前 " + (next - day) +
                " 天。提前超过 " + CycleCalculator.EARLY_PERIOD_THRESHOLD_DAYS +
                " 天属异常出血范围，建议咨询医生。"
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
    // 结束本次经期只对真正的经期记录响应；特殊情况记录（备注标记的单日出血）不参与
    val activeLog = logs.firstOrNull { it.note == null && it.startDateEpochDay <= today }
    val activeDiff = activeLog?.let { (today - it.startDateEpochDay + 1).toInt() }
    // 今天恰好是本次经期记录的最后一天（已结束/记录到今天）：阶段显示用「今日结束」而非「月经期」
    val todayIsPeriodEnd =
        activeLog != null && today == activeLog.startDateEpochDay + activeLog.periodDays - 1
    var editingLog by remember { mutableStateOf<CycleLogEntity?>(null) }
    // 日历点选的日期（null = 未选中，展示今天的信息）。点同一天可取消选中，与「今日」高亮互不干扰。
    var selectedDay by remember { mutableStateOf<Long?>(null) }
    var showAddNote by remember { mutableStateOf(false) }
    var showDeleteNote by remember { mutableStateOf(false) }

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
            horizontalAlignment = Alignment.Start
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
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "已到预测经期日（" + formatDate(predicted) + "）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "登记实际开始日期可提高推算准确度",
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
                            notes = notes,
                            cycleDays = cycleDays,
                            today = today,
                            selectedDay = selectedDay,
                            onSelectDay = { day -> selectedDay = if (selectedDay == day) null else day }
                        )
                    } else {
                        CycleRing(
                            lastStart = lastLog?.startDateEpochDay,
                            periodDays = periodDays,
                            cycleDays = cycleDays,
                            today = today,
                            todayIsPeriodEnd = todayIsPeriodEnd
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

            // ===== 选中日详情区（仅日历视图、仅在有选中时出现）=====
            // 位置紧贴日历下方：点选的即时反馈要离被点的格子近，不能等滚到页面底部才看见
            if (showCalendar && selectedDay != null) {
                val detailDay = selectedDay!!
                Spacer(Modifier.height(16.dp))
                CycleDayDetail(
                    day = detailDay,
                    logs = logs,
                    dayNotes = notes.filter { it.dateEpochDay == detailDay },
                    cycleDays = cycleDays,
                    today = today,
                    onBackToToday = { selectedDay = null },
                    onAdd = { showAddNote = true },
                    onDelete = { showDeleteNote = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ===== 操作按钮：2×2 网格，上排「结束 / 开始」对称，下排「修订 / 补记」 =====
            // 经期进行中（今天未到记录结束日）：「结束本次经期」可点，「开始新经期」置灰；
            // 已到/已过结束日：结束侧置灰（经期已结束），开始侧恢复可点——两个主按钮恰好一活一灰
            if (lastLog != null) {
                val endDay = activeLog?.let { it.startDateEpochDay + it.periodDays - 1 }
                val ongoing = endDay != null && today < endDay
                val pastEnd = endDay != null && today > endDay
                val alreadyToday = endDay != null && today == endDay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeLog != null && activeDiff != null) {
                        Button(
                            onClick = { showEndConfirm = true },
                            enabled = ongoing,
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
                    Button(
                        onClick = { showRegister = true },
                        enabled = !ongoing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (ongoing) "经期中" else "开始新经期", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { editingLog = lastLog },
                        modifier = Modifier.weight(1f)
                    ) { Text("修订上次经期", maxLines = 1) }
                    OutlinedButton(
                        onClick = { showBackfill = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("补记历史经期", maxLines = 1) }
                }
            } else {
                // 无任何记录时的空状态操作行
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
            }

            Spacer(Modifier.height(16.dp))

            // ===== 图例 =====
            val legendPeriod = MaterialTheme.colorScheme.primary
            val legendFollicular = MaterialTheme.colorScheme.secondaryContainer
            val legendOvulation = MaterialTheme.colorScheme.tertiary
            // 深色模式下用 onSurface 透明度（半透明白）而非固定半透明黑
            val legendLuteal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                LegendDot(legendPeriod, "月经期")
                LegendDot(legendFollicular, "卵泡期")
                LegendDot(legendOvulation, "排卵期")
                LegendDot(legendLuteal, "黄体期")
            }

            Spacer(Modifier.height(20.dp))

            // ===== 关键日期（2×2 网格卡片，填满版面不留大空白） =====
            if (lastLog != null) {
                val nextStart = CycleCalculator.nextStartAfter(lastLog.startDateEpochDay, cycleDays)
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
                        // 明确限定为「今天」：选中其它日期时详情区会显示那天的阶段，两者不能都叫「当前阶段」而打架
                        "今日阶段",
                        if (todayIsPeriodEnd) "今日结束" else phase.label,
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
                    "尚无记录。登记最近一次经期首日后，此处将显示完整推算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "温馨提示",
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
                            "日期与已有记录重叠或提前过多，保存时需确认是否作为特殊情况记录"
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
                    // 三种 reason 文案结尾并不统一（「日期重叠」无句号，另两种自带句号），
                    // 直接拼接会产出「。。」——统一去掉结尾句号后由此处补一个
                    Text(pending.reason.trimEnd('。') + "。\n特殊情况仅标记当天，不按经期天数向后延伸。可附备注说明。")
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
                            " 天。今天（" + formatDate(today) + "）计为最后一天。如有错误，可进行补记或修订。"
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
                TextButton(onClick = { showTips = false }) { Text("好") }
            },
            title = { Text("温馨提示") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "功能说明",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "周期管家记录每次经期首日，以日历法推算月经周期，预测下次经期、排卵日与排卵期，并将下次经期同步为首页倒数事件。所有数据仅保存在本机。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "日常记录",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "在日历上点选任意日期，即可为那天添加日常记录（性生活、出血与分泌物、身体症状、情绪，或自己填写）。日常记录只作留痕，不参与周期与排卵推算，也不会点亮经期圆点——随手记一条症状不会影响预测结果。",
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
                        "日历标记",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "· 日期底色代表当天所处阶段。\n· 实心圆点：已登记的经期日。\n· 空心圆点：预测的经期日，到来后变为实心。\n· 右上角小圆点：当天有日常记录，点选该日可查看。\n· 粗蓝框：今天；细蓝框：当前选中的日期，再次点击可取消。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "关于准确性",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "周期长度与经期天数因人而异，情绪、压力、作息、出行、疾病等均可能导致周期提前或延后。日历法按平均值推算，与实际排卵时间可能存在数天误差。推算结果仅供参考，不可作为避孕或医学诊断依据；如周期长期紊乱或伴有不适，请咨询医生。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }

    // 添加记录弹窗（仅从选中日详情区唤起；选中日即记录日）
    val detailDay = selectedDay
    if (showAddNote && detailDay != null) {
        AddNoteDialog(
            day = detailDay,
            onDismiss = { showAddNote = false },
            onConfirm = { newNotes ->
                scope.launch { container.cycleNoteRepository.addAll(newNotes) }
                showAddNote = false
            }
        )
    }

    // 删除记录弹窗：列出该日全部记录供勾选，勾选后确认即删
    if (showDeleteNote && detailDay != null) {
        DeleteNotesDialog(
            day = detailDay,
            dayNotes = notes.filter { it.dateEpochDay == detailDay },
            onDismiss = { showDeleteNote = false },
            onConfirm = { ids ->
                scope.launch { container.cycleNoteRepository.deleteByIds(ids) }
                showDeleteNote = false
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
    today: Long,
    todayIsPeriodEnd: Boolean = false
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
                    if (todayIsPeriodEnd) "今日结束" else phase.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        todayIsPeriodEnd -> periodColor
                        phase == CycleCalculator.Phase.PERIOD -> periodColor
                        phase == CycleCalculator.Phase.OVULATION -> ovulationColor
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
    // 日常记录：历史记录管理页按日分组展示与删除（不参与本页任何推算）
    val notes by container.cycleNoteRepository.observeAll().collectAsState(initial = emptyList())
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
                notes = notes,
                onBack = { showHistory = false },
                onEdit = { editingLog = it },
                onDelete = { deletingLog = it },
                onDeleteNotes = { ids -> scope.launch { container.cycleNoteRepository.deleteByIds(ids) } }
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
                    cycleAuto && avg != null -> "周期天数自动测算：近 ${CycleCalculator.AVG_WINDOW} 次均值 $avg 天"
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
                    periodAuto && periodAvg != null -> "经期持续天数自动测算：近 ${CycleCalculator.AVG_WINDOW} 次均值 $periodAvg 天"
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
                "进入周期管家时默认显示的视图。主视图中的手动切换不会改变此设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )

            Spacer(Modifier.height(20.dp))

            if (logs.isEmpty()) {
                Text(
                    "尚无记录。登记最近一次经期首日后，主视图将显示四个阶段的推算。",
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
                subtitle = "以普通事件的形式存在，可移动、放入文件夹、置顶。点击直达本功能，日期随预测自动更新。",
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
                "本功能采用日历法推算：排卵日约为预测下次经期首日减 14 天。" +
                    "经期较长时排卵日与阶段划分自动微调，卵泡期不短于 2 天、黄体期不短于 11 天，均在医学共识波动区间内。" +
                    "周期受压力、作息、疾病等影响存在波动，结果仅供参考，不可作为避孕或医学诊断依据；" +
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
            text = { Text(formatRange(log.startDateEpochDay, log.periodDays) + " 的经期记录将被删除，推算将基于剩余记录进行。") },
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
            text = { Text("周期管家的密码保护复用 Vault 密码。请先进入 Vault 设置密码，再返回此处开启。") },
            confirmButton = {
                TextButton(onClick = { showPasswordNeedVault = false }) { Text("好") }
            }
        )
    }

    if (showNeedLog) {
        AlertDialog(
            onDismissRequest = { showNeedLog = false },
            title = { Text("请先登记一次经期") },
            text = { Text("快捷事件的日期来自周期推算，需要至少一次经期登记。请先返回主视图点「开始新经期」登记，之后快捷事件会自动创建并显示在主页。") },
            confirmButton = {
                TextButton(onClick = { showNeedLog = false }) { Text("好") }
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

/** 历史记录管理子页：经期记录（可调整/删除）+ 日常记录（按日分组，可整日删除）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleHistoryScreen(
    logs: List<CycleLogEntity>,
    notes: List<CycleNoteEntity>,
    onBack: () -> Unit,
    onEdit: (CycleLogEntity) -> Unit,
    onDelete: (CycleLogEntity) -> Unit,
    onDeleteNotes: (List<Long>) -> Unit
) {
    // 0 = 经期记录，1 = 日常记录。页内状态不进设置：用户从设置进来通常只看一类，看完即走
    var tab by remember { mutableStateOf(0) }
    var deletingNotesDay by remember { mutableStateOf<Long?>(null) }
    // 按日期倒序分组：同一天的记录聚成一块，删除也以「一天」为单位
    val noteGroups = remember(notes) {
        notes.groupBy { it.dateEpochDay }.entries.sortedByDescending { it.key }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
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
                    ViewToggle("经期记录", selected = tab == 0) { tab = 0 }
                    ViewToggle("日常记录", selected = tab == 1) { tab = 1 }
                }
            }

            val empty = if (tab == 0) logs.isEmpty() else noteGroups.isEmpty()
            if (empty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (tab == 0) "尚无经期记录" else "尚无日常记录\n在日历上点选日期即可添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
                return@Column
            }

            if (tab == 0) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    noteGroups.forEach { (day, dayNotes) ->
                        item(key = "noteHead$day") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        formatDate(day),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        LocalDate.ofEpochDay(day).dayOfWeek
                                            .getDisplayName(TextStyle.FULL, Locale.CHINA),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                IconButton(onClick = { deletingNotesDay = day }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除该日记录",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        items(dayNotes, key = { it.id }) { n ->
                            Box(Modifier.padding(horizontal = 20.dp)) { NoteRow(n) }
                        }
                        item(key = "noteDiv$day") {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }

    // 整日删除：复用详情区那套「勾选后确认」的弹窗，交互与入口保持一致
    deletingNotesDay?.let { day ->
        DeleteNotesDialog(
            day = day,
            dayNotes = notes.filter { it.dateEpochDay == day },
            onDismiss = { deletingNotesDay = null },
            onConfirm = { ids ->
                onDeleteNotes(ids)
                deletingNotesDay = null
            }
        )
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

/**
 * 月历视图：每天底色 = 当日所处阶段，圆点 = 已登记经期日；可翻月。已登记区间按各记录自身的持续天数。
 *
 * 三种「框」的语义必须互不混淆（用户明确提过这个痛点）：
 *  - **今日**：3dp 主色粗框 + 内侧 2dp 白衬。白衬是关键——排卵期底色用的是 Material 默认
 *    tertiary 紫红（#7D5260），与主色 #00668C 同属暗色，旧版 1.5dp 单色框在排卵期里几乎看不见；
 *    加一圈白衬后粗框在任何阶段底色上都跳得出来，也不必牺牲品牌色。
 *  - **选中**：1.5dp 主色细框 + 内侧 1.5dp 白衬。与今日同一套视觉语言，**仅以粗细区分**，
 *    用户不必学两套规则；同一格既是今日又是选中时只画粗的那道。
 *  - **有记录**：右上角一枚小圆点。位置与「圆点在数字下方」的经期语义天然分开，不会与实心/空心圆点混淆。
 */
@Composable
private fun CycleCalendarMonth(
    logs: List<CycleLogEntity>,
    notes: List<CycleNoteEntity>,
    cycleDays: Int,
    today: Long,
    selectedDay: Long?,
    onSelectDay: (Long) -> Unit
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
    // 有日常记录的日期集合（只用于格子右上角的小圆点，不参与任何阶段着色）
    val noteDays = remember(notes) { notes.map { it.dateEpochDay }.toSet() }

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
                                futurePeriod -> periodColor.copy(alpha = 0.18f)
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
                            val isToday = epochDay == today
                            val isSelected = selectedDay == epochDay
                            val hasNote = noteDays.contains(epochDay)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable { onSelectDay(epochDay) },
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
                                        color = fg,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
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
                                // 当日有日常记录：右上角一枚小圆点（与数字下方的经期圆点分区，不会看混）
                                if (hasNote) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 4.dp, end = 4.dp)
                                            .size(4.dp)
                                            .background(fg, CircleShape)
                                    )
                                }
                                if (isToday) {
                                    // 今日：粗主色框 + 白衬（白衬保证在排卵期紫红底上也清晰）
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .border(3.dp, periodColor, RoundedCornerShape(9.dp))
                                    )
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .padding(3.dp)
                                            .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                                    )
                                } else if (isSelected) {
                                    // 选中：同款视觉语言，仅比今日细一半，一眼可分
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .border(1.5.dp, periodColor, RoundedCornerShape(9.dp))
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

// ============================ 日常记录：详情区 / 添加 / 删除 ============================

/**
 * 描述某一天在周期里的定位，返回 (阶段标签, 副说明)。
 *
 * 口径必须与 [CycleCalculator.phaseOfAnyDay] 完全一致，否则会出现「日历底色是黄体期、
 * 详情区却写排卵期」这种自相矛盾的展示。锚点推进逻辑与之一一对应：先把锚点推到不晚于该日，
 * 再以「锚点 + 周期天数」为下次经期，避免逾期未登记时算出负的「距下次经期天数」。
 */
private fun describeDay(
    day: Long,
    logs: List<CycleLogEntity>,
    cycleDays: Int,
    today: Long
): Pair<String, String> {
    if (logs.isEmpty()) return "尚无记录" to "登记一次经期后即可推算阶段"

    // 落在任一已登记经期区间内 → 月经期（按该记录自身的持续天数）
    logs.firstOrNull { day in CycleCalculator.periodRange(it.startDateEpochDay, it.periodDays) }
        ?.let { return "月经期" to ("经期第 " + (day - it.startDateEpochDay + 1) + " 天") }

    val entries = logs.map { it.startDateEpochDay to it.periodDays }
    val phase = CycleCalculator.phaseOfAnyDay(day, entries, cycleDays)

    // 锚点推进：与 phaseOfAnyDay 同款，保证 nextStart 恒晚于 day。
    // 两个方向都要覆盖——记录都在未来时要向前虚拟推算，否则 days 早于最早记录时会算出错位的阶段。
    val base = logs.lastOrNull { it.startDateEpochDay <= day } ?: logs.last()
    val anchorStart: Long
    if (base.startDateEpochDay > day) {
        val k = (base.startDateEpochDay - day + cycleDays - 1) / cycleDays
        anchorStart = base.startDateEpochDay - k * cycleDays
    } else {
        var s = base.startDateEpochDay
        while (s + cycleDays <= day) s += cycleDays
        anchorStart = s
    }
    val nextStart = anchorStart + cycleDays
    val ovu = CycleCalculator.effectiveOvulationDay(anchorStart, base.periodDays, nextStart)
    val window = CycleCalculator.ovulationWindow(anchorStart, base.periodDays, nextStart)

    return when {
        phase == CycleCalculator.Phase.PERIOD ->
            "预测经期" to ("系统预测，尚未登记 · " +
                if (day > today) "还有 " + (day - today) + " 天" else "已过 " + (today - day) + " 天")
        day == ovu ->
            "排卵日" to ("受孕概率最高 · 排卵期 " + formatDate(window.first) + " ~ " + formatDate(window.last))
        phase == CycleCalculator.Phase.OVULATION ->
            "排卵期" to ("排卵日 " + formatDate(ovu) + " · 窗口 " + formatDate(window.first) + " ~ " + formatDate(window.last))
        phase == CycleCalculator.Phase.LUTEAL ->
            "黄体期" to ("距下次经期 " + (nextStart - day) + " 天")
        else ->
            "卵泡期" to ("距排卵日 " + (ovu - day) + " 天")
    }
}

/** 阶段标签的配色（与圆环/日历同一套色板，保证三处说法一致）。 */
@Composable
private fun phaseChipColors(label: String): Pair<Color, Color> = when (label) {
    "月经期" -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    "预测经期" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) to MaterialTheme.colorScheme.onSurface
    "排卵日", "排卵期" -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
    "卵泡期" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) to MaterialTheme.colorScheme.onSurface
}

/** 记录大类的色条颜色：用于详情区列表与历史页，让「症状/情绪/性生活」一眼可分。 */
@Composable
private fun noteCategoryColor(category: String): Color = when (NoteCatalog.Category.of(category)) {
    NoteCatalog.Category.SEX -> Color(0xFFD4537E)
    NoteCatalog.Category.BLEED -> MaterialTheme.colorScheme.primary
    NoteCatalog.Category.SYMPTOM -> Color(0xFFBA7517)
    NoteCatalog.Category.MOOD -> MaterialTheme.colorScheme.tertiary
    NoteCatalog.Category.CUSTOM -> MaterialTheme.colorScheme.secondary
}

/** 一条日常记录：左侧大类色条 + 名称 +（可选）补充说明。 */
@Composable
private fun NoteRow(note: CycleNoteEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(noteCategoryColor(note.category), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(note.label, style = MaterialTheme.typography.bodyMedium)
        note.note?.let { extra ->
            Spacer(Modifier.width(8.dp))
            Text(
                extra,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 选中日的详情区：阶段定位 + 当日记录 + 添加/删除入口。
 *
 * 位置紧贴日历下方——点某天之后的即时反馈必须离被点的格子足够近，否则用户不知道点中了什么。
 * 本区只做展示与入口，不含任何推算副作用：日常记录永远不参与周期计算（见 CycleNoteEntity）。
 */
@Composable
private fun CycleDayDetail(
    day: Long,
    logs: List<CycleLogEntity>,
    dayNotes: List<CycleNoteEntity>,
    cycleDays: Int,
    today: Long,
    onBackToToday: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    val (phaseLabel, sub) = describeDay(day, logs, cycleDays, today)
    val (chipBg, chipFg) = phaseChipColors(phaseLabel)
    val date = LocalDate.ofEpochDay(day)
    val dateText = date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)) + " " +
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (day == today) "今天 · " + dateText else dateText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chipBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(phaseLabel, style = MaterialTheme.typography.labelSmall, color = chipFg)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(10.dp))

            Text(
                "当日记录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            if (dayNotes.isEmpty()) {
                Text(
                    "暂无记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            } else {
                dayNotes.forEach { NoteRow(it) }
            }

            Spacer(Modifier.height(12.dp))
            // 层级约定：实心 = 会动数据的执行动作；空心 = 次级/破坏性动作
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Text("添加记录", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = dayNotes.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("删除记录", maxLines = 1)
                }
            }
            if (day != today) {
                TextButton(onClick = onBackToToday, modifier = Modifier.align(Alignment.End)) {
                    Text("回到今天")
                }
            }
        }
    }
}

/** 记录选择用的圆角 chip（与「圆环/日历」切换同一套外观，全 App 只此一种 chip 样式）。 */
@Composable
private fun NoteChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}

/**
 * 添加记录弹窗：大类分段 → 预置项多选（或自定义文本）→ 补充说明。
 *
 * 补充说明是**本次添加的所有项共用**的一条备注：绝大多数时候用户只勾一项，此时语义与
 * 「给这条记录写备注」完全一致；勾多项时仍然合理（例如同时勾「痛经 + 乏力」写「第一天，量少」）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddNoteDialog(
    day: Long,
    onDismiss: () -> Unit,
    onConfirm: (List<CycleNoteEntity>) -> Unit
) {
    var category by remember { mutableStateOf(NoteCatalog.Category.SYMPTOM) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var customText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val isCustom = category == NoteCatalog.Category.CUSTOM
    val canSave = if (isCustom) customText.trim().isNotEmpty() else selectedKeys.isNotEmpty()

    fun switchTo(c: NoteCatalog.Category) {
        category = c
        selectedKeys = emptySet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加记录 · " + formatDate(day)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NoteCatalog.selectable.forEach { c ->
                        NoteChip(c.label, selected = category == c) { switchTo(c) }
                    }
                    NoteChip("自定义", selected = isCustom) { switchTo(NoteCatalog.Category.CUSTOM) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                if (isCustom) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.take(NoteCatalog.MAX_LABEL_LENGTH) },
                        label = { Text("记录内容") },
                        placeholder = { Text("例如：泡脚、喝红糖水") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "选择要记录的项目（可多选）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NoteCatalog.presets[category].orEmpty().forEach { p ->
                            NoteChip(p.label, selected = p.key in selectedKeys) {
                                selectedKeys =
                                    if (p.key in selectedKeys) selectedKeys - p.key
                                    else selectedKeys + p.key
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(NoteCatalog.MAX_NOTE_LENGTH) },
                    label = { Text("补充说明（可选）") },
                    placeholder = { Text("例如：有保护措施、量少") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val extra = noteText.trim().ifEmpty { null }
                    val now = System.currentTimeMillis()
                    val items = if (isCustom) {
                        listOf(
                            CycleNoteEntity(
                                dateEpochDay = day,
                                category = NoteCatalog.CUSTOM_KEY,
                                presetKey = null,
                                label = customText.trim(),
                                note = extra,
                                createdAt = now
                            )
                        )
                    } else {
                        NoteCatalog.presets[category].orEmpty()
                            .filter { it.key in selectedKeys }
                            .map {
                                CycleNoteEntity(
                                    dateEpochDay = day,
                                    category = category.key,
                                    presetKey = it.key,
                                    label = it.label,
                                    note = extra,
                                    createdAt = now
                                )
                            }
                    }
                    onConfirm(items)
                }
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 删除记录弹窗：列出该日全部记录供勾选，默认全选。
 *
 * 弹窗本身就是「删除前确认」这一关——危险操作按钮用 error 色、文案写明不可恢复，
 * 再叠一层「你确定吗」只会让删除变啰嗦而不会更安全。
 */
@Composable
private fun DeleteNotesDialog(
    day: Long,
    dayNotes: List<CycleNoteEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var checkedIds by remember(dayNotes) { mutableStateOf(dayNotes.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除记录 · " + formatDate(day)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "勾选要删除的记录，删除后不可恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                dayNotes.forEach { n ->
                    val on = n.id in checkedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checkedIds = if (on) checkedIds - n.id else checkedIds + n.id
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { v ->
                                checkedIds = if (v) checkedIds + n.id else checkedIds - n.id
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(n.label, style = MaterialTheme.typography.bodyMedium)
                            val meta = listOfNotNull(NoteCatalog.labelOf(n.category), n.note)
                                .joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = checkedIds.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                onClick = { onConfirm(checkedIds.toList()) }
            ) { Text("删除选中（" + checkedIds.size + "）") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
