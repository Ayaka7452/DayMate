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
import androidx.compose.runtime.collectAsState
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
import com.ayaka7452.daymate.R
import com.ayaka7452.daymate.core.i18n.Tr
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
    // 所在文件夹：新建时预置入口传入的 folderId（主页=null 根目录，文件夹页=当前文件夹）；编辑时从事件加载
    var folderIdSel by remember { mutableStateOf<Long?>(folderId) }
    var showFolderPicker by remember { mutableStateOf(false) }
    // 用 remember 固定 Flow 实例：否则每次重组都会新建 Flow，collectAsState 反复取消/重建观察者，
    // 从其它页面返回时可能漏掉 Room 的变更通知（与 HomeScreen 同一处理）
    val folders by remember { container.folderRepository.observeAll() }
        .collectAsState(initial = emptyList())
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
                folderIdSel = e.folderId
                // 跟随节日的事件不用 repeatRule（节日锚定优先），加载时归零保持数据一致
                if (e.linkedFestival != null) repeatRule = null
            }
        }
    }

    val isEdit = loaded != null
    // 对照值的单位跟随「倒计时显示单位」：按月显示时对照值即「月数」，其余同理。
    val refUnitLabel = when (displayUnit) {
        CountdownCalculator.UNIT_MONTH -> Tr.s(R.string.form_unit_months)
        CountdownCalculator.UNIT_YEAR -> Tr.s(R.string.form_unit_years)
        else -> Tr.s(R.string.form_unit_days)
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
                title = { Text(if (isEdit) Tr.s(R.string.event_edit) else Tr.s(R.string.event_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Tr.s(R.string.common_back))
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
                label = { Text(Tr.s(R.string.common_title)) },
                placeholder = { Text(Tr.s(R.string.event_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(Tr.s(R.string.event_note_label)) },
                placeholder = { Text(Tr.s(R.string.event_note_hint)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // ===== 所在文件夹：可随时切换根目录/任意文件夹 =====
            Text(Tr.s(R.string.detail_folder), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { showFolderPicker = true }) {
                val currentFolder = folders.firstOrNull { it.id == folderIdSel }
                Text("📂  ${currentFolder?.name ?: Tr.s(R.string.event_root_space)}")
            }
            if (folders.isEmpty()) {
                Text(
                    Tr.s(R.string.event_no_folder_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                TextButton(onClick = { showDatePicker = true }) {
                    val date = LocalDate.ofEpochDay(epochDay)
                    Text(
                        Tr.s(
                            R.string.event_target_date,
                            date.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd)))
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                TextButton(onClick = { showResetConfirm = true }) {
                    Text(Tr.s(R.string.event_reset_today))
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(Tr.s(R.string.repeat_label), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            // 跟随节日时循环不可选：节日每年日期不同（尤其农历），过期后由节假日数据自动锚定到该节日下一次
            val repeatEnabled = linkedFestival == null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = repeatRule == null,
                    enabled = repeatEnabled,
                    onClick = { repeatRule = null },
                    label = { Text(Tr.s(R.string.repeat_none)) }
                )
                FilterChip(
                    selected = repeatRule == CountdownCalculator.REPEAT_WEEKLY,
                    enabled = repeatEnabled,
                    onClick = { repeatRule = CountdownCalculator.REPEAT_WEEKLY },
                    label = { Text(Tr.s(R.string.repeat_weekly)) }
                )
                FilterChip(
                    selected = repeatRule == CountdownCalculator.REPEAT_MONTHLY,
                    enabled = repeatEnabled,
                    onClick = { repeatRule = CountdownCalculator.REPEAT_MONTHLY },
                    label = { Text(Tr.s(R.string.repeat_monthly)) }
                )
                FilterChip(
                    selected = repeatRule == CountdownCalculator.REPEAT_YEARLY,
                    enabled = repeatEnabled,
                    onClick = { repeatRule = CountdownCalculator.REPEAT_YEARLY },
                    label = { Text(Tr.s(R.string.repeat_yearly)) }
                )
            }
            Text(
                if (linkedFestival != null) {
                    Tr.s(R.string.event_follow_festival_hint, linkedFestival.orEmpty())
                } else {
                    Tr.s(R.string.event_repeat_hint)
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
                }) { Text(Tr.s(R.string.event_follow_festival)) }
                if (linkedFestival != null) {
                    TextButton(onClick = { linkedFestival = null }) {
                        Text(Tr.s(R.string.event_unfollow, linkedFestival.orEmpty()))
                    }
                }
            }
            Text(
                Tr.s(R.string.event_festival_pick_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(16.dp))

            Text(Tr.s(R.string.event_display_unit), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = displayUnit == CountdownCalculator.UNIT_DAY,
                    onClick = { displayUnit = CountdownCalculator.UNIT_DAY },
                    label = { Text(Tr.s(R.string.form_unit_days)) }
                )
                FilterChip(
                    selected = displayUnit == CountdownCalculator.UNIT_MONTH,
                    onClick = { displayUnit = CountdownCalculator.UNIT_MONTH },
                    label = { Text(Tr.s(R.string.form_unit_months)) }
                )
                FilterChip(
                    selected = displayUnit == CountdownCalculator.UNIT_YEAR,
                    onClick = { displayUnit = CountdownCalculator.UNIT_YEAR },
                    label = { Text(Tr.s(R.string.form_unit_years)) }
                )
            }
            Text(
                Tr.s(R.string.event_display_unit_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = refDaysText,
                onValueChange = { refDaysText = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text(Tr.s(R.string.event_ref_label, refUnitLabel)) },
                placeholder = { Text(Tr.s(R.string.event_ref_hint)) },
                supportingText = { Text(Tr.s(R.string.event_ref_desc, refUnitLabel)) },
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
                                    title = title.ifBlank { Tr.s(R.string.event_unnamed) },
                                    note = noteValue,
                                    targetDateEpochDay = epochDay,
                                    refDays = refValue,
                                    displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY },
                                    repeatRule = repeatRule.takeIf { linkedFestival.isNullOrBlank() },
                                    linkedFestival = linkedFestival,
                                    folderId = folderIdSel
                                )
                            )
                        } else {
                            container.eventRepository.add(
                                EventEntity(
                                    title = title.ifBlank { Tr.s(R.string.event_unnamed) },
                                    note = noteValue,
                                    targetDateEpochDay = epochDay,
                                    refDays = refValue,
                                    displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY },
                                    repeatRule = repeatRule.takeIf { linkedFestival.isNullOrBlank() },
                                    linkedFestival = linkedFestival,
                                    folderId = folderIdSel
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
                Text(Tr.s(R.string.common_save))
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
                }) { Text(Tr.s(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(Tr.s(R.string.common_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showResetConfirm) {
        val current = LocalDate.ofEpochDay(epochDay)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(Tr.s(R.string.event_reset_title)) },
            text = {
                Text(
                    Tr.s(
                        R.string.event_reset_desc,
                        current.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd)))
                    )
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
                }) { Text(Tr.s(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(Tr.s(R.string.common_cancel)) }
            }
        )
    }

    if (showFolderPicker) {
        com.ayaka7452.daymate.feature.common.PickFolderDialog(
            title = Tr.s(R.string.event_pick_folder),
            folders = folders.map { it.id to "${it.icon ?: "📁"}  ${it.name}" },
            onDismiss = { showFolderPicker = false },
            onPick = { picked ->
                folderIdSel = picked
                showFolderPicker = false
            }
        )
    }

    if (showFestivalDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFestivalDialog = false },
            title = { Text(Tr.s(R.string.event_follow_festival_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (festivalOptions.isEmpty()) {
                        Text(
                            Tr.s(R.string.event_no_festival_data),
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
                                            Tr.s(
                                                R.string.event_festival_estimate,
                                                f.date.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd)))
                                            )
                                        } else {
                                            f.date.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd)))
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val days = f.date.toEpochDay() - LocalDate.now().toEpochDay()
                                Text(
                                    if (days == 0L) Tr.s(R.string.common_today) else Tr.s(R.string.unit_days_future, days),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFestivalDialog = false }) { Text(Tr.s(R.string.common_close)) }
            }
        )
    }
}
