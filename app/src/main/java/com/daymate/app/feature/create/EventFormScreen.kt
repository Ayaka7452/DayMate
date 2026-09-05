package com.ayaka7452.daymate.feature.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayaka7452.daymate.core.AppContainer
import com.ayaka7452.daymate.core.util.CountdownCalculator
import com.ayaka7452.daymate.data.db.EventEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormScreen(
    container: AppContainer,
    eventId: Long? = null,
    folderId: Long? = null,
    prefillTitle: String? = null,
    prefillEpochDay: Long? = null,
    prefillFestival: String? = null,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(prefillTitle ?: "") }
    var note by remember { mutableStateOf("") }
    var epochDay by remember {
        mutableStateOf(prefillEpochDay ?: LocalDate.now().plusDays(7).toEpochDay())
    }
    var refDaysText by remember { mutableStateOf("") }
    var displayUnit by remember { mutableStateOf(CountdownCalculator.UNIT_DAY) }
    var repeatRule by remember { mutableStateOf<String?>(null) }
    // 跟随节日：从节日快选或主页节日卡片进入时预置；保存后随事件持久化
    var linkedFestival by remember { mutableStateOf(prefillFestival) }
    var loaded by remember { mutableStateOf<EventEntity?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showFestivalDialog by remember { mutableStateOf(false) }
    var festivalOptions by remember { mutableStateOf<List<com.ayaka7452.daymate.data.festival.FestivalDay>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(eventId) {
        eventId?.let { id ->
            container.eventRepository.getById(id)?.let { e ->
                loaded = e
                title = e.title
                note = e.note ?: ""
                epochDay = e.targetDateEpochDay
                refDaysText = e.refDays?.toString() ?: ""
                displayUnit = e.displayUnit ?: CountdownCalculator.UNIT_DAY
                repeatRule = e.repeatRule
                linkedFestival = e.linkedFestival
                // 跟随节日的事件不用 repeatRule（节日锚定优先），加载时归零保持数据一致
                if (e.linkedFestival != null) repeatRule = null
            }
        }
    }

    val isEdit = loaded != null
    // 对照值的单位跟随「倒计时显示单位」：按月显示时对照值即「月数」，其余同理。
    val refUnitLabel = when (displayUnit) {
        CountdownCalculator.UNIT_MONTH -> "月数"
        CountdownCalculator.UNIT_YEAR -> "年数"
        else -> "天数"
    }

    val datePickerState = rememberDatePickerState()
    LaunchedEffect(epochDay) {
        datePickerState.selectedDateMillis = LocalDate.ofEpochDay(epochDay)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑事件" else "新建事件") },
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                placeholder = { Text("例如：下次生日") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("简述（可选）") },
                placeholder = { Text("补充说明，例如地点、注意事项") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                TextButton(onClick = { showDatePicker = true }) {
                    val date = LocalDate.ofEpochDay(epochDay)
                    Text(
                        "目标日期：${date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                TextButton(onClick = { showResetConfirm = true }) {
                    Text("重置为今天")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("循环", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            // 跟随节日时循环不可选：节日每年日期不同（尤其农历），过期后由节假日数据自动锚定到该节日下一次
            val repeatEnabled = linkedFestival == null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = repeatRule == null,
                    enabled = repeatEnabled,
                    onClick = { repeatRule = null },
                    label = { Text("不循环") }
                )
                FilterChip(
                    selected = repeatRule == CountdownCalculator.REPEAT_WEEKLY,
                    enabled = repeatEnabled,
                    onClick = { repeatRule = CountdownCalculator.REPEAT_WEEKLY },
                    label = { Text("每周") }
                )
                FilterChip(
                    selected = repeatRule == CountdownCalculator.REPEAT_MONTHLY,
                    enabled = repeatEnabled,
                    onClick = { repeatRule = CountdownCalculator.REPEAT_MONTHLY },
                    label = { Text("每月") }
                )
                FilterChip(
                    selected = repeatRule == CountdownCalculator.REPEAT_YEARLY,
                    enabled = repeatEnabled,
                    onClick = { repeatRule = CountdownCalculator.REPEAT_YEARLY },
                    label = { Text("每年") }
                )
            }
            Text(
                if (linkedFestival != null) {
                    "已跟随「$linkedFestival」：目标日期过后自动锚定到该节日的下一次日期，无需设置循环"
                } else {
                    "目标日期过后自动锚定到下一周期：每周同一星期几、每月同一日、每年同月同日"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(12.dp))

            // ===== 跟随节日：目标日期自动锚定到节假日数据源中该节日的下一次日期 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    // 打开弹窗时加载可快选的节日：跨年取各节日下一次日期，
                    // 数据源还没发布新年份的（如明年春节）按「+1年」预估并标注「约」
                    festivalOptions = container.festivalRepository.pickerFestivals(LocalDate.now())
                    showFestivalDialog = true
                }) { Text("跟随节日…") }
                if (linkedFestival != null) {
                    TextButton(onClick = { linkedFestival = null }) {
                        Text("取消跟随（${linkedFestival}）")
                    }
                }
            }
            Text(
                "选择节日后，目标日期过后会自动更新到该节日的下一次日期（如春节每年变动）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(16.dp))

            Text("倒计时显示单位", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = displayUnit == CountdownCalculator.UNIT_DAY,
                    onClick = { displayUnit = CountdownCalculator.UNIT_DAY },
                    label = { Text("天数") }
                )
                FilterChip(
                    selected = displayUnit == CountdownCalculator.UNIT_MONTH,
                    onClick = { displayUnit = CountdownCalculator.UNIT_MONTH },
                    label = { Text("月数") }
                )
                FilterChip(
                    selected = displayUnit == CountdownCalculator.UNIT_YEAR,
                    onClick = { displayUnit = CountdownCalculator.UNIT_YEAR },
                    label = { Text("年数") }
                )
            }
            Text(
                "随时可更改；按月/按年不足一个完整单位时自动改用更小的单位显示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = refDaysText,
                onValueChange = { refDaysText = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("对照${refUnitLabel}（可选）") },
                placeholder = { Text("例如：8") },
                supportingText = { Text("目标日期已过去时，显示为「已过 X/N $refUnitLabel」，如 2/8；切换显示单位后请按新单位填写") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        val noteValue = note.takeIf { it.isNotBlank() }
                        val refValue = refDaysText.toIntOrNull()?.takeIf { it > 0 }
                        if (isEdit) {
                            container.eventRepository.update(
                                loaded!!.copy(
                                    title = title.ifBlank { "未命名事件" },
                                    note = noteValue,
                                    targetDateEpochDay = epochDay,
                                    refDays = refValue,
                                    displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY },
                                    repeatRule = repeatRule.takeIf { linkedFestival.isNullOrBlank() },
                                    linkedFestival = linkedFestival
                                )
                            )
                        } else {
                            container.eventRepository.add(
                                EventEntity(
                                    title = title.ifBlank { "未命名事件" },
                                    note = noteValue,
                                    targetDateEpochDay = epochDay,
                                    refDays = refValue,
                                    displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY },
                                    repeatRule = repeatRule.takeIf { linkedFestival.isNullOrBlank() },
                                    linkedFestival = linkedFestival,
                                    folderId = folderId
                                )
                            )
                        }
                        // 必须等写库完成后再关闭页面，否则 Activity 可能在 insert 提交前就被
                        // finish，Room 失效通知尚未发出，返回首页时列表读到的仍是旧快照（需再次操作才刷新）
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存")
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        epochDay = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showResetConfirm) {
        val current = LocalDate.ofEpochDay(epochDay)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置目标日期") },
            text = {
                Text(
                    "是否把目标日期重置为今天？\n当前：${current.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    val today = LocalDate.now().toEpochDay()
                    if (isEdit) {
                        // 编辑模式：立即写库（只改日期，不动表单里未保存的其他修改），
                        // 并同步表单状态，随后按「保存」会以此日期为准
                        scope.launch {
                            loaded?.let { e ->
                                val updated = e.copy(
                                    targetDateEpochDay = today,
                                    updatedAt = System.currentTimeMillis()
                                )
                                container.eventRepository.update(updated)
                                loaded = updated
                            }
                        }
                    }
                    epochDay = today
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showFestivalDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFestivalDialog = false },
            title = { Text("跟随节日") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (festivalOptions.isEmpty()) {
                        Text(
                            "暂无节假日数据。\n请到「设置 → 节假日数据」选择数据源并下载后再使用跟随节日功能。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        festivalOptions.forEach { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 标题为空或仍是上一个跟随节日名时自动填入，避免覆盖用户自定义标题
                                        if (title.isBlank() || title == linkedFestival) title = f.name
                                        linkedFestival = f.name
                                        epochDay = f.date.toEpochDay()
                                        repeatRule = null
                                        showFestivalDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (f.isEstimate) {
                                            "约 ${f.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}（暂按去年推算，下载新数据后自动校正）"
                                        } else {
                                            f.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val days = f.date.toEpochDay() - LocalDate.now().toEpochDay()
                                Text(
                                    if (days == 0L) "今天" else "还有 $days 天",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFestivalDialog = false }) { Text("关闭") }
            }
        )
    }
}
