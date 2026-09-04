package com.ayaka7452.daymate.feature.create

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
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var epochDay by remember { mutableStateOf(LocalDate.now().plusDays(7).toEpochDay()) }
    var refDaysText by remember { mutableStateOf("") }
    var displayUnit by remember { mutableStateOf(CountdownCalculator.UNIT_DAY) }
    var loaded by remember { mutableStateOf<EventEntity?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
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

            TextButton(onClick = { showDatePicker = true }) {
                val date = LocalDate.ofEpochDay(epochDay)
                Text(
                    "目标日期：${date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

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
                                    displayUnit = displayUnit.takeIf { it != CountdownCalculator.UNIT_DAY }
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
}
