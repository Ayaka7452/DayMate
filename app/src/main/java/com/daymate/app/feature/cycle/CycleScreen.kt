package com.ayaka7452.daymate.feature.cycle

import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.core.util.CycleCalculator
import com.ayaka7452.daymate.data.db.CycleLogEntity
import kotlinx.coroutines.launch
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
    val passwordEnabled by container.settingsRepository.cyclePasswordEnabled
        .collectAsState(initial = false)
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

    if (locked) {
        CycleUnlockGate(
            container = container,
            onUnlocked = { unlockedByUser = true },
            onExit = onExit
        )
        return
    }
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

// ============================ 主视图（圆环） ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleOverviewScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val logs by container.cycleRepository.observeAll().collectAsState(initial = emptyList())
    val cycleDays by container.settingsRepository.cycleDays.collectAsState(initial = CycleCalculator.DEFAULT_CYCLE_DAYS)
    val periodDays by container.settingsRepository.cyclePeriodDays.collectAsState(initial = CycleCalculator.DEFAULT_PERIOD_DAYS)

    val today = LocalDate.now().toEpochDay()
    val lastLog = logs.firstOrNull()
    val scope = rememberCoroutineScope()
    var showRegister by remember { mutableStateOf(false) }
    var showTips by remember { mutableStateOf(false) }
    val overdue = lastLog != null && today >= CycleCalculator.nextStartAfter(lastLog.startDateEpochDay, cycleDays)

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

            // ===== 圆环周期图 =====
            CycleRing(
                lastStart = lastLog?.startDateEpochDay,
                periodDays = periodDays,
                cycleDays = cycleDays,
                today = today
            )

            Spacer(Modifier.height(20.dp))

            // ===== 图例 =====
            val legendPeriod = MaterialTheme.colorScheme.primary
            val legendFollicular = MaterialTheme.colorScheme.secondaryContainer
            val legendOvulation = MaterialTheme.colorScheme.tertiary
            val legendLuteal = Color(0x24000000)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(legendPeriod, "月经期")
                LegendDot(legendFollicular, "卵泡期")
                LegendDot(legendOvulation, "排卵期")
                LegendDot(legendLuteal, "黄体期")
            }

            Spacer(Modifier.height(20.dp))

            // ===== 关键日期 =====
            if (lastLog != null) {
                val nextStart = CycleCalculator.nextStartAfter(lastLog.startDateEpochDay, cycleDays)
                val overdue = today >= nextStart
                val phase = CycleCalculator.phaseOf(today, lastLog.startDateEpochDay, periodDays, cycleDays)
                InfoRow("上次经期", formatDate(lastLog.startDateEpochDay) + " · " + lastLog.periodDays + "天")
                InfoRow("当前阶段", phase.label)
                if (overdue) {
                    InfoRow("下次经期", "已到预测日期，请登记本次经期", highlight = true)
                } else {
                    InfoRow(
                        "下次经期",
                        formatDate(nextStart) + "（还有 " + (nextStart - today) + " 天）",
                        highlight = true
                    )
                }
                InfoRow("排卵日", formatDate(CycleCalculator.ovulationDay(nextStart)))
                InfoRow("排卵期窗口", formatDate(CycleCalculator.ovulationRange(nextStart).first) +
                    " ~ " + formatDate(CycleCalculator.ovulationRange(nextStart).last))
            } else {
                Text(
                    "还没有登记记录。\n点击右上角设置，登记最近一次经期首日后，这里会显示完整的周期推算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings) { Text("去设置并登记") }
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

    // 逾期登记弹窗（主视图直达）
    if (showRegister && lastLog != null) {
        // 默认选中今天：直接点确定即登记今天，避免不触摸日期选择器时静默无效果
        val registerState = rememberDatePickerState(
            initialSelectedDateMillis = today * 86400000L
        )
        DatePickerDialog(
            onDismissRequest = { showRegister = false },
            confirmButton = {
                TextButton(onClick = {
                    registerState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        scope.launch {
                            container.cycleRepository.add(
                                CycleLogEntity(startDateEpochDay = day, periodDays = periodDays)
                            )
                            scope.launch { runCatching { container.cycleEventBridge.syncEvent() } }
                        }
                    }
                    showRegister = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRegister = false }) { Text("取消") } }
        ) { DatePicker(state = registerState) }
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
    val lutealColor = Color(0x24000000)
    val trackColor = Color(0x14000000)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26.dp.toPx()
            val ringSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            // 分段（0-based 天偏移，clamp 保证有序且总和 = cycleDays）
            val ovuWinStart = maxOf(periodDays.toFloat(), (cycleDays - 20).toFloat())
            val lutealStart = maxOf(ovuWinStart + 0.5f, (cycleDays - 13).toFloat())
            val segments = listOf(
                periodDays.toFloat() to periodColor,
                (ovuWinStart - periodDays) to follicularColor,
                (lutealStart - ovuWinStart) to ovulationColor,
                (cycleDays - lutealStart) to lutealColor
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
    val cycleDays by container.settingsRepository.cycleDays.collectAsState(initial = CycleCalculator.DEFAULT_CYCLE_DAYS)
    val periodDays by container.settingsRepository.cyclePeriodDays.collectAsState(initial = CycleCalculator.DEFAULT_PERIOD_DAYS)
    val passwordEnabled by container.settingsRepository.cyclePasswordEnabled.collectAsState(initial = false)
    val eventEnabled by container.settingsRepository.cycleEventEnabled.collectAsState(initial = false)
    val eventTitle by container.settingsRepository.cycleEventTitle.collectAsState(initial = "周期管家")
    val vaultSet by container.settingsRepository.vaultPasswordSet.collectAsState(initial = false)

    var showAddPicker by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<CycleLogEntity?>(null) }
    var deletingLog by remember { mutableStateOf<CycleLogEntity?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var avgHintDismissed by remember { mutableStateOf(false) }
    var showEventNameDialog by remember { mutableStateOf(false) }
    var showPasswordNeedVault by remember { mutableStateOf(false) }
    var showNeedLog by remember { mutableStateOf(false) }

    val today = LocalDate.now().toEpochDay()
    val lastLog = logs.firstOrNull()
    val avg = container.cycleRepository.averageCycleDays(logs)
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
            // ===== 周期参数（± 步进） =====
            Text("周期参数", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SettingStepper(
                label = "周期天数",
                value = cycleDays,
                range = CycleCalculator.MIN_CYCLE_DAYS..CycleCalculator.MAX_CYCLE_DAYS
            ) { v ->
                scope.launch {
                    container.settingsRepository.setCycleDays(v)
                    syncEvent()
                }
            }
            if (avg != null && avg != cycleDays && !avgHintDismissed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "您近${CycleCalculator.AVG_WINDOW}次平均周期为 $avg 天，建议更新设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { avgHintDismissed = true }) { Text("✕") }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingStepper(
                label = "经期持续天数",
                value = periodDays,
                range = CycleCalculator.MIN_PERIOD_DAYS..CycleCalculator.MAX_PERIOD_DAYS
            ) { v ->
                scope.launch { container.settingsRepository.setCyclePeriodDays(v) }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 登记 =====
            Text("经期登记", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showAddPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (overdue || lastLog == null) "登记本次经期" else "补记 / 更正既往经期") }
            Spacer(Modifier.height(12.dp))

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
                "⚠️ 本功能采用日历法推算（黄体期按固定 14 天近似，排卵日 = 预测下次经期首日 − 14 天）。" +
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
    if (showAddPicker) {
        // 默认选中今天：直接点确定即登记今天
        val addPickerState = rememberDatePickerState(
            initialSelectedDateMillis = today * 86400000L
        )
        DatePickerDialog(
            onDismissRequest = { showAddPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    addPickerState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        scope.launch {
                            container.cycleRepository.add(
                                CycleLogEntity(startDateEpochDay = day, periodDays = periodDays)
                            )
                            syncEvent()
                        }
                    }
                    showAddPicker = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddPicker = false }) { Text("取消") } }
        ) { DatePicker(state = addPickerState) }
    }

    if (editingLog != null) {
        val log = editingLog!!
        var daysText by remember(log.id) { mutableStateOf(log.periodDays.toString()) }
        AlertDialog(
            onDismissRequest = { editingLog = null },
            title = { Text("调整经期天数") },
            text = {
                Column {
                    Text(formatDate(log.startDateEpochDay) + " 开始的这次经期", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("持续天数（${CycleCalculator.MIN_PERIOD_DAYS}~${CycleCalculator.MAX_PERIOD_DAYS}）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = daysText.toIntOrNull()
                    if (v != null && CycleCalculator.isPeriodDaysValid(v)) {
                        scope.launch {
                            container.cycleRepository.update(log.copy(periodDays = v))
                            editingLog = null
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingLog = null }) { Text("取消") } }
        )
    }

    if (deletingLog != null) {
        val log = deletingLog!!
        AlertDialog(
            onDismissRequest = { deletingLog = null },
            title = { Text("删除这条记录？") },
            text = { Text(formatDate(log.startDateEpochDay) + " 开始的经期记录将被删除，推算将基于剩余的记录进行。") },
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
            text = { Text("快捷事件的日期来自周期推算，需要至少一次经期登记。请先在上方「登记本次经期」，登记后快捷事件会自动创建并显示在主页。") },
            confirmButton = {
                TextButton(onClick = { showNeedLog = false }) { Text("知道了") }
            }
        )
    }
}

// ============================ 通用组件 ============================

@Composable
private fun SettingStepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { if (value - 1 >= range.first) onChange(value - 1) }
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(
            "$value 天",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        OutlinedButton(
            onClick = { if (value + 1 <= range.last) onChange(value + 1) }
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
                                formatDate(log.startDateEpochDay) + " · " + log.periodDays + "天",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val dow = LocalDate.ofEpochDay(log.startDateEpochDay)
                                .dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
                            Text(
                                dow,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
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
