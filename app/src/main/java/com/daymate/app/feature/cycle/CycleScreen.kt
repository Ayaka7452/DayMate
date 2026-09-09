package com.ayaka7452.daymate.feature.cycle

import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.util.CycleCalculator
import com.ayaka7452.daymate.core.security.VaultCrypto
import com.ayaka7452.daymate.data.db.CycleLogEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DateFmt = DateTimeFormatter.ofPattern("yyyy年M月d日")

/**
 * 周期管家：经期登记 + 月经期/卵泡期/排卵期/黄体期推算。
 * 密码保护默认关闭；开启后复用 Vault 密码与指纹验证（Vault 未设密码时先引导去设置）。
 */
@Composable
fun CycleScreen(
    container: AppContainer,
    onExit: () -> Unit
) {
    val passwordEnabled by container.settingsRepository.cyclePasswordEnabled
        .collectAsState(initial = false)
    var unlocked by remember { mutableStateOf(!passwordEnabled) }
    // 密码开关变化时即时生效（在页面内直接开/关）
    LaunchedEffect(passwordEnabled) {
        if (!passwordEnabled) unlocked = true
    }

    if (!unlocked) {
        CycleUnlockGate(
            container = container,
            onUnlocked = { unlocked = true },
            onExit = onExit
        )
        return
    }
    CycleManagerScreen(container = container, onExit = onExit)
}

/** 解锁门：验证 Vault 密码（只验证不解密），支持指纹（跟随 Vault 的指纹开关）。 */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleManagerScreen(
    container: AppContainer,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logs by container.cycleRepository.observeAll().collectAsState(initial = emptyList())
    val cycleDays by container.settingsRepository.cycleDays.collectAsState(initial = 28)
    val periodDays by container.settingsRepository.cyclePeriodDays.collectAsState(initial = 5)
    val passwordEnabled by container.settingsRepository.cyclePasswordEnabled.collectAsState(initial = false)
    val eventEnabled by container.settingsRepository.cycleEventEnabled.collectAsState(initial = false)
    val eventTitle by container.settingsRepository.cycleEventTitle.collectAsState(initial = "周期管家")
    val vaultSet by container.settingsRepository.vaultPasswordSet.collectAsState(initial = false)

    var showAddPicker by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<CycleLogEntity?>(null) }
    var avgHintDismissed by remember { mutableStateOf(false) }
    var showEventNameDialog by remember { mutableStateOf(false) }
    var showPasswordNeedVault by remember { mutableStateOf(false) }

    // 防截屏/最近任务缩略图遮挡（与 Vault 一致）
    val activity = context as? androidx.fragment.app.FragmentActivity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val today = LocalDate.now().toEpochDay()
    val lastLog = logs.firstOrNull()
    val avg = container.cycleRepository.averageCycleDays(logs)
    val nextStart = lastLog?.let { CycleCalculator.nextStartAfter(it.startDateEpochDay, cycleDays) }
    val overdue = nextStart != null && today >= nextStart
    val phase = lastLog?.let {
        CycleCalculator.phaseOf(today, it.startDateEpochDay, periodDays, cycleDays)
    }

    fun syncEvent() {
        scope.launch { runCatching { container.cycleEventBridge.syncEvent() } }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ===== 当前状态卡 =====
            if (lastLog != null && nextStart != null && phase != null) {
                val phaseColor = when (phase) {
                    CycleCalculator.Phase.PERIOD -> MaterialTheme.colorScheme.primary
                    CycleCalculator.Phase.OVULATION -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        phase.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = phaseColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    if (overdue) {
                        Text(
                            "已到预测日期，请登记本次经期",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        val diff = nextStart - today
                        Text(
                            "距下次经期还有 $diff 天",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "预测下次经期：" + formatDate(nextStart) +
                            "（排卵日 " + formatDate(CycleCalculator.ovulationDay(nextStart)) + "）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // ===== 手动设置：周期天数 / 持续天数（± 步进） =====
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
            Button(
                onClick = { showAddPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (overdue || lastLog == null) "登记本次经期" else "补记 / 更正既往经期") }
            Spacer(Modifier.height(16.dp))

            if (logs.isEmpty()) {
                Text(
                    "还没有记录。登记最近一次经期首日后，这里会显示月经期、卵泡期、排卵期和黄体期的推算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                Text("历史记录", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                logs.forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                        TextButton(onClick = { editingLog = log }) { Text("调整") }
                        IconButton(onClick = {
                            scope.launch {
                                container.cycleRepository.delete(log)
                                syncEvent()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== 隐私与快捷事件 =====
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
                subtitle = "作为一个普通事件存在：可移动、放入文件夹、置顶；点击直达本页，日期自动跟随预测滚动",
                checked = eventEnabled,
                enabled = true
            ) { want ->
                scope.launch {
                    container.settingsRepository.setCycleEventEnabled(want)
                    syncEvent()
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

    // ===== 弹窗层 =====
    val addPickerState = rememberDatePickerState()
    if (showAddPicker) {
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
}

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

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateFmt)
