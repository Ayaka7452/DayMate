package com.ayaka7452.daymate.feature.cycle

import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.i18n.LocaleWrap
import com.ayaka7452.daymate.core.i18n.Tr
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
import java.time.format.TextStyle
import java.util.Locale

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
    fun unreasonableLogReason(day: Long, days: Int): String? { // TODO(i18n): 见 skipped
        val last = logs.firstOrNull()?.startDateEpochDay
            ?: return null
        if (logs.any {
                day <= it.startDateEpochDay + it.periodDays - 1 &&
                    day + days - 1 >= it.startDateEpochDay
            }
        ) {
            return Tr.s(R.string.cycle_warn_overlap)
        }
        // 与任一真实经期记录首日间隔不足 15 天（不重叠）：两次「经期」间隔过短，
        // 基本不可能是两次独立经期，多为经间期出血。特殊情况记录（带备注的单日标记）不参与判断
        val tooClose = logs.filter { it.note == null }.firstOrNull {
            val gap = kotlin.math.abs(it.startDateEpochDay - day)
            gap in 1 until CycleCalculator.MIN_PERIOD_INTERVAL_DAYS
        }
        if (tooClose != null) {
            val gap = kotlin.math.abs(tooClose.startDateEpochDay - day)
            return Tr.s(
                R.string.cycle_warn_too_close,
                formatDate(tooClose.startDateEpochDay),
                gap,
                CycleCalculator.MIN_PERIOD_INTERVAL_DAYS
            )
        }
        val next = CycleCalculator.nextStartAfter(last, cycleDays)
        if (day > last && day < next - CycleCalculator.EARLY_PERIOD_THRESHOLD_DAYS) {
            return Tr.s(
                R.string.cycle_warn_too_early,
                formatDate(next),
                next - day,
                CycleCalculator.EARLY_PERIOD_THRESHOLD_DAYS
            )
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
    // 收起动画期间 selectedDay 已被置 null，用「最后一次选中日」兜底渲染详情内容——
    // 否则内容会先跳成今天的资料、再随卡片一起滑走，看起来像闪了一下。
    // 只在点击回调里更新（不在组合期写状态），保持单向数据流。
    var lastDetailDay by remember { mutableStateOf<Long?>(null) }
    var showAddNote by remember { mutableStateOf(false) }
    var showDeleteNote by remember { mutableStateOf(false) }
    // 正在修改的那条日常记录（null = 未打开修改弹窗）。修改入口挂在选中日详情区的每一行右侧。
    var editingNote by remember { mutableStateOf<CycleNoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cycle_title)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.common_settings))
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
                            stringResource(R.string.cycle_predicted_period_day, formatDate(predicted)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.cycle_register_improve_accuracy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { showRegister = true }) { Text(stringResource(R.string.cycle_register_this_period)) }
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
                            onSelectDay = { day ->
                                if (selectedDay == day) {
                                    selectedDay = null          // 再点同一天 = 收起
                                } else {
                                    selectedDay = day
                                    lastDetailDay = day
                                }
                            }
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
                    ViewToggle(stringResource(R.string.cycle_view_ring), selected = !showCalendar) { manualCalendar = false }
                    ViewToggle(stringResource(R.string.cycle_view_calendar), selected = showCalendar) { manualCalendar = true }
                }
            }

            // ===== 选中日详情区（仅日历视图、仅在有选中时出现）=====
            // 位置紧贴日历下方：点选的即时反馈要离被点的格子近，不能等滚到页面底部才看见。
            // 展开/收起走纵向滑动而非瞬间出现——高度突变会让下方那排按钮整块跳一下，看着像页面重排。
            // 内嵌 animateContentSize：换一天时记录条数不同导致高度变化，也让它平滑过渡而不是硬切。
            // 时长刻意偏长（展开 300ms / 收起 360ms）并配 FastOutSlowIn：这块卡片是「点一下才出现」
            // 的提示区，速度一快就像页面重排闪了一下；缓出曲线让收尾变慢，收起时是被"送走"而不是"被抽走"。
            // 淡入淡出比滑动略快且先结束——几何动画还在收尾时文字已经化开，不会出现文字被压扁的过程。
            AnimatedVisibility(
                visible = showCalendar && selectedDay != null,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(360, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing))
            ) {
                // 退出动画期间 selectedDay 已置 null，用「最后一次选中日」兜底渲染，避免内容中途跳变
                val detailDay = selectedDay ?: lastDetailDay ?: today
                Column(Modifier.animateContentSize()) {
                    Spacer(Modifier.height(16.dp))
                    CycleDayDetail(
                        day = detailDay,
                        logs = logs,
                        dayNotes = notes.filter { it.dateEpochDay == detailDay },
                        cycleDays = cycleDays,
                        today = today,
                        onCollapse = { selectedDay = null },
                        onAdd = { showAddNote = true },
                        onEdit = { editingNote = it },
                        onDelete = { showDeleteNote = true }
                    )
                }
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
                                    pastEnd -> stringResource(R.string.cycle_period_ended)
                                    alreadyToday -> stringResource(R.string.cycle_recorded_today)
                                    else -> stringResource(R.string.cycle_end_period)
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
                        Text(if (ongoing) stringResource(R.string.cycle_in_period) else stringResource(R.string.cycle_start_new_period), maxLines = 1)
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
                    ) { Text(stringResource(R.string.cycle_edit_last_period), maxLines = 1) }
                    OutlinedButton(
                        onClick = { showBackfill = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.cycle_backfill_history_period), maxLines = 1) }
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
                    ) { Text(stringResource(R.string.cycle_start_new_period)) }
                    OutlinedButton(
                        onClick = { showBackfill = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.cycle_backfill_history_period)) }
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
                LegendDot(legendPeriod, stringResource(R.string.cycle_phase_period))
                LegendDot(legendFollicular, stringResource(R.string.cycle_phase_follicular))
                LegendDot(legendOvulation, stringResource(R.string.cycle_phase_ovulation))
                LegendDot(legendLuteal, stringResource(R.string.cycle_phase_luteal))
            }

            Spacer(Modifier.height(20.dp))

            // ===== 关键日期（2×2 网格卡片，填满版面不留大空白） =====
            if (lastLog != null) {
                val nextStart = CycleCalculator.nextStartAfter(lastLog.startDateEpochDay, cycleDays)
                val phase = CycleCalculator.phaseOf(today, lastLog.startDateEpochDay, periodDays, cycleDays)
                val ovuRange = CycleCalculator.ovulationWindow(lastLog.startDateEpochDay, periodDays, nextStart)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoCard(
                        stringResource(R.string.cycle_last_period),
                        formatRange(lastLog.startDateEpochDay, lastLog.periodDays),
                        stringResource(R.string.cycle_total_days_n, lastLog.periodDays),
                        Modifier.weight(1f)
                    )
                    InfoCard(
                        // 明确限定为「今天」：选中其它日期时详情区会显示那天的阶段，两者不能都叫「当前阶段」而打架
                        stringResource(R.string.cycle_today_phase),
                        if (todayIsPeriodEnd) stringResource(R.string.cycle_today_end)
                        else stringResource(phase.labelRes),
                        stringResource(R.string.cycle_day_n, (today - lastLog.startDateEpochDay + 1)),
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoCard(
                        stringResource(R.string.cycle_next_period),
                        formatDate(nextStart),
                        if (overdue) stringResource(R.string.cycle_overdue_register) else stringResource(R.string.cycle_days_left_n, (nextStart - today)),
                        Modifier.weight(1f)
                    )
                    InfoCard(
                        stringResource(R.string.cycle_ovulation_day),
                        formatDate(CycleCalculator.effectiveOvulationDay(lastLog.startDateEpochDay, periodDays, nextStart)),
                        stringResource(R.string.cycle_window_range, formatDate(ovuRange.first), formatDate(ovuRange.last)),
                        Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    stringResource(R.string.cycle_no_record_overview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.cycle_tips_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showTips = true }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.cycle_disclaimer_calendar),
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
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showRegister = false }) { Text(stringResource(R.string.common_cancel)) } }
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
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showBackfill = false }) { Text(stringResource(R.string.common_cancel)) } }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    stringResource(R.string.cycle_backfill_history_period),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    when {
                        startDay == null || selEnd == null -> stringResource(R.string.cycle_select_period_range)
                        !valid -> stringResource(R.string.cycle_period_days_range, CycleCalculator.MIN_PERIOD_DAYS, CycleCalculator.MAX_PERIOD_DAYS)
                        startDay != null && unreasonableLogReason(startDay, days) != null ->
                            stringResource(R.string.cycle_overlap_early_note)
                        else -> stringResource(R.string.cycle_will_record_n, formatRange(startDay, days), days)
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
        // remember 的 calculation 是 @DisallowComposableCalls，这里不能调 stringResource；
        // 用 Tr.s 取（等价、且非 Composable 位置通用）。
        var noteText by remember(pending) { mutableStateOf(Tr.s(R.string.cycle_default_special_note)) }
        AlertDialog(
            onDismissRequest = { pendingSpecial = null },
            title = { Text(stringResource(R.string.cycle_confirm_special)) },
            text = {
                Column {
                    // 三种 reason 文案结尾并不统一（「日期重叠」无句号，另两种自带句号），
                    // 直接拼接会产出「。。」——统一去掉结尾句号后由此处补一个
                    Text(pending.reason.trimEnd('。') + stringResource(R.string.cycle_special_note_suffix))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(50) },
                        label = { Text(stringResource(R.string.cycle_note_optional_label)) },
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
                }) { Text(stringResource(R.string.cycle_save_as_special)) }
            },
            dismissButton = { TextButton(onClick = { pendingSpecial = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // 结束本次经期确认：避免误触；明确「今天计为经期最后一天」的口径
    if (showEndConfirm) {
        val log = activeLog
        if (log != null) {
            val diff = (today - log.startDateEpochDay + 1).toInt()
            AlertDialog(
                onDismissRequest = { showEndConfirm = false },
                title = { Text(stringResource(R.string.cycle_end_period_confirm_title)) },
                text = {
                    Text(
                        stringResource(R.string.cycle_end_period_confirm_text, formatRange(log.startDateEpochDay, diff), diff, formatDate(today))
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            container.cycleRepository.update(log.copy(periodDays = diff))
                            scope.launch { runCatching { container.cycleEventBridge.syncEvent() } }
                        }
                        showEndConfirm = false
                    }) { Text(stringResource(R.string.cycle_confirm_end)) }
                },
                dismissButton = { TextButton(onClick = { showEndConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
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
                TextButton(onClick = { showTips = false }) { Text(stringResource(R.string.common_ok)) }
            },
            title = { Text(stringResource(R.string.cycle_tips_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.cycle_tips_function),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_function_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_daily_log),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_daily_log_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_phases),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_phases_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_calendar_marks),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_calendar_marks_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_accuracy),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.cycle_tips_accuracy_body),
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

    // 修改记录弹窗：入口在选中日详情区每一行右侧的铅笔
    val noteToEdit = editingNote
    if (noteToEdit != null) {
        EditNoteDialog(
            note = noteToEdit,
            onDismiss = { editingNote = null },
            onConfirm = { updated ->
                scope.launch { container.cycleNoteRepository.update(updated) }
                editingNote = null
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
                stringResource(R.string.cycle_no_record),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } else {
            val nextStart = CycleCalculator.nextStartAfter(lastStart, cycleDays)
            val overdue = today >= nextStart
            val phase = CycleCalculator.phaseOf(today, lastStart, periodDays, cycleDays)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (todayIsPeriodEnd) stringResource(R.string.cycle_today_end)
                        else stringResource(phase.labelRes),
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
                        stringResource(R.string.cycle_register_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        stringResource(R.string.cycle_day_n, (today - lastStart + 1)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        stringResource(R.string.cycle_days_to_next_period_n, (nextStart - today)),
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
    val entryEnabled by container.settingsRepository.cycleEntryEnabled.collectAsState(initial = false)
    /** 「周期管家快捷事件」的默认标题。名称留空时回落到它，重命名弹窗里也用同一个值。 */
    val cycleTitleDef = stringResource(R.string.cycle_title)
    val eventTitle by container.settingsRepository.cycleEventTitle.collectAsState(initial = cycleTitleDef)
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
                title = { Text(stringResource(R.string.cycle_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            Text(stringResource(R.string.cycle_params_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            ToggleRow(
                title = stringResource(R.string.cycle_auto_cycle_days),
                subtitle = when {
                    cycleAuto && avg != null -> stringResource(R.string.cycle_auto_cycle_sub_calc, CycleCalculator.AVG_WINDOW, avg)
                    cycleAuto -> stringResource(R.string.cycle_auto_insufficient)
                    else -> stringResource(R.string.cycle_auto_off_manual)
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
                label = stringResource(R.string.cycle_label_cycle_days),
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
                title = stringResource(R.string.cycle_auto_period_days),
                subtitle = when {
                    periodAuto && periodAvg != null -> stringResource(R.string.cycle_auto_period_sub_calc, CycleCalculator.AVG_WINDOW, periodAvg)
                    periodAuto -> stringResource(R.string.cycle_auto_period_insufficient)
                    else -> stringResource(R.string.cycle_auto_off_manual)
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
                label = stringResource(R.string.cycle_label_period_days),
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
            Text(stringResource(R.string.cycle_default_view), style = MaterialTheme.typography.titleSmall)
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
                    ViewToggle(stringResource(R.string.cycle_view_ring), selected = !defaultCalendar) {
                        scope.launch { container.settingsRepository.setCycleDefaultCalendar(false) }
                    }
                    ViewToggle(stringResource(R.string.cycle_view_calendar), selected = defaultCalendar) {
                        scope.launch { container.settingsRepository.setCycleDefaultCalendar(true) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.cycle_default_view_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )

            Spacer(Modifier.height(20.dp))

            if (logs.isEmpty()) {
                Text(
                    stringResource(R.string.cycle_no_record_settings),
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
                        stringResource(R.string.cycle_history_manage_n, logs.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cycle_enter),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 隐私与快捷事件 =====
            Text(stringResource(R.string.cycle_privacy_quick), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            ToggleRow(
                title = stringResource(R.string.cycle_title_password_protect),
                subtitle = if (vaultSet) stringResource(R.string.cycle_sub_password_set) else stringResource(R.string.cycle_sub_password_need),
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
                title = stringResource(R.string.cycle_title_show_event),
                stringResource(R.string.cycle_sub_show_event),
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
                    Text(stringResource(R.string.cycle_event_name_custom, eventTitle))
                }
            }
            ToggleRow(
                title = stringResource(R.string.cycle_title_show_entry),
                stringResource(R.string.cycle_sub_show_entry),
                checked = entryEnabled,
                enabled = true
            ) { want ->
                scope.launch { container.settingsRepository.setCycleEntryEnabled(want) }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.cycle_method_note),
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
            title = { Text(stringResource(R.string.cycle_delete_record_title)) },
            text = { Text(stringResource(R.string.cycle_delete_record_text, formatRange(log.startDateEpochDay, log.periodDays))) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.cycleRepository.delete(log)
                        deletingLog = null
                        syncEvent()
                    }
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingLog = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (showEventNameDialog) {
        var name by remember { mutableStateOf(eventTitle) }
        AlertDialog(
            onDismissRequest = { showEventNameDialog = false },
            title = { Text(stringResource(R.string.cycle_event_name_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text(stringResource(R.string.cycle_event_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.settingsRepository.setCycleEventTitle(name.ifBlank { cycleTitleDef })
                        syncEvent()
                        showEventNameDialog = false
                    }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showEventNameDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (showPasswordNeedVault) {
        AlertDialog(
            onDismissRequest = { showPasswordNeedVault = false },
            title = { Text(stringResource(R.string.cycle_need_vault_title)) },
            text = { Text(stringResource(R.string.cycle_need_vault_text)) },
            confirmButton = {
                TextButton(onClick = { showPasswordNeedVault = false }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }

    if (showNeedLog) {
        AlertDialog(
            onDismissRequest = { showNeedLog = false },
            title = { Text(stringResource(R.string.cycle_register_first_title)) },
            text = { Text(stringResource(R.string.cycle_register_first_text)) },
            confirmButton = {
                TextButton(onClick = { showNeedLog = false }) { Text(stringResource(R.string.common_ok)) }
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
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text(
                stringResource(R.string.cycle_edit_record_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                when {
                    startDay == null || selEnd == null -> stringResource(R.string.cycle_reselect_range)
                    overlap -> stringResource(R.string.cycle_overlap_adjust)
                    !valid -> stringResource(R.string.cycle_period_days_range, CycleCalculator.MIN_PERIOD_DAYS, CycleCalculator.MAX_PERIOD_DAYS)
                    else -> stringResource(R.string.cycle_will_change_n, formatRange(startDay, days), days)
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
                label = { Text(stringResource(R.string.cycle_note_special_optional)) },
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
            stringResource(R.string.cycle_value_days_n, value),
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
    val cycleErrEmpty = stringResource(R.string.cycle_error_empty_password)
    val cycleErrWrong = stringResource(R.string.cycle_error_wrong_password)
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
            .setTitle(act.getString(R.string.cycle_biometric_title))
            .setSubtitle(act.getString(R.string.cycle_biometric_subtitle))
            .setNegativeButtonText(act.getString(R.string.common_cancel))
            .build()
        prompt.authenticate(info)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cycle_title)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                // 提示一律写在输入框**下方**的小字里（supportingText），不放进框内。
                // 框内提示（label 未聚焦态 / placeholder）长得像「已经填好的内容」，
                // 而这里要传达的是「还没输入，该输什么」——放下面才不会被误读。
                // 出错时同一位置换成错误文案并染红，避免下方叠两行字。
                supportingText = { Text(error ?: stringResource(R.string.cycle_input_password)) },
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val h = hash ?: return@Button
                    val s = salt ?: return@Button
                    if (password.isBlank()) {
                        error = cycleErrEmpty
                        return@Button
                    }
                    val ok = try {
                        VaultCrypto.hash(password, s) == h
                    } catch (e: Exception) {
                        false
                    }
                    if (ok) onUnlocked() else error = cycleErrWrong
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cycle_unlock))
            }
            if (biometricAvailable && biometricEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { authenticateWithBiometric() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cycle_use_fingerprint))
                }
            }
        }
    }
}

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(LocaleWrap.dateFormatter(R.string.date_pattern_md))

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
                title = { Text(stringResource(R.string.cycle_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                    ViewToggle(stringResource(R.string.cycle_tab_period), selected = tab == 0) { tab = 0 }
                    ViewToggle(stringResource(R.string.cycle_tab_daily), selected = tab == 1) { tab = 1 }
                }
            }

            val empty = if (tab == 0) logs.isEmpty() else noteGroups.isEmpty()
            if (empty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (tab == 0) stringResource(R.string.cycle_no_period_records) else stringResource(R.string.cycle_no_daily_records),
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
                                    stringResource(R.string.cycle_log_range_days, formatRange(log.startDateEpochDay, log.periodDays), log.periodDays),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                val dow = LocalDate.ofEpochDay(log.startDateEpochDay)
                                    .dayOfWeek.getDisplayName(TextStyle.FULL, LocaleWrap.locale())
                                Text(
                                    dow,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                log.note?.let { note ->
                                    Text(
                                        stringResource(R.string.cycle_note_prefix, note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            TextButton(onClick = { onEdit(log) }) { Text(stringResource(R.string.cycle_adjust)) }
                            IconButton(onClick = { onDelete(log) }) {
                                Icon(
                                    Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete),
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
                                        contentDescription = stringResource(R.string.cycle_delete_day_records),
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
 *  - **选中**：3dp 主色粗框 + 内侧 2dp 白衬。粗框给「用户主动点开的那天」——最需要一眼锁定，所以给最重的视觉。
 *    白衬是关键：排卵期底色用的是 Material 默认 tertiary 紫红（#7D5260），与主色 #00668C 同属暗色，
 *    单色框在排卵期里几乎看不见；加一圈白衬后粗框在任何阶段底色上都跳得出来，也不必牺牲品牌色。
 *  - **今日**：1.5dp 主色细框 + 内侧 1.5dp 白衬。与选中同一套视觉语言，**仅以粗细区分**，用户不必学两套规则。
 *  - **有记录**：右上角一枚小圆点。位置与「圆点在数字下方」的经期语义天然分开，不会与实心/空心圆点混淆。
 *
 * 同一格既是选中又是今日时，**只画粗的那道**（选中优先）。
 * 详情区收起后 [selectedDay] 归 null，粗框随之消失——框表达的是「当前正在查看这天」，而不是给那天永久盖戳。
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
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cycle_prev_month))
            }
            Text(
                month.format(LocaleWrap.dateFormatter(R.string.date_pattern_ym)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { monthOffset += 1 }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = stringResource(R.string.cycle_next_month))
            }
        }
        Spacer(Modifier.height(4.dp))
        // 星期表头（周一开始）
        Row(Modifier.fillMaxWidth()) {
            val weekLocale = LocaleWrap.locale()
            (1..7).map {
                java.time.DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, weekLocale)
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
                                // 选中优先：粗框＝「我正在看的这天」。收起详情区后 selectedDay 归 null，
                                // 粗框随之消失，只留今天的细框——框表达的是「当前正在查看」，不是永久标注。
                                if (isSelected) {
                                    // 选中：粗主色框 + 白衬（白衬保证在排卵期紫红底上也清晰）
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
                                } else if (isToday) {
                                    // 今日：同款视觉语言，仅比选中细一半，一眼可分
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
): Pair<String, String> { // TODO(i18n): 见 skipped
    if (logs.isEmpty()) {
        return Tr.s(R.string.cycle_no_records) to Tr.s(R.string.cycle_no_records_hint)
    }

    // 落在任一已登记经期区间内 → 月经期（按该记录自身的持续天数）
    logs.firstOrNull { day in CycleCalculator.periodRange(it.startDateEpochDay, it.periodDays) }
        ?.let {
            return Tr.s(R.string.cycle_phase_period) to
                Tr.s(R.string.cycle_day_n, day - it.startDateEpochDay + 1)
        }

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
            Tr.s(R.string.cycle_predict_period) to Tr.s(
                R.string.cycle_predict_not_logged,
                if (day > today) Tr.s(R.string.unit_days_future, day - today)
                else Tr.s(R.string.unit_days_past, today - day)
            )
        day == ovu ->
            Tr.s(R.string.cycle_ovulation_day) to Tr.s(
                R.string.cycle_ovu_best_sub, formatDate(window.first), formatDate(window.last)
            )
        phase == CycleCalculator.Phase.OVULATION ->
            Tr.s(R.string.cycle_phase_ovulation) to Tr.s(
                R.string.cycle_ovu_phase_sub,
                formatDate(ovu), formatDate(window.first), formatDate(window.last)
            )
        phase == CycleCalculator.Phase.LUTEAL ->
            Tr.s(R.string.cycle_phase_luteal) to Tr.s(R.string.cycle_luteal_sub_n, nextStart - day)
        else ->
            Tr.s(R.string.cycle_phase_follicular) to Tr.s(R.string.cycle_follicular_sub_n, ovu - day)
    }
}

/** 阶段标签的配色（与圆环/日历同一套色板，保证三处说法一致）。 */
@Composable
private fun phaseChipColors(label: String): Pair<Color, Color> = when (label) {
    stringResource(R.string.cycle_phase_period) ->
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    stringResource(R.string.cycle_predict_period) ->
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) to MaterialTheme.colorScheme.onSurface
    stringResource(R.string.cycle_ovulation_day), stringResource(R.string.cycle_phase_ovulation) ->
        MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
    stringResource(R.string.cycle_phase_follicular) ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else ->
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) to MaterialTheme.colorScheme.onSurface
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

/**
 * 一条日常记录：左侧大类色条 + 名称 +（可选）补充说明 +（可选）修改入口。
 *
 * [onEdit] 只在选中日详情区传入——历史页是只读列表，不给入口（那里只做浏览与删除）。
 */
@Composable
private fun NoteRow(note: CycleNoteEntity, onEdit: (() -> Unit)? = null) {
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
        Text(NoteCatalog.displayLabel(note.presetKey, note.label), style = MaterialTheme.typography.bodyMedium)
        val extra = note.note
        if (extra != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                extra,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else if (onEdit != null) {
            // 没有补充说明时，补一段弹性空白把修改按钮顶到行尾，两种行型的按钮位置才一致
            Spacer(Modifier.weight(1f))
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = Tr.s(R.string.common_edit),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
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
    /** 收起详情区（＝取消选中）。点「今天」同样会展开详情，所以这个按钮的语义是「收起」，不是「回到今天」。 */
    onCollapse: () -> Unit,
    onAdd: () -> Unit,
    /** 修改某一条已有记录（入口就挂在那一行右侧）。 */
    onEdit: (CycleNoteEntity) -> Unit,
    onDelete: () -> Unit
) {
    val (phaseLabel, sub) = describeDay(day, logs, cycleDays, today)
    val (chipBg, chipFg) = phaseChipColors(phaseLabel)
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
                .padding(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (day == today) stringResource(R.string.cycle_today_prefix, dateText) else dateText,
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
                stringResource(R.string.cycle_day_records),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            if (dayNotes.isEmpty()) {
                Text(
                    stringResource(R.string.cycle_no_record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            } else {
                dayNotes.forEach { n -> NoteRow(n, onEdit = { onEdit(n) }) }
            }

            Spacer(Modifier.height(12.dp))
            // 层级约定：实心 = 会动数据的执行动作；空心 = 次级/破坏性动作
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cycle_add_record), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = dayNotes.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cycle_delete_record), maxLines = 1)
                }
            }
            // 始终显示：点「今天」也会展开详情，此时同样需要一条收起的出口（原先只在非今天时显示，是个死路）
            TextButton(onClick = onCollapse, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.common_collapse))
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
        title = { Text(stringResource(R.string.cycle_add_note_title, formatDate(day))) },
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
                        NoteChip(stringResource(c.labelRes), selected = category == c) { switchTo(c) }
                    }
                    NoteChip(stringResource(R.string.cycle_chip_custom), selected = isCustom) { switchTo(NoteCatalog.Category.CUSTOM) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                if (isCustom) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.take(NoteCatalog.MAX_LABEL_LENGTH) },
                        label = { Text(stringResource(R.string.cycle_label_content)) },
                        placeholder = { Text(stringResource(R.string.cycle_placeholder_example)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // 性生活整类都是互斥项 → 实际只能选一个，提示要跟着改成「单选」，
                    // 否则写「可多选」会误导（用户实测报出「自我愉悦 + 有保护措施」能同时勾上）
                    Text(
                        stringResource(
                            if (NoteCatalog.isSingleChoice(category)) R.string.cycle_select_one
                            else R.string.cycle_select_items
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NoteCatalog.presets[category].orEmpty().forEach { p ->
                            NoteChip(stringResource(p.labelRes), selected = p.key in selectedKeys) {
                                selectedKeys = if (p.key in selectedKeys) {
                                    selectedKeys - p.key
                                } else {
                                    // 互斥组内先踢掉同组其它项，否则会落一条「既保护又无保护」的矛盾记录
                                    val group = NoteCatalog.exclusiveGroups[p.key]
                                    val base = if (group == null) selectedKeys
                                    else selectedKeys.filterNot {
                                        NoteCatalog.exclusiveGroups[it] == group
                                    }.toSet()
                                    base + p.key
                                }
                            }
                        }
                    }
                    // 勾选后即时显示对应的小字提示（取消勾选即消失），出去再补一条记录就太晚了
                    NoteCatalog.hintsOf(selectedKeys).forEach { NoteHint(it) }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(NoteCatalog.MAX_NOTE_LENGTH) },
                    label = { Text(stringResource(R.string.cycle_label_extra_note)) },
                    placeholder = { Text(stringResource(R.string.cycle_placeholder_extra)) },
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
                                    label = Tr.s(it.labelRes),
                                    note = extra,
                                    createdAt = now
                                )
                            }
                    }
                    onConfirm(items)
                }
            ) { Text(stringResource(R.string.common_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
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
        title = { Text(stringResource(R.string.cycle_delete_notes_title, formatDate(day))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.cycle_delete_notes_hint),
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
                            Text(NoteCatalog.displayLabel(n.presetKey, n.label), style = MaterialTheme.typography.bodyMedium)
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
            ) { Text(stringResource(R.string.cycle_delete_selected_n, checkedIds.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/**
 * 预置项小字提示：带一层极浅底色的注记块。
 *
 * 用带底色的块而不是裸文本，是因为它紧跟在选中态 chip 下面——裸文本会被读成
 * 「又一个没选中的选项」；浅底把它明确划成「补充说明」而不是「可点项」。
 */
@Composable
private fun NoteHint(resId: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(resId),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * 修改记录弹窗：入口在选中日详情区每一行的右侧。
 *
 * 与添加弹窗共用 chip 外观与字段，但**预置项是单选**——一条记录只承载一个预置项
 * （添加时勾多个会落成多条记录），改成多选就无法表达「改的是哪一条」。
 * 自定义记录回落到自定义文本输入框，与添加弹窗的语义一致。
 *
 * 保存时保留原记录的 id / 日期 / 创建时间，只替换内容字段：id 不变才是原地更新，
 * 否则会变成「删旧增新」，历史排序与创建时间会一起乱掉。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditNoteDialog(
    note: CycleNoteEntity,
    onDismiss: () -> Unit,
    onConfirm: (CycleNoteEntity) -> Unit
) {
    // 以 note.id 作 remember key：连续修改不同记录时，上一次的草稿必须重来一遍
    var category by remember(note.id) {
        mutableStateOf(
            NoteCatalog.categoryOfPreset(note.presetKey) ?: NoteCatalog.Category.of(note.category)
        )
    }
    var selectedKey by remember(note.id) { mutableStateOf(note.presetKey) }
    // 自定义记录才有内容文本；预置项记录的 label 是写入时的语言文本，不该回填进输入框
    var customText by remember(note.id) {
        mutableStateOf(if (note.presetKey == null) note.label else "")
    }
    var noteText by remember(note.id) { mutableStateOf(note.note.orEmpty()) }

    val isCustom = category == NoteCatalog.Category.CUSTOM
    val canSave = if (isCustom) customText.trim().isNotEmpty() else selectedKey != null

    fun switchTo(c: NoteCatalog.Category) {
        category = c
        selectedKey = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cycle_edit_note_title, formatDate(note.dateEpochDay))) },
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
                        NoteChip(stringResource(c.labelRes), selected = category == c) { switchTo(c) }
                    }
                    NoteChip(
                        stringResource(R.string.cycle_chip_custom),
                        selected = isCustom
                    ) { switchTo(NoteCatalog.Category.CUSTOM) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                if (isCustom) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.take(NoteCatalog.MAX_LABEL_LENGTH) },
                        label = { Text(stringResource(R.string.cycle_label_content)) },
                        placeholder = { Text(stringResource(R.string.cycle_placeholder_example)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        stringResource(R.string.cycle_select_one),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NoteCatalog.presets[category].orEmpty().forEach { p ->
                            // 单选且不提供取消：点已选中的项保持原样，避免出现「记录没有内容」的中间态
                            NoteChip(stringResource(p.labelRes), selected = p.key == selectedKey) {
                                selectedKey = p.key
                            }
                        }
                    }
                    NoteCatalog.hintsOf(listOfNotNull(selectedKey)).forEach { NoteHint(it) }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(NoteCatalog.MAX_NOTE_LENGTH) },
                    label = { Text(stringResource(R.string.cycle_label_extra_note)) },
                    placeholder = { Text(stringResource(R.string.cycle_placeholder_extra)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val key = selectedKey
                    onConfirm(
                        note.copy(
                            category = if (isCustom) NoteCatalog.CUSTOM_KEY else category.key,
                            presetKey = if (isCustom) null else key,
                            // 预置项按当前语言重新落文案；自定义项就是用户输入本身
                            label = if (isCustom) customText.trim()
                            else key?.let { k -> NoteCatalog.presetResOf(k)?.let { Tr.s(it) } }
                                ?: note.label,
                            note = noteText.trim().ifEmpty { null }
                        )
                    )
                }
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
