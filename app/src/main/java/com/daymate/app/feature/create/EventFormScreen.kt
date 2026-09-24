package com.ayaka7452.daymate.feature.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
    var showByDaysDialog by remember { mutableStateOf(false) }
    var showUnitDialog by remember { mutableStateOf(false) }
    var showRefDialog by remember { mutableStateOf(false) }
    // 更多设置默认折叠；从节日卡片点进（预置跟随节日）时自动展开，让用户看到节日绑定
    var moreExpanded by remember { mutableStateOf(prefillFestival != null) }
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
                // 跟随节日的事件不用 repeatRule（节日锚定优先），加载时归零保持数据一致；
                // 编辑已有节日绑定的事件时自动展开「更多设置」，否则绑定状态被折叠藏住
                if (e.linkedFestival != null) {
                    repeatRule = null
                    moreExpanded = true
                }
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

    // 保存：顶栏右侧实心按钮触发；必须等写库完成后再关闭页面，否则 Activity 可能在
    // insert 提交前就被 finish，Room 失效通知尚未发出，返回首页时列表读到的仍是旧快照
    val onSave: () -> Unit = {
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
            onBack()
        }
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
                },
                actions = {
                    Button(
                        onClick = onSave,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(Tr.s(R.string.common_save))
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ===== 卡片 1：基本信息（含所在文件夹——归属是基本设置，不收进更多设置） =====
            FormCard {
                Text(
                    Tr.s(R.string.form_section_basic),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(Tr.s(R.string.common_title)) },
                    placeholder = { Text(Tr.s(R.string.event_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(Tr.s(R.string.event_note_label)) },
                    placeholder = { Text(Tr.s(R.string.event_note_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                // ---- 所在文件夹：基本设置，单独成行 ----
                SettingRow(
                    label = Tr.s(R.string.detail_folder),
                    value = {
                        val currentFolder = folders.firstOrNull { it.id == folderIdSel }
                        Text(
                            "${currentFolder?.icon ?: "📁"}  ${currentFolder?.name ?: Tr.s(R.string.event_root_space)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = { showFolderPicker = true }
                )
                if (folders.isEmpty()) {
                    Text(
                        Tr.s(R.string.event_no_folder_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ===== 卡片 2：时间 =====
            FormCard {
                Text(
                    Tr.s(R.string.form_section_time),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))

                // ---- 目标日期块：日期大字 + 还剩X天（同行）+ 选日期 / 按天数 / 重置今天 ----
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        val date = LocalDate.ofEpochDay(epochDay)
                        val diff = epochDay - LocalDate.now().toEpochDay()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                date.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd))),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    diff > 0 -> Tr.s(R.string.unit_days_future, diff)
                                    diff == 0L -> Tr.s(R.string.common_today)
                                    else -> Tr.s(R.string.unit_days_past, -diff)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.weight(1f)
                            ) { Text(Tr.s(R.string.form_pick_date)) }
                            Button(
                                onClick = { showByDaysDialog = true },
                                modifier = Modifier.weight(1f)
                            ) { Text(Tr.s(R.string.form_by_days)) }
                            OutlinedButton(
                                onClick = { showResetConfirm = true },
                                modifier = Modifier.weight(1f)
                            ) { Text(Tr.s(R.string.event_reset_today)) }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ---- 重复：跟随节日时不可选（节日每年日期不同，锚定优先） ----
                Text(Tr.s(R.string.repeat_label), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
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
                        // 库里存的是锚定名（跟着数据源走，可能是中文也可能是英文），
                        // 展示要过一道 HolidayNames，否则英文界面会出现「Following 春节」
                        Tr.s(
                            R.string.event_follow_festival_hint,
                            com.ayaka7452.daymate.data.festival.HolidayNames.displayLinked(linkedFestival.orEmpty())
                        )
                    } else {
                        Tr.s(R.string.event_repeat_hint)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ===== 卡片 3：更多设置（默认折叠，点击展开；只收纳低频项） =====
            FormCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { moreExpanded = !moreExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Tr.s(R.string.form_section_more),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.rotate(if (moreExpanded) 90f else 0f)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (moreExpanded) "" else linkedFestival?.let {
                            com.ayaka7452.daymate.data.festival.HolidayNames.displayLinked(it)
                        } ?: refUnitLabel.takeIf { displayUnit != CountdownCalculator.UNIT_DAY } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
                if (moreExpanded) {
                    Spacer(Modifier.height(4.dp))
                    // ---- 跟随节日：行式入口；ⓘ 气泡后跟「取消跟随」内联操作，压缩纵向空间 ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 打开弹窗时加载可快选的节日：跨年取各节日下一次日期，
                                // 数据源还没发布新年份的（如明年春节）按「+1年」预估并标注「约」
                                festivalOptions = container.festivalRepository.pickerFestivals(LocalDate.now())
                                showFestivalDialog = true
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Tr.s(R.string.form_festival_row), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(4.dp))
                        InfoHint(Tr.s(R.string.form_festival_info))
                        if (linkedFestival != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                Tr.s(
                                    R.string.event_unfollow,
                                    com.ayaka7452.daymate.data.festival.HolidayNames.displayLinked(linkedFestival.orEmpty())
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { linkedFestival = null }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            linkedFestival?.let {
                                com.ayaka7452.daymate.data.festival.HolidayNames.displayLinked(it)
                            } ?: Tr.s(R.string.form_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (linkedFestival != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("›", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    // ---- 倒计时显示单位 ----
                    SettingRow(
                        label = Tr.s(R.string.event_display_unit),
                        value = { Text(refUnitLabel, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { showUnitDialog = true }
                    )
                    // ---- 对照值（可选）：过期后卡片显示「已过 X/N」中的 N ----
                    SettingRow(
                        label = Tr.s(R.string.form_ref_row),
                        info = Tr.s(R.string.form_ref_info),
                        value = {
                            val refValue = refDaysText.toIntOrNull()?.takeIf { it > 0 }
                            Text(
                                if (refValue != null) "$refValue $refUnitLabel" else Tr.s(R.string.form_not_set),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (refValue != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        },
                        onClick = { showRefDialog = true }
                    )
                }
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

    // ---- 按天数设置：输 N + 单位（天/月/年，默认跟随显示单位、可手动切）+ 剩余/已过，实时预览 ----
    if (showByDaysDialog) {
        var byDaysText by remember { mutableStateOf("") }
        var byElapsed by remember { mutableStateOf(false) }
        var byUnit by remember { mutableStateOf(displayUnit) }
        val days = byDaysText.toIntOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showByDaysDialog = false },
            title = { Text(Tr.s(R.string.form_by_days_title)) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = byDaysText,
                            onValueChange = { byDaysText = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = { Text(Tr.s(R.string.form_days_label)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        // 剩余/已过：下拉框选择，替代并排双 chip（更省空间也更清晰）
                        Box {
                            var modeMenuOpen by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { modeMenuOpen = true },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    if (byElapsed) Tr.s(R.string.form_days_elapsed)
                                    else Tr.s(R.string.form_days_remaining)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("▾", style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(
                                expanded = modeMenuOpen,
                                onDismissRequest = { modeMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(Tr.s(R.string.form_days_remaining)) },
                                    onClick = {
                                        byElapsed = false
                                        modeMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(Tr.s(R.string.form_days_elapsed)) },
                                    onClick = {
                                        byElapsed = true
                                        modeMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // ---- 单位切换：默认与显示单位一致；即便显示单位是年，也仍可切回按天设置 ----
                    // 选中态用实底 primary + onPrimary，避免对话框浅底上浅蓝 chip 分不清是否选中
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = byUnit == CountdownCalculator.UNIT_DAY,
                            onClick = { byUnit = CountdownCalculator.UNIT_DAY },
                            label = { Text(Tr.s(R.string.form_unit_days)) },
                            colors = solidSelectedChipColors()
                        )
                        FilterChip(
                            selected = byUnit == CountdownCalculator.UNIT_MONTH,
                            onClick = { byUnit = CountdownCalculator.UNIT_MONTH },
                            label = { Text(Tr.s(R.string.form_unit_months)) },
                            colors = solidSelectedChipColors()
                        )
                        FilterChip(
                            selected = byUnit == CountdownCalculator.UNIT_YEAR,
                            onClick = { byUnit = CountdownCalculator.UNIT_YEAR },
                            label = { Text(Tr.s(R.string.form_unit_years)) },
                            colors = solidSelectedChipColors()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // 快捷键随单位变化：天 7/30/100/365，月 1/3/6/12，年 1/5/10
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = when (byUnit) {
                            CountdownCalculator.UNIT_MONTH -> listOf("1", "3", "6", "12")
                            CountdownCalculator.UNIT_YEAR -> listOf("1", "5", "10")
                            else -> listOf("7", "30", "100", "365")
                        }
                        presets.forEach { preset ->
                            FilterChip(
                                selected = byDaysText == preset,
                                onClick = { byDaysText = preset },
                                label = { Text(preset) },
                                colors = solidSelectedChipColors()
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (days != null) {
                        val now = LocalDate.now()
                        val signed = if (byElapsed) -days.toLong() else days.toLong()
                        val target = when (byUnit) {
                            CountdownCalculator.UNIT_MONTH -> now.plusMonths(signed)
                            CountdownCalculator.UNIT_YEAR -> now.plusYears(signed)
                            else -> now.plusDays(signed)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    Tr.s(
                                        R.string.form_days_preview,
                                        target.format(DateTimeFormatter.ofPattern(Tr.s(R.string.date_pattern_ymd)))
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    when {
                                        byUnit == CountdownCalculator.UNIT_MONTH && byElapsed -> Tr.s(R.string.form_days_calc_minus_m, days)
                                        byUnit == CountdownCalculator.UNIT_MONTH -> Tr.s(R.string.form_days_calc_plus_m, days)
                                        byUnit == CountdownCalculator.UNIT_YEAR && byElapsed -> Tr.s(R.string.form_days_calc_minus_y, days)
                                        byUnit == CountdownCalculator.UNIT_YEAR -> Tr.s(R.string.form_days_calc_plus_y, days)
                                        byElapsed -> Tr.s(R.string.form_days_calc_minus, days)
                                        else -> Tr.s(R.string.form_days_calc_plus, days)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = days != null,
                    onClick = {
                        days?.let { d ->
                            val signed = if (byElapsed) -d.toLong() else d.toLong()
                            epochDay = when (byUnit) {
                                CountdownCalculator.UNIT_MONTH -> LocalDate.now().plusMonths(signed)
                                CountdownCalculator.UNIT_YEAR -> LocalDate.now().plusYears(signed)
                                else -> LocalDate.now().plusDays(signed)
                            }.toEpochDay()
                        }
                        showByDaysDialog = false
                    }
                ) { Text(Tr.s(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showByDaysDialog = false }) { Text(Tr.s(R.string.common_cancel)) }
            }
        )
    }

    // ---- 显示单位：单选对话框 ----
    if (showUnitDialog) {
        AlertDialog(
            onDismissRequest = { showUnitDialog = false },
            title = { Text(Tr.s(R.string.form_unit_dialog_title)) },
            text = {
                Column {
                    listOf(
                        CountdownCalculator.UNIT_DAY to Tr.s(R.string.form_unit_days),
                        CountdownCalculator.UNIT_MONTH to Tr.s(R.string.form_unit_months),
                        CountdownCalculator.UNIT_YEAR to Tr.s(R.string.form_unit_years)
                    ).forEach { (unit, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    displayUnit = unit
                                    showUnitDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = displayUnit == unit, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Tr.s(R.string.event_display_unit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnitDialog = false }) { Text(Tr.s(R.string.common_close)) }
            }
        )
    }

    // ---- 对照值：数字输入对话框（可清除） ----
    if (showRefDialog) {
        var refDraft by remember { mutableStateOf(refDaysText) }
        AlertDialog(
            onDismissRequest = { showRefDialog = false },
            title = { Text(Tr.s(R.string.form_ref_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = refDraft,
                        onValueChange = { refDraft = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text(Tr.s(R.string.form_ref_row)) },
                        placeholder = { Text(Tr.s(R.string.event_ref_hint)) },
                        supportingText = { Text(Tr.s(R.string.event_ref_desc, refUnitLabel)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    refDaysText = refDraft
                    showRefDialog = false
                }) { Text(Tr.s(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    refDaysText = ""
                    showRefDialog = false
                }) { Text(Tr.s(R.string.form_ref_clear)) }
            }
        )
    }

    if (showResetConfirm) {
        val current = LocalDate.ofEpochDay(epochDay)
        AlertDialog(
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
        AlertDialog(
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
                                        // 标题填**当前语言的显示名**（用户看到什么就存什么），
                                        // 锚定用的 linkedFestival 仍存数据源原名——两者刻意分开。
                                        // 标题为空、或恰好还是上一个节日的显示名时自动填入，不覆盖用户自定义标题。
                                        val shown = com.ayaka7452.daymate.data.festival.HolidayNames.display(f)
                                        val prevShown = linkedFestival
                                            ?.let { com.ayaka7452.daymate.data.festival.HolidayNames.displayLinked(it) }
                                        if (title.isBlank() || (prevShown != null && title == prevShown)) {
                                            title = shown
                                        }
                                        linkedFestival = f.name
                                        epochDay = f.date.toEpochDay()
                                        repeatRule = null
                                        showFestivalDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        com.ayaka7452.daymate.data.festival.HolidayNames.display(f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
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

/** 表单分组卡片容器：圆角 + 细边框 + 内边距。 */
@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

/** 对话框内 FilterChip 的选中配色：实底 primary + onPrimary 文字，选中与否一眼可辨。 */
@Composable
private fun solidSelectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)

/** 设置行：左标签（可带 ⓘ），右值 + ›，整行可点。 */
@Composable
private fun SettingRow(
    label: String,
    value: @Composable () -> Unit,
    onClick: () -> Unit,
    info: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (info != null) {
            Spacer(Modifier.width(4.dp))
            InfoHint(info)
        }
        Spacer(Modifier.weight(1f))
        value()
        Spacer(Modifier.width(4.dp))
        Text("›", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

/** ⓘ 信息气泡：轻点图标切换显示，点外部关闭。 */
@Composable
private fun InfoHint(text: String) {
    var show by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .clickable { show = !show },
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (show) {
            Popup(
                onDismissRequest = { show = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text,
                        modifier = Modifier
                            .padding(10.dp)
                            .widthIn(max = 260.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
